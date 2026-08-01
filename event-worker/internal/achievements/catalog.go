// Package achievements contains the fixed achievement catalog and the pure
// threshold-crossing rule, kept separate from any database access so it's
// trivially unit-testable (mirroring the internal/streak package).
package achievements

// Kind identifies which per-user metric an achievement's threshold applies
// to.
const (
	KindStreak      = "streak"
	KindCompletions = "completions"
)

// Definition describes one achievement in the fixed catalog.
type Definition struct {
	Code        string
	Kind        string
	Threshold   int
	Title       string
	Description string
}

// Catalog is the complete, fixed set of achievements the app awards.
// Changing it requires a deploy, not a data edit -- see the
// achievement-system design doc for why that's an accepted trade-off.
var Catalog = []Definition{
	{
		Code:        "STREAK_3",
		Kind:        KindStreak,
		Threshold:   3,
		Title:       "3-Day Streak",
		Description: "Practiced three days in a row.",
	},
	{
		Code:        "STREAK_7",
		Kind:        KindStreak,
		Threshold:   7,
		Title:       "7-Day Streak",
		Description: "Practiced seven days in a row.",
	},
	{
		Code:        "STREAK_30",
		Kind:        KindStreak,
		Threshold:   30,
		Title:       "30-Day Streak",
		Description: "Practiced thirty days in a row.",
	},
	{
		Code:        "LESSONS_1",
		Kind:        KindCompletions,
		Threshold:   1,
		Title:       "First Steps",
		Description: "Completed your first lesson.",
	},
	{
		Code:        "LESSONS_3",
		Kind:        KindCompletions,
		Threshold:   3,
		Title:       "Halfway There",
		Description: "Completed three lessons.",
	},
	{
		Code:        "LESSONS_5",
		Kind:        KindCompletions,
		Threshold:   5,
		Title:       "Course Complete",
		Description: "Completed every lesson in the course.",
	},
}
