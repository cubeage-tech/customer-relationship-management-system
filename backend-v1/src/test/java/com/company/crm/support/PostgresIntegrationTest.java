package com.company.crm.support;

import com.company.crm.common.mail.MailService;
import com.company.crm.communication.integration.TwilioClient;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Base for HTTP-level integration tests: the full app against one real PostgreSQL shared by
 * every subclass (started once per test JVM), with outgoing mail mocked. Subclasses with the
 * same configuration also share one Spring context. Each test creates its own tenants via
 * {@link TestTenants}, so tests never depend on each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @Autowired protected MockMvc mockMvc;
    @Autowired protected TestTenants testTenants;

    @MockitoBean protected MailService mailService;

    /** WhatsApp/SMS provider — never called for real in tests (disabled unless a test stubs it). */
    @MockitoBean protected TwilioClient twilioClient;

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // JVM is exiting anyway
                }
            }));
            return postgres;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        // FR-1.5: uploaded test documents go to a temp folder, never into the repository.
        registry.add("app.documents.storage-path",
                () -> java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "crm-test-documents").toString());
    }
}
