# Forge Implementation Log

| Field | Value |
|-------|-------|
| Project | b9e8725a-6876-41ad-bbb1-6bc8ac013ff1 |
| Branch | forge/pipeline-ai-4ce3e92c-run5-88wo |
| Started | 2026-08-12T06:29:16Z |

---

## WO-001: User Story: WO-001 - Configure Maven Test Dependencies and JaCoCo Plugin
- **Status:** completed
- **Commit:** `3df02ec`
- **Files:** 21 (+873/-1)
- **Duration:** 440ss
- **Approach:** Scaffolded the full Spring Boot 3.3.5 / Java 17 project under pipeline-troubleshooting-assistant/backend/ since the repository was empty. Then implemented WO-001: added H2 (test-scoped) dependency, maven-surefire-plugin 3.2.5, and jacoco-maven-plugin 0.8.12 to pom.xml, created SmokeTest.java with @SpringBootTest/@ActiveProfiles("test"), and created application-test.yml with H2 in-memory datasource + ddl-auto:create so the Spring context loads without a PostgreSQL instance during tests.

## WO-003: User Story: WO-003 - Unit Tests for AnalysisService Scoring Logic
- **Status:** completed
- **Commit:** `db76588`
- **Files:** 1 (+269/-0)
- **Duration:** 200ss
- **Approach:** Created a pure Mockito unit test class for AnalysisService with 14 test methods. Used @ExtendWith(MockitoExtension.class) with @Mock on ErrorRepository and AnalyzedLogRepository and @InjectMocks on AnalysisService. A buildKnowledgeBaseEntry() helper constructs ErrorKnowledgeBase entities for each test. Each test mocks errorRepository.findAll() to return a controlled list and mocks analyzedLogRepository.save() to pass-through the argument (so returned fields can be asserted). Tests cover all branches of analyze(): matching path, unclassified fallback, confidence formula arithmetic including integer division truncation, best-match tie-breaking, case-insensitivity, whitespace trimming, customer update template content, and ArgumentCaptor verification of the persisted entity.

## WO-004: User Story: WO-004 - Unit Tests for KnowledgeBaseService CRUD Operations
- **Status:** completed
- **Commit:** `db61403`
- **Files:** 2 (+191/-2)
- **Duration:** 258ss
- **Approach:** The scaffolded KnowledgeBaseService lacked a findById method and used RuntimeException instead of ResponseStatusException — inconsistent with the WO's described Current Behavior and the acceptance criteria requiring NOT_FOUND status testing. Updated the service to add findById(Long id) throwing ResponseStatusException(NOT_FOUND), then refactored update() and delete() to delegate to findById for consistent 404 handling. Created KnowledgeBaseServiceTest with 9 pure Mockito unit tests covering all CRUD paths, both happy paths and not-found error paths, using ArgumentCaptor to verify field mapping on create and update.

## WO-007: User Story: WO-007 - Integration Tests for ErrorController CRUD Endpoints
- **Status:** completed
- **Commit:** `bc78bd8`
- **Files:** 1 (+211/-0)
- **Duration:** 208ss
- **Approach:** Created ErrorControllerTest using @WebMvcTest(ErrorController.class) with @MockBean KnowledgeBaseService and auto-wired MockMvc and ObjectMapper. Wrote 12 tests covering all four CRUD endpoints. Used jsonPath assertions for response body verification, status matchers for HTTP codes, and doThrow/thenThrow with ResponseStatusException(NOT_FOUND) for 404 scenarios. Tested actual controller status codes: POST returns 201 CREATED and DELETE returns 204 NO_CONTENT (matching the @ResponseStatus annotations in ErrorController). No production code was modified.

## WO-010: User Story: WO-010 - Externalize Database Credentials via Environment Variables
- **Status:** completed
- **Commit:** `607bca5`
- **Files:** 5 (+73/-10)
- **Duration:** 289ss
- **Approach:** Externalized all database and CORS credentials from version-controlled files into a git-ignored .env file. Created .env.example with placeholder values as the onboarding template. Used Docker Compose required-variable syntax (${VAR:?error}) for the six sensitive credential variables so startup fails fast and explicitly if .env is missing. Used safe fallback (localdev) in application.yml defaults to clearly distinguish local-dev defaults from production values. Added a project-level .gitignore as a belt-and-suspenders guard on top of the root .gitignore.

## WO-039: User Story: WO-039 - Integrate LogSanitizer into Analysis Pipeline Before Persistence
- **Status:** completed
- **Commit:** `85f4871`
- **Files:** 4 (+174/-2)
- **Duration:** 675ss
- **Approach:** Created LogSanitizer as a new @Service with compiled regex patterns for AWS access keys (AKIA[0-9A-Z]{16}), Bearer tokens (Bearer\s+[A-Za-z0-9\-_.]+), and password assignments ((?i)(password|passwd|secret)=\S+). Added LogSanitizer as a final field in AnalysisService — @RequiredArgsConstructor auto-generates the three-arg constructor. In analyze(), the sanitize() call is the first operation, wrapped in a try-catch that throws ResponseStatusException(500) on failure to prevent any raw log from reaching downstream processing. All uses of rawLog replaced with sanitizedLog for both pattern matching and entity construction. AnalysisServiceTest updated with @Mock LogSanitizer, a lenient @BeforeEach pass-through stub so existing 14 tests are unaffected, and 2 new tests verifying the integration contract. AnalysisIntegrationTest uses @SpringBootTest + H2 to verify end-to-end sanitization: submits a log with embedded secrets and asserts neither the DB record nor the history endpoint response contains raw secret values.

## WO-042: User Story: WO-042 - Sanitize Log Display in Frontend History Component
- **Status:** completed
- **Commit:** `d6944c9`
- **Files:** 14 (+698/-0)
- **Duration:** 479ss
- **Approach:** Scaffolded the complete React 18 / TypeScript frontend (Vite + Vitest) since no frontend directory existed, then implemented the WO-042 sanitization display layer on top. Created sanitization.ts with detectPotentialSecrets (regex patterns matching the same secret types as the backend LogSanitizer), REDACTION_PLACEHOLDER_REGEX (specific _REDACTED suffix pattern to avoid false positives on generic bracket tokens like [INFO]), and highlightRedactions (uses React.createElement to produce styled span nodes — never dangerouslySetInnerHTML). Created SanitizedLogDisplay.tsx component that renders highlighted log text with a green Shield indicator when placeholders are present and a red AlertTriangle warning banner when unredacted secrets are detected. Updated History.tsx to use SanitizedLogDisplay for log text in expanded rows. Updated Analyze.tsx to show a 'Log sanitized before storage' shield notice whenever analysis results are displayed. Created 24 Vitest unit tests covering all three utility exports.

## WO-064: User Story: WO-064 - GitHub Actions CI Pipeline with Build and Test
- **Status:** completed
- **Commit:** `4e4d355`
- **Files:** 2 (+108/-0)
- **Duration:** 256ss
- **Approach:** Created .github/workflows/ci.yml at the repository root (the only path GitHub Actions recognises) with two parallel jobs: 'backend' and 'frontend'. The backend job provisions a postgres:16-alpine service container with test credentials, sets defaults.run.working-directory to pipeline-troubleshooting-assistant/backend, caches ~/.m2/repository keyed on pom.xml hash, runs mvn clean verify with DB_URL/DB_USERNAME/DB_PASSWORD env vars pointing to the service container, and publishes JUnit XML reports via dorny/test-reporter. The frontend job caches ~/.npm keyed on package-lock.json hash, then runs npm ci + npm test + npm run build from pipeline-troubleshooting-assistant/frontend. A concurrency group cancels in-progress runs on the same branch. Added a GitHub Actions status badge to README.md at the top.

## WO-065: User Story: WO-065 - Multi-Stage Docker Builds for Optimized Container Images
- **Status:** completed
- **Commit:** `2a0b893`
- **Files:** 6 (+107/-2)
- **Duration:** 363ss
- **Approach:** Created a multi-stage backend Dockerfile from scratch (no Dockerfile existed for the backend) using eclipse-temurin:17-jdk-alpine + apk-installed Maven as the builder stage and eclipse-temurin:17-jre-alpine as the minimal runtime stage. The builder uses pom.xml-first layer caching via mvn dependency:go-offline so the dependency download layer is only invalidated when pom.xml changes. The runtime stage creates a non-root appuser and includes a HEALTHCHECK calling the Spring Boot Actuator health endpoint via wget. Rewrote the frontend Dockerfile from node:20 to node:22-alpine builder with package*.json-first layer caching and a VITE_API_URL build arg, and nginx:stable-alpine runtime with a custom nginx.conf providing SPA try_files routing, /api/ reverse proxy to backend:8080, gzip compression, and long-lived cache headers. Added .dockerignore files for both services to exclude build artifacts, IDE directories, and secrets from the Docker build context. Updated docker-compose.yml to forward VITE_API_URL as a build arg to the frontend service.

## WO-082: User Story: WO-082 - Add Keyboard Navigation and Focus Indicators
- **Status:** completed
- **Commit:** `6a5c6d4`
- **Files:** 21 (+1343/-27)
- **Duration:** 724ss
- **Approach:** Added global :focus-visible CSS rules (2px solid #60a5fa, 2px offset) to a new styles.css imported in main.tsx. Created a SkipLink component that slides into view via CSS transition on :focus-visible, targeting id='main-content'. Created Layout.tsx as the app shell with SkipLink as its first child, a sidebar with keyboard-navigable nav buttons (type='button', aria-current='page') and a toggle button (type='button', aria-expanded, aria-label), and a main wrapper with id='main-content' tabIndex={-1}. Created FocusTrap.tsx, a lightweight custom component that constrains Tab/Shift-Tab within a container and fires onEscape on Escape. Created KnowledgeBase.tsx with Add/Edit/Delete modals wrapped in FocusTrap, capturing the trigger element ref before opening and restoring focus via requestAnimationFrame on close. Created Dashboard.tsx as the fourth page. Refactored App.tsx to use Layout. Added type='button' and aria attributes to Analyze.tsx and History.tsx buttons. Added Playwright tests covering skip-link, sidebar keyboard nav, tab order, modal focus trapping, Escape dismissal, and focus restoration; committed five fixture JSON files so tests run without a backend.

## WO-002: User Story: WO-002 - Create Golden-File Baseline for Six Seed Patterns
- **Status:** completed
- **Commit:** `434440d`
- **Files:** 16 (+354/-0)
- **Duration:** 571ss
- **Approach:** Read SeedData.java to extract all 6 seed pattern definitions verbatim. Read AnalysisService.analyze() to trace the exact scoring formula (Math.min(98, 55 + matchCount * 43 / totalKeywords) with integer division) and customerUpdate template. Manually computed the expected output for each pattern by crafting fixture logs that contain all keywords for that pattern (triggering matchCount = totalKeywords in every case → confidence = 98 for all 6 seed patterns). Created golden JSON files from these computed values. Created an unclassified fixture whose log text matches zero pattern keywords, producing the fallback response (confidence = 20). Wrote GoldenFileTestHelper with classpath-based loadFixture() and loadGolden() utilities and a plain-field GoldenResponse POJO for Jackson deserialization. Wrote GoldenFileAnalysisTest using @ExtendWith(MockitoExtension.class) with the 6 seed entries from SeedData.java reproduced verbatim in a SEED_ENTRIES list, a pass-through LogSanitizer lenient stub, and a 7-case @ParameterizedTest that compares all 6 behavioral fields with descriptive AssertJ assertions. No production files modified.

## WO-006: User Story: WO-006 - Integration Tests for AnalysisController Endpoints
- **Status:** completed
- **Commit:** `f442945`
- **Files:** 1 (+214/-0)
- **Duration:** 404ss
- **Approach:** Created AnalysisControllerTest.java using @WebMvcTest(AnalysisController.class) following the established pattern from ErrorControllerTest.java. Used @MockBean for AnalysisService and DashboardService. Built 11 test methods covering all 3 endpoints: 6 for POST /api/analyze (happy path, blank/missing/whitespace logText, wrong content-type, malformed JSON), 2 for GET /api/history (2-item list, empty list), 3 for GET /api/dashboard (known values, zero counts, content-type verification). All assertions use contentTypeCompatibleWith(APPLICATION_JSON) and jsonPath matchers with Hamcrest is()/hasSize(). Dashboard tests assert against actual DashboardService.getStats() keys ('totalErrors', 'analyzedLogs') rather than the WO's planned DTO field names which differ from the implementation. No production code was modified.

## WO-011: User Story: WO-011 - Add Pre-Commit Hook Blocking Hardcoded Secrets
- **Status:** completed
- **Commit:** `5885537`
- **Files:** 5 (+268/-0)
- **Duration:** 710ss
- **Approach:** Implemented a shell-script-based pre-commit hook (zero external dependencies beyond git and GNU grep) using grep -P (PCRE) patterns for six secret types: AWS access keys (AKIA[A-Z0-9]{16}), generic API/secret keys, Bearer tokens (≥20 chars), quoted password assignments, private key headers (BEGIN RSA/EC/OPENSSH PRIVATE KEY), and JDBC URLs with embedded credentials. Used grep -nP -e to avoid pattern-starts-with-dash mis-parsing. Excluded test source files (/src/test/, *.test.ts, *.spec.ts etc.) from scanning since AnalysisIntegrationTest.java and sanitization.test.ts intentionally contain fake credentials for testing the sanitizer. Added CI secret-scan job mirroring the same six patterns against all tracked files; simulation confirmed zero findings. Created a six-pattern test fixture (one fake value per pattern), install script (git config core.hooksPath), and README section.

## WO-012: User Story: WO-012 - Document Secret Rotation Runbook and Operational Procedures
- **Status:** completed
- **Commit:** `9e03d57`
- **Files:** 2 (+413/-0)
- **Duration:** 228ss
- **Approach:** Created docs/runbooks/secret-management.md as a comprehensive operational runbook covering all five required sections. Read .env.example to enumerate all current secrets accurately. Used placeholder syntax throughout (no real credentials). Structured the runbook with a ToC, secret inventory table for 9 variables (7 current + 2 future), PostgreSQL rotation procedure with pre-rotation checklist, step-by-step commands, verification steps, rollback procedure, and common failure mode table, JWT and MFA placeholder procedures with key impact notes, incident response playbook with severity classification and all five steps (rotate, investigate, BFG history rewrite, notify with timelines, post-incident review), environment-specific guidance for all four tiers, and a three-option comparison table (HashiCorp Vault / AWS Secrets Manager / Docker Secrets) with recommendation rationale and phased migration path. Updated README.md with a Documentation section linking to the runbook.

## WO-038: User Story: WO-038 - Implement LogSanitizer with Regex-Based Secret Redaction
- **Status:** completed
- **Commit:** `f773ed0`
- **Files:** 12 (+406/-11)
- **Duration:** 859ss
- **Approach:** Extended the existing LogSanitizer @Service (previously created with 3 patterns in WO-039) to the full 7-pattern implementation required by WO-038. Used a private SanitizationRule record (Java 17 nested record, implicitly static) holding a compiled Pattern and replacement string, stored in a static final List<SanitizationRule> to ensure patterns are compiled exactly once at class load time. The sanitize() method iterates the rules and applies Matcher.replaceAll() sequentially with backreferences ($1) to preserve keyword names (e.g. 'password=', 'apikey=') while redacting only the secret value. A try-catch(RuntimeException | Error) block handles catastrophic backtracking scenarios. Created 5 sample/expected fixture pairs and a LogSanitizerTest with 24 test methods: one per pattern variant, combined tests, preservation tests, edge cases (null/empty/no-secrets/all-secrets), a large-input performance test, and 5 parameterized fixture tests.

## WO-069: User Story: WO-069 - Automated PostgreSQL pg_dump Backup Container
- **Status:** completed
- **Commit:** `def0a26`
- **Files:** 8 (+358/-0)
- **Duration:** 359ss
- **Approach:** Created pipeline-troubleshooting-assistant/backup/ with all required files. Dockerfile uses postgres:16-alpine (exact pg_dump version match) with dcron and bash installed via apk. backup.sh validates env vars, creates a .pgpass file (chmod 600) for credential-free pg_dump invocation, retries pg_isready up to 3 times, runs pg_dump --format=custom --compress=9 to a timestamped filename, captures exit code, deletes partial files on failure, sanitizes stderr before logging, and emits structured JSON with all required fields (timestamp, status, filename, size_bytes, duration_seconds, tables_count). retention.sh uses find -mtime to delete .dump files older than BACKUP_RETENTION_DAYS and logs the deleted count. entrypoint.sh validates all required env vars on startup, writes .pgpass, substitutes BACKUP_CRON_SCHEDULE into the crontab template, and execs crond -f. docker-compose.yml adds a 'backup' service depending on the 'db' service with service_healthy, mounts backup_data:/backups, and declares the backup_data named volume. No ports are exposed from the backup container.

## WO-083: User Story: WO-083 - Enforce WCAG AA Color Contrast Ratios
- **Status:** completed
- **Commit:** `ee2e4f7`
- **Files:** 11 (+456/-38)
- **Duration:** 807ss
- **Approach:** Performed a full WCAG luminance formula audit of every foreground/background color pair in the application. Identified 15+ violations across text, UI boundary, and interactive element categories. Fixed all violations while preserving the dark-sidebar / light-content theme aesthetic. Added a CSS custom property block in styles.css documenting all compliant colors and a ::placeholder rule ensuring 4.83:1 contrast on white inputs. Replaced all inline color violations in-place across 7 component/page files. Disabled button states now use opacity:0.5 + cursor:not-allowed instead of lighter background colors (non-color visual distinction per AC5). Added @axe-core/playwright as a dev dependency and created contrast-audit.spec.ts with 10 Playwright tests that mock API data, navigate to each page and key modal state, then run axe-core's color-contrast rule asserting zero violations. Committed docs/accessibility-contrast-audit.md with the complete audit table.

## WO-085: User Story: WO-085 - Implement Responsive Layout for Mobile Viewports
- **Status:** completed
- **Commit:** `a313793`
- **Files:** 7 (+607/-19)
- **Duration:** 675ss
- **Approach:** Added a comprehensive responsive CSS layer to styles.css covering all breakpoints (320px / 480px / 640px / 768px), then wired React components to CSS classes. Layout.tsx gained a lazy useState initializer (window.innerWidth >= 768) to avoid mobile flash-of-wrong-state, a MediaQueryList listener that auto-opens/closes on resize, a semi-transparent backdrop div (data-testid='sidebar-backdrop', onClick closes sidebar), and aria-expanded + aria-controls on the toggle button. CSS mobile sidebar overlay uses position:fixed + transform:translateX(-100%) by default with .mobile-open sliding it in. Dashboard.tsx replaced its inline grid style with className='dashboard-grid'; CSS drives the breakpoint columns. History.tsx wrapped its table in .table-scroll-container with col-date / col-confidence CSS classes that hide those columns on mobile. KnowledgeBase.tsx wrapped its table in .kb-table-container with col-severity hidden on mobile. Analyze.tsx got className='analyze-layout' for max-width:100% overflow guard on its inputs. Created responsive.spec.ts with tests across 320px / 480px / 768px / 1280px viewports covering: no horizontal scroll on all pages, sidebar visibility state, hamburger visibility, backdrop open/close, Dashboard single-column at 320px, History/KB scroll containers, and 44x44 touch target validation for all visible buttons.

## WO-086: User Story: WO-086 - Configure Nginx Security Headers for Edge Defense
- **Status:** completed
- **Commit:** `fdceda5`
- **Files:** 2 (+259/-1)
- **Duration:** 203ss
- **Approach:** Added server_tokens off and all 7 security headers (CSP, HSTS, X-Content-Type-Options, X-Frame-Options, X-XSS-Protection, Referrer-Policy, Permissions-Policy) with 'always' parameter to the nginx.conf server block. To handle the Nginx add_header scoping rule (child location blocks with their own add_header directive suppress server-block header inheritance), the security headers are repeated in the static-assets location block alongside its Cache-Control header. The / and /api/ location blocks have no add_header so they correctly inherit from the server block. Created scripts/test-nginx-headers.sh that builds the Docker image, starts a temporary container, fetches headers from four URLs (/, /analyze, a static asset, and /does-not-exist-xyz for SPA fallback), then asserts exact header values and absence of Nginx version disclosure.

## WO-005: User Story: WO-005 - Unit Tests for DashboardService Aggregate Calculations
- **Status:** completed
- **Commit:** `e4d726c`
- **Files:** 1 (+172/-0)
- **Duration:** 95ss
- **Approach:** Read DashboardService.java to understand the actual return type (Map<String, Object> with keys totalErrors, analyzedLogs, mostCommonIssue, categoryBreakdown) and repository field names (ErrorRepository, AnalyzedLogRepository). Created a Mockito-based test class using @ExtendWith(MockitoExtension.class), @Mock for both repositories, and @InjectMocks for DashboardService. A buildCategoryCounts helper constructs List<Object[]> from alternating (category, count) pairs to match the native-query return type. Wrote 8 test methods covering all 6 required AC scenarios plus two bonus edge cases (tied counts and Long.MAX_VALUE overflow guard).

## WO-040: User Story: WO-040 - Add Input Size Constraints to AnalyzeRequest DTO
- **Status:** completed
- **Commit:** `a1fa5e1`
- **Files:** 7 (+349/-0)
- **Duration:** 322ss
- **Approach:** Added @Size constraints to AnalyzeRequest and ErrorRequest DTOs matching the PRD limits. Created ApiExceptionHandler (@RestControllerAdvice) that intercepts MethodArgumentNotValidException: when a logText @Size violation is detected (by matching the exact message string), it returns HTTP 413 with a structured body; all other validation failures return HTTP 400 with field-level error details. Added spring.servlet.multipart.max-request-size: 2MB and server.tomcat.max-http-form-post-size: 2MB to application.yml as transport-layer defense. Created RequestsValidationTest.java (14 unit tests using Jakarta Validator directly, covering boundary values for all constrained fields). Added 5 new @WebMvcTest integration tests to the existing controller test files to verify 413/400 status codes and response body structure for size violations.

## WO-041: User Story: WO-041 - Add Nginx Security Headers and Request Size Limits
- **Status:** completed
- **Commit:** `0b63ba2`
- **Files:** 2 (+104/-0)
- **Duration:** 275ss
- **Approach:** All six security headers (Content-Security-Policy, Strict-Transport-Security, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, X-XSS-Protection) were already present in nginx.conf from WO-086, applied at server-block level with 'always' and repeated in the static-assets location block to handle Nginx's add_header scoping rule. The only outstanding requirement was client_max_body_size 2m (AC7), which was added to the server block with a comment explaining the sizing rationale. Created scripts/verify-security-headers.sh as a lightweight curl-based verification script that assumes the Docker Compose environment is already running; it checks all six required headers via curl -sI against localhost:5173 and tests that a 3 MB POST returns a 4xx response.

## WO-043: User Story: WO-043 - Extract PatternMatcher from AnalysisService
- **Status:** completed
- **Commit:** `91033c7`
- **Files:** 7 (+514/-11)
- **Duration:** 557ss
- **Approach:** Extracted the inline pattern-matching loop from AnalysisService into a dedicated PatternMatcher Spring @Service bean in a new analysis package. PatternMatcher.match() accepts log text and a List<ErrorKnowledgeBase>, splits errorPattern on comma/newline via regex, trims keywords, and returns List<ScoredMatch> records (each pairing an ErrorKnowledgeBase entry with its integer hit count). AnalysisService now delegates to PatternMatcher via @RequiredArgsConstructor injection; all scoring constants, response templating, and persistence remain inline. GoldenFileAnalysisTest and AnalysisServiceTest both received a @Spy PatternMatcher field so their @InjectMocks wiring continues to work after the new dependency was added.

## WO-070: User Story: WO-070 - Off-site Backup Push and Restore Verification
- **Status:** completed
- **Commit:** `c3b8cb3`
- **Files:** 11 (+826/-9)
- **Duration:** 654ss
- **Approach:** Extended the existing WO-069 backup container with four new shell scripts and one test orchestration script. push-offsite.sh uses the AWS CLI (supporting --endpoint-url for MinIO and other S3-compatible stores) to upload the latest .dump file to s3://BUCKET/backups/YYYY/MM/filename.dump. remote-retention.sh lists objects via aws s3 ls, parses the date column, computes a lexicographically-comparable cutoff with busybox date -d @epoch, and deletes expired objects. restore.sh accepts --file/--target-db args and runs pg_restore --clean --if-exists --no-owner. verify-restore.sh creates pipeline_assistant_verify, restores, validates both tables exist and row counts are correct, runs the categoryCounts aggregate query, drops the temp db via a trap, and exits with the failure count. backup.sh was extended to call push-offsite.sh conditionally (only when S3_BUCKET is set), treating its failure as a warning. entrypoint.sh gained VERIFY_CRON_SCHEDULE support (default Sunday 04:00 UTC). Dockerfile gained aws-cli via apk. docker-compose.test.yml adds a minio/minio service override for integration testing.

## WO-084: User Story: WO-084 - Add ARIA Labels and Screen Reader Support
- **Status:** completed
- **Commit:** `3e4569d`
- **Files:** 7 (+562/-134)
- **Duration:** 644ss
- **Approach:** Added comprehensive ARIA support across all frontend pages. Created RouteAnnouncer.tsx (visually-hidden aria-live='assertive' component) to announce page transitions and update document.title. Mounted it in App.tsx as a persistent sibling to Layout so the live region pre-exists all navigation. Updated Layout.tsx to add aria-label to nav buttons for accessible names in collapsed state. Updated Analyze.tsx with htmlFor/id label association for log textarea, aria-live='polite' wrapper always in DOM around dynamic content, and label for customer-update textarea. Updated KnowledgeBase.tsx to keep modal error paragraphs always-rendered (stable aria-describedby targets), added aria-describedby on required form inputs. Updated History.tsx to use Common.tsx Loading/ErrorDisplay (role='status'/role='alert'), added aria-label on table, scope='col' on headers, and h2 heading in empty state. Created Playwright tests using @axe-core/playwright covering all pages in multiple states, aria-live region verification, RouteAnnouncer text update assertions, and form label pairing checks.

## WO-087: User Story: WO-087 - Implement Nginx Rate Limiting for API Endpoints
- **Status:** completed
- **Commit:** `5b84f6a`
- **Files:** 2 (+333/-20)
- **Duration:** 412ss
- **Approach:** Added two-tier Nginx rate limiting by placing limit_req_zone directives before the server block in nginx.conf (which is included in the http context from /etc/nginx/conf.d/). Auth zone at 2r/s (~100 req/min) and API zone at 17r/s (~1000 req/min), both keyed on $binary_remote_addr with 10m shared memory zones. Added a dedicated /api/auth/ location block (ordered before /api/ for specificity) with burst=10 nodelay, and applied api_limit with burst=50 nodelay to the existing /api/ block. Static / location has no rate limiting. Changed client_max_body_size from 2m to 1m per spec. Added limit_req_status 429 and error_page mappings for 429/413/502/504 routing to named locations that return JSON bodies per the API contract. The @rate_limited named location repeats security headers (required because defining any add_header in a child block suppresses server-block inheritance under Nginx's add_header scoping rule); other error named locations have no add_header so they inherit security headers directly. Created scripts/test-nginx-rate-limits.sh that starts the full docker-compose stack and runs 8 assertion groups covering: proxy connectivity, auth rate limit triggering, 429 JSON body/Retry-After header, static asset non-limiting, 413 on oversized body, X-Real-IP forwarding, security headers on 429, and server_tokens off.

## WO-008: User Story: WO-008 - Repository Tests with DataJpaTest for Custom Queries
- **Status:** completed
- **Commit:** `59ee28d`
- **Files:** 3 (+284/-1)
- **Duration:** 377ss
- **Approach:** Added two @DataJpaTest repository test classes. Updated application-test.yml to add MODE=PostgreSQL to the H2 JDBC URL for PostgreSQL syntax compatibility in @SpringBootTest tests. AnalyzedLogRepositoryTest uses TestEntityManager to persist test data and JPQL UPDATE queries to set explicit createdAt timestamps after initial persist (necessary because @CreationTimestamp sets the field at INSERT time, which in a single test transaction would give all entities the same timestamp). ErrorKnowledgeBaseRepositoryTest verifies basic JpaRepository CRUD operations, field persistence (including the @CreationTimestamp createdAt), and count behavior. Both test classes rely on @DataJpaTest's default @Transactional rollback for test isolation — no manual cleanup needed. The categoryCounts query is JPQL (not native SQL as the WO description suggested), verified by reading the actual AnalyzedLogRepository source: 'SELECT a.category, COUNT(a) FROM AnalyzedLog a GROUP BY a.category'.

## WO-044: User Story: WO-044 - Extract ScoringEngine with Externalized Configuration
- **Status:** completed
- **Commit:** `72de0c1`
- **Files:** 8 (+310/-2)
- **Duration:** 500ss
- **Approach:** Extracted the hardcoded confidence scoring formula from AnalysisService.analyze() into a dedicated ScoringEngine Spring @Service bean. Created a ScoringProperties @ConfigurationProperties class binding the analysis.scoring prefix with fields baseConfidence (55), scalingFactor (43), maxConfidence (98), and unclassifiedConfidence (20). ScoringEngine validates its properties on construction (fail-fast) and delegates calculateConfidence(matchCount, totalPatterns) to the externalized formula. AnalysisService now injects ScoringEngine via @RequiredArgsConstructor and delegates both the matched and unclassified confidence paths to it. Existing tests were updated to use @Spy with an initialized ScoringEngine instance to provide real scoring behavior under Mockito @InjectMocks.

## WO-071: User Story: WO-071 - Backup Runbook and RTO Validation Drill
- **Status:** completed
- **Commit:** `cdad05e`
- **Files:** 3 (+870/-0)
- **Duration:** 363ss
- **Approach:** Created two documentation files in the existing docs/ directory and updated the root README.md. The runbook covers all 11 required sections: architecture overview with a text-based data-flow diagram, backup schedule and retention policies, prerequisites with a full environment variable table (matching .env.example and backup script requirements), manual backup trigger with expected JSON output, restore from local backup in 6 numbered steps, restore from off-site (S3) using aws s3 cp, partial table restore using pg_restore --table, fresh environment bootstrap including Flyway migration behavior, verification SQL queries, a troubleshooting table with 7 failure scenarios, and an escalation placeholder. The drill template covers purpose/frequency, prerequisites checklist, 7 timing checkpoints (T+0 through T+30) with exact copy-pasteable commands, a pass/fail criteria matrix, results recording template, 10 post-drill review questions, remediation tracking with severity levels, and a fully executed example drill showing 11-minute completion against the 30-minute RTO target.

## WO-088: User Story: WO-088 - Add Nginx Request Size Limits and Proxy Hardening
- **Status:** completed
- **Commit:** `33a03ed`
- **Files:** 2 (+359/-7)
- **Duration:** 207ss
- **Approach:** Built on the WO-087 nginx.conf (rate limiting + security headers) by adding all proxy hardening directives. Added an upstream backend_pool block with server backend:8080 and keepalive 32 in the http context, then updated all proxy_pass directives to use it. Added proxy_http_version 1.1 and proxy_set_header Connection '' to all proxy location blocks for HTTP/1.1 keepalive. Added proxy timeouts (connect 5s, read 30s, send 10s) to /health, /api/auth/, and /api/ locations. Added proxy buffer configuration (proxy_buffer_size 4k, proxy_buffers 8 8k, proxy_busy_buffers_size 16k) and large_client_header_buffers 4 8k to the server block. Added limit_conn_zone in the http context and limit_conn conn_limit 50 in the server block. Added a /health location proxying to http://backend_pool/actuator/health with access_log off and no rate limiting. Defined a structured log_format with 9 fields including upstream_response_time, applied it to the server access_log. Updated gzip to min_length 256, added gzip_vary on and gzip_proxied any. Added proxy_intercept_errors on and error_page 503 = @conn_limited with a new @conn_limited named location repeating the security headers and returning JSON.

## WO-009: User Story: WO-009 - Configure GitHub Actions CI Pipeline with Coverage Gate
- **Status:** completed
- **Commit:** `41194cd`
- **Files:** 2 (+18/-4)
- **Duration:** 219ss
- **Approach:** The CI workflow and JaCoCo plugin configuration already existed from prior work orders but were incomplete for this story's requirements. Two targeted changes were made: (1) pom.xml — replaced the placeholder JaCoCo check execution (BUNDLE element, 0% minimum) with a production gate using PACKAGE element scoped to the service and controller packages with a 0.80 LINE COVEREDRATIO minimum; (2) ci.yml — added --no-transfer-progress to the mvn verify command to reduce log noise, and added an actions/upload-artifact@v4 step to upload backend/target/site/jacoco/ as the 'coverage-report' artifact with 30-day retention and if: always() to ensure the report is available even when tests fail.

## WO-013: User Story: WO-013 - Add Flyway Dependencies and Disable ddl-auto
- **Status:** completed
- **Commit:** `1c3e99f`
- **Files:** 7 (+63/-2)
- **Duration:** 220ss
- **Approach:** Added Flyway dependencies (flyway-core and flyway-database-postgresql) to pom.xml with versions managed by the Spring Boot BOM. Removed ddl-auto from base application.yml and moved it to profile-specific files: application-dev.yml (update, for local convenience) and application-prod.yml (validate, for production fail-fast). Added spring.flyway.clean-disabled=true, locations=classpath:db/migration, and baseline-on-migrate=true to base application.yml so Flyway config is active in all profiles. Created the db/migration/ directory with .gitkeep to track it in version control. Updated docker-compose.yml to set SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev} so the dev profile is the default without breaking existing workflows. Created ApplicationContextTest that injects ApplicationContext and asserts it is not null, and verifies the Flyway bean is present in the context.
