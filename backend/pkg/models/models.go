package models

import "time"

// Component represents a software component / package from an SBOM
type Component struct {
	ID              string          `json:"id"`
	Name            string          `json:"name"`
	Version         string          `json:"version"`
	PURL            string          `json:"purl"`
	Ecosystem       string          `json:"ecosystem"`
	License         string          `json:"license"`
	Direct          bool            `json:"direct"`
	Dependencies    []string        `json:"dependencies,omitempty"`
	Vulnerabilities []Vulnerability `json:"vulnerabilities,omitempty"`
	SourceLocation  string          `json:"sourceLocation,omitempty"`
}

// Vulnerability represents a security vulnerability (CVE/GHSA)
type Vulnerability struct {
	ID             string   `json:"id"`
	Title          string   `json:"title"`
	Description    string   `json:"description"`
	Severity       string   `json:"severity"` // CRITICAL, HIGH, MEDIUM, LOW
	CVSS           float64  `json:"cvss"`
	FixedVersion   string   `json:"fixedVersion,omitempty"`
	AdvisoryURL    string   `json:"advisoryUrl,omitempty"`
	PackageName    string   `json:"packageName,omitempty"`
	PackageVersion string   `json:"packageVersion,omitempty"`
	References     []string `json:"references,omitempty"`
}

// Artifact represents the root scanned entity (app, container, repository)
type Artifact struct {
	ID         string       `json:"id"`
	Name       string       `json:"name"`
	Version    string       `json:"version"`
	Type       string       `json:"type"` // "application", "container", "repository"
	Ecosystem  string       `json:"ecosystem"`
	ScannedAt  time.Time    `json:"scannedAt"`
	Components []Component  `json:"components"`
}

// Violation represents an individual policy violation
type Violation struct {
	ComponentID      string `json:"componentId"`
	ComponentName    string `json:"componentName"`
	ComponentVersion string `json:"componentVersion"`
	RuleID           string `json:"ruleId"`
	Message          string `json:"message"`
	Severity         string `json:"severity"` // "ERROR", "WARNING", "INFO"
	Details          string `json:"details,omitempty"`
}

// PolicyEvaluationResult holds the result of evaluating an OPA Rego policy
type PolicyEvaluationResult struct {
	RuleID      string      `json:"ruleId"`
	RuleName    string      `json:"ruleName"`
	Description string      `json:"description"`
	Passed      bool        `json:"passed"`
	Severity    string      `json:"severity"`
	Violations  []Violation `json:"violations"`
}

// PolicyRule is a configurable Rego policy
type PolicyRule struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	RegoCode    string `json:"regoCode"`
	Enabled     bool   `json:"enabled"`
	Category    string `json:"category"` // "security", "license", "supply-chain"
}

// ScanReport represents the final output delivered to developers & CI/CD
type ScanReport struct {
	ScanID                 string                   `json:"scanId"`
	Artifact               Artifact                 `json:"artifact"`
	Timestamp              time.Time                `json:"timestamp"`
	TotalPackages          int                      `json:"totalPackages"`
	DirectDependencies     int                      `json:"directDependencies"`
	TransitiveDependencies int                      `json:"transitiveDependencies"`
	VulnerabilitiesSummary map[string]int           `json:"vulnerabilitiesSummary"`
	LicenseBreakdown       map[string]int           `json:"licenseBreakdown"`
	PolicyResults          []PolicyEvaluationResult `json:"policyResults"`
	PassedGate             bool                     `json:"passedGate"`
	GateDecision           string                   `json:"gateDecision"` // "PASS", "FAIL", "WARN"
	ScanDurationMs         int64                    `json:"scanDurationMs"`
}

// GraphNode for visual frontend & Dgraph representation
type GraphNode struct {
	ID       string                 `json:"id"`
	Label    string                 `json:"label"`
	Type     string                 `json:"type"` // "artifact", "package", "vulnerability", "license"
	Severity string                 `json:"severity,omitempty"`
	Data     map[string]interface{} `json:"data,omitempty"`
}

// GraphEdge connects nodes in the dependency graph
type GraphEdge struct {
	ID       string `json:"id"`
	Source   string `json:"source"`
	Target   string `json:"target"`
	Relation string `json:"relation"` // "DEPENDS_ON", "HAS_VULNERABILITY", "LICENSED_UNDER"
	Direct   bool   `json:"direct,omitempty"`
}

// GraphData returned to the frontend visualization
type GraphData struct {
	Nodes []GraphNode `json:"nodes"`
	Edges []GraphEdge `json:"edges"`
}

// ScanRequest payload
type ScanRequest struct {
	TargetType  string `json:"targetType"` // "manifest", "cyclonedx", "spdx", "sample"
	SampleName  string `json:"sampleName,omitempty"`
	Content     string `json:"content,omitempty"`
	FileName    string `json:"fileName,omitempty"`
	CustomRego  string `json:"customRego,omitempty"`
}
