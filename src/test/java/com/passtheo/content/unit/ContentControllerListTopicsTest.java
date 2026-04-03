package com.passtheo.content.unit;

import com.passtheo.content.controller.ContentController;
import com.passtheo.content.dto.response.TopicWithProgressDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiTopicDto;
import com.passtheo.content.repository.DomainProgressRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.service.EntitlementChecker;
import com.passtheo.content.service.OnboardingCatalogService;
import com.passtheo.shared.core.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentControllerListTopicsTest {

    @Mock private StrapiContentCache strapiContentCache;
    @Mock private DomainProgressRepository domainProgressRepository;
    @Mock private QuestionProgressRepository questionProgressRepository;
    @Mock private EntitlementChecker entitlementChecker;
    @Mock private OnboardingCatalogService onboardingCatalogService;

    private ContentController controller;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PRODUCT = "auto-b";
    private static final String DOMAIN = "verkeersborden";
    private static final String LOCALE = "nl";

    @BeforeEach
    void setUp() {
        controller = new ContentController(
                strapiContentCache, domainProgressRepository,
                questionProgressRepository, entitlementChecker, onboardingCatalogService);
    }

    @Test
    void listTopics_noProgress_returnsZeroOverlay() {
        when(strapiContentCache.getTopics(DOMAIN, LOCALE)).thenReturn(List.of(
                new StrapiTopicDto(1, "doc1", "Prohibition Signs", "verbodsborden", "verbodsborden", "", "easy", 10, true, 1)));
        when(strapiContentCache.getQuestionCountByTopic("verbodsborden", LOCALE)).thenReturn(10);
        when(questionProgressRepository.aggregateByTopic(USER_ID, PRODUCT, DOMAIN)).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<TopicWithProgressDto>>> response =
                controller.listTopics("NL", "cbr", PRODUCT, DOMAIN, TENANT_ID, USER_ID, LOCALE);

        List<TopicWithProgressDto> topics = response.getBody().getData();
        assertEquals(1, topics.size());
        TopicWithProgressDto.TopicProgressOverlay progress = topics.getFirst().progress();
        assertNotNull(progress);
        assertEquals(0.0, progress.coveragePercent());
        assertEquals(0.0, progress.accuracyPercent());
        assertEquals(0, progress.masteredCount());
    }

    @Test
    void listTopics_withProgress_computesAccuracyAndCoverage() {
        when(strapiContentCache.getTopics(DOMAIN, LOCALE)).thenReturn(List.of(
                new StrapiTopicDto(1, "doc1", "Prohibition Signs", "verbodsborden", "verbodsborden", "", "easy", 10, true, 1)));
        when(strapiContentCache.getQuestionCountByTopic("verbodsborden", LOCALE)).thenReturn(20);

        QuestionProgressRepository.TopicMasteryProjection projection = mock(QuestionProgressRepository.TopicMasteryProjection.class);
        when(projection.getTopicCode()).thenReturn("verbodsborden");
        when(projection.getAttemptedCount()).thenReturn(10L);
        when(projection.getCorrectCount()).thenReturn(7L);
        when(projection.getTotalAttempts()).thenReturn(14L);
        when(projection.getMasteredCount()).thenReturn(3L);
        when(questionProgressRepository.aggregateByTopic(USER_ID, PRODUCT, DOMAIN)).thenReturn(List.of(projection));

        ResponseEntity<ApiResponse<List<TopicWithProgressDto>>> response =
                controller.listTopics("NL", "cbr", PRODUCT, DOMAIN, TENANT_ID, USER_ID, LOCALE);

        TopicWithProgressDto.TopicProgressOverlay progress = response.getBody().getData().getFirst().progress();
        assertEquals(50.0, progress.coveragePercent(), 0.01);  // 10/20 * 100
        assertEquals(50.0, progress.accuracyPercent(), 0.01);  // 7/14 * 100
        assertEquals(3, progress.masteredCount());
    }

    @Test
    void listTopics_zeroQuestionCount_noDivisionByZero() {
        when(strapiContentCache.getTopics(DOMAIN, LOCALE)).thenReturn(List.of(
                new StrapiTopicDto(1, "doc1", "Empty Topic", "empty", "empty", "", "easy", 0, true, 1)));
        when(strapiContentCache.getQuestionCountByTopic("empty", LOCALE)).thenReturn(0);

        QuestionProgressRepository.TopicMasteryProjection projection = mock(QuestionProgressRepository.TopicMasteryProjection.class);
        when(projection.getTopicCode()).thenReturn("empty");
        when(projection.getTotalAttempts()).thenReturn(0L);
        when(projection.getMasteredCount()).thenReturn(0L);
        when(questionProgressRepository.aggregateByTopic(USER_ID, PRODUCT, DOMAIN)).thenReturn(List.of(projection));

        ResponseEntity<ApiResponse<List<TopicWithProgressDto>>> response =
                controller.listTopics("NL", "cbr", PRODUCT, DOMAIN, TENANT_ID, USER_ID, LOCALE);

        TopicWithProgressDto.TopicProgressOverlay progress = response.getBody().getData().getFirst().progress();
        assertEquals(0.0, progress.coveragePercent());
        assertEquals(0.0, progress.accuracyPercent());
        assertEquals(0, progress.masteredCount());
    }

    @Test
    void listTopics_multipleTopics_mapsProgressCorrectly() {
        when(strapiContentCache.getTopics(DOMAIN, LOCALE)).thenReturn(List.of(
                new StrapiTopicDto(1, "doc1", "Topic A", "topic-a", "topic-a", "", "easy", 10, true, 1),
                new StrapiTopicDto(2, "doc2", "Topic B", "topic-b", "topic-b", "", "medium", 5, true, 2)));
        when(strapiContentCache.getQuestionCountByTopic("topic-a", LOCALE)).thenReturn(10);
        when(strapiContentCache.getQuestionCountByTopic("topic-b", LOCALE)).thenReturn(5);

        QuestionProgressRepository.TopicMasteryProjection projA = mock(QuestionProgressRepository.TopicMasteryProjection.class);
        when(projA.getTopicCode()).thenReturn("topic-a");
        when(projA.getAttemptedCount()).thenReturn(5L);
        when(projA.getCorrectCount()).thenReturn(4L);
        when(projA.getTotalAttempts()).thenReturn(5L);
        when(projA.getMasteredCount()).thenReturn(2L);
        when(questionProgressRepository.aggregateByTopic(USER_ID, PRODUCT, DOMAIN)).thenReturn(List.of(projA));

        ResponseEntity<ApiResponse<List<TopicWithProgressDto>>> response =
                controller.listTopics("NL", "cbr", PRODUCT, DOMAIN, TENANT_ID, USER_ID, LOCALE);

        List<TopicWithProgressDto> topics = response.getBody().getData();
        assertEquals(2, topics.size());

        // Topic A has progress
        TopicWithProgressDto.TopicProgressOverlay progressA = topics.get(0).progress();
        assertEquals(50.0, progressA.coveragePercent(), 0.01);  // 5/10 * 100
        assertEquals(80.0, progressA.accuracyPercent(), 0.01);  // 4/5 * 100
        assertEquals(2, progressA.masteredCount());

        // Topic B has no progress (not in aggregation results)
        TopicWithProgressDto.TopicProgressOverlay progressB = topics.get(1).progress();
        assertEquals(0.0, progressB.coveragePercent());
        assertEquals(0.0, progressB.accuracyPercent());
        assertEquals(0, progressB.masteredCount());
    }
}
