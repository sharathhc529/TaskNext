import React, { useEffect, useRef, useState } from 'react';
import { Network } from 'vis-network';
import { GitBranch, ShieldAlert, Key, Filter, Maximize2, RefreshCw, ZoomIn, ZoomOut, Info } from 'lucide-react';

export default function GraphViewer({ graphData, onSelectNode, onOpenVuln }) {
  const containerRef = useRef(null);
  const networkRef = useRef(null);
  const [selectedDetails, setSelectedDetails] = useState(null);
  const [filterType, setFilterType] = useState('all'); // all, vulns, licenses, packages
  const [physicsEnabled, setPhysicsEnabled] = useState(true);

  useEffect(() => {
    if (!containerRef.current || !graphData || !graphData.nodes || graphData.nodes.length === 0) {
      return;
    }

    // Transform graphData into vis-network DataSet format
    const visNodes = graphData.nodes
      .filter((n) => {
        if (filterType === 'all') return true;
        if (filterType === 'vulns') return n.type === 'vulnerability' || n.type === 'package' || n.type === 'artifact';
        if (filterType === 'licenses') return n.type === 'license' || n.type === 'package' || n.type === 'artifact';
        if (filterType === 'packages') return n.type === 'package' || n.type === 'artifact';
        return true;
      })
      .map((node) => {
        let color = {
          background: '#1e293b',
          border: '#475569',
          highlight: { background: '#334155', border: '#06b6d4' }
        };
        let shape = 'dot';
        let size = 16;
        let font = { color: '#f8fafc', face: 'Plus Jakarta Sans', size: 12 };

        if (node.type === 'artifact') {
          color = {
            background: '#0284c7',
            border: '#38bdf8',
            highlight: { background: '#0369a1', border: '#7dd3fc' }
          };
          shape = 'hexagon';
          size = 28;
          font = { color: '#ffffff', face: 'Plus Jakarta Sans', size: 14, bold: true };
        } else if (node.type === 'vulnerability') {
          const isCrit = node.severity === 'CRITICAL';
          color = {
            background: isCrit ? '#e11d48' : '#ea580c',
            border: isCrit ? '#fda4af' : '#fdba74',
            highlight: { background: '#be123c', border: '#ffffff' }
          };
          shape = 'diamond';
          size = 20;
          font = { color: '#ffffff', face: 'JetBrains Mono', size: 11, bold: true };
        } else if (node.type === 'license') {
          color = {
            background: '#7c3aed',
            border: '#c4b5fd',
            highlight: { background: '#6d28d9', border: '#e9d5ff' }
          };
          shape = 'triangle';
          size = 14;
          font = { color: '#e2e8f0', face: 'Plus Jakarta Sans', size: 10 };
        } else if (node.type === 'package') {
          if (node.severity === 'CRITICAL') {
            color = { background: '#881337', border: '#f43f5e' };
          } else if (node.severity === 'HIGH') {
            color = { background: '#7c2d12', border: '#f97316' };
          } else if (node.severity === 'MEDIUM') {
            color = { background: '#78350f', border: '#fbbf24' };
          } else {
            color = { background: '#0f766e', border: '#2dd4bf' };
          }
          shape = 'dot';
          size = node.data?.direct ? 18 : 13;
        }

        return {
          id: node.id,
          label: node.label,
          color,
          shape,
          size,
          font,
          rawNode: node,
        };
      });

    const activeNodeIds = new Set(visNodes.map((n) => n.id));

    const visEdges = (graphData.edges || [])
      .filter((e) => activeNodeIds.has(e.source) && activeNodeIds.has(e.target))
      .map((edge) => {
        let edgeColor = '#334155';
        let dashes = false;
        let arrows = 'to';

        if (edge.relation === 'HAS_VULNERABILITY') {
          edgeColor = '#f43f5e';
          dashes = true;
        } else if (edge.relation === 'LICENSED_UNDER') {
          edgeColor = '#a78bfa';
          dashes = [2, 4];
        } else if (edge.direct) {
          edgeColor = '#38bdf8';
        }

        return {
          id: edge.id,
          from: edge.source,
          to: edge.target,
          color: { color: edgeColor, highlight: '#06b6d4' },
          dashes,
          arrows: { to: { enabled: true, scaleFactor: 0.6 } },
          smooth: { type: 'continuous' },
        };
      });

    const options = {
      nodes: {
        borderWidth: 2,
        shadow: true,
      },
      edges: {
        width: 1.5,
        shadow: false,
      },
      physics: {
        enabled: physicsEnabled,
        barnesHut: {
          gravitationalConstant: -3500,
          centralGravity: 0.3,
          springLength: 95,
          springConstant: 0.04,
          damping: 0.09,
          avoidOverlap: 0.2,
        },
        stabilization: {
          iterations: 150,
        },
      },
      interaction: {
        hover: true,
        tooltipDelay: 200,
        hideEdgesOnDrag: false,
      },
    };

    const network = new Network(containerRef.current, { nodes: visNodes, edges: visEdges }, options);
    networkRef.current = network;

    network.on('click', (params) => {
      if (params.nodes.length > 0) {
        const nodeId = params.nodes[0];
        const selected = visNodes.find((n) => n.id === nodeId);
        if (selected) {
          setSelectedDetails(selected.rawNode);
          if (onSelectNode) onSelectNode(selected.rawNode);
        }
      } else {
        setSelectedDetails(null);
      }
    });

    return () => {
      if (networkRef.current) {
        networkRef.current.destroy();
      }
    };
  }, [graphData, filterType, physicsEnabled]);

  const fitNetwork = () => {
    if (networkRef.current) {
      networkRef.current.fit({ animation: { duration: 500, easingFunction: 'easeInOutQuad' } });
    }
  };

  const zoomIn = () => {
    if (networkRef.current) {
      const scale = networkRef.current.getScale();
      networkRef.current.moveTo({ scale: scale * 1.3, animation: { duration: 300 } });
    }
  };

  const zoomOut = () => {
    if (networkRef.current) {
      const scale = networkRef.current.getScale();
      networkRef.current.moveTo({ scale: scale * 0.7, animation: { duration: 300 } });
    }
  };

  if (!graphData || !graphData.nodes || graphData.nodes.length === 0) {
    return (
      <div className="bg-[#101726] border border-slate-800 rounded-2xl p-12 text-center text-slate-400">
        <GitBranch className="h-12 w-12 mx-auto text-slate-600 mb-3" />
        <h3 className="text-base font-bold text-slate-300">No Dependency Graph Available</h3>
        <p className="text-xs mt-1">Please execute a scan in the Dashboard tab first to generate the Dgraph graph representation.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4 animate-fadeIn">
      {/* Top Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 bg-[#101726] border border-slate-800 p-4 rounded-2xl shadow-lg">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
            <GitBranch className="h-5 w-5" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-white">Dgraph Software Dependency Tree</h2>
            <p className="text-xs text-slate-400">Interactive graph representation of packages, transitive blast radius, and CVEs</p>
          </div>
        </div>

        {/* Filters and Controls */}
        <div className="flex items-center gap-2">
          {/* Legend Badges */}
          <div className="hidden xl:flex items-center gap-2 text-[11px] mr-2 px-3 py-1 rounded-lg bg-slate-900 border border-slate-800">
            <span className="flex items-center gap-1"><span className="h-2 w-2 rounded bg-sky-400"></span> Artifact</span>
            <span className="flex items-center gap-1"><span className="h-2 w-2 rounded-full bg-teal-500"></span> Package</span>
            <span className="flex items-center gap-1"><span className="h-2 w-2 rounded bg-rose-500"></span> CVE</span>
            <span className="flex items-center gap-1"><span className="h-2 w-2 rounded bg-purple-500"></span> License</span>
          </div>

          <div className="flex items-center gap-1 bg-slate-900 p-1 rounded-xl border border-slate-800">
            <button
              onClick={() => setFilterType('all')}
              className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                filterType === 'all' ? 'bg-cyan-500/20 text-cyan-400' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              All
            </button>
            <button
              onClick={() => setFilterType('vulns')}
              className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                filterType === 'vulns' ? 'bg-rose-500/20 text-rose-400' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              CVEs Only
            </button>
            <button
              onClick={() => setFilterType('licenses')}
              className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                filterType === 'licenses' ? 'bg-purple-500/20 text-purple-400' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Licenses
            </button>
          </div>

          {/* Graph Action Buttons */}
          <div className="flex items-center gap-1">
            <button
              onClick={zoomIn}
              className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800"
              title="Zoom In"
            >
              <ZoomIn className="h-4 w-4" />
            </button>
            <button
              onClick={zoomOut}
              className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800"
              title="Zoom Out"
            >
              <ZoomOut className="h-4 w-4" />
            </button>
            <button
              onClick={fitNetwork}
              className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800"
              title="Fit to Screen"
            >
              <Maximize2 className="h-4 w-4" />
            </button>
            <button
              onClick={() => setPhysicsEnabled(!physicsEnabled)}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold border transition ${
                physicsEnabled
                  ? 'bg-cyan-950 text-cyan-400 border-cyan-800'
                  : 'bg-slate-900 text-slate-400 border-slate-800'
              }`}
            >
              Physics: {physicsEnabled ? 'ON' : 'OFF'}
            </button>
          </div>
        </div>
      </div>

      {/* Main Canvas & Detail Sidebar */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-4">
        {/* Interactive Graph Canvas */}
        <div className="lg:col-span-3 bg-[#0a0e17] border border-slate-800 rounded-2xl overflow-hidden h-[620px] relative shadow-2xl">
          <div ref={containerRef} className="w-full h-full cursor-grab active:cursor-grabbing" />
          <div className="absolute bottom-3 left-3 bg-slate-900/90 backdrop-blur px-3 py-1.5 rounded-lg border border-slate-800 text-[11px] text-slate-400 pointer-events-none">
            Click any node to inspect details • Scroll to zoom • Drag to pan
          </div>
        </div>

        {/* Selected Node Inspector Sidebar */}
        <div className="bg-[#101726] border border-slate-800 rounded-2xl p-5 shadow-xl h-[620px] overflow-y-auto flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-2 pb-3 border-b border-slate-800 mb-4">
              <Info className="h-4 w-4 text-cyan-400" />
              <h3 className="font-bold text-white text-sm">Node Inspector</h3>
            </div>

            {selectedDetails ? (
              <div className="space-y-4 text-xs animate-fadeIn">
                <div>
                  <span className="text-[10px] uppercase font-extrabold tracking-wider text-slate-400">Node Type</span>
                  <div className="mt-1">
                    <span
                      className={`px-2 py-0.5 rounded text-[11px] font-bold uppercase ${
                        selectedDetails.type === 'vulnerability'
                          ? 'bg-rose-950 text-rose-400 border border-rose-800'
                          : selectedDetails.type === 'artifact'
                          ? 'bg-sky-950 text-sky-400 border border-sky-800'
                          : selectedDetails.type === 'license'
                          ? 'bg-purple-950 text-purple-400 border border-purple-800'
                          : 'bg-teal-950 text-teal-400 border border-teal-800'
                      }`}
                    >
                      {selectedDetails.type}
                    </span>
                  </div>
                </div>

                <div>
                  <span className="text-[10px] uppercase font-extrabold tracking-wider text-slate-400">Label / Name</span>
                  <p className="text-white font-bold mt-0.5 text-sm mono break-words">{selectedDetails.label}</p>
                </div>

                {selectedDetails.type === 'package' && (
                  <>
                    <div>
                      <span className="text-[10px] uppercase font-extrabold tracking-wider text-slate-400">PURL</span>
                      <p className="text-cyan-400 font-mono text-[11px] bg-slate-900 p-2 rounded border border-slate-800 break-all mt-1">
                        {selectedDetails.data?.purl}
                      </p>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <div className="bg-slate-900 p-2 rounded border border-slate-800">
                        <span className="text-slate-400 block text-[10px]">License</span>
                        <span className="font-bold text-slate-200">{selectedDetails.data?.license || 'Unknown'}</span>
                      </div>
                      <div className="bg-slate-900 p-2 rounded border border-slate-800">
                        <span className="text-slate-400 block text-[10px]">Dependency</span>
                        <span className="font-bold text-slate-200">{selectedDetails.data?.direct ? 'Direct' : 'Transitive'}</span>
                      </div>
                    </div>

                    {selectedDetails.data?.vulnerabilities && selectedDetails.data.vulnerabilities.length > 0 && (
                      <div>
                        <span className="text-[10px] uppercase font-extrabold tracking-wider text-rose-400">
                          Vulnerabilities ({selectedDetails.data.vulnerabilities.length})
                        </span>
                        <div className="space-y-1.5 mt-1">
                          {selectedDetails.data.vulnerabilities.map((v, i) => (
                            <div
                              key={i}
                              onClick={() => onOpenVuln(v)}
                              className="p-2 rounded bg-rose-950/40 border border-rose-500/30 text-[11px] cursor-pointer hover:border-rose-400 transition"
                            >
                              <div className="flex justify-between font-bold text-rose-300">
                                <span>{v.id}</span>
                                <span>CVSS {v.cvss?.toFixed(1)}</span>
                              </div>
                              <p className="text-slate-400 line-clamp-1 mt-0.5">{v.title || v.description}</p>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </>
                )}

                {selectedDetails.type === 'vulnerability' && (
                  <>
                    <div className="bg-slate-900 p-3 rounded-lg border border-slate-800 space-y-2">
                      <div className="flex justify-between">
                        <span className="text-slate-400">Severity</span>
                        <span className="font-bold text-rose-400">{selectedDetails.data?.severity}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-400">CVSS Score</span>
                        <span className="font-bold text-white mono">{selectedDetails.data?.cvss?.toFixed(1)} / 10.0</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-400">Fixed In</span>
                        <span className="font-bold text-emerald-400 mono">{selectedDetails.data?.fixedVersion || 'N/A'}</span>
                      </div>
                    </div>

                    <button
                      onClick={() => onOpenVuln(selectedDetails.data)}
                      className="w-full py-2 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs shadow transition"
                    >
                      View Full Vulnerability Report
                    </button>
                  </>
                )}
              </div>
            ) : (
              <div className="text-center py-16 text-slate-400 text-xs">
                <Info className="h-8 w-8 mx-auto mb-2 opacity-40 text-cyan-400" />
                <p>Click on any package, license, or vulnerability node in the graph to inspect metadata and relationships.</p>
              </div>
            )}
          </div>

          <div className="pt-3 border-t border-slate-800 text-[11px] text-slate-400">
            Dgraph Model: <span className="text-slate-300 font-semibold">Artifact → Package → CVE & License</span>
          </div>
        </div>
      </div>
    </div>
  );
}
