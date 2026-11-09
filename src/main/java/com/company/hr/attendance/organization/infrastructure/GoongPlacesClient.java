package com.company.hr.attendance.organization.infrastructure;

import com.company.hr.attendance.organization.api.PlaceModels.*;
import com.company.hr.attendance.organization.application.WorkplaceException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GoongPlacesClient {
    private final RestClient client;
    private final String apiKey;
    @Autowired
    public GoongPlacesClient(@Value("${app.maps.goong.api-key:}") String apiKey) {
        this(defaultClient(), apiKey);
    }
    GoongPlacesClient(RestClient client, String apiKey) { this.client = client; this.apiKey = apiKey.trim(); }
    private static RestClient defaultClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); factory.setReadTimeout(5000);
        return RestClient.builder().baseUrl("https://rsapi.goong.io").requestFactory(factory).build();
    }
    public List<Suggestion> search(String query) {
        var response = call("/v2/place/autocomplete", "input", query, true);
        if (response.path("status").asText().equals("ZERO_RESULTS")) return List.of();
        var predictions = response.path("predictions");
        if (!predictions.isArray()) throw unavailable();
        var results = new ArrayList<Suggestion>();
        for (var item : predictions) {
            String id = item.path("place_id").asText(""), label = item.path("description").asText("");
            if (!id.isBlank() && !label.isBlank() && id.length() <= 2048) results.add(new Suggestion(id, label));
            if (results.size() == 5) break;
        }
        return List.copyOf(results);
    }
    public Place detail(String placeId) {
        var response = call("/v2/place/detail", "place_id", placeId, false);
        var result = response.path("result");
        var location = result.path("geometry").path("location");
        var lat = location.path("lat"); var lon = location.path("lng");
        String label = result.path("formatted_address").asText("");
        if (!lat.isNumber() || !lon.isNumber() || label.isBlank() || !Double.isFinite(lat.asDouble()) ||
            !Double.isFinite(lon.asDouble()) || Math.abs(lat.asDouble()) > 90 || Math.abs(lon.asDouble()) > 180) throw unavailable();
        return new Place(placeId, label, lat.asDouble(), lon.asDouble());
    }
    private JsonNode call(String path, String parameter, String value, boolean search) {
        if (apiKey.isBlank()) throw new WorkplaceException(HttpStatus.SERVICE_UNAVAILABLE, "PLACE_SEARCH_NOT_CONFIGURED",
            "Tìm địa chỉ chưa được cấu hình. Vui lòng liên hệ quản trị hoặc chọn vị trí trực tiếp trên bản đồ.");
        try {
            var response = client.get().uri(builder -> {
                var uri = builder.path(path).queryParam("api_key", "{key}").queryParam(parameter, "{value}");
                if (search) uri.queryParam("limit", 5);
                return uri.build(apiKey, value);
            }).retrieve().body(JsonNode.class);
            if (response == null || !(response.path("status").asText().equals("OK") ||
                    search && response.path("status").asText().equals("ZERO_RESULTS"))) throw unavailable();
            return response;
        } catch (RestClientException error) {
            // Provider responses/exceptions may contain the key or request URL. Never forward or log them.
            throw unavailable();
        }
    }
    private WorkplaceException unavailable() {
        return new WorkplaceException(HttpStatus.BAD_GATEWAY, "PLACE_SEARCH_UNAVAILABLE",
            "Không thể tìm địa chỉ lúc này. Hãy thử lại hoặc chọn vị trí trực tiếp trên bản đồ.");
    }
}
