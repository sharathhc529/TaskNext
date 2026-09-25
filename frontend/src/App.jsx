import React, { useState, useEffect } from 'react';
import Navbar from './components/Navbar';
import Dashboard from './components/Dashboard';
import GraphViewer from './components/GraphViewer';
import SbomTable from './components/SbomTable';
import PolicyStudio from './components/PolicyStudio';
import CicdPipeline from './components/CicdPipeline';
import VulnerabilityModal from './components/VulnerabilityModal';
import { fetchSamples, fetchPolicies, runScan, uploadFileScan, fetchGraph } from './services/api';
import { ShieldCheck, AlertCircle } from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [samples, setSamples] = useState([]);
  const [selectedSample, setSelectedSample] = useState('vulnerable-node');
  const [policies, setPolicies] = useState([]);
  const [report, setReport] = useState(null);
  const [graphData, setGraphData] = useState(null);
  const [isScanning, setIsScanning] = useState(false);
  const [activeVuln, setActiveVuln] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    loadInitialData();
  }, []);

  const loadInitialData = async () => {
    try {
      const [sampleList, policyList] = await Promise.all([
        fetchSamples().catch(() => []),
        fetchPolicies().catch(() => []),
      ]);
      setSamples(sampleList);
      if (sampleList.length > 0) {
        setSelectedSample(sampleList[0].id);
      }
      setPolicies(policyList);

      // Trigger automatic initial scan on the first sample for instant live demo experience
      if (sampleList.length > 0) {
        executeScan(sampleList[0].id);
      }
    } catch (err) {
      console.error('Initialization error:', err);
    }
  };

  const executeScan = async (sampleId) => {
    setIsScanning(true);
    setErrorMessage(null);
    try {
      const scanReport = await runScan({
        targetType: 'sample',
        sampleName: sampleId || selectedSample,
      });
      setReport(scanReport);

      // Fetch Dgraph dependency tree
      const graph = await fetchGraph(scanReport.artifact.id);
      setGraphData(graph);
    } catch (err) {
      setErrorMessage(err.message || 'Failed to execute SBOM scan');
    } finally {
      setIsScanning(false);
    }
  };

  const handleUploadFile = async (file) => {
    setIsScanning(true);
    setErrorMessage(null);
    try {
      const scanReport = await uploadFileScan(file);
      setReport(scanReport);

      const graph = await fetchGraph(scanReport.artifact.id);
      setGraphData(graph);
      setActiveTab('dashboard');
    } catch (err) {
      setErrorMessage(err.message || 'Failed to scan uploaded file');
    } finally {
      setIsScanning(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col text-slate-100 selection:bg-cyan-500 selection:text-white">
      {/* Top Navigation */}
      <Navbar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        report={report}
        isScanning={isScanning}
      />

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {errorMessage && (
          <div className="mb-6 p-4 rounded-xl bg-rose-950/50 border border-rose-500/40 text-rose-300 flex items-center gap-3 animate-fadeIn">
            <AlertCircle className="h-5 w-5 shrink-0" />
            <div className="text-xs font-semibold">{errorMessage}</div>
          </div>
        )}

        {activeTab === 'dashboard' && (
          <Dashboard
            report={report}
            samples={samples}
            selectedSample={selectedSample}
            setSelectedSample={setSelectedSample}
            onRunScan={executeScan}
            onUploadFile={handleUploadFile}
            isScanning={isScanning}
            onOpenVuln={setActiveVuln}
            setActiveTab={setActiveTab}
          />
        )}

        {activeTab === 'graph' && (
          <GraphViewer
            graphData={graphData}
            onOpenVuln={setActiveVuln}
          />
        )}

        {activeTab === 'sbom' && (
          <SbomTable
            artifact={report?.artifact}
            onOpenVuln={setActiveVuln}
          />
        )}

        {activeTab === 'policy' && (
          <PolicyStudio
            policies={policies}
            artifact={report?.artifact}
            onPolicyUpdated={loadInitialData}
          />
        )}

        {activeTab === 'pipeline' && (
          <CicdPipeline
            report={report}
            onRunScan={executeScan}
            selectedSample={selectedSample}
            isScanning={isScanning}
          />
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-[#090d16] py-6 text-center text-xs text-slate-400">
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4 text-cyan-400" />
            <span>SBOMGuard • Go 1.24, React, Open Policy Agent (OPA), Dgraph & OSV.dev</span>
          </div>
          <div className="text-[11px] text-slate-400">
            Software Supply Chain Security Hands-on Developer Kit
          </div>
        </div>
      </footer>

      {/* Vulnerability Drilldown Modal */}
      {activeVuln && (
        <VulnerabilityModal
          vuln={activeVuln}
          onClose={() => setActiveVuln(null)}
        />
      )}
    </div>
  );
}
