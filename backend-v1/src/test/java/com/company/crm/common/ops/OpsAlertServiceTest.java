package com.company.crm.common.ops;

import com.company.crm.common.exception.GlobalExceptionHandler;
import com.company.crm.common.mail.MailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** ERR-5: critical errors alert the operations team, throttled, without the error's message or data. */
class OpsAlertServiceTest {

    private final MailService mail = mock(MailService.class);

    private OpsAlertService service(String emails) {
        OpsAlertService s = new OpsAlertService(mail, new ObjectMapper());
        ReflectionTestUtils.setField(s, "alertEmails", emails);
        ReflectionTestUtils.setField(s, "webhookUrl", "");
        ReflectionTestUtils.setField(s, "throttleMinutes", 15L);
        ReflectionTestUtils.setField(s, "environment", "crm-test");
        return s;
    }

    private static RuntimeException failure() {
        try {
            throw new IllegalStateException("customer +919876543210 secret details");
        } catch (IllegalStateException e) {
            return e;
        }
    }

    @Test
    void alertsOnceperWindowWithoutTheMessage() throws Exception {
        OpsAlertService s = service("ops@example.com, oncall@example.com");
        RuntimeException error = failure();
        assertThat(s.critical("ABC12345", "GET /api/customers/1", error)).isTrue();
        assertThat(s.critical("DEF67890", "GET /api/customers/1", error)).isFalse(); // same fault, throttled
        verify(mail, timeout(2000)).sendCalendarEmail(eq("ops@example.com"), contains("ABC12345"), argThat(body ->
                body.contains("IllegalStateException") && body.contains("GET /api/customers/1") && !body.contains("+919876543210")), isNull());
        verify(mail, timeout(2000)).sendCalendarEmail(eq("oncall@example.com"), anyString(), anyString(), isNull());
        verify(mail, after(300).times(2)).sendCalendarEmail(anyString(), anyString(), anyString(), any());
    }

    @Test
    void nothingIsSentWhenNotConfigured() {
        OpsAlertService s = service("");
        assertThat(s.isConfigured()).isFalse();
        assertThat(s.critical("X", "job", failure())).isFalse();
        verifyNoInteractions(mail);
    }

    @Test
    void unhandledRequestErrorsGetAReferenceAndRaiseAnAlert() {
        OpsAlertService ops = mock(OpsAlertService.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(ops);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/leads");
        var response = handler.handleUnexpected(failure(), request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).contains("quote reference").doesNotContain("secret");
        verify(ops).critical(anyString(), eq("POST /api/leads"), any(IllegalStateException.class));
    }
}
