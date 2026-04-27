package model

type CustomError struct {
	Code    string
	Message string
}

func (e *CustomError) Error() string {
	return e.Code + ": " + e.Message
}

func NewCustomError(code, message string) *CustomError {
	return &CustomError{Code: code, Message: message}
}

type ErrorResponse struct {
	ResponseInfo ResponseInfo  `json:"ResponseInfo"`
	Errors       []ErrorDetail `json:"Errors"`
}

type ErrorDetail struct {
	Code        string            `json:"code"`
	Message     string            `json:"message"`
	Description string            `json:"description,omitempty"`
	Params      map[string]string `json:"params,omitempty"`
}
