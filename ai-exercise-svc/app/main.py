import os

from fastapi import FastAPI
from opentelemetry import metrics, trace
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

from app.routes import router

_resource = Resource.create({"service.name": "ai-exercise-svc"})

# OTLPSpanExporter() with no arguments reads OTEL_EXPORTER_OTLP_ENDPOINT
# from the environment itself (appending /v1/traces), same as
# genai.Client() reading GEMINI_API_KEY -- no explicit config wiring here.
tracer_provider = TracerProvider(resource=_resource)
tracer_provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
trace.set_tracer_provider(tracer_provider)

# Metrics export is opt-in (default off) because local Jaeger only accepts
# OTLP traces, not metrics -- an always-on exporter would error against it.
# A MeterProvider is registered either way so `meter.create_*` calls never
# fail regardless of the flag; when disabled it just has no export path.
if os.environ.get("OTEL_METRICS_ENABLED") == "true":
    meter_provider = MeterProvider(
        resource=_resource,
        metric_readers=[PeriodicExportingMetricReader(OTLPMetricExporter())],
    )
else:
    meter_provider = MeterProvider(resource=_resource)
metrics.set_meter_provider(meter_provider)
meter = metrics.get_meter("ai-exercise-svc")

app = FastAPI(title="ai-exercise-svc")
FastAPIInstrumentor.instrument_app(app)
app.include_router(router)
