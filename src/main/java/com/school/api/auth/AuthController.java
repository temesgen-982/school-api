package com.school.api.auth;

import com.school.api.auth.dto.LoginRequest;
import com.school.api.auth.dto.LoginResponse;
import com.school.api.auth.dto.SignupRequest;
import com.school.api.auth.dto.SignupResponse;
import com.school.api.common.web.ApiPaths;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    public static final String LOGIN = ApiPaths.V1 + "/auth/login";
    public static final String SIGNUP = ApiPaths.V1 + "/auth/register";

    private final AuthService authService;
    private final SignupService signupService;

    public AuthController(AuthService authService, SignupService signupService) {
        this.authService = authService;
        this.signupService = signupService;
    }

    @PostMapping(LOGIN)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping(SIGNUP)
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = signupService.signup(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/schools/" + response.school().id()))
                .body(response);
    }
}
