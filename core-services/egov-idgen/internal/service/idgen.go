package service

import (
	"fmt"
	"math/rand"
	"regexp"
	"strings"
	"time"

	"github.com/egovernments/egov-idgen/internal/model"
	"github.com/egovernments/egov-idgen/internal/repository"
)

const defaultCount = 1

var tokenPattern = regexp.MustCompile(`\[(.*?)\]`)
var lengthPattern = regexp.MustCompile(`\{(\d+)\}`)

type IdGenService struct {
	repo             *repository.IdGenRepository
	mdms             *MdmsService
	idFormatFromMDMS bool
	autoCreateNewSeq bool
	timezone         string
}

func NewIdGenService(
	repo *repository.IdGenRepository,
	mdms *MdmsService,
	idFormatFromMDMS bool,
	autoCreateNewSeq bool,
	timezone string,
) *IdGenService {
	return &IdGenService{
		repo:             repo,
		mdms:             mdms,
		idFormatFromMDMS: idFormatFromMDMS,
		autoCreateNewSeq: autoCreateNewSeq,
		timezone:         timezone,
	}
}

func (s *IdGenService) GenerateIdResponse(req model.IdGenerationRequest) (*model.IdGenerationResponse, error) {
	var idResponses []model.IdResponse

	for _, idReq := range req.IdRequests {
		ids, err := s.generateFromRequest(idReq, req.RequestInfo)
		if err != nil {
			return nil, err
		}
		for _, id := range ids {
			idResponses = append(idResponses, model.IdResponse{Id: id})
		}
	}

	return &model.IdGenerationResponse{
		ResponseInfo: model.NewResponseInfo(req.RequestInfo, true),
		IdResponses:  idResponses,
	}, nil
}

func (s *IdGenService) generateFromRequest(idReq model.IdRequest, reqInfo model.RequestInfo) ([]string, error) {
	autoCreate := false

	if idReq.IdName != "" {
		format, cityCode, err := s.resolveFormat(idReq, reqInfo)
		if err != nil {
			return nil, err
		}
		_ = cityCode
		if format != "" {
			idReq.Format = format
			autoCreate = true
		}
	}

	if idReq.Format == "" {
		return nil, model.NewCustomError("ID_NOT_FOUND", "No Format is available in the MDMS for the given name and tenant")
	}

	return s.buildFormattedIds(idReq, reqInfo, autoCreate)
}

func (s *IdGenService) resolveFormat(idReq model.IdRequest, reqInfo model.RequestInfo) (format, cityCode string, err error) {
	if s.idFormatFromMDMS {
		format, cityCode, err = s.mdms.GetIdFormatAndCity(reqInfo, idReq)
		if err != nil {
			return "", "", model.NewCustomError("ID_NOT_FOUND", "No Format is available in the MDMS for the given name and tenant")
		}
		return format, cityCode, nil
	}
	format, err = s.repo.GetFormatFromDB(idReq.IdName, idReq.TenantId)
	if err != nil {
		return "", "", model.NewCustomError("ID_NOT_FOUND", "No Format is available in the MDMS for the given name and tenant")
	}
	return format, "", nil
}

func (s *IdGenService) buildFormattedIds(idReq model.IdRequest, reqInfo model.RequestInfo, autoCreate bool) ([]string, error) {
	format := idReq.Format
	count := defaultCount
	if idReq.Count != nil {
		count = *idReq.Count
	}

	if strings.TrimSpace(format) != "" && idReq.TenantId != "" {
		format = strings.ReplaceAll(format, "[tenantid]", idReq.TenantId)
		format = strings.ReplaceAll(format, "[tenant_id]", strings.ReplaceAll(idReq.TenantId, ".", "_"))
		format = strings.ReplaceAll(format, "[TENANT_ID]", strings.ToUpper(strings.ReplaceAll(idReq.TenantId, ".", "_")))
	}

	tokens := extractTokens(format)

	// Pre-fetch all sequence blocks once (shared across all count iterations)
	sequences := make(map[string][]string)

	// Determine city code lazily — only fetch if a city token exists
	var cityCode string
	var cityFetched bool

	for _, token := range tokens {
		lower := strings.ToLower(token)
		if strings.HasPrefix(lower, "seq") {
			if _, ok := sequences[token]; !ok {
				seqNums, err := s.fetchSequence(token, count, autoCreate)
				if err != nil {
					return nil, err
				}
				sequences[token] = seqNums
			}
		} else if strings.HasPrefix(lower, "city") && !cityFetched {
			_, cc, err := s.mdms.GetIdFormatAndCity(reqInfo, idReq)
			if err != nil {
				return nil, model.NewCustomError("PARSING ERROR", "Failed to get citycode from MDMS")
			}
			cityCode = cc
			cityFetched = true
		}
	}

	var result []string
	for i := 0; i < count; i++ {
		id := format
		for _, token := range tokens {
			lower := strings.ToLower(token)
			var replacement string

			switch {
			case strings.HasPrefix(lower, "seq"):
				seqList := sequences[token]
				if i < len(seqList) {
					replacement = seqList[i]
				}
			case strings.HasPrefix(lower, "fy"):
				r, err := generateFinancialYear(token)
				if err != nil {
					return nil, model.NewCustomError("INVALID_FORMAT", err.Error())
				}
				replacement = r
			case strings.HasPrefix(lower, "cy"):
				r, err := generateCurrentYear(token, s.timezone)
				if err != nil {
					return nil, model.NewCustomError("INVALID_FORMAT", err.Error())
				}
				replacement = r
			case strings.HasPrefix(lower, "city"):
				replacement = cityCode
			default:
				replacement = generateRandomText(token)
			}

			id = strings.Replace(id, "["+token+"]", replacement, 1)
		}
		result = append(result, id)
	}
	return result, nil
}

func (s *IdGenService) fetchSequence(seqName string, count int, autoCreate bool) ([]string, error) {
	nums, err := s.repo.GenerateSequenceNumbers(seqName, count)
	if err != nil {
		if !autoCreate || !s.autoCreateNewSeq {
			return nil, model.NewCustomError("SEQ_DOES_NOT_EXIST", "auto creation of seq is not allowed in DB")
		}
		if createErr := s.repo.CreateSequence(seqName); createErr != nil {
			return nil, model.NewCustomError("ERROR_CREATING_SEQ", "Error occurred while auto creating seq in DB")
		}
		nums, err = s.repo.GenerateSequenceNumbers(seqName, count)
		if err != nil {
			return nil, model.NewCustomError("SEQ_NUMBER_ERROR", "Error retrieving seq number after creation")
		}
	}
	return nums, nil
}

func extractTokens(format string) []string {
	matches := tokenPattern.FindAllStringSubmatch(format, -1)
	seen := make(map[string]bool)
	var tokens []string
	for _, m := range matches {
		token := m[1]
		if !seen[token] {
			seen[token] = true
			tokens = append(tokens, token)
		}
	}
	return tokens
}

// generateFinancialYear returns the financial year string for a format like "fy:yyyy-yy".
// Financial year starts April 1; months 4-12 are the start of the new FY.
func generateFinancialYear(token string) (string, error) {
	idx := strings.Index(token, ":")
	if idx < 0 {
		return "", fmt.Errorf("invalid financial year format: %s", token)
	}
	fmtStr := strings.TrimSpace(token[idx+1:])
	parts := strings.SplitN(fmtStr, "-", 2)
	if len(parts) != 2 {
		return "", fmt.Errorf("financial year format must contain two parts separated by '-': %s", fmtStr)
	}

	now := time.Now()
	month := int(now.Month())
	year := now.Year()

	var preYear, postYear int
	if month > 3 {
		preYear = year
		postYear = year + 1
	} else {
		preYear = year - 1
		postYear = year
	}

	return formatYear(parts[0], preYear) + "-" + formatYear(parts[1], postYear), nil
}

func formatYear(pattern string, year int) string {
	switch strings.TrimSpace(pattern) {
	case "yyyy", "YYYY":
		return fmt.Sprintf("%04d", year)
	case "yy", "YY":
		return fmt.Sprintf("%02d", year%100)
	default:
		return fmt.Sprintf("%d", year)
	}
}

// generateCurrentYear returns a date string for a format like "cy:yyyy" using the current date.
func generateCurrentYear(token, timezone string) (string, error) {
	idx := strings.Index(token, ":")
	if idx < 0 {
		return "", fmt.Errorf("invalid current year format: %s", token)
	}
	javaFmt := strings.TrimSpace(token[idx+1:])
	goFmt := javaToGoDateFormat(javaFmt)

	loc, err := time.LoadLocation(locationName(timezone))
	if err != nil {
		loc = time.UTC
	}
	return time.Now().In(loc).Format(goFmt), nil
}

// javaToGoDateFormat converts common Java SimpleDateFormat patterns to Go time layout strings.
func javaToGoDateFormat(javaFmt string) string {
	r := strings.NewReplacer(
		"yyyy", "2006",
		"YYYY", "2006",
		"yy", "06",
		"YY", "06",
		"MM", "01",
		"dd", "02",
		"HH", "15",
		"mm", "04",
		"ss", "05",
	)
	return r.Replace(javaFmt)
}

// locationName maps common timezone abbreviations to IANA names.
func locationName(tz string) string {
	switch strings.ToUpper(tz) {
	case "IST":
		return "Asia/Kolkata"
	case "UTC":
		return "UTC"
	default:
		return tz
	}
}

// generateRandomText generates a random numeric string of the length specified in {n} within the token.
// Default length is 2 if no {n} is found.
func generateRandomText(token string) string {
	length := 2
	if m := lengthPattern.FindStringSubmatch(token); len(m) == 2 {
		if n := 0; fmt.Sscanf(m[1], "%d", &n) == 1 && n > 0 {
			length = n
		}
	}
	var sb strings.Builder
	for i := 0; i < length; i++ {
		sb.WriteByte(byte('0' + rand.Intn(10)))
	}
	return sb.String()
}
