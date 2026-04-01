package com.passtheo.content.integration.strapi;

import com.passtheo.content.integration.strapi.dto.StrapiAchievementDefDto;
import com.passtheo.content.integration.strapi.dto.StrapiAppConfigDto;
import com.passtheo.content.integration.strapi.dto.StrapiCountryDto;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.integration.strapi.dto.StrapiLessonDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductTypeDto;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.content.integration.strapi.dto.StrapiResponse;
import com.passtheo.content.integration.strapi.dto.StrapiRoadSignDto;
import com.passtheo.content.integration.strapi.dto.StrapiSingleResponse;
import com.passtheo.content.integration.strapi.dto.StrapiTopicDto;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * REST client for Strapi CMS. Read-only — never writes to Strapi.
 * All responses are raw from Strapi; caching is handled by StrapiContentCache.
 */
@Component
public class StrapiClient {

    private static final Logger LOG = LoggerFactory.getLogger(StrapiClient.class);
    private static final String POPULATE_QUESTION = "populate[0]=answerOptions&populate[1]=explanation&populate[2]=imageRegions&populate[3]=dragTargets&populate[4]=image&populate[5]=video&populate[6]=domain&populate[7]=topic&populate[8]=roadSigns";
    private static final String FILTER_ACTIVE_APPROVED = "&filters[isActive][$eq]=true&filters[reviewStatus][$eq]=APPROVED";

    private final WebClient webClient;

    /**
     * Constructs the Strapi client.
     *
     * @param strapiWebClient the pre-configured Strapi WebClient
     */
    public StrapiClient(@Qualifier("strapiWebClient") WebClient strapiWebClient) {
        this.webClient = strapiWebClient;
    }

    /**
     * Fetches all active countries.
     *
     * @param locale the content locale
     * @return list of countries
     */
    public List<StrapiCountryDto> getCountries(@Nonnull String locale) {
        LOG.debug("Fetching countries from Strapi, locale={}", locale);
        return fetchCollection("/api/countries?filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<StrapiResponse<StrapiCountryDto>>() {
                });
    }

    /**
     * Fetches product types for a country.
     *
     * @param countryCode the country code
     * @param locale      the content locale
     * @return list of product types
     */
    public List<StrapiProductTypeDto> getProductTypes(@Nonnull String countryCode, @Nonnull String locale) {
        LOG.debug("Fetching product types from Strapi, country={}, locale={}", countryCode, locale);
        return fetchCollection("/api/product-types?filters[country][code][$eq]=" + countryCode
                + "&filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<StrapiResponse<StrapiProductTypeDto>>() {
                });
    }

    /**
     * Fetches products for a product type.
     *
     * @param productTypeCode the product type code
     * @param locale          the content locale
     * @return list of products
     */
    public List<StrapiProductDto> getProducts(@Nonnull String productTypeCode, @Nonnull String locale) {
        LOG.debug("Fetching products from Strapi, productType={}, locale={}", productTypeCode, locale);
        return fetchCollection("/api/products?filters[productType][code][$eq]=" + productTypeCode
                + "&filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<StrapiResponse<StrapiProductDto>>() {
                });
    }

    /**
     * Fetches domains for a product.
     *
     * @param productCode the product code
     * @param locale      the content locale
     * @return list of domains
     */
    public List<StrapiDomainDto> getDomains(@Nonnull String productCode, @Nonnull String locale) {
        LOG.debug("Fetching domains from Strapi, product={}, locale={}", productCode, locale);
        return fetchCollection("/api/domains?filters[product][code][$eq]=" + productCode
                + "&filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<StrapiResponse<StrapiDomainDto>>() {
                });
    }

    /**
     * Fetches topics for a domain.
     *
     * @param domainCode the domain code
     * @param locale     the content locale
     * @return list of topics
     */
    public List<StrapiTopicDto> getTopics(@Nonnull String domainCode, @Nonnull String locale) {
        LOG.debug("Fetching topics from Strapi, domain={}, locale={}", domainCode, locale);
        return fetchCollection("/api/topics?filters[domain][code][$eq]=" + domainCode
                + "&filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<StrapiResponse<StrapiTopicDto>>() {
                });
    }

    /**
     * Fetches questions for a topic with full populate.
     *
     * @param topicCode the topic code
     * @param locale    the content locale
     * @return list of questions
     */
    public List<StrapiQuestionDto> getQuestionsByTopic(@Nonnull String topicCode, @Nonnull String locale) {
        LOG.debug("Fetching questions from Strapi, topic={}, locale={}", topicCode, locale);
        return fetchCollection("/api/questions?filters[topic][code][$eq]=" + topicCode
                + FILTER_ACTIVE_APPROVED + "&" + POPULATE_QUESTION + "&locale=" + locale
                + "&pagination[pageSize]=100",
                new ParameterizedTypeReference<StrapiResponse<StrapiQuestionDto>>() {
                });
    }

    /**
     * Fetches questions for a domain with full populate.
     *
     * @param domainCode the domain code
     * @param locale     the content locale
     * @return list of questions
     */
    public List<StrapiQuestionDto> getQuestionsByDomain(@Nonnull String domainCode, @Nonnull String locale) {
        LOG.debug("Fetching questions from Strapi, domain={}, locale={}", domainCode, locale);
        return fetchCollection("/api/questions?filters[topic][domain][code][$eq]=" + domainCode
                + FILTER_ACTIVE_APPROVED + "&" + POPULATE_QUESTION + "&locale=" + locale
                + "&pagination[pageSize]=200",
                new ParameterizedTypeReference<StrapiResponse<StrapiQuestionDto>>() {
                });
    }

    /**
     * Fetches all questions for a product.
     *
     * @param productCode the product code
     * @param locale      the content locale
     * @return list of questions
     */
    public List<StrapiQuestionDto> getQuestionsByProduct(@Nonnull String productCode, @Nonnull String locale) {
        LOG.debug("Fetching all questions from Strapi, product={}, locale={}", productCode, locale);
        return fetchCollection("/api/questions?filters[topic][domain][product][code][$eq]=" + productCode
                + FILTER_ACTIVE_APPROVED + "&" + POPULATE_QUESTION + "&locale=" + locale
                + "&pagination[pageSize]=500",
                new ParameterizedTypeReference<StrapiResponse<StrapiQuestionDto>>() {
                });
    }

    /**
     * Fetches a single question by ID.
     *
     * @param questionId the Strapi question ID
     * @param locale     the content locale
     * @return the question, or null if not found
     */
    public StrapiQuestionDto getQuestion(@Nonnull String questionId, @Nonnull String locale) {
        LOG.debug("Fetching question from Strapi, id={}, locale={}", questionId, locale);
        try {
            StrapiSingleResponse<StrapiQuestionDto> response = webClient.get()
                    .uri("/api/questions/" + questionId + "?" + POPULATE_QUESTION + "&locale=" + locale)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<StrapiSingleResponse<StrapiQuestionDto>>() {
                    })
                    .block();
            return response != null ? response.data() : null;
        } catch (Exception e) {
            LOG.error("Failed to fetch question {} from Strapi: {}", questionId, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches exam config for a product.
     *
     * @param productCode the product code
     * @return the exam config
     */
    public StrapiExamConfigDto getExamConfig(@Nonnull String productCode) {
        LOG.debug("Fetching exam config from Strapi, product={}", productCode);
        List<StrapiExamConfigDto> configs = fetchCollection(
                "/api/exam-configs?filters[product][code][$eq]=" + productCode
                        + "&populate=domainWeights",
                new ParameterizedTypeReference<StrapiResponse<StrapiExamConfigDto>>() {
                });
        return configs.isEmpty() ? null : configs.getFirst();
    }

    /**
     * Fetches all achievement definitions.
     *
     * @param productCode the product code
     * @return list of achievements
     */
    public List<StrapiAchievementDefDto> getAchievements(@Nonnull String productCode) {
        LOG.debug("Fetching achievements from Strapi, productCode={}", productCode);
        return fetchCollection(
                "/api/achievements?filters[$or][0][product][code][$eq]=" + productCode
                        + "&filters[$or][1][product][$null]=true"
                        + "&filters[isActive][$eq]=true&sort=sortOrder",
                new ParameterizedTypeReference<StrapiResponse<StrapiAchievementDefDto>>() {
                });
    }

    /**
     * Fetches road signs for a country.
     *
     * @param countryCode the country code
     * @param locale      the content locale
     * @param category    optional sign category filter
     * @return list of road signs
     */
    public List<StrapiRoadSignDto> getRoadSigns(@Nonnull String countryCode, @Nonnull String locale,
            String category) {
        LOG.debug("Fetching road signs from Strapi, country={}, locale={}", countryCode, locale);
        String url = "/api/road-signs?filters[country][code][$eq]=" + countryCode
                + "&filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale;
        if (category != null && !category.isBlank()) {
            url += "&filters[signCategory][$eq]=" + category;
        }
        return fetchCollection(url, new ParameterizedTypeReference<StrapiResponse<StrapiRoadSignDto>>() {
        });
    }

    /**
     * Fetches lessons for a topic.
     *
     * @param topicCode the topic code
     * @param locale    the content locale
     * @return list of lessons
     */
    public List<StrapiLessonDto> getLessons(@Nonnull String topicCode, @Nonnull String locale) {
        LOG.debug("Fetching lessons from Strapi, topic={}, locale={}", topicCode, locale);
        return fetchCollection("/api/lessons?filters[topic][code][$eq]=" + topicCode
                + "&filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale
                + "&populate[sections][populate]=*",
                new ParameterizedTypeReference<StrapiResponse<StrapiLessonDto>>() {
                });
    }

    /**
     * Fetches the app config single-type from Strapi.
     *
     * @return the app config, or null if unavailable
     */
    public StrapiAppConfigDto getAppConfig() {
        LOG.debug("Fetching app config from Strapi");
        try {
            StrapiSingleResponse<StrapiAppConfigDto> response = webClient.get()
                    .uri("/api/app-config")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<StrapiSingleResponse<StrapiAppConfigDto>>() {
                    })
                    .block();
            return response != null ? response.data() : null;
        } catch (Exception e) {
            LOG.error("Failed to fetch app config from Strapi: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetches a Strapi 5 collection endpoint, unwraps the {@code {"data":[...],"meta":{...}}} wrapper,
     * and logs a warning when the total item count exceeds the page size.
     */
    private <T> List<T> fetchCollection(String uri, ParameterizedTypeReference<StrapiResponse<T>> typeRef) {
        try {
            StrapiResponse<T> response = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(typeRef)
                    .block();
            if (response == null) {
                LOG.warn("Strapi returned null response for [{}]", uri);
                return List.of();
            }
            if (response.meta() != null && response.meta().pagination() != null) {
                StrapiResponse.StrapiPagination p = response.meta().pagination();
                if (p.total() > p.pageSize()) {
                    LOG.warn("Strapi pagination: pageSize={} < total={} — items truncated for [{}]",
                            p.pageSize(), p.total(), uri);
                }
            }
            return response.data() != null ? response.data() : List.of();
        } catch (Exception e) {
            LOG.error("Failed to fetch from Strapi [{}]: {}", uri, e.getMessage());
            return List.of();
        }
    }
}
