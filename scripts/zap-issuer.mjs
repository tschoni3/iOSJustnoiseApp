// Factory tool for macOS/Linux, using Node's built-in crypto. Keep the issuer directory off the app tree.
import { createCipheriv, createPrivateKey, generateKeyPairSync, randomBytes, sign } from 'node:crypto';
import { linkSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const [command, directory, argument, ...extra] = process.argv.slice(2);
const usage = 'Usage: node zap-issuer.mjs init ISSUER_DIR TRUST_JSON | register ISSUER_DIR UID';
const appRepo = fileURLToPath(new URL('../', import.meta.url));
const encode = buffer => buffer.toString('base64');

function initialize(dir, trustFile) {
  if (dir.startsWith(appRepo) || dir + sep === appRepo) throw Error('Keep issuer keys outside the mobile repository.');
  // Exclusive creation prevents accidental rotation or overwriting an existing issuer.
  mkdirSync(dir, { mode: 0o700 });
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const contentKey = encode(randomBytes(32));
  writeFileSync(join(dir, 'issuer.json'), JSON.stringify({
    privateKey: privateKey.export({ format: 'pem', type: 'pkcs8' }), contentKey
  }), { flag: 'wx', mode: 0o600 });
  mkdirSync(join(dir, 'registrations'), { mode: 0o700 });
  const trust = { verificationKey: encode(Buffer.from(publicKey.export({ format: 'jwk' }).x, 'base64url')), contentKey };
  writeFileSync(trustFile, JSON.stringify(trust, null, 2) + '\n', { flag: 'wx' });
}

export function register(dir, input) {
  if (!/^(?:[\da-f]{14}|[\da-f]{2}(?::[\da-f]{2}){6})$/i.test(input)) throw Error('Expected a 7-byte chip UID in NFC display order.');
  const uid = Buffer.from(input.replaceAll(':', ''), 'hex');
  const registrations = join(dir, 'registrations');
  const registered = join(registrations, uid.toString('hex') + '.txt');
  const issuer = JSON.parse(readFileSync(join(dir, 'issuer.json'), 'utf8'));
  const binding = Buffer.concat([Buffer.from('JustNoise/Zap/1'), uid]);
  const nonce = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', Buffer.from(issuer.contentKey, 'base64'), nonce);
  cipher.setAAD(binding);
  const ciphertext = Buffer.concat([cipher.update('justnoise.zap.activation.v1', 'utf8'), cipher.final()]);
  const sealed = Buffer.concat([nonce, ciphertext, cipher.getAuthTag()]);
  const signature = sign(null, Buffer.concat([binding, sealed]), createPrivateKey(issuer.privateKey));
  const record = 'JNZ1.' + encode(Buffer.concat([signature, sealed]));
  // Publish the complete registration atomically. Concurrent retries return the original text.
  const temporary = mkdtempSync(join(registrations, '.new-'));
  try {
    const prepared = join(temporary, 'record');
    writeFileSync(prepared, record + '\n', { flag: 'wx', mode: 0o600 });
    try { linkSync(prepared, registered); }
    catch (err) { if (err.code !== 'EEXIST') throw err; }
  } finally { rmSync(temporary, { recursive: true, force: true }); }
  return readFileSync(registered, 'utf8').trimEnd();
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (!directory || !argument || extra.length) throw Error(usage);
    if (command === 'init') initialize(resolve(directory), resolve(argument));
    else if (command === 'register') console.log(register(resolve(directory), argument));
    else throw Error(usage);
  } catch (err) {
    console.error(err.message);
    process.exitCode = 1;
  }
}
