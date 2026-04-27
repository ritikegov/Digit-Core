package model

type IdGenerationRequest struct {
	RequestInfo RequestInfo `json:"RequestInfo"`
	IdRequests  []IdRequest `json:"idRequests"`
}

type IdRequest struct {
	IdName   string `json:"idName"`
	TenantId string `json:"tenantId"`
	Format   string `json:"format,omitempty"`
	Count    *int   `json:"count,omitempty"`
}

type RequestInfo struct {
	ApiId         string    `json:"apiId"`
	Ver           string    `json:"ver"`
	Ts            int64     `json:"ts"`
	Action        string    `json:"action"`
	Did           string    `json:"did,omitempty"`
	Key           string    `json:"key,omitempty"`
	MsgId         string    `json:"msgId"`
	RequesterId   string    `json:"requesterId,omitempty"`
	AuthToken     string    `json:"authToken,omitempty"`
	UserInfo      *UserInfo `json:"userInfo,omitempty"`
	CorrelationId string    `json:"correlationId,omitempty"`
}

type UserInfo struct {
	TenantId        string       `json:"tenantId"`
	Id              int          `json:"id,omitempty"`
	Username        string       `json:"username"`
	Mobile          string       `json:"mobile,omitempty"`
	Email           string       `json:"email,omitempty"`
	PrimaryRole     []Role       `json:"primaryrole"`
	AdditionalRoles []TenantRole `json:"additionalroles,omitempty"`
}

type Role struct {
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
}

type TenantRole struct {
	TenantId string `json:"tenantId"`
	Roles    []Role `json:"roles"`
}
