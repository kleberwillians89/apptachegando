\set ON_ERROR_STOP on
-- Entire suite rolls back. Requires canonical migrations, RC1, web and catalogue onboarding migration.
begin;
create function pg_temp.assert_true(p_ok boolean,p_label text) returns void
language plpgsql as $$
begin
  if p_ok is distinct from true then
    raise exception 'CATALOGUE ASSERTION FAILED: %',p_label;
  end if;
  raise notice 'CATALOGUE PASS: %',p_label;
end $$;
create function pg_temp.denied(p_stmt text) returns boolean language plpgsql as $$
begin
  execute p_stmt;
  return false;
exception when insufficient_privilege then
  return true;
end $$;
create function pg_temp.invalid(p_stmt text) returns boolean language plpgsql as $$
begin
  execute p_stmt;
  return false;
exception when invalid_parameter_value then
  return true;
end $$;
grant execute on function pg_temp.assert_true(boolean,text),pg_temp.denied(text),pg_temp.invalid(text) to authenticated,anon;

-- Uses synthetic identities and genuine SQL Auth/session rows, never actual customers.
insert into auth.users(id,email,email_confirmed_at,raw_user_meta_data) values
 ('e0000000-0000-4000-8000-000000000001','catalog-vendor@example.test',now(),'{"role":"vendor","name":"Vendor Test"}'),
 ('e0000000-0000-4000-8000-000000000002','catalog-other@example.test',now(),'{"role":"vendor","name":"Other Vendor"}'),
 ('e0000000-0000-4000-8000-000000000003','catalog-unconfirmed@example.test',null,'{"role":"vendor","name":"Unconfirmed"}');
insert into auth.sessions(id,user_id) select id,id from auth.users where id::text like 'e0000000-%';

select pg_temp.assert_true(
  not has_function_privilege('anon','public.complete_vendor_onboarding(text,text,text,text[],text)','execute'),
  'anon cannot execute onboarding'
);
select pg_temp.assert_true(
  not (select prosecdef from pg_proc where oid='public.complete_vendor_onboarding(text,text,text,text[],text)'::regprocedure),
  'public wrapper is SECURITY INVOKER'
);
select pg_temp.assert_true(
  (select relrowsecurity from pg_class where oid='public.products'::regclass),
  'product RLS remains enabled'
);
set local role anon;
select pg_temp.assert_true(pg_temp.denied(
  'select public.complete_vendor_onboarding(''A'',''B'',''Pães'',array[''Bread''])'
), 'anonymous call denied');
reset role;

set local role authenticated;
select set_config('request.jwt.claim.sub','e0000000-0000-4000-8000-000000000003',true);
select set_config('request.jwt.claims','{"session_id":"e0000000-0000-4000-8000-000000000003"}',true);
select pg_temp.assert_true(pg_temp.denied(
  'select public.complete_vendor_onboarding(''A'',''B'',''Pães'',array[''Bread''])'
), 'unconfirmed account denied');

select set_config('request.jwt.claim.sub','e0000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims','{"session_id":"e0000000-0000-4000-8000-000000000001"}',true);
select pg_temp.assert_true(pg_temp.invalid(
  'select public.complete_vendor_onboarding(''A'',''Shop'',''Pães'',array[]::text[])'
), 'empty catalogue rejected');
select pg_temp.assert_true(pg_temp.invalid(
  'select public.complete_vendor_onboarding(''A'',''Shop'',''Pães'',array[''Bread'','' bread ''])'
), 'duplicate case-insensitive names rejected');
select pg_temp.assert_true(pg_temp.invalid(
  'select public.complete_vendor_onboarding(''A'',''Shop'',''Invalid'',array[''Bread''])'
), 'invalid category rejected');
select pg_temp.assert_true(
  (select count(*)=0 from public.vendors where user_id='e0000000-0000-4000-8000-000000000001'),
  'failed onboarding did not persist professional profile'
);
select set_config('catalog.owner.first',
  public.complete_vendor_onboarding('Vendor Test','Bakery Test','Pães',array['Pão francês','Croissant','Sonho'])#>>'{profile,vendor_id}',true);
select pg_temp.assert_true(
  (select count(*)=3 from public.products where vendor_id=current_setting('catalog.owner.first')::uuid),
  'three catalogue products saved'
);
select pg_temp.assert_true(
  (select count(*)=3 from public.products where vendor_id=current_setting('catalog.owner.first')::uuid and price=0 and is_active),
  'legacy price does not imply checkout and all products are active'
);
select pg_temp.assert_true(
  (public.complete_vendor_onboarding('Renamed Owner','Changed Shop','Frutas',array['Other Item'])#>>'{profile,vendor_id}')=current_setting('catalog.owner.first'),
  'repetition preserves professional identity'
);
select pg_temp.assert_true(
  (select count(*)=3 from public.products where vendor_id=current_setting('catalog.owner.first')::uuid),
  'repetition never duplicates or overwrites existing catalogue'
);
select pg_temp.assert_true(
  (select name='Bakery Test' from public.vendors where id=current_setting('catalog.owner.first')::uuid),
  'repetition preserves existing shop information'
);

select set_config('request.jwt.claim.sub','e0000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims','{"session_id":"e0000000-0000-4000-8000-000000000002"}',true);
select pg_temp.assert_true(
  (public.complete_vendor_onboarding('Other Vendor','Fruit Shop','Frutas',array['Maçã'])#>>'{profile,vendor_id}')
    <>current_setting('catalog.owner.first'),
  'different user receives different vendor identity'
);
select pg_temp.assert_true(
  (select count(*)=1 from public.products where vendor_id=(select id from public.vendors where user_id='e0000000-0000-4000-8000-000000000002')),
  'another account cannot overwrite original catalog'
);
reset role;
rollback;
