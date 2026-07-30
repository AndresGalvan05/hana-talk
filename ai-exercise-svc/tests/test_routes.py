from unittest.mock import patch

from fastapi.testclient import TestClient

from app.chat import ChatFailedError
from app.generation import GenerationFailedError
from app.main import app
from app.schemas import (
    ChatReply,
    ExerciseType,
    GeneratedExercise,
    GenerationResult,
    GrammarPointInput,
)

client = TestClient(app)

_MCQ = GeneratedExercise(type=ExerciseType.MCQ, prompt="p", options=["A", "B"], correct_answer="A")
_FILL_IN_BLANK = GeneratedExercise(type=ExerciseType.FILL_IN_BLANK, prompt="p", correct_answer="B")
_SAMPLE_RESULT = GenerationResult(exercises=[_MCQ, _FILL_IN_BLANK, _MCQ, _FILL_IN_BLANK])

_REQUEST_BODY = {
    "lesson_id": "lesson-1",
    "grammar_points": [{"title": "X は Y です", "explanation": "topic + copula"}],
    "jlpt_level": "N5",
}


def test_cache_hit_skips_provider_call():
    with (
        patch("app.routes.cache.get_cached", return_value=_SAMPLE_RESULT) as get_cached,
        patch("app.routes.generate_exercises") as generate,
        patch("app.routes.cache.put_cached") as put_cached,
    ):
        response = client.post("/generate", json=_REQUEST_BODY)

    assert response.status_code == 200
    assert response.json()["exercises"][0]["type"] == "MCQ"
    get_cached.assert_called_once_with("lesson-1")
    generate.assert_not_called()
    put_cached.assert_not_called()


def test_cache_miss_calls_provider_and_populates_cache():
    with (
        patch("app.routes.cache.get_cached", return_value=None),
        patch("app.routes.generate_exercises", return_value=_SAMPLE_RESULT) as generate,
        patch("app.routes.cache.put_cached") as put_cached,
    ):
        response = client.post("/generate", json=_REQUEST_BODY)

    assert response.status_code == 200
    generate.assert_called_once_with(
        [GrammarPointInput(title="X は Y です", explanation="topic + copula")],
        "N5",
    )
    put_cached.assert_called_once_with("lesson-1", _SAMPLE_RESULT)


def test_malformed_provider_response_returns_502_and_does_not_cache():
    with (
        patch("app.routes.cache.get_cached", return_value=None),
        patch("app.routes.generate_exercises", side_effect=GenerationFailedError("bad json")),
        patch("app.routes.cache.put_cached") as put_cached,
    ):
        response = client.post("/generate", json=_REQUEST_BODY)

    assert response.status_code == 502
    put_cached.assert_not_called()


_CHAT_REQUEST_BODY = {
    "jlpt_level": "N5",
    "history": [{"speaker": "tutor", "japanese": "こんにちは！"}],
    "message": "こんにちは。",
}


def test_chat_success_returns_reply():
    reply = ChatReply(japanese="げんきですか。", english="Are you doing well?")
    with patch("app.routes.get_chat_reply", return_value=reply):
        response = client.post("/chat", json=_CHAT_REQUEST_BODY)

    assert response.status_code == 200
    assert response.json()["japanese"] == "げんきですか。"


def test_chat_failure_returns_502():
    with patch("app.routes.get_chat_reply", side_effect=ChatFailedError("all providers failed")):
        response = client.post("/chat", json=_CHAT_REQUEST_BODY)

    assert response.status_code == 502
