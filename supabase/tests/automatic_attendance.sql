\set ON_ERROR_STOP on
begin;
create function pg_temp.assert_true(ok boolean,label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'ASSERTION FAILED: %',label; end if; end $$;
create function pg_temp.denied(statement text) returns boolean language plpgsql as $$
begin execute statement; return false; exception when insufficient_privilege then return true; end $$;
grant execute on function pg_temp.assert_true(boolean,text),pg_temp.denied(text) to anon,authenticated;

select pg_temp.assert_true((select bool_and(not prosecdef) from pg_proc where proname in ('get_route_attendance','set_route_availability','cancel_purchase_intent','search_vendor_catalog')),'new RPCs are security invoker');
select pg_temp.assert_true((select count(*)=2 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename in ('purchase_intents','vendor_routes')),'attendance tables prepared for Realtime replication');
set local role anon;
select pg_temp.assert_true(pg_temp.denied($q$select public.get_route_attendance('00000000-0000-4000-8000-000000000001')$q$),'anonymous cannot read attendance');
select pg_temp.assert_true(pg_temp.denied($q$select public.search_vendor_catalog()$q$),'anonymous cannot search catalog');
reset role;
set local role authenticated;
select pg_temp.assert_true(pg_temp.denied($q$select public.search_vendor_catalog()$q$),'missing identity cannot search');
select set_config('request.jwt.claim.sub','20000000-0000-4000-8000-000000000001',true);
insert into public.vendor_routes(vendor_id,name,waypoints,active_days) values
 ('30000000-0000-4000-8000-000000000001','Percurso habitual','[{"latitude":0,"longitude":0,"orderIndex":0},{"latitude":0,"longitude":0.01,"orderIndex":1}]',array['monday','saturday']);
select set_config('test.route',(select id::text from public.vendor_routes),true);
update public.products set name='Pão francês' where id='40000000-0000-4000-8000-000000000001';
select pg_temp.assert_true((select count(*)=1 from public.search_vendor_catalog('pao')),'search normalizes accents in product names');
select pg_temp.assert_true((select not (to_jsonb(result) ? 'user_id') from public.search_vendor_catalog('pao') result),'discovery RPC does not expose Auth identity');
select pg_temp.assert_true((select count(*)=1 from public.search_vendor_catalog('', 'Pães')),'new category matches legacy product names');
select pg_temp.assert_true((select count(*)=0 from public.search_vendor_catalog('%')),'query wildcard is literal');
select pg_temp.assert_true((select count(*)=0 from public.search_vendor_catalog('pao','',1,1)),'search paginates filtered results');
select pg_temp.assert_true((select price=12.50 from public.products where id='40000000-0000-4000-8000-000000000001'),'rename preserves legacy price');

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select pg_temp.assert_true(pg_temp.denied($q$select public.create_purchase_intent('30000000-0000-4000-8000-000000000002',0,0.005)$q$),'online vendor without active route rejects interest');
select set_config('test.intent',public.create_purchase_intent('30000000-0000-4000-8000-000000000001',0.02,0.005)::text,true);
select pg_temp.assert_true(public.create_purchase_intent('30000000-0000-4000-8000-000000000001',0.02,0.005)::text=current_setting('test.intent'),'repeated requests return same pending ID');
select pg_temp.assert_true((select route_id=current_setting('test.route')::uuid from public.purchase_intents where id=current_setting('test.intent')::uuid),'interest bound to current journey');
select pg_temp.assert_true(pg_temp.denied(format('select public.get_route_attendance(%L)',current_setting('test.route'))),'consumer cannot read private queue');
select pg_temp.assert_true(pg_temp.denied(format('select public.set_route_availability(%L,false)',current_setting('test.route'))),'consumer cannot pause vendor');
select pg_temp.assert_true(pg_temp.denied($q$update public.purchase_intents set status='fulfilled'$q$),'consumer cannot claim a sale');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
select pg_temp.assert_true(pg_temp.denied(format('select public.cancel_purchase_intent(%L)',current_setting('test.intent'))),'consumer B cannot cancel consumer A');
select set_config('request.jwt.claim.sub','20000000-0000-4000-8000-000000000002',true);
select pg_temp.assert_true(pg_temp.denied(format('select public.get_route_attendance(%L)',current_setting('test.route'))),'vendor B cannot read queue A');
select pg_temp.assert_true(pg_temp.denied(format('select public.set_route_availability(%L,false)',current_setting('test.route'))),'vendor B cannot pause A');
select set_config('request.jwt.claim.sub','20000000-0000-4000-8000-000000000001',true);
select pg_temp.assert_true(jsonb_array_length(public.get_route_attendance(current_setting('test.route')::uuid)->'intents')=1,'snapshot includes outside corridor point for explicit classification');
select pg_temp.assert_true(pg_temp.denied(format('select public.cancel_purchase_intent(%L)',current_setting('test.intent'))),'vendor cannot cancel another consumer');

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select pg_temp.assert_true(public.cancel_purchase_intent(current_setting('test.intent')::uuid)::text=current_setting('test.intent'),'owner cancellation confirmed');
select pg_temp.assert_true(public.cancel_purchase_intent(current_setting('test.intent')::uuid)::text=current_setting('test.intent'),'cancellation retry is idempotent');
select set_config('test.new_intent',public.create_purchase_intent('30000000-0000-4000-8000-000000000001',0,0.008)::text,true);
select pg_temp.assert_true(current_setting('test.new_intent')<>current_setting('test.intent'),'new interest after cancellation gets new protocol');
select set_config('request.jwt.claim.sub','20000000-0000-4000-8000-000000000001',true);
select pg_temp.assert_true((select count(*)=1 from jsonb_array_elements(public.get_route_attendance(current_setting('test.route')::uuid)->'intents') i where i->>'status'='cancelled'),'snapshot includes cancellation tombstone');
select pg_temp.assert_true((select count(*)=1 from jsonb_array_elements(public.get_route_attendance(current_setting('test.route')::uuid)->'intents') i where i->>'status'='pending'),'new pending protocol remains separate');
select public.set_route_availability(current_setting('test.route')::uuid,false);
select pg_temp.assert_true(not (public.get_route_attendance(current_setting('test.route')::uuid)->>'online')::boolean,'pause persists offline');
select pg_temp.assert_true((public.get_route_attendance(current_setting('test.route')::uuid)->>'active')::boolean,'paused route remains resumable');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
select pg_temp.assert_true(pg_temp.denied($q$select public.create_purchase_intent('30000000-0000-4000-8000-000000000001',0,0.008)$q$),'paused vendor rejects new interests');
select pg_temp.assert_true((select count(*)=0 from public.search_vendor_catalog('pao')),'paused vendor hidden from discovery');
select set_config('request.jwt.claim.sub','20000000-0000-4000-8000-000000000001',true);
select public.set_route_availability(current_setting('test.route')::uuid,true);
select pg_temp.assert_true((public.get_route_attendance(current_setting('test.route')::uuid)->>'online')::boolean,'resume persists online');
select public.set_route_availability(current_setting('test.route')::uuid,false,true);
select pg_temp.assert_true(not (public.get_route_attendance(current_setting('test.route')::uuid)->>'active')::boolean,'end persists ended route');
select pg_temp.assert_true(not (public.get_route_attendance(current_setting('test.route')::uuid)->>'online')::boolean,'end also persists offline');
select pg_temp.assert_true(jsonb_array_length(public.get_route_attendance(current_setting('test.route')::uuid)->'intents')=0,'ended queue exposes no active coordinates');
select pg_temp.assert_true(pg_temp.denied(format('select public.set_route_availability(%L,true)',current_setting('test.route'))),'ended journey cannot be resumed');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select pg_temp.assert_true((select status='expired' from public.purchase_intents where id=current_setting('test.new_intent')::uuid),'ending journey expires pending interest without claiming sale');
select pg_temp.assert_true(pg_temp.denied(format('select private.change_route_availability(%L,true,false)',current_setting('test.route'))),'private writer also checks identity');
rollback;
select 'Automatic attendance and discovery assertions passed' as result;
