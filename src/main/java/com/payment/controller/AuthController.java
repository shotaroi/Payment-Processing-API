package com.payment.controller;

import com.payment.dto.AuthResponse;
import com.payment.dto.LoginRequest;
import com.payment.dto.RegisterRequest;
import com.payment.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Merchant registration and login")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new merchant")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.name(), request.email(), request.password());
        String token = authService.login(request.email(), request.password());
        return new AuthResponse(token);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and get JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.email(), request.password());
        return new AuthResponse(token);
    }
}
