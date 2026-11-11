import { createSign } from 'node:crypto';
export const QA_PROJECT_REF = 'vhnkezwjknwpddqhlnfk';
function endpoint(value, { allowLocalTest = false, projectRef = QA_PROJECT_REF } = {}) {
  const url = new URL(value);
  if (url.username || url.password || url.search || url.hash || url.pathname !== '/') throw new Error('Invalid API origin');
  const local = allowLocalTest && url.protocol === 'http:' && ['127.0.0.1', '[::1]'].includes(url.hostname);
  if (!local && !(url.protocol === 'https:' && url.hostname === `${projectRef}.supabase.co` && !url.port)) throw new Error('Unauthorized API origin');
  return url;
}
function checkedLocal(value) {
  const u = new URL(value);
  if (u.protocol !== 'http:' || !['127.0.0.1','[::1]'].includes(u.hostname) || u.username || u.password) throw new Error('Test transport must be loopback');
  return u;
}
async function json(response) {
  const text = await response.text();
  if (text.length > 65_536) throw new Error('Response too large');
  try { return JSON.parse(text); } catch { throw new Error('Invalid provider response'); }
}
export function createPushStore({ url, serviceKey, fetchImpl = fetch, allowLocalTest = false }) {
  const origin = endpoint(url, { allowLocalTest });
  if (!serviceKey || (!allowLocalTest && !serviceKey.startsWith('sb_secret_') && serviceKey.split('.').length !== 3)) throw new Error('Server API key required');
  if (!allowLocalTest && serviceKey.split('.').length === 3) {
    let payload; try { payload = JSON.parse(Buffer.from(serviceKey.split('.')[1], 'base64url')); } catch { throw new Error('Invalid server key'); }
    if (payload.role !== 'service_role') throw new Error('Service role required');
  }
  async function rpc(name, body) {
    const headers = { apikey: serviceKey, 'Content-Type': 'application/json' };
    // New sb_secret keys are NOT JWTs. Only legacy service_role JWTs go in Bearer.
    if (serviceKey.split('.').length === 3) headers.Authorization = `Bearer ${serviceKey}`;
    const response = await fetchImpl(new URL(`/rest/v1/rpc/${name}`, origin), { method:'POST', headers, body:JSON.stringify(body), redirect:'error', signal:AbortSignal.timeout(3000) });
    if (!response.ok) throw new Error('Database RPC failed');
    return json(response);
  }
  async function confirmed(name, body) { if (await rpc(name, body) !== true) throw new Error('Claim no longer owned'); }
  return {
    claim: jobId => rpc('claim_interest_push_job', { p_job_id: jobId }),
    binding: (owner, installation, job) => {
      if (!job || job.ownerId !== owner || job.installationId !== installation) throw new Error('Claim mismatch');
      return rpc('interest_push_job_binding', { p_job_id: job.id, p_claim_token: job.claimToken });
    },
    eligible: job => rpc('interest_push_job_eligible', { p_job_id:job.id, p_claim_token:job.claimToken }),
    finish: (job, result, providerId = null) => confirmed('finish_interest_push_job', { p_job_id:job.id,p_claim_token:job.claimToken,p_result:result,p_provider_message_id:providerId }),
    retry: (job, delay = null) => confirmed('retry_interest_push_job', { p_job_id:job.id,p_claim_token:job.claimToken,p_delay_seconds:delay }),
    invalidateIfVersion: (owner, installation, version) => rpc('invalidate_interest_push_device', { p_owner_id:owner,p_installation_id:installation,p_binding_version:version }),
  };
}
export function createFcmTransport({ projectId, serviceAccount, fetchImpl = fetch, now = Date.now, testEndpoints = null }) {
  if (!/^[a-z][a-z0-9-]{4,62}$/.test(projectId ?? '') || serviceAccount?.project_id !== projectId ||
      !/^[A-Za-z0-9._-]+@[A-Za-z0-9-]+\.iam\.gserviceaccount\.com$/.test(serviceAccount?.client_email ?? '') || !serviceAccount?.private_key?.includes('PRIVATE KEY')) throw new Error('Invalid FCM server configuration');
  const tokenUrl = testEndpoints ? checkedLocal(testEndpoints.tokenUrl) : new URL('https://oauth2.googleapis.com/token');
  const sendUrl = testEndpoints ? checkedLocal(testEndpoints.sendUrl) : new URL(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`);
  let access = null, expires = 0, pending = null;
  async function token() {
    if (access && expires > now()+60_000) return access;
    if (pending) return pending;
    pending = (async () => {
      const issued = Math.floor(now()/1000);
      const b64 = data => Buffer.from(JSON.stringify(data)).toString('base64url');
      const unsigned = `${b64({alg:'RS256',typ:'JWT'})}.${b64({iss:serviceAccount.client_email,scope:'https://www.googleapis.com/auth/firebase.messaging',aud:'https://oauth2.googleapis.com/token',iat:issued,exp:issued+3600})}`;
      const signature = createSign('RSA-SHA256').update(unsigned).sign(serviceAccount.private_key).toString('base64url');
      const response = await fetchImpl(tokenUrl, { method:'POST', redirect:'error', signal:AbortSignal.timeout(3000), headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:`${unsigned}.${signature}`}) });
      if (!response.ok) throw new Error('OAuth unavailable');
      const body = await json(response);
      if (typeof body.access_token !== 'string' || !Number.isFinite(body.expires_in) || body.expires_in <= 60 || body.expires_in > 3600) throw new Error('Invalid OAuth response');
      access = body.access_token; expires = now()+body.expires_in*1000; return access;
    })();
    try { return await pending; } finally { pending = null; }
  }
  return { async send(message) {
    for (let attempt=0; attempt<2; attempt++) {
      const response = await fetchImpl(sendUrl, { method:'POST', redirect:'error', signal:AbortSignal.timeout(4000), headers:{Authorization:`Bearer ${await token()}`,'Content-Type':'application/json'}, body:JSON.stringify({message}) });
      if (response.status === 401 && attempt === 0) { access=null;expires=0;continue; }
      const body = await json(response);
      if (response.ok) {
        if (typeof body.name !== 'string' || !body.name.startsWith(`projects/${projectId}/messages/`)) throw new Error('No provider confirmation');
        return {accepted:true,messageId:body.name};
      }
      const unregistered = body?.error?.details?.some(item => item['@type'] === 'type.googleapis.com/google.firebase.fcm.v1.FcmError' && item.errorCode === 'UNREGISTERED');
      if (unregistered) return {accepted:false,code:'UNREGISTERED',retryable:false};
      const raw = response.headers.get('retry-after');
      const retryAfterSeconds = raw && /^\d+$/.test(raw) ? Math.min(3600, Number(raw)) : raw ? Math.max(1,Math.min(3600,Math.ceil((Date.parse(raw)-now())/1000))) : null;
      return {accepted:false,retryable:response.status===429 || response.status>=500,code:'PROVIDER_ERROR',retryAfterSeconds:Number.isFinite(retryAfterSeconds)?retryAfterSeconds:null};
    }
    throw new Error('OAuth retry exhausted');
  }};
}
