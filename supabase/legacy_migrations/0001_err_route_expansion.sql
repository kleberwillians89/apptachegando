-- ============================================================
-- Migration: ERR — Expansor de Rotas de Requisição
-- Data: 2026-04-30
-- Descrição: Tabelas, índices, RLS e RPCs para o algoritmo ERR
-- Depende de: 0000_initial_schema.sql, PostGIS extension
-- ============================================================

-- 0. Habilitar PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Adicionar coluna geográfica em vendors
ALTER TABLE public.vendors
  ADD COLUMN IF NOT EXISTS location GEOGRAPHY(POINT, 4326);

-- Trigger para sincronizar lat/lng → geography automaticamente
CREATE OR REPLACE FUNCTION sync_vendor_location()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
    NEW.location = ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS vendor_location_sync ON public.vendors;
CREATE TRIGGER vendor_location_sync
  BEFORE INSERT OR UPDATE ON public.vendors
  FOR EACH ROW EXECUTE FUNCTION sync_vendor_location();

-- 2. Tabela de rotas do vendedor
CREATE TABLE IF NOT EXISTS public.vendor_routes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vendor_id UUID REFERENCES public.vendors(id) ON DELETE CASCADE NOT NULL,
  name TEXT NOT NULL DEFAULT 'Rota sem nome',
  waypoints JSONB NOT NULL DEFAULT '[]',
  active_days JSONB NOT NULL DEFAULT '["monday","tuesday","wednesday","thursday","friday","saturday","sunday"]'::jsonb
    CHECK (jsonb_typeof(active_days) = 'array' AND jsonb_array_length(active_days) > 0),
  deviation_tolerance_meters INTEGER NOT NULL DEFAULT 150
    CHECK (deviation_tolerance_meters BETWEEN 50 AND 300),
  influence_polygon GEOGRAPHY(POLYGON, 4326),
  is_active BOOLEAN DEFAULT false,
  started_at TIMESTAMPTZ,
  ended_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 3. Tabela de intenções de compra
CREATE TABLE IF NOT EXISTS public.purchase_intents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  consumer_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
  vendor_id UUID REFERENCES public.vendors(id) ON DELETE CASCADE NOT NULL,
  route_id UUID REFERENCES public.vendor_routes(id) ON DELETE SET NULL,
  location GEOGRAPHY(POINT, 4326) NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'accepted', 'fulfilled', 'expired')),
  created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
  expires_at TIMESTAMPTZ DEFAULT (now() + interval '30 minutes') NOT NULL
);

-- 4. Tabela de clusters de demanda (cache de processamento)
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

-- 5. Índices espaciais (GIST)
CREATE INDEX IF NOT EXISTS idx_vendors_location
  ON public.vendors USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_purchase_intents_location
  ON public.purchase_intents USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_demand_clusters_centroid
  ON public.demand_clusters USING GIST (centroid);
CREATE INDEX IF NOT EXISTS idx_vendor_routes_polygon
  ON public.vendor_routes USING GIST (influence_polygon);
CREATE INDEX IF NOT EXISTS idx_vendor_routes_active
  ON public.vendor_routes (vendor_id, is_active) WHERE is_active = true;

-- 6. RLS Policies
ALTER TABLE public.vendor_routes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_intents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demand_clusters ENABLE ROW LEVEL SECURITY;

-- vendor_routes
CREATE POLICY "Vendor manages own routes" ON public.vendor_routes
  FOR ALL USING (vendor_id IN (
    SELECT id FROM public.vendors WHERE user_id = auth.uid()
  ));

-- purchase_intents
CREATE POLICY "Consumer creates own intents" ON public.purchase_intents
  FOR INSERT WITH CHECK (consumer_id = auth.uid());

CREATE POLICY "Consumer views own intents" ON public.purchase_intents
  FOR SELECT USING (consumer_id = auth.uid());

CREATE POLICY "Vendor views route intents" ON public.purchase_intents
  FOR SELECT USING (vendor_id IN (
    SELECT id FROM public.vendors WHERE user_id = auth.uid()
  ));

-- demand_clusters
CREATE POLICY "Vendor views own clusters" ON public.demand_clusters
  FOR SELECT USING (route_id IN (
    SELECT id FROM public.vendor_routes WHERE vendor_id IN (
      SELECT id FROM public.vendors WHERE user_id = auth.uid()
    )
  ));

-- 7. RPC: Gerar polígono de influência
CREATE OR REPLACE FUNCTION generate_influence_polygon(
  p_route_id UUID,
  p_waypoints JSONB,
  p_tolerance_meters INTEGER
)
RETURNS GEOGRAPHY AS $$
DECLARE
  route_line GEOMETRY;
  buffer_polygon GEOGRAPHY;
BEGIN
  -- Converte waypoints JSON em LineString
  SELECT ST_MakeLine(
    ARRAY(
      SELECT ST_SetSRID(
        ST_MakePoint(
          (wp->>'longitude')::double precision,
          (wp->>'latitude')::double precision
        ), 4326
      )
      FROM jsonb_array_elements(p_waypoints) AS wp
    )
  ) INTO route_line;

  -- Gera buffer com tolerância D (metros)
  buffer_polygon := ST_Buffer(route_line::geography, p_tolerance_meters);

  -- Persiste o polígono na rota
  UPDATE public.vendor_routes
  SET influence_polygon = buffer_polygon
  WHERE id = p_route_id;

  RETURN buffer_polygon;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8. RPC: Buscar consumidores dentro da zona de influência
CREATE OR REPLACE FUNCTION get_consumers_in_zone(p_route_id UUID)
RETURNS TABLE(user_id UUID, lat DOUBLE PRECISION, lng DOUBLE PRECISION) AS $$
BEGIN
  RETURN QUERY
  SELECT
    v.user_id,
    ST_Y(v.location::geometry) as lat,
    ST_X(v.location::geometry) as lng
  FROM public.vendors v
  JOIN public.vendor_routes r ON r.id = p_route_id
  WHERE ST_Within(v.location::geometry, r.influence_polygon::geometry)
    AND v.is_online = true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
