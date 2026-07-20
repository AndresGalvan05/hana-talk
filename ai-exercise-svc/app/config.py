from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=None)

    gemini_model: str = "gemini-3.5-flash"
    mongo_uri: str = "mongodb://localhost:27017"
    mongo_db: str = "ai_exercise_svc"
    generation_request_timeout_seconds: float = 30.0


settings = Settings()
