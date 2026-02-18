package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.AuditEntryResponse;
import com.codeops.vault.entity.AuditEntry;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper for AuditEntry entity to DTO conversion.
 */
@Mapper(componentModel = "spring")
public interface AuditMapper {

    /**
     * Maps an AuditEntry entity to an AuditEntryResponse.
     *
     * @param entry The audit entry entity.
     * @return The audit entry response DTO.
     */
    AuditEntryResponse toResponse(AuditEntry entry);

    /**
     * Maps a list of AuditEntry entities to a list of AuditEntryResponse DTOs.
     *
     * @param entries The audit entry entities.
     * @return The list of audit entry response DTOs.
     */
    List<AuditEntryResponse> toResponses(List<AuditEntry> entries);
}
