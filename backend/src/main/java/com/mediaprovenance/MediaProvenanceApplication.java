package com.mediaprovenance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import com.mediaprovenance.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
public class MediaProvenanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaProvenanceApplication.class, args);
    }
}
