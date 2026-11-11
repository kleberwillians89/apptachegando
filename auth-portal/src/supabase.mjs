// Official Supabase Auth HTTP endpoints. Never logs response bodies, request URLs or credentials.
export class AuthFailure extends Error {
  constructor(state) { super('Auth operation not completed'); this.state = state; }
}
export class SupabaseAuth {
  constructor(config, fetcher = fetch) { this.config = config; this.fetcher = fetcher; }
  async request(path, body, token, method = 'POST') {
    let response;
    try {
      response = await this.fetcher(this.config.supabaseUrl + '/auth/v1/' + path, {
        method, headers: { apikey: this.config.publishableKey, 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: JSON.stringify(body), redirect: 'error', signal: AbortSignal.timeout(12000)
      });
    } catch { throw new AuthFailure('temporary'); }
    let data; try { data = await response.json(); } catch { data = null; }
    if (!response.ok) {
      if (response.status === 429 || response.status >= 500) throw new AuthFailure('temporary');
      if (data?.code === 'weak_password' || data?.code === 'same_password') throw new AuthFailure('password_rejected');
      // Auth often returns otp_expired for both expired and consumed links. Never guess which.
      throw new AuthFailure('invalid');
    }
    return data;
  }
  async requestRecovery(email) {
    // Fixed redirect, never controlled by a form field or query parameter.
    try {
      const result = await this.request('recover?redirect_to=' + encodeURIComponent(this.config.origin + '/auth/recovery'), { email });
      if (!result || typeof result !== 'object') throw new AuthFailure('temporary');
    }
    catch { throw new AuthFailure('temporary'); }
  }
  async requestConfirmation(email) {
    try {
      const result = await this.request('resend?redirect_to=' + encodeURIComponent(this.config.origin + '/auth/confirm'), { type: 'signup', email });
      if (!result || typeof result !== 'object') throw new AuthFailure('temporary');
    } catch { throw new AuthFailure('temporary'); }
  }
  verify(tokenHash, type) { return this.request('verify', { token_hash: tokenHash, type }); }
  async updatePassword(token, password, userId) {
    const user = await this.request('user', { password }, token, 'PUT');
    if (!user?.id || user.id !== userId) throw new AuthFailure('temporary');
  }
  async completeVendorOnboarding(token, params) {
    // The verified session token is short-lived and never leaves this server.
    // No service-role credential. PostgREST invokes the strictly authenticated RPC.
    let response;
    try {
      response = await this.fetcher(this.config.supabaseUrl + '/rest/v1/rpc/complete_vendor_onboarding', {
        method: 'POST',
        headers: {
          apikey: this.config.publishableKey,
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          Accept: 'application/json'
        },
        body: JSON.stringify(params),
        redirect: 'error',
        signal: AbortSignal.timeout(12000)
      });
    } catch { throw new AuthFailure('temporary'); }
    if (!response.ok) throw new AuthFailure('temporary');
    let data;
    try { data = await response.json(); } catch { throw new AuthFailure('temporary'); }
    if (!data?.profile?.vendor_id || !Array.isArray(data.products)) throw new AuthFailure('temporary');
    return data;
  }
  async logoutTransient(token) {
    // Scope local: never revoke unrelated browser or Android sessions through a global logout.
    await this.request('logout?scope=local', undefined, token);
  }
}
