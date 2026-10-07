package com.school.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "auth.admin")
public record AdminProperties(@DefaultValue("admin") String username, @DefaultValue("admin") String password) {
}
