package com.company.hr.attendance.attendance.infrastructure;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceExplanationImageRepository {
    public record ImageInfo(UUID id, String contentType, int sizeBytes) {}
    public record Photo(String contentType, byte[] bytes) {}

    private final JdbcTemplate jdbc;
    public AttendanceExplanationImageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(UUID explanationId, int position, String contentType, byte[] bytes) {
        jdbc.update("INSERT INTO attendance_explanation_images(id,explanation_id,image_position,content_type,size_bytes,image_bytes) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID(), explanationId, position, contentType, bytes.length, bytes);
    }

    public List<ImageInfo> forExplanation(UUID explanationId) {
        return jdbc.query("SELECT id,content_type,size_bytes FROM attendance_explanation_images WHERE explanation_id=? ORDER BY image_position",
            (rs, index) -> new ImageInfo(rs.getObject("id", UUID.class), rs.getString("content_type"), rs.getInt("size_bytes")), explanationId);
    }

    public Optional<Photo> photo(UUID explanationId, UUID imageId) {
        return jdbc.query("SELECT content_type,image_bytes FROM attendance_explanation_images WHERE explanation_id=? AND id=?",
            (rs, index) -> new Photo(rs.getString("content_type"), rs.getBytes("image_bytes")), explanationId, imageId).stream().findFirst();
    }
}
