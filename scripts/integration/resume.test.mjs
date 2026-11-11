// Loopback fake Auth ONLY: this does not certify live Supabase integration.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { resolve } from 'node:path';

test('partial signup resumes only the missing vendors, preserving consumers and pending password (loopback fake Auth)', async () => {
  mkdirSync('.local/integration', { recursive: true });
  const dir = mkdtempSync(resolve('.local/integration/offline-resume-'));
  const seen = [];
  const server = createServer(async (req, res) => {
    let body = ''; for await (const part of req) body += part;
    const account = JSON.parse(body); seen.push({ method: req.method, path: req.url, ...account });
    res.writeHead(201, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ user: { id: `synthetic-${seen.length}` } }));
  });
  try {
    await new Promise((ok, fail) => { server.once('error', fail); server.listen(0, '127.0.0.1', ok); });
    const url = `http://127.0.0.1:${server.address().port}`;
    const cfg = { environment: 'local', projectRef: '', url, publishableKey: 'sb_publishable_synthetic_test_only', emailDomain: 'example.test', emailPrefix: 'qa' };
    const run = { runId: 'offline-fixture', environment: 'local', projectRef: '', url, accounts: {
      consumerA: { userId: 'synthetic-consumer-a', password: 'test-only-a' },
      consumerB: { userId: 'synthetic-consumer-b', password: 'test-only-b' },
      vendorA: { email: 'qa+pending-vendora@example.test', password: 'preserve-this-test-only-password' }
    } };
    const path = resolve(dir, 'partial.json'), config = resolve(dir, 'target.json');
    writeFileSync(path, JSON.stringify(run), { mode: 0o600 });
    writeFileSync(config, JSON.stringify(cfg), { mode: 0o600 });
    writeFileSync(path.replace(/\.json$/, '.results.json'), JSON.stringify({ phase: 'signup', runId: run.runId, results: [{ name: 'signup.consumerA', status: 'passed' }, { name: 'signup.consumerB', status: 'passed' }] }), { mode: 0o600 });
    const result = await new Promise((ok, fail) => {
      const child = spawn(process.execPath, ['scripts/integration/supabase.mjs', '--phase', 'signup', '--config', config, '--run', path, '--allow-local-writes']);
      let output = ''; child.stdout.on('data', x => output += x); child.stderr.on('data', x => output += x);
      child.on('error', fail); child.on('close', code => ok({ code, output }));
    });
    assert.equal(result.code, 0, result.output);
    assert.equal(seen.length, 2);
    assert(seen.every(x => x.method === 'POST' && x.path === '/auth/v1/signup' && x.data.role === 'vendor'));
    assert.equal(seen[0].password, run.accounts.vendorA.password);
    const saved = JSON.parse(readFileSync(path, 'utf8'));
    assert.deepEqual(saved.accounts.consumerA, run.accounts.consumerA);
    assert.deepEqual(saved.accounts.consumerB, run.accounts.consumerB);
    assert.equal(saved.accounts.vendorA.userId, 'synthetic-1');
    assert.equal(saved.accounts.vendorB.userId, 'synthetic-2');
    assert.equal(JSON.parse(readFileSync(path.replace(/\.json$/, '.results.json'), 'utf8')).results.length, 4);
  } finally { await new Promise(ok => server.close(ok)); rmSync(dir, { recursive: true, force: true }); }
});
