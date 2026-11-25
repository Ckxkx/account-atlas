// Makes the cross-platform test vector for password-protected backups (2.6), with Node's WebCrypto,
// i.e. the same API the web app uses. Run: node tools/backup-vector.mjs
//   in : tools/testvectors/backup-plain-v1.json (a plain backup file)
//   out: tools/testvectors/backup-enc-v1.json   (the same bytes, encrypted with the password below)
// Android (BackupCryptoTest), iOS (CI step in ios.yml) and the web test decrypt it and must get the
// plain file back byte for byte. The password has non-ASCII characters on purpose (UTF-8 everywhere).
import { webcrypto as crypto } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
export const PASSWORD = '星图-Backup 1234';
const dir = fileURLToPath(new URL('./testvectors/', import.meta.url));
const b64 = u => Buffer.from(u).toString('base64');
const plain = readFileSync(dir + 'backup-plain-v1.json');
const salt = crypto.getRandomValues(new Uint8Array(16)), iv = crypto.getRandomValues(new Uint8Array(12)), iter = 210000;
const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(PASSWORD), 'PBKDF2', false, ['deriveKey']);
const key = await crypto.subtle.deriveKey({ name: 'PBKDF2', salt, iterations: iter, hash: 'SHA-256' }, base, { name: 'AES-GCM', length: 256 }, false, ['encrypt']);
const ct = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, plain));
const env = { atlasEncrypted: 1, app: 'atlas', kdf: 'PBKDF2-SHA256', iter, salt: b64(salt), iv: b64(iv), ct: b64(ct) };
writeFileSync(dir + 'backup-enc-v1.json', JSON.stringify(env, null, 2) + '\n');
writeFileSync(dir + 'backup-password.txt', PASSWORD);
console.log('wrote backup-enc-v1.json,', plain.length, 'plaintext bytes');
