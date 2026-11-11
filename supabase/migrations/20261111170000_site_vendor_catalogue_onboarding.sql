begin;
-- QA REVIEW ONLY: deliberately NOT deployed. Install after 20261111135508_web_app_identity_and_discovery.sql.
-- Vendor onboarding from the public site: profile and initial catalogue in one transaction.
-- Products are ready-made categories, not sale offers; no checkout or price UI.

create function private.complete_vendor_onboarding(
  p_name text,
  p_business_name text,
  p_category text,
  p_product_names text[],
  p_phone text default null
) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  actor uuid;
  profile_result jsonb;
  vendor uuid;
  existing_count integer;
  product_count integer;
begin
  actor := private.confirmed_actor();
  if p_product_names is null
     or coalesce(pg_catalog.array_length(p_product_names,1),0) not between 1 and 30
     or exists (
       select 1 from pg_catalog.unnest(p_product_names) as item(name)
       where name is null or pg_catalog.length(pg_catalog.btrim(name)) not between 1 and 160
     )
     or exists (
       select 1 from pg_catalog.unnest(p_product_names) as item(name)
       group by pg_catalog.lower(pg_catalog.btrim(name))
       having count(*) > 1
     )
  then
    raise exception 'Invalid initial catalogue' using errcode='22023';
  end if;

  -- Serializes competing onboarding attempts for this authenticated identity.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('profile:' || actor::text,0));

  -- An existing consumer account cannot silently turn itself into a vendor.
  -- Legacy professional accounts are accepted through their persisted vendor link.
  if not exists (
    select 1 from public.profiles p
    where p.id=actor and p.signup_role='vendor'
  ) and not exists (
    select 1 from public.vendors v where v.user_id=actor
  ) then
    raise exception 'Vendor enrollment required' using errcode='42501';
  end if;

  -- Reuses the existing confirmed-session, business-category and idempotency checks.
  profile_result := private.complete_my_profile('vendor',p_name,p_business_name,p_category,p_phone);
  vendor := (profile_result->>'vendor_id')::uuid;
  if vendor is null then
    raise exception 'Professional profile unavailable' using errcode='P0001';
  end if;

  select count(*) into existing_count from public.products where vendor_id=vendor;
  if existing_count=0 then
    insert into public.products(vendor_id,name,price,is_active)
    select vendor,pg_catalog.btrim(item.name),0,true
    from pg_catalog.unnest(p_product_names) with ordinality as item(name,position)
    order by item.position;
  end if;

  select count(*) into product_count from public.products where vendor_id=vendor;
  return pg_catalog.jsonb_build_object(
    'profile',profile_result,
    'catalogue_initialized',existing_count=0,
    'product_count',product_count,
    'products',coalesce((
      select pg_catalog.jsonb_agg(
        pg_catalog.jsonb_build_object('id',p.id,'name',p.name,'is_active',p.is_active)
        order by p.created_at,p.id
      )
      from public.products p where p.vendor_id=vendor
    ),'[]'::jsonb)
  );
end
$$;

create function public.complete_vendor_onboarding(
  p_name text,
  p_business_name text,
  p_category text,
  p_product_names text[],
  p_phone text default null
) returns jsonb
language sql security invoker set search_path=''
as $$
  select private.complete_vendor_onboarding(
    p_name,p_business_name,p_category,p_product_names,p_phone
  )
$$;

revoke all on function private.complete_vendor_onboarding(text,text,text,text[],text)
  from public,anon,authenticated,service_role;
revoke all on function public.complete_vendor_onboarding(text,text,text,text[],text)
  from public,anon,authenticated,service_role;
grant execute on function private.complete_vendor_onboarding(text,text,text,text[],text)
  to authenticated;
grant execute on function public.complete_vendor_onboarding(text,text,text,text[],text)
  to authenticated;
notify pgrst,'reload schema';
commit;
