-- READ-ONLY QA gate for site-created vendor catalogue.
-- Run in Supabase SQL Editor BEFORE any new migration; no data rows or secrets are read.
with checks as (
  select 'base' as phase,'confirmed_actor available' as item,
    to_regprocedure('private.confirmed_actor()') is not null as ok
  union all select 'base','complete_my_profile available',
    to_regprocedure('private.complete_my_profile(text,text,text,text,text)') is not null
  union all select 'base','profiles.signup_role present',
    exists(select 1 from information_schema.columns
      where table_schema='public' and table_name='profiles' and column_name='signup_role')
  union all select 'base','vendor uniqueness',
    exists(select 1 from pg_index i join pg_class c on c.oid=i.indrelid
      join pg_namespace n on n.oid=c.relnamespace
      where n.nspname='public' and c.relname='vendors' and i.indisunique
      and pg_get_indexdef(i.indexrelid) like '%(user_id)%')
  union all select 'base','products RLS on',
    coalesce((select relrowsecurity from pg_class where oid=to_regclass('public.products')),false)
  union all select 'base','products has required columns',
    (select count(*)=4 from information_schema.columns where table_schema='public'
      and table_name='products' and ((column_name='vendor_id' and data_type='uuid')
        or (column_name='name' and data_type='text')
        or (column_name='price' and data_type='numeric')
        or (column_name='is_active' and data_type='boolean')))
  union all select 'migration','new onboarding RPC not already installed',
    to_regprocedure('public.complete_vendor_onboarding(text,text,text,text[],text)') is null
    and to_regprocedure('private.complete_vendor_onboarding(text,text,text,text[],text)') is null
)
select phase,item,ok from checks order by phase,item;
