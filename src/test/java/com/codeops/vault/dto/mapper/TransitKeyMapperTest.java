package com.codeops.vault.dto.mapper;

import com.codeops.vault.dto.response.TransitKeyResponse;
import com.codeops.vault.entity.TransitKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TransitKeyMapper} MapStruct implementation.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransitKeyMapperTest {

    @Autowired
    private TransitKeyMapper transitKeyMapper;

    @Test
    void toResponse_mapsAllFields_keyMaterialExcluded() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID createdByUserId = UUID.randomUUID();
        Instant now = Instant.now();

        TransitKey key = TransitKey.builder()
                .teamId(teamId)
                .name("payment-key")
                .description("Encrypts payment data")
                .currentVersion(2)
                .minDecryptionVersion(1)
                .keyMaterial("[{\"version\":1,\"key\":\"secret-key-material\"}]")
                .algorithm("AES-256-GCM")
                .isDeletable(false)
                .isExportable(false)
                .isActive(true)
                .createdByUserId(createdByUserId)
                .build();
        key.setId(id);
        key.setCreatedAt(now);
        key.setUpdatedAt(now);

        TransitKeyResponse response = transitKeyMapper.toResponse(key);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.teamId()).isEqualTo(teamId);
        assertThat(response.name()).isEqualTo("payment-key");
        assertThat(response.description()).isEqualTo("Encrypts payment data");
        assertThat(response.currentVersion()).isEqualTo(2);
        assertThat(response.minDecryptionVersion()).isEqualTo(1);
        assertThat(response.algorithm()).isEqualTo("AES-256-GCM");
        assertThat(response.isDeletable()).isFalse();
        assertThat(response.isExportable()).isFalse();
        assertThat(response.isActive()).isTrue();
        assertThat(response.createdByUserId()).isEqualTo(createdByUserId);
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.updatedAt()).isEqualTo(now);
    }

    @Test
    void toResponses_mapsList() {
        TransitKey k1 = TransitKey.builder()
                .teamId(UUID.randomUUID()).name("key-1").keyMaterial("key1").algorithm("AES-256-GCM")
                .currentVersion(1).minDecryptionVersion(1).isDeletable(false).isExportable(false).isActive(true)
                .build();
        k1.setId(UUID.randomUUID());
        k1.setCreatedAt(Instant.now());
        k1.setUpdatedAt(Instant.now());

        TransitKey k2 = TransitKey.builder()
                .teamId(UUID.randomUUID()).name("key-2").keyMaterial("key2").algorithm("AES-256-GCM")
                .currentVersion(3).minDecryptionVersion(2).isDeletable(true).isExportable(false).isActive(true)
                .build();
        k2.setId(UUID.randomUUID());
        k2.setCreatedAt(Instant.now());
        k2.setUpdatedAt(Instant.now());

        List<TransitKeyResponse> responses = transitKeyMapper.toResponses(List.of(k1, k2));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).name()).isEqualTo("key-1");
        assertThat(responses.get(1).name()).isEqualTo("key-2");
        assertThat(responses.get(1).isDeletable()).isTrue();
    }

    @Test
    void toResponses_emptyList_returnsEmpty() {
        List<TransitKeyResponse> responses = transitKeyMapper.toResponses(List.of());
        assertThat(responses).isEmpty();
    }

    @Test
    void toResponse_exportableKey_flagPreserved() {
        TransitKey key = TransitKey.builder()
                .teamId(UUID.randomUUID()).name("exportable-key").keyMaterial("km").algorithm("AES-256-GCM")
                .currentVersion(1).minDecryptionVersion(1).isDeletable(true).isExportable(true).isActive(true)
                .build();
        key.setId(UUID.randomUUID());
        key.setCreatedAt(Instant.now());
        key.setUpdatedAt(Instant.now());

        TransitKeyResponse response = transitKeyMapper.toResponse(key);

        assertThat(response.isDeletable()).isTrue();
        assertThat(response.isExportable()).isTrue();
    }
}
