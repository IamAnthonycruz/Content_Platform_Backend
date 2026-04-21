package com.pm.content_platform_backend.auth.controller;



import com.pm.content_platform_backend.auth.dto.*;
import com.pm.content_platform_backend.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;
    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterRequestDTO registrationRequestDTO){
        RegisterResponseDTO response = userService.register(registrationRequestDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO){
        LoginResponseDTO response = userService.login(loginRequestDTO);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO refreshRequestDTO){
        RefreshResponseDTO responseDTO = userService.refresh(refreshRequestDTO);
        return new ResponseEntity<>(responseDTO, HttpStatus.OK);
    }

}
