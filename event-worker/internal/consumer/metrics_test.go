package consumer

import (
	"context"
	"testing"

	"go.opentelemetry.io/otel"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/metric/metricdata"
)

func findMetric(t *testing.T, rm metricdata.ResourceMetrics, name string) metricdata.Metrics {
	t.Helper()
	for _, sm := range rm.ScopeMetrics {
		for _, m := range sm.Metrics {
			if m.Name == name {
				return m
			}
		}
	}
	t.Fatalf("metric %q not found", name)
	return metricdata.Metrics{}
}

// TestRecordProcessed exercises both the success and failure paths in one
// test function, deliberately: OTel's global MeterProvider proxy only
// upgrades this package's already-created instruments to a real backing
// implementation the *first* time a provider is registered process-wide, so
// a second, later otel.SetMeterProvider call in a separate test would not
// retarget them -- they'd stay bound to whichever provider won that race.
func TestRecordProcessed(t *testing.T) {
	reader := sdkmetric.NewManualReader()
	provider := sdkmetric.NewMeterProvider(sdkmetric.WithReader(reader))
	otel.SetMeterProvider(provider)

	recordProcessed(context.Background(), "user.registered", 0.05, true)
	recordProcessed(context.Background(), "exercise.completed", 0.02, false)

	var rm metricdata.ResourceMetrics
	if err := reader.Collect(context.Background(), &rm); err != nil {
		t.Fatalf("collect: %v", err)
	}

	counter := findMetric(t, rm, "kafka_messages_processed_total")
	sum, ok := counter.Data.(metricdata.Sum[int64])
	if !ok {
		t.Fatalf("expected Sum[int64], got %T", counter.Data)
	}
	if len(sum.DataPoints) != 2 {
		t.Fatalf("got %d data points, want 2", len(sum.DataPoints))
	}

	byTopic := map[string]metricdata.DataPoint[int64]{}
	for _, dp := range sum.DataPoints {
		topic, _ := dp.Attributes.Value("topic")
		byTopic[topic.AsString()] = dp
	}

	success := byTopic["user.registered"]
	if success.Value != 1 {
		t.Fatalf("user.registered count = %d, want 1", success.Value)
	}
	if v, _ := success.Attributes.Value("success"); !v.AsBool() {
		t.Fatalf("user.registered success attribute = %v, want true", v)
	}

	failure := byTopic["exercise.completed"]
	if failure.Value != 1 {
		t.Fatalf("exercise.completed count = %d, want 1", failure.Value)
	}
	if v, _ := failure.Attributes.Value("success"); v.AsBool() {
		t.Fatalf("exercise.completed success attribute = %v, want false", v)
	}
}
