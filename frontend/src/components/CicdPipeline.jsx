import React, { useState } from 'react';
import { Terminal, ShieldCheck, ShieldAlert, GitBranch, Play, CheckCircle2, XCircle, ArrowRight, Layers, FileCode, Check } from 'lucide-react';

export default function CicdPipeline({ report, onRunScan, selectedSample, isScanning }) {
  const [pipelineRunning, setPipelineRunning] = useState(false);
  const [activeStep, setActiveStep] = useState(report ? 5 : 0);

  const steps = [
    {
      id: 1,
      title: 'Build & Package',
      description: 'Source compilation and container artifact packaging',
      icon: Layers,
      duration: '1.2s',
    },
    {
      id: 2,
      title: 'SBOM Generation',
      description: 'Generating CycloneDX 1.5 / SPDX 2.3 bill of materials',
      icon: FileCode,
      duration: '0.4s',
    },
    {
      id: 3,
      title: 'Dgraph Ingestion',
      description: 'Building dependency tree, purl nodes, and transitive edges',
      icon: GitBranch,
      duration: '0.3s',
    },
    {
      id: 4,
      title: 'Vulnerability Enrichment',
      description: 'Querying OSV.dev and NIST CVE databases for known advisories',
      icon: ShieldAlert,
      duration: '0.8s',
    },
    {
      id: 5,
      title: 'OPA Policy Gatekeeper',
      description: 'Evaluating Rego security and license compliance rules',
      icon: ShieldCheck,
      duration: '0.1s',
    },
  ];

  const handleSimulatePipeline = async () => {
    setPipelineRunning(true);
    setActiveStep(1);

    for (let i = 1; i <= 5; i++) {
      setActiveStep(i);
      await new Promise((r) => setTimeout(r, 600));
    }

    await onRunScan(selectedSample);
    setPipelineRunning(false);
    setActiveStep(5);
  };

  const isPassed = report?.passedGate;

  return (
    <div className="space-y-6 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 bg-[#101726] border border-slate-800 p-4 rounded-2xl shadow-lg">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
            <Terminal className="h-5 w-5" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-white">CI/CD Software Delivery Pipeline Simulator</h2>
            <p className="text-xs text-slate-400">Experience how modern DevSecOps pipelines enforce SBOM scanning and OPA quality gates</p>
          </div>
        </div>

        <button
          onClick={handleSimulatePipeline}
          disabled={pipelineRunning || isScanning}
          className="flex items-center gap-2 bg-gradient-to-r from-cyan-500 to-indigo-600 hover:from-cyan-400 hover:to-indigo-500 text-white font-bold py-2 px-4 rounded-xl text-xs shadow-lg shadow-cyan-500/20 transition disabled:opacity-50"
        >
          <Play className="h-3.5 w-3.5 fill-current" />
          <span>{pipelineRunning ? 'Pipeline Executing...' : 'Simulate Full CI/CD Pipeline Run'}</span>
        </button>
      </div>

      {/* Visual Pipeline Stages */}
      <div className="bg-[#101726] border border-slate-800 rounded-2xl p-6 shadow-xl">
        <div className="grid grid-cols-1 md:grid-cols-5 gap-3 relative">
          {steps.map((step, idx) => {
            const Icon = step.icon;
            const isCompleted = activeStep > step.id || (!pipelineRunning && report);
            const isCurrent = activeStep === step.id && pipelineRunning;
            const isBlockedAtGate = !pipelineRunning && report && !isPassed && step.id === 5;

            return (
              <div
                key={step.id}
                className={`p-4 rounded-xl border relative transition-all ${
                  isBlockedAtGate
                    ? 'bg-rose-950/30 border-rose-500/50 shadow-lg shadow-rose-950/40'
                    : isCompleted
                    ? 'bg-slate-900/80 border-emerald-500/30 shadow-sm'
                    : isCurrent
                    ? 'bg-cyan-950/40 border-cyan-500/60 ring-2 ring-cyan-500/20 animate-pulse'
                    : 'bg-slate-900/30 border-slate-800 opacity-60'
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[10px] font-bold text-slate-400 uppercase">Stage 0{step.id}</span>
                  {isBlockedAtGate ? (
                    <XCircle className="h-4 w-4 text-rose-400" />
                  ) : isCompleted ? (
                    <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                  ) : (
                    <span className="text-[10px] text-slate-400 mono">{step.duration}</span>
                  )}
                </div>

                <div className="flex items-center gap-2 mb-1">
                  <Icon
                    className={`h-4 w-4 ${
                      isBlockedAtGate
                        ? 'text-rose-400'
                        : isCompleted
                        ? 'text-emerald-400'
                        : isCurrent
                        ? 'text-cyan-400'
                        : 'text-slate-400'
                    }`}
                  />
                  <h4 className="text-xs font-bold text-white">{step.title}</h4>
                </div>

                <p className="text-[11px] text-slate-400 leading-snug">{step.description}</p>
              </div>
            );
          })}
        </div>

        {/* Pipeline Decision Banner */}
        {report && (
          <div
            className={`mt-6 p-5 rounded-xl border flex flex-wrap items-center justify-between gap-4 ${
              isPassed
                ? 'bg-emerald-950/20 border-emerald-500/30 text-emerald-300'
                : 'bg-rose-950/20 border-rose-500/30 text-rose-300'
            }`}
          >
            <div className="flex items-center gap-3">
              <div
                className={`p-3 rounded-xl border ${
                  isPassed ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400' : 'bg-rose-500/10 border-rose-500/20 text-rose-400'
                }`}
              >
                {isPassed ? <ShieldCheck className="h-7 w-7" /> : <ShieldAlert className="h-7 w-7" />}
              </div>
              <div>
                <h3 className="text-base font-extrabold text-white">
                  {isPassed ? 'Deployment Approved → Production Release' : 'Pipeline Failed → Deployment Blocked by OPA'}
                </h3>
                <p className="text-xs text-slate-300 mt-0.5">
                  {isPassed
                    ? `Artifact ${report.artifact.name} v${report.artifact.version} passed all software delivery security gates.`
                    : `Artifact violates ${report.policyResults.filter(p => !p.passed).length} supply chain security and license policies.`}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className="text-xs font-mono bg-slate-900/80 px-3 py-1.5 rounded-lg border border-slate-700 text-slate-300">
                Exit Code: <strong className={isPassed ? 'text-emerald-400' : 'text-rose-400'}>{isPassed ? '0 (SUCCESS)' : '1 (GATE_VIOLATION)'}</strong>
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Terminal Output Log simulation */}
      {report && (
        <div className="bg-[#0a0e17] border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
          <div className="bg-slate-900 px-4 py-2.5 border-b border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="flex gap-1.5">
                <div className="h-3 w-3 rounded-full bg-rose-500/80"></div>
                <div className="h-3 w-3 rounded-full bg-amber-500/80"></div>
                <div className="h-3 w-3 rounded-full bg-emerald-500/80"></div>
              </div>
              <span className="text-xs font-mono text-slate-400 ml-2">ci-cd-runner.log</span>
            </div>
            <span className="text-[10px] text-slate-400 font-mono">Job #{report.scanId?.slice(0, 8)}</span>
          </div>

          <pre className="p-4 text-xs font-mono text-slate-300 overflow-x-auto space-y-1 leading-relaxed">
            <div className="text-cyan-400">[INFO] Initializing CI/CD runner container...</div>
            <div className="text-slate-400">[INFO] Ingesting target artifact: {report.artifact.name}@{report.artifact.version}</div>
            <div className="text-slate-400">[SBOM] Generated Software Bill of Materials: {report.totalPackages} components identified</div>
            <div className="text-slate-400">[DGRAPH] Ingested dependency graph nodes and transitive edges into graph store</div>
            <div className="text-slate-400">[OSV] Querying open-source vulnerability databases (OSV.dev & CVE feeds)...</div>
            <div className="text-slate-400">[OSV] Vulnerability Summary: {report.vulnerabilitiesSummary?.CRITICAL || 0} Critical, {report.vulnerabilitiesSummary?.HIGH || 0} High</div>
            <div className="text-cyan-400">[OPA] Running Policy-as-Code Gatekeeper (Rego engine)...</div>
            {report.policyResults?.map((p, i) => (
              <div key={i} className={p.passed ? 'text-emerald-400 pl-4' : 'text-rose-400 pl-4 font-bold'}>
                {p.passed ? '✓' : '✗'} [{p.ruleId}] {p.ruleName} → {p.passed ? 'PASS' : 'FAIL'}
                {p.violations?.map((v, vi) => (
                  <div key={vi} className="text-rose-300 text-[11px] pl-4 font-normal">
                    ↳ Violation in {v.componentName}@{v.componentVersion}: {v.message}
                  </div>
                ))}
              </div>
            ))}
            <div className={isPassed ? 'text-emerald-400 font-bold pt-2' : 'text-rose-400 font-bold pt-2'}>
              {isPassed
                ? '[GATE] Result: APPROVED. Triggering deployment to Kubernetes cluster.'
                : '[GATE] Result: REJECTED. Delivery halted. Please resolve vulnerabilities or license violations before merge.'}
            </div>
          </pre>
        </div>
      )}
    </div>
  );
}
