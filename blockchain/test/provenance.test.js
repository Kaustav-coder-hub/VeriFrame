// Runs against a local in-memory chain (ganache). No real ETH needed.
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const ganache = require('ganache');
const { ethers } = require('ethers');
const artifact = require('../artifacts/MediaProvenance.json');

const KEY = '0x' + '11'.repeat(32);
const PORT = 8599;
let server, provider, wallet, address, p;

before(async () => {
  server = ganache.server({
    wallet: { accounts: [{ secretKey: KEY, balance: '0x56BC75E2D63100000' }] },
    logging: { quiet: true },
    chain: { hardfork: 'shanghai' },
  });
  await server.listen(PORT);
  provider = new ethers.JsonRpcProvider(`http://127.0.0.1:${PORT}`, undefined, { cacheTimeout: -1 });
  wallet = new ethers.Wallet(KEY, provider);
  const f = new ethers.ContractFactory(artifact.abi, artifact.bytecode, wallet);
  const c = await f.deploy();
  await c.waitForDeployment();
  address = await c.getAddress();
  process.env.SEPOLIA_RPC_URL = `http://127.0.0.1:${PORT}`;
  process.env.PRIVATE_KEY = KEY;
  process.env.CONTRACT_ADDRESS = address;
  p = require('../src/provenance'); // loads after env is set
});

after(async () => { await server.close(); });

const rnd = (n = 256) => crypto.randomBytes(n);

test('hashMedia is deterministic 32-byte SHA-256', () => {
  const b = Buffer.from('hello');
  assert.equal(p.hashMedia(b), p.hashMedia(b));
  assert.equal(
    p.hashMedia(b),
    '0x2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824'
  );
});

test('register original then verify -> VERIFIED', async () => {
  const img = rnd();
  const r = await p.registerOriginal(img);
  assert.equal(r.recordId, 1);
  const v = await p.verifyMedia(img);
  assert.equal(v.status, 'VERIFIED');
  assert.equal(v.record.operation, 'ORIGINAL');
  assert.equal(v.record.previousHash, null);
});

test('unregistered / tampered file -> MISMATCH', async () => {
  const v = await p.verifyMedia(rnd());
  assert.equal(v.status, 'MISMATCH');
  assert.equal(v.record, null);
});

test('one changed byte breaks verification', async () => {
  const img = rnd();
  await p.registerOriginal(img);
  const tampered = Buffer.from(img);
  tampered[0] ^= 0xff;
  assert.equal((await p.verifyMedia(tampered)).status, 'MISMATCH');
});

test('derived versions link to parent and form a timeline', async () => {
  const a = rnd(), b = rnd(), c = rnd();
  const r1 = await p.registerOriginal(a);
  const r2 = await p.registerDerived(r1.hash, b, 'CROP');
  const r3 = await p.registerDerived(r2.hash, c, 'BG_REMOVAL');
  const h = await p.getHistory(r3.hash);
  assert.deepEqual(h.map((x) => x.operation), ['ORIGINAL', 'CROP', 'BG_REMOVAL']);
  assert.equal(h[1].previousHash, r1.hash);
  assert.equal(h[2].previousHash, r2.hash);
  assert.equal(h[0].previousHash, null);
});

test('duplicate hash is rejected', async () => {
  const img = rnd();
  await p.registerOriginal(img);
  await assert.rejects(() => p.registerOriginal(img));
});

test('derived version with unknown parent is rejected', async () => {
  await assert.rejects(() => p.registerDerived(p.hashMedia(rnd()), rnd(), 'CROP'));
});

test('event carries recordId, creator and timestamp', async () => {
  const r = await p.registerOriginal(rnd());
  const v = await p.getHistory(r.hash);
  assert.equal(v[0].recordId, r.recordId);
  assert.equal(v[0].creator, wallet.address);
  assert.ok(new Date(v[0].timestamp).getTime() > 0);
});

test('concurrent registrations do not collide', async () => {
  const files = [rnd(), rnd(), rnd(), rnd()];
  const results = await Promise.all(files.map((f) => p.registerOriginal(f)));
  assert.equal(new Set(results.map((r) => r.recordId)).size, 4);
  for (const f of files) assert.equal((await p.verifyMedia(f)).status, 'VERIFIED');
});
