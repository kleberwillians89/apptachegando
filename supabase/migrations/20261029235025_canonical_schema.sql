-- New baseline only. Never apply to a legacy database without an explicit migration plan.
create schema if not exists extensions;
create schema if not exists private;
revoke create on schema public from public,anon,authenticated;
revoke all on schema private from public, anon, authenticated;
create extension if not exists postgis with schema extensions;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null default '' check (length(full_name) <= 160),
  phone text check (length(phone) <= 32),
  created_at timestamptz not null default now()
);
create table public.vendors (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references public.profiles(id) on delete cascade,
  name text not null check (length(trim(name)) between 1 and 160),
  description text not null default '' check (length(description) <= 2000),
  category text not null default 'Comida' check (length(category) between 1 and 80),
  is_online boolean not null default false,
  created_at timestamptz not null default now()
);
create table public.products (
  id uuid primary key default gen_random_uuid(),
  vendor_id uuid not null references public.vendors(id) on delete cascade,
  name text not null check (length(trim(name)) between 1 and 160),
  price numeric(10,2) not null default 0 check (price >= 0),
  unit text check (length(unit) <= 40),
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);
create index products_vendor_idx on public.products(vendor_id);
create table public.vendor_locations (
  vendor_id uuid primary key references public.vendors(id) on delete cascade,
  location extensions.geography(Point,4326) not null,
  accuracy_m double precision check (accuracy_m >= 0 and accuracy_m < 10000),
  updated_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '2 minutes'
);
create index vendor_locations_geo_idx on public.vendor_locations using gist(location);
create table public.vendor_routes (
  id uuid primary key default gen_random_uuid(),
  vendor_id uuid not null references public.vendors(id) on delete cascade,
  name text not null check (length(trim(name)) between 1 and 160),
  waypoints jsonb not null,
  active_days text[] not null default array['monday','tuesday','wednesday','thursday','friday','saturday','sunday']::text[],
  deviation_tolerance_meters integer not null default 150 check (deviation_tolerance_meters between 50 and 300),
  influence_polygon extensions.geography(Polygon,4326) not null,
  status text not null default 'active' check (status in ('draft','active','ended')),
  started_at timestamptz,
  ended_at timestamptz,
  created_at timestamptz not null default now(),
  unique(id,vendor_id),
  check (cardinality(active_days) between 1 and 7 and active_days <@ array['monday','tuesday','wednesday','thursday','friday','saturday','sunday']::text[])
);
create index vendor_routes_vendor_idx on public.vendor_routes(vendor_id);
create unique index one_active_route_per_vendor on public.vendor_routes(vendor_id) where status='active';
create index vendor_routes_geo_idx on public.vendor_routes using gist(influence_polygon);
create table public.purchase_intents (
  id uuid primary key default gen_random_uuid(),
  consumer_id uuid not null references public.profiles(id) on delete cascade,
  vendor_id uuid not null references public.vendors(id) on delete cascade,
  route_id uuid,
  location extensions.geography(Point,4326) not null,
  status text not null default 'pending' check(status in ('pending','accepted','fulfilled','expired')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now()+interval '30 minutes',
  foreign key(route_id,vendor_id) references public.vendor_routes(id,vendor_id),
  check(expires_at > created_at)
);
create index purchase_intents_consumer_idx on public.purchase_intents(consumer_id);
create index purchase_intents_vendor_idx on public.purchase_intents(vendor_id,status,expires_at);
create index purchase_intents_route_idx on public.purchase_intents(route_id);
create index purchase_intents_geo_idx on public.purchase_intents using gist(location);
create unique index one_pending_intent on public.purchase_intents(consumer_id,vendor_id) where status='pending';

-- Trigger is privileged only to provision the authenticated identity's own profile.
-- Choosing vendor at signup is an allowed product action, never an administrative privilege.
create function private.handle_new_user() returns trigger language plpgsql security definer set search_path='' as $$
begin
  insert into public.profiles(id,full_name,phone) values(new.id,left(coalesce(new.raw_user_meta_data->>'name',''),160),left(new.raw_user_meta_data->>'phone',32));
  if new.raw_user_meta_data->>'role' = 'vendor' then
    insert into public.vendors(user_id,name) values(new.id,left(coalesce(nullif(new.raw_user_meta_data->>'business_name',''),nullif(new.raw_user_meta_data->>'name',''),'Profissional'),160));
  end if;
  return new;
end $$;
create trigger on_auth_user_created after insert on auth.users for each row execute function private.handle_new_user();
revoke all on function private.handle_new_user() from public,anon,authenticated;

create function private.route_geometry() returns trigger language plpgsql set search_path=public,extensions as $$
declare line geometry; point jsonb; days_count int;
begin
  if jsonb_typeof(new.waypoints) is distinct from 'array' or jsonb_array_length(new.waypoints) not between 2 and 2000 then
    raise exception 'Route requires 2..2000 points' using errcode='22023';
  end if;
  for point in select value from jsonb_array_elements(new.waypoints) loop
    if jsonb_typeof(point->'latitude') is distinct from 'number' or jsonb_typeof(point->'longitude') is distinct from 'number'
       or (point->>'latitude')::float8 not between -90 and 90 or (point->>'longitude')::float8 not between -180 and 180 then
      raise exception 'Invalid route coordinates' using errcode='22023';
    end if;
  end loop;
  select count(distinct d) into days_count from unnest(new.active_days) d;
  if days_count <> cardinality(new.active_days) then raise exception 'Repeated active days' using errcode='22023'; end if;
  select st_makeline(st_setsrid(st_makepoint((p->>'longitude')::float8,(p->>'latitude')::float8),4326) order by ord)
    into line from jsonb_array_elements(new.waypoints) with ordinality t(p,ord);
  if st_length(line::geography) <= 0 then raise exception 'Route points must differ' using errcode='22023'; end if;
  new.influence_polygon := st_buffer(line::geography,new.deviation_tolerance_meters);
  if tg_op='UPDATE' and old.status='ended' and new.status <> 'ended' then
    raise exception 'Ended routes cannot restart' using errcode='22023';
  end if;
  if new.status='active' then new.started_at:=coalesce(new.started_at,now()); end if;
  if new.status='ended' then new.ended_at:=coalesce(new.ended_at,now()); end if;
  return new;
end $$;
create trigger compute_route before insert or update on public.vendor_routes for each row execute function private.route_geometry();
revoke all on function private.route_geometry() from public,anon,authenticated;

alter table public.profiles enable row level security;
alter table public.vendors enable row level security;
alter table public.products enable row level security;
alter table public.vendor_locations enable row level security;
alter table public.vendor_routes enable row level security;
alter table public.purchase_intents enable row level security;

revoke all on public.profiles, public.vendors, public.products, public.vendor_locations, public.vendor_routes, public.purchase_intents from anon,authenticated;
grant usage on schema public,extensions to authenticated;
grant select on public.profiles,public.vendors,public.products,public.vendor_locations,public.vendor_routes,public.purchase_intents to authenticated;
grant update(full_name,phone) on public.profiles to authenticated;
grant update(name,description,category,is_online) on public.vendors to authenticated;
grant insert(vendor_id,name,price,unit,is_active),update(name,price,unit,is_active),delete on public.products to authenticated;
grant insert(vendor_id,name,waypoints,active_days,deviation_tolerance_meters,status),update(name,waypoints,active_days,deviation_tolerance_meters,status) on public.vendor_routes to authenticated;
-- Locations and intents are writeable only through validated RPCs in the next migration.

create policy profile_owner_select on public.profiles for select to authenticated using(id=(select auth.uid()));
create policy profile_owner_update on public.profiles for update to authenticated using(id=(select auth.uid())) with check(id=(select auth.uid()));
create policy vendor_visible on public.vendors for select to authenticated using(is_online or user_id=(select auth.uid()));
create policy vendor_owner_update on public.vendors for update to authenticated using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()));
create policy product_visible on public.products for select to authenticated using(exists(select 1 from public.vendors v where v.id=vendor_id and (v.user_id=(select auth.uid()) or (v.is_online and products.is_active))));
create policy product_owner_insert on public.products for insert to authenticated with check(exists(select 1 from public.vendors v where v.id=vendor_id and v.user_id=(select auth.uid())));
create policy product_owner_update on public.products for update to authenticated using(exists(select 1 from public.vendors v where v.id=vendor_id and v.user_id=(select auth.uid()))) with check(exists(select 1 from public.vendors v where v.id=vendor_id and v.user_id=(select auth.uid())));
create policy product_owner_delete on public.products for delete to authenticated using(exists(select 1 from public.vendors v where v.id=vendor_id and v.user_id=(select auth.uid())));
create policy location_visible on public.vendor_locations for select to authenticated using(exists(select 1 from public.vendors v where v.id=vendor_id and (v.user_id=(select auth.uid()) or (v.is_online and vendor_locations.expires_at>now()))));
create policy route_owner on public.vendor_routes for all to authenticated using(exists(select 1 from public.vendors v where v.id=vendor_id and v.user_id=(select auth.uid()))) with check(exists(select 1 from public.vendors v where v.id=vendor_id and v.user_id=(select auth.uid())));
create policy intent_visible on public.purchase_intents for select to authenticated using(consumer_id=(select auth.uid()) or (expires_at>now() and exists(select 1 from public.vendors v where v.id=vendor_id and v.user_id=(select auth.uid()))));

-- Projection for the existing UI; products remain normalized in their table.
create view public.vendor_catalog with(security_invoker=true) as
select v.id,v.user_id,v.name,v.description,v.category,v.is_online,
  ''::text as distance,'0.0'::text as rating,0::int as review_count,
  extensions.st_y(l.location::extensions.geometry) as latitude,
  extensions.st_x(l.location::extensions.geometry) as longitude,
  coalesce((select jsonb_agg(jsonb_build_object('id',p.id,'name',p.name,'price',p.price::text) order by p.created_at,p.id)
    from public.products p where p.vendor_id=v.id and p.is_active),'[]'::jsonb) as products
from public.vendors v left join public.vendor_locations l on l.vendor_id=v.id and l.expires_at>now();
create view public.route_details with(security_invoker=true) as
select id,vendor_id,name,waypoints,active_days,deviation_tolerance_meters,status,
  (extensions.st_asgeojson(influence_polygon::extensions.geometry)::jsonb)->'coordinates' as polygon
from public.vendor_routes;
revoke all on public.vendor_catalog,public.route_details from anon,authenticated;
grant select on public.vendor_catalog,public.route_details to authenticated;

-- Local stacks already have this publication. Tests also exercise a scratch PostgreSQL cluster.
do $$ begin
  if exists(select 1 from pg_publication where pubname='supabase_realtime') then
    alter publication supabase_realtime add table public.vendors,public.products,public.vendor_locations;
  end if;
end $$;
