package com.pm.content_platform_backend.auth.mapper;


import com.pm.content_platform_backend.auth.dto.RegisterRequestDTO;
import com.pm.content_platform_backend.auth.dto.RegisterResponseDTO;
import com.pm.content_platform_backend.auth.entity.User;
import org.springframework.stereotype.Component;

@Component
public class RegisterMapper implements Mapper<RegisterResponseDTO, RegisterRequestDTO, User> {


    @Override
    public RegisterResponseDTO toDto(User user) {
        RegisterResponseDTO registerResponseDTO = new RegisterResponseDTO();
        registerResponseDTO.setUsername(user.getUsername());
        registerResponseDTO.setCreatedAt(user.getCreatedAt());

        return registerResponseDTO;
    }

    @Override
    public User toEntity(RegisterRequestDTO registerRequestDTO) {
        User user = new User();
        user.setUsername(registerRequestDTO.getUsername());
        return user;
    }
}
