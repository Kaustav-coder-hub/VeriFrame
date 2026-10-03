// Deploys the contract to the network in .env and prints the address.
require('dotenv').config();
const { ethers } = require('ethers');
const artifact = require('../artifacts/MediaProvenance.json');

(async () => {
  if (!process.env.SEPOLIA_RPC_URL || !process.env.PRIVATE_KEY) {
    throw new Error('Set SEPOLIA_RPC_URL and PRIVATE_KEY in .env first');
  }
  const provider = new ethers.JsonRpcProvider(process.env.SEPOLIA_RPC_URL);
  const wallet = new ethers.Wallet(process.env.PRIVATE_KEY, provider);
  console.log('Deploying from', wallet.address);
  const factory = new ethers.ContractFactory(artifact.abi, artifact.bytecode, wallet);
  const contract = await factory.deploy();
  await contract.waitForDeployment();
  const address = await contract.getAddress();
  console.log('\nDeployed! Put this in .env ->  CONTRACT_ADDRESS=' + address);
  console.log('Etherscan: https://sepolia.etherscan.io/address/' + address);
})().catch((e) => { console.error(e.message); process.exit(1); });
