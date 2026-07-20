from pymongo import MongoClient
from pymongo.collection import Collection

from app.config import settings
from app.schemas import GenerationResult

_client: MongoClient | None = None


def _get_collection() -> Collection:
    global _client
    if _client is None:
        _client = MongoClient(settings.mongo_uri)
    return _client[settings.mongo_db]["generated_exercises"]


def get_cached(lesson_id: str) -> GenerationResult | None:
    document = _get_collection().find_one({"_id": lesson_id})
    if document is None:
        return None
    return GenerationResult.model_validate(document["result"])


def put_cached(lesson_id: str, result: GenerationResult) -> None:
    _get_collection().replace_one(
        {"_id": lesson_id},
        {"_id": lesson_id, "result": result.model_dump(mode="json")},
        upsert=True,
    )
