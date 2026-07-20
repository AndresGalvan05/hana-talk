import json

from google import genai
from pydantic import ValidationError

from app.config import settings
from app.schemas import GenerationResult


class GenerationFailedError(Exception):
    """The provider response did not conform to the exercise schema."""


_PROMPT_TEMPLATE = """\
You are writing exercises for a Japanese language learner studying JLPT
level {jlpt_level}. Based on the lesson content below, write exactly one
multiple-choice (MCQ) exercise and exactly one fill-in-the-blank exercise
that test understanding of the lesson's content.

For the MCQ exercise, provide 3-4 plausible options, exactly one of which is
correct, and set correct_answer to that option's exact text.

For the fill-in-the-blank exercise, do not provide options; correct_answer
is the single expected word or short phrase.

Lesson content:
\"\"\"
{content}
\"\"\"
"""

_client: genai.Client | None = None


def _get_client() -> genai.Client:
    global _client
    if _client is None:
        _client = genai.Client()
    return _client


def generate_exercises(content: str, jlpt_level: str) -> GenerationResult:
    prompt = _PROMPT_TEMPLATE.format(jlpt_level=jlpt_level, content=content)

    interaction = _get_client().interactions.create(
        model=settings.gemini_model,
        input=prompt,
        response_format={
            "type": "text",
            "mime_type": "application/json",
            "schema": GenerationResult.model_json_schema(),
        },
    )

    try:
        return GenerationResult.model_validate_json(interaction.output_text)
    except (ValidationError, json.JSONDecodeError) as exc:
        raise GenerationFailedError(str(exc)) from exc
