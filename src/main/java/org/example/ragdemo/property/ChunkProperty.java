package org.example.ragdemo.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chunk")
public record ChunkProperty(int size, int overlap) {
}
