// Compiles contracts/MediaProvenance.sol -> artifacts/MediaProvenance.json (abi + bytecode)
const fs = require('fs');
const path = require('path');
const solc = require('solc');

const file = path.join(__dirname, '..', 'contracts', 'MediaProvenance.sol');
const input = {
  language: 'Solidity',
  sources: { 'MediaProvenance.sol': { content: fs.readFileSync(file, 'utf8') } },
  settings: {
    evmVersion: 'paris', // safe on Sepolia and local chains
    optimizer: { enabled: true, runs: 200 },
    outputSelection: { '*': { '*': ['abi', 'evm.bytecode.object'] } },
  },
};

const out = JSON.parse(solc.compile(JSON.stringify(input)));
const errors = (out.errors || []).filter((e) => e.severity === 'error');
if (errors.length) {
  errors.forEach((e) => console.error(e.formattedMessage));
  process.exit(1);
}
(out.errors || []).forEach((e) => console.warn(e.formattedMessage));

const c = out.contracts['MediaProvenance.sol'].MediaProvenance;
const dir = path.join(__dirname, '..', 'artifacts');
fs.mkdirSync(dir, { recursive: true });
fs.writeFileSync(
  path.join(dir, 'MediaProvenance.json'),
  JSON.stringify({ abi: c.abi, bytecode: '0x' + c.evm.bytecode.object }, null, 2)
);
console.log('Compiled OK -> artifacts/MediaProvenance.json');
