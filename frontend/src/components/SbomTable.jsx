import React, { useState, useMemo } from 'react';
import { Layers, Search, Filter, ShieldAlert, ShieldCheck, Download, ExternalLink, Code } from 'lucide-react';

export default function SbomTable({ artifact, onOpenVuln }) {
  const [searchTerm, setSearchTerm] = useState('');
  const [filterEcosystem, setFilterEcosystem] = useState('all');
  const [filterDepType, setFilterDepType] = useState('all'); // all, direct, transitive
  const [filterVulnOnly, setFilterVulnOnly] = useState(false);

  const components = artifact?.components || [];

  const filteredComponents = useMemo(() => {
    return components.filter((c) => {
      const matchSearch =
        c.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        c.purl?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        c.license?.toLowerCase().includes(searchTerm.toLowerCase());

      const matchEco = filterEcosystem === 'all' || c.ecosystem?.toLowerCase() === filterEcosystem.toLowerCase();
      const matchDep =
        filterDepType === 'all' ||
        (filterDepType === 'direct' && c.direct) ||
        (filterDepType === 'transitive' && !c.direct);

      const matchVuln = !filterVulnOnly || (c.vulnerabilities && c.vulnerabilities.length > 0);

      return matchSearch && matchEco && matchDep && matchVuln;
    });
  }, [components, searchTerm, filterEcosystem, filterDepType, filterVulnOnly]);

  const downloadSbomJSON = () => {
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(artifact, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `${artifact?.name || 'artifact'}-sbom.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  if (!artifact || components.length === 0) {
    return (
      <div className="bg-[#101726] border border-slate-800 rounded-2xl p-12 text-center text-slate-400">
        <Layers className="h-12 w-12 mx-auto text-slate-600 mb-3" />
        <h3 className="text-base font-bold text-slate-300">No SBOM Components to Display</h3>
        <p className="text-xs mt-1">Run a scan in the Dashboard to generate and explore the full Software Bill of Materials.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4 animate-fadeIn">
      {/* Header Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4 bg-[#101726] border border-slate-800 p-4 rounded-2xl shadow-lg">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
            <Layers className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-bold text-white">SBOM Inventory Explorer</h2>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-cyan-950 text-cyan-400 border border-cyan-800">
                {components.length} Total Components
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Artifact: <span className="text-slate-200 font-semibold">{artifact.name} v{artifact.version}</span>
            </p>
          </div>
        </div>

        <button
          onClick={downloadSbomJSON}
          className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-slate-900 border border-slate-700 hover:border-cyan-500/40 text-slate-200 hover:text-white text-xs font-semibold shadow transition"
        >
          <Download className="h-4 w-4 text-cyan-400" />
          Export JSON SBOM
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {/* Search */}
        <div className="relative">
          <Search className="absolute left-3 top-3 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search packages, PURL, licenses..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full bg-[#101726] border border-slate-800 rounded-xl pl-9 pr-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500 transition"
          />
        </div>

        {/* Ecosystem Filter */}
        <div>
          <select
            value={filterEcosystem}
            onChange={(e) => setFilterEcosystem(e.target.value)}
            className="w-full bg-[#101726] border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500 transition"
          >
            <option value="all">All Ecosystems</option>
            <option value="npm">npm</option>
            <option value="golang">Golang</option>
            <option value="pypi">PyPI</option>
            <option value="maven">Maven</option>
          </select>
        </div>

        {/* Dependency Type */}
        <div>
          <select
            value={filterDepType}
            onChange={(e) => setFilterDepType(e.target.value)}
            className="w-full bg-[#101726] border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500 transition"
          >
            <option value="all">Direct & Transitive</option>
            <option value="direct">Direct Dependencies Only</option>
            <option value="transitive">Transitive Dependencies Only</option>
          </select>
        </div>

        {/* Vulnerability Toggle */}
        <div className="flex items-center">
          <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer bg-[#101726] border border-slate-800 rounded-xl px-3 py-2 w-full">
            <input
              type="checkbox"
              checked={filterVulnOnly}
              onChange={(e) => setFilterVulnOnly(e.target.checked)}
              className="rounded bg-slate-900 border-slate-700 text-cyan-500 focus:ring-0 cursor-pointer"
            />
            <span className="font-semibold text-rose-400">Vulnerable Components Only</span>
          </label>
        </div>
      </div>

      {/* Table */}
      <div className="bg-[#101726] border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-slate-300">
            <thead className="bg-[#0b101b] border-b border-slate-800 text-slate-400 uppercase tracking-wider font-semibold">
              <tr>
                <th className="py-3 px-4">Component / Package</th>
                <th className="py-3 px-4">Version</th>
                <th className="py-3 px-4">Ecosystem</th>
                <th className="py-3 px-4">License</th>
                <th className="py-3 px-4">Scope</th>
                <th className="py-3 px-4">Vulnerabilities</th>
                <th className="py-3 px-4">PURL Identifier</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-medium">
              {filteredComponents.length === 0 ? (
                <tr>
                  <td colSpan="7" className="py-8 text-center text-slate-400">
                    No components matching the current filter criteria.
                  </td>
                </tr>
              ) : (
                filteredComponents.map((c) => {
                  const vulns = c.vulnerabilities || [];
                  const hasCrit = vulns.some((v) => v.severity === 'CRITICAL');
                  const hasHigh = vulns.some((v) => v.severity === 'HIGH');
                  const isCopyleft = c.license?.includes('GPL') || c.license?.includes('AGPL');

                  return (
                    <tr key={c.id} className="hover:bg-slate-900/50 transition">
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <Code className="h-4 w-4 text-cyan-400 shrink-0" />
                          <span className="font-bold text-white mono">{c.name}</span>
                        </div>
                      </td>
                      <td className="py-3 px-4 mono text-slate-300">
                        {c.version}
                      </td>
                      <td className="py-3 px-4">
                        <span className="px-2 py-0.5 rounded bg-slate-900 text-slate-300 border border-slate-800 text-[10px] uppercase font-bold">
                          {c.ecosystem}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <span
                          className={`px-2 py-0.5 rounded text-[11px] font-semibold border ${
                            isCopyleft
                              ? 'bg-rose-950 text-rose-300 border-rose-800'
                              : c.license === 'Unknown'
                              ? 'bg-amber-950 text-amber-300 border-amber-800'
                              : 'bg-indigo-950 text-indigo-300 border-indigo-800'
                          }`}
                        >
                          {c.license || 'Unknown'}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                            c.direct
                              ? 'bg-sky-950 text-sky-400 border border-sky-800'
                              : 'bg-slate-900 text-slate-400 border border-slate-800'
                          }`}
                        >
                          {c.direct ? 'DIRECT' : 'TRANSITIVE'}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        {vulns.length === 0 ? (
                          <div className="flex items-center gap-1.5 text-emerald-400 text-[11px] font-semibold">
                            <ShieldCheck className="h-4 w-4" />
                            <span>Clean</span>
                          </div>
                        ) : (
                          <div className="flex flex-wrap gap-1.5">
                            {vulns.map((v, i) => (
                              <button
                                key={i}
                                onClick={() => onOpenVuln({ ...v, packageName: c.name, packageVersion: c.version })}
                                className={`flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold border transition ${
                                  v.severity === 'CRITICAL'
                                    ? 'bg-rose-950 text-rose-300 border-rose-700 hover:bg-rose-900'
                                    : v.severity === 'HIGH'
                                    ? 'bg-orange-950 text-orange-300 border-orange-700 hover:bg-orange-900'
                                    : 'bg-amber-950 text-amber-300 border-amber-700 hover:bg-amber-900'
                                }`}
                              >
                                <span>{v.id}</span>
                                <ExternalLink className="h-3 w-3" />
                              </button>
                            ))}
                          </div>
                        )}
                      </td>
                      <td className="py-3 px-4 text-slate-400 mono text-[11px] max-w-xs truncate" title={c.purl}>
                        {c.purl}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
