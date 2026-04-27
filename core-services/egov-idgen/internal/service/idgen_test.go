package service

import (
	"testing"
	"time"
)

func TestExtractTokens(t *testing.T) {
	tokens := extractTokens("PB-PT-[fy:yyyy-yy]-[seq_pt_assessment]")
	if len(tokens) != 2 {
		t.Fatalf("expected 2 tokens, got %d: %v", len(tokens), tokens)
	}
	if tokens[0] != "fy:yyyy-yy" {
		t.Errorf("expected fy:yyyy-yy, got %s", tokens[0])
	}
	if tokens[1] != "seq_pt_assessment" {
		t.Errorf("expected seq_pt_assessment, got %s", tokens[1])
	}
}

func TestExtractTokens_Dedup(t *testing.T) {
	tokens := extractTokens("[seq_foo]-[seq_foo]")
	if len(tokens) != 1 {
		t.Errorf("expected deduplication, got %d tokens", len(tokens))
	}
}

func TestGenerateFinancialYear(t *testing.T) {
	result, err := generateFinancialYear("fy:yyyy-yy")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	now := time.Now()
	month := int(now.Month())
	year := now.Year()
	var expectedPre, expectedPost int
	if month > 3 {
		expectedPre = year
		expectedPost = year + 1
	} else {
		expectedPre = year - 1
		expectedPost = year
	}
	expected := formatYear("yyyy", expectedPre) + "-" + formatYear("yy", expectedPost)
	if result != expected {
		t.Errorf("expected %s, got %s", expected, result)
	}
}

func TestGenerateFinancialYear_InvalidFormat(t *testing.T) {
	_, err := generateFinancialYear("fy:yyyy")
	if err == nil {
		t.Error("expected error for format without dash separator")
	}
}

func TestGenerateCurrentYear(t *testing.T) {
	result, err := generateCurrentYear("cy:yyyy", "IST")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	loc, _ := time.LoadLocation("Asia/Kolkata")
	expected := time.Now().In(loc).Format("2006")
	if result != expected {
		t.Errorf("expected %s, got %s", expected, result)
	}
}

func TestFormatYear(t *testing.T) {
	cases := []struct {
		pattern string
		year    int
		want    string
	}{
		{"yyyy", 2024, "2024"},
		{"yy", 2024, "24"},
		{"yyyy", 2000, "2000"},
		{"yy", 2000, "00"},
	}
	for _, c := range cases {
		got := formatYear(c.pattern, c.year)
		if got != c.want {
			t.Errorf("formatYear(%q, %d) = %q, want %q", c.pattern, c.year, got, c.want)
		}
	}
}

func TestGenerateRandomText_DefaultLength(t *testing.T) {
	result := generateRandomText("random")
	if len(result) != 2 {
		t.Errorf("expected length 2, got %d: %s", len(result), result)
	}
}

func TestGenerateRandomText_CustomLength(t *testing.T) {
	result := generateRandomText("random{5}")
	if len(result) != 5 {
		t.Errorf("expected length 5, got %d: %s", len(result), result)
	}
}

func TestJavaToGoDateFormat(t *testing.T) {
	cases := []struct {
		java string
		want string
	}{
		{"yyyy", "2006"},
		{"yy", "06"},
		{"yyyy-MM-dd", "2006-01-02"},
	}
	for _, c := range cases {
		got := javaToGoDateFormat(c.java)
		if got != c.want {
			t.Errorf("javaToGoDateFormat(%q) = %q, want %q", c.java, got, c.want)
		}
	}
}

func TestLocationName(t *testing.T) {
	if locationName("IST") != "Asia/Kolkata" {
		t.Error("IST should map to Asia/Kolkata")
	}
	if locationName("UTC") != "UTC" {
		t.Error("UTC should stay UTC")
	}
}
