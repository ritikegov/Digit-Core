package handler

import (
	"encoding/json"
	"errors"
	"net/http"

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
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req model.IdGenerationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, model.ResponseInfo{Status: "FAILED"}, "INVALID_REQUEST", err.Error())
		return
	}

	if err := validateRequest(req); err != nil {
		writeError(w, http.StatusBadRequest, model.ResponseInfo{Status: "FAILED"}, "INVALID_REQUEST", err.Error())
		return
	}

	resp, err := h.svc.GenerateIdResponse(req)
	if err != nil {
		var ce *model.CustomError
		if errors.As(err, &ce) {
			writeError(w, http.StatusBadRequest, model.NewResponseInfo(req.RequestInfo, false), ce.Code, ce.Message)
			return
		}
		writeError(w, http.StatusInternalServerError, model.NewResponseInfo(req.RequestInfo, false), "INTERNAL_ERROR", err.Error())
		return
	}

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
