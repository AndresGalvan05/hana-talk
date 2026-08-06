import time
from typing import Callable, TypeVar

from opentelemetry import metrics

from app.providers import call_gemini, call_groq, call_openrouter

T = TypeVar("T")

# metrics.get_meter reads whatever MeterProvider main.py has registered
# globally by the time this is actually called (not at import time), so
# this doesn't need to import from app.main -- doing so would be a
# circular import (main -> routes -> generation/chat -> llm_fallback).
meter = metrics.get_meter("ai-exercise-svc")

llm_call_duration = meter.create_histogram(
    "llm_call_duration_seconds", description="LLM provider call duration"
)
llm_call_total = meter.create_counter(
    "llm_call_total", description="LLM provider call attempts, by provider and outcome"
)


class AllProvidersFailedError(Exception):
    """No provider in the chain produced a response `parse` accepted."""


def call_with_fallback(prompt: str, schema: dict, parse: Callable[[str], T]) -> T:
    last_error: Exception | None = None

    # Providers are named here (not a module-level list) so each name is
    # re-resolved from this module's globals on every call — that's what
    # lets tests patch app.llm_fallback.call_gemini/call_groq/call_openrouter
    # and actually affect the chain, instead of patching a stale reference
    # captured once at import time. Paired with a plain string label (not a
    # dict keyed by function identity) for the same reason: a patched
    # callable wouldn't match a dict key captured from the original function.
    providers = (
        ("gemini", call_gemini),
        ("groq", call_groq),
        ("openrouter", call_openrouter),
    )
    for name, call_provider in providers:
        start = time.monotonic()
        try:
            raw = call_provider(prompt, schema)
            result = parse(raw)
        except Exception as exc:  # noqa: BLE001 - intentional: any failure, transport or parse, falls through to the next provider
            duration = time.monotonic() - start
            llm_call_duration.record(duration, {"provider": name})
            llm_call_total.add(1, {"provider": name, "success": "false"})
            last_error = exc
            continue
        duration = time.monotonic() - start
        llm_call_duration.record(duration, {"provider": name})
        llm_call_total.add(1, {"provider": name, "success": "true"})
        return result

    raise AllProvidersFailedError(str(last_error)) from last_error
