-- ============================================================
-- Migration: ERR — Suporte a chamadas diretas RPC
-- Data: 2026-04-30
-- Descrição: Funções para receber coordenadas do app e
--            retornar intents e o polígono ST_Buffer.
-- ============================================================

-- 1. RPC: get_intents_in_polygon
-- Recebe um JSON com array bidimensional de coordenadas (ex: [[lng, lat], [lng, lat]])
-- e retorna as intenções de compra dentro desse polígono.
CREATE OR REPLACE FUNCTION get_intents_in_polygon(coordinates JSONB)
RETURNS TABLE (
    id UUID,
    consumer_id UUID,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    status TEXT
) AS $$
DECLARE
    search_polygon GEOMETRY;
BEGIN
    -- Converte o array de arrays [[lng, lat]] para uma string LINESTRING e depois para POLYGON
    -- Assumindo que o primeiro e o último ponto são iguais para fechar o polígono.
    -- Para robustez, se não fechar, o PostgreSQL exigirá o fechamento (ST_MakePolygon).
    -- Uma abordagem mais segura usando PostGIS:
    WITH points AS (
        SELECT ST_MakePoint((c->>0)::numeric, (c->>1)::numeric) AS geom
        FROM jsonb_array_elements(coordinates) AS c
    )
    SELECT ST_MakePolygon(ST_MakeLine(geom)) INTO search_polygon
    FROM points;

    -- Garantir o SRID 4326
    search_polygon := ST_SetSRID(search_polygon, 4326);

    RETURN QUERY
    SELECT
        pi.id,
        pi.consumer_id,
        ST_Y(pi.location::geometry) AS lat,
        ST_X(pi.location::geometry) AS lng,
        pi.status
    FROM public.purchase_intents pi
    WHERE ST_Within(pi.location::geometry, search_polygon)
      AND pi.status = 'pending';

END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- 2. RPC: create_route_buffer
-- Recebe uma rota (pontos) e uma tolerância, calcula a área de influência
-- e retorna os pontos da borda do polígono gerado.
CREATE OR REPLACE FUNCTION create_route_buffer(points JSONB, tolerance_meters INTEGER)
RETURNS JSONB AS $$
DECLARE
    route_line GEOMETRY;
    buffer_polygon GEOMETRY;
    geojson_result JSONB;
BEGIN
    -- 1. Cria a linha da rota
    WITH route_points AS (
        SELECT ST_SetSRID(ST_MakePoint((p->>0)::numeric, (p->>1)::numeric), 4326) AS geom
        FROM jsonb_array_elements(points) AS p
    )
    SELECT ST_MakeLine(geom) INTO route_line
    FROM route_points;

    -- 2. Gera o buffer geográfico com a tolerância em metros
    -- O cast para geography garante que o buffer seja em metros exatos, não graus.
    buffer_polygon := ST_Buffer(route_line::geography, tolerance_meters)::geometry;

    -- 3. Converte as coordenadas do polígono de volta para um Array JSON de coordenadas
    -- ST_AsGeoJSON retorna um formato padrão. Extrairemos apenas o array 'coordinates'.
    SELECT (ST_AsGeoJSON(buffer_polygon)::JSONB)->'coordinates' INTO geojson_result;

    RETURN geojson_result;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
