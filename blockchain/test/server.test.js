const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const ganache = require('ganache');
const { ethers } = require('ethers');
const artifact = require('../artifacts/MediaProvenance.json');

const KEY = '0x' + '33'.repeat(32);
const CHAIN_PORT = 8597;
let chain, http, base;

const post = (path, fields, file) => {
  const fd = new FormData();
  for (const [k, v] of Object.entries(fields || {})) fd.append(k, v);
  if (file) fd.append('file', new Blob([file]), 'media.bin');
  return fetch(base + path, { method: 'POST', body: fd });
};

before(async () => {
  chain = ganache.server({
    wallet: { accounts: [{ secretKey: KEY, balance: '0x56BC75E2D63100000' }] },
    logging: { quiet: true },
    chain: { hardfork: 'shanghai' },
  });
  await chain.listen(CHAIN_PORT);
  const provider = new ethers.JsonRpcProvider(`http://127.0.0.1:${CHAIN_PORT}`);
  const wallet = new ethers.Wallet(KEY, provider);
  const c = await new ethers.ContractFactory(artifact.abi, artifact.bytecode, wallet).deploy();
  await c.waitForDeployment();
  process.env.SEPOLIA_RPC_URL = `http://127.0.0.1:${CHAIN_PORT}`;
  process.env.PRIVATE_KEY = KEY;
  process.env.CONTRACT_ADDRESS = await c.getAddress();
  const app = require('../src/server');
  http = app.listen(0);
  base = `http://127.0.0.1:${http.address().port}`;
});

after(async () => { http.close(); await chain.close(); });

test('full API flow: register -> version -> verify -> history', async () => {
  const original = crypto.randomBytes(500), cropped = crypto.randomBytes(300);

  const r1 = await post('/api/register', {}, original);
  assert.equal(r1.status, 201);
  const o = await r1.json();

  const r2 = await post('/api/version', { previousHash: o.hash, operation: 'CROP' }, cropped);
  assert.equal(r2.status, 201);
  const v = await r2.json();
  assert.equal(v.previousHash, o.hash);

  const ok = await (await post('/api/verify', {}, cropped)).json();
  assert.equal(ok.status, 'VERIFIED');
  assert.equal(ok.record.operation, 'CROP');

  const bad = await (await post('/api/verify', {}, crypto.randomBytes(300))).json();
  assert.equal(bad.status, 'MISMATCH');

  const hist = await (await fetch(`${base}/api/history/${v.hash}`)).json();
  assert.deepEqual(hist.map((x) => x.operation), ['ORIGINAL', 'CROP']);
});

test('error handling: duplicate 409, unknown parent 404, bad input 400', async () => {
  const f = crypto.randomBytes(100);
  assert.equal((await post('/api/register', {}, f)).status, 201);
  assert.equal((await post('/api/register', {}, f)).status, 409);
  const unknown = '0x' + 'ab'.repeat(32);
  assert.equal((await post('/api/version', { previousHash: unknown, operation: 'CROP' }, crypto.randomBytes(50))).status, 404);
  assert.equal((await post('/api/register', {})).status, 400);
  assert.equal((await post('/api/version', { previousHash: 'nope', operation: 'X' }, f)).status, 400);
  assert.equal((await fetch(`${base}/api/history/bad`)).status, 400);
});

test('health endpoint reports record count', async () => {
  const j = await (await fetch(`${base}/health`)).json();
  assert.equal(j.status, 'ok');
  assert.ok(j.records >= 1);
});
