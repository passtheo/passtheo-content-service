package com.passtheo.content.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for content caching (DB 3) and access grant reading (DB 2).
 * Uses Lettuce 6.5.x with RESP2 protocol to avoid NOAUTH HELLO issues.
 *
 * <p>Two connection factories:
 * <ul>
 *   <li><b>Primary (DB 3)</b> — Strapi content cache, used by StrapiContentCache</li>
 *   <li><b>accessCacheRedisTemplate (DB 2)</b> — shared access cache written by subscription-service,
 *       used by EntitlementChecker</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    /**
     * Creates primary Redis connection factory for the Strapi content cache (DB 3).
     */
    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.database:3}") int database,
            @Value("${spring.data.redis.password:}") String password) {

        return createConnectionFactory(host, port, database, password);
    }

    /**
     * Creates Redis connection factory for the shared access cache (DB 2).
     */
    @Bean("accessCacheConnectionFactory")
    public LettuceConnectionFactory accessCacheConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${passtheo.access-cache.redis-database:2}") int database,
            @Value("${spring.data.redis.password:}") String password) {

        return createConnectionFactory(host, port, database, password);
    }

    /**
     * Primary RedisTemplate for Strapi content cache.
     *
     * @param connectionFactory the primary connection factory (DB 3)
     * @return the configured RedisTemplate
     */
    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory connectionFactory) {
        return createStringTemplate(connectionFactory);
    }

    /**
     * RedisTemplate for reading the shared access cache (DB 2).
     *
     * @param connectionFactory the access cache connection factory (DB 2)
     * @return the configured RedisTemplate
     */
    @Bean("accessCacheRedisTemplate")
    public RedisTemplate<String, String> accessCacheRedisTemplate(
            @Qualifier("accessCacheConnectionFactory") LettuceConnectionFactory connectionFactory) {
        return createStringTemplate(connectionFactory);
    }

    private LettuceConnectionFactory createConnectionFactory(
            String host, int port, int database, String password) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(database);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder()
                        .protocolVersion(ProtocolVersion.RESP2)
                        .build())
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    private RedisTemplate<String, String> createStringTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
