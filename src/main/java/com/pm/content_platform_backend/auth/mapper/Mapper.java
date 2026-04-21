package com.pm.content_platform_backend.auth.mapper;

public interface Mapper<ResponseDto,RequestDto,Entity>  {
    ResponseDto toDto(Entity entity);
    Entity toEntity(RequestDto dto);
}
