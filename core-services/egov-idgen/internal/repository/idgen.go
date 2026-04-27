package repository

import (
	"database/sql"
	"fmt"

	_ "github.com/lib/pq"
)

type IdGenRepository struct {
	db *sql.DB
}

func NewIdGenRepository(db *sql.DB) *IdGenRepository {
	return &IdGenRepository{db: db}
}

func (r *IdGenRepository) GetFormatFromDB(idName, tenantId string) (string, error) {
	var format string
	err := r.db.QueryRow(
		"SELECT format FROM id_generator WHERE idname=$1 AND tenantid=$2",
		idName, tenantId,
	).Scan(&format)
	if err == sql.ErrNoRows {
		err = r.db.QueryRow(
			"SELECT format FROM id_generator WHERE idname=$1",
			idName,
		).Scan(&format)
		if err == sql.ErrNoRows {
			return "", nil
		}
		return format, err
	}
	return format, err
}

func (r *IdGenRepository) GenerateSequenceNumbers(seqName string, count int) ([]string, error) {
	if !isValidIdentifier(seqName) {
		return nil, fmt.Errorf("invalid sequence name: %s", seqName)
	}
	query := fmt.Sprintf("SELECT NEXTVAL('%s') FROM GENERATE_SERIES(1,$1)", seqName)
	rows, err := r.db.Query(query, count)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var results []string
	for rows.Next() {
		var val int64
		if err := rows.Scan(&val); err != nil {
			return nil, err
		}
		results = append(results, fmt.Sprintf("%06d", val))
	}
	return results, rows.Err()
}

func (r *IdGenRepository) CreateSequence(seqName string) error {
	if !isValidIdentifier(seqName) {
		return fmt.Errorf("invalid sequence name: %s", seqName)
	}
	_, err := r.db.Exec(fmt.Sprintf("CREATE SEQUENCE %s", seqName))
	return err
}

// isValidIdentifier guards against SQL injection in sequence names.
// Sequence names may only contain alphanumeric characters and underscores.
func isValidIdentifier(name string) bool {
	if len(name) == 0 || len(name) > 63 {
		return false
	}
	for _, c := range name {
		if !((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
			return false
		}
	}
	return true
}
