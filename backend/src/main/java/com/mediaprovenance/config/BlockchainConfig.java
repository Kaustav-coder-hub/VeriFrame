package com.mediaprovenance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BlockchainConfig {

    @Bean
    public RestClient blockchainRestClient(AppProperties appProperties) {
        return RestClient.builder()
                .baseUrl(appProperties.getBlockchain().getServiceUrl())
                .build();
    }
}
