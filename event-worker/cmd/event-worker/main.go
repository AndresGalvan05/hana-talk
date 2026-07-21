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

	"github.com/AndresGalvan05/hana-talk/event-worker/internal/api"
	"github.com/AndresGalvan05/hana-talk/event-worker/internal/config"
	"github.com/AndresGalvan05/hana-talk/event-worker/internal/consumer"
	"github.com/AndresGalvan05/hana-talk/event-worker/internal/db"
	"github.com/AndresGalvan05/hana-talk/event-worker/internal/store"
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
		Handler: api.NewServer(s),
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
