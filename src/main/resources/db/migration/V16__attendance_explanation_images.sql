CREATE TABLE attendance_explanation_images (
    id UUID PRIMARY KEY,
    explanation_id UUID NOT NULL REFERENCES attendance_explanations(id) ON DELETE CASCADE,
    image_position SMALLINT NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    size_bytes INTEGER NOT NULL,
    image_bytes BYTEA NOT NULL,
    CONSTRAINT uq_attendance_explanation_image_position UNIQUE (explanation_id, image_position),
    CONSTRAINT ck_attendance_explanation_image_position CHECK (image_position BETWEEN 0 AND 2),
    CONSTRAINT ck_attendance_explanation_image_size CHECK (size_bytes BETWEEN 1 AND 5242880),
    CONSTRAINT ck_attendance_explanation_image_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))
);

CREATE INDEX ix_attendance_explanation_images_explanation ON attendance_explanation_images(explanation_id);
