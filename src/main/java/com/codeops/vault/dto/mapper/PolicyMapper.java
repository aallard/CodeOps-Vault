package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.AccessPolicyResponse;
import com.codeops.vault.dto.response.PolicyBindingResponse;
import com.codeops.vault.entity.AccessPolicy;
import com.codeops.vault.entity.PolicyBinding;
import com.codeops.vault.entity.enums.PolicyPermission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * MapStruct mapper for AccessPolicy and PolicyBinding entities to DTO conversion.
 *
 * <p>Handles the conversion of the comma-separated permissions string stored
 * in {@link AccessPolicy} to a {@code List<PolicyPermission>} in the response DTO.</p>
 */
@Mapper(componentModel = "spring")
public interface PolicyMapper {

    /**
     * Maps an AccessPolicy entity to an AccessPolicyResponse with binding count.
     *
     * @param policy       The access policy entity.
     * @param bindingCount The number of bindings attached to this policy.
     * @return The access policy response DTO.
     */
    @Mapping(source = "policy.id", target = "id")
    @Mapping(source = "policy.teamId", target = "teamId")
    @Mapping(source = "policy.name", target = "name")
    @Mapping(source = "policy.description", target = "description")
    @Mapping(source = "policy.pathPattern", target = "pathPattern")
    @Mapping(source = "policy.permissions", target = "permissions", qualifiedByName = "permissionsStringToList")
    @Mapping(source = "policy.isDenyPolicy", target = "isDenyPolicy")
    @Mapping(source = "policy.isActive", target = "isActive")
    @Mapping(source = "policy.createdByUserId", target = "createdByUserId")
    @Mapping(source = "bindingCount", target = "bindingCount")
    @Mapping(source = "policy.createdAt", target = "createdAt")
    @Mapping(source = "policy.updatedAt", target = "updatedAt")
    AccessPolicyResponse toResponse(AccessPolicy policy, int bindingCount);

    /**
     * Maps a PolicyBinding entity to a PolicyBindingResponse with the policy name.
     *
     * @param binding    The policy binding entity.
     * @param policyName The name of the bound policy.
     * @return The policy binding response DTO.
     */
    @Mapping(source = "binding.id", target = "id")
    @Mapping(source = "binding.policy.id", target = "policyId")
    @Mapping(source = "policyName", target = "policyName")
    @Mapping(source = "binding.bindingType", target = "bindingType")
    @Mapping(source = "binding.bindingTargetId", target = "bindingTargetId")
    @Mapping(source = "binding.createdByUserId", target = "createdByUserId")
    @Mapping(source = "binding.createdAt", target = "createdAt")
    PolicyBindingResponse toBindingResponse(PolicyBinding binding, String policyName);

    /**
     * Converts a comma-separated permissions string to a list of PolicyPermission enums.
     *
     * @param permissions The comma-separated string (e.g., "READ,WRITE,LIST").
     * @return The list of PolicyPermission values.
     */
    @Named("permissionsStringToList")
    default List<PolicyPermission> permissionsStringToList(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .map(PolicyPermission::valueOf)
                .toList();
    }
}
