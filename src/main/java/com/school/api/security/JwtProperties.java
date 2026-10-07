package com.school.api.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(String secret, @DefaultValue("1h") Duration ttl) {
}
