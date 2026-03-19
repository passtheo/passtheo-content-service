package com.passtheo.content;

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
import com.passtheo.content.integration.strapi.dto.StrapiTopicDto;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
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
class KarateRunner {

    @LocalServerPort
    private int port;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private StrapiClient strapiClient;

    @Autowired
    @Qualifier("actualDataSource")
    private DataSource rawDataSource;

    @BeforeAll
    void setupAll() {
        runFlywayMigrations();
        seedRedisEntitlements();
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
                .thenAnswer(inv -> buildSingleQuestion(inv.getArgument(0), "verkeersborden"));
        when(strapiClient.getExamConfig(anyString()))
                .thenReturn(new StrapiExamConfigDto(50, 30, 44, null, null, true, false, false));
        when(strapiClient.getAchievements())
                .thenReturn(buildAchievements());
        when(strapiClient.getRoadSigns(anyString(), anyString(), any()))
                .thenReturn(List.of());
        when(strapiClient.getLessons(anyString(), anyString()))
                .thenReturn(List.of());
    }

    // ─── Test Data Builders ───

    private List<StrapiCountryDto> buildCountries() {
        return List.of(new StrapiCountryDto("Nederland", "NL", null, "nl", List.of("nl", "en"), true, 1));
    }

    private List<StrapiProductTypeDto> buildProductTypes() {
        return List.of(new StrapiProductTypeDto(
                "CBR Rijbewijzen", "cbr", "Dutch driving authority",
                null, null, "CBR", "https://cbr.nl", true, 1, 3));
    }

    private List<StrapiProductDto> buildProducts() {
        return List.of(new StrapiProductDto(
                "Auto B", "auto-b", "B", "Auto rijbewijs theorie",
                null, null, true, false, 1,
                new StrapiExamConfigDto(50, 30, 44, null, null, true, false, false),
                8, 500));
    }

    private List<StrapiDomainDto> buildDomains() {
        return List.of(
                new StrapiDomainDto("Verkeersborden", "verkeersborden", "verkeersborden",
                        "Kennis van verkeersborden", null, "#E63946", 50, true, true, 1),
                new StrapiDomainDto("Snelheid", "snelheid", "snelheid",
                        "Snelheidsregels", null, "#457B9D", 30, true, false, 2),
                new StrapiDomainDto("Voorrang", "voorrang", "voorrang",
                        "Voorrangsregels", null, "#1D3557", 40, true, false, 3),
                new StrapiDomainDto("Rijbaan", "rijbaan", "rijbaan",
                        "Rijbaangebruik", null, "#2A9D8F", 35, true, false, 4),
                new StrapiDomainDto("Gevaarherkenning", "gevaarherkenning", "gevaarherkenning",
                        "Gevaarherkenning", null, "#F4A261", 30, true, false, 5),
                new StrapiDomainDto("Milieu", "milieu", "milieu",
                        "Milieu en economisch rijden", null, "#2ECC71", 20, true, false, 6)
        );
    }

    private List<StrapiTopicDto> buildTopics() {
        return List.of(
                new StrapiTopicDto("Gebodsborden", "gebodsborden", "gebodsborden",
                        "Gebodsborden herkennen", "medium", 15, true, 1),
                new StrapiTopicDto("Verbodsborden", "verbodsborden", "verbodsborden",
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
                .mapToObj(i -> buildSingleQuestion("q-" + domainCode + "-" + i, domainCode))
                .toList();
    }

    private StrapiQuestionDto buildSingleQuestion(String id, String domainCode) {
        return new StrapiQuestionDto(
                id,
                "Wat betekent dit verkeersbord? (vraag " + id + ")",
                "multiple_choice",
                "medium",
                null, null, null, null, 1,
                List.of(
                        new StrapiQuestionDto.AnswerOptionDto("opt-a", "Verboden in te rijden", null, true, 1),
                        new StrapiQuestionDto.AnswerOptionDto("opt-b", "Gevaarlijke bocht", null, false, 2),
                        new StrapiQuestionDto.AnswerOptionDto("opt-c", "Doorrijden verboden", null, false, 3),
                        new StrapiQuestionDto.AnswerOptionDto("opt-d", "Pas op voor tegenliggers", null, false, 4)
                ),
                new StrapiQuestionDto.ExplanationDto(
                        "Een verbodsbord met rode rand en kruis betekent verboden in te rijden.",
                        null, "Kijk altijd naar de rand en symbolen op het bord.",
                        null, "RVV 1990 Art. 62"),
                null, null,
                domainCode, "gebodsborden"
        );
    }

    private List<StrapiAchievementDefDto> buildAchievements() {
        return List.of(
                new StrapiAchievementDefDto("Eerste Stap", "first_question",
                        "Beantwoord je eerste vraag", "\uD83C\uDFAF", "\uD83D\uDD12",
                        "questions_answered", 1, 10, true, 1),
                new StrapiAchievementDefDto("Beginnersluk", "ten_questions",
                        "Beantwoord 10 vragen", "\u2B50", "\uD83D\uDD12",
                        "questions_answered", 10, 50, true, 2),
                new StrapiAchievementDefDto("Weekkampioen", "7_day_streak",
                        "7 dagen op rij gestudeerd", "\uD83D\uDD25", "\uD83D\uDD12",
                        "study_days_streak", 7, 100, true, 3)
        );
    }
}


