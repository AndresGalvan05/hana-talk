// Package consumer wires two Kafka topic readers into a single
// DB-writing goroutine: reads happen concurrently, writes are serialized
// without a mutex, and each reader only commits an offset once the write
// it depends on has actually succeeded.
package consumer

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/segmentio/kafka-go"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"

	"github.com/AndresGalvan05/hana-talk/event-worker/internal/events"
	"github.com/AndresGalvan05/hana-talk/event-worker/internal/store"
)

const groupID = "event-worker"

var tracer = otel.Tracer("event-worker/consumer")

type job struct {
	topic   string
	value   []byte
	headers []kafka.Header
	result  chan error
}

// extractTraceContext builds a context carrying the trace the producer
// (core-api's EventPublisher, with spring.kafka.template.observation-enabled)
// started, by reading the W3C traceparent/tracestate headers off the
// message. There's no off-the-shelf kafka-go OTel instrumentation as
// standardized as the HTTP ecosystem's, so this is done by hand.
func extractTraceContext(ctx context.Context, headers []kafka.Header) context.Context {
	carrier := propagation.MapCarrier{}
	for _, h := range headers {
		carrier.Set(h.Key, string(h.Value))
	}
	return otel.GetTextMapPropagator().Extract(ctx, carrier)
}

// Run blocks until ctx is cancelled, consuming both topics and writing
// through s. Kafka delivery is at-least-once; every write s performs is
// idempotent, so reprocessing after a crash or rebalance is always safe.
func Run(ctx context.Context, brokers []string, s *store.Store) error {
	jobs := make(chan job)
	defer close(jobs)

	go processJobs(ctx, jobs, s)

	errCh := make(chan error, 2)
	go func() { errCh <- consumeTopic(ctx, brokers, events.TopicUserRegistered, jobs) }()
	go func() { errCh <- consumeTopic(ctx, brokers, events.TopicExerciseCompleted, jobs) }()

	select {
	case err := <-errCh:
		return err
	case <-ctx.Done():
		return ctx.Err()
	}
}

func consumeTopic(ctx context.Context, brokers []string, topic string, jobs chan<- job) error {
	slog.Info("starting consumer", "topic", topic, "brokers", brokers)
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers: brokers,
		Topic:   topic,
		GroupID: groupID,
	})
	defer reader.Close()

	for {
		msg, err := reader.FetchMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return fmt.Errorf("fetch from %s: %w", topic, err)
		}
		slog.Debug("fetched message", "topic", topic, "offset", msg.Offset)

		result := make(chan error, 1)
		jobs <- job{topic: topic, value: msg.Value, headers: msg.Headers, result: result}

		if err := <-result; err != nil {
			slog.Error("processing failed, will redeliver", "topic", topic, "error", err)
			continue
		}
		if err := reader.CommitMessages(ctx, msg); err != nil {
			slog.Error("commit failed", "topic", topic, "error", err)
		}
	}
}

func processJobs(ctx context.Context, jobs <-chan job, s *store.Store) {
	for j := range jobs {
		msgCtx := extractTraceContext(ctx, j.headers)
		msgCtx, span := tracer.Start(msgCtx, "consume "+j.topic)
		start := time.Now()
		err := handle(msgCtx, j.topic, j.value, s)
		recordProcessed(msgCtx, j.topic, time.Since(start).Seconds(), err == nil)
		if err != nil {
			span.RecordError(err)
		}
		span.End()
		j.result <- err
	}
}

func handle(ctx context.Context, topic string, value []byte, s *store.Store) error {
	switch topic {
	case events.TopicUserRegistered:
		var e events.UserRegistered
		if err := json.Unmarshal(value, &e); err != nil {
			return fmt.Errorf("decode user.registered: %w", err)
		}
		return s.UpsertUser(ctx, e.UserID, e.Username)

	case events.TopicExerciseCompleted:
		var e events.ExerciseCompleted
		if err := json.Unmarshal(value, &e); err != nil {
			return fmt.Errorf("decode exercise.completed: %w", err)
		}
		occurredAt, err := time.Parse(time.RFC3339, e.OccurredAt)
		if err != nil {
			return fmt.Errorf("parse occurredAt: %w", err)
		}
		activityDate := time.Date(occurredAt.Year(), occurredAt.Month(), occurredAt.Day(), 0, 0, 0, 0, time.UTC)
		_, err = s.RecordExerciseCompletion(ctx, e.UserID, e.LessonID, activityDate)
		return err

	default:
		return fmt.Errorf("unknown topic: %s", topic)
	}
}
