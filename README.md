# SBOMGuard: Software Supply Chain Security & SBOM Delivery Platform

An end-to-end hands-on platform demonstrating modern software supply chain security, SBOM (Software Bill of Materials) scanning, graph-based dependency modeling with **Dgraph**, and policy-as-code gatekeeping with **Open Policy Agent (OPA)**.

---

## 🌟 Key Features

- 📦 **Multi-Format SBOM Engine (Go 1.24)**:
  - Supports **CycloneDX 1.5 JSON**, **SPDX 2.3 JSON**, and direct manifest scanning (`package.json`, `go.mod`, `requirements.txt`).
- 🛡️ **Vulnerability Intelligence (OSV.dev & CVE Feeds)**:
  - Real-time vulnerability lookup with CVSS ratings, severity classifications, and remediation versions.
- 🕸️ **Graph-Based Dependency Modeling (Dgraph)**:
  - Models software components as graph relationships (`Artifact` $\to$ `Package` $\to$ `License` & `Vulnerability`) for blast radius analysis.
  - Zero-dependency embedded in-memory graph driver + live Dgraph cluster support via `dgo`.
- ⚖️ **Policy-as-Code Gatekeeper (OPA / Rego)**:
  - Embedded Open Policy Agent evaluating license compliance (e.g. GPL/copyleft restrictions) and CVE severity thresholds.
  - Interactive live Rego editor and policy sandbox.
- 🚀 **Interactive Developer Dashboard (React 18 + Tailwind + Vis-Network)**:
  - Visual dependency graph canvas with node inspector.
  - Full SBOM inventory explorer with search and JSON export.
  - CI/CD delivery gate simulator with live terminal logs.

---

## 📁 Repository Structure

```
secureSoftware/
├── backend/                  # Go 1.24 API Server & Scanning Engine
│   ├── main.go               # Server entrypoint
│   ├── pkg/
│   │   ├── models/           # Domain data types
│   │   ├── sbom/             # CycloneDX, SPDX & manifest parsers
│   │   ├── vuln/             # OSV.dev client & vulnerability enrichment
│   │   ├── graph/            # Dgraph & in-memory graph engines
│   │   ├── policy/           # Embedded OPA Rego policy evaluator
│   │   └── api/              # HTTP REST controllers
│   ├── samples/              # Preloaded vulnerable & clean project fixtures
│   └── tests/                # Automated Go test suite
├── frontend/                 # React + Vite Dashboard
│   ├── src/
│   │   ├── components/       # Dashboard, GraphViewer, SbomTable, PolicyStudio, CicdPipeline
│   │   └── services/         # API client
│   └── package.json
├── docker-compose.yml        # Dgraph cluster (Zero + Alpha + Ratel UI)
├── GUIDE.md                  # Hands-on developer tutorial & exercises
└── README.md
```

---

## ⚡ Quick Start

### 1. Prerequisites
- **Go** (1.24+)
- **Node.js** (v18+) & **npm**

### 2. Run the Backend API
```powershell
cd backend
go run main.go
```
API server runs on `http://localhost:8080`.

### 3. Run the Frontend Dashboard
```powershell
cd frontend
npm install
npm run dev
```
Dashboard opens at `http://localhost:5173`.

### 4. Run Backend Unit Tests
```powershell
cd backend
go test -v ./...
```

---

## 📖 Hands-On Tutorial

Read [`GUIDE.md`](./GUIDE.md) for a comprehensive step-by-step developer walkthrough on:
- SBOM specifications (CycloneDX vs SPDX)
- Graph dependency modeling and transitive blast radius in Dgraph
- Policy-as-Code engineering with OPA & Rego
- Hands-on exercises for CI/CD pipeline gatekeeping
