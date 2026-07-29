import pytest
from pydantic import ValidationError

from app.schemas import ExerciseType, GeneratedExercise, GenerationResult


def test_mcq_with_empty_options_is_rejected():
    with pytest.raises(ValidationError):
        GeneratedExercise(type=ExerciseType.MCQ, prompt="p", options=[], correct_answer="A")


def test_mcq_correct_answer_must_be_among_options():
    with pytest.raises(ValidationError):
        GeneratedExercise(type=ExerciseType.MCQ, prompt="p", options=["A", "B"], correct_answer="C")


def test_fill_in_blank_with_options_is_rejected():
    with pytest.raises(ValidationError):
        GeneratedExercise(
            type=ExerciseType.FILL_IN_BLANK,
            prompt="p",
            options=["A"],
            correct_answer="A",
        )


def test_translation_with_options_is_rejected():
    with pytest.raises(ValidationError):
        GeneratedExercise(
            type=ExerciseType.TRANSLATION,
            prompt="p",
            options=["A"],
            correct_answer="Watashi wa gakusei desu",
        )


def test_sentence_ordering_with_empty_options_is_rejected():
    with pytest.raises(ValidationError):
        GeneratedExercise(
            type=ExerciseType.SENTENCE_ORDERING,
            prompt="p",
            options=[],
            correct_answer="わたしは がくせい です",
        )


def test_sentence_ordering_correct_answer_must_use_exactly_the_option_tokens():
    with pytest.raises(ValidationError):
        GeneratedExercise(
            type=ExerciseType.SENTENCE_ORDERING,
            prompt="p",
            options=["です", "がくせい", "わたしは"],
            correct_answer="わたしは がくせい だ",
        )


def test_sentence_ordering_accepts_tokens_in_any_option_order():
    exercise = GeneratedExercise(
        type=ExerciseType.SENTENCE_ORDERING,
        prompt="p",
        options=["です", "がくせい", "わたしは"],
        correct_answer="わたしは がくせい です",
    )
    assert exercise.correct_answer == "わたしは がくせい です"


def test_generation_result_requires_both_exercise_types():
    mcq = GeneratedExercise(
        type=ExerciseType.MCQ,
        prompt="p",
        options=["A", "B"],
        correct_answer="A",
    )
    with pytest.raises(ValidationError):
        GenerationResult(exercises=[mcq])
