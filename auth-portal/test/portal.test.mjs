// LOCAL tests with injected fake Auth. No real signup, verification, password change or user session.
import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { once } from 'node:events';
import { createPortal } from '../src/server.mjs';
import { SealedCookies } from '../src/sealed-cookie.mjs';
import { AuthFailure, SupabaseAuth } from '../src/supabase.mjs';
import { loadConfig } from '../src/config.mjs';

const HASH = 'a'.repeat(64), OTHER_HASH = 'b'.repeat(64);
async function fixture(t, { role = 'consumer', mode = 'success' } = {}) {
  let time = Date.now();
  const calls = [];
  const auth = {
    mode,
    async verify(hash, type) {
      calls.push({ operation: 'verify', hash, type });
      if (auth.mode === 'temporary') throw new AuthFailure('temporary');
      if (auth.mode === 'invalid') throw new AuthFailure('invalid');
      if (auth.mode === 'no-proof') return { user: { id: 'synthetic-user' } };
      return { user: { id: 'synthetic-' + role, email_confirmed_at: '2026-11-01T00:00:00Z' }, access_token: 'SYNTHETIC_ACCESS_ONLY', refresh_token: 'SYNTHETIC_REFRESH_ONLY', expires_in: 3600 };
    },
    async requestConfirmation(email) { calls.push({ operation: 'request-confirmation', email }); if(auth.mode==='temporary') throw new AuthFailure('temporary'); },
    async requestRecovery(email) { calls.push({ operation: 'request-recovery', email }); if(auth.mode==='temporary') throw new AuthFailure('temporary'); },
    async updatePassword(token, password, userId) { calls.push({ operation: 'password', token, password, userId }); if (auth.mode === 'password-failure') throw new AuthFailure('password_rejected'); },
    async logoutTransient(token) { calls.push({ operation: 'logout-local', token }); if (auth.mode === 'logout-failure') throw new AuthFailure('temporary'); }
  };
  const config = { origin: 'http://127.0.0.1', secure: false, cookieKey: randomBytes(32) };
  const server = createPortal(config, { auth, now: () => time });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  config.origin = `http://127.0.0.1:${server.address().port}`;
  t.after(() => new Promise(ok => { server.close(ok); server.closeAllConnections(); }));
  const jar = new Map([['existing_site_session', 'synthetic-unrelated-session']]);
  async function request(path, { method = 'GET', body, origin = config.origin, cookies = true, extraHeaders = {} } = {}) {
    const headers = { ...extraHeaders };
    if (cookies) headers.Cookie = [...jar].map(([k,v]) => `${k}=${v}`).join('; ');
    if (method === 'POST') { headers.Origin = origin; headers['Content-Type'] = 'application/x-www-form-urlencoded'; }
    const response = await fetch(config.origin + path, { method, headers, body, redirect: 'manual' });
    for (const cookie of response.headers.getSetCookie()) { const [pair] = cookie.split(';'), i = pair.indexOf('='); jar.set(pair.slice(0,i), pair.slice(i+1)); }
    return { response, text: await response.text() };
  }
  async function begin(kind = 'confirm', hash = HASH, type = kind === 'confirm' ? 'email' : 'recovery') {
    const first = await request(`/auth/${kind}?token_hash=${hash}&type=${type}`);
    assert.equal(first.response.status, 303);
    assert.equal(first.response.headers.get('location'), `/auth/${kind}`);
    const page = await request(`/auth/${kind}`);
    const csrf = /name="csrf" value="([^"]+)"/.exec(page.text)?.[1]; assert(csrf);
    return { first, page, csrf };
  }
  const post = (kind, csrf, action = 'verify', fields = {}) => request(`/auth/${kind}`, { method: 'POST', body: new URLSearchParams({ csrf, action, ...fields }) });
  return { calls, auth, config, server, jar, request, begin, post, advance: ms => time += ms };
}

for (const role of ['consumer', 'vendor']) test(`${role}: explicit confirmation requires a real Auth proof (fake transport only)`, async t => {
  const f = await fixture(t, { role }); const { csrf } = await f.begin();
  assert.equal(f.calls.length, 0);
  const r = await f.post('confirm', csrf); assert.equal(r.response.status, 303);
  const page = await f.request('/auth/confirm'); assert.match(page.text, /Email confirmado/);
  assert.deepEqual(f.calls.map(x=>x.operation), ['verify', 'logout-local']);
  assert.equal(f.calls[0].hash,HASH); assert.equal(f.calls[0].type,'email');
  assert(!page.text.includes('SYNTHETIC_ACCESS_ONLY')); assert(!page.text.includes('SYNTHETIC_REFRESH_ONLY'));
  assert(![...f.jar.values()].join('').includes('SYNTHETIC_ACCESS_ONLY'));
  assert.equal(f.jar.get('existing_site_session'), 'synthetic-unrelated-session');
});

test('email scanners/prefetch GETs never consume the token; clean redirect, no token in HTML', async t => {
  const f = await fixture(t); const { first, page } = await f.begin();
  for (let i=0; i<3; i++) await f.request('/auth/confirm');
  assert.equal(f.calls.length, 0); assert(!first.text.includes(HASH)); assert(!page.text.includes(HASH));
  assert.match(first.response.headers.get('set-cookie'), /HttpOnly/);
  assert.match(first.response.headers.get('set-cookie'), /SameSite=Lax/);
  assert(!first.response.headers.get('set-cookie').includes(HASH));
  assert.equal(page.response.headers.get('referrer-policy'), 'no-referrer');
  assert.match(page.response.headers.get('cache-control'), /no-store/);
  assert.match(page.response.headers.get('content-security-policy'), /frame-ancestors 'none'/);
});

test('confirmation works in a different browser without a PKCE verifier or Android', async t => {
  const f = await fixture(t); const { csrf } = await f.begin();
  await f.post('confirm', csrf);
  assert.match((await f.request('/auth/confirm')).text, /Email confirmado/);
});

test('invalid/expired/reused unknown token remains an honest ambiguous error', async t => {
  const f = await fixture(t, { mode: 'invalid' }); const { csrf } = await f.begin();
  await f.post('confirm', csrf); const page = await f.request('/auth/confirm');
  assert.match(page.text, /Link inválido, expirado ou já utilizado/); assert(!page.text.includes('<h1 id="title">Email confirmado'));
});

test('a known successful receipt identifies reuse without another verify', async t => {
  const f = await fixture(t); const { csrf } = await f.begin(); await f.post('confirm', csrf);
  const reopened = await f.request(`/auth/confirm?token_hash=${HASH}&type=email`);
  assert.equal(reopened.response.status, 303);
  assert.match((await f.request('/auth/confirm')).text, /Este link já foi confirmado/);
  assert.equal(f.calls.length, 2);
});

test('a different token never inherits success from a previous confirmed receipt', async t => {
  const f = await fixture(t); const { csrf } = await f.begin(); await f.post('confirm', csrf);
  const next = await f.begin('confirm', OTHER_HASH); assert.match(next.page.text, /Confirmar meu email/);
  assert(!next.page.text.includes('<h1 id="title">Email confirmado'));
});

test('a 200 without confirmed user is not success', async t => {
  const f = await fixture(t, { mode: 'no-proof' }); const { csrf } = await f.begin();
  assert.equal((await f.post('confirm', csrf)).response.status, 503);
  assert(!((await f.request('/auth/confirm')).text.includes('<h1 id="title">Email confirmado')));
});

test('temporary failure preserves retry rather than claiming success', async t => {
  const f = await fixture(t, { mode: 'temporary' }); const { csrf } = await f.begin();
  const result = await f.post('confirm', csrf); assert.equal(result.response.status, 503); assert.match(result.text, /Confirmar meu email/);
  f.auth.mode = 'success'; await f.post('confirm', csrf); assert.match((await f.request('/auth/confirm')).text, /Email confirmado/);
});

test('missing CSRF, Unicode CSRF and external Origin are rejected without hitting Auth', async t => {
  const f = await fixture(t); const { csrf } = await f.begin();
  assert.equal((await f.post('confirm', 'wrong')).response.status, 403);
  assert.equal((await f.post('confirm', 'é'.repeat(csrf.length))).response.status, 403);
  assert.equal((await f.request('/auth/confirm', { method:'POST', origin:'https://evil.example', body:new URLSearchParams({ csrf, action:'verify' }) })).response.status, 403);
  assert.equal(f.calls.length, 0);
});

test('operation confusion, duplicate parameters, session parameters and open redirects rejected', async t => {
  const f = await fixture(t);
  for (const path of [
    `/auth/confirm?token_hash=${HASH}&type=recovery`, `/auth/recovery?token_hash=${HASH}&type=email`,
    `/auth/confirm?token_hash=${HASH}&type=email&type=email`, `/auth/confirm?token_hash=${HASH}&type=email&next=https://evil.example`,
    '/auth/confirm?code=synthetic', '/auth/open?next=https://evil.example', '/auth/confirm?access_token=synthetic&refresh_token=synthetic',
    `/auth/confirm?token_hash=${encodeURIComponent('<script>alert(1)</script>')}&type=email`
  ]) assert.equal((await f.request(path)).response.status, 400, path);
  assert.equal(f.calls.length, 0);
});

test('flow expires locally without a request to Auth', async t => {
  const f = await fixture(t); const { csrf } = await f.begin(); f.advance(16*60*1000);
  assert.equal((await f.post('confirm', csrf)).response.status, 403); assert.equal(f.calls.length, 0);
});

test('recovery verifies explicitly, stores no public session, changes password and logs out only recovery', async t => {
  const f = await fixture(t); const { csrf } = await f.begin('recovery'); await f.post('recovery', csrf);
  const ready = await f.request('/auth/recovery'); assert.match(ready.text, /Escolha sua nova senha/);
  assert(!ready.text.includes('SYNTHETIC_ACCESS_ONLY')); assert(!ready.text.includes('SYNTHETIC_REFRESH_ONLY'));
  assert(![...f.jar.values()].join('').includes('SYNTHETIC_ACCESS_ONLY'));
  const next = /name="csrf" value="([^"]+)"/.exec(ready.text)[1];
  await f.post('recovery', next, 'password', { password:'SyntheticPassword1!', confirmation:'SyntheticPassword1!' });
  assert.match((await f.request('/auth/recovery')).text, /Senha alterada/);
  assert.deepEqual(f.calls.map(x=>x.operation), ['verify','password','logout-local']);
  assert.equal(f.jar.get('existing_site_session'), 'synthetic-unrelated-session');
});

test('password cannot be changed without verification; wrong confirmation rejected', async t => {
  const f = await fixture(t); const { csrf } = await f.begin('recovery');
  assert.equal((await f.post('recovery', csrf, 'password', {password:'SyntheticPassword1!',confirmation:'SyntheticPassword1!'})).response.status, 400);
  await f.post('recovery', csrf); const page = await f.request('/auth/recovery');
  const next = /name="csrf" value="([^"]+)"/.exec(page.text)[1];
  assert.equal((await f.post('recovery', next, 'password', {password:'SyntheticPassword1!',confirmation:'different'})).response.status, 400);
  assert.equal(f.calls.filter(x=>x.operation==='password').length, 0);
});

test('password policy failure remains editable and does not log out an unrelated account', async t => {
  const f = await fixture(t); const { csrf } = await f.begin('recovery'); await f.post('recovery', csrf);
  const page = await f.request('/auth/recovery'), next = /name="csrf" value="([^"]+)"/.exec(page.text)[1];
  f.auth.mode='password-failure'; const fail=await f.post('recovery',next,'password',{password:'SyntheticPassword1!',confirmation:'SyntheticPassword1!'});
  assert.equal(fail.response.status,400);assert.match(fail.text,/name="password"/);
  assert.equal(f.calls.filter(x=>x.operation==='logout-local').length,0);
});

test('logout failure after confirmed password update is explicitly disclosed', async t => {
  const f=await fixture(t);const {csrf}=await f.begin('recovery');await f.post('recovery',csrf);
  const next=/name="csrf" value="([^"]+)"/.exec((await f.request('/auth/recovery')).text)[1];
  f.auth.mode='logout-failure';await f.post('recovery',next,'password',{password:'SyntheticPassword1!',confirmation:'SyntheticPassword1!'});
  assert.match((await f.request('/auth/recovery')).text,/não foi possível confirmar o encerramento/);
});

test('without installed app browser fallback is honest; legacy fragment is scrubbed, never imported', async t => {
  const f=await fixture(t);assert.match((await f.request('/auth/open')).text,/Se o aplicativo não estiver instalado/);
  const callback=await f.request('/auth/callback');assert.match(callback.text,/history.replaceState/);assert.equal(f.calls.length,0);
});

test('assetlinks contains public real fingerprints and no invented release association',async t=>{
  const f=await fixture(t),r=await f.request('/.well-known/assetlinks.json');assert.equal(r.response.status,200);
  const links=JSON.parse(r.text);assert.deepEqual(links.map(x=>x.target.package_name).sort(),['org.example.project','org.example.project.homologation','org.example.project.performance']);
  assert(links.every(x=>x.target.sha256_cert_fingerprints[0]==='0A:09:84:68:2D:45:DB:7E:56:4F:53:B6:83:24:1B:52:33:1B:A3:14:7F:34:2F:0F:B7:FB:7F:58:02:15:ED:E3'));
});

test('sealed cookies are purpose-bound, tamper-resistant and secure in HTTPS',()=>{
  const jar=new SealedCookies({cookieKey:randomBytes(32),secure:true});const value={expiresAt:Date.now()+60000,tokenHash:HASH};
  const encoded=jar.seal(value,'confirm');assert.equal(jar.open(encoded,'confirm').tokenHash,HASH);assert.equal(jar.open(encoded,'recovery'),null);
  assert.equal(jar.open('changed'+encoded,'confirm'),null);assert.match(jar.header(value,'confirm'),/; Secure/);
});

test('configuration rejects server keys, wrong project/origin and missing cookie key',()=>{
  const env={APP_ORIGIN:'https://app.tachegandoapp.com.br',SUPABASE_URL:'https://vhnkezwjknwpddqhlnfk.supabase.co',SUPABASE_PUBLISHABLE_KEY:'sb_publishable_synthetic',COOKIE_ENCRYPTION_KEY:randomBytes(32).toString('base64')};
  assert(loadConfig(env));for(const changes of [{SUPABASE_PUBLISHABLE_KEY:'sb_secret_not_real'},{SUPABASE_URL:'https://other.supabase.co'},{APP_ORIGIN:'https://evil.example'},{COOKIE_ENCRYPTION_KEY:''},{APP_ORIGIN:'http://127.0.0.1:3000',NODE_ENV:'production'},{APP_ORIGIN:'http://127.0.0.1:3000',HOST:'0.0.0.0'}])assert.throws(()=>loadConfig({...env,...changes}));
});

test('official Auth transport uses fixed endpoints, no redirects and local logout only',async()=>{
  const calls=[];const auth=new SupabaseAuth({origin:'https://app.tachegandoapp.com.br',supabaseUrl:'https://example.invalid',publishableKey:'sb_publishable_synthetic'},async(url,options)=>{calls.push({url,options});return new Response(JSON.stringify({id:'synthetic-user'}),{status:200});});
  await auth.verify(HASH,'email');await auth.updatePassword('synthetic-access','synthetic-password','synthetic-user');await auth.logoutTransient('synthetic-access');await auth.requestRecovery('synthetic@example.test');
  assert.equal(calls[0].url,'https://example.invalid/auth/v1/verify');assert.equal(calls[1].options.method,'PUT');assert.equal(calls[2].url,'https://example.invalid/auth/v1/logout?scope=local');assert(calls.every(c=>c.options.redirect==='error'));assert.equal(calls[3].url,'https://example.invalid/auth/v1/recover?redirect_to=https%3A%2F%2Fapp.tachegandoapp.com.br%2Fauth%2Frecovery');
});


test('confirmation is proven even when transient logout fails, with explicit disclosure',async t=>{
  const f=await fixture(t,{mode:'logout-failure'});const {csrf}=await f.begin();await f.post('confirm',csrf);
  const page=await f.request('/auth/confirm');assert.match(page.text,/Email confirmado/);assert.match(page.text,/encerramento da sessão temporária/);
});


test('browser without app can request recovery, with neutral result and no account disclosure',async t=>{
  const f=await fixture(t);const page=await f.request('/auth/recovery');assert.match(page.text,/name="email"/);assert.equal(f.calls.length,0);
  const csrf=/name="csrf" value="([^"]+)"/.exec(page.text)[1];
  await f.post('recovery',csrf,'request',{email:'synthetic@example.test'});
  const result=await f.request('/auth/recovery');assert.match(result.text,/Se o email estiver cadastrado/);
  assert.deepEqual(f.calls,[{operation:'request-recovery',email:'synthetic@example.test'}]);
});

test('failed recovery email request remains retryable, not falsely sent',async t=>{
  const f=await fixture(t,{mode:'temporary'});const page=await f.request('/auth/recovery');const csrf=/name="csrf" value="([^"]+)"/.exec(page.text)[1];
  const result=await f.post('recovery',csrf,'request',{email:'synthetic@example.test'});assert.equal(result.response.status,503);assert.match(result.text,/name="email"/);assert(!result.text.includes('Email enviado'));
});


test('cookies cannot cross portal origins even if an operator reused an encryption key',()=>{
  const key=randomBytes(32),a=new SealedCookies({cookieKey:key,origin:'http://127.0.0.1:3000',secure:false}),b=new SealedCookies({cookieKey:key,origin:'https://app.tachegandoapp.com.br',secure:true});
  const cookie=a.seal({expiresAt:Date.now()+60000,state:'confirmed'},'confirm');assert.equal(b.open(cookie,'confirm'),null);
});


test('inspection mode blocks all Auth POSTs before any upstream call and labels the UI',async t=>{
  const f=await fixture(t);f.config.inspection=true;const {csrf,page}=await f.begin();assert.match(page.text,/INSPEÇÃO LOCAL/);
  const result=await f.post('confirm',csrf);assert.equal(result.response.status,503);assert.match(result.text,/Operação desativada/);
  const recovery=await f.request('/auth/recovery'),token=/name="csrf" value="([^"]+)"/.exec(recovery.text)[1];
  assert.equal((await f.post('recovery',token,'request',{email:'synthetic@example.test'})).response.status,503);
  assert.equal((await f.post('recovery',token,'password',{password:'SyntheticPassword1!',confirmation:'SyntheticPassword1!'})).response.status,503);
  assert.equal(f.calls.length,0);
});


test('confirmation can be resent in browser without installing app; GET sends nothing and result is neutral', async t => {
  const f = await fixture(t); const page = await f.request('/auth/confirm');
  assert.match(page.text, /Reenvie a confirmação/); assert.equal(f.calls.length, 0);
  const csrf = /name="csrf" value="([^"]+)"/.exec(page.text)[1];
  assert.equal((await f.post('confirm', csrf, 'request', {email:'synthetic@example.test'})).response.status, 303);
  const result = await f.request('/auth/confirm'); assert.match(result.text, /Se houver um cadastro aguardando confirmação/);
  assert.deepEqual(f.calls, [{operation:'request-confirmation',email:'synthetic@example.test'}]);
  const next = /name="csrf" value="([^"]+)"/.exec(result.text)[1];
  assert.equal((await f.post('confirm', next, 'request', {email:'another@example.test'})).response.status, 429);
  f.advance(60000); assert.equal((await f.post('confirm', next, 'request', {email:'synthetic@example.test'})).response.status, 303);
});

test('resend failure stays an error, is throttled, and CSRF is required before any Auth call', async t => {
  const f = await fixture(t, {mode:'temporary'}); const page = await f.request('/auth/confirm');
  const csrf = /name="csrf" value="([^"]+)"/.exec(page.text)[1];
  assert.equal((await f.post('confirm','forged','request',{email:'synthetic@example.test'})).response.status,403); assert.equal(f.calls.length,0);
  const result = await f.post('confirm',csrf,'request',{email:'synthetic@example.test'});
  assert.equal(result.response.status,503); assert.match(result.text,/name="email"/); assert(!result.text.includes('Email enviado'));
  assert.equal((await f.post('confirm',csrf,'request',{email:'synthetic@example.test'})).response.status,429); assert.equal(f.calls.length,1);
});

test('resend Auth transport pins signup type and confirmation redirect without exposing admin keys', async () => {
  let call; const auth = new SupabaseAuth({origin:'https://app.tachegandoapp.com.br',supabaseUrl:'https://example.invalid',publishableKey:'sb_publishable_test'}, async (url,options) => {
    call = {url,options}; return new Response('{}',{status:200});
  });
  await auth.requestConfirmation('synthetic@example.test');
  assert.equal(call.url,'https://example.invalid/auth/v1/resend?redirect_to=https%3A%2F%2Fapp.tachegandoapp.com.br%2Fauth%2Fconfirm');
  assert.deepEqual(JSON.parse(call.options.body),{type:'signup',email:'synthetic@example.test'});
  assert.equal(call.options.redirect,'error'); assert.equal(call.options.headers.Authorization,undefined);
});
