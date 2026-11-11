\set ON_ERROR_STOP on
begin;
create function pg_temp.assert_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'ASSERTION FAILED: %',label; end if; end $$;
create function pg_temp.denied(statement text) returns boolean language plpgsql as $$
begin execute statement; return false; exception when insufficient_privilege then return true; end $$;
create function pg_temp.invalid(statement text) returns boolean language plpgsql as $$
begin execute statement; return false; exception when invalid_parameter_value or check_violation or not_null_violation then return true; end $$;

grant execute on function pg_temp.assert_true(boolean,text), pg_temp.denied(text), pg_temp.invalid(text) to anon,authenticated;

select pg_temp.assert_true((select count(*)=4 from public.profiles),'signup provisions four profiles');
select pg_temp.assert_true((select count(*)=2 from public.vendors),'consumer metadata without role never creates vendor');
select pg_temp.assert_true((select bool_and(id<>user_id) from public.vendors),'business and Auth UUIDs differ');
select pg_temp.assert_true((select count(*)=6 from pg_tables where schemaname='public' and rowsecurity),'RLS enabled on every base table');

insert into public.vendor_routes(vendor_id,name,waypoints,active_days) values
('30000000-0000-4000-8000-000000000001','Route A','[{"latitude":-23.5505,"longitude":-46.6333,"orderIndex":0},{"latitude":-23.5505,"longitude":-46.6323,"orderIndex":1}]',array['monday']);

set local role anon;
select pg_temp.assert_true(pg_temp.denied('select * from public.profiles'),'anon cannot read profiles');
select pg_temp.assert_true(pg_temp.denied('select * from public.vendor_catalog'),'anon cannot read catalog');
select pg_temp.assert_true(pg_temp.denied($q$select public.create_route_buffer('[[0,0],[0.1,0.1]]',100)$q$),'anon cannot invoke buffer');
select pg_temp.assert_true(pg_temp.denied($q$select public.create_purchase_intent('30000000-0000-4000-8000-000000000001',0,0)$q$),'anon cannot write intent');
reset role;

set local role authenticated;
select pg_temp.assert_true(pg_temp.denied($q$select public.create_route_buffer('[[0,0],[1,1]]',100)$q$),'authenticated role without JWT identity rejected');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select pg_temp.assert_true((select count(*)=1 from public.profiles),'consumer sees only own profile');
select pg_temp.assert_true((select count(*)=2 from public.vendor_catalog),'consumer sees online vendors');
select pg_temp.assert_true(pg_temp.denied($q$insert into public.products(vendor_id,name,price) values('30000000-0000-4000-8000-000000000001','Attack',1)$q$),'consumer cannot write vendor products');
select pg_temp.assert_true(pg_temp.denied($q$insert into public.purchase_intents(consumer_id,vendor_id,location) values('10000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000001','POINT(0 0)')$q$),'cannot spoof another consumer');
select public.create_purchase_intent('30000000-0000-4000-8000-000000000001',-23.5505,-46.6333);
select public.create_purchase_intent('30000000-0000-4000-8000-000000000001',-23.5505,-46.6333);
select pg_temp.assert_true((select count(*)=1 from public.purchase_intents),'repeated intent is idempotent');
select pg_temp.assert_true(pg_temp.denied($q$select public.update_vendor_location(0,0)$q$),'consumer cannot publish vendor location');
select pg_temp.assert_true(pg_temp.invalid($q$select public.create_route_buffer(null,100)$q$),'null geometry rejected');
select pg_temp.assert_true(pg_temp.invalid($q$select public.create_route_buffer('[[0,0],[1,1]]',null)$q$),'null tolerance rejected');
select pg_temp.assert_true(pg_temp.invalid($q$select public.create_route_buffer('[[0,91],[1,1]]',100)$q$),'out of bounds geometry rejected');
select pg_temp.assert_true(pg_temp.invalid($q$select public.create_route_buffer('[[0,0],[0,0]]',100)$q$),'degenerate geometry rejected');
select pg_temp.assert_true(public.create_route_buffer('[[-46.6333,-23.5505],[-46.6323,-23.5505]]',150) is not null,'valid buffer');

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
select pg_temp.assert_true((select count(*)=0 from public.purchase_intents),'other consumer cannot read intent');
select pg_temp.assert_true((select count(*)=0 from public.vendor_routes),'consumer cannot read routes');

select set_config('request.jwt.claim.sub','20000000-0000-4000-8000-000000000001',true);
select pg_temp.assert_true((select count(*)=1 from public.profiles),'vendor sees only own private profile');
select public.update_vendor_location(-23.5505,-46.6333,5);
select pg_temp.assert_true((select count(*)=1 from public.vendor_locations),'vendor writes own location');
select pg_temp.assert_true((select count(*)=1 from public.route_details),'owner can reload route from persistent view');
select pg_temp.assert_true((select count(*)=1 from public.get_intents_for_route((select id from public.vendor_routes))),'owner RPC gets its own vendor intent');
select pg_temp.assert_true(pg_temp.denied($q$update public.vendors set user_id='10000000-0000-4000-8000-000000000002'$q$),'owner cannot reassign identity');
select pg_temp.assert_true(pg_temp.denied($q$update public.products set vendor_id='30000000-0000-4000-8000-000000000002'$q$),'owner cannot reassign product');
with changed as (update public.products set name='Hacked' where vendor_id='30000000-0000-4000-8000-000000000002' returning *)
select pg_temp.assert_true((select count(*)=0 from changed),'vendor cannot update another vendor product');
select pg_temp.assert_true(pg_temp.denied($q$insert into public.vendor_routes(vendor_id,name,waypoints) values('30000000-0000-4000-8000-000000000002','Attack','[{"latitude":0,"longitude":0},{"latitude":0,"longitude":1}]')$q$),'cannot insert route for another vendor');
select pg_temp.assert_true(pg_temp.invalid($q$update public.vendor_routes set active_days=array['monday','monday']$q$),'duplicate weekdays rejected');
select pg_temp.assert_true(pg_temp.invalid($q$update public.vendor_routes set active_days=array['invalid']$q$),'unknown weekday rejected');
-- Hold owner route id in a session-local setting before changing JWT identity.
select set_config('test.owner_route',(select id::text from public.vendor_routes),true);

select set_config('request.jwt.claim.sub','20000000-0000-4000-8000-000000000002',true);
select pg_temp.assert_true((select count(*)=0 from public.vendor_routes),'vendor cannot read another vendor route');
select pg_temp.assert_true((select count(*)=0 from public.purchase_intents),'vendor cannot read another vendor intents');
select pg_temp.assert_true(pg_temp.denied(format('select public.get_intents_for_route(%L)',current_setting('test.owner_route'))),'RPC rejects another vendor route');
with changed as (update public.vendor_routes set status='ended' where id=current_setting('test.owner_route')::uuid returning *)
select pg_temp.assert_true((select count(*)=0 from changed),'vendor cannot close another vendor route');
select pg_temp.assert_true(pg_temp.denied('select private.handle_new_user()'),'private helper unavailable');

select set_config('request.jwt.claim.sub','20000000-0000-4000-8000-000000000001',true);
update public.vendor_routes set status='ended';
select pg_temp.assert_true((select count(*)=0 from public.get_intents_for_route(current_setting('test.owner_route')::uuid)),'ended route returns no intents');
select pg_temp.assert_true(pg_temp.invalid($q$update public.vendor_routes set status='active'$q$),'ended route cannot restart');
-- Reuse copies the persisted geometry into a NEW journey; ended history is immutable.
insert into public.vendor_routes(vendor_id,name,waypoints,active_days,deviation_tolerance_meters)
select vendor_id,name,waypoints,active_days,deviation_tolerance_meters
from public.vendor_routes where id=current_setting('test.owner_route')::uuid;
select pg_temp.assert_true((select count(*)=1 from public.route_details where status='ended'),'reusing preserves ended journey');
select pg_temp.assert_true((select count(*)=1 from public.route_details where status='active' and id<>current_setting('test.owner_route')::uuid),'reusing creates a separate active journey');
select pg_temp.assert_true((select old.waypoints=new.waypoints and old.active_days=new.active_days and old.name=new.name
  from public.route_details old join public.route_details new on old.vendor_id=new.vendor_id
  where old.status='ended' and new.status='active'),'reusing preserves geometry name and days');
update public.vendors set is_online=false where user_id=auth.uid();
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select pg_temp.assert_true((select count(*)=1 from public.vendor_catalog),'offline vendor hidden');
select pg_temp.assert_true((select count(*)=0 from public.vendor_locations),'offline location hidden');
select pg_temp.assert_true(pg_temp.denied($q$select public.create_purchase_intent('30000000-0000-4000-8000-000000000001',0,0)$q$),'offline vendor cannot receive intent');
reset role;
update public.vendor_locations set expires_at=now()-interval '1 minute';
update public.vendors set is_online=true;
set local role authenticated;
select pg_temp.assert_true((select count(*)=0 from public.vendor_locations),'expired location hidden');
reset role;
rollback;
select 'Security and lifecycle assertions passed' as result;
