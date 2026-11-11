#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { createPushStore, createFcmTransport, QA_PROJECT_REF } from './adapters.mjs';
import { dispatchInterest } from './interest-dispatcher.mjs';
export async function runWorker({store,fcm,once=false,signal,pollMillis=1000,onResult=()=>{}}) {
  if (pollMillis<250 || pollMillis>60_000) throw new Error('Invalid poll interval');
  do {
    if (signal?.aborted) break;
    try { const result = await dispatchInterest(store,fcm,null); onResult(result); if (once) return result; }
    catch { onResult('worker-error'); if (once) throw new Error('Worker iteration failed'); }
    try { await delay(pollMillis,undefined,{signal}); } catch { if (signal?.aborted) break; throw new Error('Worker wait failed'); }
  } while (!signal?.aborted);
}
export async function main(env=process.env,args=process.argv.slice(2)) {
  if (env.TCH_PUSH_EXECUTE !== '1' || env.TCH_PUSH_PROJECT_REF !== QA_PROJECT_REF || !env.SUPABASE_URL || !env.SUPABASE_SERVER_KEY || !env.FCM_SERVICE_ACCOUNT_FILE || !env.FCM_PROJECT_ID) throw new Error('Worker disabled or missing authorized QA configuration');
  if (args.some(a=>a!=='--once')) throw new Error('Unknown worker argument');
  const store=createPushStore({url:env.SUPABASE_URL,serviceKey:env.SUPABASE_SERVER_KEY});
  const serviceAccount=JSON.parse(await readFile(env.FCM_SERVICE_ACCOUNT_FILE,'utf8'));
  const fcm=createFcmTransport({projectId:env.FCM_PROJECT_ID,serviceAccount});
  const abort=new AbortController();const stop=()=>abort.abort();
  process.once('SIGINT',stop);process.once('SIGTERM',stop);
  try { await runWorker({store,fcm,once:args.includes('--once'),signal:abort.signal,onResult:result=>process.stdout.write(JSON.stringify({result})+'\n')}); }
  finally {process.removeListener('SIGINT',stop);process.removeListener('SIGTERM',stop);}
}
if (process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) main().catch(()=>{process.stderr.write('Push worker disabled or failed; check authorized server configuration. No credentials are logged.\n');process.exitCode=1;});
