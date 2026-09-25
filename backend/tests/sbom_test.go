package tests

import (
	"context"
	"testing"

	"github.com/sbomguard/backend/pkg/graph"
	"github.com/sbomguard/backend/pkg/policy"
	"github.com/sbomguard/backend/pkg/sbom"
	"github.com/sbomguard/backend/pkg/vuln"
)

func TestParsePackageJSON(t *testing.T) {
	pkgJSON := `{
		"name": "test-app",
		"version": "1.0.0",
		"dependencies": {
			"lodash": "4.17.15",
			"express": "4.16.4"
		}
	}`

	artifact, err := sbom.ScanInput("manifest", "package.json", []byte(pkgJSON))
	if err != nil {
		t.Fatalf("ScanInput failed: %v", err)
	}

	if artifact.Name != "test-app" {
		t.Errorf("expected name 'test-app', got %s", artifact.Name)
	}

	if len(artifact.Components) == 0 {
		t.Fatalf("expected components to be parsed, got 0")
	}

	// Test vulnerability enrichment
	vuln.EnrichArtifactWithVulnerabilities(artifact)

	hasVuln := false
	for _, c := range artifact.Components {
		if c.Name == "lodash" && len(c.Vulnerabilities) > 0 {
			hasVuln = true
			break
		}
	}

	if !hasVuln {
		t.Errorf("expected lodash@4.17.15 to have vulnerabilities detected")
	}
}

func TestCycloneDXParsing(t *testing.T) {
	cdxJSON := `{
		"bomFormat": "CycloneDX",
		"specVersion": "1.5",
		"metadata": {
			"component": {
				"name": "demo-portal",
				"version": "2.0.0"
			}
		},
		"components": [
			{
				"name": "log4j-core",
				"version": "2.14.1",
				"purl": "pkg:maven/log4j-core@2.14.1",
				"licenses": [{"license": {"id": "Apache-2.0"}}]
			}
		]
	}`

	artifact, err := sbom.ScanInput("cyclonedx", "bom.json", []byte(cdxJSON))
	if err != nil {
		t.Fatalf("CycloneDX parsing failed: %v", err)
	}

	if artifact.Name != "demo-portal" {
		t.Errorf("expected artifact name demo-portal, got %s", artifact.Name)
	}

	if len(artifact.Components) != 1 {
		t.Fatalf("expected 1 component, got %d", len(artifact.Components))
	}
}

func TestOPAPolicyEvaluation(t *testing.T) {
	engine := policy.NewEngine()
	ctx := context.Background()

	// Clean component
	cleanArtifact, err := sbom.ScanInput("manifest", "package.json", []byte(`{
		"name": "clean-app",
		"dependencies": {
			"uuid": "9.0.1"
		}
	}`))
	if err != nil {
		t.Fatalf("failed to parse: %v", err)
	}
	vuln.EnrichArtifactWithVulnerabilities(cleanArtifact)

	results, passed, err := engine.EvaluateArtifact(ctx, cleanArtifact, "")
	if err != nil {
		t.Fatalf("OPA evaluation failed: %v", err)
	}

	if !passed {
		t.Errorf("expected clean artifact to pass all policies, got fail: %+v", results)
	}

	// Vulnerable component with copyleft license
	badArtifact, err := sbom.ScanInput("manifest", "package.json", []byte(`{
		"name": "vulnerable-app",
		"dependencies": {
			"lodash": "4.17.15",
			"gpl-library": "1.0.0"
		}
	}`))
	if err != nil {
		t.Fatalf("failed to parse: %v", err)
	}
	vuln.EnrichArtifactWithVulnerabilities(badArtifact)

	badResults, badPassed, err := engine.EvaluateArtifact(ctx, badArtifact, "")
	if err != nil {
		t.Fatalf("OPA evaluation failed: %v", err)
	}

	if badPassed {
		t.Errorf("expected vulnerable artifact with GPL to fail OPA gate")
	}

	foundCritRule := false
	foundLicRule := false
	for _, r := range badResults {
		if r.RuleID == "RULE-SEC-01" && !r.Passed {
			foundCritRule = true
		}
		if r.RuleID == "RULE-LIC-01" && !r.Passed {
			foundLicRule = true
		}
	}

	if !foundCritRule {
		t.Errorf("expected critical vuln rule RULE-SEC-01 to fail")
	}
	if !foundLicRule {
		t.Errorf("expected license rule RULE-LIC-01 to fail")
	}
}

func TestGraphGeneration(t *testing.T) {
	memStore := graph.NewMemoryGraphStore()
	artifact, err := sbom.ScanInput("manifest", "package.json", []byte(`{
		"name": "graph-test-app",
		"dependencies": {
			"express": "4.16.4"
		}
	}`))
	if err != nil {
		t.Fatalf("failed to parse: %v", err)
	}
	vuln.EnrichArtifactWithVulnerabilities(artifact)

	err = memStore.SaveArtifactGraph(artifact)
	if err != nil {
		t.Fatalf("failed to save graph: %v", err)
	}

	graphData, err := memStore.GetArtifactGraph(artifact.ID)
	if err != nil {
		t.Fatalf("failed to get graph: %v", err)
	}

	if len(graphData.Nodes) == 0 {
		t.Errorf("expected graph nodes to be populated")
	}
	if len(graphData.Edges) == 0 {
		t.Errorf("expected graph edges to be populated")
	}
}
