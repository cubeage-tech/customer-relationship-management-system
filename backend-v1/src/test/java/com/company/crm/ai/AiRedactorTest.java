package com.company.crm.ai;

import com.company.crm.ai.privacy.AiRedactor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** SEC-5: contact, identity and account details never reach the AI provider; dates and amounts do. */
class AiRedactorTest {

    @Test
    void removesContactAndIdentityDetails() {
        String out = AiRedactor.redact("Mail ravi.k@buyer.co.in or call +91 98765 43210 / 022-2345 6789. PAN ABCDE1234F, "
                + "GSTIN 27ABCDE1234F1Z5, Aadhaar 1234 5678 9012, card 4111-1111-1111-1111. "
                + "Link https://crm.example.com/q/abc?token=secret123");
        assertThat(out).doesNotContain("ravi.k@", "98765", "2345 6789", "ABCDE1234F", "27ABCDE", "9012", "4111", "secret123")
                .contains("[email]", "[number]", "[tax id]", "https://crm.example.com/q/abc");
    }

    @Test
    void keepsDatesAmountsAndShortNumbers() {
        String text = "Deal value 5000000.00, due 2026-10-27, 12 units at Rs 45,000, ticket 1234";
        assertThat(AiRedactor.redact(text)).isEqualTo(text);
    }
}
