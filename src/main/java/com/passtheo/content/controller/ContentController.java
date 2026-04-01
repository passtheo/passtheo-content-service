package com.passtheo.content.controller;

import com.passtheo.content.domain.entity.DomainProgress;
import com.passtheo.content.domain.valueobject.AccessGrant;
import com.passtheo.content.dto.response.CountryDto;
import com.passtheo.content.dto.response.DomainWithProgressDto;
import com.passtheo.content.dto.response.LessonDto;
import com.passtheo.content.dto.response.LessonDto.LessonSectionDto;
import com.passtheo.content.dto.response.OnboardingCatalogDto;
import com.passtheo.content.dto.response.ProductDto;
import com.passtheo.content.dto.response.ProductTypeDto;
import com.passtheo.content.dto.response.RoadSignDto;
import com.passtheo.content.dto.response.TopicWithProgressDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.repository.DomainProgressRepository;
import com.passtheo.content.service.EntitlementChecker;
import com.passtheo.content.service.OnboardingCatalogService;
import com.passtheo.shared.core.dto.ApiResponse;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Browsing the content hierarchy: Country → ProductType → Product → Domain → Topic.
 * All data comes from Strapi via Redis cache.
 */
@RestController
@RequestMapping("/api/content")
public class ContentController {

    private static final Logger LOG = LoggerFactory.getLogger(ContentController.class);

    private final StrapiContentCache strapiContentCache;
    private final DomainProgressRepository domainProgressRepository;
    private final EntitlementChecker entitlementChecker;
    private final OnboardingCatalogService onboardingCatalogService;

    /**
     * Constructs the content controller.
     *
     * @param strapiContentCache       Strapi content cache
     * @param domainProgressRepository domain progress repository
     * @param entitlementChecker       entitlement checker
     * @param onboardingCatalogService onboarding catalog service
     */
    public ContentController(StrapiContentCache strapiContentCache,
                             DomainProgressRepository domainProgressRepository,
                             EntitlementChecker entitlementChecker,
                             OnboardingCatalogService onboardingCatalogService) {
        this.strapiContentCache = strapiContentCache;
        this.domainProgressRepository = domainProgressRepository;
        this.entitlementChecker = entitlementChecker;
        this.onboardingCatalogService = onboardingCatalogService;
    }

    /**
     * Returns the full onboarding catalog in one call.
     * Public — no auth required (user has not registered yet).
     *
     * @return onboarding catalog with countries, product types, products, and app config
     */
    @GetMapping("/onboarding-catalog")
    public ResponseEntity<ApiResponse<OnboardingCatalogDto>> getOnboardingCatalog() {
        OnboardingCatalogDto catalog = onboardingCatalogService.getOnboardingCatalog();
        return ResponseEntity.ok(ApiResponse.success(catalog, MDC.get("traceId")));
    }

    /**
     * Lists available countries.
     *
     * @param locale content locale
     * @return list of countries
     */
    @GetMapping("/countries")
    public ResponseEntity<ApiResponse<List<CountryDto>>> listCountries(
            @RequestParam(defaultValue = "nl") String locale) {
        var countries = strapiContentCache.getCountries(locale).stream()
                .map(c -> new CountryDto(c.code(), c.name(), c.flagImage(), c.supportedLocales()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(countries, MDC.get("traceId")));
    }

    /**
     * Lists product types for a country.
     *
     * @param countryCode the country code
     * @param locale      content locale
     * @return list of product types
     */
    @GetMapping("/{countryCode}/product-types")
    public ResponseEntity<ApiResponse<List<ProductTypeDto>>> listProductTypes(
            @PathVariable @Nonnull String countryCode,
            @RequestParam(defaultValue = "nl") String locale) {
        var types = strapiContentCache.getProductTypes(countryCode, locale).stream()
                .map(t -> new ProductTypeDto(t.code(), t.name(), t.description(), t.icon(), t.productCount()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(types, MDC.get("traceId")));
    }

    /**
     * Lists products for a product type.
     *
     * @param countryCode     the country code
     * @param productTypeCode the product type code
     * @param locale          content locale
     * @return list of products
     */
    @GetMapping("/{countryCode}/{productTypeCode}/products")
    public ResponseEntity<ApiResponse<List<ProductDto>>> listProducts(
            @PathVariable @Nonnull String countryCode,
            @PathVariable @Nonnull String productTypeCode,
            @RequestParam(defaultValue = "nl") String locale) {
        var products = strapiContentCache.getProducts(productTypeCode, locale).stream()
                .map(p -> new ProductDto(
                        p.code(), p.name(), p.licenceCode(), p.description(), p.icon(),
                        p.examConfig() != null ? new ProductDto.ExamConfigDto(
                                p.examConfig().totalQuestions(),
                                p.examConfig().timeLimitMinutes(),
                                p.examConfig().passScore()) : null,
                        p.domainCount(), strapiContentCache.getQuestionCount(p.code(), locale)))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(products, MDC.get("traceId")));
    }

    /**
     * Lists domains for a product with user progress overlay.
     *
     * @param countryCode     the country code
     * @param productTypeCode the product type code
     * @param productCode     the product code
     * @param tenantId        tenant ID from header
     * @param userId          user ID from header
     * @param locale          content locale
     * @return list of domains with progress
     */
    @GetMapping("/{countryCode}/{productTypeCode}/{productCode}/domains")
    public ResponseEntity<ApiResponse<List<DomainWithProgressDto>>> listDomains(
            @PathVariable @Nonnull String countryCode,
            @PathVariable @Nonnull String productTypeCode,
            @PathVariable @Nonnull String productCode,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam(defaultValue = "nl") String locale) {

        List<StrapiDomainDto> domains = strapiContentCache.getDomains(productCode, locale);
        AccessGrant access = entitlementChecker.getAccess(tenantId, userId);

        Map<String, DomainProgress> progressMap = domainProgressRepository
                .findByKeycloakUserIdAndProductCode(userId, productCode).stream()
                .collect(Collectors.toMap(DomainProgress::getDomainCode, dp -> dp));

        List<DomainWithProgressDto> result = domains.stream().map(d -> {
            DomainProgress dp = progressMap.get(d.code());
            boolean isLocked = !access.isPaid() && !d.isFreePreview();

            DomainWithProgressDto.ProgressOverlay progress = dp != null
                    ? new DomainWithProgressDto.ProgressOverlay(
                    dp.getCoveragePercent() != null ? dp.getCoveragePercent().doubleValue() : 0.0,
                    dp.getAccuracyPercent() != null ? dp.getAccuracyPercent().doubleValue() : 0.0,
                    dp.getMasteredCount(),
                    dp.getStrength() != null ? dp.getStrength().name() : "UNKNOWN")
                    : new DomainWithProgressDto.ProgressOverlay(0.0, 0.0, 0, "UNKNOWN");

            int questionCount = strapiContentCache.getQuestionCountByDomain(d.code(), locale);

            return new DomainWithProgressDto(
                    d.code(), d.name(), d.icon(), d.color(),
                    0, questionCount,
                    d.isFreePreview(), isLocked, progress);
        }).toList();

        return ResponseEntity.ok(ApiResponse.success(result, MDC.get("traceId")));
    }

    /**
     * Lists topics for a domain with user progress.
     *
     * @param countryCode     the country code
     * @param productTypeCode the product type code
     * @param productCode     the product code
     * @param domainCode      the domain code
     * @param locale          content locale
     * @return list of topics with progress
     */
    @GetMapping("/{countryCode}/{productTypeCode}/{productCode}/domains/{domainCode}/topics")
    public ResponseEntity<ApiResponse<List<TopicWithProgressDto>>> listTopics(
            @PathVariable @Nonnull String countryCode,
            @PathVariable @Nonnull String productTypeCode,
            @PathVariable @Nonnull String productCode,
            @PathVariable @Nonnull String domainCode,
            @RequestParam(defaultValue = "nl") String locale) {
        var topics = strapiContentCache.getTopics(domainCode, locale).stream()
                .map(t -> new TopicWithProgressDto(
                        t.code(), t.name(), t.difficulty(),
                        strapiContentCache.getQuestionCountByTopic(t.code(), locale),
                        null))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(topics, MDC.get("traceId")));
    }

    /**
     * Lists road signs for a product.
     *
     * @param countryCode the country code
     * @param locale      content locale
     * @param category    optional sign category filter
     * @return list of road signs
     */
    @GetMapping("/{countryCode}/{productTypeCode}/{productCode}/road-signs")
    public ResponseEntity<ApiResponse<List<RoadSignDto>>> listRoadSigns(
            @PathVariable @Nonnull String countryCode,
            @PathVariable String productTypeCode,
            @PathVariable String productCode,
            @RequestParam(defaultValue = "nl") String locale,
            @RequestParam(required = false) String category) {
        var signs = strapiContentCache.getRoadSigns(countryCode, locale, category).stream()
                .map(s -> new RoadSignDto(s.code(), s.name(), s.signCategory(),
                        s.description(), s.imageUrl(), s.shape()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(signs, MDC.get("traceId")));
    }

    /**
     * Lists lessons for a topic.
     *
     * @param topicCode the topic code
     * @param locale    content locale
     * @return list of lessons
     */
    @GetMapping("/{countryCode}/{productTypeCode}/{productCode}/lessons/{topicCode}")
    public ResponseEntity<ApiResponse<List<LessonDto>>> listLessons(
            @PathVariable String countryCode,
            @PathVariable String productTypeCode,
            @PathVariable String productCode,
            @PathVariable @Nonnull String topicCode,
            @RequestParam(defaultValue = "nl") String locale) {
        var lessons = strapiContentCache.getLessons(topicCode, locale).stream()
                .map(l -> new LessonDto(
                        l.title(), l.slug(),
                        l.sections() != null ? l.sections().stream()
                                .map(s -> new LessonSectionDto(
                                        s.heading(), s.body(), s.tip(),
                                        s.keyRule(), s.relatedRoadSignCode(), s.sortOrder()))
                                .toList() : List.of(),
                        l.summary(), l.coverImage(), l.videoUrl(), l.readTimeMinutes()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(lessons, MDC.get("traceId")));
    }
}
