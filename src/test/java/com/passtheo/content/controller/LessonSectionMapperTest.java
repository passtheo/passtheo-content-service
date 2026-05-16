package com.passtheo.content.controller;

import com.passtheo.content.dto.response.LessonDto.LessonSectionDto;
import com.passtheo.content.integration.strapi.dto.StrapiLessonDto.RoadSignRefDto;
import com.passtheo.content.integration.strapi.dto.StrapiLessonDto.SectionDto;
import com.passtheo.content.integration.strapi.dto.StrapiLessonDto.SectionImageDto;
import com.passtheo.content.integration.strapi.dto.StrapiMediaDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the {@code roadSigns} list semantics + image unwrap on
 * {@link ContentController#toLessonSectionDto}. Particularly the
 * "never null, always a list" contract that the public API depends on.
 */
class LessonSectionMapperTest {

    @Test
    void toLessonSectionDto_nullRoadSigns_returnsEmptyList() {
        SectionDto s = new SectionDto("h", "b", null, null, null, null, null);
        LessonSectionDto out = ContentController.toLessonSectionDto(s);
        assertThat(out.roadSigns()).isNotNull().isEmpty();
    }

    @Test
    void toLessonSectionDto_emptyRoadSigns_returnsEmptyList() {
        SectionDto s = new SectionDto("h", "b", null, null, null, null, List.of());
        LessonSectionDto out = ContentController.toLessonSectionDto(s);
        assertThat(out.roadSigns()).isNotNull().isEmpty();
    }

    @Test
    void toLessonSectionDto_multipleRoadSigns_preservesOrderAndProjectsCodeName() {
        List<RoadSignRefDto> signs = List.of(
                new RoadSignRefDto(1, "doc-1", "B6", "Voorrang verlenen"),
                new RoadSignRefDto(2, "doc-2", "B7", "Stoppen"));
        SectionDto s = new SectionDto("h", "b", null, null, null, null, signs);

        LessonSectionDto out = ContentController.toLessonSectionDto(s);

        assertThat(out.roadSigns()).hasSize(2);
        assertThat(out.roadSigns().get(0).code()).isEqualTo("B6");
        assertThat(out.roadSigns().get(0).name()).isEqualTo("Voorrang verlenen");
        assertThat(out.roadSigns().get(1).code()).isEqualTo("B7");
        assertThat(out.roadSigns().get(1).name()).isEqualTo("Stoppen");
    }

    @Test
    void toLessonSectionDto_listWithNullEntry_filtersOutNull() {
        // Defensive: Strapi normally doesn't return null entries in a populate
        // list, but we cannot rely on it — the mapper must not NPE.
        List<RoadSignRefDto> signs = Arrays.asList(
                new RoadSignRefDto(1, "doc-1", "B1", "Voorrangsweg"),
                null,
                new RoadSignRefDto(2, "doc-2", "B4", "Einde voorrangsweg"));
        SectionDto s = new SectionDto("h", "b", null, null, null, null, signs);

        LessonSectionDto out = ContentController.toLessonSectionDto(s);

        assertThat(out.roadSigns()).hasSize(2);
        assertThat(out.roadSigns()).extracting(LessonDtoRoadSignCode::of).containsExactly("B1", "B4");
    }

    @Test
    void toLessonSectionDto_imageWithFile_unwrapsUrl() {
        StrapiMediaDto media = new StrapiMediaDto(7, "/uploads/foo.png", "Alt fallback", 800, 600);
        SectionImageDto image = new SectionImageDto(media, "Caption text", "Alt text");
        SectionDto s = new SectionDto("h", "b", null, null, null, image, null);

        LessonSectionDto out = ContentController.toLessonSectionDto(s);

        assertThat(out.image()).isNotNull();
        assertThat(out.image().url()).isEqualTo("/uploads/foo.png");
        assertThat(out.image().caption()).isEqualTo("Caption text");
        assertThat(out.image().alt()).isEqualTo("Alt text");
    }

    @Test
    void toLessonSectionDto_imageWithoutFile_returnsNullImage() {
        // Edge case: image component present in Strapi but the `file` slot is
        // unset (admin started editing then bailed). The mapper guards on it.
        SectionImageDto image = new SectionImageDto(null, "caption", "alt");
        SectionDto s = new SectionDto("h", "b", null, null, null, image, null);

        LessonSectionDto out = ContentController.toLessonSectionDto(s);

        assertThat(out.image()).isNull();
    }

    @Test
    void toLessonSectionDto_returnedListIsImmutable() {
        // Stream.toList() returns an unmodifiable list — clients (Jackson +
        // Flutter) read-only this; mutation attempts should fail fast.
        List<RoadSignRefDto> signs = List.of(new RoadSignRefDto(1, "d", "B1", "n"));
        SectionDto s = new SectionDto("h", "b", null, null, null, null, signs);

        LessonSectionDto out = ContentController.toLessonSectionDto(s);

        List<com.passtheo.content.dto.response.LessonDto.RoadSignRefDto> result = out.roadSigns();
        assertThatThrownBy(() -> result.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** Small adapter to extract the public RoadSignRefDto code via method reference. */
    private interface LessonDtoRoadSignCode {
        static String of(com.passtheo.content.dto.response.LessonDto.RoadSignRefDto r) {
            return r.code();
        }
    }
}
