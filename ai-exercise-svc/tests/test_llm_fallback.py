from unittest.mock import MagicMock, patch

from app.llm_fallback import call_with_fallback


def _patched_metrics():
    """Patches the module-level metric instruments with mocks, so tests can
    assert on recorded metrics without depending on process-wide OTel
    MeterProvider state (which other test files importing app.main may
    already have set once, making a real test-local provider unreliable)."""
    return (
        patch("app.llm_fallback.llm_call_duration", MagicMock()),
        patch("app.llm_fallback.llm_call_total", MagicMock()),
    )


def test_first_provider_success_records_one_success_metric():
    duration_patch, total_patch = _patched_metrics()
    with (
        duration_patch as duration,
        total_patch as total,
        patch("app.llm_fallback.call_gemini", return_value="raw"),
        patch("app.llm_fallback.call_groq") as groq,
        patch("app.llm_fallback.call_openrouter") as openrouter,
    ):
        result = call_with_fallback("prompt", {}, lambda raw: raw)

    assert result == "raw"
    groq.assert_not_called()
    openrouter.assert_not_called()
    duration.record.assert_called_once()
    assert duration.record.call_args.args[1] == {"provider": "gemini"}
    total.add.assert_called_once_with(1, {"provider": "gemini", "success": "true"})


def test_first_provider_failure_then_second_success_records_failure_then_success():
    duration_patch, total_patch = _patched_metrics()
    with (
        duration_patch as duration,
        total_patch as total,
        patch("app.llm_fallback.call_gemini", side_effect=RuntimeError("gemini down")),
        patch("app.llm_fallback.call_groq", return_value="raw"),
        patch("app.llm_fallback.call_openrouter") as openrouter,
    ):
        result = call_with_fallback("prompt", {}, lambda raw: raw)

    assert result == "raw"
    openrouter.assert_not_called()
    assert duration.record.call_count == 2
    assert total.add.call_args_list == [
        ((1, {"provider": "gemini", "success": "false"}),),
        ((1, {"provider": "groq", "success": "true"}),),
    ]


def test_all_providers_failing_records_three_failure_metrics():
    duration_patch, total_patch = _patched_metrics()
    with (
        duration_patch as duration,
        total_patch as total,
        patch("app.llm_fallback.call_gemini", side_effect=RuntimeError("gemini down")),
        patch("app.llm_fallback.call_groq", side_effect=RuntimeError("groq down")),
        patch("app.llm_fallback.call_openrouter", side_effect=RuntimeError("openrouter down")),
    ):
        try:
            call_with_fallback("prompt", {}, lambda raw: raw)
        except Exception:
            pass

    assert duration.record.call_count == 3
    assert total.add.call_args_list == [
        ((1, {"provider": "gemini", "success": "false"}),),
        ((1, {"provider": "groq", "success": "false"}),),
        ((1, {"provider": "openrouter", "success": "false"}),),
    ]
