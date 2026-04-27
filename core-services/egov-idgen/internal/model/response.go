package model

type IdGenerationResponse struct {
	ResponseInfo ResponseInfo `json:"responseInfo"`
	IdResponses  []IdResponse `json:"idResponses"`
}

type IdResponse struct {
	Id string `json:"id"`
}

type ResponseInfo struct {
	ApiId    string `json:"apiId"`
	Ver      string `json:"ver"`
	Ts       int64  `json:"ts"`
	ResMsgId string `json:"resMsgId,omitempty"`
	MsgId    string `json:"msgId,omitempty"`
	Status   string `json:"status"`
}

func NewResponseInfo(req RequestInfo, success bool) ResponseInfo {
	status := "SUCCESSFUL"
	if !success {
		status = "FAILED"
	}
	return ResponseInfo{
		ApiId:    req.ApiId,
		Ver:      req.Ver,
		Ts:       req.Ts,
		ResMsgId: "uief87324",
		MsgId:    req.MsgId,
		Status:   status,
	}
}
