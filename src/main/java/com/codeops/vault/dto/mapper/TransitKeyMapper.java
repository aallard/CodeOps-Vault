package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.TransitKeyResponse;
import com.codeops.vault.entity.TransitKey;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper for TransitKey entity to DTO conversion.
 *
 * <p>Maps {@link TransitKey} entities to {@link TransitKeyResponse} DTOs.
 * Key material is NEVER included in the response — only metadata fields
 * are mapped.</p>
 */
@Mapper(componentModel = "spring")
public interface TransitKeyMapper {

    /**
     * Maps a TransitKey entity to a TransitKeyResponse (NEVER includes key material).
     *
     * @param key The transit key entity.
     * @return The transit key response DTO.
     */
    TransitKeyResponse toResponse(TransitKey key);

    /**
     * Maps a list of TransitKey entities to a list of TransitKeyResponse DTOs.
     *
     * @param keys The transit key entities.
     * @return The list of transit key response DTOs.
     */
    List<TransitKeyResponse> toResponses(List<TransitKey> keys);
}
