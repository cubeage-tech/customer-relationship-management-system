package com.company.crm.branding;

import com.company.crm.support.PostgresIntegrationTest;
import com.company.crm.support.TestTenants.TestTenant;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static com.company.crm.common.enums.RoleType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AC-4.2: the quotation PDF carries the line items, totals and the tenant's branding (logo + details). */
class QuotationBrandingIntegrationTest extends PostgresIntegrationTest {

    private TestTenant tenant;
    private String adminToken;

    @BeforeEach
    void setUp() {
        tenant = testTenants.tenantWithAllRoles("Brand Co " + System.nanoTime());
        adminToken = tenant.token(ADMIN);
    }

    @Test
    void adminSetsBrandingAndThePdfUsesIt() throws Exception {
        send(put("/api/branding"), adminToken, "{\"address\":\"12 MG Road\\nPune 411001\",\"phone\":\"+91 20 1234 5678\","
                + "\"email\":\"sales@brand.test\",\"taxId\":\"27ABCDE1234F1Z5\",\"color\":\"#4f46e5\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxId").value("27ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.data.hasLogo").value(false));
        mockMvc.perform(multipart("/api/branding/logo").file(new MockMultipartFile("file", "logo.png", "image/png", png()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasLogo").value(true));

        // Everyone in the tenant can read it (the Settings page shows it); the logo streams as an image.
        send(get("/api/branding"), tenant.token(FINANCE_APPROVER), null).andExpect(jsonPath("$.data.address").value("12 MG Road\nPune 411001"));
        send(get("/api/branding/logo"), tenant.token(SALES_EXECUTIVE), null)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        byte[] pdf = quotationPdf();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains(tenant.tenant().getCompanyName(), "12 MG Road", "Pune 411001",
                    "sales@brand.test", "GSTIN: 27ABCDE1234F1Z5", "Crane", "Grand Total");
            assertThat(document.getPage(0).getResources().getXObjectNames()).isNotEmpty(); // the logo image
        }

        send(delete("/api/branding/logo"), adminToken, null).andExpect(jsonPath("$.data.hasLogo").value(false));
        send(get("/api/branding/logo"), adminToken, null).andExpect(status().isNotFound());
        try (PDDocument document = Loader.loadPDF(quotationPdf())) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).isEmpty();
        }
    }

    @Test
    void brandingIsValidatedAdminOnlyAndPerTenant() throws Exception {
        send(put("/api/branding"), tenant.token(SALES_MANAGER), "{\"phone\":\"1\"}").andExpect(status().isForbidden());
        send(put("/api/branding"), adminToken, "{\"color\":\"blue\"}").andExpect(status().isBadRequest());
        send(put("/api/branding"), adminToken, "{\"email\":\"not-an-email\"}").andExpect(status().isBadRequest());

        // A renamed text file, or bytes that only look like a PNG, are refused.
        uploadLogo("logo.png", "not an image".getBytes()).andExpect(status().isBadRequest());
        byte[] fakePng = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        uploadLogo("logo.png", fakePng).andExpect(status().isBadRequest());
        uploadLogo("logo.png", new byte[1024 * 1024 + 1]).andExpect(status().isBadRequest());

        send(put("/api/branding"), adminToken, "{\"taxId\":\"GST-1\"}").andExpect(status().isOk());
        TestTenant other = testTenants.tenantWithAllRoles("Other Brand " + System.nanoTime());
        send(get("/api/branding"), other.token(ADMIN), null).andExpect(jsonPath("$.data.taxId").doesNotExist());
    }

    private byte[] quotationPdf() throws Exception {
        long customerId = idOf(send(post("/api/customers"), adminToken, "{\"companyName\":\"Buyer\",\"industry\":\"trading\"}"));
        long quotationId = idOf(send(post("/api/quotations"), adminToken, "{\"customerId\":" + customerId
                + ",\"lineItems\":[{\"productName\":\"Crane\",\"quantity\":1,\"unitPrice\":1000,\"discountPercent\":0}]}"));
        return send(get("/api/quotations/" + quotationId + "/pdf"), adminToken, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private ResultActions uploadLogo(String name, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/branding/logo").file(new MockMultipartFile("file", name, "image/png", bytes))
                .header("Authorization", "Bearer " + adminToken));
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
