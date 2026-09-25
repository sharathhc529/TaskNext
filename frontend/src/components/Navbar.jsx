import React from 'react';
import { ShieldCheck, ShieldAlert, GitBranch, Terminal, Cpu, FileCode2, Layers } from 'lucide-react';

export default function Navbar({ activeTab, setActiveTab, report, isScanning }) {
  const navItems = [
    { id: 'dashboard', label: 'Dashboard & Scanner', icon: Cpu },
    { id: 'graph', label: 'Dgraph Dependency Tree', icon: GitBranch },
    { id: 'sbom', label: 'SBOM Components', icon: Layers },
    { id: 'policy', label: 'OPA Policy Studio', icon: FileCode2 },
    { id: 'pipeline', label: 'CI/CD Delivery Gate', icon: Terminal },
  ];

  return (
    <header className="border-b border-slate-800 bg-[#0c121e]/90 backdrop-blur sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo & Brand */}
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-tr from-cyan-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-cyan-500/20">
              <ShieldCheck className="h-6 w-6 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold text-lg tracking-tight bg-gradient-to-r from-cyan-400 via-sky-300 to-indigo-300 bg-clip-text text-transparent">
                  SBOMGuard
                </span>
                <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded bg-cyan-950/80 text-cyan-400 border border-cyan-800/50">
                  Go • OPA • Dgraph
                </span>
              </div>
              <p className="text-xs text-slate-400">Software Supply Chain Security & Delivery Engine</p>
            </div>
          </div>

          {/* Navigation Tabs */}
          <nav className="hidden md:flex items-center space-x-1">
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id)}
                  className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-sm font-medium transition-all ${
                    isActive
                      ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/30 shadow-sm shadow-cyan-500/10'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                  }`}
                >
                  <Icon className={`h-4 w-4 ${isActive ? 'text-cyan-400' : 'text-slate-400'}`} />
                  {item.label}
                  {item.id === 'sbom' && report?.totalPackages > 0 && (
                    <span className="ml-1 px-1.5 py-0.2 rounded-full text-[10px] bg-slate-800 text-slate-300 border border-slate-700">
                      {report.totalPackages}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>

          {/* Gate Status Pill */}
          <div className="flex items-center gap-3">
            {isScanning ? (
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-cyan-950/60 border border-cyan-500/40 text-cyan-400 text-xs font-semibold animate-pulse">
                <span className="h-2 w-2 rounded-full bg-cyan-400"></span>
                Scanning Artifact...
              </div>
            ) : report ? (
              <div
                className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-bold border ${
                  report.passedGate
                    ? 'bg-emerald-950/60 text-emerald-400 border-emerald-500/30 shadow-sm shadow-emerald-500/10'
                    : 'bg-rose-950/60 text-rose-400 border-rose-500/30 shadow-sm shadow-rose-500/10'
                }`}
              >
                {report.passedGate ? (
                  <>
                    <ShieldCheck className="h-4 w-4 text-emerald-400" />
                    <span>GATE: PASSED</span>
                  </>
                ) : (
                  <>
                    <ShieldAlert className="h-4 w-4 text-rose-400" />
                    <span>GATE: BLOCKED</span>
                  </>
                )}
              </div>
            ) : (
              <div className="text-xs text-slate-400 px-3 py-1 rounded bg-slate-900 border border-slate-800">
                Ready for Scan
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
