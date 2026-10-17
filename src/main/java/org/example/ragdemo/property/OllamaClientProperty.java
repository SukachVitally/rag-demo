package org.example.ragdemo.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.olama-client")
public record OllamaClientProperty(String baseUrl, String embedModel, String chatModel) {
}
