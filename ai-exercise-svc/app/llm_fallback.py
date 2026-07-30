from typing import Callable, TypeVar

from app.providers import call_gemini, call_groq, call_openrouter

T = TypeVar("T")


class AllProvidersFailedError(Exception):
    """No provider in the chain produced a response `parse` accepted."""


def call_with_fallback(prompt: str, schema: dict, parse: Callable[[str], T]) -> T:
    last_error: Exception | None = None

    # Providers are named here (not a module-level list) so each name is
    # re-resolved from this module's globals on every call — that's what
    # lets tests patch app.llm_fallback.call_gemini/call_groq/call_openrouter
    # and actually affect the chain, instead of patching a stale reference
    # captured once at import time.
    for call_provider in (call_gemini, call_groq, call_openrouter):
        try:
            raw = call_provider(prompt, schema)
            return parse(raw)
        except Exception as exc:  # noqa: BLE001 - intentional: any failure, transport or parse, falls through to the next provider
            last_error = exc
            continue

    raise AllProvidersFailedError(str(last_error)) from last_error
