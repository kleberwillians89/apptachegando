\set ON_ERROR_STOP on
-- Disposable database contract; every synthetic user, vendor, and item rolls back.
begin;
create function pg_temp.tc_assert(p_ok boolean,p_message text) returns void
language plpgsql as $$
begin if p_ok is distinct from true then
  raise exception 'BATCH CATALOGUE FAIL: %',p_message;
end if; end $$;
create function pg_temp.tc_denied(p_stmt text) returns boolean language plpgsql as $$
begin execute p_stmt; return false;
exception when insufficient_privilege then return true; end $$;

grant execute on function pg_temp.tc_assert(boolean,text), pg_temp.tc_denied(text) to authenticated,anon;

select pg_temp.tc_assert(
  not has_function_privilege('anon','public.append_my_vendor_products(text[])','execute'),
  'anonymous must not append products'
);
select pg_temp.tc_assert(
  not (select prosecdef from pg_proc where oid='public.append_my_vendor_products(text[])'::regprocedure),
  'public API must be invoker'
);

insert into auth.users(id,email,email_confirmed_at,raw_user_meta_data)
values
 ('e1000000-0000-4000-8000-000000000001','catalog-batch-owner@example.test',now(),'{"role":"vendor","name":"Batch Owner"}'),
 ('e1000000-0000-4000-8000-000000000002','catalog-batch-consumer@example.test',now(),'{"role":"consumer","name":"Batch Consumer"}');
insert into auth.sessions(id,user_id)
select id,id from auth.users where id::text like 'e1000000-%';

set local role authenticated;
select set_config('request.jwt.claim.sub','e1000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims','{"session_id":"e1000000-0000-4000-8000-000000000002"}',true);
select pg_temp.tc_assert(pg_temp.tc_denied(
  'select public.append_my_vendor_products(array[''Não autorizado''])'
), 'consumer cannot append vendor products');

select set_config('request.jwt.claim.sub','e1000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims','{"session_id":"e1000000-0000-4000-8000-000000000001"}',true);
select set_config('test.batch.owner', public.complete_vendor_onboarding(
 'Batch Owner','Pães do Bairro','Pães',array['Pão francês','Sonho'])#>>'{profile,vendor_id}',true);

select pg_temp.tc_assert(
 (public.append_my_vendor_products(array['Rosquinha','Bolo']) ->> 'inserted')::int=2,
 'first batch adds two products');
select pg_temp.tc_assert(
 (public.append_my_vendor_products(array['Bolo','Rosquinha']) ->> 'inserted')::int=0,
 'replay is idempotent');
select pg_temp.tc_assert(
 (public.append_my_vendor_products(array['Brioche']) ->> 'product_count')::int=5,
 'subsequent batch preserves original catalogue and appends');
select pg_temp.tc_assert(
 (select count(*)=5 from public.products where vendor_id=current_setting('test.batch.owner')::uuid),
 'all five products persist once');
reset role;
rollback;
