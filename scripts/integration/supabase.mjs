/** Real HTTP/WebSocket integration runner. No admin key, migrations, mocks or production mode. */
import { readFileSync, writeFileSync, mkdirSync, chmodSync } from 'node:fs';
import { resolve, dirname, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { randomBytes, randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const LEGACY = 'wzgorvljvogicctpqyli';
const ACCOUNTS = ['consumerA', 'consumerB', 'vendorA', 'vendorB'];
export class Blocked extends Error {}

export function validateTarget(config, options = {}) {
  let url;
  try { url = new URL(config.url); } catch { throw new Blocked('Configure a URL do ambiente autorizado.'); }
  if (url.username || url.password || url.search || url.hash || !['', '/'].includes(url.pathname)) {
    throw new Blocked('A URL deve conter somente origem, sem credenciais ou parâmetros.');
  }
  if (!['local', 'homologation'].includes(config.environment)) throw new Blocked('Produção não é um destino permitido.');
  if (url.hostname.toLowerCase().includes(LEGACY)) throw new Blocked('O projeto legado está bloqueado.');
  const key = config.publishableKey;
  if (typeof key !== 'string' || !key) throw new Blocked('Configure somente a chave pública.');
  if (key.startsWith('sb_secret_')) throw new Blocked('Chave de servidor proibida.');
  if (!key.startsWith('sb_publishable_')) {
    let claims;
    try { claims = JSON.parse(Buffer.from(key.split('.')[1], 'base64url').toString()); }
    catch { throw new Blocked('A chave deve ser publishable ou anon.'); }
    if (claims.role !== 'anon') throw new Blocked('Somente chave anon é permitida no formato JWT.');
    if (config.environment === 'homologation' && claims.ref !== config.projectRef) throw new Blocked('Chave anon pertence a outro projeto.');
  }
  if (config.environment === 'local') {
    if (!['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname) || !['http:', 'https:'].includes(url.protocol)) {
      throw new Blocked('Local aceita apenas loopback.');
    }
    if (!options.validateOnly && !options.allowLocalWrites) throw new Blocked('Use --allow-local-writes para dados sintéticos locais.');
  } else {
    if (!/^[a-z]{20}$/.test(config.projectRef || '') || url.origin !== `https://${config.projectRef}.supabase.co`) {
      throw new Blocked('Confirme o project ref e a URL HTTPS exata da homologação.');
    }
    if (!options.validateOnly && (!options.allowRemoteWrites || options.projectRef !== config.projectRef)) {
      throw new Blocked('Homologação exige --allow-remote-writes e --project-ref correspondente.');
    }
  }
  if (!/^[a-z0-9.-]+\.[a-z]{2,}$/i.test(config.emailDomain || '')) throw new Blocked('Informe o domínio de email controlado pela equipe de teste.');
  if (config.environment === 'homologation' && /(?:^|\.)test$/i.test(config.emailDomain)) {
    throw new Blocked('Homologação com confirmação exige um domínio real de email controlado.');
  }
  if (!/^[a-z0-9-]{1,30}$/.test(config.emailPrefix || 'tch-qa')) throw new Blocked('Prefixo de email inválido.');
  return Object.freeze({ ...config, url: url.origin });
}

export function validateRunTarget(run, target) {
  if (run.url !== target.url || run.environment !== target.environment || (run.projectRef || null) !== (target.projectRef || null)) {
    throw new Blocked('As contas pertencem a outro destino.');
  }
  if (!run.accounts || typeof run.accounts !== 'object' || Array.isArray(run.accounts)) throw new Blocked('Arquivo de contas inválido.');
  return run;
}

export function jwtClaims(token) {
  try { return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()); }
  catch { throw new Error('JWT retornado pelo servidor não é legível.'); }
}
export function privateRunPath(path) {
  const location = resolve(ROOT, path);
  const parent = resolve(ROOT, '.local/integration') + sep;
  if (!location.startsWith(parent) || !location.endsWith('.json')) throw new Blocked('Use arquivo de execução dentro de .local/integration.');
  return location;
}
function loadJson(path) {
  try { return JSON.parse(readFileSync(path, 'utf8')); }
  catch { throw new Blocked('Não foi possível ler o arquivo JSON de configuração/execução.'); }
}
function savePrivate(path, object) {
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
  writeFileSync(path, JSON.stringify(object, null, 2) + '\n', { mode: 0o600 });
  chmodSync(path, 0o600);
}

class Api {
  constructor(target, token = null) { this.target = target; this.token = token; }
  async request(method, path, body, expected = [200], options = {}) {
    assert(path.startsWith('/') && !path.startsWith('//'), 'Caminho de API inválido');
    const headers = { apikey: this.target.publishableKey, 'Content-Type': 'application/json' };
    if (this.token) headers.Authorization = `Bearer ${this.token}`;
    if (options.representation) headers.Prefer = 'return=representation';
    const response = await fetch(this.target.url + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'error', signal: AbortSignal.timeout(15_000)
    });
    const text = await response.text();
    let data;
    try { data = text ? JSON.parse(text) : null; } catch { throw new Error('Resposta da API não é JSON.'); }
    if (!expected.includes(response.status)) {
      if (data?.error_code === 'email_not_confirmed' || data?.code === 'email_not_confirmed') {
        throw new Blocked('Confirme os emails de teste antes de executar verify.');
      }
      // Never print request bodies, raw responses, passwords, tokens or key-bearing URLs.
      const error = new Error(`HTTP ${response.status} inesperado em ${method} ${path.split('?')[0]}`);
      error.httpStatus = response.status;
      const code = data?.error_code || data?.code;
      if (typeof code === 'string' && /^[a-z0-9_]{1,80}$/.test(code)) error.apiCode = code;
      throw error;
    }
    return data;
  }
  async rows(method, table, query = '', body, expected = [200]) {
    return this.request(method, `/rest/v1/${table}${query ? '?' + query : ''}`, body, expected, { representation: true });
  }
  rpc(name, args, expected = name === 'update_vendor_location' ? [200, 204] : [200]) { return this.request('POST', `/rest/v1/rpc/${name}`, args, expected); }
}

/** Requires a server confirmation that postgres_changes subscribed, then an actual event. */
export function observeChange(target, token, table, filter, match, mutate, timeoutMs = 20_000) {
  return new Promise((resolveResult, reject) => {
    const url = new URL(target.url + '/realtime/v1/websocket');
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    url.searchParams.set('apikey', target.publishableKey);
    url.searchParams.set('vsn', '1.0.0');
    const socket = new WebSocket(url);
    const topic = `realtime:tch-qa-${randomUUID()}`;
    let triggered = false, finished = false;
    const finish = error => {
      if (finished) return;
      finished = true;
      clearTimeout(timer); clearInterval(heartbeat);
      socket.close();
      if (error) reject(error); else resolveResult();
    };
    const timer = setTimeout(() => finish(new Error(`Realtime não confirmou evento em ${table}.`)), timeoutMs);
    const heartbeat = setInterval(() => {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ topic: 'phoenix', event: 'heartbeat', payload: {}, ref: randomUUID() }));
    }, 5_000);
    socket.addEventListener('open', () => socket.send(JSON.stringify({
      topic, event: 'phx_join', ref: '1', join_ref: '1',
      payload: { access_token: token, config: { broadcast: { ack: false, self: false }, presence: { enabled: false }, private: false,
        postgres_changes: [{ event: '*', schema: 'public', table, filter }] } }
    })));
    socket.addEventListener('message', async event => {
      let message;
      try { message = JSON.parse(String(event.data)); } catch { finish(new Error('Mensagem Realtime inválida.')); return; }
      if (message.event === 'phx_reply' && message.payload?.status === 'error') return finish(new Error('Realtime recusou a assinatura.'));
      if (message.event === 'system' && message.payload?.status === 'error') return finish(new Error('Realtime não pôde assinar postgres_changes.'));
      if (message.event === 'system' && message.payload?.extension === 'postgres_changes' && message.payload.status === 'ok' && !triggered) {
        triggered = true;
        try { await mutate(); } catch { finish(new Error(`Falhou a alteração de teste em ${table}.`)); }
      }
      if (message.event === 'postgres_changes' && message.payload?.data && match(message.payload.data)) finish();
    });
    socket.addEventListener('error', () => finish(new Error('Falha na conexão WebSocket Realtime.')));
    socket.addEventListener('close', () => { if (!finished) finish(new Error('Realtime encerrou antes de confirmar o evento.')); });
  });
}

export async function runVerify(target, run, step) {
  const sessions = {};
  for (const label of ACCOUNTS) {
    await step(`login-jwt.${label}`, async () => {
      const account = run.accounts[label];
      const api = new Api(target);
      const session = await api.request('POST', '/auth/v1/token?grant_type=password', { email: account.email, password: account.password });
      assert(session.access_token && session.refresh_token, 'Login não retornou sessão real.');
      const claims = jwtClaims(session.access_token);
      assert.equal(claims.role, 'authenticated'); assert.equal(claims.sub, account.userId);
      const client = new Api(target, session.access_token);
      const user = await client.request('GET', '/auth/v1/user');
      assert.equal(user.id, account.userId);
      sessions[label] = { ...session, client };
    });
  }
  const cA = sessions.consumerA.client, cB = sessions.consumerB.client;
  const vA = sessions.vendorA.client, vB = sessions.vendorB.client;
  const ids = {};
  const at = (field, value) => `${field}=eq.${encodeURIComponent(value)}`;
  await step('jwt-rls.private-profiles', async () => {
    for (const label of ACCOUNTS) {
      const rows = await sessions[label].client.rows('GET', 'profiles');
      assert.equal(rows.length, 1); assert.equal(rows[0].id, run.accounts[label].userId);
    }
    assert.deepEqual(await cA.rows('GET', 'profiles', at('id', run.accounts.consumerB.userId)), []);
    const anonymous = new Api(target);
    await anonymous.rows('GET', 'profiles', '', undefined, [401, 403]);
    const bad = sessions.consumerA.access_token;
    const tampered = bad.slice(0, bad.lastIndexOf('.') + 1) + 'invalid-signature';
    await new Api(target, tampered).rows('GET', 'profiles', '', undefined, [401, 403]);
  });
  await step('vendor.identity-and-online', async () => {
    for (const label of ['vendorA', 'vendorB']) {
      const api = sessions[label].client;
      const rows = await api.rows('GET', 'vendor_catalog', at('user_id', run.accounts[label].userId));
      assert.equal(rows.length, 1); assert.notEqual(rows[0].id, run.accounts[label].userId);
      ids[label] = rows[0].id;
      const updated = await api.rows('PATCH', 'vendors', at('id', ids[label]), { is_online: true });
      assert.equal(updated.length, 1);
    }
    assert.equal((await cA.rows('GET', 'vendor_catalog', at('id', ids.vendorA))).length, 1);
  });
  await step('product.create-read-update-and-rls', async () => {
    const created = await vA.rows('POST', 'products', '', { vendor_id: ids.vendorA, name: `QA ${run.runId}`, price: '12.50' }, [201]);
    assert.equal(created.length, 1); ids.product = created[0].id;
    assert.equal(Number(created[0].price), 12.50);
    const updated = await vA.rows('PATCH', 'products', at('id', ids.product), { name: `QA edit ${run.runId}`, price: '13.75' });
    assert.equal(Number(updated[0].price), 13.75);
    assert.equal((await vA.rows('GET', 'products', at('id', ids.product))).length, 1);
    assert.deepEqual(await vB.rows('PATCH', 'products', at('id', ids.product), { name: 'Forbidden' }), []);
    await cB.rows('POST', 'products', '', { vendor_id: ids.vendorA, name: 'Forbidden', price: '1.00' }, [401, 403]);
    const catalog = await cA.rows('GET', 'vendor_catalog', at('id', ids.vendorA));
    assert(catalog[0].products.some(p => p.id === ids.product && p.price === '13.75'));
  });
  await step('location-and-rls', async () => {
    await vA.rpc('update_vendor_location', { p_latitude: -23.5505, p_longitude: -46.6333 });
    const [row] = await cA.rows('GET', 'vendor_catalog', at('id', ids.vendorA));
    assert(Math.abs(row.latitude + 23.5505) < 0.00001 && Math.abs(row.longitude + 46.6333) < 0.00001);
    await cB.rpc('update_vendor_location', { p_latitude: 0, p_longitude: 0 }, [401, 403]);
  });
  await step('route.save-reload-and-rls', async () => {
    const buffer = await vA.rpc('create_route_buffer', { points: [[-46.6333, -23.5505], [-46.6323, -23.5505]], tolerance_meters: 150 });
    assert(Array.isArray(buffer) && buffer[0].length >= 4);
    const [created] = await vA.rows('POST', 'vendor_routes', '', {
      vendor_id: ids.vendorA, name: `QA ${run.runId}`,
      waypoints: [{ latitude: -23.5505, longitude: -46.6333, orderIndex: 0 }, { latitude: -23.5505, longitude: -46.6323, orderIndex: 1 }],
      active_days: ['monday', 'saturday'], deviation_tolerance_meters: 150, status: 'active'
    }, [201]);
    ids.route = created.id;
    const fresh = new Api(target, sessions.vendorA.access_token);
    const [loaded] = await fresh.rows('GET', 'route_details', at('id', ids.route));
    assert.equal(loaded.status, 'active'); assert.deepEqual(loaded.active_days, ['monday', 'saturday']); assert(loaded.polygon[0].length >= 4);
    assert.deepEqual(await vB.rows('GET', 'route_details', at('id', ids.route)), []);
    assert.deepEqual(await cA.rows('GET', 'route_details', at('id', ids.route)), []);
    await vB.rpc('get_intents_for_route', { p_route_id: ids.route }, [401, 403]);
  });
  await step('purchase-intent.idempotency-and-rls', async () => {
    const params = { p_vendor_id: ids.vendorA, p_latitude: -23.5505, p_longitude: -46.6333 };
    ids.intent = await cA.rpc('create_purchase_intent', params);
    assert.equal(await cA.rpc('create_purchase_intent', params), ids.intent);
    assert((await Promise.all([cA.rpc('create_purchase_intent', params), cA.rpc('create_purchase_intent', params)])).every(id => id === ids.intent));
    assert.equal((await cA.rows('GET', 'purchase_intents', at('id', ids.intent)))[0].route_id, ids.route);
    assert.equal((await cA.rows('GET', 'purchase_intents', at('id', ids.intent))).length, 1);
    assert.deepEqual(await cB.rows('GET', 'purchase_intents', at('id', ids.intent)), []);
    assert.deepEqual(await vB.rows('GET', 'purchase_intents', at('id', ids.intent)), []);
    assert((await vA.rpc('get_intents_for_route', { p_route_id: ids.route })).some(i => i.id === ids.intent));
  });
  await step('attendance.snapshot-owner-only', async () => {
    const snapshot = await vA.rpc('get_route_attendance', { p_route_id: ids.route });
    assert.equal(snapshot.route_id, ids.route); assert.equal(snapshot.vendor_id, ids.vendorA);
    assert(snapshot.active && snapshot.online && snapshot.intents.some(i => i.id === ids.intent));
    await cA.rpc('get_route_attendance', { p_route_id: ids.route }, [401, 403]);
    await vB.rpc('get_route_attendance', { p_route_id: ids.route }, [401, 403]);
  });
  await step('attendance.cancel-realtime-and-new-protocol', async () => {
    await cB.rpc('cancel_purchase_intent', { p_intent_id: ids.intent }, [401, 403]);
    await observeChange(target, sessions.vendorA.access_token, 'purchase_intents', at('vendor_id', ids.vendorA),
      data => data.type === 'UPDATE' && data.record?.id === ids.intent && data.record.status === 'cancelled',
      () => cA.rpc('cancel_purchase_intent', { p_intent_id: ids.intent }));
    assert.equal(await cA.rpc('cancel_purchase_intent', { p_intent_id: ids.intent }), ids.intent);
    assert((await vA.rpc('get_route_attendance', { p_route_id: ids.route })).intents.some(i => i.id === ids.intent && i.status === 'cancelled'));
    const old = ids.intent;
    ids.intent = await cA.rpc('create_purchase_intent', { p_vendor_id: ids.vendorA, p_latitude: -23.5505, p_longitude: -46.6333 });
    assert.notEqual(ids.intent, old);
  });
  await step('attendance.pause-resume-and-discovery', async () => {
    await vB.rpc('set_route_availability', { p_route_id: ids.route, p_online: false }, [401, 403]);
    assert.equal(await vA.rpc('set_route_availability', { p_route_id: ids.route, p_online: false }), ids.route);
    const paused = await vA.rpc('get_route_attendance', { p_route_id: ids.route });
    assert(paused.active && !paused.online);
    assert.deepEqual(await cA.rows('GET', 'vendor_catalog', at('id', ids.vendorA)), []);
    await cB.rpc('create_purchase_intent', { p_vendor_id: ids.vendorA, p_latitude: -23.5505, p_longitude: -46.6333 }, [401, 403]);
    await vA.rpc('set_route_availability', { p_route_id: ids.route, p_online: true });
    const found = await cA.rpc('search_vendor_catalog', { p_query: `QA edit ${run.runId}` });
    assert(found.some(v => v.id === ids.vendorA && v.products.some(p => p.id === ids.product)));
  });
  await step('realtime.vendor-update', () => observeChange(target, sessions.consumerA.access_token, 'vendors', at('id', ids.vendorA),
    data => data.type === 'UPDATE' && data.record?.id === ids.vendorA,
    () => vA.rows('PATCH', 'vendors', at('id', ids.vendorA), { description: `Realtime ${run.runId}` })));
  await step('realtime.product-update', async () => {
    await observeChange(target, sessions.consumerA.access_token, 'products', at('id', ids.product),
      data => data.type === 'UPDATE' && data.record?.id === ids.product && Number(data.record.price) === 14,
      () => vA.rows('PATCH', 'products', at('id', ids.product), { price: '14.00' }));
    assert((await cA.rows('GET', 'vendor_catalog', at('id', ids.vendorA)))[0].products.some(p => p.id === ids.product && p.price === '14.00'));
  });
  await step('realtime.location-update', () => observeChange(target, sessions.consumerA.access_token, 'vendor_locations', at('vendor_id', ids.vendorA),
    data => data.type === 'UPDATE' && data.record?.vendor_id === ids.vendorA,
    () => vA.rpc('update_vendor_location', { p_latitude: -23.5506, p_longitude: -46.6333 })));
  await step('route.end-and-reload', async () => {
    assert.deepEqual(await vB.rows('PATCH', 'vendor_routes', at('id', ids.route), { status: 'ended' }), []);
    assert.equal(await vA.rpc('set_route_availability', { p_route_id: ids.route, p_online: false, p_end: true }), ids.route);
    assert.equal((await cA.rows('GET', 'purchase_intents', at('id', ids.intent)))[0].status, 'expired');
    const ended = await vA.rpc('get_route_attendance', { p_route_id: ids.route });
    assert(!ended.active && !ended.online && ended.intents.length === 0);
    assert.equal((await vA.rows('GET', 'route_details', at('id', ids.route)))[0].status, 'ended');
    assert.deepEqual(await vA.rpc('get_intents_for_route', { p_route_id: ids.route }), []);
  });
  await step('product.delete', async () => {
    const rows = await vA.rows('DELETE', 'products', at('id', ids.product));
    assert.equal(rows.length, 1); assert.deepEqual(await vA.rows('GET', 'products', at('id', ids.product)), []);
  });
  await step('catalog.offline-hides-location', async () => {
    for (const label of ['vendorA', 'vendorB']) await sessions[label].client.rows('PATCH', 'vendors', at('id', ids[label]), { is_online: false });
    assert.deepEqual(await cA.rows('GET', 'vendor_catalog', at('id', ids.vendorA)), []);
    assert.deepEqual(await cA.rows('GET', 'vendor_locations', at('vendor_id', ids.vendorA)), []);
  });
  for (const label of ACCOUNTS) await step(`logout-refresh-revoked.${label}`, async () => {
    const session = sessions[label];
    await session.client.request('POST', '/auth/v1/logout?scope=local', undefined, [204]);
    const revoked = await new Api(target).request('POST', '/auth/v1/token?grant_type=refresh_token', { refresh_token: session.refresh_token }, [400, 401, 403]);
    assert(['refresh_token_not_found', 'refresh_token_already_used', 'session_not_found'].includes(revoked?.error_code || revoked?.code), 'Auth não confirmou a revogação do refresh token.');
    // Access JWTs may remain valid until expiry. Do not claim immediate revocation by RLS.
  });
}

function parseArgs(args) {
  const options = {};
  for (let i = 0; i < args.length; i++) {
    const key = args[i];
    if (['--allow-local-writes', '--allow-remote-writes', '--help'].includes(key)) options[key.slice(2)] = true;
    else if (['--config', '--phase', '--run', '--project-ref'].includes(key) && args[i + 1] && !args[i + 1].startsWith('--')) options[key.slice(2)] = args[++i];
    else throw new Blocked('Argumentos inválidos; consulte --help.');
  }
  return options;
}
async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log('Node 22+; nenhuma dependência npm.\n--config .local/integration-target.json --phase validate|signup|verify\nEscrita local: --allow-local-writes\nEscrita homologação: --allow-remote-writes --project-ref REF\nVerify: --run .local/integration/RUN.json\nSignup cria quatro contas sintéticas e salva senhas somente em arquivo privado. Confirme emails antes de verify.');
    return;
  }
  if (!args.config || !['validate', 'signup', 'verify'].includes(args.phase)) throw new Blocked('Informe --config e --phase.');
  const target = validateTarget(loadJson(resolve(args.config)), { validateOnly: args.phase === 'validate', allowLocalWrites: args['allow-local-writes'], allowRemoteWrites: args['allow-remote-writes'], projectRef: args['project-ref'] });
  if (args.phase === 'validate') { console.log('PASS: configuração e destino validados offline; nenhum teste de integração executado.'); return; }
  const startedAt = new Date().toISOString();
  const results = [];
  const step = async (name, operation) => {
    console.log(`RUN: ${name}`);
    try { await operation(); results.push({ name, status: 'passed' }); console.log(`PASS: ${name}`); }
    catch (error) {
      const evidence = { name, status: error instanceof Blocked ? 'blocked' : 'failed' };
      if (Number.isInteger(error.httpStatus)) evidence.httpStatus = error.httpStatus;
      if (error.apiCode) evidence.apiCode = error.apiCode;
      results.push(evidence); throw error;
    }
  };
  let run, path;
  if (args.phase === 'signup') {
    if (args.run) {
      path = privateRunPath(args.run);
      run = validateRunTarget(loadJson(path), target);
      const previous = loadJson(path.replace(/\.json$/, '.results.json'));
      if (previous.phase !== 'signup' || previous.runId !== run.runId) throw new Blocked('Somente cadastro parcial pode ser retomado.');
      results.push(...previous.results);
    } else {
      const runId = Date.now().toString(36) + '-' + randomBytes(4).toString('hex');
      path = privateRunPath(`.local/integration/${runId}.json`);
      run = { runId, environment: target.environment, url: target.url, projectRef: target.projectRef || null, accounts: {} };
      savePrivate(path, run);
    }
    const runId = run.runId;
    try {
      for (const label of ACCOUNTS) {
        if (run.accounts[label]?.userId) { console.log(`SKIP: signup.${label} já aceito no mesmo RUN; conta preservada.`); continue; }
        await step(`signup.${label}`, async () => {
          const account = run.accounts[label] || { email: `${target.emailPrefix || 'tch-qa'}+${runId}-${label.toLowerCase()}@${target.emailDomain}`, password: 'Qa1!' + randomBytes(24).toString('base64url') };
          run.accounts[label] = account; savePrivate(path, run);
          const data = await new Api(target).request('POST', '/auth/v1/signup', { ...account, data: { name: `QA ${label}`, role: label.startsWith('vendor') ? 'vendor' : 'consumer', business_name: `QA ${label} ${runId}` } }, [200, 201]);
          const user = data.user || data;
          assert(user.id, 'Cadastro não retornou identidade.');
          account.userId = user.id; account.confirmationRequired = !data.access_token;
          savePrivate(path, run);
        });
      }
      console.log(`Contas em arquivo privado: .local/integration/${run.runId}.json`);
      console.log('Cadastro aceito pelo Auth; confirme os emails quando exigido e execute verify. A suíte completa ainda não foi aprovada.');
    } finally { savePrivate(path.replace(/\.json$/, '.results.json'), { phase: 'signup', runId: run.runId, environment: target.environment, projectRef: target.projectRef || null, startedAt, finishedAt: new Date().toISOString(), results, completeIntegration: false }); }
  } else {
    if (!args.run) throw new Blocked('Verify exige --run com as contas sintéticas criadas por signup.');
    path = privateRunPath(args.run); run = loadJson(path);
    validateRunTarget(run, target);
    for (const label of ACCOUNTS) if (!run.accounts?.[label]?.userId || !run.accounts[label].password) throw new Blocked('O cadastro das quatro contas está incompleto.');
    let complete = false;
    try { await runVerify(target, run, step); complete = true; console.log('PASS: integração HTTP/JWT/RLS e eventos Realtime reais.'); }
    finally { savePrivate(path.replace(/\.json$/, '.results.json'), { phase: 'verify', runId: run.runId, environment: target.environment, projectRef: target.projectRef || null, startedAt, finishedAt: new Date().toISOString(), results, completeIntegration: complete }); }
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(error => {
    console.error(error instanceof Blocked ? `BLOCKED: ${error.message}` : 'FAIL: integração não aprovada; verifique o último passo e os logs do servidor. Respostas/segredos não são impressos.');
    process.exitCode = error instanceof Blocked ? 2 : 1;
  });
}
