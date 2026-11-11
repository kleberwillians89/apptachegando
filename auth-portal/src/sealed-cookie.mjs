import { randomBytes, createCipheriv, createDecipheriv, createHmac, timingSafeEqual } from 'node:crypto';

export const COOKIE_NAMES = { confirm: 'tch_confirmation', recovery: 'tch_recovery' };
export const randomId = () => randomBytes(24).toString('base64url');
export const same = (a, b) => {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const left = Buffer.from(a), right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
};
export class SealedCookies {
  constructor(config, now = Date.now) { this.config = config; this.now = now; }
  receipt(tokenHash) { return createHmac('sha256', this.config.cookieKey).update('confirmation-receipt:' + tokenHash).digest('hex'); }
  seal(value, purpose) {
    const iv = randomBytes(12), cipher = createCipheriv('aes-256-gcm', this.config.cookieKey, iv);
    cipher.setAAD(Buffer.from(purpose + ':' + this.config.origin));
    const ciphertext = Buffer.concat([cipher.update(JSON.stringify(value)), cipher.final()]);
    const encoded = Buffer.concat([iv, cipher.getAuthTag(), ciphertext]).toString('base64url');
    if (encoded.length > 3600) throw Error('Cookie excede limite.');
    return encoded;
  }
  open(encoded, purpose) {
    try {
      if (!encoded || encoded.length > 3600) return null;
      const b = Buffer.from(encoded, 'base64url');
      const cipher = createDecipheriv('aes-256-gcm', this.config.cookieKey, b.subarray(0, 12));
      cipher.setAAD(Buffer.from(purpose + ':' + this.config.origin)); cipher.setAuthTag(b.subarray(12, 28));
      const value = JSON.parse(Buffer.concat([cipher.update(b.subarray(28)), cipher.final()]).toString());
      return Number.isFinite(value.expiresAt) && value.expiresAt > this.now() ? value : null;
    } catch { return null; }
  }
  read(header, purpose) {
    const name = COOKIE_NAMES[purpose];
    const values = (header || '').split(';').map(x => x.trim()).filter(x => x.startsWith(name + '='));
    return values.length === 1 ? this.open(values[0].slice(name.length + 1), purpose) : null;
  }
  header(value, purpose) {
    const maxAge = Math.max(0, Math.floor((value.expiresAt - this.now()) / 1000));
    return `${COOKIE_NAMES[purpose]}=${this.seal(value, purpose)}; HttpOnly; SameSite=Lax; Path=/auth/${purpose}; Max-Age=${maxAge}${this.config.secure ? '; Secure' : ''}`;
  }
}
