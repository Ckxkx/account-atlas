#!/usr/bin/env node
// Writes the version.json the website serves for the iOS / Android 检查更新.
//   node tools/version-json.mjs <version> <release-notes.md | -> <out.json>
// - version: the latest published release (release.yml passes the one it just published, web.yml asks
//   GitHub for the latest release), so the apps never hear about a version before its APK / IPA exist.
// - notes: the repo's version.json "notes" when its "version" is that same version (hand-written), otherwise
//   the bullet list under "## 更新内容" in the release notes.
// - url / password / github and any other field: copied from the repo's version.json.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const [, , rawV, notesFile, out] = process.argv;
const V = String(rawV || '').trim().replace(/^v/, '');
if (!/^\d+\.\d+(\.\d+)?$/.test(V) || !out) {
  console.error('usage: version-json.mjs <version like 2.6 or 2.6.1> <release-notes.md|-> <out.json>');
  process.exit(1);
}
const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const repo = JSON.parse(readFileSync(join(root, 'version.json'), 'utf8'));
const same = (a, b) => {
  const p = s => String(s || '').replace(/^v/, '').split('.').map(Number);
  const x = p(a), y = p(b);
  for (let i = 0; i < Math.max(x.length, y.length); i++) if ((x[i] || 0) !== (y[i] || 0)) return false;
  return true;
};

let notes = [];
if (same(repo.version, V) && Array.isArray(repo.notes)) notes = repo.notes.filter(n => typeof n === 'string' && n.trim()).map(n => n.trim());
else if (notesFile && notesFile !== '-') {
  let md = '';
  try { md = readFileSync(notesFile, 'utf8'); } catch {}
  const part = md.split(/^##\s*更新内容\s*$/m)[1] || '';
  notes = part.split(/^##\s/m)[0].split('\n').map(l => l.trim()).filter(l => l.startsWith('- ')).map(l => l.slice(2).trim()).filter(Boolean);
}
notes = notes.slice(0, 8).map(n => (n.length > 120 ? n.slice(0, 119) + '…' : n));
if (!notes.length) notes = ['小改进与修复'];

const res = { ...repo, version: V, notes };
writeFileSync(out, JSON.stringify(res, null, 2) + '\n');
console.log(JSON.stringify(res));
