import React, { useState } from 'react';
import { 
  Play, Upload, ShieldCheck, ShieldAlert, AlertTriangle, 
  PackageCheck, GitMerge, FileCheck, RefreshCw, Cpu, Layers,
  ExternalLink, CheckCircle2, XCircle, Info
} from 'lucide-react';

export default function Dashboard({ 
  report, 
  samples, 
  selectedSample, 
  setSelectedSample, 
  onRunScan, 
  onUploadFile, 
  isScanning, 
  onOpenVuln,
  setActiveTab 
}) {
  const [dragOver, setDragOver] = useState(false);

  const handleFileDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      onUploadFile(e.dataTransfer.files[0]);
    }
  };

  const handleFileSelect = (e) => {
    if (e.target.files && e.target.files[0]) {
      onUploadFile(e.target.files[0]);
    }
  };

  return (
    <div className="space-y-8 animate-fadeIn">
      {/* Hero / Quick Action Banner */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Scan Launcher Card */}
        <div className="lg:col-span-2 bg-gradient-to-br from-[#101726] to-[#0c121e] border border-slate-800 rounded-2xl p-6 relative overflow-hidden shadow-xl">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                <Cpu className="h-6 w-6" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-white">SBOM Scanning Engine</h2>
                <p className="text-xs text-slate-400">Trigger scan from sample projects or upload package manifest / CycloneDX SBOM</p>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
            {/* Preset Samples */}
            <div className="space-y-3">
              <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
                Select Pre-Configured Target
              </label>
              <select
                value={selectedSample}
                onChange={(e) => setSelectedSample(e.target.value)}
                className="w-full bg-slate-900 border border-slate-700 rounded-xl px-3.5 py-2.5 text-sm text-slate-200 focus:outline-none focus:border-cyan-500 transition"
              >
                {samples.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.type})
                  </option>
                ))}
              </select>

              <button
                onClick={() => onRunScan(selectedSample)}
                disabled={isScanning}
                className="w-full flex items-center justify-center gap-2 bg-gradient-to-r from-cyan-500 to-indigo-600 hover:from-cyan-400 hover:to-indigo-500 text-white font-bold py-2.5 px-4 rounded-xl shadow-lg shadow-cyan-500/20 transition-all disabled:opacity-50"
              >
                {isScanning ? (
                  <>
                    <RefreshCw className="h-4 w-4 animate-spin" />
                    <span>Analyzing Components & Policies...</span>
                  </>
                ) : (
                  <>
                    <Play className="h-4 w-4 fill-current" />
                    <span>Run Full SBOM & OPA Scan</span>
                  </>
                )}
              </button>
            </div>

            {/* Custom File Upload Dropzone */}
            <div className="space-y-3">
              <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
                Or Upload File / SBOM
              </label>
              <div
                onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
                onDragLeave={() => setDragOver(false)}
                onDrop={handleFileDrop}
                className={`border-2 border-dashed rounded-xl p-4 text-center cursor-pointer transition ${
                  dragOver
                    ? 'border-cyan-400 bg-cyan-950/20'
                    : 'border-slate-700 hover:border-slate-600 bg-slate-900/40'
                }`}
                onClick={() => document.getElementById('file-upload-input').click()}
              >
                <Upload className="h-5 w-5 mx-auto text-slate-400 mb-1" />
                <p className="text-xs font-semibold text-slate-200">
                  Drop <span className="text-cyan-400">package.json</span>, <span className="text-cyan-400">go.mod</span>, or <span className="text-cyan-400">CycloneDX .json</span>
                </p>
                <p className="text-[11px] text-slate-400 mt-0.5">Click to browse local files</p>
                <input
                  id="file-upload-input"
                  type="file"
                  className="hidden"
                  onChange={handleFileSelect}
                  accept=".json,.mod,.txt"
                />
              </div>
            </div>
          </div>
        </div>

        {/* CI/CD Gate Decision Card */}
        <div className="bg-gradient-to-br from-[#101726] to-[#0c121e] border border-slate-800 rounded-2xl p-6 flex flex-col justify-between shadow-xl">
          <div>
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Delivery Gate Status</span>
              <span className="text-[10px] px-2 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700">
                OPA Gatekeeper
              </span>
            </div>

            {report ? (
              <div className="text-center py-4">
                <div
                  className={`mx-auto h-16 w-16 rounded-2xl flex items-center justify-center mb-3 shadow-xl ${
                    report.passedGate
                      ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 shadow-emerald-500/10'
                      : 'bg-rose-500/10 text-rose-400 border border-rose-500/30 shadow-rose-500/10'
                  }`}
                >
                  {report.passedGate ? (
                    <ShieldCheck className="h-10 w-10 text-emerald-400" />
                  ) : (
                    <ShieldAlert className="h-10 w-10 text-rose-400" />
                  )}
                </div>
                <h3 className={`text-xl font-extrabold ${report.passedGate ? 'text-emerald-400' : 'text-rose-400'}`}>
                  {report.passedGate ? 'PIPELINE APPROVED' : 'RELEASE BLOCKED'}
                </h3>
                <p className="text-xs text-slate-400 mt-1 max-w-xs mx-auto">
                  {report.passedGate
                    ? 'All OPA security and license compliance policies passed.'
                    : `${report.policyResults.filter(p => !p.passed).length} policy rule(s) violated. Action required.`}
                </p>
              </div>
            ) : (
              <div className="text-center py-6 text-slate-400 text-xs">
                <Info className="h-8 w-8 mx-auto mb-2 opacity-50" />
                No active scan yet. Run a scan on the left to evaluate security gates.
              </div>
            )}
          </div>

          {report && (
            <button
              onClick={() => setActiveTab('pipeline')}
              className="w-full text-center text-xs font-semibold text-cyan-400 hover:text-cyan-300 py-2 border-t border-slate-800 transition"
            >
              Inspect CI/CD Simulator Step-by-Step →
            </button>
          )}
        </div>
      </div>

      {/* Metrics Counter Bar */}
      {report && (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
          <div className="bg-[#101726] border border-slate-800 rounded-xl p-4">
            <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Total Packages</span>
            <div className="text-2xl font-black text-white mt-1 mono">{report.totalPackages}</div>
            <span className="text-[10px] text-slate-400">{report.directDependencies} direct / {report.transitiveDependencies} transitive</span>
          </div>

          <div className="bg-[#101726] border border-slate-800 rounded-xl p-4">
            <span className="text-[11px] font-semibold text-rose-400 uppercase tracking-wider">Critical CVEs</span>
            <div className="text-2xl font-black text-rose-400 mt-1 mono">{report.vulnerabilitiesSummary?.CRITICAL || 0}</div>
            <span className="text-[10px] text-slate-400">CVSS ≥ 9.0</span>
          </div>

          <div className="bg-[#101726] border border-slate-800 rounded-xl p-4">
            <span className="text-[11px] font-semibold text-orange-400 uppercase tracking-wider">High CVEs</span>
            <div className="text-2xl font-black text-orange-400 mt-1 mono">{report.vulnerabilitiesSummary?.HIGH || 0}</div>
            <span className="text-[10px] text-slate-400">CVSS 7.0 - 8.9</span>
          </div>

          <div className="bg-[#101726] border border-slate-800 rounded-xl p-4">
            <span className="text-[11px] font-semibold text-amber-400 uppercase tracking-wider">Medium CVEs</span>
            <div className="text-2xl font-black text-amber-400 mt-1 mono">{report.vulnerabilitiesSummary?.MEDIUM || 0}</div>
            <span className="text-[10px] text-slate-400">CVSS 4.0 - 6.9</span>
          </div>

          <div className="bg-[#101726] border border-slate-800 rounded-xl p-4">
            <span className="text-[11px] font-semibold text-indigo-400 uppercase tracking-wider">Distinct Licenses</span>
            <div className="text-2xl font-black text-indigo-300 mt-1 mono">
              {Object.keys(report.licenseBreakdown || {}).length}
            </div>
            <span className="text-[10px] text-slate-400">Permissive & Copyleft</span>
          </div>

          <div className="bg-[#101726] border border-slate-800 rounded-xl p-4">
            <span className="text-[11px] font-semibold text-cyan-400 uppercase tracking-wider">Scan Duration</span>
            <div className="text-2xl font-black text-cyan-400 mt-1 mono">{report.scanDurationMs}ms</div>
            <span className="text-[10px] text-slate-400">End-to-end latency</span>
          </div>
        </div>
      )}

      {/* Main Content Columns: Policy Violations & Discovered CVEs */}
      {report && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* OPA Policy Compliance Breakdown */}
          <div className="bg-[#101726] border border-slate-800 rounded-2xl p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <FileCheck className="h-5 w-5 text-cyan-400" />
                <h3 className="font-bold text-white text-base">OPA Policy Evaluation Results</h3>
              </div>
              <button
                onClick={() => setActiveTab('policy')}
                className="text-xs text-cyan-400 hover:text-cyan-300 font-semibold"
              >
                Policy Studio →
              </button>
            </div>

            <div className="space-y-3">
              {report.policyResults?.map((policy) => (
                <div
                  key={policy.ruleId}
                  className={`p-4 rounded-xl border transition ${
                    policy.passed
                      ? 'bg-slate-900/50 border-slate-800/80'
                      : 'bg-rose-950/20 border-rose-500/30'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2.5">
                      {policy.passed ? (
                        <CheckCircle2 className="h-5 w-5 text-emerald-400 shrink-0" />
                      ) : (
                        <XCircle className="h-5 w-5 text-rose-400 shrink-0" />
                      )}
                      <div>
                        <span className="text-xs font-bold text-slate-200 mono mr-2">{policy.ruleId}</span>
                        <span className="text-sm font-semibold text-white">{policy.ruleName}</span>
                      </div>
                    </div>
                    <span
                      className={`text-[10px] font-extrabold px-2 py-0.5 rounded-full uppercase ${
                        policy.passed
                          ? 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                          : 'bg-rose-950 text-rose-400 border border-rose-800'
                      }`}
                    >
                      {policy.passed ? 'PASS' : 'VIOLATION'}
                    </span>
                  </div>

                  <p className="text-xs text-slate-400 mt-1 pl-7">{policy.description}</p>

                  {/* Violation details */}
                  {policy.violations && policy.violations.length > 0 && (
                    <div className="mt-3 pl-7 space-y-2">
                      {policy.violations.map((v, idx) => (
                        <div key={idx} className="p-2.5 rounded-lg bg-rose-950/40 border border-rose-500/20 text-xs text-slate-300">
                          <p className="font-semibold text-rose-300">{v.message}</p>
                          {v.details && <p className="text-[11px] text-slate-400 mt-1">{v.details}</p>}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* High Priority Vulnerabilities */}
          <div className="bg-[#101726] border border-slate-800 rounded-2xl p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-orange-400" />
                <h3 className="font-bold text-white text-base">Vulnerabilities Detected ({getAllVulns(report).length})</h3>
              </div>
              <button
                onClick={() => setActiveTab('graph')}
                className="text-xs text-cyan-400 hover:text-cyan-300 font-semibold"
              >
                View in Dgraph →
              </button>
            </div>

            <div className="space-y-3 max-h-[440px] overflow-y-auto pr-1">
              {getAllVulns(report).length === 0 ? (
                <div className="text-center py-12 text-slate-400 text-xs">
                  <ShieldCheck className="h-10 w-10 mx-auto text-emerald-400 mb-2 opacity-80" />
                  <p className="font-semibold text-slate-300">Clean Bill of Health!</p>
                  <p className="mt-1">No known vulnerabilities detected in scanned components.</p>
                </div>
              ) : (
                getAllVulns(report).map((v, i) => (
                  <div
                    key={`${v.id}-${i}`}
                    onClick={() => onOpenVuln(v)}
                    className="p-3.5 rounded-xl bg-slate-900/70 border border-slate-800 hover:border-cyan-500/40 cursor-pointer transition flex items-center justify-between group"
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-sm text-white mono group-hover:text-cyan-400 transition">
                          {v.id}
                        </span>
                        <span
                          className={`text-[10px] font-extrabold px-2 py-0.2 rounded-full border ${
                            v.severity === 'CRITICAL'
                              ? 'bg-rose-950 text-rose-400 border-rose-800'
                              : v.severity === 'HIGH'
                              ? 'bg-orange-950 text-orange-400 border-orange-800'
                              : 'bg-amber-950 text-amber-400 border-amber-800'
                          }`}
                        >
                          {v.severity} ({v.cvss?.toFixed(1)})
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 line-clamp-1">{v.title || v.description}</p>
                      <div className="flex items-center gap-2 text-[11px] text-slate-400">
                        <span>Package: <strong className="text-slate-300">{v.packageName}@{v.packageVersion}</strong></span>
                        {v.fixedVersion && (
                          <span className="text-emerald-400 font-semibold">• Fix: v{v.fixedVersion}</span>
                        )}
                      </div>
                    </div>
                    <ExternalLink className="h-4 w-4 text-slate-400 group-hover:text-cyan-400 transition shrink-0 ml-2" />
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function getAllVulns(report) {
  if (!report || !report.artifact?.components) return [];
  const list = [];
  report.artifact.components.forEach((c) => {
    (c.vulnerabilities || []).forEach((v) => {
      list.push({ ...v, packageName: c.name, packageVersion: c.version });
    });
  });
  return list;
}
