from unittest.mock import patch

import pytest

from app.generation import GenerationFailedError, generate_exercises
from app.schemas import ExerciseType, GeneratedExercise, GenerationResult, GrammarPointInput

_GRAMMAR_POINTS = [
    GrammarPointInput(title="X は Y です", explanation="topic + copula"),
    GrammarPointInput(title="Noun の Noun", explanation="possession"),
]

_MCQ = GeneratedExercise(type=ExerciseType.MCQ, prompt="p", options=["A", "B"], correct_answer="A")
_FILL_IN_BLANK = GeneratedExercise(type=ExerciseType.FILL_IN_BLANK, prompt="p", correct_answer="b")
_VALID_RESULT_JSON = GenerationResult(
    exercises=[_MCQ, _FILL_IN_BLANK, _MCQ, _FILL_IN_BLANK],
).model_dump_json()

# Schema-valid JSON that still fails GenerationResult's own validation:
# below the minimum exercise count, and no FILL_IN_BLANK exercise at all.
_SCHEMA_INVALID_JSON = (
    '{"exercises": [{"type": "MCQ", "prompt": "p", "options": [], "correct_answer": "A"}]}'
)


def test_gemini_succeeds_groq_and_openrouter_are_never_called():
    with (
        patch("app.generation.call_gemini", return_value=_VALID_RESULT_JSON) as gemini,
        patch("app.generation.call_groq") as groq,
        patch("app.generation.call_openrouter") as openrouter,
    ):
        result = generate_exercises(_GRAMMAR_POINTS, "N5")

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
        result = generate_exercises(_GRAMMAR_POINTS, "N5")

    assert result.exercises[0].type == ExerciseType.MCQ
    groq.assert_called_once()
    openrouter.assert_not_called()


def test_gemini_and_groq_fail_openrouter_succeeds():
    with (
        patch("app.generation.call_gemini", side_effect=RuntimeError("gemini down")),
        patch("app.generation.call_groq", side_effect=RuntimeError("groq down")),
        patch("app.generation.call_openrouter", return_value=_VALID_RESULT_JSON) as openrouter,
    ):
        result = generate_exercises(_GRAMMAR_POINTS, "N5")

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
            generate_exercises(_GRAMMAR_POINTS, "N5")

    gemini.assert_called_once()
    groq.assert_called_once()
    openrouter.assert_called_once()


def test_schema_invalid_response_falls_through_like_a_transport_error():
    with (
        patch("app.generation.call_gemini", return_value=_SCHEMA_INVALID_JSON),
        patch("app.generation.call_groq", return_value=_VALID_RESULT_JSON) as groq,
        patch("app.generation.call_openrouter") as openrouter,
    ):
        result = generate_exercises(_GRAMMAR_POINTS, "N5")

    assert result.exercises[0].type == ExerciseType.MCQ
    groq.assert_called_once()
    openrouter.assert_not_called()


def test_one_malformed_exercise_is_dropped_not_the_whole_batch():
    # Real-world failure mode: a SENTENCE_ORDERING exercise whose
    # correct_answer doesn't use the same tokens as its options (e.g. the
    # model romanized it). That single item should be dropped rather than
    # invalidating an otherwise-good batch and forcing a fall-through.
    raw = (
        '{"exercises": ['
        '{"type": "MCQ", "prompt": "p", "options": ["A", "B"], "correct_answer": "A"}, '
        '{"type": "FILL_IN_BLANK", "prompt": "p", "correct_answer": "b"}, '
        '{"type": "MCQ", "prompt": "p", "options": ["A", "B"], "correct_answer": "A"}, '
        '{"type": "FILL_IN_BLANK", "prompt": "p", "correct_answer": "b"}, '
        '{"type": "SENTENCE_ORDERING", "prompt": "p", "options": ["学生", "です"], '
        '"correct_answer": "gakusei desu"}'
        "]}"
    )
    with (
        patch("app.generation.call_gemini", return_value=raw) as gemini,
        patch("app.generation.call_groq") as groq,
        patch("app.generation.call_openrouter") as openrouter,
    ):
        result = generate_exercises(_GRAMMAR_POINTS, "N5")

    assert len(result.exercises) == 4
    assert all(e.type != ExerciseType.SENTENCE_ORDERING for e in result.exercises)
    gemini.assert_called_once()
    groq.assert_not_called()
    openrouter.assert_not_called()


def test_batch_left_below_minimum_after_dropping_bad_items_falls_through():
    # If dropping the malformed items leaves too few exercises (or the
    # required MCQ/FILL_IN_BLANK mix is gone), that still counts as this
    # provider failing, and the chain falls through to the next one.
    raw = (
        '{"exercises": ['
        '{"type": "MCQ", "prompt": "p", "options": ["A", "B"], "correct_answer": "A"}, '
        '{"type": "SENTENCE_ORDERING", "prompt": "p", "options": ["学生", "です"], '
        '"correct_answer": "gakusei desu"}'
        "]}"
    )
    with (
        patch("app.generation.call_gemini", return_value=raw),
        patch("app.generation.call_groq", return_value=_VALID_RESULT_JSON) as groq,
        patch("app.generation.call_openrouter") as openrouter,
    ):
        result = generate_exercises(_GRAMMAR_POINTS, "N5")

    assert result.exercises[0].type == ExerciseType.MCQ
    groq.assert_called_once()
    openrouter.assert_not_called()
