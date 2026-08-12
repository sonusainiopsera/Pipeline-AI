export interface AnalyzedLog {
  id: number;
  logText: string;
  category: string;
  rootCause: string;
  suggestedFix: string;
  customerUpdate: string;
  severity: string;
  confidence: number;
  createdAt: string;
}

const BASE = '/api';

export async function analyze(logText: string): Promise<AnalyzedLog> {
  const res = await fetch(`${BASE}/analyze`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ logText }),
  });
  if (!res.ok) throw new Error(`Analysis failed: ${res.statusText}`);
  return res.json() as Promise<AnalyzedLog>;
}

export async function getHistory(): Promise<AnalyzedLog[]> {
  const res = await fetch(`${BASE}/history`);
  if (!res.ok) throw new Error(`Failed to fetch history: ${res.statusText}`);
  return res.json() as Promise<AnalyzedLog[]>;
}
