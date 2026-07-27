package config

import (
	"net/url"
	"os"
)

type Config struct {
	KafkaBootstrapServers string
	DBURL                 string
	InternalAPIPort       string
}

func Load() Config {
	return Config{
		KafkaBootstrapServers: getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
		DBURL:                 buildDBURL(),
		InternalAPIPort:       getEnv("INTERNAL_API_PORT", "8090"),
	}
}

// buildDBURL combines DB_URL (host/port/dbname, no credentials) with
// DB_USERNAME/DB_PASSWORD via net/url so special characters in the password
// get percent-encoded correctly. Prod's k8s deployment injects the two
// separately (sourced from a Secret via $(VAR) plain-text expansion, which
// can't escape anything) -- raw string interpolation there broke once
// already when a generated password happened to contain a character with
// URL meaning. Local compose still passes credentials embedded directly in
// DB_URL and leaves DB_USERNAME/DB_PASSWORD unset, so this is a no-op there.
func buildDBURL() string {
	base := getEnv("DB_URL", "postgres://hanatalk:hanatalk@localhost:5432/hanatalk")
	user := os.Getenv("DB_USERNAME")
	if user == "" {
		return base
	}
	u, err := url.Parse(base)
	if err != nil {
		return base
	}
	if pass := os.Getenv("DB_PASSWORD"); pass != "" {
		u.User = url.UserPassword(user, pass)
	} else {
		u.User = url.User(user)
	}
	return u.String()
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
