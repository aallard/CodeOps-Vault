package com.codeops.vault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Provides a {@link RestTemplate} bean configured with connection and read timeouts
 * for cross-service REST calls (to Server, Registry, Logger).
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates a {@link RestTemplate} with a 10-second connect timeout and 30-second read timeout.
     *
     * @return the configured {@link RestTemplate}
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }
}
