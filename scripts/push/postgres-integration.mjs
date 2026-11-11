/** Invoked only by the disposable PG harness. Actual SQL + actual loopback HTTP transports.
 * Auth schema is a local shim; OAuth/FCM responses are controlled, no Google/Supabase calls. */
import assert from 'node:assert/strict';
import { execFileSync, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createServer } from 'node:http';
import { generateKeyPairSync, verify } from 'node:crypto';
import { createPushStore,createFcmTransport } from './adapters.mjs';
import { runWorker } from './worker.mjs';
const socket=process.env.TCH_TEST_PG_SOCKET;
assert(socket?.includes('/tch-sql-') && socket.endsWith('/sock'),'Disposable Unix socket required');
const pg=process.env.TCH_TEST_PG_BIN+'/psql';
const args=['-X','-qAt','-v','ON_ERROR_STOP=1','-h',socket,'-p','55433','-U','postgres','-d','postgres'];
const sql=query=>execFileSync(pg,[...args,'-c',query],{encoding:'utf8',stdio:['ignore','pipe','pipe']}).trim();
const literal=value=>value===null?'null':typeof value==='number'?String(value):"'"+String(value).replaceAll("'","''")+"'";
const owner='60000000-0000-4000-8000-000000000009',vendor='30000000-0000-4000-8000-000000000009',session='70000000-0000-4000-8000-000000000009',route='80000000-0000-4000-8000-000000000009',installation='90000000-0000-4000-8000-000000000009';
const version=n=>'a0000000-0000-4000-8000-'+String(n).padStart(12,'0');
const actor=(user,sessionId,query)=>`begin;set local role authenticated;select set_config('request.jwt.claim.sub',${literal(user)},true);select set_config('request.jwt.claims',${literal(JSON.stringify({session_id:sessionId}))},true);${query};commit;`;
const vendorSql=query=>sql(actor(owner,session,query));
sql(`insert into auth.users(id,email,raw_user_meta_data) values('${owner}','push-integration@example.test','{"role":"vendor","name":"Synthetic integration"}');insert into public.vendors(id,user_id,name,is_online) values('${vendor}','${owner}','Synthetic integration',true);insert into auth.sessions(id,user_id) values('${session}','${owner}');insert into public.vendor_routes(id,vendor_id,name,waypoints) values('${route}','${vendor}','Synthetic integration route','[{"latitude":0,"longitude":0,"orderIndex":0},{"latitude":0,"longitude":0.01,"orderIndex":1}]')`);
vendorSql(`select public.register_interest_push_device('${installation}','integration-token-aaaaaaaaaa','${version(1)}');select public.publish_route_location('${route}',0,0.002,5,clock_timestamp()-interval '1 millisecond')`);
function interest(consumer){return sql(actor(consumer,null,`select public.create_purchase_intent('${vendor}',0,0.008)`)).split('\n').findLast(x=>/^[0-9a-f-]{36}$/.test(x));}
const first=interest('10000000-0000-4000-8000-000000000001');assert(first);
let oauthCalls=0,sends=0,mode='accepted';const keys=generateKeyPairSync('rsa',{modulusLength:2048});
const allowed={claim_interest_push_job:{p_job_id:'uuid'},interest_push_job_binding:{p_job_id:'uuid',p_claim_token:'uuid'},interest_push_job_eligible:{p_job_id:'uuid',p_claim_token:'uuid'},finish_interest_push_job:{p_job_id:'uuid',p_claim_token:'uuid',p_result:'text',p_provider_message_id:'text'},retry_interest_push_job:{p_job_id:'uuid',p_claim_token:'uuid',p_delay_seconds:'integer'},invalidate_interest_push_device:{p_owner_id:'uuid',p_installation_id:'uuid',p_binding_version:'uuid'}};
const server=createServer(async(req,res)=>{
 try{
  let text='';for await(const c of req){text+=c;if(text.length>65536)throw Error('Large request');}
  res.setHeader('Content-Type','application/json');
  if(req.url==='/oauth'){
   oauthCalls++;const assertion=new URLSearchParams(text).get('assertion').split('.');assert(verify('RSA-SHA256',Buffer.from(assertion.slice(0,2).join('.')),keys.publicKey,Buffer.from(assertion[2],'base64url')));
   res.end(JSON.stringify({access_token:'local-controlled-oauth',expires_in:3600}));return;
  }
  if(req.url==='/fcm'){
   sends++;assert.equal(req.headers.authorization,'Bearer local-controlled-oauth');const msg=JSON.parse(text).message;assert.equal(msg.notification,undefined);assert.equal(msg.data.owner_id,owner);assert.equal(msg.data.route_id,route);
   if(mode==='rotate')vendorSql(`select public.register_interest_push_device('${installation}','integration-token-cccccccccc','${version(3)}')`);
   if(mode==='invalid'||mode==='rotate'){res.statusCode=404;res.end(JSON.stringify({error:{details:[{'@type':'type.googleapis.com/google.firebase.fcm.v1.FcmError',errorCode:'UNREGISTERED'}]}}));return;}
   res.end(JSON.stringify({name:'projects/test-qa-project/messages/controlled-'+sends}));return;
  }
  assert.equal(req.headers.apikey,'sb_secret_local_controlled');assert.equal(req.headers.authorization,undefined);
  const name=req.url.split('/').at(-1),schema=allowed[name];assert(schema);const body=JSON.parse(text);
  const params=Object.entries(body).map(([k,v])=>{assert(schema[k]);return `${k}=>${literal(v)}::${schema[k]}`;}).join(',');
  const output=sql(`begin;set local role service_role;select coalesce(to_jsonb(public.${name}(${params})),'null'::jsonb);commit;`);res.end(output);
 }catch{res.statusCode=400;res.end('{"error":"Controlled transport failure"}');}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;
try{
 const store=createPushStore({url:origin,serviceKey:'sb_secret_local_controlled',allowLocalTest:true});
 const fcm=createFcmTransport({projectId:'test-qa-project',serviceAccount:{project_id:'test-qa-project',client_email:'worker@test-qa-project.iam.gserviceaccount.com',private_key:keys.privateKey.export({format:'pem',type:'pkcs8'})},testEndpoints:{tokenUrl:origin+'/oauth',sendUrl:origin+'/fcm'}});
 assert.equal(await runWorker({store,fcm,once:true}),'provider-accepted');assert.equal(sql(`select status||':'||provider_message_id from private.interest_push_outbox where protocol_id='${first}'`),'provider-accepted:projects/test-qa-project/messages/controlled-1');
 assert.equal(await runWorker({store,fcm,once:true}),'already-claimed');assert.equal(sends,1);console.log('RC1 INTEGRATION PASS: real PostgreSQL outbox through HTTP adapters, OAuth assertion, HTTP v1 provider ACK and dedupe');
 const second=interest('10000000-0000-4000-8000-000000000002');mode='invalid';assert.equal(await runWorker({store,fcm,once:true}),'invalid-token');assert.equal(sql(`select revoked_at is not null from private.interest_push_devices where owner_id='${owner}'`),'t');console.log('RC1 INTEGRATION PASS: real token invalidation on controlled FCM UNREGISTERED');
 vendorSql(`select public.register_interest_push_device('${installation}','integration-token-bbbbbbbbbb','${version(2)}')`);
 sql(`update public.purchase_intents set status='cancelled' where id='${first}'`);const third=interest('10000000-0000-4000-8000-000000000001');mode='rotate';assert.equal(await runWorker({store,fcm,once:true}),'retry');assert.equal(sql(`select (revoked_at is null)::text||':'||binding_version from private.interest_push_devices where owner_id='${owner}'`),'true:'+version(3));
 sql(`update private.interest_push_outbox set next_attempt_at=clock_timestamp()-interval '1 second' where protocol_id='${third}'`);mode='accepted';assert.equal(await runWorker({store,fcm,once:true}),'provider-accepted');assert.equal(oauthCalls,1);console.log('RC1 INTEGRATION PASS: rotation during FCM failure keeps new token and retries real outbox');
 sql(`update public.purchase_intents set status='cancelled' where id='${third}'`);const fourth=interest('10000000-0000-4000-8000-000000000001');
 const asyncExec=promisify(execFile);const claim=`begin;set local role service_role;select public.claim_interest_push_job((select null::uuid));commit;`;
 const concurrent=await Promise.all(Array.from({length:6},()=>asyncExec(pg,[...args,'-c',claim],{encoding:'utf8'})));
 const jobs=concurrent.map(r=>r.stdout.trim()).filter(x=>x && x!=='null');assert.equal(jobs.length,1);console.log('RC1 INTEGRATION PASS: six real concurrent PostgreSQL claims produce exactly one lease');
 const revoke=actor(owner,session,`select public.revoke_interest_push_device('${installation}','${version(3)}');select pg_sleep(0.1)`);
 const register=actor(owner,session,`select public.register_interest_push_device('${installation}','integration-token-cccccccccc','${version(3)}');select pg_sleep(0.1)`);
 await Promise.allSettled([asyncExec(pg,[...args,'-c',register]),asyncExec(pg,[...args,'-c',revoke])]);assert.equal(sql(`select revoked_at is not null from private.interest_push_devices where owner_id='${owner}'`),'t');console.log('RC1 INTEGRATION PASS: real registration/revocation race ends revoked');
 assert.equal(sql(`select count(*) from public.purchase_intents where id in ('${first}','${second}','${third}','${fourth}')`),'4');console.log('RC1 INTEGRATION PASS: protocol records preserved across worker operations');
}finally{await new Promise(resolve=>server.close(resolve));}
