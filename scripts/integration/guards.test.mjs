// Offline safety tests. Passing these does NOT mean Supabase integration passed.
import test from 'node:test';
import assert from 'node:assert/strict';
import { validateTarget, privateRunPath, jwtClaims, validateRunTarget, Blocked } from './supabase.mjs';

const ref = 'abcdefghijklmnopqrst';
const homolog = { environment: 'homologation', projectRef: ref, url: `https://${ref}.supabase.co`, publishableKey: 'sb_publishable_synthetic_test_only', emailDomain: 'qa.client.example', emailPrefix: 'tch-qa' };
const allowed = { allowRemoteWrites: true, projectRef: ref };
const jwtKey = claims => ['header', Buffer.from(JSON.stringify(claims)).toString('base64url'), 'signature'].join('.');

test('homologation requires explicit matching project and write opt-in', () => {
  assert.throws(() => validateTarget(homolog), Blocked);
  assert.throws(() => validateTarget(homolog, { allowRemoteWrites: true, projectRef: 'wrong' }), Blocked);
  assert.equal(validateTarget(homolog, allowed).url, homolog.url);
});
test('offline validation does not authorize writes', () => {
  assert.equal(validateTarget(homolog, { validateOnly: true }).environment, 'homologation');
  assert.throws(() => validateTarget(homolog), Blocked);
});
test('production mode and legacy destination are rejected', () => {
  assert.throws(() => validateTarget({ ...homolog, environment: 'production' }, allowed), Blocked);
  assert.throws(() => validateTarget({ ...homolog, projectRef: 'wzgorvljvogicctpqyli', url: 'https://wzgorvljvogicctpqyli.supabase.co' }, { allowRemoteWrites: true, projectRef: 'wzgorvljvogicctpqyli' }), Blocked);
});
test('server keys and cross-project anon keys never pass', () => {
  for (const key of ['sb_secret_synthetic_only', jwtKey({ role: 'service_role', ref }), jwtKey({ role: 'anon', ref: 'wrong' }), 'invalid']) {
    assert.throws(() => validateTarget({ ...homolog, publishableKey: key }, allowed), Blocked);
  }
  assert.equal(validateTarget({ ...homolog, publishableKey: jwtKey({ role: 'anon', ref }) }, allowed).projectRef, ref);
});
test('origin cannot contain credentials, query, path, non-HTTPS or a different host', () => {
  for (const url of [`https://user:pass@${ref}.supabase.co`, `${homolog.url}/rest/v1`, `${homolog.url}?apikey=bad`, `http://${ref}.supabase.co`, 'https://other.example']) {
    assert.throws(() => validateTarget({ ...homolog, url }, allowed), Blocked);
  }
});
test('local mode only accepts loopback and explicit writes', () => {
  const local = { ...homolog, environment: 'local', projectRef: '', url: 'http://127.0.0.1:54321', emailDomain: 'example.test' };
  assert.throws(() => validateTarget(local), Blocked);
  assert.equal(validateTarget(local, { allowLocalWrites: true }).url, local.url);
  assert.throws(() => validateTarget({ ...local, url: 'http://192.168.1.2' }, { allowLocalWrites: true }), Blocked);
  assert.throws(() => validateTarget({ ...local, url: 'http://127.0.0.1.attacker.example' }, { allowLocalWrites: true }), Blocked);
});
test('homologation rejects test-only domain and unsafe alias prefix', () => {
  assert.throws(() => validateTarget({ ...homolog, emailDomain: 'example.test' }, allowed), Blocked);
  assert.throws(() => validateTarget({ ...homolog, emailPrefix: '../unsafe' }, allowed), Blocked);
});
test('credentials/results stay under ignored local integration directory', () => {
  assert(privateRunPath('.local/integration/run.json').endsWith('/.local/integration/run.json'));
  for (const path of ['docs/run.json', '.local/integration/../../run.json', '.local/integration/run.txt']) assert.throws(() => privateRunPath(path), Blocked);
});
test('JWT parser is only decoding, never a substitute for server verification', () => {
  assert.equal(jwtClaims(jwtKey({ sub: 'test-user', role: 'authenticated' })).sub, 'test-user');
  assert.throws(() => jwtClaims('invalid'));
});


test('partial signup resume preserves the same accounts and rejects a different target', () => {
  const run = { environment: homolog.environment, projectRef: ref, url: homolog.url,
    accounts: { consumerA: { userId: 'synthetic-user', password: 'synthetic-test-only' }, vendorA: { password: 'preserve-pending-password' } } };
  assert.strictEqual(validateRunTarget(run, homolog), run);
  assert.equal(run.accounts.vendorA.password, 'preserve-pending-password');
  assert.throws(() => validateRunTarget({ ...run, projectRef: 'wrong' }, homolog), Blocked);
  assert.throws(() => validateRunTarget({ ...run, url: 'https://other.example' }, homolog), Blocked);
  assert.throws(() => validateRunTarget({ ...run, environment: 'production' }, homolog), Blocked);
  assert.throws(() => validateRunTarget({ ...run, accounts: [] }, homolog), Blocked);
});
