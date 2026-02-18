package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.SecretResponse;
import com.codeops.vault.dto.response.SecretVersionResponse;
import com.codeops.vault.entity.Secret;
import com.codeops.vault.entity.SecretVersion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Map;

/**
 * MapStruct mapper for Secret entity to DTO conversion.
 *
 * <p>Handles mapping of {@link Secret} entities to {@link SecretResponse} DTOs
 * and {@link SecretVersion} entities to {@link SecretVersionResponse} DTOs.
 * Metadata is passed as a separate parameter since it is stored in a
 * related entity rather than directly on the Secret.</p>
 */
@Mapper(componentModel = "spring")
public interface SecretMapper {

    /**
     * Maps a Secret entity to a SecretResponse, including metadata from related SecretMetadata entities.
     *
     * @param secret   The secret entity.
     * @param metadata The metadata key-value pairs collected from SecretMetadata entities.
     * @return The secret response DTO.
     */
    @Mapping(source = "secret.id", target = "id")
    @Mapping(source = "secret.teamId", target = "teamId")
    @Mapping(source = "secret.path", target = "path")
    @Mapping(source = "secret.name", target = "name")
    @Mapping(source = "secret.description", target = "description")
    @Mapping(source = "secret.secretType", target = "secretType")
    @Mapping(source = "secret.currentVersion", target = "currentVersion")
    @Mapping(source = "secret.maxVersions", target = "maxVersions")
    @Mapping(source = "secret.retentionDays", target = "retentionDays")
    @Mapping(source = "secret.expiresAt", target = "expiresAt")
    @Mapping(source = "secret.lastAccessedAt", target = "lastAccessedAt")
    @Mapping(source = "secret.lastRotatedAt", target = "lastRotatedAt")
    @Mapping(source = "secret.ownerUserId", target = "ownerUserId")
    @Mapping(source = "secret.referenceArn", target = "referenceArn")
    @Mapping(source = "secret.isActive", target = "isActive")
    @Mapping(source = "metadata", target = "metadata")
    @Mapping(source = "secret.createdAt", target = "createdAt")
    @Mapping(source = "secret.updatedAt", target = "updatedAt")
    SecretResponse toResponse(Secret secret, Map<String, String> metadata);

    /**
     * Maps a SecretVersion entity to a SecretVersionResponse.
     *
     * @param version The secret version entity.
     * @return The secret version response DTO.
     */
    @Mapping(source = "secret.id", target = "secretId")
    SecretVersionResponse toVersionResponse(SecretVersion version);

    /**
     * Maps a list of SecretVersion entities to a list of SecretVersionResponse DTOs.
     *
     * @param versions The secret version entities.
     * @return The list of secret version response DTOs.
     */
    List<SecretVersionResponse> toVersionResponses(List<SecretVersion> versions);
}
