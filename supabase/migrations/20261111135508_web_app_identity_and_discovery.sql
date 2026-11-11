begin;
-- LOCAL REVIEW ONLY. Depends on the RC1 projection contract; never apply automatically.
-- No backfill or modification of existing QA identities, routes, products or interests.
alter table public.profiles add column signup_role text check(signup_role in ('consumer','vendor'));

create or replace function private.handle_new_user() returns trigger language plpgsql security definer set search_path='' as $$
begin
 insert into public.profiles(id,full_name,phone,signup_role)
 values(new.id,left(coalesce(new.raw_user_meta_data->>'name',new.raw_user_meta_data->>'full_name',''),160),
 left(new.raw_user_meta_data->>'phone',32),case when new.raw_user_meta_data->>'role' in ('consumer','vendor') then new.raw_user_meta_data->>'role' else null end);
 -- Metadata is only an enrollment hint. All new professional links require a confirmed,
 -- authenticated complete_my_profile call. Existing vendors are left untouched.
 return new;
end $$;

create function private.confirmed_actor() returns uuid language plpgsql stable security definer set search_path='' as $$
declare actor uuid:=auth.uid();
begin
 perform private.push_actor_session();
 if actor is null or auth.jwt()->>'is_anonymous'='true' or not exists(select 1 from auth.users where id=actor and email_confirmed_at is not null) then
  raise exception 'Confirmed account required' using errcode='42501';
 end if;
 return actor;
end $$;
create function private.my_account_profile() returns jsonb language plpgsql stable security definer set search_path='' as $$
declare actor uuid:=private.confirmed_actor(); p public.profiles%rowtype; v public.vendors%rowtype;
begin
 select * into p from public.profiles where id=actor;
 if p.id is null then raise exception 'Profile unavailable' using errcode='P0001'; end if;
 select * into v from public.vendors where user_id=actor;
 return jsonb_build_object('user_id',actor,'role',case when v.id is not null then 'vendor' when p.signup_role='vendor' then 'vendor_incomplete' else 'consumer' end,
 'vendor_id',v.id,'name',p.full_name,'business_name',v.name,'category',v.category);
end $$;
create function private.complete_my_profile(p_kind text,p_name text,p_business_name text,p_category text,p_phone text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=private.confirmed_actor(); existing uuid;
begin
 if p_kind is null or p_kind not in ('consumer','vendor') or p_name is null or length(trim(p_name)) not between 1 and 160 or length(coalesce(p_phone,''))>32 then
  raise exception 'Invalid profile' using errcode='22023'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('profile:'||actor::text,0));
 select id into existing from public.vendors where user_id=actor;
 if p_kind='consumer' and (existing is not null or exists(select 1 from public.profiles where id=actor and signup_role='vendor')) then
  raise exception 'Professional profile cannot become consumer through completion' using errcode='22023'; end if;
 if p_kind='vendor' and (p_business_name is null or length(trim(p_business_name)) not between 1 and 160 or p_category is null or p_category not in
 ('Pães','Frutas','Salgados','Doces de padaria','Lanches naturais','Sorvetes','Temperos','Produtos de limpeza prontos')) then
  raise exception 'Invalid business profile' using errcode='22023'; end if;
 -- Repeat completion never overwrites an existing professional or its products/routes.
 if existing is not null then return private.my_account_profile(); end if;
 insert into public.profiles(id,full_name,phone,signup_role) values(actor,trim(p_name),nullif(trim(p_phone),''),p_kind)
 on conflict(id) do update set full_name=excluded.full_name,phone=coalesce(excluded.phone,public.profiles.phone),signup_role=excluded.signup_role;
 if p_kind='vendor' then
  insert into public.vendors(user_id,name,category) values(actor,trim(p_business_name),p_category) on conflict(user_id) do nothing;
 end if;
 return private.my_account_profile();
end $$;
create function public.get_my_account_profile() returns jsonb language sql stable security invoker set search_path='' as $$ select private.my_account_profile() $$;
create function public.complete_my_profile(p_kind text,p_name text,p_business_name text default null,p_category text default null,p_phone text default null)
returns jsonb language sql security invoker set search_path='' as $$select private.complete_my_profile(p_kind,p_name,p_business_name,p_category,p_phone)$$;

-- Request only: this never deletes Auth/users/business rows. Fulfillment is a separate authorized server operation.
create table private.account_deletion_requests(user_id uuid primary key references auth.users(id) on delete cascade,requested_at timestamptz not null default now());
alter table private.account_deletion_requests enable row level security;
revoke all on private.account_deletion_requests from public,anon,authenticated,service_role;
create function private.request_account_deletion() returns jsonb language plpgsql security definer set search_path='' as $$
declare actor uuid:=private.confirmed_actor(); requested timestamptz;
begin
 insert into private.account_deletion_requests(user_id) values(actor) on conflict(user_id) do nothing;
 select requested_at into requested from private.account_deletion_requests where user_id=actor;
 return jsonb_build_object('status','requested','requested_at',requested);
end $$;
create function public.request_account_deletion() returns jsonb language sql security invoker set search_path='' as $$ select private.request_account_deletion() $$;

-- Shared read-only projection. No route geometry, private progress, consumer coordinates or user IDs leave this helper.
create function private.route_interest_condition(p_route uuid,p_point extensions.geography) returns text
language plpgsql stable security definer set search_path='' as $$
declare r public.vendor_routes%rowtype; l public.vendor_locations%rowtype; g private.interest_push_progress%rowtype; vp record; cp record; progress float8;
begin
 select vr.* into r from public.vendor_routes vr join public.vendors v on v.id=vr.vendor_id where vr.id=p_route and vr.status='active' and v.is_online;
 if r.id is null then return 'awaiting_availability'; end if;
 select * into l from public.vendor_locations where vendor_id=r.vendor_id and expires_at>now() and updated_at between now()-interval '30 seconds' and now()
 and accuracy_m between 0 and 50;
 if l.vendor_id is null then return 'vendor_location_missing'; end if;
 select * into g from private.interest_push_progress where route_id=r.id;
 if g.route_id is not null then
  if not g.valid or g.recorded_at is distinct from l.updated_at or g.recorded_at<now()-interval '30 seconds' or
   not extensions.st_dwithin(l.location,extensions.st_setsrid(extensions.st_makepoint(g.longitude,g.latitude),4326)::extensions.geography,0.01) then return 'vendor_location_missing'; end if;
  progress:=g.along_m;
 else
  -- Compatibility with existing foreground publisher until strict GPS is activated.
  select * into vp from private.push_projection(r.waypoints,extensions.st_y(l.location::extensions.geometry),extensions.st_x(l.location::extensions.geometry));
  if vp.along_m is null or vp.ambiguous or vp.cross_m>r.deviation_tolerance_meters then return 'awaiting_location'; end if;
  progress:=vp.along_m;
 end if;
 select * into cp from private.push_projection(r.waypoints,extensions.st_y(p_point::extensions.geometry),extensions.st_x(p_point::extensions.geometry));
 if cp.along_m is null or cp.ambiguous then return 'ambiguous_route'; end if;
 if cp.cross_m>r.deviation_tolerance_meters then return 'outside_route'; end if;
 if cp.along_m<progress then return 'behind_route'; end if;
 return 'eligible';
end $$;
create function private.my_interest_state(p_intent_id uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare actor uuid:=private.confirmed_actor(); i public.purchase_intents%rowtype; condition text;
begin
 select * into i from public.purchase_intents where id=p_intent_id and consumer_id=actor;
 if i.id is null then raise exception 'Interest unavailable' using errcode='42501'; end if;
 condition:=case when i.status='cancelled' then 'cancelled' when i.status<>'pending' or i.expires_at<=now() then 'expired'
 when i.route_id is null then 'awaiting_availability' else private.route_interest_condition(i.route_id,i.location) end;
 return jsonb_build_object('id',i.id,'vendor_id',i.vendor_id,'status',case when condition='expired' then 'expired' else i.status end,'expires_at',i.expires_at,'eligibility',condition);
end $$;
create function public.get_my_interest_state(p_intent_id uuid) returns jsonb language sql stable security invoker set search_path='' as $$select private.my_interest_state(p_intent_id)$$;

create function private.discover_vendors(p_latitude float8,p_longitude float8,p_query text,p_category text,p_offset integer,p_limit integer,p_radius_m integer)
returns table(id uuid,name text,description text,category text,is_online boolean,distance text,rating text,review_count integer,latitude float8,longitude float8,products jsonb)
language plpgsql stable security definer set search_path='' as $$
declare actor uuid:=private.confirmed_actor(); q text:=public.catalog_term(p_query); c text:=public.catalog_term(p_category); keys text[]; point extensions.geography;
begin
 if p_latitude is null or p_longitude is null or p_latitude not between -90 and 90 or p_longitude not between -180 and 180 or p_offset is null or p_offset not between 0 and 10000 or
 p_limit is null or p_limit not between 1 and 50 or p_radius_m is null or p_radius_m not between 100 and 50000 or length(q)>120 then raise exception 'Invalid discovery' using errcode='22023'; end if;
 keys:=case c when '' then array[''] when 'paes' then array['paes','pao'] when 'frutas' then array['fruta','hortifruti']
 when 'salgados' then array['salgado'] when 'doces de padaria' then array['doce','bolo'] when 'lanches naturais' then array['lanche natural','lanches naturais','sanduiche natural']
 when 'sorvetes' then array['sorvete','picole'] when 'temperos' then array['tempero'] when 'produtos de limpeza prontos' then array['limpeza','detergente','sabao'] else null end;
 if keys is null then raise exception 'Unknown category' using errcode='22023'; end if;
 point:=extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography;
 return query select v.id,v.name,v.description,v.category,v.is_online,round(extensions.st_distance(l.location,point))::text||' m',''::text,0,
 extensions.st_y(l.location::extensions.geometry),extensions.st_x(l.location::extensions.geometry),
 coalesce((select jsonb_agg(jsonb_build_object('id',p.id,'name',p.name,'price','') order by p.id) from public.products p where p.vendor_id=v.id and p.is_active),'[]'::jsonb)
 from public.vendors v join public.vendor_locations l on l.vendor_id=v.id join public.vendor_routes r on r.vendor_id=v.id and r.status='active'
 where v.is_online and v.user_id<>actor and l.expires_at>now() and l.updated_at>=now()-interval '30 seconds'
 and extensions.st_dwithin(l.location,point,p_radius_m)
 and extensions.st_dwithin(r.influence_polygon,point,0)
 and private.route_interest_condition(r.id,point)='eligible'
 and (q='' or strpos(public.catalog_term(v.name||' '||v.description||' '||v.category),q)>0 or exists(select 1 from public.products p where p.vendor_id=v.id and p.is_active and strpos(public.catalog_term(p.name),q)>0))
 and exists(select 1 from unnest(keys) k where k='' or strpos(public.catalog_term(v.category),k)>0 or exists(select 1 from public.products p where p.vendor_id=v.id and p.is_active and strpos(public.catalog_term(p.name),k)>0))
 order by extensions.st_distance(l.location,point),v.id offset p_offset limit p_limit;
end $$;
create function public.discover_vendors(p_latitude float8,p_longitude float8,p_query text default '',p_category text default '',p_offset integer default 0,p_limit integer default 50,p_radius_m integer default 5000)
returns table(id uuid,name text,description text,category text,is_online boolean,distance text,rating text,review_count integer,latitude float8,longitude float8,products jsonb)
language sql stable security invoker set search_path='' as $$select * from private.discover_vendors(p_latitude,p_longitude,p_query,p_category,p_offset,p_limit,p_radius_m)$$;

-- Deny defaults explicitly, including helpers not intended as client APIs.
do $$ declare f record; begin for f in select p.oid::regprocedure as signature from pg_proc p join pg_namespace n on n.oid=p.pronamespace
 where (n.nspname='private' and p.proname in ('confirmed_actor','my_account_profile','complete_my_profile','request_account_deletion','route_interest_condition','my_interest_state','discover_vendors'))
 or (n.nspname='public' and p.proname in ('get_my_account_profile','complete_my_profile','request_account_deletion','get_my_interest_state','discover_vendors')) loop
 execute format('revoke all on function %s from public,anon,authenticated,service_role',f.signature); end loop; end $$;
grant usage on schema private to authenticated;
grant execute on function private.my_account_profile(),private.complete_my_profile(text,text,text,text,text),private.request_account_deletion(),private.my_interest_state(uuid),private.discover_vendors(float8,float8,text,text,integer,integer,integer) to authenticated;
grant execute on function public.get_my_account_profile(),public.complete_my_profile(text,text,text,text,text),public.request_account_deletion(),public.get_my_interest_state(uuid),public.discover_vendors(float8,float8,text,text,integer,integer,integer) to authenticated;
notify pgrst,'reload schema';
commit;
