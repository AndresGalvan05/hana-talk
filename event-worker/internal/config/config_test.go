package config

import (
	"testing"
)

func TestBuildDBURL(t *testing.T) {
	t.Run("no DB_USERNAME set returns DB_URL unchanged", func(t *testing.T) {
		t.Setenv("DB_URL", "postgres://hanatalk:hanatalk@localhost:5432/hanatalk")
		t.Setenv("DB_USERNAME", "")
		t.Setenv("DB_PASSWORD", "")

		got := buildDBURL()
		want := "postgres://hanatalk:hanatalk@localhost:5432/hanatalk"
		if got != want {
			t.Errorf("got %q, want %q", got, want)
		}
	})

	t.Run("percent-encodes URL-meaningful characters in the password", func(t *testing.T) {
		t.Setenv("DB_URL", "postgres://postgres:5432/hanatalk")
		t.Setenv("DB_USERNAME", "hanatalk")
		t.Setenv("DB_PASSWORD", "p@ss:word/with?special#chars")

		got := buildDBURL()
		want := "postgres://hanatalk:p%40ss%3Aword%2Fwith%3Fspecial%23chars@postgres:5432/hanatalk"
		if got != want {
			t.Errorf("got %q, want %q", got, want)
		}
	})
}
