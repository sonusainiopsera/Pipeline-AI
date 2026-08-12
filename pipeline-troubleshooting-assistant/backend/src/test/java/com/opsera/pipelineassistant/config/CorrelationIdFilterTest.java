package com.opsera.pipelineassistant.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanUpMdc() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    // ── MDC behaviour ─────────────────────────────────────────────────────────

    @Test
    void shouldSetMdcCorrelationIdDuringFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedMdcValue = new AtomicReference<>();

        FilterChain chain = (req, res) -> capturedMdcValue.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilterInternal(request, response, chain);

        assertThat(capturedMdcValue.get()).isNotNull().isNotBlank();
    }

    @Test
    void shouldClearMdcAfterFilterChainCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {});

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void shouldClearMdcEvenWhenFilterChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilterInternal(request, response, (req, res) -> {
                throw new RuntimeException("simulated downstream failure");
            });
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    // ── Response header ───────────────────────────────────────────────────────

    @Test
    void shouldAddXCorrelationIdResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotNull().isNotBlank();
    }

    @Test
    void shouldSetResponseHeaderToSameValueAsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedMdcValue = new AtomicReference<>();

        filter.doFilterInternal(request, response,
                (req, res) -> capturedMdcValue.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
                .isEqualTo(capturedMdcValue.get());
    }

    // ── Generated correlation ID format ───────────────────────────────────────

    @Test
    void shouldGenerateUuidFormattedCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedId = new AtomicReference<>();

        filter.doFilterInternal(request, response,
                (req, res) -> capturedId.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        // Standard UUID: 8-4-4-4-12 hex digits separated by hyphens
        assertThat(capturedId.get())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    // ── Existing header reuse ─────────────────────────────────────────────────

    @Test
    void shouldReuseExistingXCorrelationIdFromRequest() throws Exception {
        String existingId = "existing-correlation-id-from-upstream";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, existingId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedId = new AtomicReference<>();

        filter.doFilterInternal(request, response,
                (req, res) -> capturedId.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(capturedId.get()).isEqualTo(existingId);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(existingId);
    }

    // ── MDC included in log events (prerequisite for JSON log fields) ─────────

    @Test
    void shouldIncludeCorrelationIdInLogEventsViaListAppender() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("test.correlation");
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilterInternal(request, response, (req, res) -> logger.info("log inside request scope"));
        } finally {
            logger.detachAppender(listAppender);
        }

        assertThat(listAppender.list).hasSize(1);
        assertThat(listAppender.list.get(0).getMDCPropertyMap())
                .containsKey(CorrelationIdFilter.MDC_KEY);
    }
}
