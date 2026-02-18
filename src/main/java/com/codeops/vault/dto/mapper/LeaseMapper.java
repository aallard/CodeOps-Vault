package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.DynamicLeaseResponse;
import com.codeops.vault.entity.DynamicLease;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

/**
 * MapStruct mapper for DynamicLease entity to DTO conversion.
 *
 * <p>Connection details are passed as a separate parameter since the lease
 * entity stores encrypted credentials, and the decrypted connection details
 * are only provided on lease creation.</p>
 */
@Mapper(componentModel = "spring")
public interface LeaseMapper {

    /**
     * Maps a DynamicLease entity to a DynamicLeaseResponse with connection details.
     *
     * @param lease             The dynamic lease entity.
     * @param connectionDetails The decrypted connection details (only on creation).
     * @return The dynamic lease response DTO.
     */
    @Mapping(source = "lease.id", target = "id")
    @Mapping(source = "lease.leaseId", target = "leaseId")
    @Mapping(source = "lease.secretId", target = "secretId")
    @Mapping(source = "lease.secretPath", target = "secretPath")
    @Mapping(source = "lease.backendType", target = "backendType")
    @Mapping(source = "lease.status", target = "status")
    @Mapping(source = "lease.ttlSeconds", target = "ttlSeconds")
    @Mapping(source = "lease.expiresAt", target = "expiresAt")
    @Mapping(source = "lease.revokedAt", target = "revokedAt")
    @Mapping(source = "lease.requestedByUserId", target = "requestedByUserId")
    @Mapping(source = "connectionDetails", target = "connectionDetails")
    @Mapping(source = "lease.createdAt", target = "createdAt")
    DynamicLeaseResponse toResponse(DynamicLease lease, Map<String, Object> connectionDetails);
}
