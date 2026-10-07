package com.school.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.school.api.auth.dto.LoginRequest;
import com.school.api.auth.dto.LoginResponse;
import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.common.exception.UnauthorizedException;
import com.school.api.security.AuthenticatedUser;
import com.school.api.security.JwtService;
import com.school.api.user.UserAccount;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, jwtService);
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        UserAccount account = account();
        AuthenticatedUser principal = new AuthenticatedUser(account);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.issue(account)).thenReturn(new JwtService.IssuedToken("signed-token", expiresAt));

        LoginResponse response = authService.login(new LoginRequest("admin", "secret"));

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void loginRejectsBadPassword() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void loginRejectsInactiveAccount() {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("Account is disabled"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "secret")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");
    }

    private static UserAccount account() {
        UserAccount account = new UserAccount();
        account.setUsername("admin");
        account.setPassword("$2a$10$hash");
        account.setStatus(Status.ACTIVE);
        account.setRoles(Set.of(Role.PLATFORM_ADMIN));
        return account;
    }
}
