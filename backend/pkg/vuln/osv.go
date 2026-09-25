package vuln

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/sbomguard/backend/pkg/models"
)

var httpClient = &http.Client{
	Timeout: 4 * time.Second,
}

// OSVQueryRequest payload for OSV API
type OSVQueryRequest struct {
	Package struct {
		Name      string `json:"name"`
		Ecosystem string `json:"ecosystem"`
	} `json:"package"`
	Version string `json:"version"`
}

// OSVQueryResponse from OSV API
type OSVQueryResponse struct {
	Vulns []struct {
		ID      string `json:"id"`
		Summary string `json:"summary"`
		Details string `json:"details"`
		Aliases []string `json:"aliases"`
		DatabaseSpecific struct {
			Severity string `json:"severity"`
		} `json:"database_specific"`
		Severity []struct {
			Type  string `json:"type"`
			Score string `json:"score"`
		} `json:"severity"`
		Affected []struct {
			Ranges []struct {
				Events []struct {
					Introduced string `json:"introduced,omitempty"`
					Fixed      string `json:"fixed,omitempty"`
				} `json:"events"`
			} `json:"ranges"`
		} `json:"affected"`
		References []struct {
			Type string `json:"type"`
			URL  string `json:"url"`
		} `json:"references"`
	} `json:"vulns"`
}

// Known CVE database fallback / offline seed for high reliability & instant demo
var knownVulnerabilities = map[string][]models.Vulnerability{
	"lodash@4.17.15": {
		{
			ID:             "CVE-2019-10744",
			Title:          "Prototype Pollution in lodash",
			Description:    "Versions of lodash prior to 4.17.19 are vulnerable to Prototype Pollution via defaultsDeep, merge, and mergeWith functions.",
			Severity:       "CRITICAL",
			CVSS:           9.8,
			FixedVersion:   "4.17.19",
			AdvisoryURL:    "https://nvd.nist.gov/vuln/detail/CVE-2019-10744",
			PackageName:    "lodash",
			PackageVersion: "4.17.15",
		},
		{
			ID:             "CVE-2020-8203",
			Title:          "Prototype Pollution in lodash.zipObjectDeep",
			Description:    "Prototype pollution vulnerability in lodash before 4.17.19 allows attackers to modify object prototype.",
			Severity:       "HIGH",
			CVSS:           7.4,
			FixedVersion:   "4.17.19",
			AdvisoryURL:    "https://nvd.nist.gov/vuln/detail/CVE-2020-8203",
			PackageName:    "lodash",
			PackageVersion: "4.17.15",
		},
	},
	"express@4.16.4": {
		{
			ID:             "CVE-2024-43796",
			Title:          "Express Open Redirect / XSS vulnerability",
			Description:    "Express before 4.19.2 contains improper input handling allowing bypass in static routing.",
			Severity:       "HIGH",
			CVSS:           7.5,
			FixedVersion:   "4.19.2",
			AdvisoryURL:    "https://github.com/advisories/GHSA-qw6h-v8gh-w3fs",
			PackageName:    "express",
			PackageVersion: "4.16.4",
		},
	},
	"qs@6.7.0": {
		{
			ID:             "CVE-2022-24999",
			Title:          "qs prototype pollution vulnerability",
			Description:    "qs before 6.10.3 is vulnerable to prototype pollution when parsing query strings.",
			Severity:       "HIGH",
			CVSS:           7.5,
			FixedVersion:   "6.10.3",
			AdvisoryURL:    "https://nvd.nist.gov/vuln/detail/CVE-2022-24999",
			PackageName:    "qs",
			PackageVersion: "6.7.0",
		},
	},
	"jsonwebtoken@8.5.1": {
		{
			ID:             "CVE-2022-23529",
			Title:          "jsonwebtoken Insecure Key Retrieval Remote Code Execution",
			Description:    "Versions of jsonwebtoken prior to 9.0.0 allow arbitrary file write / code execution via forged key objects.",
			Severity:       "CRITICAL",
			CVSS:           9.8,
			FixedVersion:   "9.0.0",
			AdvisoryURL:    "https://nvd.nist.gov/vuln/detail/CVE-2022-23529",
			PackageName:    "jsonwebtoken",
			PackageVersion: "8.5.1",
		},
	},
	"log4j-core@2.14.1": {
		{
			ID:             "CVE-2021-44228",
			Title:          "Log4Shell: Remote Code Execution in Apache Log4j2",
			Description:    "JNDI features used in configuration, log messages, and parameters do not protect against attacker controlled LDAP and other JNDI related endpoints.",
			Severity:       "CRITICAL",
			CVSS:           10.0,
			FixedVersion:   "2.17.1",
			AdvisoryURL:    "https://nvd.nist.gov/vuln/detail/CVE-2021-44228",
			PackageName:    "log4j-core",
			PackageVersion: "2.14.1",
		},
	},
	"golang.org/x/crypto@v0.0.0-20201221181555-eec23a3978ad": {
		{
			ID:             "CVE-2022-27191",
			Title:          "Crash in golang.org/x/crypto/ssh on empty signature",
			Description:    "An attacker can cause a denial of service (panic) in ssh server implementations.",
			Severity:       "HIGH",
			CVSS:           7.5,
			FixedVersion:   "v0.0.0-20220314234659-1baeb1ce4c0b",
			AdvisoryURL:    "https://nvd.nist.gov/vuln/detail/CVE-2022-27191",
			PackageName:    "golang.org/x/crypto",
			PackageVersion: "v0.0.0-20201221181555-eec23a3978ad",
		},
	},
	"django@2.2": {
		{
			ID:             "CVE-2021-35042",
			Title:          "SQL Injection vulnerability in Django QuerySet.order_by()",
			Description:    "Unfiltered user input passed to QuerySet.order_by() can cause SQL Injection.",
			Severity:       "CRITICAL",
			CVSS:           9.8,
			FixedVersion:   "2.2.24",
			AdvisoryURL:    "https://nvd.nist.gov/vuln/detail/CVE-2021-35042",
			PackageName:    "django",
			PackageVersion: "2.2",
		},
	},
}

// EnrichArtifactWithVulnerabilities queries OSV and local databases for vulnerabilities
func EnrichArtifactWithVulnerabilities(artifact *models.Artifact) {
	var wg sync.WaitGroup
	var mu sync.Mutex

	for i := range artifact.Components {
		comp := &artifact.Components[i]
		if len(comp.Vulnerabilities) > 0 {
			continue // Already has vulnerability info from SBOM
		}

		// Check local offline database first
		key := fmt.Sprintf("%s@%s", comp.Name, comp.Version)
		if vulns, exists := knownVulnerabilities[key]; exists {
			comp.Vulnerabilities = append(comp.Vulnerabilities, vulns...)
			continue
		}

		// Asynchronously query OSV API
		wg.Add(1)
		go func(c *models.Component) {
			defer wg.Done()
			vulns := queryOSV(c.Name, c.Version, c.Ecosystem)
			if len(vulns) > 0 {
				mu.Lock()
				c.Vulnerabilities = append(c.Vulnerabilities, vulns...)
				mu.Unlock()
			}
		}(comp)
	}

	wg.Wait()
}

func queryOSV(pkgName, version, ecosystem string) []models.Vulnerability {
	osvEco := "npm"
	switch strings.ToLower(ecosystem) {
	case "golang", "go":
		osvEco = "Go"
	case "pypi", "python":
		osvEco = "PyPI"
	case "maven":
		osvEco = "Maven"
	default:
		osvEco = "npm"
	}

	reqBody := OSVQueryRequest{
		Version: version,
	}
	reqBody.Package.Name = pkgName
	reqBody.Package.Ecosystem = osvEco

	bodyBytes, err := json.Marshal(reqBody)
	if err != nil {
		return nil
	}

	resp, err := httpClient.Post("https://api.osv.dev/v1/query", "application/json", bytes.NewReader(bodyBytes))
	if err != nil || resp.StatusCode != http.StatusOK {
		return nil
	}
	defer resp.Body.Close()

	var res OSVQueryResponse
	if err := json.NewDecoder(resp.Body).Decode(&res); err != nil {
		return nil
	}

	var results []models.Vulnerability
	for _, v := range res.Vulns {
		id := v.ID
		if len(v.Aliases) > 0 {
			for _, a := range v.Aliases {
				if strings.HasPrefix(a, "CVE-") {
					id = a
					break
				}
			}
		}

		sev := "MEDIUM"
		var cvss float64 = 6.0
		if strings.EqualFold(v.DatabaseSpecific.Severity, "CRITICAL") {
			sev = "CRITICAL"
			cvss = 9.5
		} else if strings.EqualFold(v.DatabaseSpecific.Severity, "HIGH") {
			sev = "HIGH"
			cvss = 7.5
		} else if strings.EqualFold(v.DatabaseSpecific.Severity, "LOW") {
			sev = "LOW"
			cvss = 3.5
		}

		fixedVer := ""
		for _, aff := range v.Affected {
			for _, rng := range aff.Ranges {
				for _, evt := range rng.Events {
					if evt.Fixed != "" {
						fixedVer = evt.Fixed
						break
					}
				}
			}
		}

		advisory := ""
		if len(v.References) > 0 {
			advisory = v.References[0].URL
		}

		results = append(results, models.Vulnerability{
			ID:             id,
			Title:          v.Summary,
			Description:    v.Details,
			Severity:       sev,
			CVSS:           cvss,
			FixedVersion:   fixedVer,
			AdvisoryURL:    advisory,
			PackageName:    pkgName,
			PackageVersion: version,
		})
	}

	return results
}
