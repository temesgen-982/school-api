package com.school.api.auth;

import com.school.api.auth.dto.LoginRequest;
import com.school.api.auth.dto.LoginResponse;
import com.school.api.common.exception.UnauthorizedException;
import com.school.api.security.AuthenticatedUser;
import com.school.api.security.JwtService;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException | AccountStatusException ex) {
            throw new UnauthorizedException("Invalid username or password");
        }
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        JwtService.IssuedToken token = jwtService.issue(principal.getAccount());
        return new LoginResponse(token.accessToken(), "Bearer", token.expiresAt());
    }
}
