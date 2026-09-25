package graph

import (
	"fmt"
	"sync"

	"github.com/sbomguard/backend/pkg/models"
)

// GraphStore is the storage interface for dependency and vulnerability graphs
type GraphStore interface {
	SaveArtifactGraph(artifact *models.Artifact) error
	GetArtifactGraph(artifactID string) (*models.GraphData, error)
	GetBlastRadius(packageName string) ([]string, error)
}

// MemoryGraphStore provides high-speed in-memory graph queries and visualization
type MemoryGraphStore struct {
	mu        sync.RWMutex
	artifacts map[string]*models.Artifact
}

// NewMemoryGraphStore creates a new in-memory graph store
func NewMemoryGraphStore() *MemoryGraphStore {
	return &MemoryGraphStore{
		artifacts: make(map[string]*models.Artifact),
	}
}

// SaveArtifactGraph stores artifact and builds dependency nodes
func (m *MemoryGraphStore) SaveArtifactGraph(artifact *models.Artifact) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.artifacts[artifact.ID] = artifact
	return nil
}

// GetArtifactGraph generates visual node-link structure for frontend & graph analytics
func (m *MemoryGraphStore) GetArtifactGraph(artifactID string) (*models.GraphData, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	artifact, ok := m.artifacts[artifactID]
	if !ok {
		// If exact ID not found, return first artifact if exists
		for _, art := range m.artifacts {
			artifact = art
			break
		}
	}

	if artifact == nil {
		return &models.GraphData{Nodes: []models.GraphNode{}, Edges: []models.GraphEdge{}}, nil
	}

	nodes := make([]models.GraphNode, 0)
	edges := make([]models.GraphEdge, 0)
	nodeMap := make(map[string]bool)

	// Add Root Artifact Node
	artNodeID := fmt.Sprintf("root-%s", artifact.ID)
	nodes = append(nodes, models.GraphNode{
		ID:    artNodeID,
		Label: fmt.Sprintf("%s (v%s)", artifact.Name, artifact.Version),
		Type:  "artifact",
		Data: map[string]interface{}{
			"ecosystem": artifact.Ecosystem,
			"type":      artifact.Type,
		},
	})
	nodeMap[artNodeID] = true

	// Track licenses to create distinct license nodes
	licenseMap := make(map[string]string)

	for _, comp := range artifact.Components {
		compNodeID := fmt.Sprintf("comp-%s-%s", comp.Name, comp.Version)
		sev := "OK"
		for _, v := range comp.Vulnerabilities {
			if v.Severity == "CRITICAL" {
				sev = "CRITICAL"
				break
			} else if v.Severity == "HIGH" && sev != "CRITICAL" {
				sev = "HIGH"
			} else if v.Severity == "MEDIUM" && sev == "OK" {
				sev = "MEDIUM"
			}
		}

		if !nodeMap[compNodeID] {
			nodes = append(nodes, models.GraphNode{
				ID:       compNodeID,
				Label:    fmt.Sprintf("%s@%s", comp.Name, comp.Version),
				Type:     "package",
				Severity: sev,
				Data: map[string]interface{}{
					"name":            comp.Name,
					"version":         comp.Version,
					"purl":            comp.PURL,
					"ecosystem":       comp.Ecosystem,
					"license":         comp.License,
					"direct":          comp.Direct,
					"vulnerabilities": comp.Vulnerabilities,
				},
			})
			nodeMap[compNodeID] = true
		}

		// Connect root to direct dependencies
		if comp.Direct {
			edges = append(edges, models.GraphEdge{
				ID:       fmt.Sprintf("e-%s-%s", artNodeID, compNodeID),
				Source:   artNodeID,
				Target:   compNodeID,
				Relation: "DEPENDS_ON",
				Direct:   true,
			})
		}

		// Connect transitive sub-dependencies
		for _, depPURL := range comp.Dependencies {
			for _, targetComp := range artifact.Components {
				if targetComp.PURL == depPURL || targetComp.Name == depPURL {
					targetNodeID := fmt.Sprintf("comp-%s-%s", targetComp.Name, targetComp.Version)
					edges = append(edges, models.GraphEdge{
						ID:       fmt.Sprintf("e-%s-%s", compNodeID, targetNodeID),
						Source:   compNodeID,
						Target:   targetNodeID,
						Relation: "DEPENDS_ON",
						Direct:   false,
					})
				}
			}
		}

		// Add & connect License Node
		if comp.License != "" && comp.License != "Unknown" {
			licNodeID := fmt.Sprintf("lic-%s", comp.License)
			if !nodeMap[licNodeID] {
				nodes = append(nodes, models.GraphNode{
					ID:    licNodeID,
					Label: fmt.Sprintf("License: %s", comp.License),
					Type:  "license",
					Data: map[string]interface{}{
						"license": comp.License,
					},
				})
				nodeMap[licNodeID] = true
			}
			licenseMap[comp.License] = licNodeID
			edges = append(edges, models.GraphEdge{
				ID:       fmt.Sprintf("e-%s-%s", compNodeID, licNodeID),
				Source:   compNodeID,
				Target:   licNodeID,
				Relation: "LICENSED_UNDER",
			})
		}

		// Add & connect Vulnerability Nodes
		for _, v := range comp.Vulnerabilities {
			vulnNodeID := fmt.Sprintf("vuln-%s", v.ID)
			if !nodeMap[vulnNodeID] {
				nodes = append(nodes, models.GraphNode{
					ID:       vulnNodeID,
					Label:    fmt.Sprintf("%s (%s CVSS %.1f)", v.ID, v.Severity, v.CVSS),
					Type:     "vulnerability",
					Severity: v.Severity,
					Data: map[string]interface{}{
						"cve":          v.ID,
						"title":        v.Title,
						"severity":     v.Severity,
						"cvss":         v.CVSS,
						"fixedVersion": v.FixedVersion,
						"advisoryUrl":  v.AdvisoryURL,
					},
				})
				nodeMap[vulnNodeID] = true
			}

			edges = append(edges, models.GraphEdge{
				ID:       fmt.Sprintf("e-%s-%s", compNodeID, vulnNodeID),
				Source:   compNodeID,
				Target:   vulnNodeID,
				Relation: "HAS_VULNERABILITY",
			})
		}
	}

	return &models.GraphData{
		Nodes: nodes,
		Edges: edges,
	}, nil
}

// GetBlastRadius finds which artifacts and components are affected by a vulnerable package
func (m *MemoryGraphStore) GetBlastRadius(packageName string) ([]string, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	var impacted []string
	for _, art := range m.artifacts {
		for _, c := range art.Components {
			if c.Name == packageName {
				impacted = append(impacted, fmt.Sprintf("Artifact: %s v%s relies on %s@%s", art.Name, art.Version, c.Name, c.Version))
			}
		}
	}
	return impacted, nil
}
