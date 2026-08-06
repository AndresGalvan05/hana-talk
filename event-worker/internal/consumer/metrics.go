package consumer

import (
	"context"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"
)

var meter = otel.Meter("event-worker/consumer")

var (
	processingDuration, _ = meter.Float64Histogram(
		"kafka_message_processing_duration_seconds",
		metric.WithDescription("Kafka message processing duration"),
	)
	messagesProcessed, _ = meter.Int64Counter(
		"kafka_messages_processed_total",
		metric.WithDescription("Kafka messages processed, by topic and outcome"),
	)
)

func recordProcessed(ctx context.Context, topic string, durationSeconds float64, success bool) {
	attrs := metric.WithAttributes(
		attribute.String("topic", topic),
		attribute.Bool("success", success),
	)
	processingDuration.Record(ctx, durationSeconds, metric.WithAttributes(attribute.String("topic", topic)))
	messagesProcessed.Add(ctx, 1, attrs)
}
