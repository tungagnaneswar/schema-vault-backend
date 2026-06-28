package com.gnanadhan.app.mapper;

import com.gnanadhan.app.dto.DbConnectionRequest;
import com.gnanadhan.app.dto.DbConnectionResponse;
import com.gnanadhan.app.entity.DbConnection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DbConnectionMapper {

    @Mapping(target = "createdBy", source = "createdBy.email")
    @Mapping(target = "permissionLevel", ignore = true)
    @Mapping(target = "environmentId", source = "environment.id")
    @Mapping(target = "environmentName", source = "environment.name")
    @Mapping(target = "projectId", source = "environment.project.id")
    @Mapping(target = "projectName", source = "environment.project.name")
    @Mapping(target = "engine", expression = "java(dbConnection.getEngine().name())")
    DbConnectionResponse toResponse(DbConnection dbConnection);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "encryptedPassword", ignore = true) // Set manually
    @Mapping(target = "createdBy", ignore = true) // Set manually
    @Mapping(target = "environment", ignore = true) // Set manually
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "includedSchemas", ignore = true)
    @Mapping(target = "excludedTables", ignore = true)
    DbConnection toEntity(DbConnectionRequest request);
}

