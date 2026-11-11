import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { loadConfig } from './config.mjs';
import { SealedCookies, randomId, same } from './sealed-cookie.mjs';
import { SupabaseAuth, AuthFailure } from './supabase.mjs';
import { vendorOnboardingDraft } from './onboarding.mjs';
import { render } from './render.mjs';

const PUBLIC = fileURLToPath(new URL('../public/', import.meta.url));
const TOKEN_HASH = /^[A-Za-z0-9_-]{32,512}$/;
const TTL = 15 * 60 * 1000;
function headers(res, nonce) {
  res.setHeader('Cache-Control', 'no-store, private, max-age=0');
  res.setHeader('Pragma', 'no-cache');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('X-Robots-Tag', 'noindex, nofollow, noarchive');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  res.setHeader('Content-Security-Policy', `default-src 'none'; style-src 'self'; img-src 'self'; script-src 'nonce-${nonce}'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`);
}
async function formBody(req) {
  if (req.headers['content-type']?.split(';')[0] !== 'application/x-www-form-urlencoded') throw Error('Unsupported form');
  let body = '';
  for await (const chunk of req) { body += chunk; if (Buffer.byteLength(body) > 8192) throw Error('Oversize'); }
  const form = new URLSearchParams(body);
  if ([...form.keys()].some(k => form.getAll(k).length !== 1 || !['csrf', 'action', 'password', 'confirmation', 'email'].includes(k))) throw Error('Invalid form');
  return form;
}
export function createPortal(config, { auth = new SupabaseAuth(config), now = Date.now } = {}) {
  const cookies = new SealedCookies(config, now);
  const inFlight = new Set();
  const resendAttempts = new Map();
  // Brief, bounded duplicate-submit receipts; never stores hashes, tokens, passwords or user data.
  const completed = new Map();
  function remember(id, state) {
    for (const [key, value] of completed) if (value.expiresAt <= now()) completed.delete(key);
    if (completed.size >= 1000) completed.delete(completed.keys().next().value);
    completed.set(id, { state, expiresAt: now() + TTL });
  }
  const server = createServer(async (req, res) => {
    const nonce = randomId(); headers(res, nonce);
    if (config.secure) res.setHeader('Strict-Transport-Security', 'max-age=31536000');
    const html = (status, options) => { res.writeHead(status, { 'Content-Type': 'text/html; charset=utf-8' }); res.end(render({ nonce, ...options, inspection: config.inspection === true })); };
    const redirect = (path, flow, kind) => {
      if (flow) res.setHeader('Set-Cookie', cookies.header(flow, kind));
      res.writeHead(303, { Location: path }); res.end();
    };
    try {
      if (!req.url?.startsWith('/') || req.url.startsWith('//') || req.url.length > 4096) return html(400, { state: 'request_invalid' });
      const url = new URL(req.url, config.origin);
      if (req.method === 'GET' && ['/theme.css', '/logo.png', '/.well-known/assetlinks.json'].includes(url.pathname)) {
        if (url.search) return html(400, { state: 'request_invalid' });
        const path = PUBLIC + url.pathname.slice(1);
        const data = await readFile(path);
        res.writeHead(200, { 'Content-Type': url.pathname.endsWith('.json') ? 'application/json' : url.pathname.endsWith('.css') ? 'text/css; charset=utf-8' : 'image/png' });
        return res.end(data);
      }
      if (req.method === 'GET' && ['/', '/auth/open'].includes(url.pathname)) {
        if (url.search) return html(400, { state: 'request_invalid' });
        return html(200, { state: 'open' });
      }
      const kind = url.pathname === '/auth/confirm' ? 'confirm' : url.pathname === '/auth/recovery' ? 'recovery' : null;
      if (!kind) return html(404, { state: 'request_invalid' });
      if (config.inspection && req.method === 'POST') return html(503, { kind, state: 'inspection' });
      let flow = cookies.read(req.headers.cookie, kind);
      if (req.method === 'GET') {
        if (url.search) {
          const keys = [...url.searchParams.keys()];
          const tokenHash = url.searchParams.get('token_hash');
          const type = url.searchParams.get('type');
          const allowedType = kind === 'confirm' ? ['email'] : ['recovery'];
          if (keys.length !== 2 || keys.some(k => !['token_hash', 'type'].includes(k)) || !TOKEN_HASH.test(tokenHash || '') || !allowedType.includes(type)) return html(400, { kind, state: 'request_invalid' });
          const digest = cookies.receipt(tokenHash);
          // Known receipt proves reuse only in this browser; unknown failures remain ambiguous.
          if (kind === 'confirm' && ['confirmed', 'confirmed_logout_pending', 'already_used'].includes(flow?.state) && same(flow.digest, digest)) {
            return redirect(url.pathname, { ...flow, state: 'already_used' }, kind);
          }
          return redirect(url.pathname, { id: randomId(), state: 'pending', stage: 'verify', csrf: randomId(), tokenHash, type, digest, expiresAt: now() + TTL }, kind);
        }
        if (!flow || flow.state === 'invalid') {
          flow = { id: randomId(), state: flow?.state || 'email_request', stage: 'email_request', csrf: randomId(), expiresAt: now() + TTL };
          res.setHeader('Set-Cookie', cookies.header(flow, kind));
        }
        return html(200, { kind, state: flow?.state || 'missing', csrf: flow?.csrf, passwordReady: flow?.stage === 'password', emailRequest: flow?.stage === 'email_request' });
      }
      if (req.method !== 'POST') { res.setHeader('Allow', 'GET, POST'); return html(405, { kind, state: 'request_invalid' }); }
      if (url.search || req.headers.origin !== config.origin || req.headers['sec-fetch-site'] === 'cross-site') return html(403, { kind, state: 'request_invalid' });
      let form; try { form = await formBody(req); } catch { return html(400, { kind, state: 'request_invalid' }); }
      if (!flow || !same(form.get('csrf'), flow.csrf)) return html(403, { kind, state: 'request_invalid' });
      const prior = completed.get(flow.id);
      if (prior?.state === 'recovery_verified' && prior.expiresAt > now() && flow.stage === 'verify') return html(409, { kind, state: 'unsupported' });
      if (prior && prior.state !== 'recovery_verified' && prior.expiresAt > now()) return redirect(url.pathname, { id: flow.id, state: prior.state, expiresAt: now() + TTL, csrf: randomId(), ...(kind === 'confirm' ? { digest: flow.digest } : {}) }, kind);
      if (inFlight.has(flow.id)) return html(409, { kind, state: 'temporary', csrf: flow.csrf, canRetry: true, passwordReady: flow.stage === 'password', emailRequest: flow.stage === 'email_request' });
      inFlight.add(flow.id);
      try {
        if (form.get('action') === 'request' && flow.stage === 'email_request') {
          const email = (form.get('email') || '').trim();
          if (email.length > 254 || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return html(400, { kind, state: 'request_invalid', csrf: flow.csrf, emailRequest: true });
          if (kind === 'confirm') {
            const key = cookies.receipt('resend:' + email.toLowerCase());
            for (const [id, until] of resendAttempts) if (until <= now()) resendAttempts.delete(id);
            if ((resendAttempts.get(key) || 0) > now() || (flow.resendAfter || 0) > now()) return html(429, { kind, state: 'cooldown', csrf: flow.csrf, emailRequest: true });
            if (resendAttempts.size >= 1000) return html(429, { kind, state: 'cooldown', csrf: flow.csrf, emailRequest: true });
            resendAttempts.set(key, now() + 60000);
            await auth.requestConfirmation(email);
          } else await auth.requestRecovery(email);
          // Accepted request is not proof of account existence or email delivery.
          return redirect(url.pathname, { ...flow, state: kind === 'confirm' ? 'confirmation_requested' : 'recovery_requested', csrf: randomId(), ...(kind === 'confirm' ? { resendAfter: now() + 60000 } : {}) }, kind);
        }
        if (form.get('action') === 'verify' && flow.stage === 'verify' && TOKEN_HASH.test(flow.tokenHash || '')) {
          const result = await auth.verify(flow.tokenHash, flow.type);
          if (kind === 'confirm') {
            if (typeof result?.user?.id !== 'string' || !result.user.id || !Number.isFinite(Date.parse(result.user.email_confirmed_at || ''))) throw new AuthFailure('temporary');
            // Verification may create a transient server session. Revoke only that session,
            // then discard both tokens. Never import it into the browser's or Android's login.
            let state = 'confirmed';
            const onboarding = vendorOnboardingDraft(result.user);
            if (onboarding) {
              if (onboarding.needsCompletion || !result.access_token) {
                state = 'confirmed_onboarding_pending';
              } else {
                try {
                  await auth.completeVendorOnboarding(result.access_token, onboarding.params);
                  state = 'confirmed_onboarded';
                } catch {
                  // The email remains confirmed even when catalogue provisioning fails.
                  // Never claim business readiness without a confirmed RPC response.
                  state = 'confirmed_onboarding_pending';
                }
              }
            }
            if (typeof result.access_token === 'string' && result.access_token) {
              try {
                await auth.logoutTransient(result.access_token);
              } catch {
                if (state === 'confirmed' || state === 'confirmed_onboarded') state = 'confirmed_logout_pending';
              }
            }
            remember(flow.id, state);
            return redirect(url.pathname, { id: flow.id, state, digest: flow.digest, csrf: randomId(), expiresAt: now() + TTL }, kind);
          }
          if (!result?.user?.id || typeof result.access_token !== 'string' || !result.access_token || !Number.isFinite(result.expires_in) || result.expires_in <= 0) throw new AuthFailure('temporary');
          const expiresAt = Math.min(now() + 10 * 60 * 1000, now() + result.expires_in * 1000);
          remember(flow.id, 'recovery_verified');
          // Dedicated encrypted HttpOnly cookie; no refresh token or normal login session is stored.
          return redirect(url.pathname, { id: flow.id, stage: 'password', state: 'password_ready', csrf: randomId(), userId: result.user.id, accessToken: result.access_token, expiresAt }, kind);
        }
        if (kind === 'recovery' && form.get('action') === 'password' && flow.stage === 'password') {
          const password = form.get('password') || '';
          if (password.length < 8 || password.length > 128 || password !== form.get('confirmation')) return html(400, { kind, state: 'password_rejected', csrf: flow.csrf, passwordReady: true });
          await auth.updatePassword(flow.accessToken, password, flow.userId);
          let state = 'password_changed';
          try { await auth.logoutTransient(flow.accessToken); } catch { state = 'password_changed_logout_pending'; }
          remember(flow.id, state);
          return redirect(url.pathname, { id: flow.id, state, csrf: randomId(), expiresAt: now() + TTL }, kind);
        }
        return html(400, { kind, state: 'request_invalid' });
      } catch (error) {
        const state = error instanceof AuthFailure ? error.state : 'temporary';
        if (state === 'invalid') {
          return redirect(url.pathname, { id: flow.id, state, csrf: randomId(), expiresAt: now() + TTL }, kind);
        }
        return html(state === 'temporary' ? 503 : 400, { kind, state, csrf: flow.csrf, canRetry: true, passwordReady: flow.stage === 'password', emailRequest: flow.stage === 'email_request' });
      } finally { inFlight.delete(flow.id); }
    } catch {
      // No URL, cookie, request body, response body or raw exception enters stdout/stderr.
      if (!res.headersSent) html(503, { state: 'temporary' }); else res.end();
    }
  });
  server.maxConnections = 200;
  server.requestTimeout = 15000; server.headersTimeout = 10000; server.maxHeadersCount = 40;
  return server;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { const config = loadConfig(); createPortal(config).on('error', () => { console.error('Portal listener unavailable; no request or credential details printed.'); process.exitCode = 1; }).listen(config.port, config.host, () => console.log('TáChegando Auth portal ready. Request logging disabled.')); }
  catch { console.error('Portal configuration invalid; no credential values printed.'); process.exitCode = 1; }
}
