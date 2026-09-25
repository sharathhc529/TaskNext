package policy

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/open-policy-agent/opa/rego"
	"github.com/sbomguard/backend/pkg/models"
)

// DefaultPolicies contains built-in supply chain security rules
var DefaultPolicies = []models.PolicyRule{
	{
		ID:          "RULE-SEC-01",
		Name:        "No Critical Vulnerabilities Allowed",
		Description: "Blocks release if any component has a CRITICAL severity vulnerability (CVSS >= 9.0).",
		Category:    "security",
		Enabled:     true,
		RegoCode: `package sbom.security.critical_vuln

import rego.v1

default allow := true

# Deny if any component has a CRITICAL vulnerability
deny contains msg if {
    some comp in input.components
    some vuln in comp.vulnerabilities
    vuln.severity == "CRITICAL"
    msg := {
        "componentId": comp.id,
        "componentName": comp.name,
        "componentVersion": comp.version,
        "ruleId": "RULE-SEC-01",
        "severity": "ERROR",
        "message": sprintf("Critical vulnerability found in %v@%v: %v (CVSS %.1f) - %v", [comp.name, comp.version, vuln.id, vuln.cvss, vuln.title]),
        "details": sprintf("Fixed in version: %v | Advisory: %v", [vuln.fixedVersion, vuln.advisoryUrl])
    }
}

allow := count(deny) == 0
`,
	},
	{
		ID:          "RULE-LIC-01",
		Name:        "GPL / Copyleft License Gatekeeper",
		Description: "Enforces license compliance by prohibiting strong copyleft licenses (GPL, AGPL) in proprietary builds.",
		Category:    "license",
		Enabled:     true,
		RegoCode: `package sbom.license.copyleft

import rego.v1

default allow := true

restricted_licenses := ["GPL-3.0-only", "GPL-3.0", "GPL-2.0-only", "GPL-2.0", "AGPL-3.0-or-later", "AGPL-3.0"]

deny contains msg if {
    some comp in input.components
    some restricted in restricted_licenses
    contains(lower(comp.license), lower(restricted))
    msg := {
        "componentId": comp.id,
        "componentName": comp.name,
        "componentVersion": comp.version,
        "ruleId": "RULE-LIC-01",
        "severity": "ERROR",
        "message": sprintf("Prohibited copyleft license detected in %v@%v: License %v", [comp.name, comp.version, comp.license]),
        "details": "Proprietary software distribution cannot bundle GPL/AGPL licensed components without exposing proprietary source."
    }
}

allow := count(deny) == 0
`,
	},
	{
		ID:          "RULE-SEC-02",
		Name:        "High Severity CVE Gate",
		Description: "Warns or fails on High severity vulnerabilities (CVSS >= 7.0) with known fixes available.",
		Category:    "security",
		Enabled:     true,
		RegoCode: `package sbom.security.high_cve

import rego.v1

default allow := true

deny contains msg if {
    some comp in input.components
    some vuln in comp.vulnerabilities
    vuln.severity == "HIGH"
    vuln.fixedVersion != ""
    msg := {
        "componentId": comp.id,
        "componentName": comp.name,
        "componentVersion": comp.version,
        "ruleId": "RULE-SEC-02",
        "severity": "WARNING",
        "message": sprintf("High severity CVE with available fix in %v@%v: %v", [comp.name, comp.version, vuln.id]),
        "details": sprintf("Please upgrade %v to %v or higher to resolve %v.", [comp.name, vuln.fixedVersion, vuln.id])
    }
}

allow := count(deny) == 0
`,
	},
	{
		ID:          "RULE-SUPPLY-01",
		Name:        "Unresolved License Check",
		Description: "Flags components with 'Unknown' or missing license metadata for legal review.",
		Category:    "supply-chain",
		Enabled:     true,
		RegoCode: `package sbom.supplychain.unresolved_license

import rego.v1

default allow := true

deny contains msg if {
    some comp in input.components
    comp.license == "Unknown"
    msg := {
        "componentId": comp.id,
        "componentName": comp.name,
        "componentVersion": comp.version,
        "ruleId": "RULE-SUPPLY-01",
        "severity": "WARNING",
        "message": sprintf("Unknown license metadata for component %v@%v", [comp.name, comp.version]),
        "details": "Legal review required: verify package terms before distribution."
    }
}

allow := count(deny) == 0
`,
	},
}

// Engine wraps OPA evaluation
type Engine struct {
	policies []models.PolicyRule
}

// NewEngine creates an OPA engine with standard policy rules
func NewEngine() *Engine {
	return &Engine{
		policies: DefaultPolicies,
	}
}

// GetPolicies returns all registered policies
func (e *Engine) GetPolicies() []models.PolicyRule {
	return e.policies
}

// UpdatePolicies updates policies in the engine
func (e *Engine) UpdatePolicies(policies []models.PolicyRule) {
	e.policies = policies
}

// EvaluateArtifact evaluates all active OPA Rego policies against an artifact
func (e *Engine) EvaluateArtifact(ctx context.Context, artifact *models.Artifact, customRego string) ([]models.PolicyEvaluationResult, bool, error) {
	// Prepare input JSON
	var inputMap map[string]interface{}
	inputBytes, err := json.Marshal(artifact)
	if err != nil {
		return nil, false, fmt.Errorf("failed to serialize artifact: %w", err)
	}
	if err := json.Unmarshal(inputBytes, &inputMap); err != nil {
		return nil, false, fmt.Errorf("failed to build input map: %w", err)
	}

	var results []models.PolicyEvaluationResult
	overallPassed := true

	// Evaluate standard registered policies
	for _, rule := range e.policies {
		if !rule.Enabled {
			continue
		}

		res, err := evaluateSingleRule(ctx, rule.ID, rule.Name, rule.Description, rule.RegoCode, inputMap)
		if err != nil {
			// Log error and report rule failure
			results = append(results, models.PolicyEvaluationResult{
				RuleID:      rule.ID,
				RuleName:    rule.Name,
				Description: rule.Description,
				Passed:      false,
				Severity:    "ERROR",
				Violations: []models.Violation{
					{
						RuleID:   rule.ID,
						Message:  fmt.Sprintf("Rego Evaluation Error: %v", err),
						Severity: "ERROR",
					},
				},
			})
			overallPassed = false
			continue
		}

		results = append(results, *res)
		if !res.Passed {
			overallPassed = false
		}
	}

	// Evaluate custom user Rego if provided
	if strings.TrimSpace(customRego) != "" {
		customRes, err := evaluateSingleRule(ctx, "CUSTOM-RULE", "User-Defined Custom Rego Policy", "Custom user-supplied security policy", customRego, inputMap)
		if err != nil {
			results = append(results, models.PolicyEvaluationResult{
				RuleID:      "CUSTOM-RULE",
				RuleName:    "Custom User Policy",
				Description: "Evaluated from interactive Policy Studio",
				Passed:      false,
				Severity:    "ERROR",
				Violations: []models.Violation{
					{
						RuleID:   "CUSTOM-RULE",
						Message:  fmt.Sprintf("Custom Rego Syntax/Evaluation Error: %v", err),
						Severity: "ERROR",
					},
				},
			})
			overallPassed = false
		} else {
			results = append(results, *customRes)
			if !customRes.Passed {
				overallPassed = false
			}
		}
	}

	return results, overallPassed, nil
}

func evaluateSingleRule(ctx context.Context, ruleID, ruleName, desc, regoCode string, input map[string]interface{}) (*models.PolicyEvaluationResult, error) {
	// Parse package name from rego code
	pkgName := extractPackageName(regoCode)
	if pkgName == "" {
		pkgName = "sbom.policy"
	}

	r := rego.New(
		rego.Query(fmt.Sprintf("data.%s.deny", pkgName)),
		rego.Module(fmt.Sprintf("%s.rego", ruleID), regoCode),
		rego.Input(input),
	)

	querySet, err := r.PrepareForEval(ctx)
	if err != nil {
		return nil, fmt.Errorf("rego compilation error: %w", err)
	}

	rs, err := querySet.Eval(ctx)
	if err != nil {
		return nil, fmt.Errorf("rego eval error: %w", err)
	}

	var violations []models.Violation
	if len(rs) > 0 && len(rs[0].Expressions) > 0 {
		val := rs[0].Expressions[0].Value
		if valSlice, ok := val.([]interface{}); ok {
			for _, item := range valSlice {
				if itemMap, ok := item.(map[string]interface{}); ok {
					v := models.Violation{
						RuleID: ruleID,
					}
					if cid, ok := itemMap["componentId"].(string); ok {
						v.ComponentID = cid
					}
					if cname, ok := itemMap["componentName"].(string); ok {
						v.ComponentName = cname
					}
					if cver, ok := itemMap["componentVersion"].(string); ok {
						v.ComponentVersion = cver
					}
					if msg, ok := itemMap["message"].(string); ok {
						v.Message = msg
					}
					if sev, ok := itemMap["severity"].(string); ok {
						v.Severity = sev
					}
					if det, ok := itemMap["details"].(string); ok {
						v.Details = det
					}
					violations = append(violations, v)
				}
			}
		}
	}

	passed := len(violations) == 0
	sev := "INFO"
	if !passed {
		sev = "ERROR"
		for _, v := range violations {
			if v.Severity == "ERROR" {
				sev = "ERROR"
				break
			} else if v.Severity == "WARNING" {
				sev = "WARNING"
			}
		}
	}

	return &models.PolicyEvaluationResult{
		RuleID:      ruleID,
		RuleName:    ruleName,
		Description: desc,
		Passed:      passed,
		Severity:    sev,
		Violations:  violations,
	}, nil
}

func extractPackageName(regoCode string) string {
	for _, line := range strings.Split(regoCode, "\n") {
		trimmed := strings.TrimSpace(line)
		if strings.HasPrefix(trimmed, "package ") {
			return strings.TrimSpace(strings.TrimPrefix(trimmed, "package "))
		}
	}
	return ""
}
