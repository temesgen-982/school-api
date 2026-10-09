package com.school.api.security;

import com.school.api.common.web.ApiPaths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Applies the per-school scoping rules to every route under {@code /schools}, so a
 * school-scoped caller can only reach their own school and its nested resources.
 */
@Configuration
public class SchoolScopeConfig implements WebMvcConfigurer {

    private final SchoolScopeInterceptor schoolScopeInterceptor;

    public SchoolScopeConfig(SchoolScopeInterceptor schoolScopeInterceptor) {
        this.schoolScopeInterceptor = schoolScopeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(schoolScopeInterceptor)
                .addPathPatterns(ApiPaths.V1 + "/schools/**");
    }
}
