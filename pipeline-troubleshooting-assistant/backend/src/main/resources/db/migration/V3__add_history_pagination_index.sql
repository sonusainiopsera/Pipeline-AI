-- Index supporting paginated history queries ordered by created_at DESC.
-- Targets the Page<AnalyzedLog> findAllByOrderByCreatedAtDesc(Pageable) repository method
-- introduced in WO-056, enabling the <150ms p95 requirement at scale.
CREATE INDEX IF NOT EXISTS idx_analyzed_logs_created_at_desc ON analyzed_logs(created_at DESC);
