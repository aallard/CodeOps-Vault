package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.RotationHistoryResponse;
import com.codeops.vault.dto.response.RotationPolicyResponse;
import com.codeops.vault.entity.RotationHistory;
import com.codeops.vault.entity.RotationPolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for RotationPolicy and RotationHistory entities to DTO conversion.
 *
 * <p>The secret path is passed as a separate parameter since the RotationPolicy
 * entity holds a reference to the Secret entity, and the path needs to be
 * resolved and passed explicitly.</p>
 */
@Mapper(componentModel = "spring")
public interface RotationMapper {

    /**
     * Maps a RotationPolicy entity to a RotationPolicyResponse with the secret's path.
     *
     * @param policy     The rotation policy entity.
     * @param secretPath The path of the secret this policy applies to.
     * @return The rotation policy response DTO.
     */
    @Mapping(source = "policy.id", target = "id")
    @Mapping(source = "policy.secret.id", target = "secretId")
    @Mapping(source = "secretPath", target = "secretPath")
    @Mapping(source = "policy.strategy", target = "strategy")
    @Mapping(source = "policy.rotationIntervalHours", target = "rotationIntervalHours")
    @Mapping(source = "policy.randomLength", target = "randomLength")
    @Mapping(source = "policy.randomCharset", target = "randomCharset")
    @Mapping(source = "policy.externalApiUrl", target = "externalApiUrl")
    @Mapping(source = "policy.isActive", target = "isActive")
    @Mapping(source = "policy.failureCount", target = "failureCount")
    @Mapping(source = "policy.maxFailures", target = "maxFailures")
    @Mapping(source = "policy.lastRotatedAt", target = "lastRotatedAt")
    @Mapping(source = "policy.nextRotationAt", target = "nextRotationAt")
    @Mapping(source = "policy.createdAt", target = "createdAt")
    @Mapping(source = "policy.updatedAt", target = "updatedAt")
    RotationPolicyResponse toResponse(RotationPolicy policy, String secretPath);

    /**
     * Maps a RotationHistory entity to a RotationHistoryResponse.
     *
     * @param history The rotation history entity.
     * @return The rotation history response DTO.
     */
    RotationHistoryResponse toHistoryResponse(RotationHistory history);

    /**
     * Maps a list of RotationHistory entities to a list of RotationHistoryResponse DTOs.
     *
     * @param histories The rotation history entities.
     * @return The list of rotation history response DTOs.
     */
    List<RotationHistoryResponse> toHistoryResponses(List<RotationHistory> histories);
}
