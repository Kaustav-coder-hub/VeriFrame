package com.mediaprovenance.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.blockchain.mode", havingValue = "HTTP")
public class BlockchainConfig {

    @Bean
    public RestClient blockchainStandardRestClient(AppProperties appProperties) {
        AppProperties.BlockchainProperties props = appProperties.getBlockchain();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutStandardMs());

        return RestClient.builder()
                .baseUrl(props.getServiceUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    public RestClient blockchainLongRestClient(AppProperties appProperties) {
        AppProperties.BlockchainProperties props = appProperties.getBlockchain();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutLongMs());

        return RestClient.builder()
                .baseUrl(props.getServiceUrl())
                .requestFactory(factory)
                .build();
    }
}
