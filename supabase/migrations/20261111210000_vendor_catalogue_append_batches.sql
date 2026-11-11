begin;
-- Add vendor catalogue items in bounded, idempotent batches after initial onboarding.
-- Each call is limited to 100 names, but there is no fixed total catalogue limit.
create function private.append_my_vendor_products(p_product_names text[])
returns jsonb language plpgsql security definer set search_path=''
as $$
declare actor uuid; vendor uuid; inserted_count integer; total_count integer;
begin
  actor := private.confirmed_actor();
  if p_product_names is null
    or coalesce(pg_catalog.array_length(p_product_names,1),0) not between 1 and 100
    or exists (
       select 1 from pg_catalog.unnest(p_product_names) as item(name)
       where name is null or pg_catalog.length(pg_catalog.btrim(name)) not between 1 and 160
    )
    or exists (
       select 1 from pg_catalog.unnest(p_product_names) as item(name)
       group by pg_catalog.lower(pg_catalog.btrim(name))
       having count(*)>1
    ) then
    raise exception 'Invalid catalogue batch' using errcode='22023';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('profile:'||actor::text,0));
  select v.id into vendor from public.vendors v where v.user_id=actor;
  if vendor is null then raise exception 'Professional profile required' using errcode='42501'; end if;

  with inserted as (
    insert into public.products(vendor_id,name,price,is_active)
    select vendor,pg_catalog.btrim(item.name),0,true
    from pg_catalog.unnest(p_product_names) with ordinality item(name,position)
    where not exists (
      select 1 from public.products existing
      where existing.vendor_id=vendor
      and pg_catalog.lower(pg_catalog.btrim(existing.name))=pg_catalog.lower(pg_catalog.btrim(item.name))
    )
    order by item.position
    returning id
  ) select pg_catalog.count(*) into inserted_count from inserted;
  select pg_catalog.count(*) into total_count from public.products p where p.vendor_id=vendor;
  return pg_catalog.jsonb_build_object('inserted',inserted_count,'product_count',total_count);
end $$;

create function public.append_my_vendor_products(p_product_names text[])
returns jsonb language sql security invoker set search_path=''
as $$ select private.append_my_vendor_products(p_product_names) $$;

revoke all on function private.append_my_vendor_products(text[]) from public,anon,authenticated,service_role;
revoke all on function public.append_my_vendor_products(text[]) from public,anon,authenticated,service_role;
grant execute on function private.append_my_vendor_products(text[]) to authenticated;
grant execute on function public.append_my_vendor_products(text[]) to authenticated;
notify pgrst,'reload schema';
commit;
