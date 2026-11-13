package com.company.hr.attendance.attendance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class TimemarkReaderHttpTest {
    private static final String URL = "https://example.test/v1beta/models/test-model:generateContent";
    private final MockMultipartFile image = new MockMultipartFile("image", "timemark.jpg", "image/jpeg",
        new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
    private MockRestServiceServer server;
    private TimemarkReader reader;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://example.test/v1beta/models");
        server = MockRestServiceServer.bindTo(builder).build();
        reader = new TimemarkReader(new ObjectMapper(), "test-key", "test-model", builder.build());
    }

    @Test
    void googleBadRequestIsReportedAsConfigurationProblemWithoutExposingBody() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key", "test-key"))
            .andExpect(header("Accept", "application/json"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
            .andExpect(jsonPath("$.generationConfig.responseJsonSchema.type").value("object"))
            .andExpect(jsonPath("$.contents[0].parts[1].inlineData.mimeType").value("image/jpeg"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"Unknown name inlineData: sensitive upstream details\"}}"));

        assertFailure("TIMEMARK_AI_REQUEST_REJECTED", 502, "Trường bị từ chối: inlineData");
        server.verify();
    }

    @Test
    void googleFailedPreconditionReportsBillingOrRegion() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
            .body("{\"error\":{\"status\":\"FAILED_PRECONDITION\",\"message\":\"sensitive upstream details\"}}"));

        assertFailure("TIMEMARK_AI_PRECONDITION_FAILED", 503, "billing");
        server.verify();
    }

    @Test
    void invalidApiKeyInBadRequestIsReportedAsAuthFailure() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
            .body("{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}],\"message\":\"sensitive upstream details\"}}"));

        assertFailure("TIMEMARK_AI_AUTH_FAILED", 503, "GEMINI_API_KEY");
        server.verify();
    }

    @Test
    void googleAuthFailureIsReportedSeparately() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertFailure("TIMEMARK_AI_AUTH_FAILED", 503, "GEMINI_API_KEY");
        server.verify();
    }

    @Test
    void missingModelIsReportedSeparately() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFailure("TIMEMARK_AI_MODEL_UNAVAILABLE", 503, "GEMINI_MODEL");
        server.verify();
    }

    @Test
    void quotaFailureIsReportedSeparately() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertFailure("TIMEMARK_AI_QUOTA_EXCEEDED", 503, "hạn mức");
        server.verify();
    }

    @Test
    void malformedGeminiResponseIsReportedWithoutBlamingImage() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"not-json\"}]}}]}"));

        assertFailure("TIMEMARK_AI_OUTPUT_INVALID", 502, "Kết quả đọc ảnh");
        server.verify();
    }

    @Test
    void malformedHttpBodyIsReportedSeparately() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body("not-json"));

        assertFailure("TIMEMARK_AI_RESPONSE_INVALID", 502, "Phản hồi HTTP");
        server.verify();
    }

    @Test
    void jsonBodyWithUnexpectedContentTypeCanStillBeRead() {
        var response = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"readable\\":true,\\"printedDateTime\\":\\"2026-11-13T12:05:00+07:00\\",\\"latitude\\":10.0,\\"longitude\\":106.0,\\"address\\":null,\\"reason\\":null}"}]}}]}
            """;
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.TEXT_PLAIN).body(response));

        assertThat(reader.read(image).readable()).isTrue();
        server.verify();
    }

    @Test
    void successfulResponseReadsPrintedFields() {
        var response = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"readable\\":true,\\"printedDateTime\\":\\"2026-11-13T12:05:00+07:00\\",\\"latitude\\":10.0,\\"longitude\\":106.0,\\"address\\":null,\\"reason\\":null}"}]}}]}
            """;
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response));

        var result = reader.read(image);
        assertThat(result.readable()).isTrue();
        assertThat(result.printedDateTime()).isEqualTo("2026-11-13T12:05:00+07:00");
        assertThat(result.latitude()).isEqualTo(10.0);
        assertThat(result.longitude()).isEqualTo(106.0);
        server.verify();
    }

    private void assertFailure(String code, int httpStatus, String messageFragment) {
        assertThatThrownBy(() -> reader.read(image))
            .isInstanceOfSatisfying(AttendanceException.class, error -> {
                assertThat(error.getCode()).isEqualTo(code);
                assertThat(error.getStatus().value()).isEqualTo(httpStatus);
                assertThat(error.getMessage()).contains(messageFragment).doesNotContain("sensitive upstream details");
            });
    }
}
