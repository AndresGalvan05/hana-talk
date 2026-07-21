package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"

	"github.com/AndresGalvan05/hana-talk/event-worker/internal/api"
	"github.com/AndresGalvan05/hana-talk/event-worker/internal/config"
	"github.com/AndresGalvan05/hana-talk/event-worker/internal/consumer"
	"github.com/AndresGalvan05/hana-talk/event-worker/internal/db"
	"github.com/AndresGalvan05/hana-talk/event-worker/internal/store"
	"github.com/AndresGalvan05/hana-talk/event-worker/internal/tracing"
)

func main() {
	if err := run(); err != nil {
		slog.Error("event-worker exited", "error", err)
		os.Exit(1)
	}
}

func run() error {
	cfg := config.Load()

	if err := db.Migrate(cfg.DBURL); err != nil {
		return err
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	shutdownTracing, err := tracing.Setup(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = shutdownTracing(context.Background()) }()

	pool, err := db.Connect(ctx, cfg.DBURL)
	if err != nil {
		return err
	}
	defer pool.Close()

	s := store.New(pool)
	brokers := strings.Split(cfg.KafkaBootstrapServers, ",")

	go func() {
		if err := consumer.Run(ctx, brokers, s); err != nil && ctx.Err() == nil {
			slog.Error("consumer stopped", "error", err)
		}
	}()

	server := &http.Server{
		Addr:    ":" + cfg.InternalAPIPort,
		Handler: otelhttp.NewHandler(api.NewServer(s), "event-worker"),
	}

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = server.Shutdown(shutdownCtx)
	}()

	slog.Info("event-worker listening", "port", cfg.InternalAPIPort)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		return err
	}
	return nil
}
