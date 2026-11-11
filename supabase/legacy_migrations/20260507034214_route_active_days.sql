ALTER TABLE public.vendor_routes
ADD COLUMN IF NOT EXISTS active_days JSONB NOT NULL
DEFAULT '["monday","tuesday","wednesday","thursday","friday","saturday","sunday"]'::jsonb;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'vendor_routes_active_days_non_empty'
  ) THEN
    ALTER TABLE public.vendor_routes
    ADD CONSTRAINT vendor_routes_active_days_non_empty
    CHECK (
      jsonb_typeof(active_days) = 'array'
      AND jsonb_array_length(active_days) > 0
    );
  END IF;
END $$;
