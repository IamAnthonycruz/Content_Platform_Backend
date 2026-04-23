package com.pm.content_platform_backend.auth.service;


import com.pm.content_platform_backend.auth.dto.*;

public interface UserService {
    RegisterResponseDTO register(RegisterRequestDTO registerRequestDTO);
    LoginResponseDTO login (LoginRequestDTO loginRequestDTO);
    RefreshResponseDTO refresh(RefreshRequestDTO refreshRequestDTO);
    void logout(RefreshRequestDTO refreshRequestDTO);
}
