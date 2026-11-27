package com.company.crm.customer;

import com.company.crm.customer.repository.CustomerDocumentRepository;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FR-1.5a upload + FR-1.5b view/download/delete of documents on a customer record. */
class CustomerDocumentIntegrationTest extends PostgresIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\n%test contract\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired private CustomerDocumentRepository documentRepository;

    @Value("${app.documents.storage-path}")
    private String storagePath;

    private TestTenant tenant;
    private String adminToken;
    private long customerId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = testTenants.tenantWithAllRoles("Docs Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
        customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"ABC Engineering\",\"industry\":\"automobile\"}"));
    }

    @Test
    void uploadListViewDownloadDelete() throws Exception {
        long docId = idOf(upload(adminToken, "Contract 2026.pdf", PDF)
                .andExpect(jsonPath("$.data.fileName").value("Contract 2026.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.sizeBytes").value(PDF.length))
                .andExpect(jsonPath("$.data.uploadedByName").isNotEmpty()));
        Path stored = Path.of(storagePath).resolve(documentRepository.findById(docId).orElseThrow().getStorageKey());
        assertThat(stored).exists();

        send(get("/api/customers/" + customerId + "/documents"), adminToken)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(docId));

        // Download returns the same bytes as an attachment; inline=true lets a PDF open in the browser.
        byte[] downloaded = send(get("/api/customers/" + customerId + "/documents/" + docId + "/download"), adminToken)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);
        send(get("/api/customers/" + customerId + "/documents/" + docId + "/download").param("inline", "true"), adminToken)
                .andExpect(header().string("Content-Disposition", containsString("inline")));

        send(delete("/api/customers/" + customerId + "/documents/" + docId), adminToken).andExpect(status().isOk());
        send(get("/api/customers/" + customerId + "/documents"), adminToken).andExpect(jsonPath("$.data.length()").value(0));
        assertThat(Files.exists(stored)).isFalse();
        send(get("/api/customers/" + customerId + "/documents/" + docId + "/download"), adminToken).andExpect(status().isNotFound());
    }

    @Test
    void rejectsDisallowedOrSpoofedOrOversizedFiles() throws Exception {
        upload(adminToken, "setup.exe", new byte[]{0x4D, 0x5A, 0x00}).andExpect(status().isBadRequest());
        // Right extension, wrong content (an executable renamed to .pdf).
        upload(adminToken, "invoice.pdf", new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00}).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("PDF, DOCX, XLSX, JPG and PNG")));
        upload(adminToken, "empty.pdf", new byte[0]).andExpect(status().isBadRequest());
        byte[] big = new byte[(int) (25L * 1024 * 1024) + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        upload(adminToken, "huge.pdf", big).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("25 MB")));
        // A path in the name is reduced to the base name.
        upload(adminToken, "../../etc/notes.png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2})
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("notes.png"));
    }

    @Test
    void accessFollowsCustomerRolesAndScope() throws Exception {
        long docId = idOf(upload(adminToken, "po.pdf", PDF));

        // Finance can view and download but not upload or delete.
        send(get("/api/customers/" + customerId + "/documents"), tenant.token(FINANCE_APPROVER)).andExpect(status().isOk());
        send(get("/api/customers/" + customerId + "/documents/" + docId + "/download"), tenant.token(FINANCE_APPROVER)).andExpect(status().isOk());
        upload(tenant.token(FINANCE_APPROVER), "x.pdf", PDF).andExpect(status().isForbidden());
        send(delete("/api/customers/" + customerId + "/documents/" + docId), tenant.token(FINANCE_APPROVER)).andExpect(status().isForbidden());

        // A sales executive who doesn't own the customer can't see its documents.
        send(get("/api/customers/" + customerId + "/documents"), tenant.token(SALES_EXECUTIVE)).andExpect(status().isForbidden());
        upload(tenant.token(SALES_EXECUTIVE), "x.pdf", PDF).andExpect(status().isForbidden());

        // Another tenant can't reach the customer or the document.
        TestTenant other = testTenants.tenantWithAllRoles("Other Docs " + System.nanoTime());
        send(get("/api/customers/" + customerId + "/documents/" + docId + "/download"), other.token(ADMIN)).andExpect(status().isNotFound());
    }

    private ResultActions upload(String token, String name, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "application/octet-stream", bytes);
        return mockMvc.perform(multipart("/api/customers/" + customerId + "/documents").file(file).header("Authorization", "Bearer " + token));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
