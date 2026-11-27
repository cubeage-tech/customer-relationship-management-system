package com.company.crm.customer;

import com.company.crm.customer.service.DuplicateCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** VAL-3: company names compare without case, punctuation or legal suffixes. */
class DuplicateNameTest {

    @Test
    void legalSuffixesAndPunctuationDontMakeADifferentCompany() {
        assertThat(normalize("Acme Industries Pvt. Ltd.")).isEqualTo("acmeindustries");
        assertThat(normalize("ACME INDUSTRIES PRIVATE LIMITED")).isEqualTo("acmeindustries");
        assertThat(normalize("The Acme-Industries Co.")).isEqualTo("acmeindustries");
        assertThat(normalize("Coca Cola")).isEqualTo("cocacola"); // "co" only as a whole word
        assertThat(normalize(null)).isEmpty();
    }

    private static String normalize(String name) {
        return ReflectionTestUtils.invokeMethod(DuplicateCheckService.class, "normalize", name);
    }
}
