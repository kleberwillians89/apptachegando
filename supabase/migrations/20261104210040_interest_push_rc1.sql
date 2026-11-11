begin;
-- RC1: reviewed/local-test contract. DO NOT apply remotely without explicit approval.
-- Additive only: no QA users, products, routes or existing intents are modified/backfilled.
create table private.interest_push_devices (
 owner_id uuid not null references auth.users(id) on delete cascade,
 installation_id uuid not null,
 vendor_id uuid not null references public.vendors(id) on delete cascade,
 session_id uuid not null references auth.sessions(id) on delete cascade,
 token text not null check(length(token) between 20 and 4096),
 token_digest bytea not null,
 binding_version uuid not null,
 active_since timestamptz not null default clock_timestamp(),
 refreshed_at timestamptz not null default clock_timestamp(),
 expires_at timestamptz not null default clock_timestamp()+interval '24 hours',
 revoked_at timestamptz,
 primary key(owner_id,installation_id)
);
create unique index interest_push_live_token on private.interest_push_devices(token_digest) where revoked_at is null;
create index interest_push_vendor on private.interest_push_devices(vendor_id) where revoked_at is null;
create index interest_push_session on private.interest_push_devices(session_id);
create table private.interest_push_revocations (
 owner_id uuid not null references auth.users(id) on delete cascade,
 installation_id uuid not null, binding_version uuid not null,
 revoked_at timestamptz not null default clock_timestamp(),
 primary key(owner_id,installation_id,binding_version)
);
create table private.interest_push_progress (
 route_id uuid primary key references public.vendor_routes(id) on delete cascade,
 vendor_id uuid not null references public.vendors(id) on delete cascade,
 latitude double precision not null, longitude double precision not null,
 accuracy_m double precision not null, recorded_at timestamptz,
 along_m double precision not null, valid boolean not null
);
create table private.interest_push_outbox (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid not null, installation_id uuid not null,
 route_id uuid not null references public.vendor_routes(id) on delete cascade,
 protocol_id uuid not null references public.purchase_intents(id) on delete cascade,
 vendor_id uuid not null references public.vendors(id) on delete cascade,
 created_at timestamptz not null, expires_at timestamptz not null,
 status text not null default 'pending' check(status in ('pending','processing','provider-accepted','expired','ineligible','unbound','invalid-token','failed')),
 attempts integer not null default 0, next_attempt_at timestamptz not null default clock_timestamp(),
 lease_until timestamptz, claim_token uuid, provider_message_id text,
 unique(protocol_id,installation_id),
 foreign key(owner_id,installation_id) references private.interest_push_devices(owner_id,installation_id) on delete cascade
);
create index interest_push_due on private.interest_push_outbox(next_attempt_at,expires_at,id) where status in ('pending','processing');
create index interest_push_outbox_owner on private.interest_push_outbox(owner_id,installation_id);
create index interest_push_outbox_route on private.interest_push_outbox(route_id);
create index interest_push_outbox_vendor on private.interest_push_outbox(vendor_id);
alter table private.interest_push_devices enable row level security;
alter table private.interest_push_revocations enable row level security;
alter table private.interest_push_progress enable row level security;
alter table private.interest_push_outbox enable row level security;
revoke all on private.interest_push_devices,private.interest_push_revocations,private.interest_push_progress,private.interest_push_outbox from public,anon,authenticated,service_role;

create function private.push_session_live(p_owner uuid,p_session uuid) returns boolean
language sql stable security definer set search_path='' as $$
 select exists(select 1 from auth.sessions s where s.id=p_session and s.user_id=p_owner and (s.not_after is null or s.not_after>now()));
$$;
create function private.push_actor_session() returns uuid language plpgsql stable security definer set search_path='' as $$
declare sid uuid;
begin
 if auth.uid() is null or auth.jwt()->>'is_anonymous'='true' then raise exception 'Authentication required' using errcode='42501'; end if;
 begin sid:=nullif(auth.jwt()->>'session_id','')::uuid; exception when invalid_text_representation then sid:=null; end;
 if not private.push_session_live(auth.uid(),sid) then raise exception 'Live session required' using errcode='42501'; end if;
 return sid;
end $$;

-- Exact planar projection policy used by common RouteProgress: 111195*cos(origin),
-- every clamped segment, tie +2m and >30m along-distance ambiguity. No ST_LineLocatePoint shortcut.
create function private.push_projection(p_points jsonb,p_lat float8,p_lng float8)
returns table(along_m float8,cross_m float8,ambiguous boolean)
language plpgsql immutable security invoker set search_path='' as $$
declare ordered jsonb; n int; origin_lat float8; origin_lng float8; scale float8;
 ax float8; ay float8; bx float8; by_ float8; px float8; py float8; dx float8; dy float8; sq float8;
 t float8; dist float8; at_m float8; prefix_m float8:=0; best float8:='Infinity'; best_at float8:=0;
 distances float8[]:='{}'; positions float8[]:='{}'; min_at float8:='Infinity'; max_at float8:='-Infinity'; j int; p jsonb;
begin
 if p_lat is null or p_lng is null or p_lat not between -90 and 90 or p_lng not between -180 and 180 or jsonb_typeof(p_points)<>'array' then return; end if;
 n:=jsonb_array_length(p_points); if n not between 2 and 2000 then return; end if;
 select jsonb_agg(value order by (value->>'orderIndex')::integer) into ordered from jsonb_array_elements(p_points);
 if (select count(distinct (value->>'orderIndex')::integer) from jsonb_array_elements(ordered))<>n then return; end if;
 origin_lat:=(ordered->0->>'latitude')::float8; origin_lng:=(ordered->0->>'longitude')::float8;
 scale:=111195.0*cos(origin_lat*pi()/180.0); px:=(p_lng-origin_lng)*scale; py:=(p_lat-origin_lat)*111195.0;
 for j in 0..n-1 loop
  p:=ordered->j;
  if (p->>'latitude')::float8 not between -90 and 90 or (p->>'longitude')::float8 not between -180 and 180 then return; end if;
  -- Same 50 km urban envelope as RouteProgress (spherical haversine).
  if 2*6371000*asin(least(1.0,sqrt(power(sin(((p->>'latitude')::float8-origin_lat)*pi()/360),2)+cos(origin_lat*pi()/180)*cos((p->>'latitude')::float8*pi()/180)*power(sin(((p->>'longitude')::float8-origin_lng)*pi()/360),2))))>=50000 then return; end if;
 end loop;
 for j in 0..n-2 loop
  ax:=((ordered->j->>'longitude')::float8-origin_lng)*scale; ay:=((ordered->j->>'latitude')::float8-origin_lat)*111195;
  bx:=((ordered->(j+1)->>'longitude')::float8-origin_lng)*scale; by_:=((ordered->(j+1)->>'latitude')::float8-origin_lat)*111195;
  dx:=bx-ax; dy:=by_-ay; sq:=dx*dx+dy*dy;
  if sq>=0.0001 then
   t:=greatest(0.0,least(1.0,((px-ax)*dx+(py-ay)*dy)/sq));
   dist:=sqrt(power(px-ax-t*dx,2)+power(py-ay-t*dy,2)); at_m:=prefix_m+t*sqrt(sq);
   distances:=array_append(distances,dist); positions:=array_append(positions,at_m);
   if dist<best then best:=dist;best_at:=at_m; end if;
  end if;
  prefix_m:=prefix_m+sqrt(sq);
 end loop;
 if prefix_m<=1 or array_length(distances,1) is null then return; end if;
 for j in 1..array_length(distances,1) loop
  if distances[j]<=best+2 then min_at:=least(min_at,positions[j]);max_at:=greatest(max_at,positions[j]); end if;
 end loop;
 return query select best_at,best,(max_at-min_at)>30;
exception when invalid_text_representation or numeric_value_out_of_range then return;
end $$;

create function private.push_register(p_installation_id uuid,p_token text,p_binding_version uuid) returns uuid
language plpgsql security definer set search_path='' as $$
declare sid uuid:=private.push_actor_session(); vid uuid; d private.interest_push_devices%rowtype;
begin
 if p_installation_id is null or p_binding_version is null or p_token is null or length(p_token) not between 20 and 4096 then raise exception 'Invalid device' using errcode='22023'; end if;
 select id into vid from public.vendors where user_id=auth.uid();
 if vid is null then raise exception 'Vendor required' using errcode='42501'; end if;
 perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text||':'||p_installation_id::text,0));
 if exists(select 1 from private.interest_push_revocations where owner_id=auth.uid() and installation_id=p_installation_id and binding_version=p_binding_version) then raise exception 'Revoked binding' using errcode='42501'; end if;
 select * into d from private.interest_push_devices where owner_id=auth.uid() and installation_id=p_installation_id for update;
 if found and d.binding_version=p_binding_version and (d.session_id<>sid or d.token<>p_token or d.revoked_at is not null) then raise exception 'Binding must rotate' using errcode='42501'; end if;
 -- A known token does not authorize taking over a live device belonging to another user.
 update private.interest_push_devices set revoked_at=clock_timestamp()
 where token_digest=pg_catalog.sha256(pg_catalog.convert_to(p_token,'UTF8')) and revoked_at is null
 and (expires_at<=clock_timestamp() or not private.push_session_live(owner_id,session_id));
 if exists(select 1 from private.interest_push_devices where token_digest=pg_catalog.sha256(pg_catalog.convert_to(p_token,'UTF8')) and revoked_at is null and (owner_id<>auth.uid() or installation_id<>p_installation_id)) then raise exception 'Device unavailable' using errcode='42501'; end if;
 insert into private.interest_push_devices(owner_id,installation_id,vendor_id,session_id,token,token_digest,binding_version)
 values(auth.uid(),p_installation_id,vid,sid,p_token,pg_catalog.sha256(pg_catalog.convert_to(p_token,'UTF8')),p_binding_version)
 on conflict(owner_id,installation_id) do update set
  token=excluded.token,token_digest=excluded.token_digest,binding_version=excluded.binding_version,session_id=excluded.session_id,
  active_since=case when interest_push_devices.revoked_at is null and interest_push_devices.expires_at>clock_timestamp() and interest_push_devices.session_id=excluded.session_id then interest_push_devices.active_since else clock_timestamp() end,
  refreshed_at=clock_timestamp(),expires_at=clock_timestamp()+interval '24 hours',revoked_at=null;
 return p_installation_id;
end $$;
create function private.push_revoke(p_installation_id uuid,p_binding_version uuid) returns uuid
language plpgsql security definer set search_path='' as $$
begin
 perform private.push_actor_session();
 if p_installation_id is null or p_binding_version is null then raise exception 'Invalid device' using errcode='22023'; end if;
 perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text||':'||p_installation_id::text,0));
 insert into private.interest_push_revocations values(auth.uid(),p_installation_id,p_binding_version,clock_timestamp()) on conflict do nothing;
 update private.interest_push_devices set revoked_at=clock_timestamp()
 where owner_id=auth.uid() and installation_id=p_installation_id and binding_version=p_binding_version;
 return p_installation_id;
end $$;

create function private.push_publish(p_route_id uuid,p_latitude float8,p_longitude float8,p_accuracy_m float8,p_recorded_at timestamptz) returns uuid
language plpgsql security definer set search_path='' as $$
declare r public.vendor_routes%rowtype; old private.interest_push_progress%rowtype; pr record; ok boolean; now_ timestamptz:=clock_timestamp();
begin
 perform private.push_actor_session();
 if p_recorded_at is null or p_recorded_at>now_ or p_recorded_at<now_-interval '30 seconds' or p_accuracy_m is null or p_accuracy_m not between 0 and 50 or p_latitude is null or p_longitude is null or p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then raise exception 'Invalid GPS fix' using errcode='22023'; end if;
 select vr.* into r from public.vendor_routes vr join public.vendors v on v.id=vr.vendor_id where vr.id=p_route_id and v.user_id=auth.uid() and v.is_online and vr.status='active' for update of vr,v;
 if r.id is null then raise exception 'Route unavailable' using errcode='42501'; end if;
 select * into old from private.interest_push_progress where route_id=r.id for update;
 if old.recorded_at is not null and p_recorded_at<=old.recorded_at then return r.id; end if;
 select * into pr from private.push_projection(r.waypoints,p_latitude,p_longitude);
 ok:=pr.along_m is not null and not pr.ambiguous and pr.cross_m<=r.deviation_tolerance_meters;
 if old.recorded_at is not null then
  ok:=ok and pr.along_m>=old.along_m-30 and pr.along_m-old.along_m<=15*extract(epoch from p_recorded_at-old.recorded_at)+2*p_accuracy_m;
 end if;
 -- Invalid jumps invalidate push eligibility; they never refresh discovery with a teleport.
 insert into private.interest_push_progress values(r.id,r.vendor_id,p_latitude,p_longitude,p_accuracy_m,case when ok then p_recorded_at else null end,coalesce(pr.along_m,0),coalesce(ok,false))
 on conflict(route_id) do update set latitude=excluded.latitude,longitude=excluded.longitude,accuracy_m=excluded.accuracy_m,
  recorded_at=case when excluded.valid then excluded.recorded_at else interest_push_progress.recorded_at end,
  along_m=case when excluded.valid then greatest(interest_push_progress.along_m,excluded.along_m) else interest_push_progress.along_m end,valid=excluded.valid;
 if ok then
  insert into public.vendor_locations(vendor_id,location,accuracy_m,updated_at,expires_at)
  values(r.vendor_id,extensions.st_setsrid(extensions.st_makepoint(p_longitude,p_latitude),4326)::extensions.geography,p_accuracy_m,p_recorded_at,p_recorded_at+interval '2 minutes')
  on conflict(vendor_id) do update set location=excluded.location,accuracy_m=excluded.accuracy_m,updated_at=excluded.updated_at,expires_at=excluded.expires_at;
 end if;
 return r.id;
end $$;

create function private.push_eligible(p_route uuid,p_intent uuid,p_owner uuid,p_installation uuid,p_version uuid default null) returns boolean
language plpgsql security definer set search_path='' as $$
declare d private.interest_push_devices%rowtype; r public.vendor_routes%rowtype; i public.purchase_intents%rowtype; g private.interest_push_progress%rowtype; pr record;
begin
 select * into d from private.interest_push_devices where owner_id=p_owner and installation_id=p_installation and revoked_at is null and expires_at>clock_timestamp();
 if d.owner_id is null or (p_version is not null and p_version<>d.binding_version) or not private.push_session_live(d.owner_id,d.session_id) then return false; end if;
 select vr.* into r from public.vendor_routes vr join public.vendors v on v.id=vr.vendor_id where vr.id=p_route and vr.vendor_id=d.vendor_id and v.user_id=d.owner_id and v.is_online and vr.status='active';
 if r.id is null then return false; end if;
 select * into i from public.purchase_intents where id=p_intent and route_id=r.id and vendor_id=r.vendor_id and status='pending' and expires_at>clock_timestamp() and created_at>=d.active_since and created_at>=clock_timestamp()-interval '30 seconds';
 if i.id is null then return false; end if;
 select * into g from private.interest_push_progress where route_id=r.id and valid and recorded_at between clock_timestamp()-interval '30 seconds' and clock_timestamp();
 if g.route_id is null then return false; end if;
 -- Legacy writes cannot lend an unrelated recent timestamp to the verified fix.
 if not exists(select 1 from public.vendor_locations l where l.vendor_id=r.vendor_id and l.updated_at=g.recorded_at and l.expires_at>clock_timestamp() and l.accuracy_m=g.accuracy_m and extensions.st_dwithin(l.location,extensions.st_setsrid(extensions.st_makepoint(g.longitude,g.latitude),4326)::extensions.geography,0.01)) then return false; end if;
 select * into pr from private.push_projection(r.waypoints,extensions.st_y(i.location::extensions.geometry),extensions.st_x(i.location::extensions.geometry));
 return coalesce(not pr.ambiguous and pr.cross_m<=r.deviation_tolerance_meters and pr.along_m>=g.along_m,false);
end $$;

create function private.push_enqueue() returns trigger language plpgsql security definer set search_path='' as $$
declare d record;
begin
 if new.status<>'pending' or new.route_id is null or new.created_at<clock_timestamp()-interval '30 seconds' then return new; end if;
 for d in select owner_id,installation_id from private.interest_push_devices where vendor_id=new.vendor_id and revoked_at is null and expires_at>clock_timestamp() loop
  if private.push_eligible(new.route_id,new.id,d.owner_id,d.installation_id) then
   insert into private.interest_push_outbox(owner_id,installation_id,route_id,protocol_id,vendor_id,created_at,expires_at)
   values(d.owner_id,d.installation_id,new.route_id,new.id,new.vendor_id,new.created_at,new.created_at+interval '30 seconds') on conflict(protocol_id,installation_id) do nothing;
  end if;
 end loop;
 return new;
end $$;
create trigger enqueue_interest_push after insert on public.purchase_intents for each row execute function private.push_enqueue();

create function public.register_interest_push_device(p_installation_id uuid,p_token text,p_binding_version uuid) returns uuid language sql security invoker set search_path='' as $$ select private.push_register(p_installation_id,p_token,p_binding_version) $$;
create function public.revoke_interest_push_device(p_installation_id uuid,p_binding_version uuid) returns uuid language sql security invoker set search_path='' as $$ select private.push_revoke(p_installation_id,p_binding_version) $$;
create function public.publish_route_location(p_route_id uuid,p_latitude float8,p_longitude float8,p_accuracy_m float8,p_recorded_at timestamptz) returns uuid language sql security invoker set search_path='' as $$ select private.push_publish(p_route_id,p_latitude,p_longitude,p_accuracy_m,p_recorded_at) $$;

-- Worker-only API. Atomic leases + claim UUID keep stale workers from acknowledging a new claim.
create function private.push_claim(p_job_id uuid default null) returns jsonb language plpgsql security definer set search_path='' as $$
declare j private.interest_push_outbox%rowtype;
begin
 update private.interest_push_outbox set status='expired',claim_token=null,lease_until=null where status in ('pending','processing') and expires_at<=clock_timestamp();
 select * into j from private.interest_push_outbox where (p_job_id is null or id=p_job_id) and expires_at>clock_timestamp() and attempts<5
 and ((status='pending' and next_attempt_at<=clock_timestamp()) or (status='processing' and lease_until<=clock_timestamp()))
 order by next_attempt_at,id for update skip locked limit 1;
 if j.id is null then return null; end if;
 update private.interest_push_outbox set status='processing',attempts=attempts+1,claim_token=gen_random_uuid(),lease_until=clock_timestamp()+interval '20 seconds' where id=j.id returning * into j;
 return jsonb_build_object('id',j.id,'ownerId',j.owner_id,'vendorId',j.vendor_id,'routeId',j.route_id,'protocolId',j.protocol_id,'installationId',j.installation_id,'claimToken',j.claim_token,'createdAtMillis',floor(extract(epoch from j.created_at)*1000),'expiresAtMillis',floor(extract(epoch from j.expires_at)*1000));
end $$;
create function private.push_binding(p_job_id uuid,p_claim_token uuid) returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('ownerId',d.owner_id,'vendorId',d.vendor_id,'token',d.token,'version',d.binding_version,'active',true)
 from private.interest_push_outbox j join private.interest_push_devices d on d.owner_id=j.owner_id and d.installation_id=j.installation_id
 where j.id=p_job_id and j.status='processing' and j.claim_token=p_claim_token and j.lease_until>now() and j.expires_at>now() and d.revoked_at is null and d.expires_at>now() and private.push_session_live(d.owner_id,d.session_id);
$$;
create function private.push_job_eligible(p_job_id uuid,p_claim_token uuid) returns boolean language sql stable security definer set search_path='' as $$
 select coalesce((select private.push_eligible(route_id,protocol_id,owner_id,installation_id) from private.interest_push_outbox where id=p_job_id and claim_token=p_claim_token and status='processing' and lease_until>now() and expires_at>now()),false);
$$;
create function private.push_finish(p_job_id uuid,p_claim_token uuid,p_result text,p_provider_message_id text default null) returns boolean language plpgsql security definer set search_path='' as $$
begin
 if p_result not in ('provider-accepted','expired','ineligible','unbound','invalid-token','failed') then raise exception 'Invalid result' using errcode='22023'; end if;
 update private.interest_push_outbox set status=p_result,provider_message_id=left(p_provider_message_id,512),claim_token=null,lease_until=null where id=p_job_id and claim_token=p_claim_token and status='processing' and lease_until>clock_timestamp();
 return found;
end $$;
create function private.push_retry(p_job_id uuid,p_claim_token uuid,p_delay_seconds integer default null) returns boolean language plpgsql security definer set search_path='' as $$
begin
 update private.interest_push_outbox set status=case when expires_at<=clock_timestamp() then 'expired' when attempts>=5 then 'failed' else 'pending' end,
 next_attempt_at=least(expires_at,clock_timestamp()+make_interval(secs=>greatest(1,least(3600,coalesce(p_delay_seconds,least(16,power(2,attempts)::integer)))))),claim_token=null,lease_until=null
 where id=p_job_id and claim_token=p_claim_token and status='processing' and lease_until>clock_timestamp();
 return found;
end $$;
create function private.push_invalidate(p_owner_id uuid,p_installation_id uuid,p_binding_version uuid) returns boolean language plpgsql security definer set search_path='' as $$
begin
 update private.interest_push_devices set revoked_at=clock_timestamp() where owner_id=p_owner_id and installation_id=p_installation_id and binding_version=p_binding_version and revoked_at is null;
 return found;
end $$;
create function public.claim_interest_push_job(p_job_id uuid default null) returns jsonb language sql security invoker set search_path='' as $$select private.push_claim(p_job_id)$$;
create function public.interest_push_job_binding(p_job_id uuid,p_claim_token uuid) returns jsonb language sql stable security invoker set search_path='' as $$select private.push_binding(p_job_id,p_claim_token)$$;
create function public.interest_push_job_eligible(p_job_id uuid,p_claim_token uuid) returns boolean language sql stable security invoker set search_path='' as $$select private.push_job_eligible(p_job_id,p_claim_token)$$;
create function public.finish_interest_push_job(p_job_id uuid,p_claim_token uuid,p_result text,p_provider_message_id text default null) returns boolean language sql security invoker set search_path='' as $$select private.push_finish(p_job_id,p_claim_token,p_result,p_provider_message_id)$$;
create function public.retry_interest_push_job(p_job_id uuid,p_claim_token uuid,p_delay_seconds integer default null) returns boolean language sql security invoker set search_path='' as $$select private.push_retry(p_job_id,p_claim_token,p_delay_seconds)$$;
create function public.invalidate_interest_push_device(p_owner_id uuid,p_installation_id uuid,p_binding_version uuid) returns boolean language sql security invoker set search_path='' as $$select private.push_invalidate(p_owner_id,p_installation_id,p_binding_version)$$;

-- Explicit ACL for every function created above; no default PUBLIC execute is left behind.
do $$ declare f record;begin
 for f in select p.oid::regprocedure as signature from pg_proc p join pg_namespace n on n.oid=p.pronamespace
 where (n.nspname='private' and p.proname in ('push_session_live','push_actor_session','push_projection','push_register','push_revoke','push_publish','push_eligible','push_enqueue','push_claim','push_binding','push_job_eligible','push_finish','push_retry','push_invalidate'))
 or (n.nspname='public' and p.proname in ('register_interest_push_device','revoke_interest_push_device','publish_route_location','verify_interest_push','claim_interest_push_job','interest_push_job_binding','interest_push_job_eligible','finish_interest_push_job','retry_interest_push_job','invalidate_interest_push_device')) loop
 execute format('revoke all on function %s from public,anon,authenticated,service_role',f.signature);
 end loop;
end $$;
grant usage on schema private to authenticated,service_role;
grant execute on function private.push_actor_session(),private.push_register(uuid,text,uuid),private.push_revoke(uuid,uuid),private.push_publish(uuid,float8,float8,float8,timestamptz) to authenticated;
create function private.push_verify(p_route uuid,p_intent uuid,p_installation uuid,p_version uuid) returns boolean language plpgsql stable security definer set search_path='' as $$
declare sid uuid:=private.push_actor_session();
begin
 if not exists(select 1 from private.interest_push_devices where owner_id=auth.uid() and installation_id=p_installation and binding_version=p_version and session_id=sid) then return false; end if;
 return private.push_eligible(p_route,p_intent,auth.uid(),p_installation,p_version);
end $$;
revoke all on function private.push_verify(uuid,uuid,uuid,uuid) from public,anon,authenticated,service_role;
grant execute on function private.push_verify(uuid,uuid,uuid,uuid) to authenticated;
create or replace function public.verify_interest_push(p_route_id uuid,p_intent_id uuid,p_installation_id uuid,p_binding_version uuid) returns boolean language sql stable security invoker set search_path='' as $$select private.push_verify(p_route_id,p_intent_id,p_installation_id,p_binding_version)$$;
revoke all on function public.verify_interest_push(uuid,uuid,uuid,uuid) from public,anon,authenticated,service_role;
grant execute on function public.register_interest_push_device(uuid,text,uuid),public.revoke_interest_push_device(uuid,uuid),public.publish_route_location(uuid,float8,float8,float8,timestamptz),public.verify_interest_push(uuid,uuid,uuid,uuid) to authenticated;
grant execute on function private.push_claim(uuid),private.push_binding(uuid,uuid),private.push_job_eligible(uuid,uuid),private.push_finish(uuid,uuid,text,text),private.push_retry(uuid,uuid,integer),private.push_invalidate(uuid,uuid,uuid) to service_role;
grant execute on function public.claim_interest_push_job(uuid),public.interest_push_job_binding(uuid,uuid),public.interest_push_job_eligible(uuid,uuid),public.finish_interest_push_job(uuid,uuid,text,text),public.retry_interest_push_job(uuid,uuid,integer),public.invalidate_interest_push_device(uuid,uuid,uuid) to service_role;

notify pgrst, 'reload schema';
commit;
