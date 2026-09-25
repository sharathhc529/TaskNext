import React, { useState } from 'react';
import { FileCode2, Play, CheckCircle2, XCircle, AlertTriangle, ShieldCheck, Copy, Check, Sparkles, BookOpen } from 'lucide-react';
import { evaluateCustomPolicy } from '../services/api';

const SAMPLE_CUSTOM_POLICY = `package sbom.custom.rule

import rego.v1

default allow := true

# Rule: Block any Axios package with version < 1.0.0
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
        "message": sprintf("Outdated legacy Axios version detected: %v@%v", [comp.name, comp.version]),
        "details": "Axios versions prior to 1.0.0 have known SSRF and prototype pollution quirks. Upgrade to v1.7+."
    }
}

allow := count(deny) == 0
`;

export default function PolicyStudio({ policies, artifact, onPolicyUpdated }) {
  const [activePolicyTab, setActivePolicyTab] = useState('editor'); // editor, registered
  const [regoCode, setRegoCode] = useState(SAMPLE_CUSTOM_POLICY);
  const [evaluating, setEvaluating] = useState(false);
  const [evalResult, setEvalResult] = useState(null);
  const [copied, setCopied] = useState(false);

  const handleTestPolicy = async () => {
    if (!artifact) {
      alert('Please run a scan on the Dashboard first so there is an active SBOM artifact to test against.');
      return;
    }
    setEvaluating(true);
    setEvalResult(null);
    try {
      const res = await evaluateCustomPolicy(regoCode, artifact);
      setEvalResult(res);
    } catch (err) {
      setEvalResult({
        passed: false,
        error: err.message,
      });
    } finally {
      setEvaluating(false);
    }
  };

  const copyRego = () => {
    navigator.clipboard.writeText(regoCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-6 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 bg-[#101726] border border-slate-800 p-4 rounded-2xl shadow-lg">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
            <FileCode2 className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-bold text-white">OPA (Open Policy Agent) Studio</h2>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-950 text-indigo-300 border border-indigo-800">
                Rego v1 Policy-as-Code
              </span>
            </div>
            <p className="text-xs text-slate-400">Write, test, and enforce security & license guardrails before software delivery</p>
          </div>
        </div>

        <div className="flex items-center gap-2 bg-slate-900 p-1 rounded-xl border border-slate-800">
          <button
            onClick={() => setActivePolicyTab('editor')}
            className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
              activePolicyTab === 'editor' ? 'bg-cyan-500/20 text-cyan-400' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Live Rego Editor & Tester
          </button>
          <button
            onClick={() => setActivePolicyTab('registered')}
            className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
              activePolicyTab === 'registered' ? 'bg-cyan-500/20 text-cyan-400' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Built-in Policies ({policies?.length || 0})
          </button>
        </div>
      </div>

      {activePolicyTab === 'editor' ? (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Rego Code Editor */}
          <div className="lg:col-span-7 bg-[#101726] border border-slate-800 rounded-2xl p-5 shadow-xl flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-3">
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-cyan-400"></span>
                  <span className="text-xs font-bold text-white uppercase tracking-wider">Custom Policy (Rego)</span>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={copyRego}
                    className="p-1.5 text-xs text-slate-400 hover:text-white rounded-lg bg-slate-900 border border-slate-800 transition"
                    title="Copy Rego Code"
                  >
                    {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                  </button>
                </div>
              </div>

              <div className="relative">
                <textarea
                  value={regoCode}
                  onChange={(e) => setRegoCode(e.target.value)}
                  className="w-full h-96 bg-[#0a0e17] border border-slate-800 rounded-xl p-4 text-xs font-mono text-cyan-300 focus:outline-none focus:border-cyan-500 transition leading-relaxed resize-none"
                  spellCheck="false"
                />
              </div>
            </div>

            <div className="flex items-center justify-between pt-4 border-t border-slate-800 mt-4">
              <span className="text-[11px] text-slate-400">
                Evaluating against active artifact: <strong className="text-slate-200">{artifact?.name || 'None (Run scan first)'}</strong>
              </span>
              <button
                onClick={handleTestPolicy}
                disabled={evaluating || !artifact}
                className="flex items-center gap-2 bg-gradient-to-r from-cyan-500 to-indigo-600 hover:from-cyan-400 hover:to-indigo-500 text-white text-xs font-bold py-2.5 px-4 rounded-xl shadow-lg shadow-cyan-500/20 transition disabled:opacity-50"
              >
                <Play className="h-3.5 w-3.5 fill-current" />
                <span>{evaluating ? 'Evaluating OPA...' : 'Execute Policy against SBOM'}</span>
              </button>
            </div>
          </div>

          {/* Policy Evaluation Output */}
          <div className="lg:col-span-5 bg-[#101726] border border-slate-800 rounded-2xl p-5 shadow-xl flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-3">
                <div className="flex items-center gap-2">
                  <Sparkles className="h-4 w-4 text-indigo-400" />
                  <h3 className="text-xs font-bold text-white uppercase tracking-wider">Evaluation Output</h3>
                </div>
                {evalResult && (
                  <span
                    className={`text-[10px] font-extrabold px-2 py-0.5 rounded-full ${
                      evalResult.passed
                        ? 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                        : 'bg-rose-950 text-rose-400 border border-rose-800'
                    }`}
                  >
                    {evalResult.passed ? 'PASSED (ALLOW)' : 'FAILED (DENY)'}
                  </span>
                )}
              </div>

              {evalResult ? (
                <div className="space-y-4 animate-fadeIn">
                  {evalResult.error ? (
                    <div className="p-3.5 rounded-xl bg-rose-950/40 border border-rose-500/30 text-rose-300 text-xs">
                      <div className="flex items-center gap-2 font-bold mb-1">
                        <AlertTriangle className="h-4 w-4" />
                        <span>Rego Compilation Error</span>
                      </div>
                      <p className="font-mono text-[11px] break-all">{evalResult.error}</p>
                    </div>
                  ) : evalResult.passed ? (
                    <div className="p-4 rounded-xl bg-emerald-950/30 border border-emerald-500/30 text-emerald-300 text-xs text-center py-8">
                      <ShieldCheck className="h-10 w-10 mx-auto text-emerald-400 mb-2" />
                      <h4 className="font-bold text-sm">Policy Passed!</h4>
                      <p className="text-slate-400 text-[11px] mt-1">Zero rule violations detected for this custom policy.</p>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      <div className="p-3 rounded-xl bg-rose-950/30 border border-rose-500/30 text-xs text-rose-300">
                        <div className="flex items-center gap-2 font-bold mb-1">
                          <XCircle className="h-4 w-4" />
                          <span>Violations Found</span>
                        </div>
                        <p className="text-[11px] text-slate-300">The custom policy triggered deny conditions on the current SBOM.</p>
                      </div>

                      <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
                        {evalResult.results?.[evalResult.results.length - 1]?.violations?.map((v, i) => (
                          <div key={i} className="p-3 rounded-xl bg-slate-900 border border-slate-800 text-xs space-y-1">
                            <div className="flex justify-between">
                              <span className="font-bold text-white mono">{v.componentName}@{v.componentVersion}</span>
                              <span className="text-[10px] text-rose-400 font-bold uppercase">{v.severity}</span>
                            </div>
                            <p className="text-slate-300">{v.message}</p>
                            {v.details && <p className="text-[11px] text-slate-400">{v.details}</p>}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-center py-16 text-slate-400 text-xs">
                  <FileCode2 className="h-10 w-10 mx-auto mb-2 opacity-40 text-cyan-400" />
                  <p className="text-slate-300 font-semibold">Test Your Custom Rego</p>
                  <p className="text-slate-400 text-[11px] mt-1 max-w-xs mx-auto">
                    Click "Execute Policy against SBOM" to compile and run the Rego rule in real-time.
                  </p>
                </div>
              )}
            </div>

            <div className="p-3 rounded-xl bg-slate-900/70 border border-slate-800 text-[11px] text-slate-400">
              <strong className="text-slate-300">Policy Tip:</strong> Use OPA's <code className="text-cyan-400">input.components</code> to iterate over software packages, check licenses, or inspect CVE scores.
            </div>
          </div>
        </div>
      ) : (
        /* Built-in Policies Catalog */
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {policies?.map((rule) => (
            <div key={rule.id} className="bg-[#101726] border border-slate-800 rounded-2xl p-5 shadow-xl space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-cyan-400 mono">{rule.id}</span>
                  <span className="text-sm font-bold text-white">{rule.name}</span>
                </div>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-slate-900 text-slate-300 border border-slate-800 uppercase">
                  {rule.category}
                </span>
              </div>
              <p className="text-xs text-slate-400">{rule.description}</p>
              <div className="bg-[#0a0e17] rounded-xl p-3 border border-slate-800">
                <pre className="text-[11px] font-mono text-slate-300 overflow-x-auto max-h-48">
                  {rule.regoCode}
                </pre>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
