\set ON_ERROR_STOP on
begin;
create function pg_temp.check_onboarding(ok boolean,label text) returns void language plpgsql as $$begin if ok is distinct from true then raise exception 'ONBOARDING FAILED: %',label; end if; raise notice 'ONBOARDING PASS: %',label;end$$;
create function pg_temp.rejected(statement text) returns boolean language plpgsql as $$begin execute statement;return false;exception when insufficient_privilege or invalid_parameter_value then return true;end$$;
grant execute on function pg_temp.check_onboarding(boolean,text),pg_temp.rejected(text) to authenticated,anon;
insert into auth.users(id,email,email_confirmed_at,raw_user_meta_data) values
 ('e0000000-0000-4000-8000-000000000001','onboarding@example.test',now(),'{"name":"Synthetic seller","role":"vendor"}'),
 ('e0000000-0000-4000-8000-000000000002','unconfirmed-onboarding@example.test',null,'{"name":"Unconfirmed","role":"vendor"}');
insert into auth.sessions(id,user_id) values ('e0000000-0000-4000-8000-000000000001','e0000000-0000-4000-8000-000000000001'),('e0000000-0000-4000-8000-000000000002','e0000000-0000-4000-8000-000000000002');
set local role anon;
select pg_temp.check_onboarding(pg_temp.rejected($q$select public.complete_vendor_onboarding('No','No','Pães',array['Pão'])$q$),'anonymous denied');
reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub','e0000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims','{"session_id":"e0000000-0000-4000-8000-000000000002"}',true);
select pg_temp.check_onboarding(pg_temp.rejected($q$select public.complete_vendor_onboarding('No','No','Pães',array['Pão'])$q$),'unconfirmed denied');
select set_config('request.jwt.claim.sub','e0000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims','{"session_id":"e0000000-0000-4000-8000-000000000001"}',true);
select pg_temp.check_onboarding(pg_temp.rejected($q$select public.complete_vendor_onboarding('Synthetic seller','Shop','Pães',array['Pão',' pão '])$q$),'duplicate names rejected before provisioning');
select pg_temp.check_onboarding(not exists(select 1 from public.vendors where user_id=auth.uid()),'invalid request leaves no partial vendor');
select pg_temp.check_onboarding(pg_temp.rejected($q$select public.complete_vendor_onboarding('Synthetic seller','Shop','Pães',array[]::text[])$q$),'empty catalogue rejected');
select pg_temp.check_onboarding(pg_temp.rejected($q$select public.complete_vendor_onboarding('Synthetic seller','Shop','invalid',array['Pão'])$q$),'profile validation failure rolls back catalogue');
select set_config('test.onboard_vendor',public.complete_vendor_onboarding('Synthetic seller','Shop','Pães',array['Pão','Bolo','Salgado'])->'profile'->>'vendor_id',true);
select pg_temp.check_onboarding((select count(*)=3 from public.products where vendor_id=current_setting('test.onboard_vendor')::uuid),'three products persisted for own vendor');
select pg_temp.check_onboarding((select bool_and(price=0 and is_active) from public.products where vendor_id=current_setting('test.onboard_vendor')::uuid),'no price offer introduced');
select pg_temp.check_onboarding((public.complete_vendor_onboarding('Synthetic seller','Shop','Pães',array['Pão','Bolo','Salgado'])->>'product_count')::int=3,'retry does not duplicate catalogue');
select pg_temp.check_onboarding(not (public.complete_vendor_onboarding('Changed','Changed','Frutas',array['Replacement'])->>'catalogue_initialized')::boolean,'existing catalogue not replaced');
select pg_temp.check_onboarding((select count(*)=0 from public.products where vendor_id=current_setting('test.onboard_vendor')::uuid and name='Replacement'),'existing products preserved');
select pg_temp.check_onboarding((select name='Shop' from public.vendors where id=current_setting('test.onboard_vendor')::uuid),'existing vendor preserved');
reset role;
delete from auth.sessions where id='e0000000-0000-4000-8000-000000000001';
set local role authenticated;
select pg_temp.check_onboarding(pg_temp.rejected($q$select public.complete_vendor_onboarding('Synthetic seller','Shop','Pães',array['Pão'])$q$),'revoked session denied');
reset role;
rollback;
