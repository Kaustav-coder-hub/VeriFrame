// Run: node demo-test.js   (proves the full loop on Sepolia)
const p = require('../src/provenance');
const crypto = require('crypto');

(async () => {
  const original = crypto.randomBytes(2048);   // stand-in for the uploaded image
  const cropped  = crypto.randomBytes(1024);   // stand-in for the cropped version
  const tampered = crypto.randomBytes(1024);   // a file that was never registered

  console.log('1. Registering original...');
  const r1 = await p.registerOriginal(original);
  console.log('   ', r1);

  console.log('2. Registering CROP version...');
  const r2 = await p.registerDerived(r1.hash, cropped, 'CROP');
  console.log('   ', r2);

  console.log('3. Verify cropped file (expect VERIFIED)');
  console.log('   ', (await p.verifyMedia(cropped)).status);

  console.log('4. Verify unknown file (expect MISMATCH)');
  console.log('   ', (await p.verifyMedia(tampered)).status);

  console.log('5. Timeline:');
  console.log(await p.getHistory(r2.hash));
})().catch(console.error);
