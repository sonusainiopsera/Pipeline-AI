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

## WO-045: User Story: WO-045 - Extract ResponseTemplater with Configurable Templates
- **Status:** completed
- **Commit:** `bf14b98`
- **Files:** 8 (+280/-7)
- **Duration:** 273ss
- **Approach:** Captured the exact customer update template format from AnalysisService: the matched case uses String.format with rootCause and suggestedFix, and the unclassified case uses a static string. Created ResponseProperties @ConfigurationProperties(prefix='analysis.response') with two fields: template (default: '{rootCause}/{suggestedFix}' pattern) and unclassifiedMessage (default: static unclassified string). Created ResponseTemplater @Service with explicit constructor injection (no @RequiredArgsConstructor to allow null-check logic), using String.replace for {category}/{rootCause}/{suggestedFix} placeholder substitution, null-safe defaults for each field, and WARN logging + hardcoded fallback when the configured template is blank/null. Added analysis.response.* block to application.yml. Registered ResponseProperties in @EnableConfigurationProperties alongside ScoringProperties. Updated AnalysisService to inject ResponseTemplater and delegate both customer update paths. Updated AnalysisServiceTest and GoldenFileAnalysisTest with @Spy ResponseTemplater initialized with default ResponseProperties. Created ResponseTemplaterTest with 15 tests.

## WO-060: User Story: WO-060 - Implement Structured JSON Logging with Correlation IDs
- **Status:** completed
- **Commit:** `3841531`
- **Files:** 12 (+303/-5)
- **Duration:** 619ss
- **Approach:** Added logstash-logback-encoder:7.4 and micrometer-registry-prometheus (BOM-managed) to pom.xml. Created logback-spring.xml with two springProfile blocks: 'local' uses PatternLayoutEncoder with correlationId in the pattern, '!local' uses LogstashEncoder with customFields for app_name='pipeline-assistant' and environment from a <springProperty> pulling spring.profiles.active. MDC fields (correlationId) are automatically included by LogstashEncoder. Created CorrelationIdFilter extending OncePerRequestFilter with @Order(HIGHEST_PRECEDENCE) and @Component; generates UUID, reuses existing X-Correlation-Id header from upstream requests, puts correlationId in MDC, sets response header, and clears MDC in finally block. Added @Slf4j and INFO log statements to all 3 services (entry/exit for AnalysisService, CRUD operation logging for KnowledgeBaseService, stats-retrieval logging for DashboardService) and both controllers (request receipt logging). Added @Slf4j, HttpServletRequest parameter, and log.warn statements to ApiExceptionHandler for 4xx validation failures. Expanded Actuator exposure to health,info,prometheus,metrics,loggers and added metrics.tags.application. Created logback-test.xml with pattern layout and WARN root / INFO com.opsera levels. Created CorrelationIdFilterTest with 8 unit tests.

## WO-066: User Story: WO-066 - JaCoCo Coverage Gate Enforced in CI Pipeline
- **Status:** completed
- **Commit:** `d99bb1f`
- **Files:** 2 (+14/-0)
- **Duration:** 123ss
- **Approach:** The core JaCoCo setup (prepare-agent, report, check executions with 80% COVEREDRATIO on PACKAGE element for service and controller packages, and CI artifact upload) was already fully in place from WO-009. WO-066 added the two remaining items: (1) a global <configuration><excludes> block on the JaCoCo plugin covering entity, dto, config, exception packages and PipelineAssistantApplication.class, so Lombok-generated code and framework wiring classes are excluded from coverage measurement; (2) a lombok.config file with 'lombok.addLombokGeneratedAnnotation = true' so JaCoCo automatically recognises @lombok.Generated-annotated methods and excludes them from coverage.

## WO-014: User Story: WO-014 - Create V1 Baseline Migration from Current Schema
- **Status:** completed
- **Commit:** `ada0151`
- **Files:** 4 (+270/-0)
- **Duration:** 506ss
- **Approach:** Inspected AnalyzedLog.java and ErrorKnowledgeBase.java entities to derive exact Hibernate 6 column mappings using SpringPhysicalNamingStrategy (camelCase→snake_case). Created V1__baseline.sql with CREATE TABLE IF NOT EXISTS for both tables: error_knowledge_base (7 columns — id BIGSERIAL PK, error_pattern TEXT, category VARCHAR(255), root_cause TEXT, solution TEXT, severity VARCHAR(255), created_at TIMESTAMP) and analyzed_logs (9 columns — id BIGSERIAL PK, log_text TEXT, category VARCHAR(255), root_cause TEXT, suggested_fix TEXT, customer_update TEXT, severity VARCHAR(255), confidence INTEGER, created_at TIMESTAMP). Column types follow Hibernate 6 defaults: @Column(columnDefinition='TEXT')→TEXT, String without @Column→VARCHAR(255), Integer→INTEGER, LocalDateTime→TIMESTAMP, Long @Id @GeneratedValue(IDENTITY)→BIGSERIAL. No NOT NULL constraints added (entities have no @Column(nullable=false)) to avoid ddl-auto: validate failures. ErrorKnowledgeBase has 7 columns (no updated_at — the entity has no @UpdateTimestamp field). Added Testcontainers postgresql and junit-jupiter dependencies (Spring Boot BOM-managed, no explicit version). Created FlywayBaselineMigrationTest with @Testcontainers using postgres:16-alpine container; @DynamicPropertySource overrides datasource and sets ddl-auto=validate so Hibernate confirms schema-entity alignment. Created docs/runbook-flyway-baseline.md with baseline command, pre-flight checks, post-baseline verification, and rollback steps.

## WO-046: User Story: WO-046 - Refactor AnalysisService into Thin Orchestrator
- **Status:** completed
- **Commit:** `d5c4576`
- **Files:** 4 (+150/-34)
- **Duration:** 605ss
- **Approach:** Completed the three-part refactoring: (1) ScoredMatch record gained a totalPatterns field so PatternMatcher computes both match count and keyword count in one pass — eliminating the caller's re-parsing of errorPattern with a different split regex; (2) PatternMatcher.scoreEntry() now returns ScoredMatch directly, counting only non-blank keywords to maintain consistency between matchCount and totalPatterns; (3) AnalysisService.analyze() refactored to a clean linear pipeline — fetch KB entries, match via PatternMatcher, select best via stream reduce with strict > (preserving first-wins tie-breaking), delegate confidence to ScoringEngine and templating to ResponseTemplater, build and persist entity. No business logic remains in AnalysisService. Added AnalysisServiceIntegrationTest using MockMvc to exercise the full pipeline end-to-end for all 6 seed patterns plus the unclassified path.

## WO-061: User Story: WO-061 - Add Prometheus Grafana Loki Docker Compose Services
- **Status:** completed
- **Commit:** `09c3eba`
- **Files:** 6 (+199/-0)
- **Duration:** 194ss
- **Approach:** Added an explicit pipeline-net bridge network to docker-compose.yml and attached all existing services to it. Added 4 observability services (prometheus, loki, promtail, grafana) behind the 'observability' Docker Compose profile so plain 'docker compose up' starts only the original services unchanged. Created all required config files: prometheus.yml with 15s scrape interval targeting backend:8080/actuator/prometheus; loki-config.yml with filesystem backend and 168h retention; promtail-config.yml with docker_sd_configs via Docker socket, relabeling for container_name/service_name, and JSON pipeline stages; Grafana provisioning files auto-configuring Prometheus and Loki data sources. Added prometheus_data, grafana_data, and loki_data named volumes. All 4 observability services have health checks (wget --spider), restart: unless-stopped, and appropriate depends_on ordering (prometheus→backend, promtail→loki healthy, grafana→prometheus+loki).

## WO-067: User Story: WO-067 - Container Image Publishing to GitHub Container Registry
- **Status:** completed
- **Commit:** `de8963b`
- **Files:** 2 (+97/-0)
- **Duration:** 227ss
- **Approach:** Added tags: ['v*'] to the workflow push trigger to enable tag-based release builds. Added a 'publish' job gated behind needs: [backend, frontend] (test-gate) and a job-level if condition that limits execution to push events on main or v* tag events — pull request runs skip the job entirely. Buildx setup enables BuildKit + GitHub Actions cache backend. docker/login-action authenticates to ghcr.io using GITHUB_TOKEN (packages:write permission on the job). docker/metadata-action generates sha, branch, semver, and latest tags (latest only on is_default_branch) plus OCI image labels (source, revision, created) automatically. docker/build-push-action builds and pushes both backend and frontend with scoped GHA cache. Frontend receives VITE_API_URL=/api as a build arg for production reverse-proxy use. Created docker-compose.prod.yml as a Compose override that swaps build: for image: directives pointing to the registry, supporting REGISTRY_OWNER, BACKEND_IMAGE_TAG, and FRONTEND_IMAGE_TAG env vars for rollback and environment targeting.

## WO-015: User Story: WO-015 - Create V2 Migration for Users Table
- **Status:** completed
- **Commit:** `3fc20e0`
- **Files:** 9 (+340/-2)
- **Duration:** 413ss
- **Approach:** Created the V2 SQL migration with all three new tables and the analyzed_logs alteration. Placed JPA entities in the model package (consistent with existing AnalyzedLog and ErrorKnowledgeBase) rather than a new entity package. User uses @GeneratedValue(UUID) for application-layer UUID generation (the DB DEFAULT gen_random_uuid() acts as fallback). AuditLog uses @JdbcTypeCode(SqlTypes.JSON) + @Column(columnDefinition='jsonb') for the JSONB details column to ensure Hibernate correctly maps the type during ddl-auto validate. AnalyzedLog received a nullable @ManyToOne(LAZY) user field. FlywayBaselineMigrationTest was updated to fix the analyzed_logs column count (9→10) and extended with five new V2 tests covering table existence, column counts, the UUID type of analyzed_logs.user_id, role CHECK constraint presence, and Flyway history V2 applied status.

## WO-047: User Story: WO-047 - Golden-File Tests for Analysis Scoring Baseline
- **Status:** completed
- **Commit:** `2a93bf5`
- **Files:** 0 (+0/-0)
- **Duration:** 263ss
- **Approach:** WO-047 required establishing golden-file test infrastructure before the analysis service decomposition. All required artifacts were already implemented by earlier WOs in the batch: WO-001 created the test infrastructure (application-test.yml, pom.xml dependencies), WO-002 created GoldenFileAnalysisTest.java and all six golden files plus fixture log samples, and WO-043 through WO-046 kept the test class up-to-date as the analysis pipeline was refactored. The working tree is clean — no new files were needed. The existing GoldenFileAnalysisTest.java covers all 7 test cases (6 seed patterns + unclassified) with field-by-field assertion against golden JSON files. application-test.yml uses H2 in PostgreSQL compatibility mode. All test dependencies (spring-boot-starter-test, h2 test scope, testcontainers postgresql and junit-jupiter) are present in pom.xml.

## WO-048: User Story: WO-048 - Integrate Caffeine Cache for Knowledge Base Queries
- **Status:** completed
- **Commit:** `8a92153`
- **Files:** 10 (+301/-25)
- **Duration:** 465ss
- **Approach:** Added Caffeine in-process caching for the knowledge base query path. CacheConfig registers a CaffeineCacheManager with a 'knowledgeBase' cache (max 1000 entries, TTL from configurable property). KnowledgeBaseService.getAllEntries() is the @Cacheable cache boundary — AnalysisService calls through this bean (different Spring proxy) to avoid the self-invocation trap. CRUD mutators carry @CacheEvict(allEntries=true) to ensure stale entries are never served after writes. AnalysisServiceTest and GoldenFileAnalysisTest were updated to mock KnowledgeBaseService instead of ErrorRepository.

## WO-056: User Story: WO-056 - Create HistoryListDTO and Paginated Repository Query
- **Status:** completed
- **Commit:** `d6438a7`
- **Files:** 6 (+400/-0)
- **Duration:** 278ss
- **Approach:** Created Responses.java as a final class with private constructor containing the HistoryListDTO record — following the existing project pattern. HistoryListDTO has 7 fields (id, detectedCategory, rootCause, suggestedFix, severity, confidence, createdAt) deliberately excluding logText and customerUpdate per data classification policy. Added a static from() factory method mapping AnalyzedLog.category to detectedCategory. Extended AnalyzedLogRepository with Page<AnalyzedLog> findAllByOrderByCreatedAtDesc(Pageable) via Spring Data JPA derived query, keeping the existing findTop50 method intact. Added V3 Flyway migration for a DESC index on analyzed_logs.created_at for pagination performance.

## WO-062: User Story: WO-062 - Instrument Backend with Micrometer Custom Metrics
- **Status:** completed
- **Commit:** `ffbeab8`
- **Files:** 10 (+571/-56)
- **Duration:** 637ss
- **Approach:** Created MetricsConfig with a pre-registered Timer bean for analysis.duration (publishPercentileHistogram enabled) and placeholder Counter beans for auth.events (4 event_type values) and cache.operations (2 result values). Services inject MeterRegistry via @RequiredArgsConstructor and record dynamic-tag counters inline. All metric calls are wrapped in try-catch so failures never propagate to callers. Timer.Sample uses Clock.SYSTEM at start so no registry is needed until stop(), placed in a try-finally to guarantee recording even when exceptions occur. Added ResponseStatusException and catch-all Exception handlers to ApiExceptionHandler for complete error coverage.

## WO-068: User Story: WO-068 - Docker Compose Health Checks and Dependency Ordering
- **Status:** completed
- **Commit:** `251d87c`
- **Files:** 1 (+16/-1)
- **Duration:** 69ss
- **Approach:** Modified docker-compose.yml to add health checks to backend and frontend services, add start_period to the existing db health check, add restart: unless-stopped to db, and upgrade frontend depends_on from short-form list syntax to long-form condition: service_healthy. The backend was already using condition: service_healthy for db. This enforces a three-stage ordered startup: db healthy → backend starts and becomes healthy → frontend starts.

## WO-075: User Story: WO-075 - Harden Pipeline Log Analysis with Matched Patterns
- **Status:** completed
- **Commit:** `7c8f589`
- **Files:** 12 (+190/-12)
- **Duration:** 984ss
- **Approach:** Added matchedPatterns to the analysis pipeline end-to-end without schema changes: (1) ScoredMatch record gained a 4th field List<String> matchedPatterns; (2) PatternMatcher.scoreEntry() now collects matched keyword strings into matchedKeywords list while counting, returning them alongside the score; (3) AnalyzedLog entity gained a @Transient List<String> matchedPatterns field (never persisted); (4) AnalysisResponse record added to Responses.java with all prior fields plus matchedPatterns, with a from(AnalyzedLog) factory; (5) AnalysisService.analyze() extracts best.matchedPatterns() for classified results or empty list for unclassified and sets it on the entity; (6) AnalysisController.analyze() wraps the entity in AnalysisResponse.from() before returning. @Size and 413 handling were already in place from WO-046. Frontend api.ts updated to add optional matchedPatterns?: string[] to the AnalyzedLog interface.

## WO-016: User Story: WO-016 - Flyway CI Validation and Production Runbook
- **Status:** completed
- **Commit:** `e6173a6`
- **Files:** 4 (+447/-0)
- **Duration:** 358ss
- **Approach:** Extended the existing GitHub Actions CI workflow (which already had a PostgreSQL 16 service container and ran mvn clean verify) by adding SPRING_PROFILES_ACTIVE=ci to the Maven build environment. Created application-ci.yml Spring profile that activates ddl-auto: validate and explicit Flyway settings — this causes CI to apply all migrations via Flyway and then validate JPA entity alignment, failing fast on any schema mismatch. Created flyway-baseline.sh with pre-flight checks (table existence verification, double-baseline guard, backup reminder) and post-verification. Created a comprehensive runbook covering all required sections: pre-baseline checklist, baseline procedure with exact CLI commands for flyway baseline/info/validate/repair, post-baseline verification, migration failure troubleshooting (checksum mismatch, syntax errors, out-of-order, duplicates), rollback procedure with estimated RTO under 5 minutes, and monitoring via flyway_schema_history queries and startup log patterns.

## WO-017: User Story: WO-017 - Add Spring Security and JWT Dependencies
- **Status:** completed
- **Commit:** `d49d177`
- **Files:** 4 (+106/-0)
- **Duration:** 215ss
- **Approach:** Added all required security dependencies to pom.xml (spring-boot-starter-security, jjwt-api/impl/jackson 0.12.6 with impl and jackson as runtime scope, spring-security-test for test scope). Created SecurityConfig.java in the existing config package with @Configuration @EnableWebSecurity that defines a SecurityFilterChain bean — CSRF disabled (REST API), sessions set to STATELESS, CORS delegated to the existing WebConfig WebMvcConfigurer via Customizer.withDefaults(), and all requests permitted (anyRequest().permitAll()) as the auth-optional transitional state. Added JWT placeholder properties to application.yml under the jwt prefix with environment variable resolution and safe defaults. Created SecurityConfigTest following the CacheConfigTest pattern to verify the SecurityFilterChain bean loads in the Spring context with the test profile.

## WO-049: User Story: WO-049 - Expose Cache Hit Rate via Actuator Metrics
- **Status:** completed
- **Commit:** `b0b0e3a`
- **Files:** 4 (+119/-2)
- **Duration:** 391ss
- **Approach:** Added .recordStats() to the Caffeine.newBuilder() chain in CacheConfig.java — this single call enables hit/miss/eviction/size statistics which Spring Boot's CaffeineCacheMeterBinder auto-detects and registers with Micrometer. Added 'caches' to management.endpoints.web.exposure.include alongside the existing 'metrics' entry. Extended CacheConfigTest with a stats recording verification test that triggers a getIfPresent miss and asserts missCount >= 1 on the native Caffeine cache. Created CacheMetricsIntegrationTest that clears the cache before each test, performs POST /api/analyze requests to drive cache activity, then queries /actuator/metrics/cache.gets with cache:knowledgeBase and result:miss or result:hit tags to verify counts via JSONPath assertions.

## WO-051: User Story: WO-051 - Implement Immutable Audit Log Entity and Repository
- **Status:** completed
- **Commit:** `d3136cc`
- **Files:** 5 (+400/-8)
- **Duration:** 368ss
- **Approach:** No new Flyway migration was needed — the audit_logs table (with all required columns and composite indexes) already exists in V2__add_users_and_auth_tables.sql, and V3 is already taken by idx_analyzed_logs_created_at_desc. The existing AuditLog entity had three defects: (1) @Data generated setters, making it mutable; (2) details was typed as String instead of Map<String,Object>; (3) most @Column annotations lacked updatable=false. Fixed by replacing @Data with @Getter, changing details to Map<String,Object> with @JdbcTypeCode(SqlTypes.JSON), and adding updatable=false to all @Column and @JoinColumn annotations. AuditLogRepository gained two missing query methods: findByResourceTypeOrderByCreatedAtDesc and findByCreatedAtBefore, plus a @Modifying/@Transactional deleteByCreatedAtBefore for retention purge. Created AuditLogTestFixtures with 7 individual factory methods (LOGIN, LOGIN_FAILED, CREATE, UPDATE, DELETE, ANALYZE, PURGE) and a sampleLogs() method returning 5 records. AuditLogEntityTest has 6 pure unit tests verifying builder field population, null details, null actor, null resourceId, absence of setter methods (via reflection), and nested Map details. AuditLogRepositoryTest uses @DataJpaTest @ActiveProfiles('test') with H2 PostgreSQL mode and covers: JSONB roundtrip with Map, null details persistence, findByResourceTypeOrderByCreatedAtDesc ordering, findByCreatedAtBefore filtering, deleteByCreatedAtBefore atomic bulk delete, and fixture bulk persistence.

## WO-057: User Story: WO-057 - Update History Endpoint with Pageable Response
- **Status:** completed
- **Commit:** `29b8b12`
- **Files:** 6 (+441/-27)
- **Duration:** 420ss
- **Approach:** AnalysisRepository.findAllByOrderByCreatedAtDesc(Pageable) and HistoryListDTO were already present from prior WOs, so the work focused on wiring them into the service and controller. AnalysisService.getHistory() changed from returning List<AnalyzedLog> to accepting a Pageable parameter and returning Page<HistoryListDTO> via page.map(HistoryListDTO::from). AnalysisController.getHistory() now accepts @PageableDefault(size=20, sort='createdAt', direction=DESC) Pageable, applies server-side size clamping (>100 → 100) using PageRequest.of(), and returns Page<HistoryListDTO>. application.yml got spring.data.web.pageable.max-page-size=100 as defense-in-depth. The three existing history tests in AnalysisControllerTest were updated to use when(analysisService.getHistory(any(Pageable.class))).thenReturn(new PageImpl<>(...)) and assert against $.content[*] instead of the old $[*] array path. New AnalysisControllerHistoryTest covers 9 pagination scenarios, and AnalysisServiceHistoryTest covers 6 service-layer scenarios with Mockito.

## WO-063: User Story: WO-063 - Provision Grafana Dashboards and Alert Rules
- **Status:** completed
- **Commit:** `7c8b990`
- **Files:** 7 (+840/-1)
- **Duration:** 353ss
- **Approach:** Created three Grafana 11.x dashboard JSON files (schemaVersion 39) and one unified alerting YAML provisioned under the existing ./grafana/provisioning volume mount that already covers all subdirectories. Added explicit uid fields (prometheus, loki) to datasources.yml so alert rules and dashboard panels can reference data sources deterministically without relying on Grafana's auto-generated UUIDs. Updated dashboard.yml to set disableDeletion: true (was false). Created grafana/provisioning/alerting/alerts.yml with the three required alert rules using Grafana unified alerting classic_conditions format, plus a default email contact point and routing policy. Added GF_UNIFIED_ALERTING_ENABLED=true and GF_ALERTING_ENABLED=false to the grafana service in docker-compose.yml to activate Grafana 11 unified alerting. All JSON files pass python3 -m json.tool validation.

## WO-072: User Story: WO-072 - Upgrade Java 17 to Java 21 LTS
- **Status:** completed
- **Commit:** `f4ae35d`
- **Files:** 2 (+3/-3)
- **Duration:** 259ss
- **Approach:** Changed <java.version>17</java.version> to <java.version>21</java.version> in pom.xml. Updated both stages of the multi-stage Dockerfile: build stage from eclipse-temurin:17-jdk-alpine to eclipse-temurin:21-jdk-alpine and runtime stage from eclipse-temurin:17-jre-alpine to eclipse-temurin:21-jre-alpine. No Lombok version pin required — Spring Boot 3.3.5 BOM manages Lombok 1.18.32 which is Java 21 compatible (>= 1.18.28 required). No source code changes made per the WO constraint. docker-compose.yml required no changes as it contains no Java version references, only a build context pointer to ./backend.

## WO-018: User Story: WO-018 - Create User Entity and Repository
- **Status:** completed
- **Commit:** `cb09d09`
- **Files:** 5 (+200/-3)
- **Duration:** 235ss
- **Approach:** Created Role.java enum in the model package (consistent with all other entities in this project per WO-015 prior-changes) with values ANALYST, KB_ADMIN, MANAGER. Updated User.java's role field from String to Role enum with @Enumerated(EnumType.STRING), @Column(length=20, nullable=false), and @Builder.Default(Role.ANALYST). Added explicit length=255 to email and passwordHash columns as specified. Extended UserRepository with existsByEmail(String) returning boolean alongside the existing findByEmail method. Created UserTestFactory in a new testutil package (matching the WO-specified path) with six builder-pattern factory methods covering all role values and common test scenarios. Created UserRepositoryTest with @DataJpaTest @ActiveProfiles('test') and seven tests covering all acceptance criteria scenarios.

## WO-021: User Story: WO-021 - Implement CustomUserDetailsService for Authentication
- **Status:** completed
- **Commit:** `02de541`
- **Files:** 2 (+260/-0)
- **Duration:** 204ss
- **Approach:** Created a new security package containing CustomUserDetailsService (@Service @RequiredArgsConstructor) implementing Spring Security's UserDetailsService. loadUserByUsername() normalises the input email to lowercase (edge-case requirement), queries UserRepository.findByEmail(), throws UsernameNotFoundException with a message containing the email when absent, then maps the User entity to Spring Security UserDetails using the User.builder() fluent API: username=entity.getEmail(), password=entity.getPasswordHash(), authority=ROLE_+role.name() (satisfies Spring Security ROLE_ prefix convention), accountLocked=(lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())), disabled=!emailVerified. No SecurityConfig changes were required — Spring Boot 3.x auto-configuration detects the single UserDetailsService bean and wires it into DaoAuthenticationProvider automatically, replacing the default InMemoryUserDetailsManager. Created CustomUserDetailsServiceTest with 11 Mockito (@ExtendWith(MockitoExtension.class)) unit tests.

## WO-050: User Story: WO-050 - Validate Cache Performance Meets P95 Latency SLOs
- **Status:** completed
- **Commit:** `59ae886`
- **Files:** 3 (+396/-0)
- **Duration:** 700ss
- **Approach:** Created a Testcontainers-backed performance test suite that seeds 500 ErrorKnowledgeBase entries across 10 categories via KnowledgeBaseTestDataFactory, then measures AnalysisService.analyze() latency under three scenarios. Cold-cache scenario: clears Caffeine cache via CacheManager.getCache("knowledgeBase").clear(), runs 20 sequential requests (after 5 JVM warm-up requests), sorts latencies, asserts p95 < 200ms. Warm-cache scenario: primes cache, discards 5 warm-up requests, measures 100 requests, asserts p95 < 50ms. Post-eviction scenario: warms cache, calls KnowledgeBaseService.create() to trigger @CacheEvict(allEntries=true), asserts single subsequent request < 200ms. All three scenarios log p50/p95/p99 values at INFO level. Performance tests are excluded from default mvn test via <excludedGroups>performance</excludedGroups> in maven-surefire-plugin and run via mvn verify -Pperformance using the new performance Maven profile with maven-failsafe-plugin 3.2.5.

## WO-052: User Story: WO-052 - Implement AuditService for Centralized Audit Event Recording
- **Status:** completed
- **Commit:** `328ec71`
- **Files:** 3 (+484/-0)
- **Duration:** 601ss
- **Approach:** Created AuditService in a new com.opsera.pipelineassistant.audit package as a @Service bean with @RequiredArgsConstructor injecting AuditLogRepository. The public logEvent method is annotated @Transactional(propagation=REQUIRES_NEW) to isolate audit persistence from outer transaction rollbacks. Convenience methods logCreate/logUpdate/logDelete also carry REQUIRES_NEW (necessary because Spring AOP only intercepts calls through the proxy — internal this.logEvent() calls bypass the proxy, so the outer convenience method must own the transaction boundary). The entire logEvent body is wrapped in try-catch: repository exceptions are logged at ERROR level and swallowed so audit failures never propagate to callers. resolveActorEmail() reads from SecurityContextHolder: returns authentication.getName() for authenticated users, 'SYSTEM' for null/unauthenticated/anonymous contexts (supporting scheduled tasks and startup events). resolveClientIp() reads from RequestContextHolder: checks X-Forwarded-For header first (taking first IP from comma-separated proxy chain for Nginx support), falls back to request.getRemoteAddr(), returns null when no HTTP request context exists. The AuditLog entity is built with actorEmail, action, resourceType, resourceId, details, and ipAddress; the actor (User JPA entity FK) is left null since the standard Spring Security User from CustomUserDetailsService does not carry a UUID — actorEmail is the primary actor identifier.

## WO-054: User Story: WO-054 - Implement Automated Data Retention Purge Scheduler
- **Status:** completed
- **Commit:** `a1d3391`
- **Files:** 7 (+403/-1)
- **Duration:** 555ss
- **Approach:** Created SchedulerConfig (@Configuration @EnableScheduling) in the existing config package to activate Spring's scheduling infrastructure without touching PipelineAssistantApplication. Created DataRetentionScheduler (@Component @RequiredArgsConstructor @Slf4j) in a new scheduler package. The purge method is @Scheduled(cron='0 0 2 * * *', zone='UTC') + @Transactional, so both delete operations run in the same DB transaction. Retention periods are injected via @Value with :90 and :365 defaults, also documented in application.yml under a new retention: section. After both deletes run: if both counts are 0, logs INFO and returns without creating an audit event (AC8). If any records were deleted, calls auditService.logEvent('PURGE', 'ANALYSIS', null, details) with a HashMap containing analyzedLogsDeleted and auditLogsDeleted integer counts. The whole method body is wrapped in try-catch so uncaught exceptions are logged at ERROR and the scheduler thread is never killed. Added deleteByCreatedAtBefore with JPQL @Query returning int to AnalyzedLogRepository (new method) and changed the existing void method in AuditLogRepository to return int with explicit JPQL — both needed to report deletion counts. Unit tests use ReflectionTestUtils.setField to inject @Value fields into the Mockito-created instance. Integration tests use JdbcTemplate.update to backdate created_at after JPA save (bypassing @CreationTimestamp), which cleanly avoids the complexity of @Transactional test methods conflicting with REQUIRES_NEW in AuditService.logEvent.

## WO-058: User Story: WO-058 - Add History Detail Endpoint with 404 Handling
- **Status:** completed
- **Commit:** `db271be`
- **Files:** 6 (+381/-0)
- **Duration:** 476ss
- **Approach:** Added HistoryDetailDTO to Responses.java as a new record with all AC3 fields (id, logText, detectedCategory mapped from entity category, rootCause, suggestedFix, customerUpdate, severity, confidence, createdAt) plus a static factory from(AnalyzedLog, String sanitizedLogText). Added historyDetail(Long id) to AnalysisService: uses findById with orElseThrow(ResponseStatusException NOT_FOUND), sanitizes logText via existing logSanitizer bean (null-safe: skips sanitization if logText is null; failure-safe: catches sanitizer exceptions and returns null logText). Added @GetMapping('/history/{id}') to AnalysisController with id <= 0 guard throwing 400 and delegation to analysisService.historyDetail(id). Added MethodArgumentTypeMismatchException handler to ApiExceptionHandler returning HTTP 400 with structured {timestamp, message, status} JSON for non-numeric path variable values (e.g. /api/history/abc). @PreAuthorize placeholder comment added to the service method referencing the Security epic.

## WO-073: User Story: WO-073 - Upgrade Spring Boot 3.3.5 to 3.5.x
- **Status:** completed
- **Commit:** `781fd6e`
- **Files:** 1 (+2/-2)
- **Duration:** 318ss
- **Approach:** Upgraded spring-boot-starter-parent from 3.3.5 to 3.5.3 in pom.xml. Reviewed all potential breaking changes from Spring Boot 3.4 and 3.5 migration guides: WebConfig.java uses addCorsMappings() which is not deprecated in 3.5.x; ApiExceptionHandler.java uses HttpStatus.resolve(int) which is not deprecated in Spring Framework 6.2+; application.yml properties (spring.jpa.database-platform, flyway.baseline-on-migrate, spring.data.web.pageable.max-page-size, management.endpoints configuration, jwt/retention/cache properties) all remain valid. One required companion change identified: logstash-logback-encoder was pinned at 7.4 (Logback 1.4.x series), but Spring Boot 3.4+ upgrades the managed Logback version to 1.5.x — logstash-logback-encoder 7.x is not binary compatible with Logback 1.5.x, so it was upgraded to 8.0 which targets Logback 1.5.x. No source code changes were necessary.

## WO-019: User Story: WO-019 - Create RefreshToken Entity and Repository
- **Status:** completed
- **Commit:** `0ada049`
- **Files:** 5 (+268/-2)
- **Duration:** 466ss
- **Approach:** RefreshToken entity and repository pre-existed from blocker WO-018 but were incomplete. RefreshToken.java was updated to add @Table indexes matching V2 migration (idx_refresh_tokens_user_id, idx_refresh_tokens_expires_at) and unique = true on tokenHash for defence-in-depth against hash collision exploitation. A V4 Flyway migration adds the UNIQUE constraint to PostgreSQL (V2 omitted it). RefreshTokenRepository was extended with the three missing methods: deleteByTokenHash, countByUserId (uses Spring Data JPA property path traversal through the @ManyToOne User user field to resolve user.id), and deleteAllByExpiresAtBefore; all delete methods annotated with @Transactional @Modifying per WO spec. RefreshTokenTestFactory was created in testutil package with valid/expired helpers and HASH_A..D constants. RefreshTokenRepositoryTest uses @DataJpaTest + H2 (ddl-auto: create) with @BeforeEach persisting users without entityManager.clear() to keep User entities managed when building RefreshToken instances linked via @ManyToOne.

## WO-055: User Story: WO-055 - Implement Structured JSON Audit Logging with Logback
- **Status:** completed
- **Commit:** `b0ae7ff`
- **Files:** 6 (+444/-8)
- **Duration:** 688ss
- **Approach:** MdcRequestFilter (OncePerRequestFilter, @Order(1)) populates MDC with request_id (UUID), actor_email (SecurityContext or 'anonymous'), and client_ip (X-Forwarded-For or remoteAddr) for every request, clearing all keys in a finally block. RedactingMessageJsonProvider extends MessageJsonProvider from logstash-logback-encoder and applies pre-compiled regex patterns to redact passwords, secrets, tokens, Bearer JWTs, AWS access keys, and PEM key markers before writing the message field to JSON. The !local profile in logback-spring.xml is updated from LogstashEncoder to LoggingEventCompositeJsonEncoder, which allows RedactingMessageJsonProvider to replace the default MessageJsonProvider. The local profile pattern is enriched with all new MDC fields. application.yml gains logging.level.com.opsera.pipelineassistant=INFO and spring.output.ansi.enabled=NEVER. logstash-logback-encoder 8.0 was already present (added in WO-073) so no pom.xml change is needed. @Order(1) places MdcRequestFilter after Spring Security's DelegatingFilterProxy (-100) so actor_email will reflect the authenticated principal once JWT auth is wired in.

## WO-059: User Story: WO-059 - Frontend History Page with Pagination Controls
- **Status:** completed
- **Commit:** `dea04e5`
- **Files:** 4 (+390/-72)
- **Duration:** 323ss
- **Approach:** Added HistoryListItem, HistoryItem, and PageResponse<T> types to api.ts (no separate types.ts exists in the project — all types live in api.ts). Added history(page, size) and historyDetail(id) API functions. Added SkeletonLoader component to Common.tsx using the existing CSS-class pattern; added skeleton-pulse keyframe to styles.css. Rewrote History.tsx to use pagination state (currentPage drives a useEffect → fetchPage → setPageData cycle so pagination button clicks only need setCurrentPage), accordion expansion with lazy detail loading via historyDetail(), inline detail error/retry, empty state, error state with retry, skeleton on initial load, and dimmed opacity on page transitions. The SanitizedLogDisplay component is reused for logText with a null-guard that shows 'No log text available.' for null logText records. react-hot-toast is not installed so errors are handled inline (consistent with the existing Analyze.tsx pattern which also uses inline error state, not toast). The Severity component referenced in the WO does not exist; a severityStyle() helper function is implemented inline in History.tsx.

## WO-020: User Story: WO-020 - Implement JWT Token Provider Service
- **Status:** completed
- **Commit:** `e152131`
- **Files:** 3 (+391/-0)
- **Duration:** 273ss
- **Approach:** Created JwtTokenProvider in the existing security package using JJWT 0.12.6 (already in pom.xml). The class uses @Value injection for jwt.secret, jwt.access-token-expiration (seconds), and jwt.refresh-token-expiration (seconds) from application.yml, which already had the jwt.* configuration block added in a prior WO. @PostConstruct init() converts the secret string to bytes, validates the minimum 32-byte length for HMAC-SHA256, and calls Keys.hmacShaKeyFor() to build the SecretKey. generateAccessToken() uses the JJWT 0.12.x fluent API (Jwts.builder().subject/claim/issuedAt/expiration/signWith) converting seconds to milliseconds for the Date calculation. generateRefreshToken() uses SecureRandom.nextBytes(32) + HexFormat.of().formatHex() to produce a 64-character hex string that is NOT a JWT. validateToken() calls Jwts.parser().verifyWith().build().parseSignedClaims() inside a try-catch covering ExpiredJwtException, MalformedJwtException, SignatureException, UnsupportedJwtException, and IllegalArgumentException; returns boolean without rethrowing. extractEmail/extractRole call a private parseClaims() helper. JwtTestHelper builds configured JwtTokenProvider instances via ReflectionTestUtils.setField() without a Spring context. No application.yml changes were needed since the jwt.* block was already present.

## WO-074: User Story: WO-074 - Update Dockerfile Multi-Stage Build for Java 21
- **Status:** completed
- **Commit:** `a6fa963`
- **Files:** 0 (+0/-0)
- **Duration:** 232ss
- **Approach:** WO-074 required updating the Dockerfiles to use multi-stage builds with Java 21, updating docker-compose.yml with health checks for all services, and verifying nginx.conf has SPA routing. Inspection of all four key files revealed all requirements were already fully implemented by prior WOs: WO-072 ([WO-073] Upgrade Spring Boot 3.3.5 to 3.5.3 chain) updated backend/Dockerfile to eclipse-temurin:21-jdk-alpine/21-jre-alpine and frontend/Dockerfile to node:22-alpine/nginx:stable-alpine; WO-068 added health checks to docker-compose.yml for all three services (backend wget, frontend wget, db pg_isready) and set depends_on conditions to service_healthy; frontend/nginx.conf already contains try_files $uri $uri/ /index.html for SPA routing and gzip compression. No code changes were required or made for this WO.

## WO-022: User Story: WO-022 - Implement JWT Authentication Filter
- **Status:** completed
- **Commit:** `f0a5e4f`
- **Files:** 8 (+506/-1)
- **Duration:** 726ss
- **Approach:** Created JwtAuthenticationFilter in the security package extending OncePerRequestFilter with constructor injection of JwtTokenProvider and CustomUserDetailsService (both already present from WO-020/WO-021). The filter uses two private extraction methods: extractTokenFromCookie() reads the 'access_token' cookie, extractTokenFromHeader() strips the 'Bearer ' prefix from the Authorization header. Cookie takes priority: the header is only tried when the cookie yields null. shouldNotFilter() skips /api/auth/** and /actuator/** paths. doFilterInternal() wraps all processing in a try-catch — on success, a UsernamePasswordAuthenticationToken (with WebAuthenticationDetailsSource details) is set in SecurityContextHolder; on any exception, the context is cleared and the filter chain continues without authentication. SecurityConfig.securityFilterChain() now accepts JwtAuthenticationFilter as a method parameter and registers it before UsernamePasswordAuthenticationFilter via addFilterBefore(); the existing permitAll() posture is preserved for WOREF-026. Since @WebMvcTest slices include Filter beans, the four existing controller @WebMvcTest tests were updated to add @MockBean JwtTokenProvider and @MockBean CustomUserDetailsService to satisfy the filter's constructor dependencies in the slice context — their mocked default return values (false for validateToken) keep all existing test assertions passing.

## WO-030: User Story: WO-030 - Implement Email Verification Endpoint
- **Status:** completed
- **Commit:** `e829259`
- **Files:** 10 (+445/-0)
- **Duration:** 459ss
- **Approach:** Created EmailService interface and ConsoleEmailService (@Profile('dev')) for token delivery. AuthService implements register (UUID token + 24h expiry + BCrypt password hash), verifyEmail (validates token, checks expiry, activates account), and resendVerification (email-enumeration-safe no-op for unknown/verified). AuthController exposes GET /api/auth/verify and POST /api/auth/verify/resend. Added PasswordEncoder @Bean to SecurityConfig and findByVerificationToken to UserRepository.

## WO-031: User Story: WO-031 - Implement Expired Token Cleanup Scheduler
- **Status:** completed
- **Commit:** `060b89e`
- **Files:** 7 (+297/-1)
- **Duration:** 320ss
- **Approach:** Added @ConditionalOnProperty to existing SchedulerConfig so the scheduler can be disabled in test contexts. Created TokenCleanupScheduler with @Scheduled(cron='0 0 2 * * *', zone='UTC') that batch-deletes expired refresh tokens (countByExpiresAtBefore then deleteAllByExpiresAtBefore) and batch-clears expired verification token fields from unverified users via a new @Modifying @Query on UserRepository. Added countByExpiresAtBefore to RefreshTokenRepository for count logging. Added app.scheduler.enabled: true to application.yml with disable instructions.

## WO-023: User Story: WO-023 - Implement User Registration Endpoint
- **Status:** completed
- **Commit:** `2d81789`
- **Files:** 9 (+406/-9)
- **Duration:** 341ss
- **Approach:** Added POST /api/auth/register to the existing AuthController (from WO-030). AuthService.register() was updated to normalize email to lowercase/trim, validate password complexity via regex (uppercase, lowercase, digit, special char, min 12 chars), and return a 409 with an enumeration-safe generic message for duplicate emails. BCryptPasswordEncoder upgraded to cost-12. RegisterRequest DTO with @Email/@NotBlank/@Size/@Pattern, RegisterResponse in Responses.java. AuthServiceVerificationTest updated to use valid passwords after complexity validation was added.

## WO-025: User Story: WO-025 - Implement Token Refresh and Logout Endpoints
- **Status:** completed
- **Commit:** `a431800`
- **Files:** 5 (+491/-0)
- **Duration:** 321ss
- **Approach:** Added refresh() and logout() to AuthService with SHA-256 hashing via MessageDigest. refresh() verifies the stored hash, checks expiry (deletes+401 if expired), generates new access+refresh tokens via JwtTokenProvider, rotates the DB record (delete-then-save). logout() is a best-effort delete that catches all exceptions. AuthController wraps refresh() in try-catch to ensure 401+cookie-clearing on any service error. logout() always returns 200 with Max-Age=0 cookies. RefreshResult(accessToken, refreshToken) record added to Responses.java.

## WO-032: User Story: WO-032 - Update Frontend API Client for Authentication
- **Status:** completed
- **Commit:** `60d059b`
- **Files:** 7 (+669/-90)
- **Duration:** 294ss
- **Approach:** Refactored api.ts to use a central request<T>() helper that adds credentials:'include' to every fetch call. Implemented a 401 interceptor with isRefreshing flag and refreshQueue array: on 401, one refresh attempt is made; concurrent requests queue and wait for the result; success drains the queue with retries, failure drains with rejections and redirects to /login. Auth endpoints are excluded from the refresh loop. Added type-safe auth functions (login, register, verifyEmail, logout, refreshToken, mfaSetup, mfaVerify, mfaChallenge, mfaRecover, getMe). Created AuthContext with user/isAuthenticated/isLoading state and AuthProvider that calls checkAuth on mount. Added ProtectedRoute and wrapped App with AuthProvider.

## WO-024: User Story: WO-024 - Implement JWT Login Endpoint with Cookies
- **Status:** completed
- **Commit:** `8baf44e`
- **Files:** 11 (+658/-1)
- **Duration:** 611ss
- **Approach:** Added AuthService.login() implementing the full login flow: email normalization, emailVerified check (EmailNotVerifiedException/403), lockedUntil check with expired-lock cleanup (AccountLockedException/423), BCrypt password verification, failedLoginAttempts increment/reset, lockedUntil set after 5th failure, session limit enforcement via countByUserId + findFirstByUserIdOrderByCreatedAtAsc (delete oldest when >= 3), token generation and refresh token hash storage. AuthController POST /login calls login() and sets access_token (Path=/api, Max-Age=900) and refresh_token (Path=/api/auth, Max-Age=604800) as HTTP-only secure SameSite=Strict cookies, returning only the user profile in the response body. Exception handlers for AccountLockedException (423) and EmailNotVerifiedException (403) added to ApiExceptionHandler.

## WO-027: User Story: WO-027 - Implement Role-Based Access Control with PreAuthorize
- **Status:** completed
- **Commit:** `5e95387`
- **Files:** 10 (+458/-8)
- **Duration:** 538ss
- **Approach:** Added @EnableMethodSecurity to SecurityConfig and updated the SecurityFilterChain to require authentication for all /api/** endpoints while permitting /api/auth/** and /actuator/** publicly. Added @PreAuthorize('hasAnyRole(ANALYST, KB_ADMIN, MANAGER)') to all AnalysisController methods (analyze, getHistory, getHistoryDetail, getDashboard) and to ErrorController.findAll(). Added @PreAuthorize('hasAnyRole(KB_ADMIN, MANAGER)') to ErrorController create/update/delete methods. Added AccessDeniedException handler to ApiExceptionHandler returning 403 JSON {timestamp, message, status} — this ensures @PreAuthorize failures produce consistent JSON responses instead of falling through to the catch-all 500 handler. Updated 4 existing controller test classes with @WithMockUser to prevent regressions from the new security requirements. Created 30 RBAC tests (17 in AnalysisControllerRbacTest, 13 in ErrorControllerRbacTest) covering all role-endpoint combinations including JSON error body verification.

## WO-053: User Story: WO-053 - Integrate Audit Logging into Knowledge Base Mutations
- **Status:** completed
- **Commit:** `2e2f3cb`
- **Files:** 7 (+610/-8)
- **Duration:** 606ss
- **Approach:** Injected AuditService into KnowledgeBaseService and AnalysisService via Lombok @RequiredArgsConstructor. Added @Transactional to all three KB mutation methods. In create(), called auditService.logCreate after repository.save() with a HashMap of {category, severity, errorPattern}. In update(), captured before-state into a HashMap before any setXxx() calls on the entity, then built an after-state map post-save, and called auditService.logUpdate with a {before, after} details map. In delete(), changed the discarded findById() call to capture the entity, then called auditService.logDelete with the entity state after deleteById(). In AnalysisService.analyze(), changed return statement to capture the saved AnalyzedLog, then called auditService.logEvent('ANALYZE', 'ANALYSIS', id, {category, confidence, severity}) — logText is explicitly excluded. All audit calls are wrapped in defensive try-catch so KB/analysis mutations succeed even if audit persistence fails. Updated existing tests that used direct constructor invocation to pass the new AuditService mock as an additional argument.

## WO-026: User Story: WO-026 - Configure SecurityFilterChain with Endpoint Protection
- **Status:** completed
- **Commit:** `7bb3bda`
- **Files:** 7 (+353/-13)
- **Duration:** 542ss
- **Approach:** Created CustomAuthenticationEntryPoint (implements AuthenticationEntryPoint, returns 401 JSON {timestamp, message: 'Authentication required', status: 401}) and CustomAccessDeniedHandler (implements AccessDeniedHandler, returns 403 JSON {timestamp, message: 'Access denied', status: 403}). Split SecurityConfig into two @Profile-based SecurityFilterChain beans: @Profile('!auth-optional') (auth-required chain — enforces authentication on /api/** except /api/auth/** and /actuator/health, /actuator/info, STATELESS sessions, CSRF disabled, custom entry point and access denied handler) and @Profile('auth-optional') (permissive chain — anyRequest().permitAll(), JWT filter still registered, warns at startup). The '!auth-optional' expression is chosen so existing tests with @ActiveProfiles('test') or no profile continue using the restrictive chain. Updated WebConfig to use app.cors.allowed-origins instead of wildcard, add allowCredentials(true), and restrict allowedHeaders to Content-Type/Accept/X-Requested-With/Authorization. Added spring.profiles.active: auth-optional to application.yml as the default.

## WO-028: User Story: WO-028 - Implement MFA Setup and TOTP Enrollment
- **Status:** completed
- **Commit:** `3401e3b`
- **Files:** 13 (+824/-0)
- **Duration:** 730ss
- **Approach:** Implemented TOTP-based MFA enrollment via two new endpoints on AuthController. AesEncryptionUtil uses AES-256-GCM with a random 12-byte IV (prepended to ciphertext, Base64-encoded) to encrypt TOTP secrets at rest; the key is derived from a config property via SHA-256. MfaService orchestrates the full flow: generates a base32 TOTP secret using dev.samstevens.totp, builds an otpauth:// QR URI, generates 8 eight-character alphanumeric recovery codes (BCrypt-hashed for storage as a JSON array), encrypts and stores the secret with a 10-minute setup expiry, then on verify decrypts the secret, validates the code with ±1 time-step tolerance, and sets mfaEnabled=true. Both endpoints require authentication via @PreAuthorize("isAuthenticated()"). Flyway migration V5 adds mfa_setup_expires_at and recovery_codes columns.

## WO-033: User Story: WO-033 - Create Login and MFA Frontend Pages
- **Status:** completed
- **Commit:** `be2fc97`
- **Files:** 12 (+1391/-3)
- **Duration:** 1204ss
- **Approach:** Created four auth pages following existing component patterns (inline styles using established design tokens, useState hooks, direct api calls). App.tsx uses window.location.pathname to detect auth routes (/login, /register, /mfa/verify, /mfa/enroll) and renders the corresponding page outside the ProtectedRoute/Layout wrapper — consistent with the existing state-based routing architecture (no react-router-dom). QR code is displayed as a copyable text textarea showing the otpauth:// URI, as the WO explicitly allows this alternative. Fixed MfaSetupResponse type to use qrCodeUri (matching WO-028 backend) and updated authResponses fixture. Tests use vi.mock('../api') with Vitest's hoisting to mock the api module and assert rendering, validation, submission, and redirect behavior.

## WO-029: User Story: WO-029 - Enforce MFA Verification During Login Flow
- **Status:** completed
- **Commit:** `cd9c791`
- **Files:** 11 (+726/-9)
- **Duration:** 889ss
- **Approach:** Implemented a two-step MFA login flow. AuthService.login() branches on mfaEnabled: MFA-enabled users receive a 5-minute JWT challenge token (set as an mfa_challenge HttpOnly cookie, stored as SHA-256 hash on User.mfaChallengeTokenHash for single-use enforcement) instead of access/refresh tokens. Two new endpoints complete login: POST /mfa/challenge validates the cookie + TOTP code, and POST /mfa/recover validates the cookie + a BCrypt-matched recovery code. Both clear the challenge token hash before verifying to enforce single-use regardless of outcome. JwtTokenProvider gained generateMfaChallengeToken/validateMfaChallengeToken/extractMfaChallengeEmail methods using a type=mfa-challenge claim to distinguish challenge tokens from access tokens. AuthService gained AesEncryptionUtil and ObjectMapper dependencies to decrypt the TOTP secret and parse/update the JSON recovery codes list. Non-MFA users receive full access+refresh tokens with mfaRequired=false in LoginResponse.

## WO-034: User Story: WO-034 - Add User ID to Analyzed Logs
- **Status:** completed
- **Commit:** `4a517d9`
- **Files:** 3 (+199/-0)
- **Duration:** 293ss
- **Approach:** The AnalyzedLog entity already had a nullable @ManyToOne User user field mapped to user_id FK (added by V2 migration), and the DB column/index already existed. The remaining work was: (1) adding UserRepository to AnalysisService, (2) adding extractCurrentUser() to get the email from SecurityContextHolder, look up the User entity, and return null gracefully for anonymous/unauthenticated requests, (3) wiring .user(extractCurrentUser()) into the AnalyzedLog.builder() in analyze(), and (4) adding findByUser_IdOrderByCreatedAtDesc(UUID userId) to AnalyzedLogRepository using Spring Data JPA nested property syntax. No new DB migration was needed since the schema was already up to date.

## WO-035: User Story: WO-035 - Implement Audit Logging for Auth Events
- **Status:** completed
- **Commit:** `c52a7f3`
- **Files:** 6 (+472/-13)
- **Duration:** 725ss
- **Approach:** The audit infrastructure (AuditLog entity, AuditService, AuditLogRepository, DB migration V2) already existed. This WO adds: (1) IpAddressUtil utility checking X-Forwarded-For then X-Real-IP then getRemoteAddr(); (2) public static final constants for 8 auth action names and 2 resource types on AuditService; (3) AuditService.resolveClientIp() refactored to delegate to IpAddressUtil (adds X-Real-IP support); (4) findByResourceTypeAndCreatedAtBetween added to AuditLogRepository; (5) AuditService injected into AuthService via @RequiredArgsConstructor with 13 logEvent call-sites covering all 8 required event types, each wrapped in try-catch to prevent audit failures from affecting primary auth flows.

## WO-036: User Story: WO-036 - Update CORS Configuration for Cookie Authentication
- **Status:** completed
- **Commit:** `7463fde`
- **Files:** 2 (+112/-2)
- **Duration:** 248ss
- **Approach:** WebConfig.java already had allowCredentials(true), allowedOrigins from property, allowedMethods, and partial allowedHeaders. Three changes were needed: (1) remove 'Authorization' from allowedHeaders — cookie-based auth doesn't require it cross-origin per AC3; (2) add exposedHeaders('Set-Cookie') so browsers can access cookie-setting response headers; (3) add maxAge(3600) for preflight cache. SecurityConfig already delegates CORS to WebConfig via cors(Customizer.withDefaults()) in both filter chains — no changes needed. CorsConfigTest added using @WebMvcTest(AuthController.class) to verify preflight returns correct headers, credentials flag, maxAge, and that disallowed origins receive no CORS headers.
