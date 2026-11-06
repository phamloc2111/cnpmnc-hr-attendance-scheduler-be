package com.company.hr.attendance.organization.domain;
import java.text.Normalizer;
import java.util.Locale;
public final class PositionName {
    private PositionName() {}
    public static String display(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("[\\s\\p{Z}]+", " ").strip();
    }
    public static String key(String value) { return display(value).toLowerCase(Locale.ROOT); }
}
