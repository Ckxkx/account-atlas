// Web side of the 2.6 backup-encryption tests. No dependencies: it lifts the BackupCrypto block out of
// app.js as it ships and runs it on Node's WebCrypto. Run: node tools/test-backup-crypto.mjs
import { webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const root = new URL('../', import.meta.url);
const src = readFileSync(new URL('app.js', root), 'utf8');
const a = src.indexOf('const BackupCrypto = (() => {'), b = src.indexOf('})();', a);
assert.ok(a > 0 && b > a, 'BackupCrypto block found in app.js');
const BackupCrypto = new Function('crypto', 'btoa', 'atob', 'TextEncoder', 'TextDecoder', src.slice(a, b + 5) + '\nreturn BackupCrypto;')(webcrypto, globalThis.btoa, globalThis.atob, TextEncoder, TextDecoder);
const tv = new URL('tools/testvectors/', root);
const plain = readFileSync(new URL('backup-plain-v1.json', tv), 'utf8');
const pass = readFileSync(new URL('backup-password.txt', tv), 'utf8');
const env = JSON.parse(readFileSync(new URL('backup-enc-v1.json', tv), 'utf8'));

assert.ok(BackupCrypto.isEnvelope(env));
assert.ok(!BackupCrypto.isEnvelope(JSON.parse(plain)), 'a plain backup is not an envelope');
// 1. the committed vector opens to the plain file, byte for byte
assert.equal(await BackupCrypto.open(env, pass), plain);
// 2. a wrong password is reported as such (not as a broken file)
await assert.rejects(BackupCrypto.open(env, pass + 'x'), e => e.message === 'password');
await assert.rejects(BackupCrypto.open(env, '星图-backup 1234'), e => e.message === 'password');
// 3. broken / unknown envelopes are "format"
await assert.rejects(BackupCrypto.open({ ...env, iv: 'AAAA' }, pass), e => e.message === 'format');
await assert.rejects(BackupCrypto.open({ ...env, kdf: 'scrypt' }, pass), e => e.message === 'format');
await assert.rejects(BackupCrypto.open({ ...env, atlasEncrypted: 2 }, pass), e => e.message === 'format');
// 4. what the web app writes has the agreed shape and opens again
const out = JSON.parse(await BackupCrypto.seal(plain, pass));
assert.deepEqual(Object.keys(out), ['atlasEncrypted', 'app', 'kdf', 'iter', 'salt', 'iv', 'ct']);
assert.equal(out.iter, 210000); assert.equal(Buffer.from(out.salt, 'base64').length, 16); assert.equal(Buffer.from(out.iv, 'base64').length, 12);
assert.equal(Buffer.from(out.ct, 'base64').length, Buffer.byteLength(plain) + 16, 'ct = ciphertext || 16-byte tag');
assert.equal(await BackupCrypto.open(out, pass), plain);
// 5. a big backup (base64 in chunks, no call-stack limit)
const big = JSON.stringify({ app: 'atlas', version: 1, identities: [], accounts: Array.from({ length: 4000 }, (_, k) => ({ id: 'a' + k, name: '网站' + k })) });
assert.equal(await BackupCrypto.open(JSON.parse(await BackupCrypto.seal(big, '1234')), '1234'), big);
// 6. optional: a file another platform wrote (CI passes the one iOS made): node tools/test-backup-crypto.mjs <file> <password>
if (process.argv[2]) {
  assert.equal(await BackupCrypto.open(JSON.parse(readFileSync(process.argv[2], 'utf8')), process.argv[3]), plain, 'other platform\'s file opens to the plain vector');
  console.log('opened', process.argv[2]);
}
console.log('backup crypto (web): all passed');
