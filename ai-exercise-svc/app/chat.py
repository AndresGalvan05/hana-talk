from app.llm_fallback import AllProvidersFailedError, call_with_fallback
from app.schemas import ChatMessage, ChatReply

_MAX_HISTORY_MESSAGES = 20


class ChatFailedError(Exception):
    """No provider in the chain produced a schema-conforming chat reply."""


_PROMPT_TEMPLATE = """\
You are a friendly, patient Japanese conversation tutor chatting with a
learner studying JLPT level {jlpt_level}. This level changes how you
write, not just a ceiling on difficulty -- do not default to
beginner-level, all-hiragana Japanese regardless of level.
- N5/N4: common hiragana-heavy vocabulary, basic sentence patterns,
  minimal kanji (e.g. こんにちは、げんきですか。にほんごを べんきょうしています。).
- N3: everyday kanji mixed in naturally, slightly more complex grammar
  (e.g. 最近忙しいですが、頑張っています。).
- N2/N1: natural adult-level written Japanese -- proper kanji throughout
  (not hiragana substitutes for words that are normally written in
  kanji), idiomatic expressions, and more complex grammar (e.g.
  最近の経済状況については、正直なところ少し心配しています。).
Keep replies short and conversational regardless of level -- this is a
practice conversation, not a lecture.

{history}
Learner's new message: {message}

Reply in character as the tutor, continuing the conversation naturally in
Japanese, and provide an English translation of your reply.

Separately, check the learner's new message for a genuine Japanese-
language mistake (grammar, particle, or word choice) -- not a stylistic
preference. If there is a real mistake, set correction to a short
explanation of it. If the message is correct (or is too short/ambiguous
to judge), leave correction unset -- do not invent a mistake to comment on.
"""


def _format_history(history: list[ChatMessage]) -> str:
    if not history:
        return "This is the start of the conversation.\n"
    trimmed = history[-_MAX_HISTORY_MESSAGES:]
    lines = (f"{'Learner' if m.speaker == 'user' else 'Tutor'}: {m.japanese}" for m in trimmed)
    return "Conversation so far:\n" + "\n".join(lines) + "\n"


def get_chat_reply(
    jlpt_level: str,
    history: list[ChatMessage],
    message: str,
) -> ChatReply:
    prompt = _PROMPT_TEMPLATE.format(
        jlpt_level=jlpt_level,
        history=_format_history(history),
        message=message,
    )
    schema = ChatReply.model_json_schema()
    try:
        return call_with_fallback(prompt, schema, ChatReply.model_validate_json)
    except AllProvidersFailedError as exc:
        raise ChatFailedError(str(exc)) from exc
