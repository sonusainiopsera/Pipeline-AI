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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcRequestFilterTest {

    private final MdcRequestFilter filter = new MdcRequestFilter();

    @AfterEach
    void cleanup() {
        MDC.remove(MdcRequestFilter.MDC_REQUEST_ID);
        MDC.remove(MdcRequestFilter.MDC_ACTOR_EMAIL);
        MDC.remove(MdcRequestFilter.MDC_CLIENT_IP);
        SecurityContextHolder.clearContext();
    }

    // ── MDC population ────────────────────────────────────────────────────────

    @Test
    void shouldPopulateAllThreeMdcFieldsDuringChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        AtomicReference<String> requestId  = new AtomicReference<>();
        AtomicReference<String> actorEmail = new AtomicReference<>();
        AtomicReference<String> clientIp   = new AtomicReference<>();

        filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> {
            requestId.set(MDC.get(MdcRequestFilter.MDC_REQUEST_ID));
            actorEmail.set(MDC.get(MdcRequestFilter.MDC_ACTOR_EMAIL));
            clientIp.set(MDC.get(MdcRequestFilter.MDC_CLIENT_IP));
        });

        assertThat(requestId.get()).isNotNull().isNotBlank();
        assertThat(actorEmail.get()).isNotNull().isNotBlank();
        assertThat(clientIp.get()).isNotNull().isNotBlank();
    }

    @Test
    void shouldGenerateUuidFormattedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AtomicReference<String> capturedId = new AtomicReference<>();

        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> capturedId.set(MDC.get(MdcRequestFilter.MDC_REQUEST_ID)));

        assertThat(capturedId.get())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    // ── Unauthenticated request → anonymous ───────────────────────────────────

    @Test
    void shouldSetAnonymousEmailWhenNoAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        AtomicReference<String> captured = new AtomicReference<>();

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> captured.set(MDC.get(MdcRequestFilter.MDC_ACTOR_EMAIL)));

        assertThat(captured.get()).isEqualTo("anonymous");
    }

    // ── Authenticated request → actor name ────────────────────────────────────

    @Test
    void shouldExtractActorEmailFromSecurityContext() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                "alice@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        AtomicReference<String> captured = new AtomicReference<>();

        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> captured.set(MDC.get(MdcRequestFilter.MDC_ACTOR_EMAIL)));

        assertThat(captured.get()).isEqualTo("alice@example.com");
    }

    // ── Client IP extraction ──────────────────────────────────────────────────

    @Test
    void shouldUseRemoteAddrWhenNoXForwardedForHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");
        AtomicReference<String> captured = new AtomicReference<>();

        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> captured.set(MDC.get(MdcRequestFilter.MDC_CLIENT_IP)));

        assertThat(captured.get()).isEqualTo("192.168.1.50");
    }

    @Test
    void shouldExtractClientIpFromXForwardedForHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        AtomicReference<String> captured = new AtomicReference<>();

        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> captured.set(MDC.get(MdcRequestFilter.MDC_CLIENT_IP)));

        assertThat(captured.get()).isEqualTo("203.0.113.10");
    }

    @Test
    void shouldTakeFirstEntryFromMultiValueXForwardedForHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1, 10.0.0.2");
        AtomicReference<String> captured = new AtomicReference<>();

        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> captured.set(MDC.get(MdcRequestFilter.MDC_CLIENT_IP)));

        assertThat(captured.get()).isEqualTo("203.0.113.10");
    }

    // ── MDC cleanup ───────────────────────────────────────────────────────────

    @Test
    void shouldClearAllMdcFieldsAfterFilterChainCompletes() throws Exception {
        filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {});

        assertThat(MDC.get(MdcRequestFilter.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(MdcRequestFilter.MDC_ACTOR_EMAIL)).isNull();
        assertThat(MDC.get(MdcRequestFilter.MDC_CLIENT_IP)).isNull();
    }

    @Test
    void shouldClearMdcEvenWhenFilterChainThrows() throws Exception {
        try {
            filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (req, res) -> { throw new RuntimeException("simulated downstream failure"); });
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.get(MdcRequestFilter.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(MdcRequestFilter.MDC_ACTOR_EMAIL)).isNull();
        assertThat(MDC.get(MdcRequestFilter.MDC_CLIENT_IP)).isNull();
    }

    // ── MDC fields appear in log events (prerequisite for JSON log fields) ────

    @Test
    void shouldIncludeMdcFieldsInLogEventsViaListAppender() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("test.mdc.request");
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            filter.doFilterInternal(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (req, res) -> logger.info("test log inside request scope"));
        } finally {
            logger.detachAppender(listAppender);
        }

        assertThat(listAppender.list).hasSize(1);
        var mdcMap = listAppender.list.get(0).getMDCPropertyMap();
        assertThat(mdcMap)
                .containsKey(MdcRequestFilter.MDC_REQUEST_ID)
                .containsKey(MdcRequestFilter.MDC_ACTOR_EMAIL)
                .containsKey(MdcRequestFilter.MDC_CLIENT_IP);
    }
}
