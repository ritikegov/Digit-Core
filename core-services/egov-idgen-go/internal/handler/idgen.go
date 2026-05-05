package handler

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"time"

	"github.com/egovernments/egov-idgen/internal/model"
	"github.com/egovernments/egov-idgen/internal/service"
)

type IdGenHandler struct {
	svc *service.IdGenService
}

func NewIdGenHandler(svc *service.IdGenService) *IdGenHandler {
	return &IdGenHandler{svc: svc}
}

func (h *IdGenHandler) RegisterRoutes(mux *http.ServeMux, contextPath string) {
	mux.HandleFunc(contextPath+"/id/_generate", h.generate)
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
}

func (h *IdGenHandler) generate(w http.ResponseWriter, r *http.Request) {
	start := time.Now()

	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req model.IdGenerationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		log.Printf("ERROR decode request: %v | duration=%s", err, time.Since(start))
		writeError(w, http.StatusBadRequest, model.ResponseInfo{Status: "FAILED"}, "INVALID_REQUEST", err.Error())
		return
	}

	log.Printf("INFO  _generate start | tenantId=%s idName=%s count=%v",
		firstTenantId(req), firstIdName(req), firstCount(req))

	if err := validateRequest(req); err != nil {
		log.Printf("ERROR validation: %v | duration=%s", err, time.Since(start))
		writeError(w, http.StatusBadRequest, model.ResponseInfo{Status: "FAILED"}, "INVALID_REQUEST", err.Error())
		return
	}

	// Time the service call separately to isolate DB vs MDMS vs handler overhead
	svcStart := time.Now()
	resp, err := h.svc.GenerateIdResponse(req)
	svcDuration := time.Since(svcStart)

	if err != nil {
		var ce *model.CustomError
		if errors.As(err, &ce) {
			log.Printf("ERROR [%s] %s | svc=%s total=%s", ce.Code, ce.Message, svcDuration, time.Since(start))
			writeError(w, http.StatusBadRequest, model.NewResponseInfo(req.RequestInfo, false), ce.Code, ce.Message)
			return
		}
		log.Printf("ERROR internal: %v | svc=%s total=%s", err, svcDuration, time.Since(start))
		writeError(w, http.StatusInternalServerError, model.NewResponseInfo(req.RequestInfo, false), "INTERNAL_ERROR", err.Error())
		return
	}

	log.Printf("INFO  _generate done | ids=%d svc=%s total=%s",
		len(resp.IdResponses), svcDuration, time.Since(start))
	writeJSON(w, http.StatusOK, resp)
}

func validateRequest(req model.IdGenerationRequest) error {
	if req.RequestInfo.ApiId == "" {
		return errors.New("RequestInfo.apiId is required")
	}
	if len(req.IdRequests) == 0 {
		return errors.New("idRequests must not be empty")
	}
	for _, r := range req.IdRequests {
		if r.TenantId == "" {
			return errors.New("idRequests[].tenantId is required")
		}
	}
	return nil
}

func writeError(w http.ResponseWriter, status int, respInfo model.ResponseInfo, code, message string) {
	writeJSON(w, status, model.ErrorResponse{
		ResponseInfo: respInfo,
		Errors: []model.ErrorDetail{
			{Code: code, Message: message},
		},
	})
}

func writeJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func firstTenantId(req model.IdGenerationRequest) string {
	if len(req.IdRequests) > 0 {
		return req.IdRequests[0].TenantId
	}
	return ""
}

func firstIdName(req model.IdGenerationRequest) string {
	if len(req.IdRequests) > 0 {
		return req.IdRequests[0].IdName
	}
	return ""
}

func firstCount(req model.IdGenerationRequest) interface{} {
	if len(req.IdRequests) > 0 && req.IdRequests[0].Count != nil {
		return *req.IdRequests[0].Count
	}
	return 1
}
