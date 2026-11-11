-- Inventory only: no fixture writes, DDL, migration changes or Auth account creation.
begin transaction read only;
select jsonb_build_object(
  'tables', (select jsonb_agg(jsonb_build_object('name',c.relname,'rls',c.relrowsecurity,'force_rls',c.relforcerowsecurity)) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname in ('profiles','vendors','products','vendor_locations','vendor_routes','purchase_intents') and c.relkind='r'),
  'policies', (select jsonb_agg(to_jsonb(p)) from pg_policies p where schemaname='public' and tablename in ('profiles','vendors','products','vendor_locations','vendor_routes','purchase_intents')),
  'grants', (select jsonb_agg(to_jsonb(g)) from information_schema.role_table_grants g where table_schema='public' and table_name in ('profiles','vendors','products','vendor_locations','vendor_routes','purchase_intents','vendor_catalog','route_details') and grantee in ('anon','authenticated')),
  'column_grants', (select jsonb_agg(to_jsonb(g)) from information_schema.column_privileges g where table_schema='public' and table_name in ('profiles','vendors','products','vendor_locations','vendor_routes','purchase_intents') and grantee in ('anon','authenticated')),
  'views', (select jsonb_agg(jsonb_build_object('name',c.relname,'options',c.reloptions)) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname in ('vendor_catalog','route_details') and c.relkind='v'),
  'rpc', (select jsonb_agg(jsonb_build_object('name',p.proname,'security_definer',p.prosecdef,'config',p.proconfig,'anon_execute',has_function_privilege('anon',p.oid,'EXECUTE'),'authenticated_execute',has_function_privilege('authenticated',p.oid,'EXECUTE'),'definition',pg_get_functiondef(p.oid))) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in ('create_route_buffer','get_intents_for_route','update_vendor_location','create_purchase_intent')),
  'realtime', (select jsonb_agg(jsonb_build_object('schema',schemaname,'table',tablename)) from pg_publication_tables where pubname='supabase_realtime'),
  'auth_user_count', (select count(*) from auth.users)
) as inventory;
commit;
