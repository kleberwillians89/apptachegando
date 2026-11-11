-- Synthetic fixtures only. No passwords, no production users, no restored data.
-- These identities exercise SQL/RLS; create actual local sign-in accounts through Auth.
insert into auth.users(id,email,raw_user_meta_data) values
 ('10000000-0000-4000-8000-000000000001','consumer-a@example.test','{"name":"Consumidor A"}'),
 ('10000000-0000-4000-8000-000000000002','consumer-b@example.test','{"name":"Consumidor B"}'),
 ('20000000-0000-4000-8000-000000000001','vendor-a@example.test','{"name":"Vendedor A","role":"vendor","business_name":"Produtos A"}'),
 ('20000000-0000-4000-8000-000000000002','vendor-b@example.test','{"name":"Vendedor B","role":"vendor","business_name":"Produtos B"}')
on conflict(id) do nothing;
-- Synthetic confirmed Auth fixtures: exercise the same authenticated completion as web/Android.
update auth.users set email_confirmed_at=now() where id in ('10000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000002','20000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000002');
insert into auth.sessions(id,user_id) select id,id from auth.users where id in
 ('10000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000002','20000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000002') on conflict(id) do nothing;
set role authenticated;
select set_config('request.jwt.claim.sub','20000000-0000-4000-8000-000000000001',false);
select set_config('request.jwt.claims','{"session_id":"20000000-0000-4000-8000-000000000001"}',false);
select public.complete_my_profile('vendor','Vendedor A','Produtos A','Pães');
select set_config('request.jwt.claim.sub','20000000-0000-4000-8000-000000000002',false);
select set_config('request.jwt.claims','{"session_id":"20000000-0000-4000-8000-000000000002"}',false);
select public.complete_my_profile('vendor','Vendedor B','Produtos B','Frutas');
reset role;
select set_config('request.jwt.claim.sub','',false);
select set_config('request.jwt.claims','{}',false);
-- Vendor IDs are different from Auth IDs and deterministic only in these fixtures.
update public.vendors set id='30000000-0000-4000-8000-000000000001',is_online=true where user_id='20000000-0000-4000-8000-000000000001';
update public.vendors set id='30000000-0000-4000-8000-000000000002',is_online=true where user_id='20000000-0000-4000-8000-000000000002';
insert into public.products(id,vendor_id,name,price) values
 ('40000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001','Produto A',12.50),
 ('40000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000002','Produto B',8.00)
on conflict(id) do nothing;
