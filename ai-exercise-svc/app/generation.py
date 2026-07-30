import json

from pydantic import ValidationError

from app.llm_fallback import AllProvidersFailedError, call_with_fallback
from app.schemas import GeneratedExercise, GenerationResult, GrammarPointInput


class GenerationFailedError(Exception):
    """No provider in the chain produced a schema-conforming response."""


_PROMPT_TEMPLATE = """\
You are writing exercises for a Japanese language learner studying JLPT
level {jlpt_level}. The lesson below covers the following grammar points,
in order:

{grammar_points}

Write one exercise testing each grammar point above -- write two exercises,
of different types, for any point that covers more than one distinct rule.
Across the whole batch, use a mix of all four exercise types below rather
than defaulting to just multiple-choice and fill-in-the-blank -- include at
least one multiple-choice exercise and at least one fill-in-the-blank
exercise, and prefer translation or sentence-ordering for grammar points
where word order or full-sentence construction is the point being tested.

For a multiple-choice exercise, provide 3-4 plausible options, exactly one
of which is correct, and set correct_answer to that option's exact text.

For a fill-in-the-blank exercise, do not provide options; correct_answer
is the single expected word or short phrase.

For a translation exercise, the prompt asks the learner to translate an
English sentence into Japanese; do not provide options; correct_answer is
the expected Japanese sentence.

For a sentence-ordering exercise, the prompt gives the English meaning of a
sentence and asks the learner to arrange the Japanese words in the correct
order; options is a JSON array of that sentence's words/particles in
shuffled order; correct_answer is those exact same option strings, verbatim
and in the same script (never romanized, translated, or reworded), in the
correct order, joined by single spaces.
"""


def _format_grammar_points(grammar_points: list[GrammarPointInput]) -> str:
    lines = (f"{i}. {p.title} -- {p.explanation}" for i, p in enumerate(grammar_points, start=1))
    return "\n".join(lines)


def _parse_result(raw: str) -> GenerationResult:
    # Individually schema-valid exercises are kept even when a sibling
    # exercise in the same batch is malformed (e.g. a sentence-ordering
    # correct_answer that doesn't match its options' tokens) -- discarding
    # the whole batch over one bad item forces a fall-through to the next
    # provider for no reason, and if every provider makes the same mistake
    # the request fails outright with nothing generated.
    data = json.loads(raw)
    valid_exercises = []
    for item in data.get("exercises", []):
        try:
            valid_exercises.append(GeneratedExercise.model_validate(item))
        except ValidationError:
            continue
    return GenerationResult(exercises=valid_exercises)


def generate_exercises(
    grammar_points: list[GrammarPointInput],
    jlpt_level: str,
) -> GenerationResult:
    formatted_points = _format_grammar_points(grammar_points)
    prompt = _PROMPT_TEMPLATE.format(jlpt_level=jlpt_level, grammar_points=formatted_points)
    schema = GenerationResult.model_json_schema()
    try:
        return call_with_fallback(prompt, schema, _parse_result)
    except AllProvidersFailedError as exc:
        raise GenerationFailedError(str(exc)) from exc
