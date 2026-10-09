package com.school.api.security;

import com.school.api.auth.AuthController;
import com.school.api.common.domain.Role;
import com.school.api.common.web.ApiPaths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(AuthController.LOGIN).permitAll()
                        .requestMatchers(ApiPaths.V1 + "/users/**")
                                .hasRole(Role.PLATFORM_ADMIN.name())
                        .requestMatchers(HttpMethod.GET, ApiPaths.BASE + "/**").authenticated()
                        .requestMatchers(HttpMethod.POST, ApiPaths.V1 + "/schools")
                                .hasRole(Role.PLATFORM_ADMIN.name())
                        .requestMatchers(ApiPaths.BASE + "/**")
                                .hasAnyRole(Role.PLATFORM_ADMIN.name(), Role.OWNER.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
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
