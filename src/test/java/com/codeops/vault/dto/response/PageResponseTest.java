package com.codeops.vault.dto.response;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PageResponse} record and factory method.
 */
class PageResponseTest {

    @Test
    void fromPage_mapsAllFields() {
        List<String> content = List.of("alpha", "beta", "gamma");
        Page<String> page = new PageImpl<>(content, PageRequest.of(0, 10), 3);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("alpha", "beta", "gamma");
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.isLast()).isTrue();
    }

    @Test
    void fromPage_multiplePages_correctPagination() {
        List<String> content = List.of("delta", "epsilon");
        Page<String> page = new PageImpl<>(content, PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).hasSize(2);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.isLast()).isFalse();
    }

    @Test
    void fromPage_emptyPage_handledCorrectly() {
        Page<String> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.totalElements()).isEqualTo(0);
        assertThat(response.totalPages()).isEqualTo(0);
        assertThat(response.isLast()).isTrue();
    }

    @Test
    void fromPage_lastPage_isLastTrue() {
        List<String> content = List.of("zeta");
        Page<String> page = new PageImpl<>(content, PageRequest.of(2, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.isLast()).isTrue();
    }
}
