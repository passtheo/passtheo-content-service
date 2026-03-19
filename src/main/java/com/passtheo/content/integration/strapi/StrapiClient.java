package com.passtheo.content.integration.strapi;

import com.passtheo.content.integration.strapi.dto.StrapiAchievementDefDto;
import com.passtheo.content.integration.strapi.dto.StrapiCountryDto;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.integration.strapi.dto.StrapiLessonDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductTypeDto;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.content.integration.strapi.dto.StrapiRoadSignDto;
import com.passtheo.content.integration.strapi.dto.StrapiTopicDto;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * REST client for Strapi CMS. Read-only — never writes to Strapi.
 * All responses are raw from Strapi; caching is handled by StrapiContentCache.
 */
@Component
public class StrapiClient {

    private static final Logger LOG = LoggerFactory.getLogger(StrapiClient.class);
    private static final String POPULATE_QUESTION = "populate=answerOptions,explanation,imageRegions,dragTargets,image,video";

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
        return fetchList("/api/countries?filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<>() {});
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
        return fetchList("/api/product-types?filters[country][code][$eq]=" + countryCode
                + "&filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<>() {});
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
        return fetchList("/api/products?filters[productType][code][$eq]=" + productTypeCode
                + "&filters[isActive][$eq]=true&populate=examConfig&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<>() {});
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
        return fetchList("/api/domains?filters[product][code][$eq]=" + productCode
                + "&filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<>() {});
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
        return fetchList("/api/topics?filters[domain][code][$eq]=" + domainCode
                + "&filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<>() {});
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
        return fetchList("/api/questions?filters[topic][code][$eq]=" + topicCode
                + "&filters[isActive][$eq]=true&" + POPULATE_QUESTION + "&locale=" + locale
                + "&pagination[pageSize]=100",
                new ParameterizedTypeReference<>() {});
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
        return fetchList("/api/questions?filters[domain][code][$eq]=" + domainCode
                + "&filters[isActive][$eq]=true&" + POPULATE_QUESTION + "&locale=" + locale
                + "&pagination[pageSize]=200",
                new ParameterizedTypeReference<>() {});
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
        return fetchList("/api/questions?filters[product][code][$eq]=" + productCode
                + "&filters[isActive][$eq]=true&" + POPULATE_QUESTION + "&locale=" + locale
                + "&pagination[pageSize]=500",
                new ParameterizedTypeReference<>() {});
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
            return webClient.get()
                    .uri("/api/questions/" + questionId + "?" + POPULATE_QUESTION + "&locale=" + locale)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, StrapiQuestionDto>>() {})
                    .map(r -> r.get("data"))
                    .block();
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
        List<StrapiExamConfigDto> configs = fetchList(
                "/api/exam-configs?filters[product][code][$eq]=" + productCode,
                new ParameterizedTypeReference<>() {});
        return configs.isEmpty() ? null : configs.getFirst();
    }

    /**
     * Fetches all achievement definitions.
     *
     * @return list of achievements
     */
    public List<StrapiAchievementDefDto> getAchievements() {
        LOG.debug("Fetching achievements from Strapi");
        return fetchList("/api/achievements?filters[isActive][$eq]=true&sort=sortOrder",
                new ParameterizedTypeReference<>() {});
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
        return fetchList(url, new ParameterizedTypeReference<>() {});
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
        return fetchList("/api/lessons?filters[topic][code][$eq]=" + topicCode
                + "&filters[isActive][$eq]=true&sort=sortOrder&locale=" + locale,
                new ParameterizedTypeReference<>() {});
    }

    /**
     * Generic fetch list method for Strapi collections.
     */
    private <T> List<T> fetchList(String uri, ParameterizedTypeReference<List<T>> typeRef) {
        try {
            List<T> result = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(typeRef)
                    .block();
            return result != null ? result : List.of();
        } catch (Exception e) {
            LOG.error("Failed to fetch from Strapi [{}]: {}", uri, e.getMessage());
            return List.of();
        }
    }
}
