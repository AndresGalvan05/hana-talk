from app.providers import call_gemini, call_groq, call_openrouter
from app.schemas import GenerationResult


class GenerationFailedError(Exception):
    """No provider in the chain produced a schema-conforming response."""


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


def generate_exercises(content: str, jlpt_level: str) -> GenerationResult:
    prompt = _PROMPT_TEMPLATE.format(jlpt_level=jlpt_level, content=content)
    schema = GenerationResult.model_json_schema()
    last_error: Exception | None = None

    # Providers are named here (not a module-level list) so each name is
    # re-resolved from this module's globals on every call — that's what
    # lets tests patch app.generation.call_gemini/call_groq/call_openrouter
    # and actually affect the chain, instead of patching a stale reference
    # captured once at import time.
    for call_provider in (call_gemini, call_groq, call_openrouter):
        try:
            raw = call_provider(prompt, schema)
            return GenerationResult.model_validate_json(raw)
        except Exception as exc:  # noqa: BLE001 - intentional: any failure, transport or schema, falls through to the next provider
            last_error = exc
            continue

    raise GenerationFailedError(str(last_error)) from last_error
