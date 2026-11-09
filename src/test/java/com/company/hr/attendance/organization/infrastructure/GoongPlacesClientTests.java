package com.company.hr.attendance.organization.infrastructure;

import com.company.hr.attendance.organization.application.WorkplaceException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GoongPlacesClientTests {
    final RestClient.Builder builder = RestClient.builder().baseUrl("https://rsapi.goong.io");
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final GoongPlacesClient client = new GoongPlacesClient(builder.build(), "fake-test-rest-key");
    @Test void searchesV2WithEncodedInputAndReturnsOnlySafeSuggestionFields() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://rsapi.goong.io/v2/place/autocomplete?")))
            .andExpect(decodedQuery("input", "Đại học & Bách Khoa"))
            .andExpect(queryParam("api_key", "fake-test-rest-key")).andExpect(queryParam("limit", "5"))
            .andRespond(withSuccess("""
                {"status":"OK","predictions":[{"place_id":"id/+=","description":"Đại học Bách Khoa","secret":"never-forward"}]}
                """, MediaType.APPLICATION_JSON));
        assertThat(client.search("Đại học & Bách Khoa")).containsExactly(
            new com.company.hr.attendance.organization.api.PlaceModels.Suggestion("id/+=", "Đại học Bách Khoa"));
        server.verify();
    }
    @Test void detailPreservesOpaqueIdsAndReturnsValidatedCoordinates() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://rsapi.goong.io/v2/place/detail?")))
            .andExpect(decodedQuery("place_id", "id/+=&value"))
            .andRespond(withSuccess("""
                {"status":"OK","result":{"formatted_address":"268 Lý Thường Kiệt","geometry":{"location":{"lat":10.7726,"lng":106.6579}}}}
                """, MediaType.APPLICATION_JSON));
        var place = client.detail("id/+=&value");
        assertThat(place.placeId()).isEqualTo("id/+=&value");
        assertThat(place.latitude()).isEqualTo(10.7726); assertThat(place.longitude()).isEqualTo(106.6579);
        server.verify();
    }
    @Test void zeroResultsAreAnEmptyList() {
        server.expect(anything()).andRespond(withSuccess("{\"status\":\"ZERO_RESULTS\",\"predictions\":[]}", MediaType.APPLICATION_JSON));
        assertThat(client.search("Không tồn tại")).isEmpty();
    }
    @Test void missingKeyDoesNotCallTheProvider() {
        var unconfigured = new GoongPlacesClient(builder.build(), " ");
        assertThatThrownBy(() -> unconfigured.search("Địa chỉ"))
            .isInstanceOfSatisfying(WorkplaceException.class, error -> assertThat(error.getCode()).isEqualTo("PLACE_SEARCH_NOT_CONFIGURED"));
        server.verify();
    }
    @Test void providerFailureDoesNotExposeItsBodyKeyOrUrl() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("fake-test-rest-key https://rsapi.goong.io secret"));
        assertThatThrownBy(() -> client.search("Địa chỉ"))
            .isInstanceOf(WorkplaceException.class).hasMessageNotContaining("fake-test-rest-key").hasMessageNotContaining("rsapi.goong.io").hasNoCause();
    }
    @Test void timeoutHasASanitizedError() {
        server.expect(anything()).andRespond(withException(new IOException("timeout with fake-test-rest-key")));
        assertThatThrownBy(() -> client.search("Địa chỉ")).isInstanceOf(WorkplaceException.class).hasMessageNotContaining("fake-test-rest-key");
    }
    @Test void invalidProviderCoordinatesNeverBecomeAWorkplace() {
        server.expect(anything()).andRespond(withSuccess("""
            {"status":"OK","result":{"formatted_address":"Invalid","geometry":{"location":{"lat":91,"lng":106}}}}
            """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.detail("id")).isInstanceOf(WorkplaceException.class);
    }
    @Test void malformedProviderResponseIsNotTreatedAsZeroResults() {
        server.expect(anything()).andRespond(withSuccess("{\"status\":\"OK\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.search("Địa chỉ")).isInstanceOf(WorkplaceException.class);
    }
    private org.springframework.test.web.client.RequestMatcher decodedQuery(String name, String expected) {
        return request -> {
            String raw = org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst(name);
            assertThat(java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(expected);
        };
    }
}
