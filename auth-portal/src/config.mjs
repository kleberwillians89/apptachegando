export const PROJECT = 'vhnkezwjknwpddqhlnfk';
export const ORIGIN = 'https://app.tachegandoapp.com.br';
export function loadConfig(env = process.env) {
  const origin = new URL(env.APP_ORIGIN || ORIGIN);
  const local = ['127.0.0.1', 'localhost', '[::1]'].includes(origin.hostname);
  if (local && (env.NODE_ENV === 'production' || (env.HOST && !['127.0.0.1', 'localhost', '::1'].includes(env.HOST)))) throw Error('Loopback somente para desenvolvimento local.');
  if (origin.origin !== ORIGIN && !(local && origin.protocol === 'http:')) throw Error('APP_ORIGIN inválida.');
  if (origin.pathname !== '/' || origin.search || origin.hash || origin.username || origin.password) throw Error('APP_ORIGIN deve ser somente uma origem.');
  const url = env.SUPABASE_URL;
  if (url !== `https://${PROJECT}.supabase.co` && !(local && /^http:\/\/127\.0\.0\.1:\d+$/.test(url || ''))) throw Error('Destino Supabase não autorizado.');
  const key = env.SUPABASE_PUBLISHABLE_KEY || '';
  if (!key.startsWith('sb_publishable_')) throw Error('Configure somente publishable key.');
  const cookieKey = Buffer.from(env.COOKIE_ENCRYPTION_KEY || '', 'base64');
  if (cookieKey.length !== 32) throw Error('Configure COOKIE_ENCRYPTION_KEY de 32 bytes.');
  const port = Number(env.PORT || 3000);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw Error('Porta inválida.');
  return { origin: origin.origin, secure: !local, supabaseUrl: url, publishableKey: key, cookieKey, host: env.HOST || '127.0.0.1', port };
}
