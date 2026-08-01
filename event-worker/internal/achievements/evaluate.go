package achievements

// Evaluate returns the codes of every catalog achievement whose kind and
// threshold are newly met by the given current streak / total-completions
// values, excluding any code already present in alreadyUnlocked. It has no
// side effects -- callers are responsible for persisting the result.
func Evaluate(currentStreak, totalCompletions int, alreadyUnlocked map[string]bool) []string {
	var newlyUnlocked []string
	for _, def := range Catalog {
		if alreadyUnlocked[def.Code] {
			continue
		}

		var current int
		switch def.Kind {
		case KindStreak:
			current = currentStreak
		case KindCompletions:
			current = totalCompletions
		default:
			continue
		}

		if current >= def.Threshold {
			newlyUnlocked = append(newlyUnlocked, def.Code)
		}
	}
	return newlyUnlocked
}
