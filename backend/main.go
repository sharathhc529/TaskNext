package main

import (
	"log"
	"os"
	"path/filepath"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/sbomguard/backend/pkg/api"
	"github.com/sbomguard/backend/pkg/graph"
	"github.com/sbomguard/backend/pkg/policy"
)

func main() {
	log.Println("=========================================================")
	log.Println("🛡️  Starting SBOMGuard Supply Chain Security Platform")
	log.Println("=========================================================")

	dgraphHost := os.Getenv("DGRAPH_HOST")
	if dgraphHost == "" {
		dgraphHost = "localhost:9080"
	}

	// 1. Initialize Graph Store (Dgraph with automatic in-memory fallback)
	graphStore := graph.NewDgraphStore(dgraphHost)

	// 2. Initialize Embedded OPA Policy Engine
	opaEngine := policy.NewEngine()
	log.Printf("[OPA Engine] Loaded %d default Rego security and license rules", len(opaEngine.GetPolicies()))

	// Locate samples directory
	execDir, err := os.Getwd()
	if err != nil {
		execDir = "."
	}
	samplesDir := filepath.Join(execDir, "samples")
	if _, err := os.Stat(samplesDir); os.IsNotExist(err) {
		samplesDir = filepath.Join(execDir, "backend", "samples")
	}

	// 3. Initialize REST API Server
	server := api.NewServer(graphStore, opaEngine, samplesDir)

	r := gin.Default()

	// Configure CORS for frontend access
	config := cors.DefaultConfig()
	config.AllowAllOrigins = true
	config.AllowHeaders = []string{"Origin", "Content-Length", "Content-Type", "Authorization"}
	r.Use(cors.New(config))

	server.RegisterRoutes(r)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Printf("🚀 SBOMGuard API Server running on http://localhost:%s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
