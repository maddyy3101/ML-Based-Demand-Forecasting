-- POWERGRID PostgreSQL bootstrap script
-- Safe to run multiple times.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO
$$
BEGIN
    -- Optional performance index for audit timeline queries.
    IF to_regclass('public.procurement_forecasts') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_forecast_created_at
            ON public.procurement_forecasts (created_at DESC);
    END IF;

    -- Optional performance index for stock movement dashboard queries.
    IF to_regclass('public.material_movements') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_movement_type_timestamp
            ON public.material_movements (movement_type, timestamp DESC);
    END IF;
END;
$$;
