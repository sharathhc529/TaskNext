# Hands-On Developer Guide: Software Supply Chain Security & SBOM Delivery

Welcome to the **SBOMGuard** hands-on guide! This project demonstrates how modern enterprise software delivery integrates **SBOM (Software Bill of Materials) scanning**, **Graph-based dependency modeling with Dgraph**, and **Policy-as-Code gatekeeping with Open Policy Agent (OPA)**.

---

## 1. Core Architecture & Mental Model

```
 ┌────────────────────────┐
 │ Source Code / Artifact │ (e.g. package.json, go.mod, CycloneDX JSON)
 └───────────┬────────────┘
             │
             ▼
 ┌────────────────────────┐
 │ 1. SBOM Scanner Engine │ (Go Parser: CycloneDX 1.5, SPDX 2.3, Manifests)
 └───────────┬────────────┘
             │
             ├──────────────────────────┐
             ▼                          ▼
 ┌────────────────────────┐   ┌────────────────────────┐
 │ 2. OSV.dev & CVE Feed  │   │ 3. Dgraph Graph Store  │
 │ (Vulnerability Lookup) │   │ (Nodes: App, Pkg, Lic) │
 └───────────┬────────────┘   └──────────┬─────────────┘
             │                           │
             └───────────┬───────────────┘
                         │
                         ▼
             ┌────────────────────────┐
             │ 4. OPA Engine (Rego)   │
             │ (Policy-as-Code Gate)  │
             └───────────┬────────────┘
                         │
             ┌───────────┴───────────┐
             ▼                       ▼
      [ PASS / RELEASE ]      [ FAIL / BLOCK ]
```

---

## 2. Key Components Breakdown

### A. Software Bill of Materials (SBOM)
An **SBOM** is a complete, nested inventory of all software components, third-party libraries, licenses, and metadata comprising an application.
- **CycloneDX (OWASP)**: Lightweight, modern, security-first specification optimized for vulnerability identification and automation.
- **SPDX (Linux Foundation / ISO/IEC 5962)**: Comprehensive standard with strong emphasis on software licensing, IP compliance, and copyright tracking.

### B. Dependency Graph Modeling with Dgraph
Why a Graph Database for SBOMs?
Relational databases struggle with nested and cyclic dependency hierarchies. In Dgraph:
- **`Artifact`** nodes represent the deployed microservice or container image.
- **`Package`** nodes represent libraries with exact versions (`purl` identifier).
- **`Vulnerability`** nodes store CVE IDs, CVSS severity scores, and fixed versions.
- **`License`** nodes represent license terms (MIT, Apache-2.0, GPL-3.0).
- **Edges (`depends_on`, `has_vulnerability`, `has_license`)** allow instantaneous calculation of **blast radius**: *"If Lodash 4.17.15 has a critical CVE, which downstream services in our cluster are vulnerable?"*

### C. Policy-as-Code with OPA (Open Policy Agent)
Instead of hardcoding compliance rules in CI/CD scripts, OPA separates policy logic from execution using **Rego**:
```rego
package sbom.security.critical_vuln

import rego.v1

default allow := true

# Block build if any dependency has a critical vulnerability (CVSS >= 9.0)
deny contains msg if {
    some comp in input.components
    some vuln in comp.vulnerabilities
    vuln.severity == "CRITICAL"
    msg := sprintf("Critical vulnerability %v found in %v@%v", [vuln.id, comp.name, comp.version])
}

allow := count(deny) == 0
```

---

## 3. Hands-On Developer Exercises

### Exercise 1: Scan a Vulnerable Application
1. Open the UI at `http://localhost:5173`.
2. In the **Dashboard**, select **"Vulnerable Storefront API (Node.js)"** from the target dropdown.
3. Click **"Run Full SBOM & OPA Scan"**.
4. Observe the results:
   - **Critical CVE**: `CVE-2019-10744` Prototype Pollution in `lodash@4.17.15`.
   - **GPL Violation**: `gpl-library` triggers `RULE-LIC-01` (GPL/Copyleft prohibition).
   - **Delivery Gate**: The status changes to **RELEASE BLOCKED**.

### Exercise 2: Explore the Dgraph Dependency Tree
1. Navigate to the **"Dgraph Dependency Tree"** tab.
2. Observe how the root `vulnerable-storefront-api` connects to direct dependencies and transitive sub-dependencies (`qs`, `cookie`, `send`).
3. Click on the red diamond node `CVE-2019-10744` to view CVSS score, advisory link, and remediation recommendations.

### Exercise 3: Write Custom Rego in Policy Studio
1. Navigate to the **"OPA Policy Studio"** tab.
2. In the live Rego editor, add a custom rule:
```rego
package sbom.custom.rule

import rego.v1

default allow := true

# Rule: Prohibit axios versions starting with '0.'
deny contains msg if {
    some comp in input.components
    comp.name == "axios"
    startswith(comp.version, "0.")
    msg := {
        "componentId": comp.id,
        "componentName": comp.name,
        "componentVersion": comp.version,
        "ruleId": "CUSTOM-AXIOS-01",
        "severity": "ERROR",
        "message": sprintf("Outdated legacy Axios detected: %v@%v", [comp.name, comp.version]),
        "details": "Upgrade to Axios v1.7+ for latest security patches."
    }
}

allow := count(deny) == 0
```
3. Click **"Execute Policy against SBOM"**.
4. Notice how OPA flags `axios@0.21.1` with detailed violation feedback!

### Exercise 4: Simulate the CI/CD Delivery Pipeline
1. Navigate to the **"CI/CD Delivery Gate"** tab.
2. Click **"Simulate Full CI/CD Pipeline Run"**.
3. Watch the step-by-step pipeline stages execute:
   - Build $\to$ SBOM Generation $\to$ Graph Ingestion $\to$ Vulnerability Feed $\to$ OPA Gate.
4. See how the pipeline automatically exits with `Code 1 (GATE_VIOLATION)` to prevent unsafe code from hitting production.

---

## 4. Running the Complete System

### Backend (Go):
```powershell
cd backend
go run main.go
```
The Go API server starts on `http://localhost:8080`.

### Frontend (React + Vite):
```powershell
cd frontend
npm run dev
```
The frontend dashboard opens at `http://localhost:5173`.

### Optional: Live Dgraph Cluster with Docker Compose:
```powershell
docker-compose up -d
```
Access Dgraph Ratel UI at `http://localhost:8000`.
