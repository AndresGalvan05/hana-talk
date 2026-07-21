package api

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/AndresGalvan05/hana-talk/event-worker/internal/store"
)

type fakeStore struct {
	streaks     map[string]store.StreakInfo
	leaderboard []store.LeaderboardEntry
}

func (f *fakeStore) GetStreak(_ context.Context, userID string) (store.StreakInfo, error) {
	if info, ok := f.streaks[userID]; ok {
		return info, nil
	}
	return store.StreakInfo{UserID: userID}, nil
}

func (f *fakeStore) GetLeaderboard(_ context.Context, _ int) ([]store.LeaderboardEntry, error) {
	return f.leaderboard, nil
}

func TestStreakHandler_NoActivityReturnsZeroNotError(t *testing.T) {
	srv := NewServer(&fakeStore{streaks: map[string]store.StreakInfo{}})
	req := httptest.NewRequest(http.MethodGet, "/users/unknown-user/streak", nil)
	rec := httptest.NewRecorder()

	srv.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var resp streakResponse
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if resp.CurrentStreak != 0 || resp.LastActiveDate != nil {
		t.Fatalf("got %+v, want zero streak with nil lastActiveDate", resp)
	}
}

func TestStreakHandler_ReturnsStoredStreak(t *testing.T) {
	last := time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC)
	srv := NewServer(&fakeStore{streaks: map[string]store.StreakInfo{
		"user-1": {UserID: "user-1", CurrentStreak: 5, LastActiveDate: &last},
	}})
	req := httptest.NewRequest(http.MethodGet, "/users/user-1/streak", nil)
	rec := httptest.NewRecorder()

	srv.ServeHTTP(rec, req)

	var resp streakResponse
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if resp.CurrentStreak != 5 || resp.LastActiveDate == nil || *resp.LastActiveDate != "2026-07-20" {
		t.Fatalf("got %+v, want streak 5 on 2026-07-20", resp)
	}
}

func TestLeaderboardHandler_ReturnsRankedEntries(t *testing.T) {
	srv := NewServer(&fakeStore{leaderboard: []store.LeaderboardEntry{
		{UserID: "user-1", Username: "ana", CurrentStreak: 10},
		{UserID: "user-2", Username: "kenji", CurrentStreak: 3},
	}})
	req := httptest.NewRequest(http.MethodGet, "/leaderboard", nil)
	rec := httptest.NewRecorder()

	srv.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var resp []leaderboardEntryResponse
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if len(resp) != 2 || resp[0].Username != "ana" || resp[0].CurrentStreak != 10 {
		t.Fatalf("got %+v, want ana first with streak 10", resp)
	}
}
