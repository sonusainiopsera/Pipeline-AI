-- Seed data for AnalyzedLogRepositoryPaginationTest.
-- 25 records with distinct created_at timestamps enabling multi-page pagination tests.
-- Timestamps are spaced 1 hour apart so ordering is deterministic.

INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 01', 'Memory',      'Heap exhausted',              'Increase -Xmx',             'Investigating memory issue',    'HIGH',   95, '2024-01-01 01:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 02', 'Network',     'Connection refused',          'Check firewall rules',       'Investigating network issue',   'HIGH',   88, '2024-01-01 02:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 03', 'Docker',      'Image pull failed',           'Check registry credentials', 'Investigating docker issue',   'HIGH',   82, '2024-01-01 03:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 04', 'Permissions', 'Access denied',               'Update IAM roles',           'Investigating permissions',    'MEDIUM', 76, '2024-01-01 04:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 05', 'Dependency',  'npm install failed',          'Clear node_modules',         'Investigating dependency',     'MEDIUM', 69, '2024-01-01 05:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 06', 'Testing',     'Test suite failed',           'Review failing tests',       'Investigating test failure',   'MEDIUM', 98, '2024-01-01 06:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 07', 'Memory',      'GC overhead exceeded',        'Tune GC settings',           'Investigating GC overhead',   'HIGH',   91, '2024-01-01 07:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 08', 'Network',     'DNS resolution failed',       'Check DNS config',           'Investigating DNS failure',   'HIGH',   84, '2024-01-01 08:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 09', 'Docker',      'Container exit code 1',       'Review Dockerfile CMD',      'Investigating container',     'HIGH',   77, '2024-01-01 09:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 10', 'Unclassified','Unable to determine cause',   'Review logs manually',       'Under investigation',         'MEDIUM', 20, '2024-01-01 10:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 11', 'Memory',      'PermGen space exhausted',     'Increase PermGen',           'Investigating PermGen',       'HIGH',   90, '2024-01-01 11:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 12', 'Network',     'SSL handshake failed',        'Check SSL certificates',     'Investigating SSL failure',   'HIGH',   86, '2024-01-01 12:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 13', 'Docker',      'Base image not found',        'Verify image tag',           'Investigating base image',    'MEDIUM', 79, '2024-01-01 13:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 14', 'Permissions', 'File permission denied',      'chmod 755 target dir',       'Investigating file perms',    'MEDIUM', 73, '2024-01-01 14:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 15', 'Dependency',  'Maven dependency missing',    'Check pom.xml entries',      'Investigating Maven deps',    'MEDIUM', 67, '2024-01-01 15:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 16', 'Testing',     'Integration test failed',     'Fix integration tests',      'Investigating integration',   'MEDIUM', 96, '2024-01-01 16:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 17', 'Memory',      'Stack overflow detected',     'Reduce recursion depth',     'Investigating stack issue',   'HIGH',   93, '2024-01-01 17:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 18', 'Network',     'HTTP 503 from upstream',      'Scale upstream service',     'Investigating 503 errors',    'HIGH',   87, '2024-01-01 18:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 19', 'Docker',      'Disk space exhausted',        'Clean up docker images',     'Investigating disk space',    'HIGH',   81, '2024-01-01 19:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 20', 'Permissions', 'Kubernetes RBAC denied',      'Update ClusterRole',         'Investigating RBAC issue',    'MEDIUM', 74, '2024-01-01 20:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 21', 'Dependency',  'Python package not found',    'Run pip install -r',         'Investigating pip deps',      'MEDIUM', 68, '2024-01-01 21:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 22', 'Testing',     'Unit test assertion failed',  'Fix assertion values',       'Investigating unit tests',    'MEDIUM', 98, '2024-01-01 22:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 23', 'Memory',      'Direct memory exhausted',     'Tune -XX:MaxDirectMemory',  'Investigating direct mem',    'HIGH',   89, '2024-01-01 23:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 24', 'Network',     'Connection pool exhausted',   'Increase pool size',         'Investigating conn pool',     'HIGH',   83, '2024-01-02 00:00:00');
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence, created_at)
VALUES ('Log text 25', 'Docker',      'Multi-stage build failed',    'Review build stage order',   'Investigating build stages',  'HIGH',   78, '2024-01-02 01:00:00');
