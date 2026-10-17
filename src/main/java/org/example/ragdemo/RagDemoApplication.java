package org.example.ragdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RagDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagDemoApplication.class, args);
    }

}
