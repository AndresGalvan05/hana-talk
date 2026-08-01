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

	"github.com/AndresGalvan05/hana-talk/event-worker/internal/achievements"
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

type AchievementStatus struct {
	Code        string
	Title       string
	Description string
	Unlocked    bool
	UnlockedAt  *time.Time
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

// RecordExerciseCompletion records one activity day and one distinct lesson
// completion for a user, updating the streak and evaluating achievements as
// needed, and returns any achievement codes newly unlocked by this call. It
// is safe to call repeatedly with the same (userID, lessonID, date) tuple.
//
// The day-activity guard and the lesson-completion guard are independent:
// a user completing a second, different lesson on a day that's already
// recorded skips the streak recompute (nothing new for the streak) but
// still counts as a new distinct completion and still gets evaluated for
// completion-count achievements. See the achievement-system design doc for
// why these can't share one guard.
func (s *Store) RecordExerciseCompletion(
	ctx context.Context,
	userID, lessonID string,
	date time.Time,
) ([]string, error) {
	var unlocked []string
	err := pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		currentStreak, err := recordActivityAndUpdateStreak(ctx, tx, userID, date)
		if err != nil {
			return err
		}

		tag, err := tx.Exec(ctx, `
			INSERT INTO event_worker.lesson_completions (user_id, lesson_id, completed_at)
			VALUES ($1, $2, $3)
			ON CONFLICT (user_id, lesson_id) DO NOTHING
		`, userID, lessonID, date)
		if err != nil {
			return fmt.Errorf("record lesson completion: %w", err)
		}
		if tag.RowsAffected() == 0 {
			// Already recorded (or a redelivered event) -- not a new distinct
			// completion, so no achievement re-evaluation is needed.
			return nil
		}

		var totalCompletions int
		err = tx.QueryRow(ctx, `
			SELECT COUNT(*) FROM event_worker.lesson_completions WHERE user_id = $1
		`, userID).Scan(&totalCompletions)
		if err != nil {
			return fmt.Errorf("count completions: %w", err)
		}

		if currentStreak == nil {
			cs, err := readCurrentStreak(ctx, tx, userID)
			if err != nil {
				return err
			}
			currentStreak = &cs
		}

		alreadyUnlocked, err := readUnlockedCodes(ctx, tx, userID)
		if err != nil {
			return err
		}

		newlyUnlocked := achievements.Evaluate(*currentStreak, totalCompletions, alreadyUnlocked)
		for _, code := range newlyUnlocked {
			_, err = tx.Exec(ctx, `
				INSERT INTO event_worker.user_achievements (user_id, achievement_code, unlocked_at)
				VALUES ($1, $2, $3)
				ON CONFLICT (user_id, achievement_code) DO NOTHING
			`, userID, code, date)
			if err != nil {
				return fmt.Errorf("unlock achievement %s: %w", code, err)
			}
		}
		unlocked = newlyUnlocked
		return nil
	})
	if err != nil {
		return nil, err
	}
	return unlocked, nil
}

// recordActivityAndUpdateStreak records one activity day for a user and, if
// that day is genuinely new (not a same-day or redelivered duplicate),
// recomputes and returns their current streak. It returns nil if the day
// was already recorded, since no new streak value was computed.
func recordActivityAndUpdateStreak(ctx context.Context, tx pgx.Tx, userID string, date time.Time) (*int, error) {
	tag, err := tx.Exec(ctx, `
		INSERT INTO event_worker.daily_activity (user_id, activity_date)
		VALUES ($1, $2)
		ON CONFLICT (user_id, activity_date) DO NOTHING
	`, userID, date)
	if err != nil {
		return nil, fmt.Errorf("record activity: %w", err)
	}
	if tag.RowsAffected() == 0 {
		// Already recorded today (or a redelivered event) -- nothing new
		// to fold into the streak.
		return nil, nil
	}

	currentStreak, lastActiveDate, err := readStreakState(ctx, tx, userID)
	if err != nil {
		return nil, err
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
		return nil, fmt.Errorf("update streak: %w", err)
	}
	return &newStreak, nil
}

func readStreakState(ctx context.Context, tx pgx.Tx, userID string) (int, *time.Time, error) {
	var currentStreak int
	var lastActiveDate *time.Time
	err := tx.QueryRow(ctx, `
		SELECT current_streak, last_active_date
		FROM event_worker.user_streaks
		WHERE user_id = $1
	`, userID).Scan(&currentStreak, &lastActiveDate)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return 0, nil, fmt.Errorf("read streak: %w", err)
	}
	return currentStreak, lastActiveDate, nil
}

func readCurrentStreak(ctx context.Context, tx pgx.Tx, userID string) (int, error) {
	currentStreak, _, err := readStreakState(ctx, tx, userID)
	return currentStreak, err
}

func readUnlockedCodes(ctx context.Context, tx pgx.Tx, userID string) (map[string]bool, error) {
	rows, err := tx.Query(ctx, `
		SELECT achievement_code FROM event_worker.user_achievements WHERE user_id = $1
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("read unlocked achievements: %w", err)
	}
	defer rows.Close()

	unlocked := make(map[string]bool)
	for rows.Next() {
		var code string
		if err := rows.Scan(&code); err != nil {
			return nil, fmt.Errorf("scan unlocked achievement: %w", err)
		}
		unlocked[code] = true
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate unlocked achievements: %w", err)
	}
	return unlocked, nil
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

// GetAchievements returns the full achievement catalog for a user, each
// entry annotated with whether that user has unlocked it and when. Users
// with no unlocked achievements still get the full catalog back, all
// locked.
func (s *Store) GetAchievements(ctx context.Context, userID string) ([]AchievementStatus, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT achievement_code, unlocked_at
		FROM event_worker.user_achievements
		WHERE user_id = $1
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("read unlocked achievements: %w", err)
	}
	defer rows.Close()

	unlockedAt := make(map[string]time.Time)
	for rows.Next() {
		var code string
		var t time.Time
		if err := rows.Scan(&code, &t); err != nil {
			return nil, fmt.Errorf("scan unlocked achievement: %w", err)
		}
		unlockedAt[code] = t
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate unlocked achievements: %w", err)
	}

	statuses := make([]AchievementStatus, 0, len(achievements.Catalog))
	for _, def := range achievements.Catalog {
		status := AchievementStatus{
			Code:        def.Code,
			Title:       def.Title,
			Description: def.Description,
		}
		if t, ok := unlockedAt[def.Code]; ok {
			status.Unlocked = true
			tCopy := t
			status.UnlockedAt = &tCopy
		}
		statuses = append(statuses, status)
	}
	return statuses, nil
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
