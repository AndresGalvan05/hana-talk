package db

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/golang-migrate/migrate/v4"
	"github.com/golang-migrate/migrate/v4/database/postgres"
	"github.com/golang-migrate/migrate/v4/source/iofs"
	"github.com/jackc/pgx/v5/pgxpool"
	_ "github.com/jackc/pgx/v5/stdlib"

	"github.com/AndresGalvan05/hana-talk/event-worker/internal/migrations"
)

func Connect(ctx context.Context, dbURL string) (*pgxpool.Pool, error) {
	pool, err := pgxpool.New(ctx, dbURL)
	if err != nil {
		return nil, fmt.Errorf("connect: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ping: %w", err)
	}
	return pool, nil
}

// Migrate applies embedded migrations against the event_worker schema,
// entirely independent of core-api's Flyway migration history.
func Migrate(dbURL string) error {
	sourceDriver, err := iofs.New(migrations.FS, ".")
	if err != nil {
		return fmt.Errorf("migration source: %w", err)
	}

	sqlDB, err := sql.Open("pgx", dbURL)
	if err != nil {
		return fmt.Errorf("open migration connection: %w", err)
	}
	defer sqlDB.Close()

	// golang-migrate's postgres driver expects the target schema to already
	// exist when SchemaName is set (it does not create it itself) -- create
	// it up front so the driver's own migrations-tracking table can live in
	// event_worker too, not just the tables our own migration files create.
	if _, err := sqlDB.Exec(`CREATE SCHEMA IF NOT EXISTS event_worker`); err != nil {
		return fmt.Errorf("create event_worker schema: %w", err)
	}

	dbDriver, err := postgres.WithInstance(sqlDB, &postgres.Config{
		SchemaName:      "event_worker",
		MigrationsTable: "schema_migrations",
	})
	if err != nil {
		return fmt.Errorf("migration driver: %w", err)
	}

	m, err := migrate.NewWithInstance("iofs", sourceDriver, "postgres", dbDriver)
	if err != nil {
		return fmt.Errorf("migrate instance: %w", err)
	}

	if err := m.Up(); err != nil && err != migrate.ErrNoChange {
		return fmt.Errorf("migrate up: %w", err)
	}
	return nil
}
