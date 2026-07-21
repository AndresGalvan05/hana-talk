from unittest.mock import patch

import pytest

from app.generation import GenerationFailedError, generate_exercises
from app.schemas import ExerciseType, GeneratedExercise, GenerationResult

_MCQ = GeneratedExercise(type=ExerciseType.MCQ, prompt="p", options=["A", "B"], correct_answer="A")
_FILL_IN_BLANK = GeneratedExercise(type=ExerciseType.FILL_IN_BLANK, prompt="p", correct_answer="b")
_VALID_RESULT_JSON = GenerationResult(exercises=[_MCQ, _FILL_IN_BLANK]).model_dump_json()

# Schema-valid JSON that still fails GenerationResult's own validation:
# an MCQ with empty options, and no FILL_IN_BLANK exercise at all.
_SCHEMA_INVALID_JSON = (
    '{"exercises": [{"type": "MCQ", "prompt": "p", "options": [], "correct_answer": "A"}]}'
)


def test_gemini_succeeds_groq_and_openrouter_are_never_called():
    with (
        patch("app.generation.call_gemini", return_value=_VALID_RESULT_JSON) as gemini,
        patch("app.generation.call_groq") as groq,
        patch("app.generation.call_openrouter") as openrouter,
    ):
        result = generate_exercises("content", "N5")

    assert result.exercises[0].type == ExerciseType.MCQ
    gemini.assert_called_once()
    groq.assert_not_called()
    openrouter.assert_not_called()


def test_gemini_fails_groq_succeeds_openrouter_is_never_called():
    with (
        patch("app.generation.call_gemini", side_effect=RuntimeError("gemini down")),
        patch("app.generation.call_groq", return_value=_VALID_RESULT_JSON) as groq,
        patch("app.generation.call_openrouter") as openrouter,
    ):
        result = generate_exercises("content", "N5")

    assert result.exercises[0].type == ExerciseType.MCQ
    groq.assert_called_once()
    openrouter.assert_not_called()


def test_gemini_and_groq_fail_openrouter_succeeds():
    with (
        patch("app.generation.call_gemini", side_effect=RuntimeError("gemini down")),
        patch("app.generation.call_groq", side_effect=RuntimeError("groq down")),
        patch("app.generation.call_openrouter", return_value=_VALID_RESULT_JSON) as openrouter,
    ):
        result = generate_exercises("content", "N5")

    assert result.exercises[0].type == ExerciseType.MCQ
    openrouter.assert_called_once()


def test_all_providers_failing_raises_generation_failed_error():
    with (
        patch("app.generation.call_gemini", side_effect=RuntimeError("gemini down")) as gemini,
        patch("app.generation.call_groq", side_effect=RuntimeError("groq down")) as groq,
        patch(
            "app.generation.call_openrouter",
            side_effect=RuntimeError("openrouter down"),
        ) as openrouter,
    ):
        with pytest.raises(GenerationFailedError):
            generate_exercises("content", "N5")

    gemini.assert_called_once()
    groq.assert_called_once()
    openrouter.assert_called_once()


def test_schema_invalid_response_falls_through_like_a_transport_error():
    with (
        patch("app.generation.call_gemini", return_value=_SCHEMA_INVALID_JSON),
        patch("app.generation.call_groq", return_value=_VALID_RESULT_JSON) as groq,
        patch("app.generation.call_openrouter") as openrouter,
    ):
        result = generate_exercises("content", "N5")

    assert result.exercises[0].type == ExerciseType.MCQ
    groq.assert_called_once()
    openrouter.assert_not_called()
