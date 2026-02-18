package com.codeops.vault.service;

import com.codeops.vault.dto.mapper.TransitKeyMapper;
import com.codeops.vault.dto.request.CreateTransitKeyRequest;
import com.codeops.vault.dto.request.TransitDecryptRequest;
import com.codeops.vault.dto.request.TransitEncryptRequest;
import com.codeops.vault.dto.request.TransitRewrapRequest;
import com.codeops.vault.dto.request.UpdateTransitKeyRequest;
import com.codeops.vault.dto.response.PageResponse;
import com.codeops.vault.dto.response.TransitDecryptResponse;
import com.codeops.vault.dto.response.TransitEncryptResponse;
import com.codeops.vault.dto.response.TransitKeyResponse;
import com.codeops.vault.entity.TransitKey;
import com.codeops.vault.exception.NotFoundException;
import com.codeops.vault.exception.ValidationException;
import com.codeops.vault.repository.TransitKeyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransitService}.
 *
 * <p>Covers key management, encrypt/decrypt/rewrap operations,
 * key material management, and statistics.</p>
 */
@ExtendWith(MockitoExtension.class)
class TransitServiceTest {

    @Mock
    private TransitKeyRepository transitKeyRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private TransitKeyMapper transitKeyMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TransitService transitService;

    // ─── Key Management ─────────────────────────────────────

    @Test
    void createKey_success_generatesVersion1() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateTransitKeyRequest request = new CreateTransitKeyRequest(
                "payment-key", "For payment data", null, false, false);

        byte[] rawKey = new byte[32];
        rawKey[0] = 1;

        when(transitKeyRepository.existsByTeamIdAndName(teamId, "payment-key")).thenReturn(false);
        when(encryptionService.generateDataKey()).thenReturn(rawKey);
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-material");
        when(transitKeyRepository.save(any(TransitKey.class))).thenAnswer(i -> {
            TransitKey k = i.getArgument(0);
            k.setId(UUID.randomUUID());
            return k;
        });
        TransitKeyResponse expectedResponse = buildKeyResponse("payment-key", 1);
        when(transitKeyMapper.toResponse(any(TransitKey.class))).thenReturn(expectedResponse);

        TransitKeyResponse result = transitService.createKey(request, teamId, userId);

        assertThat(result).isEqualTo(expectedResponse);
        assertThat(result.currentVersion()).isEqualTo(1);

        ArgumentCaptor<TransitKey> captor = ArgumentCaptor.forClass(TransitKey.class);
        verify(transitKeyRepository).save(captor.capture());
        TransitKey saved = captor.getValue();
        assertThat(saved.getCurrentVersion()).isEqualTo(1);
        assertThat(saved.getKeyMaterial()).isEqualTo("encrypted-material");
        assertThat(saved.getAlgorithm()).isEqualTo("AES-256-GCM");

        // Verify the key material JSON was encrypted
        verify(encryptionService).encrypt(argThat(json -> json.contains("\"version\":1") && json.contains("\"key\":")));
    }

    @Test
    void createKey_duplicateName_throwsValidation() {
        UUID teamId = UUID.randomUUID();
        CreateTransitKeyRequest request = new CreateTransitKeyRequest(
                "payment-key", null, null, false, false);

        when(transitKeyRepository.existsByTeamIdAndName(teamId, "payment-key")).thenReturn(true);

        assertThatThrownBy(() -> transitService.createKey(request, teamId, UUID.randomUUID()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("payment-key");
    }

    @Test
    void getKeyById_returnsWithoutKeyMaterial() {
        UUID keyId = UUID.randomUUID();
        TransitKey key = buildTransitKey(keyId, "test-key", 1);

        when(transitKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        TransitKeyResponse response = buildKeyResponse("test-key", 1);
        when(transitKeyMapper.toResponse(key)).thenReturn(response);

        TransitKeyResponse result = transitService.getKeyById(keyId);

        assertThat(result.name()).isEqualTo("test-key");
        // TransitKeyResponse does not have a keyMaterial field — verified by compile
    }

    @Test
    void getKeyByName_exists_returns() {
        UUID teamId = UUID.randomUUID();
        TransitKey key = buildTransitKey(UUID.randomUUID(), "my-key", 1);

        when(transitKeyRepository.findByTeamIdAndName(teamId, "my-key")).thenReturn(Optional.of(key));
        TransitKeyResponse response = buildKeyResponse("my-key", 1);
        when(transitKeyMapper.toResponse(key)).thenReturn(response);

        TransitKeyResponse result = transitService.getKeyByName(teamId, "my-key");

        assertThat(result.name()).isEqualTo("my-key");
    }

    @Test
    void listKeys_activeOnly_filtersCorrectly() {
        UUID teamId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        TransitKey activeKey = buildTransitKey(UUID.randomUUID(), "active-key", 1);
        Page<TransitKey> page = new PageImpl<>(List.of(activeKey), pageable, 1);

        when(transitKeyRepository.findByTeamIdAndIsActiveTrue(teamId, pageable)).thenReturn(page);
        TransitKeyResponse response = buildKeyResponse("active-key", 1);
        when(transitKeyMapper.toResponse(activeKey)).thenReturn(response);

        PageResponse<TransitKeyResponse> result = transitService.listKeys(teamId, true, pageable);

        assertThat(result.content()).hasSize(1);
        verify(transitKeyRepository).findByTeamIdAndIsActiveTrue(teamId, pageable);
        verify(transitKeyRepository, never()).findByTeamId(eq(teamId), any(Pageable.class));
    }

    @Test
    void updateKey_partialFields_onlyUpdatesProvided() {
        UUID keyId = UUID.randomUUID();
        TransitKey key = buildTransitKey(keyId, "test-key", 1);

        UpdateTransitKeyRequest request = new UpdateTransitKeyRequest(
                "Updated description", null, true, null, null);

        when(transitKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(transitKeyRepository.save(any(TransitKey.class))).thenAnswer(i -> i.getArgument(0));
        TransitKeyResponse response = buildKeyResponse("test-key", 1);
        when(transitKeyMapper.toResponse(any(TransitKey.class))).thenReturn(response);

        transitService.updateKey(keyId, request);

        assertThat(key.getDescription()).isEqualTo("Updated description");
        assertThat(key.getIsDeletable()).isTrue();
        // minDecryptionVersion should remain unchanged
        assertThat(key.getMinDecryptionVersion()).isEqualTo(1);
    }

    @Test
    void rotateKey_incrementsVersion() {
        UUID keyId = UUID.randomUUID();
        TransitKey key = buildTransitKey(keyId, "rotate-key", 1);

        // Set up key material with version 1
        byte[] rawKey = new byte[32];
        String base64Key = Base64.getEncoder().encodeToString(rawKey);
        String keyMaterialJson = "[{\"version\":1,\"key\":\"" + base64Key + "\"}]";

        when(transitKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(encryptionService.decrypt(key.getKeyMaterial())).thenReturn(keyMaterialJson);
        byte[] newRawKey = new byte[32];
        newRawKey[0] = 2;
        when(encryptionService.generateDataKey()).thenReturn(newRawKey);
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-v2");
        when(transitKeyRepository.save(any(TransitKey.class))).thenAnswer(i -> i.getArgument(0));
        TransitKeyResponse response = buildKeyResponse("rotate-key", 2);
        when(transitKeyMapper.toResponse(any(TransitKey.class))).thenReturn(response);

        TransitKeyResponse result = transitService.rotateKey(keyId);

        assertThat(result.currentVersion()).isEqualTo(2);
        assertThat(key.getCurrentVersion()).isEqualTo(2);

        // Verify the new material includes both version 1 and 2
        verify(encryptionService).encrypt(argThat(json ->
                json.contains("\"version\":1") && json.contains("\"version\":2")));
    }

    @Test
    void deleteKey_notDeletable_throwsValidation() {
        UUID keyId = UUID.randomUUID();
        TransitKey key = buildTransitKey(keyId, "no-delete-key", 1);
        key.setIsDeletable(false);

        when(transitKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> transitService.deleteKey(keyId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not deletable");
    }

    // ─── Encrypt / Decrypt ──────────────────────────────────

    @Test
    void encrypt_decrypt_roundTrip() {
        UUID teamId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        TransitKey key = buildTransitKey(keyId, "round-trip-key", 1);

        byte[] rawKey = new byte[32];
        String base64Key = Base64.getEncoder().encodeToString(rawKey);
        String keyMaterialJson = "[{\"version\":1,\"key\":\"" + base64Key + "\"}]";

        // Encrypt
        TransitEncryptRequest encReq = new TransitEncryptRequest("round-trip-key", "SGVsbG8gV29ybGQ=");
        when(transitKeyRepository.findByTeamIdAndName(teamId, "round-trip-key")).thenReturn(Optional.of(key));
        when(encryptionService.decrypt(key.getKeyMaterial())).thenReturn(keyMaterialJson);
        when(encryptionService.encryptWithKey(eq("SGVsbG8gV29ybGQ="), eq("round-trip-key:v1"), eq(rawKey)))
                .thenReturn("encrypted-result");

        TransitEncryptResponse encResult = transitService.encrypt(encReq, teamId);
        assertThat(encResult.ciphertext()).isEqualTo("encrypted-result");

        // Decrypt
        TransitDecryptRequest decReq = new TransitDecryptRequest("round-trip-key", "encrypted-result");
        when(encryptionService.extractKeyId("encrypted-result")).thenReturn("round-trip-key:v1");
        when(encryptionService.decryptWithKey("encrypted-result", rawKey)).thenReturn("SGVsbG8gV29ybGQ=");

        TransitDecryptResponse decResult = transitService.decrypt(decReq, teamId);
        assertThat(decResult.plaintext()).isEqualTo("SGVsbG8gV29ybGQ=");
    }

    @Test
    void encrypt_usesCurrentVersion() {
        UUID teamId = UUID.randomUUID();
        TransitKey key = buildTransitKey(UUID.randomUUID(), "versioned-key", 2);

        byte[] rawKey1 = new byte[32];
        byte[] rawKey2 = new byte[32];
        rawKey2[0] = 2;
        String b64Key1 = Base64.getEncoder().encodeToString(rawKey1);
        String b64Key2 = Base64.getEncoder().encodeToString(rawKey2);
        String keyMaterialJson = "[{\"version\":1,\"key\":\"" + b64Key1 + "\"},{\"version\":2,\"key\":\"" + b64Key2 + "\"}]";

        TransitEncryptRequest request = new TransitEncryptRequest("versioned-key", "data");
        when(transitKeyRepository.findByTeamIdAndName(teamId, "versioned-key")).thenReturn(Optional.of(key));
        when(encryptionService.decrypt(key.getKeyMaterial())).thenReturn(keyMaterialJson);
        when(encryptionService.encryptWithKey(eq("data"), eq("versioned-key:v2"), eq(rawKey2)))
                .thenReturn("ciphertext-v2");

        TransitEncryptResponse result = transitService.encrypt(request, teamId);

        assertThat(result.keyVersion()).isEqualTo(2);
        // Must use version 2 key, not version 1
        verify(encryptionService).encryptWithKey("data", "versioned-key:v2", rawKey2);
    }

    @Test
    void decrypt_olderVersion_works() {
        UUID teamId = UUID.randomUUID();
        TransitKey key = buildTransitKey(UUID.randomUUID(), "rotated-key", 2);

        byte[] rawKey1 = new byte[32];
        rawKey1[0] = 1;
        byte[] rawKey2 = new byte[32];
        rawKey2[0] = 2;
        String b64Key1 = Base64.getEncoder().encodeToString(rawKey1);
        String b64Key2 = Base64.getEncoder().encodeToString(rawKey2);
        String keyMaterialJson = "[{\"version\":1,\"key\":\"" + b64Key1 + "\"},{\"version\":2,\"key\":\"" + b64Key2 + "\"}]";

        TransitDecryptRequest request = new TransitDecryptRequest("rotated-key", "old-ciphertext");
        when(transitKeyRepository.findByTeamIdAndName(teamId, "rotated-key")).thenReturn(Optional.of(key));
        when(encryptionService.extractKeyId("old-ciphertext")).thenReturn("rotated-key:v1");
        when(encryptionService.decrypt(key.getKeyMaterial())).thenReturn(keyMaterialJson);
        when(encryptionService.decryptWithKey("old-ciphertext", rawKey1)).thenReturn("decrypted-data");

        TransitDecryptResponse result = transitService.decrypt(request, teamId);

        assertThat(result.plaintext()).isEqualTo("decrypted-data");
        verify(encryptionService).decryptWithKey("old-ciphertext", rawKey1);
    }

    @Test
    void decrypt_belowMinVersion_throwsValidation() {
        UUID teamId = UUID.randomUUID();
        TransitKey key = buildTransitKey(UUID.randomUUID(), "min-ver-key", 3);
        key.setMinDecryptionVersion(2);

        TransitDecryptRequest request = new TransitDecryptRequest("min-ver-key", "old-ciphertext");
        when(transitKeyRepository.findByTeamIdAndName(teamId, "min-ver-key")).thenReturn(Optional.of(key));
        when(encryptionService.extractKeyId("old-ciphertext")).thenReturn("min-ver-key:v1");

        assertThatThrownBy(() -> transitService.decrypt(request, teamId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("below minimum decryption version");
    }

    @Test
    void decrypt_inactiveKey_throwsNotFound() {
        UUID teamId = UUID.randomUUID();

        TransitDecryptRequest request = new TransitDecryptRequest("inactive-key", "ciphertext");
        when(transitKeyRepository.findByTeamIdAndName(teamId, "inactive-key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transitService.decrypt(request, teamId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("inactive-key");
    }

    @Test
    void rewrap_producesNewCiphertextSamePlaintext() {
        UUID teamId = UUID.randomUUID();
        TransitKey key = buildTransitKey(UUID.randomUUID(), "rewrap-key", 2);

        byte[] rawKey1 = new byte[32];
        rawKey1[0] = 1;
        byte[] rawKey2 = new byte[32];
        rawKey2[0] = 2;
        String b64Key1 = Base64.getEncoder().encodeToString(rawKey1);
        String b64Key2 = Base64.getEncoder().encodeToString(rawKey2);
        String keyMaterialJson = "[{\"version\":1,\"key\":\"" + b64Key1 + "\"},{\"version\":2,\"key\":\"" + b64Key2 + "\"}]";

        TransitRewrapRequest request = new TransitRewrapRequest("rewrap-key", "old-ciphertext-v1");
        when(transitKeyRepository.findByTeamIdAndName(teamId, "rewrap-key")).thenReturn(Optional.of(key));
        when(encryptionService.extractKeyId("old-ciphertext-v1")).thenReturn("rewrap-key:v1");
        when(encryptionService.decrypt(key.getKeyMaterial())).thenReturn(keyMaterialJson);
        when(encryptionService.rewrap("old-ciphertext-v1", rawKey1, rawKey2, "rewrap-key:v2"))
                .thenReturn("new-ciphertext-v2");

        TransitEncryptResponse result = transitService.rewrap(request, teamId);

        assertThat(result.ciphertext()).isEqualTo("new-ciphertext-v2");
        verify(encryptionService).rewrap("old-ciphertext-v1", rawKey1, rawKey2, "rewrap-key:v2");
    }

    @Test
    void rewrap_updatesToCurrentVersion() {
        UUID teamId = UUID.randomUUID();
        TransitKey key = buildTransitKey(UUID.randomUUID(), "rewrap-key", 3);

        byte[] rawKey = new byte[32];
        String b64Key = Base64.getEncoder().encodeToString(rawKey);
        String keyMaterialJson = "[{\"version\":1,\"key\":\"" + b64Key + "\"},{\"version\":2,\"key\":\"" + b64Key + "\"},{\"version\":3,\"key\":\"" + b64Key + "\"}]";

        TransitRewrapRequest request = new TransitRewrapRequest("rewrap-key", "ciphertext");
        when(transitKeyRepository.findByTeamIdAndName(teamId, "rewrap-key")).thenReturn(Optional.of(key));
        when(encryptionService.extractKeyId("ciphertext")).thenReturn("rewrap-key:v1");
        when(encryptionService.decrypt(key.getKeyMaterial())).thenReturn(keyMaterialJson);
        when(encryptionService.rewrap(eq("ciphertext"), eq(rawKey), eq(rawKey), eq("rewrap-key:v3")))
                .thenReturn("rewrapped");

        TransitEncryptResponse result = transitService.rewrap(request, teamId);

        assertThat(result.keyVersion()).isEqualTo(3);
    }

    @Test
    void generateDataKey_returnsBothForms() {
        UUID teamId = UUID.randomUUID();
        TransitKey key = buildTransitKey(UUID.randomUUID(), "gen-key", 1);

        byte[] transitRawKey = new byte[32];
        String b64TransitKey = Base64.getEncoder().encodeToString(transitRawKey);
        String keyMaterialJson = "[{\"version\":1,\"key\":\"" + b64TransitKey + "\"}]";

        byte[] dataKey = new byte[32];
        dataKey[0] = 42;
        String expectedPlaintext = Base64.getEncoder().encodeToString(dataKey);

        when(transitKeyRepository.findByTeamIdAndName(teamId, "gen-key")).thenReturn(Optional.of(key));
        when(encryptionService.decrypt(key.getKeyMaterial())).thenReturn(keyMaterialJson);
        when(encryptionService.generateDataKey()).thenReturn(dataKey);
        when(encryptionService.encryptWithKey(eq(expectedPlaintext), eq("gen-key:v1"), eq(transitRawKey)))
                .thenReturn("wrapped-data-key");

        Map<String, String> result = transitService.generateDataKey("gen-key", teamId);

        assertThat(result).containsKey("plaintextKey");
        assertThat(result).containsKey("ciphertextKey");
        assertThat(result.get("plaintextKey")).isEqualTo(expectedPlaintext);
        assertThat(result.get("ciphertextKey")).isEqualTo("wrapped-data-key");
    }

    // ─── Key Material Management ────────────────────────────

    @Test
    void loadKeyMaterial_decryptsCorrectly() {
        TransitKey key = buildTransitKey(UUID.randomUUID(), "load-key", 2);

        byte[] rawKey = new byte[32];
        String b64Key = Base64.getEncoder().encodeToString(rawKey);
        String keyMaterialJson = "[{\"version\":1,\"key\":\"" + b64Key + "\"},{\"version\":2,\"key\":\"" + b64Key + "\"}]";

        when(encryptionService.decrypt(key.getKeyMaterial())).thenReturn(keyMaterialJson);

        List<KeyVersion> result = transitService.loadKeyMaterial(key);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).version()).isEqualTo(1);
        assertThat(result.get(1).version()).isEqualTo(2);
    }

    @Test
    void getKeyForVersion_validVersion_returnsBytes() {
        TransitKey key = buildTransitKey(UUID.randomUUID(), "version-key", 1);

        byte[] rawKey = new byte[32];
        rawKey[0] = 99;
        String b64Key = Base64.getEncoder().encodeToString(rawKey);
        String keyMaterialJson = "[{\"version\":1,\"key\":\"" + b64Key + "\"}]";

        when(encryptionService.decrypt(key.getKeyMaterial())).thenReturn(keyMaterialJson);

        byte[] result = transitService.getKeyForVersion(key, 1);

        assertThat(result).hasSize(32);
        assertThat(result[0]).isEqualTo((byte) 99);
    }

    @Test
    void getKeyForVersion_invalidVersion_throwsNotFound() {
        TransitKey key = buildTransitKey(UUID.randomUUID(), "version-key", 1);

        byte[] rawKey = new byte[32];
        String b64Key = Base64.getEncoder().encodeToString(rawKey);
        String keyMaterialJson = "[{\"version\":1,\"key\":\"" + b64Key + "\"}]";

        when(encryptionService.decrypt(key.getKeyMaterial())).thenReturn(keyMaterialJson);

        assertThatThrownBy(() -> transitService.getKeyForVersion(key, 5))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("version");
    }

    @Test
    void getKeyForVersion_belowMinVersion_throwsValidation() {
        TransitKey key = buildTransitKey(UUID.randomUUID(), "min-key", 2);
        key.setMinDecryptionVersion(2);

        assertThatThrownBy(() -> transitService.getKeyForVersion(key, 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("below minimum");
    }

    // ─── Helpers ────────────────────────────────────────────

    private TransitKey buildTransitKey(UUID keyId, String name, int currentVersion) {
        TransitKey key = TransitKey.builder()
                .teamId(UUID.randomUUID())
                .name(name)
                .description("Test key")
                .currentVersion(currentVersion)
                .minDecryptionVersion(1)
                .keyMaterial("encrypted-key-material")
                .algorithm("AES-256-GCM")
                .isDeletable(false)
                .isExportable(false)
                .isActive(true)
                .createdByUserId(UUID.randomUUID())
                .build();
        key.setId(keyId);
        return key;
    }

    private TransitKeyResponse buildKeyResponse(String name, int currentVersion) {
        return new TransitKeyResponse(
                UUID.randomUUID(), UUID.randomUUID(), name, "Test key",
                currentVersion, 1, "AES-256-GCM",
                false, false, true, UUID.randomUUID(),
                Instant.now(), Instant.now());
    }
}
