package achievements

import (
	"reflect"
	"sort"
	"testing"
)

func sorted(codes []string) []string {
	out := append([]string(nil), codes...)
	sort.Strings(out)
	return out
}

func TestEvaluate_NothingUnlockedWhenUnderEveryThreshold(t *testing.T) {
	got := Evaluate(0, 0, map[string]bool{})
	if len(got) != 0 {
		t.Fatalf("Evaluate() = %v, want empty", got)
	}
}

func TestEvaluate_StreakThresholdNewlyCrossed(t *testing.T) {
	got := Evaluate(3, 0, map[string]bool{})
	want := []string{"STREAK_3"}
	if !reflect.DeepEqual(sorted(got), sorted(want)) {
		t.Fatalf("Evaluate() = %v, want %v", got, want)
	}
}

func TestEvaluate_CompletionsThresholdNewlyCrossed(t *testing.T) {
	got := Evaluate(0, 1, map[string]bool{})
	want := []string{"LESSONS_1"}
	if !reflect.DeepEqual(sorted(got), sorted(want)) {
		t.Fatalf("Evaluate() = %v, want %v", got, want)
	}
}

func TestEvaluate_AlreadyUnlockedIsNeverReturnedAgain(t *testing.T) {
	got := Evaluate(3, 0, map[string]bool{"STREAK_3": true})
	if len(got) != 0 {
		t.Fatalf("Evaluate() = %v, want empty", got)
	}
}

func TestEvaluate_MultipleThresholdsCrossedAtOnce(t *testing.T) {
	got := Evaluate(7, 3, map[string]bool{})
	want := []string{"STREAK_3", "STREAK_7", "LESSONS_1", "LESSONS_3"}
	if !reflect.DeepEqual(sorted(got), sorted(want)) {
		t.Fatalf("Evaluate() = %v, want %v", got, want)
	}
}

func TestEvaluate_AlreadyUnlockedFilteredOutAmongOthersNewlyCrossed(t *testing.T) {
	got := Evaluate(7, 0, map[string]bool{"STREAK_3": true})
	want := []string{"STREAK_7"}
	if !reflect.DeepEqual(sorted(got), sorted(want)) {
		t.Fatalf("Evaluate() = %v, want %v", got, want)
	}
}
