package com.company.crm.customer.document;

import com.company.crm.common.enums.RoleType;
import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.company.crm.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerDocumentIntegrationTest extends PostgresIntegrationTest {

    private static final Path STORAGE_DIRECTORY = createStorageDirectory();

    @DynamicPropertySource
    static void documentStorage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.customer-documents-dir", () -> STORAGE_DIRECTORY.toString());
    }

    @AfterAll
    static void cleanDocumentStorage() throws IOException {
        if (Files.exists(STORAGE_DIRECTORY)) {
            try (var paths = Files.walk(STORAGE_DIRECTORY)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void customerCanUploadListDownloadAndDeleteDocumentWithinTenantAndOwnerScope() throws Exception {
        TestTenant tenant = testTenants.tenantWithAllRoles("Documents Co " + System.nanoTime());
        User owner = tenant.user(RoleType.SALES_EXECUTIVE);
        User otherOwner = testTenants.createUser(tenant.tenant(), RoleType.SALES_EXECUTIVE);
        String adminToken = tenant.token(RoleType.ADMIN);
        String ownerToken = tenant.token(RoleType.SALES_EXECUTIVE);
        long customerId = createCustomer(adminToken, owner.getId(), "Document owner");
        long otherCustomerId = createCustomer(adminToken, otherOwner.getId(), "Other customer");
        String documentsUrl = "/api/customers/" + customerId + "/documents";
        byte[] contents = "Customer visit notes".getBytes();

        MvcResult upload = mockMvc.perform(multipart(documentsUrl)
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", contents))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("notes.txt"))
                .andReturn();

        long documentId = ((Number) JsonPath.read(
                upload.getResponse().getContentAsString(), "$.data.id")).longValue();
        String documentUrl = documentsUrl + "/" + documentId;

        mockMvc.perform(get(documentsUrl).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].originalFilename").value("notes.txt"))
                .andExpect(jsonPath("$.data[0].fileSize").value(contents.length));

        mockMvc.perform(get(documentUrl + "/download").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().bytes(contents));

        mockMvc.perform(get(documentsUrl).header("Authorization", "Bearer "
                        + testTenants.tokenFor(otherOwner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(documentUrl).header("Authorization", "Bearer "
                        + testTenants.tokenFor(otherOwner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(documentsUrl).header("Authorization", "Bearer "
                        + testTenants.tenantWithAllRoles("Documents other tenant " + System.nanoTime())
                                .token(RoleType.ADMIN)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(documentUrl).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(documentsUrl).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get(documentUrl + "/download").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/customers/" + otherCustomerId + "/documents/" + documentId + "/download")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        try (var files = Files.list(STORAGE_DIRECTORY)) {
            assertThat(files.count()).isZero();
        }
    }

    @Test
    void uploadRejectsUnsupportedFileTypes() throws Exception {
        TestTenant tenant = testTenants.tenantWithAllRoles("Document validation " + System.nanoTime());
        long customerId = createCustomer(tenant.token(RoleType.ADMIN),
                tenant.user(RoleType.SALES_EXECUTIVE).getId(), "Validation customer");

        mockMvc.perform(multipart("/api/customers/" + customerId + "/documents")
                        .file(new MockMultipartFile("file", "payload.exe", "application/octet-stream",
                                "executable".getBytes()))
                        .header("Authorization", "Bearer " + tenant.token(RoleType.SALES_EXECUTIVE)))
                .andExpect(status().isBadRequest());
    }

    private long createCustomer(String token, Long ownerId, String companyName) throws Exception {
        String body = mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + companyName
                                + "\",\"industry\":\"trading\",\"ownerId\":" + ownerId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private static Path createStorageDirectory() {
        try {
            return Files.createTempDirectory("crm-customer-documents-test-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
