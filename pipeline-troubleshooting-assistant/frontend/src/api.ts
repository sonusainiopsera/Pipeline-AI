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

export interface KnowledgeBaseEntry {
  id: number;
  errorPattern: string;
  category: string;
  rootCause: string;
  solution: string;
  severity: string;
}

export interface KnowledgeBaseRequest {
  errorPattern: string;
  category: string;
  rootCause: string;
  solution: string;
  severity: string;
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

export async function getKnowledgeBase(): Promise<KnowledgeBaseEntry[]> {
  const res = await fetch(`${BASE}/errors`);
  if (!res.ok) throw new Error(`Failed to fetch knowledge base: ${res.statusText}`);
  return res.json() as Promise<KnowledgeBaseEntry[]>;
}

export async function createKnowledgeBaseEntry(
  data: KnowledgeBaseRequest,
): Promise<KnowledgeBaseEntry> {
  const res = await fetch(`${BASE}/errors`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!res.ok) throw new Error(`Failed to create entry: ${res.statusText}`);
  return res.json() as Promise<KnowledgeBaseEntry>;
}

export async function updateKnowledgeBaseEntry(
  id: number,
  data: KnowledgeBaseRequest,
): Promise<KnowledgeBaseEntry> {
  const res = await fetch(`${BASE}/errors/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!res.ok) throw new Error(`Failed to update entry: ${res.statusText}`);
  return res.json() as Promise<KnowledgeBaseEntry>;
}

export async function deleteKnowledgeBaseEntry(id: number): Promise<void> {
  const res = await fetch(`${BASE}/errors/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Failed to delete entry: ${res.statusText}`);
}
