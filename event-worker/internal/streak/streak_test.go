package streak

import (
	"testing"
	"time"
)

func day(offset int) time.Time {
	base := time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC)
	return base.AddDate(0, 0, offset)
}

func TestNext_NoPriorActivityStartsStreakAtOne(t *testing.T) {
	got := Next(0, nil, day(0))
	if got != 1 {
		t.Fatalf("Next() = %d, want 1", got)
	}
}

func TestNext_SameDayIsANoOp(t *testing.T) {
	last := day(0)
	got := Next(5, &last, day(0))
	if got != 5 {
		t.Fatalf("Next() = %d, want 5 (unchanged)", got)
	}
}

func TestNext_ConsecutiveDayIncrements(t *testing.T) {
	last := day(0)
	got := Next(5, &last, day(1))
	if got != 6 {
		t.Fatalf("Next() = %d, want 6", got)
	}
}

func TestNext_GapResetsToOne(t *testing.T) {
	last := day(0)
	got := Next(5, &last, day(3))
	if got != 1 {
		t.Fatalf("Next() = %d, want 1 (reset)", got)
	}
}
