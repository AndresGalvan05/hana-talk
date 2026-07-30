from unittest.mock import patch

import pytest

from app.chat import ChatFailedError, get_chat_reply
from app.schemas import ChatMessage, ChatReply

_HISTORY = [
    ChatMessage(speaker="tutor", japanese="こんにちは！"),
    ChatMessage(speaker="user", japanese="こんにちは。げんきです。"),
]

_VALID_REPLY_JSON = ChatReply(
    japanese="げんきですか。",
    english="Are you doing well?",
).model_dump_json()

_MALFORMED_REPLY_JSON = '{"japanese": "", "english": ""}'


def test_gemini_succeeds_groq_and_openrouter_are_never_called():
    with (
        patch("app.llm_fallback.call_gemini", return_value=_VALID_REPLY_JSON) as gemini,
        patch("app.llm_fallback.call_groq") as groq,
        patch("app.llm_fallback.call_openrouter") as openrouter,
    ):
        reply = get_chat_reply("N5", _HISTORY, "げんきです。")

    assert reply.japanese == "げんきですか。"
    gemini.assert_called_once()
    groq.assert_not_called()
    openrouter.assert_not_called()


def test_gemini_fails_groq_succeeds_openrouter_is_never_called():
    with (
        patch("app.llm_fallback.call_gemini", side_effect=RuntimeError("gemini down")),
        patch("app.llm_fallback.call_groq", return_value=_VALID_REPLY_JSON) as groq,
        patch("app.llm_fallback.call_openrouter") as openrouter,
    ):
        reply = get_chat_reply("N5", _HISTORY, "げんきです。")

    assert reply.japanese == "げんきですか。"
    groq.assert_called_once()
    openrouter.assert_not_called()


def test_malformed_reply_falls_through_like_a_transport_error():
    with (
        patch("app.llm_fallback.call_gemini", return_value=_MALFORMED_REPLY_JSON),
        patch("app.llm_fallback.call_groq", return_value=_VALID_REPLY_JSON) as groq,
        patch("app.llm_fallback.call_openrouter") as openrouter,
    ):
        reply = get_chat_reply("N5", _HISTORY, "げんきです。")

    assert reply.japanese == "げんきですか。"
    groq.assert_called_once()
    openrouter.assert_not_called()


def test_all_providers_failing_raises_chat_failed_error():
    with (
        patch("app.llm_fallback.call_gemini", side_effect=RuntimeError("gemini down")) as gemini,
        patch("app.llm_fallback.call_groq", side_effect=RuntimeError("groq down")) as groq,
        patch(
            "app.llm_fallback.call_openrouter",
            side_effect=RuntimeError("openrouter down"),
        ) as openrouter,
    ):
        with pytest.raises(ChatFailedError):
            get_chat_reply("N5", _HISTORY, "げんきです。")

    gemini.assert_called_once()
    groq.assert_called_once()
    openrouter.assert_called_once()


def test_history_longer_than_the_cap_is_trimmed_before_the_prompt_is_built():
    long_history = [ChatMessage(speaker="user", japanese=f"メッセージ{i}") for i in range(25)]

    with patch("app.llm_fallback.call_gemini", return_value=_VALID_REPLY_JSON) as gemini:
        get_chat_reply("N5", long_history, "げんきです。")

    # 25 messages, cap 20 -- the oldest 5 (indices 0-4) are dropped, the
    # most recent 20 (indices 5-24) are kept.
    prompt = gemini.call_args.args[0]
    assert "メッセージ4" not in prompt
    assert "メッセージ5" in prompt
    assert "メッセージ24" in prompt
