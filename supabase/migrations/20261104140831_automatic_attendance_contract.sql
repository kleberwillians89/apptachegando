-- REVIEW REQUIRED. Prepared and tested locally only; do not apply remotely without approval.
-- No per-customer acceptance, sales, or public exposure of consumer coordinates.
alter table public.purchase_intents drop constraint purchase_intents_status_check;
alter table public.purchase_intents add constraint purchase_intents_status_check
  check(status in ('pending','accepted','fulfilled','expired','cancelled'));
grant update(status) on public.purchase_intents to authenticated;
create policy consumer_cancel_pending on public.purchase_intents for update to authenticated
  using(consumer_id=(select auth.uid()) and status='pending')
  with check(consumer_id=(select auth.uid()) and status='cancelled');

create function public.cancel_purchase_intent(p_intent_id uuid) returns uuid
language plpgsql security invoker set search_path='' as $$
declare result uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
  update public.purchase_intents set status='cancelled'
    where id=p_intent_id and consumer_id=auth.uid() and status='pending' returning id into result;
  if result is null then
    select id into result from public.purchase_intents where id=p_intent_id and consumer_id=auth.uid() and status='cancelled';
  end if;
  if result is null then raise exception 'Intent not available' using errcode='42501'; end if;
  return result;
end $$;

-- Full snapshots, including cancellation/expiry and points outside the route corridor.
-- Coordinates are returned only to the authenticated owner of this route/vendor.
-- Snapshot requests are serialized by the client; Realtime only invalidates snapshots.
create function public.get_route_attendance(p_route_id uuid) returns jsonb
language plpgsql stable security invoker set search_path='' as $$
declare result jsonb;
begin
  if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
  select jsonb_build_object('route_id',r.id,'vendor_id',r.vendor_id,'active',r.status='active','online',v.is_online,
    'intents',coalesce((select jsonb_agg(jsonb_build_object('id',i.id,'consumer_id',i.consumer_id,
      'lat',extensions.st_y(i.location::extensions.geometry),'lng',extensions.st_x(i.location::extensions.geometry),
      'status',case when i.status='pending' and i.expires_at<=now() then 'expired' else i.status end) order by i.id)
      from public.purchase_intents i where i.vendor_id=r.vendor_id and (i.route_id is null or i.route_id=r.id)
        and i.created_at>=r.created_at - interval '30 minutes' and r.status='active'), '[]'::jsonb))
  into result from public.vendor_routes r join public.vendors v on v.id=r.vendor_id
    where r.id=p_route_id and v.user_id=auth.uid();
  if result is null then raise exception 'Route not available' using errcode='42501'; end if;
  if jsonb_array_length(result->'intents')>2000 then raise exception 'Attendance capacity exceeded' using errcode='54000'; end if;
  return result;
end $$;

revoke all on function public.get_route_attendance(uuid),public.cancel_purchase_intent(uuid) from public,anon,authenticated;
grant execute on function public.get_route_attendance(uuid),public.cancel_purchase_intent(uuid) to authenticated;

-- Shared discovery taxonomy; existing names/prices are not rewritten or deleted.
create function public.catalog_term(value text) returns text
language sql immutable security invoker set search_path='' as $$
  select translate(lower(trim(coalesce(value,''))),'áàâãäéèêëíìîïóòôõöúùûüç','aaaaaeeeeiiiiooooouuuuc');
$$;
create function public.search_vendor_catalog(p_query text default '',p_category text default '',p_offset integer default 0,p_limit integer default 50)
returns table(id uuid,name text,description text,category text,is_online boolean,distance text,rating text,
  review_count integer,latitude double precision,longitude double precision,products jsonb)
language plpgsql stable security invoker set search_path='' as $$
declare q text:=public.catalog_term(p_query); c text:=public.catalog_term(p_category); keys text[];
begin
  if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if length(q)>120 or p_offset is null or p_offset not between 0 and 10000 or p_limit is null or p_limit not between 1 and 50 then
    raise exception 'Invalid search' using errcode='22023';
  end if;
  keys:=case c when '' then array[''] when 'paes' then array['paes','pao'] when 'frutas' then array['fruta','hortifruti']
    when 'salgados' then array['salgado'] when 'doces de padaria' then array['doce','bolo']
    when 'lanches naturais' then array['lanche natural','lanches naturais','sanduiche natural']
    when 'sorvetes' then array['sorvete','picole'] when 'temperos' then array['tempero']
    when 'produtos de limpeza prontos' then array['limpeza','detergente','sabao'] else null end;
  if keys is null then raise exception 'Unknown category' using errcode='22023'; end if;
  return query select v.id,v.name,v.description,v.category,v.is_online,v.distance,v.rating,v.review_count,v.latitude,v.longitude,v.products from public.vendor_catalog v where v.is_online
    and (q='' or strpos(public.catalog_term(v.name||' '||v.description||' '||v.category),q)>0
      or exists(select 1 from public.products p where p.vendor_id=v.id and p.is_active and strpos(public.catalog_term(p.name),q)>0))
    and exists(select 1 from unnest(keys) k where k='' or strpos(public.catalog_term(v.category),k)>0
      or exists(select 1 from public.products p where p.vendor_id=v.id and p.is_active and strpos(public.catalog_term(p.name),k)>0))
    order by v.id offset p_offset limit p_limit;
end $$;
revoke all on function public.catalog_term(text),public.search_vendor_catalog(text,text,integer,integer) from public,anon,authenticated;
grant execute on function public.catalog_term(text),public.search_vendor_catalog(text,text,integer,integer) to authenticated;

-- Current rows only; no replica identity FULL containing old consumer coordinates.
do $$ begin
  if exists(select 1 from pg_publication where pubname='supabase_realtime') then
    if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='purchase_intents') then
      alter publication supabase_realtime add table public.purchase_intents;
    end if;
    if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='vendor_routes') then
      alter publication supabase_realtime add table public.vendor_routes;
    end if;
  end if;
end $$;

-- Bind each new interest to the active journey, not a future reuse of its geometry.
-- Privileged writes stay in the non-exposed private schema with explicit identity checks.
grant usage on schema private to authenticated;
create function private.create_route_interest(p_vendor_id uuid,p_latitude double precision,p_longitude double precision)
returns uuid language plpgsql security definer set search_path='' as $$
declare result uuid; active_route uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if p_latitude is null or p_longitude is null or p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then
    raise exception 'Invalid coordinates' using errcode='22023';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(auth.uid()::text,0));
  select r.id into active_route from public.vendor_routes r join public.vendors v on v.id=r.vendor_id
    where v.id=p_vendor_id and v.is_online and v.user_id<>auth.uid() and r.status='active' for share of r,v;
  if active_route is null then raise exception 'Working route required' using errcode='42501'; end if;
  update public.purchase_intents set status='expired' where consumer_id=auth.uid() and vendor_id=p_vendor_id and status='pending'
    and (expires_at<=now() or route_id is distinct from active_route);
  select id into result from public.purchase_intents where consumer_id=auth.uid() and vendor_id=p_vendor_id
    and route_id=active_route and status='pending';
  if result is not null then return result; end if;
  if (select count(*) from public.purchase_intents where consumer_id=auth.uid() and status='pending' and expires_at>now())>=10 then
    raise exception 'Too many active intents' using errcode='22023';
  end if;
  insert into public.purchase_intents(consumer_id,vendor_id,route_id,location)
    values(auth.uid(),p_vendor_id,active_route,extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography)
    returning id into result;
  return result;
end $$;
create or replace function public.create_purchase_intent(p_vendor_id uuid,p_latitude double precision,p_longitude double precision)
returns uuid language sql security invoker set search_path='' as $$
  select private.create_route_interest(p_vendor_id,p_latitude,p_longitude);
$$;

create function private.change_route_availability(p_route_id uuid,p_online boolean,p_end boolean)
returns uuid language plpgsql security definer set search_path='' as $$
declare vendor uuid; route_status text;
begin
  if auth.uid() is null or p_online is null or p_end is null or (p_end and p_online) then
    raise exception 'Invalid journey command' using errcode='42501';
  end if;
  select r.vendor_id,r.status into vendor,route_status from public.vendor_routes r join public.vendors v on v.id=r.vendor_id
    where r.id=p_route_id and v.user_id=auth.uid() for update of r,v;
  if vendor is null or route_status<>'active' then raise exception 'Active route required' using errcode='42501'; end if;
  update public.vendors set is_online=p_online where id=vendor and user_id=auth.uid();
  if p_end then
    update public.vendor_routes set status='ended' where id=p_route_id;
    update public.purchase_intents set status='expired' where vendor_id=vendor and (route_id=p_route_id or route_id is null) and status='pending';
  end if;
  return p_route_id;
end $$;
create or replace function public.set_route_availability(p_route_id uuid,p_online boolean,p_end boolean default false)
returns uuid language sql security invoker set search_path='' as $$
  select private.change_route_availability(p_route_id,p_online,p_end);
$$;
revoke all on function private.create_route_interest(uuid,double precision,double precision),private.change_route_availability(uuid,boolean,boolean) from public,anon,authenticated;
grant execute on function private.create_route_interest(uuid,double precision,double precision),private.change_route_availability(uuid,boolean,boolean) to authenticated;

revoke all on function public.set_route_availability(uuid,boolean,boolean) from public,anon,authenticated;
grant execute on function public.set_route_availability(uuid,boolean,boolean) to authenticated;
