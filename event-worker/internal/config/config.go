package config

import "os"

type Config struct {
	KafkaBootstrapServers string
	DBURL                 string
	InternalAPIPort       string
}

func Load() Config {
	return Config{
		KafkaBootstrapServers: getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
		DBURL:                 getEnv("DB_URL", "postgres://hanatalk:hanatalk@localhost:5432/hanatalk"),
		InternalAPIPort:       getEnv("INTERNAL_API_PORT", "8090"),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
