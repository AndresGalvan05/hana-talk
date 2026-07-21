from fastapi import FastAPI
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

from app.routes import router

# OTLPSpanExporter() with no arguments reads OTEL_EXPORTER_OTLP_ENDPOINT
# from the environment itself (appending /v1/traces), same as
# genai.Client() reading GEMINI_API_KEY -- no explicit config wiring here.
provider = TracerProvider(resource=Resource.create({"service.name": "ai-exercise-svc"}))
provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
trace.set_tracer_provider(provider)

app = FastAPI(title="ai-exercise-svc")
FastAPIInstrumentor.instrument_app(app)
app.include_router(router)
