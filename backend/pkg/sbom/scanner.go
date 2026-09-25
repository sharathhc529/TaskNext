package sbom

import (
	"bufio"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/sbomguard/backend/pkg/models"
)

// ScanInput performs SBOM generation from raw source files or standard SBOM formats
func ScanInput(targetType string, fileName string, content []byte) (*models.Artifact, error) {
	// If content is JSON, check if CycloneDX or SPDX
	if strings.HasSuffix(fileName, ".json") || targetType == "cyclonedx" || targetType == "spdx" {
		var raw map[string]interface{}
		if err := json.Unmarshal(content, &raw); err == nil {
			if _, ok := raw["bomFormat"]; ok {
				return ParseCycloneDX(content)
			}
			if _, ok := raw["spdxVersion"]; ok {
				return ParseSPDX(content)
			}
			if _, ok := raw["dependencies"]; ok || strings.EqualFold(fileName, "package.json") {
				return parsePackageJSON(content)
			}
		}
	}

	if strings.EqualFold(fileName, "go.mod") || targetType == "gomod" {
		return parseGoMod(content)
	}

	if strings.EqualFold(fileName, "requirements.txt") || targetType == "requirements" {
		return parseRequirementsTxt(content)
	}

	// Try auto-detection
	if strings.Contains(string(content), "module ") && strings.Contains(string(content), "go ") {
		return parseGoMod(content)
	}

	if strings.Contains(string(content), "bomFormat") {
		return ParseCycloneDX(content)
	}

	if strings.Contains(string(content), "spdxVersion") {
		return ParseSPDX(content)
	}

	return nil, fmt.Errorf("unable to determine format for file %q", fileName)
}

func parsePackageJSON(content []byte) (*models.Artifact, error) {
	var pkg struct {
		Name         string            `json:"name"`
		Version      string            `json:"version"`
		License      string            `json:"license"`
		Dependencies map[string]string `json:"dependencies"`
		DevDeps      map[string]string `json:"devDependencies"`
	}

	if err := json.Unmarshal(content, &pkg); err != nil {
		return nil, fmt.Errorf("invalid package.json: %w", err)
	}

	if pkg.Name == "" {
		pkg.Name = "nodejs-app"
	}
	if pkg.Version == "" {
		pkg.Version = "1.0.0"
	}

	art := &models.Artifact{
		ID:        fmt.Sprintf("art-npm-%s-%s", pkg.Name, pkg.Version),
		Name:      pkg.Name,
		Version:   pkg.Version,
		Type:      "application",
		Ecosystem: "npm",
		ScannedAt: time.Now(),
	}

	knownLicenses := map[string]string{
		"lodash":       "MIT",
		"express":      "MIT",
		"axios":        "MIT",
		"react":        "MIT",
		"jsonwebtoken": "MIT",
		"moment":       "MIT",
		"minimist":     "MIT",
		"debug":        "MIT",
		"semver":       "ISC",
		"tar":          "ISC",
		"glob":         "ISC",
		"gpl-library":  "GPL-3.0-only",
		"agpl-module":  "AGPL-3.0-or-later",
	}

	// Transitive dependencies mapping for hands-on simulation
	transitiveMapping := map[string][]struct {
		Name    string
		Version string
		License string
	}{
		"express": {
			{Name: "qs", Version: "6.7.0", License: "BSD-3-Clause"},
			{Name: "cookie", Version: "0.4.0", License: "MIT"},
			{Name: "send", Version: "0.17.1", License: "MIT"},
			{Name: "mime", Version: "1.6.0", License: "MIT"},
		},
		"jsonwebtoken": {
			{Name: "jws", Version: "3.2.2", License: "MIT"},
			{Name: "jwa", Version: "1.4.1", License: "MIT"},
			{Name: "buffer-equal-constant-time", Version: "1.0.1", License: "BSD-3-Clause"},
		},
		"gpl-library": {
			{Name: "copyleft-core", Version: "2.1.0", License: "GPL-2.0-only"},
		},
	}

	var components []models.Component
	addedMap := make(map[string]bool)

	var addComp func(name, version string, direct bool)
	addComp = func(name, version string, direct bool) {
		cleanVer := strings.TrimLeft(version, "^~>=< ")
		if cleanVer == "" {
			cleanVer = "1.0.0"
		}
		key := name + "@" + cleanVer
		if addedMap[key] {
			return
		}
		addedMap[key] = true

		lic, ok := knownLicenses[name]
		if !ok {
			lic = "Apache-2.0"
		}

		var childRefs []string
		if children, ok := transitiveMapping[name]; ok {
			for _, ch := range children {
				childRefs = append(childRefs, fmt.Sprintf("pkg:npm/%s@%s", ch.Name, ch.Version))
			}
		}

		components = append(components, models.Component{
			ID:           fmt.Sprintf("pkg-%s-%s", name, cleanVer),
			Name:         name,
			Version:      cleanVer,
			PURL:         fmt.Sprintf("pkg:npm/%s@%s", name, cleanVer),
			Ecosystem:    "npm",
			License:      lic,
			Direct:       direct,
			Dependencies: childRefs,
		})

		// Add transitives
		if direct {
			if children, ok := transitiveMapping[name]; ok {
				for _, ch := range children {
					addComp(ch.Name, ch.Version, false)
				}
			}
		}
	}

	for name, ver := range pkg.Dependencies {
		addComp(name, ver, true)
	}
	for name, ver := range pkg.DevDeps {
		addComp(name, ver, true)
	}

	art.Components = components
	return art, nil
}

func parseGoMod(content []byte) (*models.Artifact, error) {
	scanner := bufio.NewScanner(strings.NewReader(string(content)))
	var moduleName string
	var components []models.Component

	knownGoLicenses := map[string]string{
		"github.com/gin-gonic/gin":        "MIT",
		"github.com/open-policy-agent/opa": "Apache-2.0",
		"golang.org/x/crypto":             "BSD-3-Clause",
		"golang.org/x/net":                "BSD-3-Clause",
		"github.com/golang-jwt/jwt":       "MIT",
		"github.com/beego/beego":          "Apache-2.0",
		"github.com/mattn/go-sqlite3":     "MIT",
		"gopkg.in/yaml.v2":                "Apache-2.0",
		"gopkg.in/yaml.v3":                "MIT",
		"github.com/stretchr/testify":     "MIT",
		"github.com/gpl/gpl-go-tool":      "GPL-3.0-only",
	}

	inRequire := false
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if strings.HasPrefix(line, "//") || line == "" {
			continue
		}

		if strings.HasPrefix(line, "module ") {
			moduleName = strings.TrimSpace(strings.TrimPrefix(line, "module"))
			continue
		}

		if strings.HasPrefix(line, "require (") {
			inRequire = true
			continue
		}

		if inRequire && line == ")" {
			inRequire = false
			continue
		}

		if inRequire || strings.HasPrefix(line, "require ") {
			reqLine := strings.TrimPrefix(line, "require ")
			parts := strings.Fields(reqLine)
			if len(parts) >= 2 {
				pkgName := parts[0]
				version := parts[1]
				isDirect := !strings.Contains(reqLine, "// indirect")

				lic, ok := knownGoLicenses[pkgName]
				if !ok {
					lic = "MIT"
				}

				components = append(components, models.Component{
					ID:        fmt.Sprintf("pkg-go-%s-%s", strings.ReplaceAll(pkgName, "/", "-"), version),
					Name:      pkgName,
					Version:   version,
					PURL:      fmt.Sprintf("pkg:golang/%s@%s", pkgName, version),
					Ecosystem: "golang",
					License:   lic,
					Direct:    isDirect,
				})
			}
		}
	}

	if moduleName == "" {
		moduleName = "go-app"
	}

	return &models.Artifact{
		ID:         fmt.Sprintf("art-go-%s", moduleName),
		Name:       moduleName,
		Version:    "1.0.0",
		Type:       "application",
		Ecosystem:  "golang",
		ScannedAt:  time.Now(),
		Components: components,
	}, nil
}

func parseRequirementsTxt(content []byte) (*models.Artifact, error) {
	scanner := bufio.NewScanner(strings.NewReader(string(content)))
	var components []models.Component

	knownPyLicenses := map[string]string{
		"django":     "BSD-3-Clause",
		"flask":      "BSD-3-Clause",
		"requests":   "Apache-2.0",
		"urllib3":    "MIT",
		"cryptography": "Apache-2.0",
		"pyyaml":     "MIT",
		"jinja2":     "BSD-3-Clause",
		"pillow":     "HPND",
		"gpl-py-tool": "GPL-3.0-only",
	}

	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}

		parts := strings.Split(line, "==")
		if len(parts) == 2 {
			pkgName := strings.TrimSpace(parts[0])
			version := strings.TrimSpace(parts[1])

			lic, ok := knownPyLicenses[strings.ToLower(pkgName)]
			if !ok {
				lic = "MIT"
			}

			components = append(components, models.Component{
				ID:        fmt.Sprintf("pkg-pypi-%s-%s", pkgName, version),
				Name:      pkgName,
				Version:   version,
				PURL:      fmt.Sprintf("pkg:pypi/%s@%s", pkgName, version),
				Ecosystem: "pypi",
				License:   lic,
				Direct:    true,
			})
		}
	}

	return &models.Artifact{
		ID:         "art-pypi-python-service",
		Name:       "python-service",
		Version:    "1.0.0",
		Type:       "application",
		Ecosystem:  "pypi",
		ScannedAt:  time.Now(),
		Components: components,
	}, nil
}
