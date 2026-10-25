package com.smartstudy.backend.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class MlClientConfig {

    @Bean
    public RestClient mlRestClient(@Value("${app.ml-service-url}") String baseUrl,
                                   @Value("${app.ml-internal-key:}") String internalKey) {
        return buildClient(baseUrl, internalKey, Duration.ofSeconds(180));
    }

    @Bean
    @Qualifier("mlOcrRestClient")
    public RestClient mlOcrRestClient(@Value("${app.ml-service-url}") String baseUrl,
                                      @Value("${app.ml-internal-key:}") String internalKey) {
        return buildClient(baseUrl, internalKey, Duration.ofSeconds(1800));
    }

    private RestClient buildClient(String baseUrl, String internalKey, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(readTimeout);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);
        if (!internalKey.isBlank()) {
            builder.defaultHeader("X-Internal-Key", internalKey);
        }
        return builder.build();
    }
}