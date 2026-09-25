package graph

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"time"

	"github.com/dgraph-io/dgo/v240"
	"github.com/dgraph-io/dgo/v240/protos/api"
	"github.com/sbomguard/backend/pkg/models"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// DgraphSchema is the DQL schema definition for software supply chain entities
const DgraphSchema = `
type Artifact {
    artifact_id: string
    name: string
    version: string
    artifact_type: string
    ecosystem: string
    scanned_at: datetime
    has_package: [Package]
}

type Package {
    package_id: string
    name: string
    version: string
    purl: string
    ecosystem: string
    license: string
    is_direct: bool
    depends_on: [Package]
    has_vulnerability: [Vulnerability]
    has_license: [License]
}

type Vulnerability {
    vuln_id: string
    title: string
    severity: string
    cvss: float
    fixed_version: string
}

type License {
    license_id: string
    name: string
    risk_category: string
}

artifact_id: string @index(exact) .
name: string @index(term, exact) .
version: string @index(exact) .
purl: string @index(exact) .
ecosystem: string @index(exact) .
license: string @index(exact) .
vuln_id: string @index(exact) .
severity: string @index(exact) .
cvss: float @index(float) .
has_package: [uid] @reverse .
depends_on: [uid] @reverse .
has_vulnerability: [uid] @reverse .
has_license: [uid] @reverse .
`

// DgraphStore implements GraphStore using a live Dgraph cluster
type DgraphStore struct {
	client      *dgo.Dgraph
	conn        *grpc.ClientConn
	memoryStore *MemoryGraphStore
	isLive      bool
}

// NewDgraphStore attempts to connect to Dgraph gRPC endpoint (default: localhost:9080)
func NewDgraphStore(targetHost string) *DgraphStore {
	memStore := NewMemoryGraphStore()
	if targetHost == "" {
		targetHost = "localhost:9080"
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	conn, err := grpc.DialContext(ctx, targetHost, grpc.WithTransportCredentials(insecure.NewCredentials()), grpc.WithBlock())
	if err != nil {
		log.Printf("[Dgraph] Live Dgraph instance not detected at %s (using high-performance embedded graph engine).", targetHost)
		return &DgraphStore{
			client:      nil,
			memoryStore: memStore,
			isLive:      false,
		}
	}

	dgraphClient := dgo.NewDgraphClient(api.NewDgraphClient(conn))
	store := &DgraphStore{
		client:      dgraphClient,
		conn:        conn,
		memoryStore: memStore,
		isLive:      true,
	}

	// Apply Schema
	if err := store.InitSchema(); err != nil {
		log.Printf("[Dgraph] Warning: Failed to apply schema: %v", err)
	} else {
		log.Printf("[Dgraph] Successfully connected to Dgraph at %s and initialized schema.", targetHost)
	}

	return store
}

// InitSchema applies DQL schema to Dgraph
func (d *DgraphStore) InitSchema() error {
	if !d.isLive || d.client == nil {
		return nil
	}
	ctx := context.Background()
	op := &api.Operation{
		Schema: DgraphSchema,
	}
	return d.client.Alter(ctx, op)
}

// SaveArtifactGraph writes artifact data to Dgraph and in-memory store
func (d *DgraphStore) SaveArtifactGraph(artifact *models.Artifact) error {
	// Always save in memory for fast UI visual graph retrieval
	_ = d.memoryStore.SaveArtifactGraph(artifact)

	if !d.isLive || d.client == nil {
		return nil
	}

	ctx := context.Background()
	txn := d.client.NewTxn()
	defer txn.Discard(ctx)

	type NQuadPackage struct {
		UID             string            `json:"uid,omitempty"`
		DType           []string          `json:"dgraph.type,omitempty"`
		PackageID       string            `json:"package_id,omitempty"`
		Name            string            `json:"name,omitempty"`
		Version         string            `json:"version,omitempty"`
		PURL            string            `json:"purl,omitempty"`
		Ecosystem       string            `json:"ecosystem,omitempty"`
		License         string            `json:"license,omitempty"`
		IsDirect        bool              `json:"is_direct"`
		HasVulnerability []models.Vulnerability `json:"has_vulnerability,omitempty"`
	}

	type NQuadArtifact struct {
		UID          string          `json:"uid,omitempty"`
		DType        []string        `json:"dgraph.type,omitempty"`
		ArtifactID   string          `json:"artifact_id,omitempty"`
		Name         string          `json:"name,omitempty"`
		Version      string          `json:"version,omitempty"`
		ArtifactType string          `json:"artifact_type,omitempty"`
		Ecosystem    string          `json:"ecosystem,omitempty"`
		ScannedAt    time.Time       `json:"scanned_at,omitempty"`
		HasPackage   []NQuadPackage  `json:"has_package,omitempty"`
	}

	var pkgs []NQuadPackage
	for _, c := range artifact.Components {
		pkgs = append(pkgs, NQuadPackage{
			UID:              fmt.Sprintf("_:%s", c.ID),
			DType:            []string{"Package"},
			PackageID:        c.ID,
			Name:             c.Name,
			Version:          c.Version,
			PURL:             c.PURL,
			Ecosystem:        c.Ecosystem,
			License:          c.License,
			IsDirect:         c.Direct,
			HasVulnerability: c.Vulnerabilities,
		})
	}

	artNode := NQuadArtifact{
		UID:          fmt.Sprintf("_:%s", artifact.ID),
		DType:        []string{"Artifact"},
		ArtifactID:   artifact.ID,
		Name:         artifact.Name,
		Version:      artifact.Version,
		ArtifactType: artifact.Type,
		Ecosystem:    artifact.Ecosystem,
		ScannedAt:    artifact.ScannedAt,
		HasPackage:   pkgs,
	}

	muJSON, err := json.Marshal(artNode)
	if err != nil {
		return err
	}

	mu := &api.Mutation{
		SetJson:   muJSON,
		CommitNow: true,
	}

	_, err = txn.Mutate(ctx, mu)
	return err
}

// GetArtifactGraph gets visual graph data
func (d *DgraphStore) GetArtifactGraph(artifactID string) (*models.GraphData, error) {
	return d.memoryStore.GetArtifactGraph(artifactID)
}

// GetBlastRadius queries for dependencies
func (d *DgraphStore) GetBlastRadius(packageName string) ([]string, error) {
	return d.memoryStore.GetBlastRadius(packageName)
}

// IsLive returns whether connected to a real Dgraph cluster
func (d *DgraphStore) IsLive() bool {
	return d.isLive
}
