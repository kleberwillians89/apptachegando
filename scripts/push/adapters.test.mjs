import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { createPushStore, createFcmTransport } from './adapters.mjs';
import { runWorker } from './worker.mjs';
const keys=generateKeyPairSync('rsa',{modulusLength:2048});
const serviceAccount={project_id:'test-qa-project',client_email:'test@test-qa-project.iam.gserviceaccount.com',private_key:keys.privateKey.export({type:'pkcs8',format:'pem'})};
const response=(body,status=200,headers={})=>new Response(JSON.stringify(body),{status,headers});
const endpoints={tokenUrl:'http://127.0.0.1:12345/token',sendUrl:'http://127.0.0.1:12345/send'};
test('RS256 OAuth assertion has fixed audience/scope, caches token and wraps HTTP v1 payload',async()=>{
 let tokens=0,sends=0;
 const fcm=createFcmTransport({projectId:'test-qa-project',serviceAccount,testEndpoints:endpoints,now:()=>100000,fetchImpl:async(url,options)=>{
  assert.equal(options.redirect,'error');
  if(url.pathname==='/token'){
   tokens++;const assertion=new URLSearchParams(options.body).get('assertion');const parts=assertion.split('.');
   assert(verify('RSA-SHA256',Buffer.from(parts.slice(0,2).join('.')),keys.publicKey,Buffer.from(parts[2],'base64url')));
   const p=JSON.parse(Buffer.from(parts[1],'base64url'));assert.equal(p.aud,'https://oauth2.googleapis.com/token');assert.equal(p.scope,'https://www.googleapis.com/auth/firebase.messaging');assert.equal(p.exp-p.iat,3600);
   return response({access_token:'controlled-token',expires_in:3600});
  }
  sends++;assert.equal(options.headers.Authorization,'Bearer controlled-token');assert.equal(JSON.parse(options.body).message.data.type,'route_interest');return response({name:'projects/test-qa-project/messages/test'});
 }});
 const message={token:'synthetic-fcm-token',data:{type:'route_interest'},android:{priority:'HIGH',ttl:'30s'}};
 assert((await fcm.send(message)).accepted);assert((await fcm.send(message)).accepted);assert.equal(tokens,1);assert.equal(sends,2);
});
test('UNREGISTERED, provider backoff and permanent errors are distinct',async()=>{
 let status=404;
 const fcm=createFcmTransport({projectId:'test-qa-project',serviceAccount,testEndpoints:endpoints,fetchImpl:async url=>url.pathname==='/token'?response({access_token:'controlled-token',expires_in:3600}):status===404?response({error:{details:[{'@type':'type.googleapis.com/google.firebase.fcm.v1.FcmError',errorCode:'UNREGISTERED'}]}},404):response({error:{}},status,{'retry-after':'25'})});
 assert.equal((await fcm.send({})).code,'UNREGISTERED');status=429;const r=await fcm.send({});assert.equal(r.retryable,true);assert.equal(r.retryAfterSeconds,25);status=403;assert.equal((await fcm.send({})).retryable,false);
});
test('401 renews OAuth once; success without provider message ID is rejected',async()=>{
 let oauth=0,sends=0;
 const fcm=createFcmTransport({projectId:'test-qa-project',serviceAccount,testEndpoints:endpoints,fetchImpl:async url=>{
  if(url.pathname==='/token'){oauth++;return response({access_token:'controlled-token-'+oauth,expires_in:3600});}
  return ++sends===1?response({error:{}},401):response({});
 }});
 await assert.rejects(fcm.send({}),/No provider confirmation/);assert.equal(oauth,2);assert.equal(sends,2);
});
test('opaque server keys use apikey only and unknown/prod origins are refused',async()=>{
 const store=createPushStore({url:'https://vhnkezwjknwpddqhlnfk.supabase.co',serviceKey:'sb_secret_controlled',fetchImpl:async(url,o)=>{assert.equal(o.headers.Authorization,undefined);assert.equal(o.headers.apikey,'sb_secret_controlled');assert.equal(url.pathname,'/rest/v1/rpc/claim_interest_push_job');return response(null);}});
 assert.equal(await store.claim(null),null);
 assert.throws(()=>createPushStore({url:'https://other-project.supabase.co',serviceKey:'sb_secret_controlled'}));
 assert.throws(()=>createPushStore({url:'https://vhnkezwjknwpddqhlnfk.supabase.co',serviceKey:'sb_publishable_not_server'}));
 assert.throws(()=>createFcmTransport({projectId:'test-qa-project',serviceAccount,testEndpoints:{tokenUrl:'https://attacker.invalid/',sendUrl:endpoints.sendUrl}}));
});
test('DB errors and lost ACK leases never become success',async()=>{
 const bad=createPushStore({url:'http://127.0.0.1:12345',serviceKey:'test',allowLocalTest:true,fetchImpl:async()=>response({},403)});await assert.rejects(bad.claim(null));
 const lost=createPushStore({url:'http://127.0.0.1:12345',serviceKey:'test',allowLocalTest:true,fetchImpl:async()=>response(false)});await assert.rejects(lost.finish({id:'id',claimToken:'lease'},'provider-accepted'));
});
test('worker has no public HTTP listener and requires explicit QA execution configuration',async()=>{
 const r=spawnSync(process.execPath,['scripts/push/worker.mjs','--once'],{env:{PATH:process.env.PATH},encoding:'utf8'});assert.equal(r.status,1);assert.match(r.stderr,/disabled/);
 const abort=new AbortController();abort.abort();let claims=0;await runWorker({store:{claim:async()=>{claims++;}},fcm:{},signal:abort.signal});assert.equal(claims,0);
});
