package com.company.hr.attendance.attendance;

import com.company.hr.attendance.attendance.application.AttendanceException;
import com.company.hr.attendance.attendance.application.TimemarkReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimemarkReaderTest {
    private final TimemarkReader reader = new TimemarkReader(new ObjectMapper(), "", "gemini-2.5-flash-lite", 4_000);

    @Test
    void read_rejectsUnsupportedImageTypeBeforeCallingAi() {
        var image = new MockMultipartFile("image", "timemark.gif", "image/gif", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> reader.read(image))
            .isInstanceOfSatisfying(AttendanceException.class, error -> {
                assertThat(error.getCode()).isEqualTo("TIMEMARK_IMAGE_INVALID");
                assertThat(error.getMessage()).contains("JPG, PNG hoặc WEBP");
            });
    }

    @Test
    void read_rejectsOversizedImageBeforeCallingAi() {
        byte[] large = new byte[5 * 1024 * 1024 + 1];
        var image = new MockMultipartFile("image", "timemark.jpg", "image/jpeg", large);

        assertThatThrownBy(() -> reader.read(image))
            .isInstanceOfSatisfying(AttendanceException.class, error -> {
                assertThat(error.getCode()).isEqualTo("TIMEMARK_IMAGE_INVALID");
                assertThat(error.getMessage()).contains("không vượt quá 5 MB");
            });
    }

    @Test
    void read_reportsMissingAiConfigurationWithoutSendingImage() {
        var image = new MockMultipartFile("image", "timemark.jpg", "image/jpeg", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> reader.read(image))
            .isInstanceOfSatisfying(AttendanceException.class, error -> {
                assertThat(error.getCode()).isEqualTo("TIMEMARK_AI_NOT_CONFIGURED");
                assertThat(error.getStatus().value()).isEqualTo(503);
            });
    }
}
