package com.company.hr.attendance.organization.api;

public final class PlaceModels {
    private PlaceModels() {}
    public record Suggestion(String placeId, String label) {}
    public record Place(String placeId, String label, double latitude, double longitude) {}
}
