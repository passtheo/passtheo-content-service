package com.passtheo.content;

import com.intuit.karate.junit5.Karate;
import com.passtheo.content.config.TestSchedulingConfig;
import com.passtheo.content.integration.strapi.StrapiClient;
import com.passtheo.content.integration.strapi.dto.StrapiCountryDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductTypeDto;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Karate contract verification runner for passtheo-content-service.
 * Verifies the provider's real implementation matches the contracts
 * in {@code ../contracts/passtheo-content-service/mappings/}.
 *
 * <p>StrapiClient is mocked with a fixed fixture so {@code /internal/products/catalog}
 * returns a deterministic shape. The feature under
 * {@code src/test/resources/karate/contract/} asserts the response shape matches
 * the stubs consumers rely on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("acceptance")
@Import({TestSchedulingConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("contract")
class KarateContractRunner {

    @LocalServerPort
    private int port;

    @MockitoBean
    private StrapiClient strapiClient;

    @Autowired
    @Qualifier("actualDataSource")
    private DataSource rawDataSource;

    @BeforeAll
    void setupAll() {
        runFlywayMigrations();
        configureStrapiMocks();
    }

    private void runFlywayMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(rawDataSource)
                .schemas("content_service")
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    private void configureStrapiMocks() {
        when(strapiClient.getCountries(anyString())).thenReturn(List.of(
                new StrapiCountryDto(1, null, "Nederland", "NL", null, "nl", List.of("nl"), true, 1)));
        when(strapiClient.getProductTypes(anyString(), anyString())).thenReturn(List.of(
                new StrapiProductTypeDto(1, null, "CBR", "cbr", null, null, null, null, null, true, 1, 2)));
        when(strapiClient.getProducts(anyString(), anyString())).thenReturn(List.of(
                new StrapiProductDto(1, null, "Auto B", "auto-b", "B", null, null, null, true, false, 1, null, 0, 0),
                new StrapiProductDto(2, null, "Motor A", "motor-a", "A", null, null, null, true, false, 2, null, 0, 0)));
    }

    @Karate.Test
    Karate verifyContracts() {
        System.setProperty("karate.baseUrl", "http://localhost:" + port);
        return Karate.run("classpath:karate/contract").relativeTo(getClass());
    }
}
