// Safe local inspection, not a fake successful Auth backend and never a production entrypoint.
import { loadConfig } from './config.mjs';
import { createPortal } from './server.mjs';
try {
  const config = loadConfig();
  if (config.secure) throw Error('Inspection requires a loopback development origin.');
  createPortal({ ...config, inspection: true })
    .on('error', () => { console.error('Inspection listener unavailable.'); process.exitCode = 1; })
    .listen(config.port, config.host, () => console.log('LOCAL INSPECTION: Auth POSTs blocked; no upstream Auth operations.'));
} catch { console.error('Inspection requires valid local configuration; values suppressed.'); process.exitCode = 1; }
