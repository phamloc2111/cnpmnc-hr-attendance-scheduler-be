-- Preserve applied V12; each reminder slot has its own delivery deduplication key.
ALTER TABLE notification_deliveries DROP CONSTRAINT IF EXISTS notification_deliveries_kind_check;
ALTER TABLE notification_deliveries ADD CONSTRAINT ck_notification_delivery_kind
    CHECK(kind IN ('OPEN', 'REMINDER', 'REMINDER_0800', 'REMINDER_1500', 'REMINDER_2000'));
