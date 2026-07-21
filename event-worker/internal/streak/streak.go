// Package streak contains the pure streak-transition rule, kept separate
// from any database access so it's trivially unit-testable.
package streak

import "time"

// Next computes the streak that results from a new activity day, given the
// previously stored streak and last-active date. lastActive is nil for a
// user with no prior activity at all.
func Next(currentStreak int, lastActive *time.Time, newDate time.Time) int {
	if lastActive == nil {
		return 1
	}

	gapDays := daysBetween(*lastActive, newDate)
	switch {
	case gapDays == 0:
		return currentStreak
	case gapDays == 1:
		return currentStreak + 1
	default:
		return 1
	}
}

func daysBetween(a, b time.Time) int {
	a = time.Date(a.Year(), a.Month(), a.Day(), 0, 0, 0, 0, time.UTC)
	b = time.Date(b.Year(), b.Month(), b.Day(), 0, 0, 0, 0, time.UTC)
	return int(b.Sub(a).Hours() / 24)
}
