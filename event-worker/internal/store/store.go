// Package store is the only place that talks to Postgres. It owns the
// event_worker schema exclusively -- no queries here ever touch core-api's
// tables.
package store

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/AndresGalvan05/hana-talk/event-worker/internal/streak"
)

type Store struct {
	pool *pgxpool.Pool
}

func New(pool *pgxpool.Pool) *Store {
	return &Store{pool: pool}
}

type StreakInfo struct {
	UserID         string
	CurrentStreak  int
	LastActiveDate *time.Time
}

type LeaderboardEntry struct {
	UserID        string
	Username      string
	CurrentStreak int
}

// UpsertUser records or refreshes a user's identity from a user.registered
// event. Redelivery of the same event is a harmless no-op update.
func (s *Store) UpsertUser(ctx context.Context, userID, username string) error {
	_, err := s.pool.Exec(ctx, `
		INSERT INTO event_worker.users (user_id, username)
		VALUES ($1, $2)
		ON CONFLICT (user_id) DO UPDATE SET username = EXCLUDED.username
	`, userID, username)
	if err != nil {
		return fmt.Errorf("upsert user: %w", err)
	}
	return nil
}

// RecordActivityAndUpdateStreak records one activity day for a user and, if
// that day is genuinely new (not a same-day or redelivered duplicate),
// recomputes their current streak. It is safe to call repeatedly with the
// same (userID, date) pair.
func (s *Store) RecordActivityAndUpdateStreak(ctx context.Context, userID string, date time.Time) error {
	return pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `
			INSERT INTO event_worker.daily_activity (user_id, activity_date)
			VALUES ($1, $2)
			ON CONFLICT (user_id, activity_date) DO NOTHING
		`, userID, date)
		if err != nil {
			return fmt.Errorf("record activity: %w", err)
		}
		if tag.RowsAffected() == 0 {
			// Already recorded today (or a redelivered event) -- nothing new
			// to fold into the streak.
			return nil
		}

		var currentStreak int
		var lastActiveDate *time.Time
		err = tx.QueryRow(ctx, `
			SELECT current_streak, last_active_date
			FROM event_worker.user_streaks
			WHERE user_id = $1
		`, userID).Scan(&currentStreak, &lastActiveDate)
		if err != nil && !errors.Is(err, pgx.ErrNoRows) {
			return fmt.Errorf("read streak: %w", err)
		}

		newStreak := streak.Next(currentStreak, lastActiveDate, date)

		_, err = tx.Exec(ctx, `
			INSERT INTO event_worker.user_streaks (user_id, current_streak, last_active_date)
			VALUES ($1, $2, $3)
			ON CONFLICT (user_id) DO UPDATE
				SET current_streak = EXCLUDED.current_streak,
				    last_active_date = EXCLUDED.last_active_date
		`, userID, newStreak, date)
		if err != nil {
			return fmt.Errorf("update streak: %w", err)
		}
		return nil
	})
}

// GetStreak returns a user's current streak, or a zero streak (not an
// error) for a user with no recorded activity yet.
func (s *Store) GetStreak(ctx context.Context, userID string) (StreakInfo, error) {
	var info StreakInfo
	info.UserID = userID
	err := s.pool.QueryRow(ctx, `
		SELECT current_streak, last_active_date
		FROM event_worker.user_streaks
		WHERE user_id = $1
	`, userID).Scan(&info.CurrentStreak, &info.LastActiveDate)
	if errors.Is(err, pgx.ErrNoRows) {
		return info, nil
	}
	if err != nil {
		return StreakInfo{}, fmt.Errorf("get streak: %w", err)
	}
	return info, nil
}

// GetLeaderboard returns up to limit users ranked by current streak,
// highest first, joined against event-worker's own users projection.
func (s *Store) GetLeaderboard(ctx context.Context, limit int) ([]LeaderboardEntry, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT s.user_id, COALESCE(u.username, ''), s.current_streak
		FROM event_worker.user_streaks s
		LEFT JOIN event_worker.users u ON u.user_id = s.user_id
		ORDER BY s.current_streak DESC, s.last_active_date DESC
		LIMIT $1
	`, limit)
	if err != nil {
		return nil, fmt.Errorf("get leaderboard: %w", err)
	}
	defer rows.Close()

	var entries []LeaderboardEntry
	for rows.Next() {
		var e LeaderboardEntry
		if err := rows.Scan(&e.UserID, &e.Username, &e.CurrentStreak); err != nil {
			return nil, fmt.Errorf("scan leaderboard row: %w", err)
		}
		entries = append(entries, e)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate leaderboard rows: %w", err)
	}
	return entries, nil
}
