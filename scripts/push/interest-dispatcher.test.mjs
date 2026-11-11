import test from 'node:test';
import assert from 'node:assert/strict';
import { registerDevice, logoutDevice, dispatchInterest } from './interest-dispatcher.mjs';
const id = n => `${String(n).padStart(8, '0')}-1111-4111-8111-111111111111`;
const owner = id(1), vendor = id(2), installation = id(3), jobId = id(4);
function fixture() {
  let binding = { ownerId: owner, vendorId: vendor, installationId: installation, version: 1, active: true, token: 'test-token-not-a-real-fcm-token' };
  let claimed = false; let eligible = true;
  const results = []; const sent = [];
  const job = { id: jobId, ownerId: owner, vendorId: vendor, installationId: installation, routeId: id(5), protocolId: id(6), createdAtMillis: 1000 };
  const store = {
    vendorForUser: async u => u === owner ? vendor : null,
    bind: async b => (binding = { ...b, version: binding.version + 1, active: true }),
    revokeForUser: async (u, d) => { if (u === binding.ownerId && d === installation) binding = { ...binding, active: false, version: binding.version + 1 }; },
    claim: async () => { if (claimed) return null; claimed = true; return job; },
    binding: async () => ({ ...binding }), eligible: async () => eligible,
    invalidateIfVersion: async (u, d, v) => { if (binding.version === v) binding.active = false; },
    finish: async (j, result) => { results.push(result); }, retry: async () => { results.push('retry'); },
  };
  const fcm = { send: async message => { sent.push(message); return { accepted: true }; } };
  return { store, fcm, sent, results, job, getBinding: () => binding, deny: () => { eligible = false; } };
}
test('standalone worker sends a claimed eligible job without screen or Realtime', async () => {
  const f = fixture(); assert.equal(await dispatchInterest(f.store, f.fcm, jobId, 2000), 'provider-accepted');
  assert.equal(f.sent.length, 1); assert.equal(f.sent[0].data.route_id, f.job.routeId);
  assert.equal(f.sent[0].notification, undefined);
  assert.deepEqual(Object.keys(f.sent[0].data).sort(), ['binding_version','expires_at_ms','issued_at_ms','owner_id','protocol_id','route_id','type']);
});
test('duplicate protocol job is not sent twice after durable claim', async () => {
  const f = fixture(); await dispatchInterest(f.store, f.fcm, jobId, 2000);
  assert.equal(await dispatchInterest(f.store, f.fcm, jobId, 2000), 'already-claimed'); assert.equal(f.sent.length, 1);
});
test('logout revokes the user/device binding before dispatch', async () => {
  const f = fixture(); await logoutDevice(f.store, { userId: owner }, installation);
  assert.equal(f.getBinding().active, false); assert.equal(await dispatchInterest(f.store, f.fcm, jobId, 2000), 'unbound'); assert.equal(f.sent.length, 0);
});
test('another user cannot revoke a vendor device', async () => {
  const f = fixture(); await logoutDevice(f.store, { userId: id(99) }, installation); assert.equal(f.getBinding().active, true);
});
test('registration resolves vendor from authenticated user; consumer is rejected', async () => {
  const f = fixture(); await assert.rejects(registerDevice(f.store, { userId: id(99) }, { installationId: installation, token: 'test-token-not-a-real-fcm-token' }));
  const b = await registerDevice(f.store, { userId: owner }, { installationId: installation, token: 'test-rotated-token-not-real' }); assert.equal(b.vendorId, vendor); assert.equal(b.version, 2);
});
test('expired or cancelled/ineligible interest is never sent', async () => {
  const old = fixture(); assert.equal(await dispatchInterest(old.store, old.fcm, jobId, 40000), 'expired'); assert.equal(old.sent.length, 0);
  const cancelled = fixture(); cancelled.deny(); assert.equal(await dispatchInterest(cancelled.store, cancelled.fcm, jobId, 2000), 'ineligible'); assert.equal(cancelled.sent.length, 0);
});
test('unregistered token is invalidated; rotation during send preserves newer token', async () => {
  const f = fixture(); f.fcm.send = async () => ({ code: 'UNREGISTERED' });
  assert.equal(await dispatchInterest(f.store, f.fcm, jobId, 2000), 'invalid-token'); assert.equal(f.getBinding().active, false);
  const rotated = fixture(); rotated.fcm.send = async () => { await registerDevice(rotated.store, { userId: owner }, { installationId: installation, token: 'new-test-token-never-sent-to-fcm' }); return { code: 'UNREGISTERED' }; };
  await dispatchInterest(rotated.store, rotated.fcm, jobId, 2000); assert.equal(rotated.getBinding().active, true); assert.equal(rotated.getBinding().version, 2);
});
test('transport failure requests retry and never reports provider acceptance', async () => {
  const f = fixture(); f.fcm.send = async () => { throw new Error('offline'); };
  assert.equal(await dispatchInterest(f.store, f.fcm, jobId, 2000), 'retry'); assert.deepEqual(f.results, ['retry']);
});
test('permanent provider rejection is terminal and never acknowledged as accepted', async () => {
  const f = fixture(); f.fcm.send = async () => ({ accepted:false,retryable:false,code:'PROVIDER_ERROR' });
  assert.equal(await dispatchInterest(f.store,f.fcm,jobId,2000),'failed');assert.deepEqual(f.results,['failed']);
});
test('provider retry-after reaches durable retry and remains a retry rather than success', async () => {
  const f = fixture();let backoff;f.store.retry=async(job,delay)=>{backoff=delay;};f.fcm.send=async()=>({accepted:false,retryable:true,retryAfterSeconds:25});
  assert.equal(await dispatchInterest(f.store,f.fcm,jobId,2000),'retry');assert.equal(backoff,25);assert.deepEqual(f.results,[]);
});
test('small clock skew never extends envelope TTL; excessive future event is dropped', async () => {
  const f = fixture();f.job.createdAtMillis=2050;assert.equal(await dispatchInterest(f.store,f.fcm,jobId,2000),'provider-accepted');assert.equal(Number(f.sent[0].data.expires_at_ms)-Number(f.sent[0].data.issued_at_ms),30000);
  const bad=fixture();bad.job.createdAtMillis=8000;assert.equal(await dispatchInterest(bad.store,bad.fcm,jobId,2000),'expired');assert.equal(bad.sent.length,0);
});
