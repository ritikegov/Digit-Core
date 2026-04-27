package main

import (
	"database/sql"
	"fmt"
	"log"
	"net/http"

	_ "github.com/lib/pq"

	"github.com/egovernments/egov-idgen/internal/config"
	"github.com/egovernments/egov-idgen/internal/handler"
	"github.com/egovernments/egov-idgen/internal/repository"
	"github.com/egovernments/egov-idgen/internal/service"
)

func main() {
	cfg := config.Load()

	db, err := sql.Open("postgres", cfg.DSN())
	if err != nil {
		log.Fatalf("open db: %v", err)
	}
	defer db.Close()

	if err := db.Ping(); err != nil {
		log.Fatalf("ping db: %v", err)
	}

	repo := repository.NewIdGenRepository(db)
	mdmsSvc := service.NewMdmsService(cfg.MdmsServiceHost, cfg.MdmsSearchURI)
	idgenSvc := service.NewIdGenService(repo, mdmsSvc, cfg.IdFormatFromMDMS, cfg.AutoCreateNewSeq, cfg.TimeZone)

	mux := http.NewServeMux()
	h := handler.NewIdGenHandler(idgenSvc)
	h.RegisterRoutes(mux, cfg.ContextPath)

	addr := fmt.Sprintf(":%s", cfg.ServerPort)
	log.Printf("egov-idgen listening on %s (context: %s)", addr, cfg.ContextPath)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("server: %v", err)
	}
}
