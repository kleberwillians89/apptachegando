-- Backend real para o schema vivo do Tá Chegando.
-- O banco remoto já possui public.vendors com id textual e products em JSONB.
-- Esta migration adiciona o vínculo auth, ERR, intenções e RPCs sem recriar vendors.

CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE public.vendors
  ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS location GEOGRAPHY(POINT, 4326);

CREATE OR REPLACE FUNCTION public.sync_vendor_location()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, extensions
AS $$
BEGIN
  IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
    NEW.location = ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS vendor_location_sync ON public.vendors;
CREATE TRIGGER vendor_location_sync
  BEFORE INSERT OR UPDATE OF latitude, longitude ON public.vendors
  FOR EACH ROW EXECUTE FUNCTION public.sync_vendor_location();

CREATE OR REPLACE FUNCTION public.handle_new_vendor_profile()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  display_name TEXT;
BEGIN
  IF NEW.raw_user_meta_data->>'role' <> 'vendor' THEN
    RETURN NEW;
  END IF;

  display_name := COALESCE(
    NULLIF(NEW.raw_user_meta_data->>'business_name', ''),
    NULLIF(NEW.raw_user_meta_data->>'name', ''),
    NULLIF(NEW.raw_user_meta_data->>'full_name', ''),
    NEW.email,
    'Profissional'
  );

  INSERT INTO public.vendors (
    id,
    user_id,
    name,
    description,
    category,
    distance,
    rating,
    review_count,
    is_online,
    products
  )
  VALUES (
    NEW.id::TEXT,
    NEW.id,
    display_name,
    'Perfil profissional',
    'Comida',
    '0.0 km',
    '0.0',
    0,
    false,
    '[]'::jsonb
  )
  ON CONFLICT (id) DO UPDATE SET
    user_id = COALESCE(public.vendors.user_id, EXCLUDED.user_id),
    name = COALESCE(NULLIF(public.vendors.name, ''), EXCLUDED.name);

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created_vendor_profile ON auth.users;
CREATE TRIGGER on_auth_user_created_vendor_profile
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_vendor_profile();

CREATE TABLE IF NOT EXISTS public.vendor_routes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id TEXT REFERENCES public.vendors(id) ON DELETE CASCADE NOT NULL,
  name TEXT NOT NULL DEFAULT 'Rota sem nome',
  waypoints JSONB NOT NULL DEFAULT '[]',
  active_days JSONB NOT NULL DEFAULT '["monday","tuesday","wednesday","thursday","friday","saturday","sunday"]'::jsonb
    CHECK (jsonb_typeof(active_days) = 'array' AND jsonb_array_length(active_days) > 0),
  deviation_tolerance_meters INTEGER NOT NULL DEFAULT 150
    CHECK (deviation_tolerance_meters BETWEEN 50 AND 300),
  influence_polygon GEOGRAPHY(POLYGON, 4326),
  is_active BOOLEAN DEFAULT false,
  started_at TIMESTAMPTZ DEFAULT now(),
  ended_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.purchase_intents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  consumer_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
  vendor_id TEXT REFERENCES public.vendors(id) ON DELETE CASCADE NOT NULL,
  route_id UUID REFERENCES public.vendor_routes(id) ON DELETE SET NULL,
  location GEOGRAPHY(POINT, 4326) NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'accepted', 'fulfilled', 'expired')),
  created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
  expires_at TIMESTAMPTZ DEFAULT (now() + interval '30 minutes') NOT NULL
);

CREATE TABLE IF NOT EXISTS public.demand_clusters (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id UUID REFERENCES public.vendor_routes(id) ON DELETE CASCADE NOT NULL,
  centroid GEOGRAPHY(POINT, 4326) NOT NULL,
  intent_count INTEGER NOT NULL DEFAULT 0,
  perpendicular_distance_m DOUBLE PRECISION NOT NULL,
  estimated_detour_seconds INTEGER NOT NULL,
  viability_score DOUBLE PRECISION NOT NULL,
  status TEXT NOT NULL DEFAULT 'suggested'
    CHECK (status IN ('suggested', 'accepted', 'rejected', 'completed')),
  created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_vendors_user_id ON public.vendors (user_id);
CREATE INDEX IF NOT EXISTS idx_vendors_location ON public.vendors USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_purchase_intents_vendor_status ON public.purchase_intents (vendor_id, status);
CREATE INDEX IF NOT EXISTS idx_purchase_intents_location ON public.purchase_intents USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_demand_clusters_centroid ON public.demand_clusters USING GIST (centroid);
CREATE INDEX IF NOT EXISTS idx_vendor_routes_polygon ON public.vendor_routes USING GIST (influence_polygon);
CREATE INDEX IF NOT EXISTS idx_vendor_routes_active ON public.vendor_routes (vendor_id, is_active) WHERE is_active = true;

ALTER TABLE public.vendor_routes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_intents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demand_clusters ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'vendors' AND policyname = 'Vendors can insert own live profile'
  ) THEN
    CREATE POLICY "Vendors can insert own live profile" ON public.vendors
      FOR INSERT WITH CHECK (auth.uid() = user_id OR auth.uid()::TEXT = id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'vendors' AND policyname = 'Vendors can update own live profile'
  ) THEN
    CREATE POLICY "Vendors can update own live profile" ON public.vendors
      FOR UPDATE USING (auth.uid() = user_id OR auth.uid()::TEXT = id)
      WITH CHECK (auth.uid() = user_id OR auth.uid()::TEXT = id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'vendor_routes' AND policyname = 'Vendor manages own routes'
  ) THEN
    CREATE POLICY "Vendor manages own routes" ON public.vendor_routes
      FOR ALL USING (
        vendor_id IN (SELECT id FROM public.vendors WHERE user_id = auth.uid() OR id = auth.uid()::TEXT)
      )
      WITH CHECK (
        vendor_id IN (SELECT id FROM public.vendors WHERE user_id = auth.uid() OR id = auth.uid()::TEXT)
      );
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'purchase_intents' AND policyname = 'Consumer creates own intents'
  ) THEN
    CREATE POLICY "Consumer creates own intents" ON public.purchase_intents
      FOR INSERT WITH CHECK (consumer_id = auth.uid());
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'purchase_intents' AND policyname = 'Consumer views own intents'
  ) THEN
    CREATE POLICY "Consumer views own intents" ON public.purchase_intents
      FOR SELECT USING (consumer_id = auth.uid());
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'purchase_intents' AND policyname = 'Vendor views own intents'
  ) THEN
    CREATE POLICY "Vendor views own intents" ON public.purchase_intents
      FOR SELECT USING (
        vendor_id IN (SELECT id FROM public.vendors WHERE user_id = auth.uid() OR id = auth.uid()::TEXT)
      );
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'demand_clusters' AND policyname = 'Vendor views own clusters'
  ) THEN
    CREATE POLICY "Vendor views own clusters" ON public.demand_clusters
      FOR SELECT USING (
        route_id IN (
          SELECT route.id
          FROM public.vendor_routes route
          JOIN public.vendors vendor ON vendor.id = route.vendor_id
          WHERE vendor.user_id = auth.uid() OR vendor.id = auth.uid()::TEXT
        )
      );
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.get_intents_in_polygon(coordinates JSONB)
RETURNS TABLE (
  id UUID,
  consumer_id UUID,
  lat DOUBLE PRECISION,
  lng DOUBLE PRECISION,
  status TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  search_polygon GEOMETRY;
BEGIN
  WITH points AS (
    SELECT
      ord,
      ST_SetSRID(ST_MakePoint((coord->>0)::DOUBLE PRECISION, (coord->>1)::DOUBLE PRECISION), 4326) AS geom
    FROM jsonb_array_elements(coordinates) WITH ORDINALITY AS items(coord, ord)
  )
  SELECT ST_MakePolygon(ST_MakeLine(geom ORDER BY ord)) INTO search_polygon
  FROM points;

  RETURN QUERY
  SELECT
    intent.id,
    intent.consumer_id,
    ST_Y(intent.location::geometry) AS lat,
    ST_X(intent.location::geometry) AS lng,
    intent.status
  FROM public.purchase_intents intent
  WHERE ST_Within(intent.location::geometry, search_polygon)
    AND intent.status = 'pending'
    AND intent.expires_at > now();
END;
$$;

CREATE OR REPLACE FUNCTION public.create_route_buffer(points JSONB, tolerance_meters INTEGER)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  route_line GEOMETRY;
  buffer_polygon GEOMETRY;
BEGIN
  WITH route_points AS (
    SELECT
      ord,
      ST_SetSRID(ST_MakePoint((point->>0)::DOUBLE PRECISION, (point->>1)::DOUBLE PRECISION), 4326) AS geom
    FROM jsonb_array_elements(points) WITH ORDINALITY AS items(point, ord)
  )
  SELECT ST_MakeLine(geom ORDER BY ord) INTO route_line
  FROM route_points;

  buffer_polygon := ST_Buffer(route_line::geography, tolerance_meters)::geometry;

  RETURN (ST_AsGeoJSON(buffer_polygon)::JSONB)->'coordinates';
END;
$$;
