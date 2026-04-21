package com.pm.content_platform_backend.auth.service;

import com.pm.content_platform_backend.auth.dto.*;
import com.pm.content_platform_backend.auth.entity.User;
import com.pm.content_platform_backend.auth.exception.DuplicateUsernameException;
import com.pm.content_platform_backend.auth.exception.InvalidCredentialException;
import com.pm.content_platform_backend.auth.exception.InvalidRefreshTokenException;
import com.pm.content_platform_backend.auth.mapper.RegisterMapper;
import com.pm.content_platform_backend.auth.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegisterMapper registerMapper;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public RegisterResponseDTO register(RegisterRequestDTO registerRequestDTO) {
        if (userRepository.findByUsername(registerRequestDTO.getUsername()).isPresent()){
            throw new DuplicateUsernameException("Username already exists");
        }
        User user = registerMapper.toEntity(registerRequestDTO);
        user.setPasswordHash(passwordEncoder.encode(registerRequestDTO.getPassword()));
        try{
            userRepository.save(user);
        } catch(DataIntegrityViolationException e) {
            throw new DuplicateUsernameException("Username already exists");
        }


        return registerMapper.toDto(user);
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO) {
        User user = userRepository.findByUsername(loginRequestDTO.getUsername())
                .orElseThrow(() -> new InvalidCredentialException("Invalid username or password"));
        if(!passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPasswordHash())){
            throw new InvalidCredentialException("Invalid username or password");
        }
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getUserId());

        LoginResponseDTO loginResponseDTO = new LoginResponseDTO();
        loginResponseDTO.setAccessToken(accessToken);
        loginResponseDTO.setRefreshToken(refreshToken);
        loginResponseDTO.setExpiresIn(jwtService.getAccessTokenTtl().toSeconds());
        return loginResponseDTO;

    }

    @Override
    public RefreshResponseDTO refresh(RefreshRequestDTO request) {
        Long userId = refreshTokenService.lookUp(request.getRefreshToken())
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    refreshTokenService.revoke(request.getRefreshToken());
                    return new InvalidRefreshTokenException("Invalid refresh token");
                });

        RefreshResponseDTO response = new RefreshResponseDTO();
        response.setAccessToken(jwtService.generateAccessToken(user));
        response.setExpiresIn(jwtService.getAccessTokenTtl().toSeconds());
        // tokenType set by DTO default
        return response;
    }

}
