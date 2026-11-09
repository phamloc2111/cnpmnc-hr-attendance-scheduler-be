-- Run manually once, after reviewing MIGRATION_RECOVERY.md. Never run at application startup.
-- Only reconciles the lost V10 file with its reconstruction; does not execute application DDL.
DO $recovery$
DECLARE
    actual_fingerprint TEXT;
    previous_checksum INTEGER;
BEGIN
    IF current_schema() <> 'public' THEN
        RAISE EXCEPTION 'Recovery requires the inspected public schema';
    END IF;
    LOCK TABLE public.flyway_schema_history IN SHARE ROW EXCLUSIVE MODE;
    LOCK TABLE public.shift_registrations, public.registration_reopens IN SHARE MODE;

    SELECT md5(string_agg(definition, E'\n' ORDER BY definition)) INTO actual_fingerprint
    FROM (
        SELECT 'column:' || table_name || ':' || ordinal_position || ':' || column_name || ':' ||
               data_type || ':' || is_nullable || ':' || coalesce(column_default, '') AS definition
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name IN ('shift_registrations', 'registration_reopens')
        UNION ALL
        SELECT 'constraint:' || c.relname || ':' || con.conname || ':' || pg_get_constraintdef(con.oid)
        FROM pg_constraint con JOIN pg_class c ON c.oid = con.conrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname IN ('shift_registrations', 'registration_reopens')
          AND con.contype <> 'n'
        UNION ALL
        SELECT 'index:' || tablename || ':' || indexname || ':' || indexdef
        FROM pg_indexes
        WHERE schemaname = 'public' AND tablename IN ('shift_registrations', 'registration_reopens')
    ) definitions;

    IF actual_fingerprint IS DISTINCT FROM '4ef32398c673568f3a5f914eee953a89' THEN
        RAISE EXCEPTION 'Registration schema differs from the inspected reconstruction; no history changed';
    END IF;

    SELECT checksum INTO previous_checksum FROM public.flyway_schema_history
    WHERE version = '10' AND description = 'shift registrations' AND type = 'SQL'
      AND script = 'V10__shift_registrations.sql' AND success = true;
    IF previous_checksum IS NULL OR previous_checksum NOT IN (-1798121770, 421065846) THEN
        RAISE EXCEPTION 'Unexpected V10 history; no history changed';
    END IF;
    UPDATE public.flyway_schema_history SET checksum = 421065846
    WHERE version = '10' AND checksum = -1798121770
      AND script = 'V10__shift_registrations.sql' AND success = true;
END
$recovery$;
