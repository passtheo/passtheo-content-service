package com.passtheo.content;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.intuit.karate.junit5.Karate;
import com.passtheo.content.config.TestSchedulingConfig;
import com.passtheo.content.integration.strapi.StrapiClient;
import com.passtheo.content.integration.strapi.dto.StrapiAchievementDefDto;
import com.passtheo.content.integration.strapi.dto.StrapiCountryDto;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductTypeDto;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.content.integration.strapi.dto.StrapiRelationDto;
import com.passtheo.content.integration.strapi.dto.StrapiTopicDto;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Karate acceptance test runner with full Spring Boot context.
 * Connects to Docker Compose infrastructure (PostgreSQL, Redis) via direct localhost URLs.
 * StrapiClient is mocked. Flyway clean+migrate runs before each test suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("acceptance")
@Import({TestSchedulingConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("acceptance")
class KarateRunner {

    private static final WireMockServer USER_SERVICE_MOCK = new WireMockServer(
            WireMockConfiguration.wireMockConfig()
                    .dynamicPort()
                    .usingFilesUnderDirectory("../contracts/user-service")
    );

    private static final WireMockServer SUBSCRIPTION_SERVICE_MOCK = new WireMockServer(
            WireMockConfiguration.wireMockConfig()
                    .dynamicPort()
                    .usingFilesUnderDirectory("../contracts/subscription-service")
    );

    @DynamicPropertySource
    static void configureExternalServiceUrls(DynamicPropertyRegistry registry) {
        USER_SERVICE_MOCK.start();
        SUBSCRIPTION_SERVICE_MOCK.start();
        registry.add("passtheo.user-service.base-url", USER_SERVICE_MOCK::baseUrl);
        registry.add("passtheo.subscription-service.base-url", SUBSCRIPTION_SERVICE_MOCK::baseUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private StrapiClient strapiClient;

    @Autowired
    @Qualifier("actualDataSource")
    private DataSource rawDataSource;

    @AfterAll
    void stopWireMock() {
        if (USER_SERVICE_MOCK.isRunning()) {
            USER_SERVICE_MOCK.stop();
        }
        if (SUBSCRIPTION_SERVICE_MOCK.isRunning()) {
            SUBSCRIPTION_SERVICE_MOCK.stop();
        }
    }

    @BeforeAll
    void setupAll() {
        runFlywayMigrations();
        seedRedisEntitlements();
        configureStrapiMocks();
        configureUserServiceMocks();
    }

    /**
     * Registers priority-1 WireMock stubs per user for deterministic
     * {@code GET /internal/users/{id}/profile} responses.
     *
     * <p>Why this exists: the file-based contract stubs in
     * {@code contracts/user-service/mappings/} share a URL pattern that
     * matches <em>any</em> UUID, so without explicit per-user priority
     * the 200/404 stubs win non-deterministically — which quietly changes
     * {@code computePlan}'s fallback behaviour between runs and breaks
     * tests that assert a specific {@code totalDays} or {@code examDate}.
     *
     * <p>Two users get explicit behaviour:
     * <ul>
     *   <li>{@code 33333333-...} (paidUser) → 404: the default-30-days test
     *       and other paid-user scenarios were written against "no profile"
     *       fallback; pin that behaviour here.
     *   <li>{@code 44444444-...} (drift test only) → verified profile with
     *       {@code examDate=2026-05-15}: required by the reconcile-on-read
     *       drift scenario.
     * </ul>
     */
    private void configureUserServiceMocks() {
        USER_SERVICE_MOCK.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/users/33333333-3333-3333-3333-333333333333/profile"))
                .atPriority(1)
                .willReturn(WireMock.aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("""
                                {
                                  "type": "https://api.passtheo.nl/errors/user-not-found",
                                  "title": "User not found",
                                  "status": 404,
                                  "detail": "User not found: 33333333-3333-3333-3333-333333333333"
                                }
                                """)));

        String driftUserVerifiedBody = """
                {
                  "success": true,
                  "data": {
                    "keycloakUserId": "44444444-4444-4444-4444-444444444444",
                    "email": "drift-test@example.com",
                    "emailVerified": true,
                    "emailVerifiedAt": "2026-03-01T10:00:00Z",
                    "examDate": "2026-05-15",
                    "tenantId": "11111111-1111-1111-1111-111111111111"
                  }
                }
                """;
        USER_SERVICE_MOCK.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/users/44444444-4444-4444-4444-444444444444/profile"))
                .atPriority(1)
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(driftUserVerifiedBody)));
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

    @Karate.Test
    Karate testAll() {
        System.setProperty("karate.baseUrl", "http://localhost:" + port);
        return Karate.run("classpath:karate").relativeTo(getClass());
    }

    // ─── Redis ───

    private void seedRedisEntitlements() {
        String tenantId = "11111111-1111-1111-1111-111111111111";
        String paidUserId = "33333333-3333-3333-3333-333333333333";
        redisTemplate.opsForValue().set(
                "access:" + tenantId + ":" + paidUserId,
                "{\"paid\":true,\"planCode\":\"MONTH_1\",\"allowedDomains\":null}");
    }

    // ─── Strapi Mocks ───

    private void configureStrapiMocks() {
        when(strapiClient.getCountries(anyString()))
                .thenReturn(buildCountries());
        when(strapiClient.getProductTypes(anyString(), anyString()))
                .thenReturn(buildProductTypes());
        when(strapiClient.getProducts(anyString(), anyString()))
                .thenReturn(buildProducts());
        when(strapiClient.getDomains(anyString(), anyString()))
                .thenReturn(buildDomains());
        when(strapiClient.getTopics(anyString(), anyString()))
                .thenReturn(buildTopics());
        when(strapiClient.getQuestionsByTopic(eq("gebodsborden"), anyString()))
                .thenReturn(buildQuestions("gebodsborden", 15));
        when(strapiClient.getQuestionsByTopic(eq("verbodsborden"), anyString()))
                .thenReturn(buildQuestions("verbodsborden", 15));
        when(strapiClient.getQuestionsByTopic(anyString(), anyString()))
                .thenReturn(buildQuestions("topic-generic", 10));
        when(strapiClient.getQuestionsByDomain(eq("verkeersborden"), anyString()))
                .thenReturn(buildQuestions("verkeersborden", 20));
        when(strapiClient.getQuestionsByDomain(eq("snelheid"), anyString()))
                .thenReturn(buildQuestions("snelheid", 15));
        when(strapiClient.getQuestionsByDomain(eq("voorrang"), anyString()))
                .thenReturn(buildQuestions("voorrang", 15));
        when(strapiClient.getQuestionsByDomain(eq("rijbaan"), anyString()))
                .thenReturn(buildQuestions("rijbaan", 10));
        when(strapiClient.getQuestionsByDomain(eq("gevaarherkenning"), anyString()))
                .thenReturn(buildQuestions("gevaarherkenning", 10));
        when(strapiClient.getQuestionsByDomain(eq("milieu"), anyString()))
                .thenReturn(buildQuestions("milieu", 10));
        when(strapiClient.getQuestionsByDomain(anyString(), anyString()))
                .thenReturn(buildQuestions("domain-generic", 10));
        when(strapiClient.getQuestionsByProduct(anyString(), anyString()))
                .thenReturn(buildAllProductQuestions());
        when(strapiClient.getQuestion(anyString(), anyString()))
                .thenAnswer(inv -> {
                    String questionId = inv.getArgument(0);
                    // If questionId is already a documentId (starts with "doc-"), use it as-is
                    // Otherwise, assume it's an old-style ID and convert it
                    String documentId = questionId.startsWith("doc-") ? questionId : "doc-" + questionId;
                    return buildSingleQuestion(questionId, documentId, "verkeersborden");
                });
        when(strapiClient.getExamConfig(anyString(), anyString()))
                .thenReturn(new StrapiExamConfigDto(0, null, "Theory Exam", "Test description", List.of("Rule 1"), 50, 30, 44, null, null, true, false, null, null, false));
        when(strapiClient.getAchievements(anyString()))
                .thenReturn(buildAchievements());
        when(strapiClient.getRoadSigns(anyString(), anyString(), any()))
                .thenReturn(List.of());
        when(strapiClient.getLessons(anyString(), anyString()))
                .thenReturn(List.of());
    }

    // ─── Test Data Builders ───

    private List<StrapiCountryDto> buildCountries() {
        return List.of(new StrapiCountryDto(1, "doc-nl", "Nederland", "NL", null, "nl", List.of("nl", "en"), true, 1));
    }

    private List<StrapiProductTypeDto> buildProductTypes() {
        return List.of(new StrapiProductTypeDto(
                1, null, "CBR Rijbewijzen", "cbr", "Dutch driving authority",
                null, null, "CBR", "https://cbr.nl", true, 1, 3));
    }

    private List<StrapiProductDto> buildProducts() {
        return List.of(new StrapiProductDto(
                1, null, "Auto B", "auto-b", "B", "Auto rijbewijs theorie",
                null, null, true, false, 1,
                new StrapiExamConfigDto(0, null, "Theory Exam", "Test description", List.of("Rule 1"), 50, 30, 44, null, null, true, false, null, null, false),
                8, 500));
    }

    private List<StrapiDomainDto> buildDomains() {
        return List.of(
                new StrapiDomainDto(1, null, "Verkeersborden", "verkeersborden", "verkeersborden",
                        "Kennis van verkeersborden", null, "#E63946", 50, true, true, 1),
                new StrapiDomainDto(2, null, "Snelheid", "snelheid", "snelheid",
                        "Snelheidsregels", null, "#457B9D", 30, true, false, 2),
                new StrapiDomainDto(3, null, "Voorrang", "voorrang", "voorrang",
                        "Voorrangsregels", null, "#1D3557", 40, true, false, 3),
                new StrapiDomainDto(4, null, "Rijbaan", "rijbaan", "rijbaan",
                        "Rijbaangebruik", null, "#2A9D8F", 35, true, false, 4),
                new StrapiDomainDto(5, null, "Gevaarherkenning", "gevaarherkenning", "gevaarherkenning",
                        "Gevaarherkenning", null, "#F4A261", 30, true, false, 5),
                new StrapiDomainDto(6, null, "Milieu", "milieu", "milieu",
                        "Milieu en economisch rijden", null, "#2ECC71", 20, true, false, 6)
        );
    }

    private List<StrapiTopicDto> buildTopics() {
        return List.of(
                new StrapiTopicDto(1, null, "Gebodsborden", "gebodsborden", "gebodsborden",
                        "Gebodsborden herkennen", "medium", 15, true, 1),
                new StrapiTopicDto(2, null, "Verbodsborden", "verbodsborden", "verbodsborden",
                        "Verbodsborden herkennen", "hard", 15, true, 2)
        );
    }

    private List<StrapiQuestionDto> buildAllProductQuestions() {
        List<StrapiQuestionDto> all = new java.util.ArrayList<>();
        all.addAll(buildQuestions("verkeersborden", 20));
        all.addAll(buildQuestions("snelheid", 15));
        all.addAll(buildQuestions("voorrang", 15));
        all.addAll(buildQuestions("rijbaan", 10));
        all.addAll(buildQuestions("gevaarherkenning", 10));
        all.addAll(buildQuestions("milieu", 10));
        return all;
    }

    private List<StrapiQuestionDto> buildQuestions(String domainCode, int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> buildSingleQuestion("q-" + domainCode + "-" + i, "doc-" + domainCode + "-" + i, domainCode))
                .toList();
    }

    private StrapiQuestionDto buildSingleQuestion(String id, String documentId, String domainCode) {
        return new StrapiQuestionDto(
                1,
                documentId,
                "Wat betekent dit verkeersbord? (vraag " + documentId + ")",
                "multiple_choice",
                "medium",
                null, null, null, null, 1, true, false, null, null,
                List.of(
                        new StrapiQuestionDto.AnswerOptionDto(1, "Verboden in te rijden", null, true, 1),
                        new StrapiQuestionDto.AnswerOptionDto(2, "Gevaarlijke bocht", null, false, 2),
                        new StrapiQuestionDto.AnswerOptionDto(3, "Doorrijden verboden", null, false, 3),
                        new StrapiQuestionDto.AnswerOptionDto(4, "Pas op voor tegenliggers", null, false, 4)
                ),
                new StrapiQuestionDto.ExplanationDto(
                        "Een verbodsbord met rode rand en kruis betekent verboden in te rijden.",
                        null, "Kijk altijd naar de rand en symbolen op het bord.",
                        null, "RVV 1990 Art. 62"),
                null, null, null, null,
                new StrapiRelationDto(0, null, domainCode, null),
                new StrapiRelationDto(0, null, "gebodsborden", null),
                null
        );
    }

    private List<StrapiAchievementDefDto> buildAchievements() {
        return java.util.Arrays.asList(
                new StrapiAchievementDefDto(1, null, "Eerste Stap", "first_question",
                        "Beantwoord je eerste vraag", "\uD83C\uDFAF", "\uD83D\uDD12",
                        "QUESTIONS_ANSWERED", 1, 10, true, 1, "auto-b"),
                new StrapiAchievementDefDto(2, null, "Beginnersluk", "ten_questions",
                        "Beantwoord 10 vragen", "\u2B50", "\uD83D\uDD12",
                        "QUESTIONS_ANSWERED", 10, 50, true, 2, null),
                new StrapiAchievementDefDto(3, null, "Weekkampioen", "7_day_streak",
                        "7 dagen op rij gestudeerd", "\uD83D\uDD25", "\uD83D\uDD12",
                        "STREAK_DAYS", 7, 100, true, 3, null)
        );
    }
}
