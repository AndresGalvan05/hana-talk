import os

from google import genai
from openai import OpenAI

from app.config import settings

_GROQ_BASE_URL = "https://api.groq.com/openai/v1"
_OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"

_gemini_client: genai.Client | None = None
_groq_client: OpenAI | None = None
_openrouter_client: OpenAI | None = None


def _get_gemini_client() -> genai.Client:
    global _gemini_client
    if _gemini_client is None:
        _gemini_client = genai.Client()
    return _gemini_client


def _get_groq_client() -> OpenAI:
    global _groq_client
    if _groq_client is None:
        _groq_client = OpenAI(api_key=os.environ["GROQ_API_KEY"], base_url=_GROQ_BASE_URL)
    return _groq_client


def _get_openrouter_client() -> OpenAI:
    global _openrouter_client
    if _openrouter_client is None:
        _openrouter_client = OpenAI(
            api_key=os.environ["OPENROUTER_API_KEY"],
            base_url=_OPENROUTER_BASE_URL,
        )
    return _openrouter_client


def call_gemini(prompt: str, schema: dict) -> str:
    interaction = _get_gemini_client().interactions.create(
        model=settings.gemini_model,
        input=prompt,
        response_format={"type": "text", "mime_type": "application/json", "schema": schema},
    )
    return interaction.output_text


def _call_openai_compatible(client: OpenAI, model: str, prompt: str, schema: dict) -> str:
    response = client.chat.completions.create(
        model=model,
        messages=[{"role": "user", "content": prompt}],
        response_format={
            "type": "json_schema",
            "json_schema": {"name": "generation_result", "strict": True, "schema": schema},
        },
    )
    return response.choices[0].message.content


def call_groq(prompt: str, schema: dict) -> str:
    return _call_openai_compatible(_get_groq_client(), settings.groq_model, prompt, schema)


def call_openrouter(prompt: str, schema: dict) -> str:
    return _call_openai_compatible(
        _get_openrouter_client(),
        settings.openrouter_model,
        prompt,
        schema,
    )
