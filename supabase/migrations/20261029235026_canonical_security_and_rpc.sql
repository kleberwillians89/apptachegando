-- Deny execution by default; each public RPC is explicitly granted below.
alter default privileges for role postgres revoke execute on functions from public;

create function public.create_route_buffer(points jsonb,tolerance_meters integer) returns jsonb
language plpgsql security invoker set search_path=public,extensions as $$
declare line geometry; p jsonb;
begin
  if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if jsonb_typeof(points) is distinct from 'array' or jsonb_array_length(points) not between 2 and 2000 or tolerance_meters is null or tolerance_meters not between 50 and 300 then
    raise exception 'Invalid route buffer' using errcode='22023';
  end if;
  for p in select value from jsonb_array_elements(points) loop
    if jsonb_typeof(p) <> 'array' or jsonb_array_length(p) <> 2 or jsonb_typeof(p->0) is distinct from 'number' or jsonb_typeof(p->1) is distinct from 'number'
       or (p->>0)::float8 not between -180 and 180 or (p->>1)::float8 not between -90 and 90 then
      raise exception 'Invalid coordinates' using errcode='22023';
    end if;
  end loop;
  select st_makeline(st_setsrid(st_makepoint((t.p->>0)::float8,(t.p->>1)::float8),4326) order by t.ord)
    into line from jsonb_array_elements(points) with ordinality t(p,ord);
  if st_length(line::geography)<=0 then raise exception 'Distinct points required' using errcode='22023'; end if;
  return (st_asgeojson(st_buffer(line::geography,tolerance_meters)::geometry)::jsonb)->'coordinates';
end $$;

create function public.get_intents_for_route(p_route_id uuid)
returns table(id uuid,consumer_id uuid,lat double precision,lng double precision,status text)
language plpgsql security invoker set search_path=public,extensions as $$
declare r public.vendor_routes;
begin
  select route.* into r from public.vendor_routes route join public.vendors v on v.id=route.vendor_id
    where route.id=p_route_id and v.user_id=auth.uid();
  if not found then raise exception 'Route not available' using errcode='42501'; end if;
  return query select i.id,i.consumer_id,st_y(i.location::geometry),st_x(i.location::geometry),i.status
    from public.purchase_intents i where i.vendor_id=r.vendor_id and (i.route_id is null or i.route_id=r.id)
      and i.status='pending' and i.expires_at>now() and r.status='active'
      and st_covers(r.influence_polygon::geometry,i.location::geometry);
end $$;

-- These narrow privileged writers have no caller-supplied user identity.
-- Ownership is checked inside the function; no direct client table write grants.
create function public.update_vendor_location(p_latitude double precision,p_longitude double precision,p_accuracy_m double precision default null)
returns void language plpgsql security definer set search_path=public,extensions as $$
declare vendor uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if p_latitude is null or p_longitude is null or p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then
    raise exception 'Invalid coordinates' using errcode='22023';
  end if;
  select id into vendor from public.vendors where user_id=auth.uid();
  if vendor is null then raise exception 'Vendor required' using errcode='42501'; end if;
  insert into public.vendor_locations(vendor_id,location,accuracy_m) values(vendor,st_setsrid(st_makepoint(p_longitude,p_latitude),4326)::geography,p_accuracy_m)
    on conflict(vendor_id) do update set location=excluded.location,accuracy_m=excluded.accuracy_m,updated_at=now(),expires_at=now()+interval '2 minutes';
end $$;

create function public.create_purchase_intent(p_vendor_id uuid,p_latitude double precision,p_longitude double precision)
returns uuid language plpgsql security definer set search_path=public,extensions as $$
declare result uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if p_latitude is null or p_longitude is null or p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then
    raise exception 'Invalid coordinates' using errcode='22023';
  end if;
  if not exists(select 1 from public.vendors where id=p_vendor_id and is_online and user_id<>auth.uid()) then
    raise exception 'Vendor not available' using errcode='42501';
  end if;
  -- Serialize duplicate requests from the same consumer, including concurrent transactions.
  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text,0));
  update public.purchase_intents set status='expired' where consumer_id=auth.uid() and status='pending' and expires_at<=now();
  select id into result from public.purchase_intents where consumer_id=auth.uid() and vendor_id=p_vendor_id and status='pending';
  if found then return result; end if;
  if (select count(*) from public.purchase_intents where consumer_id=auth.uid() and status='pending') >= 10 then
    raise exception 'Too many active intents' using errcode='22023';
  end if;
  insert into public.purchase_intents(consumer_id,vendor_id,location) values(auth.uid(),p_vendor_id,st_setsrid(st_makepoint(p_longitude,p_latitude),4326)::geography)
    returning id into result;
  return result;
end $$;

revoke all on function public.create_route_buffer(jsonb,integer) from public,anon,authenticated;
revoke all on function public.get_intents_for_route(uuid) from public,anon,authenticated;
revoke all on function public.update_vendor_location(double precision,double precision,double precision) from public,anon,authenticated;
revoke all on function public.create_purchase_intent(uuid,double precision,double precision) from public,anon,authenticated;
grant execute on function public.create_route_buffer(jsonb,integer), public.get_intents_for_route(uuid),public.update_vendor_location(double precision,double precision,double precision),public.create_purchase_intent(uuid,double precision,double precision) to authenticated;
