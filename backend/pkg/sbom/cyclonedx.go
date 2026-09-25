package sbom

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/sbomguard/backend/pkg/models"
)

// CycloneDXSpec minimal struct for parsing CycloneDX JSON format
type CycloneDXSpec struct {
	BOMFormat    string `json:"bomFormat"`
	SpecVersion  string `json:"specVersion"`
	SerialNumber string `json:"serialNumber"`
	Version      int    `json:"version"`
	Metadata     struct {
		Timestamp string `json:"timestamp"`
		Component struct {
			Name    string `json:"name"`
			Version string `json:"version"`
			Type    string `json:"type"`
			PURL    string `json:"purl"`
		} `json:"component"`
	} `json:"metadata"`
	Components []struct {
		Type        string `json:"type"`
		Name        string `json:"name"`
		Version     string `json:"version"`
		PURL        string `json:"purl"`
		Description string `json:"description"`
		Licenses    []struct {
			License struct {
				ID   string `json:"id"`
				Name string `json:"name"`
			} `json:"license"`
			Expression string `json:"expression"`
		} `json:"licenses"`
		Vulnerabilities []struct {
			ID          string `json:"id"`
			Description string `json:"description"`
			Ratings     []struct {
				Severity string  `json:"severity"`
				Score    float64 `json:"score"`
			} `json:"ratings"`
			Affects []struct {
				Ref string `json:"ref"`
			} `json:"affects"`
		} `json:"vulnerabilities"`
	} `json:"components"`
	Dependencies []struct {
		Ref       string   `json:"ref"`
		DependsOn []string `json:"dependsOn"`
	} `json:"dependencies"`
}

// ParseCycloneDX parses CycloneDX JSON string into normalized Artifact
func ParseCycloneDX(data []byte) (*models.Artifact, error) {
	var cdx CycloneDXSpec
	if err := json.Unmarshal(data, &cdx); err != nil {
		return nil, fmt.Errorf("failed to parse CycloneDX JSON: %w", err)
	}

	name := cdx.Metadata.Component.Name
	if name == "" {
		name = "scanned-artifact"
	}
	version := cdx.Metadata.Component.Version
	if version == "" {
		version = "1.0.0"
	}

	artifactType := cdx.Metadata.Component.Type
	if artifactType == "" {
		artifactType = "application"
	}

	artifact := &models.Artifact{
		ID:        fmt.Sprintf("art-%s-%s", name, version),
		Name:      name,
		Version:   version,
		Type:      artifactType,
		ScannedAt: time.Now(),
	}

	// Map direct dependencies lookup if dependency graph is present
	directRefs := make(map[string]bool)
	depMap := make(map[string][]string)
	for _, dep := range cdx.Dependencies {
		depMap[dep.Ref] = dep.DependsOn
	}

	// If root component has dependencies listed, those are direct
	if rootDeps, ok := depMap[cdx.Metadata.Component.PURL]; ok {
		for _, r := range rootDeps {
			directRefs[r] = true
		}
	}

	components := make([]models.Component, 0, len(cdx.Components))
	for _, c := range cdx.Components {
		license := "Unknown"
		if len(c.Licenses) > 0 {
			if c.Licenses[0].License.ID != "" {
				license = c.Licenses[0].License.ID
			} else if c.Licenses[0].License.Name != "" {
				license = c.Licenses[0].License.Name
			} else if c.Licenses[0].Expression != "" {
				license = c.Licenses[0].Expression
			}
		}

		isDirect := true
		if len(directRefs) > 0 {
			isDirect = directRefs[c.PURL] || directRefs[c.Name]
		}

		var vulns []models.Vulnerability
		for _, v := range c.Vulnerabilities {
			sev := "MEDIUM"
			var cvss float64 = 5.0
			if len(v.Ratings) > 0 {
				if v.Ratings[0].Severity != "" {
					sev = v.Ratings[0].Severity
				}
				if v.Ratings[0].Score > 0 {
					cvss = v.Ratings[0].Score
				}
			}
			vulns = append(vulns, models.Vulnerability{
				ID:             v.ID,
				Title:          v.ID,
				Description:    v.Description,
				Severity:       sev,
				CVSS:           cvss,
				PackageName:    c.Name,
				PackageVersion: c.Version,
			})
		}

		purl := c.PURL
		if purl == "" {
			purl = fmt.Sprintf("pkg:generic/%s@%s", c.Name, c.Version)
		}

		var childDeps []string
		if children, exists := depMap[c.PURL]; exists {
			childDeps = children
		}

		components = append(components, models.Component{
			ID:              fmt.Sprintf("pkg-%s-%s", c.Name, c.Version),
			Name:            c.Name,
			Version:         c.Version,
			PURL:            purl,
			Ecosystem:       inferEcosystem(purl),
			License:         license,
			Direct:          isDirect,
			Dependencies:    childDeps,
			Vulnerabilities: vulns,
		})
	}

	artifact.Components = components
	return artifact, nil
}

func inferEcosystem(purl string) string {
	if len(purl) > 4 && purl[:4] == "pkg:" {
		// e.g. pkg:npm/lodash@4.17.15 -> npm
		rest := purl[4:]
		for i, ch := range rest {
			if ch == '/' {
				return rest[:i]
			}
		}
	}
	return "generic"
}
