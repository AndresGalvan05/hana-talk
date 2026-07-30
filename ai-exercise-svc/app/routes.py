from fastapi import APIRouter, HTTPException

from app import cache
from app.chat import ChatFailedError, get_chat_reply
from app.generation import GenerationFailedError, generate_exercises
from app.schemas import ChatReply, ChatRequest, GenerateRequest, GenerationResult

router = APIRouter()


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@router.post("/generate", response_model=GenerationResult)
def generate(request: GenerateRequest) -> GenerationResult:
    cached = cache.get_cached(request.lesson_id)
    if cached is not None:
        return cached

    try:
        result = generate_exercises(request.grammar_points, request.jlpt_level)
    except GenerationFailedError as exc:
        raise HTTPException(status_code=502, detail=f"generation failed: {exc}") from exc

    cache.put_cached(request.lesson_id, result)
    return result


@router.post("/chat", response_model=ChatReply)
def chat(request: ChatRequest) -> ChatReply:
    try:
        return get_chat_reply(request.jlpt_level, request.history, request.message)
    except ChatFailedError as exc:
        raise HTTPException(status_code=502, detail=f"chat failed: {exc}") from exc
