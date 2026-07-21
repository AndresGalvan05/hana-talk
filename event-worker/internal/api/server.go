// Package api is event-worker's small internal read API -- never exposed
// to the frontend directly, only called by core-api.
package api

import (
	"context"
	"encoding/json"
	"net/http"

	"github.com/AndresGalvan05/hana-talk/event-worker/internal/store"
)

const defaultLeaderboardLimit = 20

// Store is the subset of store.Store this package depends on, so handlers
// can be tested against a fake without a real Postgres.
type Store interface {
	GetStreak(ctx context.Context, userID string) (store.StreakInfo, error)
	GetLeaderboard(ctx context.Context, limit int) ([]store.LeaderboardEntry, error)
}

func NewServer(s Store) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /leaderboard", leaderboardHandler(s))
	mux.HandleFunc("GET /users/{userId}/streak", streakHandler(s))
	return mux
}

type streakResponse struct {
	UserID         string  `json:"userId"`
	CurrentStreak  int     `json:"currentStreak"`
	LastActiveDate *string `json:"lastActiveDate"`
}

func streakHandler(s Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID := r.PathValue("userId")
		info, err := s.GetStreak(r.Context(), userID)
		if err != nil {
			http.Error(w, "failed to load streak", http.StatusInternalServerError)
			return
		}

		resp := streakResponse{UserID: info.UserID, CurrentStreak: info.CurrentStreak}
		if info.LastActiveDate != nil {
			formatted := info.LastActiveDate.Format("2006-01-02")
			resp.LastActiveDate = &formatted
		}
		writeJSON(w, resp)
	}
}

type leaderboardEntryResponse struct {
	UserID        string `json:"userId"`
	Username      string `json:"username"`
	CurrentStreak int    `json:"currentStreak"`
}

func leaderboardHandler(s Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		entries, err := s.GetLeaderboard(r.Context(), defaultLeaderboardLimit)
		if err != nil {
			http.Error(w, "failed to load leaderboard", http.StatusInternalServerError)
			return
		}

		resp := make([]leaderboardEntryResponse, 0, len(entries))
		for _, e := range entries {
			resp = append(resp, leaderboardEntryResponse{
				UserID:        e.UserID,
				Username:      e.Username,
				CurrentStreak: e.CurrentStreak,
			})
		}
		writeJSON(w, resp)
	}
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}
