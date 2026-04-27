package service

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"

	"github.com/egovernments/egov-idgen/internal/model"
)

type MdmsService struct {
	host      string
	searchURI string
	client    *http.Client
}

func NewMdmsService(host, searchURI string) *MdmsService {
	return &MdmsService{
		host:      strings.TrimSuffix(host, "/"),
		searchURI: strings.TrimPrefix(searchURI, "/"),
		client:    &http.Client{},
	}
}

type mdmsMasterDetail struct {
	Name   string `json:"name"`
	Filter string `json:"filter,omitempty"`
}

type mdmsModuleDetail struct {
	ModuleName    string             `json:"moduleName"`
	MasterDetails []mdmsMasterDetail `json:"masterDetails"`
}

type mdmsCriteria struct {
	TenantId      string             `json:"tenantId"`
	ModuleDetails []mdmsModuleDetail `json:"moduleDetails"`
}

type mdmsCriteriaReq struct {
	RequestInfo interface{}  `json:"RequestInfo"`
	MdmsCriteria mdmsCriteria `json:"MdmsCriteria"`
}

// GetIdFormatAndCity fetches both the ID format and city code from MDMS in a single call.
func (s *MdmsService) GetIdFormatAndCity(req model.RequestInfo, idReq model.IdRequest) (format, cityCode string, err error) {
	payload := mdmsCriteriaReq{
		RequestInfo: req,
		MdmsCriteria: mdmsCriteria{
			TenantId: idReq.TenantId,
			ModuleDetails: []mdmsModuleDetail{
				{
					ModuleName: "tenant",
					MasterDetails: []mdmsMasterDetail{
						{Name: "tenants", Filter: fmt.Sprintf("[?(@.code=='%s')]", idReq.TenantId)},
					},
				},
				{
					ModuleName: "common-masters",
					MasterDetails: []mdmsMasterDetail{
						{Name: "IdFormat", Filter: fmt.Sprintf("[?(@.idname=='%s')]", idReq.IdName)},
					},
				},
			},
		},
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return "", "", fmt.Errorf("mdms: marshal request: %w", err)
	}

	url := s.host + "/" + s.searchURI
	resp, err := s.client.Post(url, "application/json", bytes.NewReader(body))
	if err != nil {
		return "", "", fmt.Errorf("mdms: http post: %w", err)
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", "", fmt.Errorf("mdms: read response: %w", err)
	}

	var result struct {
		MdmsRes map[string]map[string][]map[string]interface{} `json:"MdmsRes"`
	}
	if err := json.Unmarshal(data, &result); err != nil {
		return "", "", fmt.Errorf("mdms: parse response: %w", err)
	}

	if tenantModule, ok := result.MdmsRes["tenant"]; ok {
		if tenants, ok := tenantModule["tenants"]; ok && len(tenants) > 0 {
			if city, ok := tenants[0]["city"].(map[string]interface{}); ok {
				if code, ok := city["code"].(string); ok {
					cityCode = code
				}
			}
		}
	}

	if formatModule, ok := result.MdmsRes["common-masters"]; ok {
		if formats, ok := formatModule["IdFormat"]; ok && len(formats) > 0 {
			if f, ok := formats[0]["format"].(string); ok {
				format = f
			}
		}
	}

	return format, cityCode, nil
}
