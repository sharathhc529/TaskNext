package api

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/sbomguard/backend/pkg/graph"
	"github.com/sbomguard/backend/pkg/models"
	"github.com/sbomguard/backend/pkg/policy"
	"github.com/sbomguard/backend/pkg/sbom"
	"github.com/sbomguard/backend/pkg/vuln"
)

type Server struct {
	graphStore graph.GraphStore
	opaEngine  *policy.Engine
	lastReport *models.ScanReport
	samplesDir string
}

func NewServer(graphStore graph.GraphStore, opaEngine *policy.Engine, samplesDir string) *Server {
	return &Server{
		graphStore: graphStore,
		opaEngine:  opaEngine,
		samplesDir: samplesDir,
	}
}

func (s *Server) RegisterRoutes(r *gin.Engine) {
	api := r.Group("/api")
	{
		api.GET("/health", s.handleHealth)
		api.GET("/samples", s.handleListSamples)
		api.POST("/scan", s.handleScan)
		api.GET("/graph/:artifactId", s.handleGetGraph)
		api.GET("/policies", s.handleGetPolicies)
		api.POST("/policies/evaluate", s.handleEvaluateCustomPolicy)
		api.GET("/report/latest", s.handleGetLatestReport)
		api.GET("/blast-radius", s.handleGetBlastRadius)
	}
}

func (s *Server) handleHealth(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"status":    "healthy",
		"timestamp": time.Now().Format(time.RFC3339),
		"version":   "1.0.0",
	})
}

func (s *Server) handleListSamples(c *gin.Context) {
	samples := []gin.H{
		{
			"id":          "vulnerable-node",
			"name":        "Vulnerable Storefront API (Node.js)",
			"description": "Contains Critical Prototype Pollution (lodash 4.17.15), RCE (jsonwebtoken 8.5.1), and prohibited GPL license.",
			"type":        "package.json",
			"category":    "High Risk Demo",
		},
		{
			"id":          "clean-node",
			"name":        "Secure Payment Gateway (Node.js)",
			"description": "Hardened modern microservice with zero high-severity CVEs and clean MIT licenses.",
			"type":        "package.json",
			"category":    "Clean Build Demo",
		},
		{
			"id":          "vulnerable-go",
			"name":        "Vulnerable Auth Service (Go)",
			"description": "Go microservice containing vulnerable crypto SSH packages and copyleft tool dependency.",
			"type":        "go.mod",
			"category":    "Go Security Demo",
		},
		{
			"id":          "cyclonedx-cloud",
			"name":        "Enterprise Cloud Portal (CycloneDX 1.5)",
			"description": "Standard CycloneDX 1.5 SBOM containing Apache Log4j Log4Shell CVE-2021-44228.",
			"type":        "CycloneDX JSON",
			"category":    "Enterprise SBOM",
		},
	}
	c.JSON(http.StatusOK, samples)
}

func (s *Server) handleScan(c *gin.Context) {
	startTime := time.Now()
	var req models.ScanRequest

	// Support JSON body or file multipart upload
	if err := c.ShouldBindJSON(&req); err != nil {
		file, err := c.FormFile("file")
		if err == nil {
			f, err := file.Open()
			if err == nil {
				defer f.Close()
				buf := make([]byte, file.Size)
				_, _ = f.Read(buf)
				req.Content = string(buf)
				req.FileName = file.Filename
				req.TargetType = "upload"
			}
		}
	}

	var contentBytes []byte
	var fileName = req.FileName
	var targetType = req.TargetType

	// Handle preloaded samples
	if req.SampleName != "" || targetType == "sample" {
		sampleKey := req.SampleName
		switch sampleKey {
		case "vulnerable-node":
			p := filepath.Join(s.samplesDir, "vulnerable-web-app", "package.json")
			contentBytes, _ = os.ReadFile(p)
			fileName = "package.json"
		case "clean-node":
			p := filepath.Join(s.samplesDir, "clean-microservice", "package.json")
			contentBytes, _ = os.ReadFile(p)
			fileName = "package.json"
		case "vulnerable-go":
			p := filepath.Join(s.samplesDir, "vulnerable-go-app", "go.mod")
			contentBytes, _ = os.ReadFile(p)
			fileName = "go.mod"
		case "cyclonedx-cloud":
			p := filepath.Join(s.samplesDir, "cyclonedx-sample.json")
			contentBytes, _ = os.ReadFile(p)
			fileName = "cyclonedx-sample.json"
		default:
			// Fallback to sample vulnerable app
			p := filepath.Join(s.samplesDir, "vulnerable-web-app", "package.json")
			contentBytes, _ = os.ReadFile(p)
			fileName = "package.json"
		}
	} else if len(req.Content) > 0 {
		contentBytes = []byte(req.Content)
	}

	if len(contentBytes) == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "No content or sample provided for scanning"})
		return
	}

	// 1. SBOM Scanning & Parsing
	artifact, err := sbom.ScanInput(targetType, fileName, contentBytes)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": fmt.Sprintf("SBOM scan error: %v", err)})
		return
	}

	// 2. Vulnerability Enrichment (OSV.dev + local intelligence)
	vuln.EnrichArtifactWithVulnerabilities(artifact)

	// 3. Ingest into Dgraph / Graph Store
	_ = s.graphStore.SaveArtifactGraph(artifact)

	// 4. Evaluate OPA Rego Policies
	ctx := context.Background()
	policyResults, passedGate, err := s.opaEngine.EvaluateArtifact(ctx, artifact, req.CustomRego)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("OPA policy evaluation failed: %v", err)})
		return
	}

	// 5. Generate Metrics & Summary
	vulnSummary := map[string]int{
		"CRITICAL": 0,
		"HIGH":     0,
		"MEDIUM":   0,
		"LOW":      0,
	}
	licenseBreakdown := make(map[string]int)
	directCount := 0
	transitiveCount := 0

	for _, comp := range artifact.Components {
		if comp.Direct {
			directCount++
		} else {
			transitiveCount++
		}

		lic := comp.License
		if lic == "" {
			lic = "Unknown"
		}
		licenseBreakdown[lic]++

		for _, v := range comp.Vulnerabilities {
			vulnSummary[v.Severity]++
		}
	}

	decision := "PASS"
	if !passedGate {
		decision = "FAIL"
	} else if vulnSummary["MEDIUM"] > 0 || vulnSummary["LOW"] > 0 {
		decision = "WARN"
	}

	scanDuration := time.Since(startTime).Milliseconds()
	report := &models.ScanReport{
		ScanID:                 uuid.New().String(),
		Artifact:               *artifact,
		Timestamp:              time.Now(),
		TotalPackages:          len(artifact.Components),
		DirectDependencies:     directCount,
		TransitiveDependencies: transitiveCount,
		VulnerabilitiesSummary: vulnSummary,
		LicenseBreakdown:       licenseBreakdown,
		PolicyResults:          policyResults,
		PassedGate:             passedGate,
		GateDecision:           decision,
		ScanDurationMs:         scanDuration,
	}

	s.lastReport = report
	c.JSON(http.StatusOK, report)
}

func (s *Server) handleGetGraph(c *gin.Context) {
	artifactID := c.Param("artifactId")
	graphData, err := s.graphStore.GetArtifactGraph(artifactID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, graphData)
}

func (s *Server) handleGetPolicies(c *gin.Context) {
	policies := s.opaEngine.GetPolicies()
	c.JSON(http.StatusOK, policies)
}

func (s *Server) handleEvaluateCustomPolicy(c *gin.Context) {
	var body struct {
		RegoCode   string           `json:"regoCode"`
		Artifact   *models.Artifact `json:"artifact"`
		SampleName string           `json:"sampleName"`
	}

	if err := c.ShouldBindJSON(&body); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request body"})
		return
	}

	targetArtifact := body.Artifact
	if targetArtifact == nil && s.lastReport != nil {
		targetArtifact = &s.lastReport.Artifact
	}

	if targetArtifact == nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "No artifact available to evaluate policy against. Run a scan first."})
		return
	}

	ctx := context.Background()
	results, passed, err := s.opaEngine.EvaluateArtifact(ctx, targetArtifact, body.RegoCode)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"passed":  passed,
		"results": results,
	})
}

func (s *Server) handleGetLatestReport(c *gin.Context) {
	if s.lastReport == nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "No scan report generated yet"})
		return
	}
	c.JSON(http.StatusOK, s.lastReport)
}

func (s *Server) handleGetBlastRadius(c *gin.Context) {
	pkg := c.Query("package")
	if pkg == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "package query param is required"})
		return
	}
	impact, err := s.graphStore.GetBlastRadius(pkg)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}
	c.JSON(http.StatusOK, gin.H{"package": pkg, "impact": impact})
}
