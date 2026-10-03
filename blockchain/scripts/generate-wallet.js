// Generates a fresh throwaway wallet locally. No browser extension or sign-up needed.
// Run: node scripts/generate-wallet.js
const { ethers } = require('ethers');
const wallet = ethers.Wallet.createRandom();

console.log('New wallet generated (keep this private key only in your .env, never share it):\n');
console.log('Address:     ', wallet.address);
console.log('Private key: ', wallet.privateKey);
console.log('\nNext steps:');
console.log('1. Paste the Address into a Sepolia faucet to get free test ETH.');
console.log('2. Put the Private key in .env as PRIVATE_KEY=... (copy .env.example to .env first).');
