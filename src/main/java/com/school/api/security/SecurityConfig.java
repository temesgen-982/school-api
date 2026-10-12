package com.school.api.security;

import com.school.api.auth.AuthController;
import com.school.api.common.domain.Role;
import com.school.api.common.web.ApiPaths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Comma-separated browser origins allowed by CORS ({@code ALLOWED_ORIGINS}). */
    private final String allowedOrigins;

    public SecurityConfig(@Value("${allowed.origins:http://localhost:3000}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(AuthController.LOGIN, AuthController.SIGNUP).permitAll()
                        // Public API docs — viewing them needs no token, calling the
                        // endpoints they describe still does
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                .permitAll()
                        .requestMatchers(ApiPaths.V1 + "/users/**")
                                .hasRole(Role.PLATFORM_ADMIN.name())
                        .requestMatchers(HttpMethod.GET, ApiPaths.BASE + "/**").authenticated()
                        .requestMatchers(HttpMethod.POST, ApiPaths.V1 + "/schools")
                                .hasRole(Role.PLATFORM_ADMIN.name())
                        .requestMatchers(ApiPaths.BASE + "/**")
                                .hasAnyRole(Role.PLATFORM_ADMIN.name(), Role.OWNER.name())
                        .anyRequest().authenticated())
                // CORS: preflights are answered here, before authorization, so
                // browser clients on allowed origins can call the API
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Allows the browser origins listed in {@code allowed.origins}
     * ({@code ALLOWED_ORIGINS}, comma-separated; defaults to the Next.js dev
     * server) to call the API. Bearer-token auth means no cookies are involved,
     * so credentials are not allowed. {@code Location} is exposed because the
     * create endpoints return it with their {@code 201} responses.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Location"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Maps the {@code roles} claim ({@code PLATFORM_ADMIN}, {@code SCHOOL_ADMIN},
     * {@code OWNER}) to the {@code ROLE_} authorities used by {@code hasRole(...)}.
     * Tokens without roles are already rejected by the decoder, so the mapping itself
     * never fails.
     */
    private static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            if (jwt.getClaim("roles") instanceof Collection<?> roles) {
                for (Object role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            return List.copyOf(authorities);
        });
        return converter;
    }
}
