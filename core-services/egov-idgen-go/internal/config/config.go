package config

import (
	"net/url"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	DBHost        string
	DBPort        string
	DBName        string
	DBUser        string
	DBPassword    string
	DBSSLMode     string
	DBMaxOpenConn int
	DBMaxIdleConn int

	MdmsServiceHost string
	MdmsSearchURI   string

	IdFormatFromMDMS bool
	AutoCreateNewSeq bool

	TimeZone    string
	ServerPort  string
	ContextPath string
}

func Load() *Config {
	cfg := &Config{
		DBUser:        getEnv("SPRING_DATASOURCE_USERNAME", "postgres"),
		DBPassword:    getEnv("SPRING_DATASOURCE_PASSWORD", "postgres"),
		DBSSLMode:     getEnv("DB_SSL_MODE", "require"),
		DBMaxOpenConn: getIntEnv("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", 10),
		DBMaxIdleConn: getIntEnv("SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE", 2),
		MdmsServiceHost:  getEnv("MDMS_SERVICE_HOST", "http://localhost:8280/"),
		MdmsSearchURI:    getEnv("MDMS_SERVICE_SEARCH_URI", "egov-mdms-service/v1/_search"),
		IdFormatFromMDMS: getBoolEnv("IDFORMAT_FROM_MDMS", true),
		AutoCreateNewSeq: getBoolEnv("AUTOCREATE_NEW_SEQ", true),
		TimeZone:         getEnv("ID_TIMEZONE", "IST"),
		ServerPort:       getEnv("SERVER_PORT", "8080"),
		ContextPath:      getEnv("SERVER_CONTEXT_PATH", "/egov-idgen"),
	}

	// Parse SPRING_DATASOURCE_URL (jdbc:postgresql://host:port/dbname) if present
	if jdbcURL := os.Getenv("SPRING_DATASOURCE_URL"); jdbcURL != "" {
		host, port, dbname := parseJDBCURL(jdbcURL)
		cfg.DBHost = host
		cfg.DBPort = port
		cfg.DBName = dbname
	} else {
		cfg.DBHost = getEnv("DB_HOST", "localhost")
		cfg.DBPort = getEnv("DB_PORT", "5432")
		cfg.DBName = getEnv("DB_NAME", "rainmaker_new")
	}

	return cfg
}

// parseJDBCURL extracts host, port, dbname from jdbc:postgresql://host:port/dbname
func parseJDBCURL(jdbcURL string) (host, port, dbname string) {
	// Strip the "jdbc:" prefix so net/url can parse it
	stripped := strings.TrimPrefix(jdbcURL, "jdbc:")
	u, err := url.Parse(stripped)
	if err != nil {
		return "localhost", "5432", "rainmaker_new"
	}
	host = u.Hostname()
	port = u.Port()
	if port == "" {
		port = "5432"
	}
	dbname = strings.TrimPrefix(u.Path, "/")
	return host, port, dbname
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

func getIntEnv(key string, defaultVal int) int {
	val := os.Getenv(key)
	if val == "" {
		return defaultVal
	}
	n, err := strconv.Atoi(val)
	if err != nil {
		return defaultVal
	}
	return n
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
