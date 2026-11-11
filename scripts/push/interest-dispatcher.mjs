/** Server-only core. No HTTP entry point or deployment; adapters are in adapters.mjs.
 * Production store MUST use verified Auth context, durable atomic claims, and authoritative
 * route/GPS eligibility. FCM adapter MUST send HTTP v1 data-only messages, never notification payloads.
 * Unit tests inject fakes; this module does not enable real push by itself.
 */
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function identity(value) { if (!uuid.test(value ?? '')) throw new Error('Invalid identity'); return value; }
export async function registerDevice(store, actor, { installationId, token }) {
  const ownerId = identity(actor?.userId); identity(installationId);
  if (typeof token !== 'string' || token.length < 20 || token.length > 4096) throw new Error('Invalid token');
  const vendorId = await store.vendorForUser(ownerId);
  if (!vendorId) throw new Error('Vendor authentication required');
  // Adapter must resolve owner from verified JWT, never request body/user_metadata.
  return store.bind({ ownerId, vendorId, installationId, token });
}
export async function logoutDevice(store, actor, installationId) {
  return store.revokeForUser(identity(actor?.userId), identity(installationId));
}
export async function dispatchInterest(store, fcm, jobId = null, nowMillis = Date.now()) {
  // Atomic durable claim: unique(protocol_id, installation_id); includes lease/retry semantics.
  const job = await store.claim(jobId === null ? null : identity(jobId));
  if (!job) return 'already-claimed';
  for (const key of ['ownerId', 'vendorId', 'routeId', 'protocolId', 'installationId']) identity(job[key]);
  if (!Number.isSafeInteger(job.createdAtMillis) || job.createdAtMillis < 0 || job.createdAtMillis > nowMillis + 5000 || nowMillis - job.createdAtMillis > 30_000) {
    await store.finish(job, 'expired'); return 'expired';
  }
  const binding = await store.binding(job.ownerId, job.installationId, job);
  if (!binding?.active || binding.ownerId !== job.ownerId || binding.vendorId !== job.vendorId) {
    await store.finish(job, 'unbound'); return 'unbound';
  }
  // Recheck cancellation, availability, route ownership, fresh GPS, ahead-of-vendor geometry
  // and unambiguous point in the database. Never trust an event's eligible boolean.
  if (!await store.eligible(job)) { await store.finish(job, 'ineligible'); return 'ineligible'; }
  const data = {
    type: 'route_interest', owner_id: job.ownerId, route_id: job.routeId,
    protocol_id: job.protocolId, binding_version: String(binding.version),
    issued_at_ms: String(nowMillis), expires_at_ms: String(Math.min(job.createdAtMillis + 30_000,nowMillis + 30_000)),
  };
  // Data only: Android receiver must validate current binding/owner/expiry and persistent
  // protocol ledger before posting generic text. This blocks post-logout OS auto-display.
  let retryAfter = null;
  try {
    const response = await fcm.send({ token: binding.token, data, android: { priority: 'HIGH', ttl: '30s' } });
    if (response.code === 'UNREGISTERED') {
      const invalidated = await store.invalidateIfVersion(job.ownerId, job.installationId, binding.version);
      if (invalidated === false) { await store.retry(job,1); return 'retry'; }
      await store.finish(job, 'invalid-token'); return 'invalid-token';
    }
    if (response.retryable === false) { await store.finish(job, 'failed'); return 'failed'; }
    retryAfter = response.retryAfterSeconds ?? null;
    if (!response.accepted) throw new Error('Push provider did not accept message');
    // Provider acceptance is not proof of delivery/visibility. Client dedupe remains required
    // if acceptance succeeds but acknowledgement fails or the lease is retried.
    await store.finish(job, 'provider-accepted', response.messageId ?? null); return 'provider-accepted';
  } catch {
    await store.retry(job,retryAfter); return 'retry';
  }
}
