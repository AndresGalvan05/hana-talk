// Package metrics sets up the global OTel meter provider. Export is opt-in
// (OTEL_METRICS_ENABLED=true) because local Jaeger only accepts OTLP
// traces, not metrics -- an always-on exporter would error against it.
// When disabled, a no-op provider is installed instead, so meter.RecordX
// calls elsewhere are always safe to make regardless of the flag.
package metrics

import (
	"context"
	"fmt"
	"os"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetrichttp"
	"go.opentelemetry.io/otel/metric/noop"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
)

// Setup installs the global meter provider, returning a shutdown func to
// flush pending metrics on exit.
func Setup(ctx context.Context) (func(context.Context) error, error) {
	if os.Getenv("OTEL_METRICS_ENABLED") != "true" {
		otel.SetMeterProvider(noop.NewMeterProvider())
		return func(context.Context) error { return nil }, nil
	}

	exporter, err := otlpmetrichttp.New(ctx)
	if err != nil {
		return nil, fmt.Errorf("otlp metric exporter: %w", err)
	}

	res, err := resource.New(ctx, resource.WithAttributes(attribute.String("service.name", "event-worker")))
	if err != nil {
		return nil, fmt.Errorf("resource: %w", err)
	}

	provider := sdkmetric.NewMeterProvider(
		sdkmetric.WithReader(sdkmetric.NewPeriodicReader(exporter)),
		sdkmetric.WithResource(res),
	)
	otel.SetMeterProvider(provider)

	return provider.Shutdown, nil
}
