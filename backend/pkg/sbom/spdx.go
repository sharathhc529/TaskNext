package sbom

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/sbomguard/backend/pkg/models"
)

// SPDXSpec minimal struct for SPDX 2.2 / 2.3 JSON
type SPDXSpec struct {
	SPDXVersion     string `json:"spdxVersion"`
	DataLicense     string `json:"dataLicense"`
	SPDXID          string `json:"SPDXID"`
	Name            string `json:"name"`
	DocumentNamespace string `json:"documentNamespace"`
	CreationInfo    struct {
		Created string `json:"created"`
	} `json:"creationInfo"`
	Packages []struct {
		SPDXID           string `json:"SPDXID"`
		Name             string `json:"name"`
		VersionInfo      string `json:"versionInfo"`
		LicenseConcluded string `json:"licenseConcluded"`
		LicenseDeclared  string `json:"licenseDeclared"`
		Description      string `json:"description"`
		ExternalRefs     []struct {
			ReferenceCategory string `json:"referenceCategory"`
			ReferenceType     string `json:"referenceType"`
			ReferenceLocator  string `json:"referenceLocator"`
		} `json:"externalRefs"`
	} `json:"packages"`
	Relationships []struct {
		SpdxElementId      string `json:"spdxElementId"`
		RelatedSpdxElement string `json:"relatedSpdxElement"`
		RelationshipType   string `json:"relationshipType"`
	} `json:"relationships"`
}

// ParseSPDX parses SPDX JSON string into normalized Artifact
func ParseSPDX(data []byte) (*models.Artifact, error) {
	var spdx SPDXSpec
	if err := json.Unmarshal(data, &spdx); err != nil {
		return nil, fmt.Errorf("failed to parse SPDX JSON: %w", err)
	}

	name := spdx.Name
	if name == "" {
		name = "spdx-scanned-artifact"
	}

	artifact := &models.Artifact{
		ID:        fmt.Sprintf("art-%s", name),
		Name:      name,
		Version:   "1.0.0",
		Type:      "application",
		ScannedAt: time.Now(),
	}

	// Map relationships
	rootElement := spdx.SPDXID
	directDeps := make(map[string]bool)
	depTree := make(map[string][]string)

	for _, rel := range spdx.Relationships {
		if rel.RelationshipType == "DEPENDS_ON" || rel.RelationshipType == "CONTAINS" {
			if rel.SpdxElementId == rootElement || rel.SpdxElementId == "SPDXRef-DOCUMENT" || rel.SpdxElementId == "SPDXRef-Package-Root" {
				directDeps[rel.RelatedSpdxElement] = true
			}
			depTree[rel.SpdxElementId] = append(depTree[rel.SpdxElementId], rel.RelatedSpdxElement)
		}
	}

	components := make([]models.Component, 0, len(spdx.Packages))
	for _, pkg := range spdx.Packages {
		if pkg.SPDXID == rootElement || pkg.SPDXID == "SPDXRef-Package-Root" || pkg.Name == spdx.Name {
			continue // Skip root package if declared in packages
		}

		license := pkg.LicenseConcluded
		if license == "" || license == "NOASSERTION" {
			license = pkg.LicenseDeclared
		}
		if license == "" || license == "NOASSERTION" {
			license = "Unknown"
		}

		purl := ""
		for _, ref := range pkg.ExternalRefs {
			if ref.ReferenceType == "purl" {
				purl = ref.ReferenceLocator
				break
			}
		}
		if purl == "" {
			purl = fmt.Sprintf("pkg:generic/%s@%s", pkg.Name, pkg.VersionInfo)
		}

		isDirect := true
		if len(directDeps) > 0 {
			isDirect = directDeps[pkg.SPDXID]
		}

		components = append(components, models.Component{
			ID:           fmt.Sprintf("pkg-%s-%s", pkg.Name, pkg.VersionInfo),
			Name:         pkg.Name,
			Version:      pkg.VersionInfo,
			PURL:         purl,
			Ecosystem:    inferEcosystem(purl),
			License:      license,
			Direct:       isDirect,
			Dependencies: depTree[pkg.SPDXID],
		})
	}

	artifact.Components = components
	return artifact, nil
}
