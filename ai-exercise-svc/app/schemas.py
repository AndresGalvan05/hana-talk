from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field, model_validator


class ExerciseType(str, Enum):
    MCQ = "MCQ"
    FILL_IN_BLANK = "FILL_IN_BLANK"
    TRANSLATION = "TRANSLATION"
    SENTENCE_ORDERING = "SENTENCE_ORDERING"


class GrammarPointInput(BaseModel):
    title: str
    explanation: str


class GenerateRequest(BaseModel):
    lesson_id: str
    grammar_points: list[GrammarPointInput]
    jlpt_level: str


class GeneratedExercise(BaseModel):
    type: ExerciseType
    prompt: str = Field(description="The question shown to the learner.")
    options: list[str] | None = Field(
        default=None,
        description="Answer choices, required for MCQ, omitted for FILL_IN_BLANK.",
    )
    correct_answer: str = Field(description="The exact correct answer.")

    @model_validator(mode="after")
    def _check_options_match_type(self) -> "GeneratedExercise":
        if self.type == ExerciseType.MCQ:
            if not self.options:
                raise ValueError("MCQ exercises must have non-empty options")
            if self.correct_answer not in self.options:
                raise ValueError("MCQ correct_answer must be one of options")
        elif self.type == ExerciseType.SENTENCE_ORDERING:
            if not self.options:
                raise ValueError("SENTENCE_ORDERING exercises must have non-empty options")
            if sorted(self.options) != sorted(self.correct_answer.split()):
                raise ValueError(
                    "SENTENCE_ORDERING correct_answer must use exactly the tokens in options",
                )
        elif self.options:
            raise ValueError(f"{self.type} exercises must not have options")
        return self


MIN_EXERCISES = 4


class GenerationResult(BaseModel):
    exercises: list[GeneratedExercise]

    @model_validator(mode="after")
    def _check_minimum_variety(self) -> "GenerationResult":
        if len(self.exercises) < MIN_EXERCISES:
            raise ValueError(f"generation result must include at least {MIN_EXERCISES} exercises")
        types = {exercise.type for exercise in self.exercises}
        if ExerciseType.MCQ not in types or ExerciseType.FILL_IN_BLANK not in types:
            raise ValueError(
                "generation result must include at least one MCQ and one FILL_IN_BLANK exercise",
            )
        return self


class ChatMessage(BaseModel):
    speaker: Literal["user", "tutor"]
    japanese: str = Field(min_length=1)


class ChatRequest(BaseModel):
    jlpt_level: str
    history: list[ChatMessage]
    message: str = Field(min_length=1)


class ChatReply(BaseModel):
    japanese: str = Field(min_length=1, description="The tutor's reply, in Japanese.")
    english: str = Field(min_length=1, description="English translation of the reply.")
    correction: str | None = Field(
        default=None,
        description="A brief correction of the learner's last message, if it contained a "
        "Japanese-language mistake; omitted or null otherwise.",
    )
