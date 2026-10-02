package com.company.crm;

import com.company.crm.plan.dto.response.PlanPriceResDto;
import com.company.crm.plan.repository.PlanRepository;
import com.company.crm.plan.service.PlanPriceService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application against a throwaway local PostgreSQL: runs every Flyway
 * migration and then Hibernate's ddl-auto=validate, so entity/schema drift fails the build.
 */
@SpringBootTest
class SchemaMigrationTest {

    private static EmbeddedPostgres postgres;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanPriceService planPriceService;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        try {
            postgres = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stop() throws IOException {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void migrationsApplyAndEntitiesMatchSchema() {
        assertThat(planRepository.findByCode("starter")).get()
                .satisfies(plan -> assertThat(plan.getMaxCustomers()).isEqualTo(500));
        assertThat(planRepository.findByCode("enterprise")).get()
                .satisfies(plan -> assertThat(plan.getMaxUsers()).isNull());

        // plan_prices were re-pointed to plans(id) and still expose the plan code.
        assertThat(planPriceService.listPrices())
                .extracting(PlanPriceResDto::getPlan)
                .containsExactlyInAnyOrder("starter", "business", "enterprise");
    }
}
