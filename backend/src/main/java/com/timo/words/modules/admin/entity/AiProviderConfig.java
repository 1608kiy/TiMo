package com.timo.words.modules.admin.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_provider_config")
public class AiProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;  // WRITE_ONLY: accepts input but never serialized to JSON responses

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "max_tokens")
    private Integer maxTokens = 2048;

    private Double temperature = 0.7;

    @Column(name = "is_active")
    private Boolean isActive = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
