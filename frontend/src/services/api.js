const API_BASE = '/api';

export async function checkHealth() {
  const res = await fetch(`${API_BASE}/health`);
  return res.json();
}

export async function fetchSamples() {
  const res = await fetch(`${API_BASE}/samples`);
  return res.json();
}

export async function runScan({ targetType, sampleName, content, fileName, customRego }) {
  const res = await fetch(`${API_BASE}/scan`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      targetType: targetType || (sampleName ? 'sample' : 'manifest'),
      sampleName,
      content,
      fileName,
      customRego,
    }),
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ error: 'Scan request failed' }));
    throw new Error(errorData.error || 'Scan request failed');
  }

  return res.json();
}

export async function uploadFileScan(file, customRego) {
  const formData = new FormData();
  formData.append('file', file);
  if (customRego) {
    formData.append('customRego', customRego);
  }

  const res = await fetch(`${API_BASE}/scan`, {
    method: 'POST',
    body: formData,
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ error: 'File upload failed' }));
    throw new Error(errorData.error || 'File upload failed');
  }

  return res.json();
}

export async function fetchGraph(artifactId) {
  const res = await fetch(`${API_BASE}/graph/${artifactId}`);
  if (!res.ok) {
    throw new Error('Failed to fetch dependency graph');
  }
  return res.json();
}

export async function fetchPolicies() {
  const res = await fetch(`${API_BASE}/policies`);
  if (!res.ok) {
    throw new Error('Failed to fetch OPA policies');
  }
  return res.json();
}

export async function evaluateCustomPolicy(regoCode, artifact) {
  const res = await fetch(`${API_BASE}/policies/evaluate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ regoCode, artifact }),
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({ error: 'Policy evaluation failed' }));
    throw new Error(errorData.error || 'Policy evaluation failed');
  }

  return res.json();
}
