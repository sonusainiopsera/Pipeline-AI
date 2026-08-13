import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Plus, Pencil, Trash2, X, Search, BookOpen, AlertTriangle } from 'lucide-react';
import {
  KnowledgeBaseEntry,
  KnowledgeBaseRequest,
  UserRole,
  getKnowledgeBase,
  createKnowledgeBaseEntry,
  updateKnowledgeBaseEntry,
  deleteKnowledgeBaseEntry,
  getUserRole,
} from '../api';
import { useAuth } from '../contexts/AuthContext';
import { PageHeader, Loading, ErrorDisplay } from '../components/Common';
import FocusTrap from '../components/FocusTrap';

type ModalMode = 'add' | 'edit' | 'delete' | null;

const EMPTY_FORM: KnowledgeBaseRequest = {
  errorPattern: '',
  category: '',
  rootCause: '',
  solution: '',
  severity: 'MEDIUM',
};

const SEVERITY_STYLES: Record<string, { bg: string; color: string; border: string }> = {
  CRITICAL: { bg: '#fef2f2', color: '#991b1b', border: '#fecaca' },
  HIGH: { bg: '#fff7ed', color: '#9a3412', border: '#fed7aa' },
  MEDIUM: { bg: '#fffbeb', color: '#92400e', border: '#fde68a' },
  LOW: { bg: '#f0fdf4', color: '#166534', border: '#bbf7d0' },
};

function canMutateKb(role: UserRole | null | undefined): boolean {
  return role === 'ANALYST' || role === 'KB_ADMIN' || role === 'MANAGER';
}

function SeverityBadge({ severity }: { severity: string }) {
  const key = severity.toUpperCase();
  const style = SEVERITY_STYLES[key] ?? SEVERITY_STYLES.MEDIUM;
  return (
    <span
      className="kb-severity-badge"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: '3px 10px',
        borderRadius: '999px',
        fontSize: '0.75em',
        fontWeight: 700,
        letterSpacing: '0.04em',
        background: style.bg,
        color: style.color,
        border: `1px solid ${style.border}`,
      }}
    >
      {key}
    </span>
  );
}

export default function KnowledgeBasePage() {
  const { user } = useAuth();
  const [entries, setEntries] = useState<KnowledgeBaseEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<UserRole | null>(null);
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL');

  const [modalMode, setModalMode] = useState<ModalMode>(null);
  const [formData, setFormData] = useState<KnowledgeBaseRequest>(EMPTY_FORM);
  const [editEntryId, setEditEntryId] = useState<number | null>(null);
  const [deleteEntry, setDeleteEntry] = useState<KnowledgeBaseEntry | null>(null);
  const [saving, setSaving] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  const triggerRef = useRef<HTMLElement | null>(null);
  const addButtonRef = useRef<HTMLButtonElement>(null);

  const effectiveRole = (userRole ?? (user?.role as UserRole | undefined) ?? null);
  const canMutate = canMutateKb(effectiveRole);

  useEffect(() => {
    getKnowledgeBase()
      .then(setEntries)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    getUserRole().then(setUserRole);
  }, []);

  const categories = useMemo(() => {
    const set = new Set(entries.map((e) => e.category).filter(Boolean));
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [entries]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return entries.filter((entry) => {
      if (categoryFilter !== 'ALL' && entry.category !== categoryFilter) return false;
      if (!q) return true;
      return (
        entry.errorPattern.toLowerCase().includes(q) ||
        entry.category.toLowerCase().includes(q) ||
        (entry.rootCause ?? '').toLowerCase().includes(q) ||
        (entry.solution ?? '').toLowerCase().includes(q)
      );
    });
  }, [entries, search, categoryFilter]);

  const openAddModal = () => {
    triggerRef.current = addButtonRef.current;
    setFormData(EMPTY_FORM);
    setEditEntryId(null);
    setModalError(null);
    setModalMode('add');
  };

  const openEditModal = (entry: KnowledgeBaseEntry, trigger: HTMLElement) => {
    triggerRef.current = trigger;
    setFormData({
      errorPattern: entry.errorPattern,
      category: entry.category,
      rootCause: entry.rootCause,
      solution: entry.solution,
      severity: entry.severity,
    });
    setEditEntryId(entry.id);
    setModalError(null);
    setModalMode('edit');
  };

  const openDeleteModal = (entry: KnowledgeBaseEntry, trigger: HTMLElement) => {
    triggerRef.current = trigger;
    setDeleteEntry(entry);
    setModalError(null);
    setModalMode('delete');
  };

  const closeModal = () => {
    const trigger = triggerRef.current;
    setModalMode(null);
    setModalError(null);
    triggerRef.current = null;
    requestAnimationFrame(() => trigger?.focus());
  };

  const handleFormChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
  ) => {
    setFormData((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSave = async () => {
    if (!formData.errorPattern.trim() || !formData.category.trim()) {
      setModalError('Error pattern and category are required.');
      return;
    }
    setSaving(true);
    setModalError(null);
    try {
      if (modalMode === 'add') {
        const created = await createKnowledgeBaseEntry(formData);
        setEntries((prev) => [...prev, created]);
      } else if (modalMode === 'edit' && editEntryId !== null) {
        const updated = await updateKnowledgeBaseEntry(editEntryId, formData);
        setEntries((prev) => prev.map((e) => (e.id === editEntryId ? updated : e)));
      }
      closeModal();
    } catch (err) {
      setModalError(err instanceof Error ? err.message : 'Save failed. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteEntry) return;
    setSaving(true);
    setModalError(null);
    try {
      await deleteKnowledgeBaseEntry(deleteEntry.id);
      setEntries((prev) => prev.filter((e) => e.id !== deleteEntry.id));
      closeModal();
    } catch (err) {
      setModalError(err instanceof Error ? err.message : 'Delete failed. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const isFormModalOpen = modalMode === 'add' || modalMode === 'edit';
  const isDeleteModalOpen = modalMode === 'delete';

  return (
    <div className="kb-page">
      <div className="kb-header-row">
        <PageHeader
          title="Knowledge Base"
          description="Manage known error patterns and their solutions"
        />
        {canMutate && (
          <button
            ref={addButtonRef}
            type="button"
            className="kb-btn kb-btn-primary"
            onClick={openAddModal}
          >
            <Plus size={16} aria-hidden="true" />
            Add Entry
          </button>
        )}
      </div>

      {!loading && !error && (
        <div className="kb-toolbar">
          <div className="kb-search">
            <Search size={16} aria-hidden="true" className="kb-search-icon" />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search patterns, categories, causes…"
              aria-label="Search knowledge base"
            />
          </div>
          <select
            className="kb-filter"
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            aria-label="Filter by category"
          >
            <option value="ALL">All categories</option>
            {categories.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
          <div className="kb-count" aria-live="polite">
            {filtered.length} of {entries.length} entries
          </div>
        </div>
      )}

      {loading && <Loading message="Loading knowledge base..." />}
      {error && <ErrorDisplay message={error} />}

      {!loading && !error && entries.length === 0 && (
        <div className="kb-empty">
          <BookOpen size={28} aria-hidden="true" />
          <h2>No knowledge base entries yet</h2>
          <p>
            {canMutate
              ? 'Add known pipeline failure patterns so analysis can suggest fixes faster.'
              : 'Ask a KB admin or manager to add the first entry.'}
          </p>
          {canMutate && (
            <button type="button" className="kb-btn kb-btn-primary" onClick={openAddModal}>
              <Plus size={16} aria-hidden="true" />
              Add the first entry
            </button>
          )}
        </div>
      )}

      {!loading && !error && entries.length > 0 && filtered.length === 0 && (
        <div className="kb-empty">
          <Search size={28} aria-hidden="true" />
          <h2>No matches</h2>
          <p>Try a different search term or category filter.</p>
        </div>
      )}

      {!loading && !error && filtered.length > 0 && (
        <div className="kb-table-container kb-table-card">
          <table className="kb-table">
            <thead>
              <tr>
                <th>Error Pattern</th>
                <th>Category</th>
                <th className="col-severity">Severity</th>
                <th className="kb-actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((entry) => (
                <tr key={entry.id}>
                  <td>
                    <div className="kb-pattern">{entry.errorPattern}</div>
                    {(entry.rootCause || entry.solution) && (
                      <div className="kb-pattern-meta">
                        {entry.rootCause
                          ? entry.rootCause.slice(0, 90) + (entry.rootCause.length > 90 ? '…' : '')
                          : entry.solution.slice(0, 90) + (entry.solution.length > 90 ? '…' : '')}
                      </div>
                    )}
                  </td>
                  <td>
                    <span className="kb-category-chip">{entry.category}</span>
                  </td>
                  <td className="col-severity">
                    <SeverityBadge severity={entry.severity} />
                  </td>
                  <td className="kb-actions-col">
                    <div className="kb-row-actions">
                      <button
                        type="button"
                        className="kb-icon-btn"
                        aria-label={`Edit entry: ${entry.errorPattern}`}
                        title="Edit"
                        onClick={(e) => openEditModal(entry, e.currentTarget)}
                      >
                        <Pencil size={15} aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className="kb-icon-btn kb-icon-btn-danger"
                        aria-label={`Delete entry: ${entry.errorPattern}`}
                        title="Delete"
                        onClick={(e) => openDeleteModal(entry, e.currentTarget)}
                      >
                        <Trash2 size={15} aria-hidden="true" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isFormModalOpen && (
        <div
          role="presentation"
          className="kb-modal-backdrop"
          onClick={(e) => { if (e.target === e.currentTarget) closeModal(); }}
        >
          <FocusTrap active={isFormModalOpen} onEscape={closeModal}>
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="form-modal-title"
              className="kb-modal"
            >
              <div className="kb-modal-header">
                <h2 id="form-modal-title">
                  {modalMode === 'add' ? 'Add Entry' : 'Edit Entry'}
                </h2>
                <button
                  type="button"
                  onClick={closeModal}
                  aria-label="Close dialog"
                  className="kb-icon-btn"
                >
                  <X size={18} aria-hidden="true" />
                </button>
              </div>

              <p
                id="kb-form-modal-error"
                role="alert"
                className="kb-modal-error"
                style={{ marginBottom: modalError ? '16px' : 0 }}
              >
                {modalError || ''}
              </p>

              <div className="kb-form-grid">
                <div className="kb-field">
                  <label htmlFor="kb-errorPattern">
                    Error Pattern <span aria-hidden="true">*</span>
                  </label>
                  <input
                    id="kb-errorPattern"
                    name="errorPattern"
                    type="text"
                    value={formData.errorPattern}
                    onChange={handleFormChange}
                    required
                    aria-required="true"
                    aria-describedby="kb-form-modal-error"
                    placeholder="e.g. OutOfMemoryError, heap space"
                  />
                </div>

                <div className="kb-field-row">
                  <div className="kb-field">
                    <label htmlFor="kb-category">
                      Category <span aria-hidden="true">*</span>
                    </label>
                    <input
                      id="kb-category"
                      name="category"
                      type="text"
                      value={formData.category}
                      onChange={handleFormChange}
                      required
                      aria-required="true"
                      aria-describedby="kb-form-modal-error"
                      placeholder="e.g. Memory"
                    />
                  </div>
                  <div className="kb-field">
                    <label htmlFor="kb-severity">Severity</label>
                    <select
                      id="kb-severity"
                      name="severity"
                      value={formData.severity}
                      onChange={handleFormChange}
                    >
                      <option value="LOW">Low</option>
                      <option value="MEDIUM">Medium</option>
                      <option value="HIGH">High</option>
                      <option value="CRITICAL">Critical</option>
                    </select>
                  </div>
                </div>

                <div className="kb-field">
                  <label htmlFor="kb-rootCause">Root Cause</label>
                  <textarea
                    id="kb-rootCause"
                    name="rootCause"
                    value={formData.rootCause}
                    onChange={handleFormChange}
                    rows={3}
                    placeholder="What typically causes this failure?"
                  />
                </div>

                <div className="kb-field">
                  <label htmlFor="kb-solution">Solution</label>
                  <textarea
                    id="kb-solution"
                    name="solution"
                    value={formData.solution}
                    onChange={handleFormChange}
                    rows={3}
                    placeholder="Recommended remediation steps"
                  />
                </div>
              </div>

              <div className="kb-modal-actions">
                <button type="button" className="kb-btn kb-btn-secondary" onClick={closeModal} disabled={saving}>
                  Cancel
                </button>
                <button type="button" className="kb-btn kb-btn-primary" onClick={handleSave} disabled={saving}>
                  {saving ? 'Saving…' : 'Save'}
                </button>
              </div>
            </div>
          </FocusTrap>
        </div>
      )}

      {isDeleteModalOpen && deleteEntry && (
        <div
          role="presentation"
          className="kb-modal-backdrop"
          onClick={(e) => { if (e.target === e.currentTarget) closeModal(); }}
        >
          <FocusTrap active={isDeleteModalOpen} onEscape={closeModal}>
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="delete-modal-title"
              className="kb-modal kb-modal-sm"
            >
              <div className="kb-delete-banner">
                <AlertTriangle size={20} aria-hidden="true" />
                <h2 id="delete-modal-title">Delete Entry</h2>
              </div>
              <p className="kb-delete-copy">
                Are you sure you want to delete{' '}
                <strong>{deleteEntry.errorPattern}</strong>? This cannot be undone.
              </p>

              <p
                id="kb-delete-modal-error"
                role="alert"
                className="kb-modal-error"
                style={{ marginBottom: modalError ? '16px' : 0 }}
              >
                {modalError || ''}
              </p>

              <div className="kb-modal-actions">
                <button type="button" className="kb-btn kb-btn-secondary" onClick={closeModal} disabled={saving}>
                  Cancel
                </button>
                <button type="button" className="kb-btn kb-btn-danger" onClick={handleDelete} disabled={saving}>
                  {saving ? 'Deleting…' : 'Delete'}
                </button>
              </div>
            </div>
          </FocusTrap>
        </div>
      )}
    </div>
  );
}
