\set ON_ERROR_STOP on
begin;
create function pg_temp.ok(value boolean,label text) returns void language plpgsql as $$begin if value is distinct from true then raise exception 'WEB ASSERTION FAILED: %',label; end if; raise notice 'WEB PASS: %',label; end$$;
create function pg_temp.denied(statement text) returns boolean language plpgsql as $$begin execute statement;return false;exception when insufficient_privilege then return true;end$$;
create function pg_temp.invalid(statement text) returns boolean language plpgsql as $$begin execute statement;return false;exception when invalid_parameter_value then return true;end$$;
grant execute on function pg_temp.ok(boolean,text),pg_temp.denied(text),pg_temp.invalid(text) to anon,authenticated;
insert into auth.users(id,email,email_confirmed_at,raw_user_meta_data) values
 ('b0000000-0000-4000-8000-000000000001','web-consumer@example.test',now(),'{"registration_contract":"web-v1","role":"consumer","name":"Web Consumer"}'),
 ('b0000000-0000-4000-8000-000000000002','web-vendor@example.test',now(),'{"registration_contract":"web-v1","role":"vendor","name":"Web Vendor","phone":"+5511000000000","business_name":"Untrusted hint","category":"Pães"}'),
 ('b0000000-0000-4000-8000-000000000003','unconfirmed@example.test',null,'{"registration_contract":"web-v1","role":"vendor","name":"Unconfirmed"}'),
 ('b0000000-0000-4000-8000-000000000004','legacy-metadata@example.test',null,'{"role":"vendor","name":"Metadata only"}');
insert into auth.sessions(id,user_id) select id,id from auth.users where id::text like 'b0000000-%';
select pg_temp.ok(not exists(select 1 from public.vendors where user_id='b0000000-0000-4000-8000-000000000002'),'signup metadata never creates professional link');
select pg_temp.ok(not exists(select 1 from public.vendors where user_id='b0000000-0000-4000-8000-000000000004'),'metadata without web marker cannot bypass authenticated completion');
select pg_temp.ok((select bool_and(not prosecdef and not has_function_privilege('anon',p.oid,'execute')) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in ('get_my_account_profile','complete_my_profile','request_account_deletion','discover_vendors','get_my_interest_state')),'all new public wrappers invoker and denied to anonymous');
select pg_temp.ok(not has_function_privilege('authenticated','private.route_interest_condition(uuid,extensions.geography)','execute'),'private route projection is not a client geometry oracle');
select pg_temp.ok(not has_function_privilege('anon','public.get_my_account_profile()','execute'),'anonymous cannot query role');
select pg_temp.ok(not has_table_privilege('authenticated','private.account_deletion_requests','select'),'deletion requests remain private');
select pg_temp.ok((select relrowsecurity from pg_class where oid='private.account_deletion_requests'::regclass),'deletion requests have RLS');
set local role anon;
select pg_temp.ok(pg_temp.denied('select public.complete_my_profile(''vendor'',''Attack'',''Attack'',''Pães'')'),'anonymous cannot provision');
reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub','b0000000-0000-4000-8000-000000000003',true);
select set_config('request.jwt.claims','{"session_id":"b0000000-0000-4000-8000-000000000003"}',true);
select pg_temp.ok(pg_temp.denied('select public.get_my_account_profile()'),'unconfirmed account denied');
select pg_temp.ok(pg_temp.denied('select public.complete_my_profile(''vendor'',''No'',''No'',''Pães'')'),'unconfirmed cannot provision');
select set_config('request.jwt.claim.sub','b0000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims','{"session_id":"b0000000-0000-4000-8000-000000000001"}',true);
select pg_temp.ok(public.get_my_account_profile()->>'role'='consumer','confirmed web consumer resolves from persisted profile');
select pg_temp.ok(public.complete_my_profile('consumer','Web Consumer')->>'role'='consumer','consumer completion');
select pg_temp.ok(public.complete_my_profile('consumer','Web Consumer')->>'role'='consumer','consumer completion repeated');
select pg_temp.ok(public.request_account_deletion()=public.request_account_deletion(),'deletion request idempotent without deleting identity');
select pg_temp.ok(pg_temp.denied('update public.profiles set signup_role=''vendor'''),'client cannot edit enrollment field directly');
select set_config('request.jwt.claim.sub','b0000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims','{"session_id":"b0000000-0000-4000-8000-000000000002"}',true);
select pg_temp.ok(public.get_my_account_profile()->>'role'='vendor_incomplete','incomplete vendor is never treated as consumer');
select pg_temp.ok(pg_temp.invalid('select public.complete_my_profile(''consumer'',''Web Vendor'')'),'incomplete professional cannot silently downgrade');
select pg_temp.ok(pg_temp.invalid('select public.complete_my_profile(''vendor'',''Web Vendor'',''Shop'',''invalid'')'),'category validated');
select set_config('web.vendor',public.complete_my_profile('vendor','Web Vendor','Web Bakery','Pães')->>'vendor_id',true);
select pg_temp.ok(public.get_my_account_profile()->>'role'='vendor','confirmed and provisioned web vendor resolves');
select pg_temp.ok((select phone='+5511000000000' from public.profiles where id='b0000000-0000-4000-8000-000000000002'),'completion without phone preserves signup phone');
select pg_temp.ok(public.complete_my_profile('vendor','Changed','Changed','Frutas')->>'vendor_id'=current_setting('web.vendor'),'repeat preserves vendor identity');
select pg_temp.ok((select name='Web Bakery' from public.vendors where id=current_setting('web.vendor')::uuid),'repeat does not overwrite existing vendor');
select pg_temp.ok((select count(*)=1 from public.vendors where user_id='b0000000-0000-4000-8000-000000000002'),'one vendor per authenticated owner');
select set_config('request.jwt.claims','{"is_anonymous":true}',true);
select pg_temp.ok(pg_temp.denied('select public.get_my_account_profile()'),'signed-in anonymous identity rejected');
select set_config('request.jwt.claims','{"session_id":"b0000000-0000-4000-8000-000000000002"}',true);
reset role;
update auth.users set raw_user_meta_data='{"role":"consumer"}' where id='b0000000-0000-4000-8000-000000000002';
set local role authenticated;
select pg_temp.ok(public.get_my_account_profile()->>'role'='vendor','editable metadata never authorizes or demotes persisted vendor');
reset role;
delete from auth.sessions where id='b0000000-0000-4000-8000-000000000002';
set local role authenticated;
select pg_temp.ok(pg_temp.denied('select public.get_my_account_profile()'),'logout invalidates profile access even with old signed identity');
reset role;
-- More than one catalog page of ineligible vendors precedes eligible IDs.
insert into auth.users(id,email,raw_user_meta_data)
select ('c0000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'geo-'||n||'@example.test','{"name":"Ineligible","role":"vendor"}'::jsonb from generate_series(1,60) n;
insert into public.vendors(id,user_id,name,is_online) select ('00000000-0000-4000-8000-'||right(id::text,12))::uuid,id,'Ineligible',true from auth.users where id::text like 'c0000000-%';
update auth.users set email_confirmed_at=now() where id in ('10000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000002');
insert into public.vendor_routes(id,vendor_id,name,waypoints) values
 ('d0000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001','Geo A','[{"latitude":0,"longitude":0,"orderIndex":0},{"latitude":0,"longitude":0.02,"orderIndex":1}]'),
 ('d0000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000002','Geo B','[{"latitude":0,"longitude":0,"orderIndex":0},{"latitude":0,"longitude":0.02,"orderIndex":1}]');
insert into public.vendor_locations(vendor_id,location,accuracy_m,updated_at,expires_at) values
 ('30000000-0000-4000-8000-000000000001',extensions.st_setsrid(extensions.st_makepoint(0.002,0),4326)::extensions.geography,5,now(),now()+interval '2 minutes'),
 ('30000000-0000-4000-8000-000000000002',extensions.st_setsrid(extensions.st_makepoint(0.003,0),4326)::extensions.geography,5,now(),now()+interval '2 minutes');
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims','{"session_id":"10000000-0000-4000-8000-000000000001"}',true);
select pg_temp.ok((select count(*)=2 from public.discover_vendors(0,0.008)),'geography filters before pagination across 60 ineligible vendors');
select pg_temp.ok((select id='30000000-0000-4000-8000-000000000002' from public.discover_vendors(0,0.008,'','',0,1)),'closest eligible vendor first');
select pg_temp.ok((select id='30000000-0000-4000-8000-000000000001' from public.discover_vendors(0,0.008,'','',1,1)),'offset applies after geographic eligibility');
select pg_temp.ok((select count(*)=1 from public.discover_vendors(0,0.008,'Produto A')),'product search combined with geography');
select pg_temp.ok((select count(*)=0 from public.discover_vendors(0.02,0.008)),'outside corridor excluded');
select pg_temp.ok((select count(*)=0 from public.discover_vendors(0,0.001)),'behind vendor excluded');
select pg_temp.ok((select count(*)=0 from public.discover_vendors(0,0.008,'','',0,50,100)),'radius applied before pagination');
select pg_temp.ok(pg_temp.invalid('select public.discover_vendors(91,0)'),'invalid consumer GPS rejected');
select set_config('web.intent',public.create_purchase_intent('30000000-0000-4000-8000-000000000001',0,0.008)::text,true);
select pg_temp.ok(public.get_my_interest_state(current_setting('web.intent')::uuid)->>'eligibility'='eligible','persisted eligible state');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims','{"session_id":"10000000-0000-4000-8000-000000000002"}',true);
select pg_temp.ok(pg_temp.denied(format('select public.get_my_interest_state(%L)',current_setting('web.intent'))),'another consumer cannot read projected state');
reset role;
update public.vendor_locations set updated_at=now()-interval '35 seconds' where vendor_id='30000000-0000-4000-8000-000000000001';
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims','{"session_id":"10000000-0000-4000-8000-000000000001"}',true);
select pg_temp.ok(public.get_my_interest_state(current_setting('web.intent')::uuid)->>'eligibility'='vendor_location_missing','stale GPS distinct from registered protocol');
select pg_temp.ok((select count(*)=1 from public.discover_vendors(0,0.008)),'stale GPS hidden before pagination');
select public.cancel_purchase_intent(current_setting('web.intent')::uuid);
select pg_temp.ok(public.get_my_interest_state(current_setting('web.intent')::uuid)->>'eligibility'='cancelled','cancelled projection');
select set_config('web.intent',public.create_purchase_intent('30000000-0000-4000-8000-000000000002',0.02,0.008)::text,true);
select pg_temp.ok(public.get_my_interest_state(current_setting('web.intent')::uuid)->>'eligibility'='outside_route','registered outside route distinguished');
select public.cancel_purchase_intent(current_setting('web.intent')::uuid);
select set_config('web.intent',public.create_purchase_intent('30000000-0000-4000-8000-000000000002',0,0.001)::text,true);
select pg_temp.ok(public.get_my_interest_state(current_setting('web.intent')::uuid)->>'eligibility'='behind_route','registered behind route distinguished');
reset role;
update public.vendors set is_online=false where id='30000000-0000-4000-8000-000000000002';
set local role authenticated;
select pg_temp.ok(public.get_my_interest_state(current_setting('web.intent')::uuid)->>'eligibility'='awaiting_availability','pause projects waiting state without mutation');
reset role;
update public.purchase_intents set created_at=now()-interval '2 hours',expires_at=now()-interval '1 second' where id=current_setting('web.intent')::uuid;
set local role authenticated;
select pg_temp.ok(public.get_my_interest_state(current_setting('web.intent')::uuid)->>'eligibility'='expired','expired projection');
reset role;
update public.vendor_locations set updated_at=now(),accuracy_m=100 where vendor_id='30000000-0000-4000-8000-000000000001';
set local role authenticated;
select pg_temp.ok((select count(*)=0 from public.discover_vendors(0,0.008)),'low precision and offline vendors are both excluded');
reset role;
update public.vendor_locations set accuracy_m=5 where vendor_id='30000000-0000-4000-8000-000000000001';
insert into private.interest_push_progress(route_id,vendor_id,latitude,longitude,accuracy_m,recorded_at,along_m,valid)
values('d0000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001',0,0.002,5,now(),222,false);
set local role authenticated;
select pg_temp.ok((select count(*)=0 from public.discover_vendors(0,0.008)),'invalid strict GPS cannot borrow fresh legacy coordinates');
reset role;
select pg_temp.ok((select count(*)=1 from pg_trigger t join pg_proc p on p.oid=t.tgfoid where t.tgrelid='auth.users'::regclass and p.proname='handle_new_user' and not t.tgisinternal),'existing provisioning trigger remains bound');
rollback;
