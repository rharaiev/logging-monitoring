package com.lohika.course.bfffrontend.config;

import brave.sampler.Sampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class Config {

    @Value("${books.url}")
    private String booksUrl;

    @Value("${authors.url}")
    private String authorsUrl;

    @Bean("bookWebClient")
    public WebClient bookWebClient() {
        return WebClient
                .builder()
                .baseUrl(booksUrl)
                .build();
    }

    @Bean("authorWebClient")
    public WebClient authorWebClient() {
        return WebClient
                .builder()
                .baseUrl(authorsUrl)
                .build();
    }

    @Bean
    public Sampler defaultSampler() {
        return Sampler.ALWAYS_SAMPLE;
    }
}
