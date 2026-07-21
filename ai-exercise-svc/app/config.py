from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=None)

    gemini_model: str = "gemini-3.5-flash"
    # Groq's strict JSON-schema mode currently only covers the gpt-oss family.
    groq_model: str = "openai/gpt-oss-20b"
    # Deliberately a different model family from Groq's, for infra diversity;
    # free tier, and OpenRouter lists it as structured-output capable.
    openrouter_model: str = "google/gemma-4-26b-a4b-it:free"
    mongo_uri: str = "mongodb://localhost:27017"
    mongo_db: str = "ai_exercise_svc"
    generation_request_timeout_seconds: float = 30.0


settings = Settings()
