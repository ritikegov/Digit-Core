package config

import (
	"os"
	"strconv"
)

type Config struct {
	DBHost     string
	DBPort     string
	DBName     string
	DBUser     string
	DBPassword string
	DBSSLMode  string

	MdmsServiceHost string
	MdmsSearchURI   string

	IdFormatFromMDMS bool
	AutoCreateNewSeq bool

	TimeZone    string
	ServerPort  string
	ContextPath string
}

func Load() *Config {
	return &Config{
		DBHost:           getEnv("DB_HOST", "localhost"),
		DBPort:           getEnv("DB_PORT", "5432"),
		DBName:           getEnv("DB_NAME", "rainmaker_new"),
		DBUser:           getEnv("DB_USER", "postgres"),
		DBPassword:       getEnv("DB_PASSWORD", "postgres"),
		DBSSLMode:        getEnv("DB_SSL_MODE", "disable"),
		MdmsServiceHost:  getEnv("MDMS_SERVICE_HOST", "http://localhost:8280/"),
		MdmsSearchURI:    getEnv("MDMS_SERVICE_SEARCH_URI", "egov-mdms-service/v1/_search"),
		IdFormatFromMDMS: getBoolEnv("IDFORMAT_FROM_MDMS", true),
		AutoCreateNewSeq: getBoolEnv("AUTOCREATE_NEW_SEQ", true),
		TimeZone:         getEnv("ID_TIMEZONE", "IST"),
		ServerPort:       getEnv("SERVER_PORT", "8088"),
		ContextPath:      getEnv("SERVER_CONTEXT_PATH", "/egov-idgen"),
	}
}

func (c *Config) DSN() string {
	return "host=" + c.DBHost +
		" port=" + c.DBPort +
		" dbname=" + c.DBName +
		" user=" + c.DBUser +
		" password=" + c.DBPassword +
		" sslmode=" + c.DBSSLMode
}

func getEnv(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}

func getBoolEnv(key string, defaultVal bool) bool {
	val := os.Getenv(key)
	if val == "" {
		return defaultVal
	}
	b, err := strconv.ParseBool(val)
	if err != nil {
		return defaultVal
	}
	return b
}
