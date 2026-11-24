#!/usr/bin/env python3
"""Writes tools/sites.tsv (the searchable site directory) into the three apps.

  app.js                                    between // SITES:BEGIN and // SITES:END
  native/Presets.swift                      between // SITES:BEGIN and // SITES:END
  android/.../atlas/Presets.kt              between // SITES:BEGIN and // SITES:END

Run from the repo root:  python3 tools/sites.py
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATS = ['社交', '购物', '支付金融', '开发', 'AI', '娱乐', '游戏', '出行', '工具', '加密', '其他']


def rows():
    out, seen = [], set()
    for n, line in enumerate((ROOT / 'tools/sites.tsv').read_text(encoding='utf-8').splitlines(), 1):
        if not line.strip() or line.startswith('#'):
            continue
        f = line.split('\t')
        if len(f) != 4:
            sys.exit(f'sites.tsv:{n}: want 4 tab-separated fields, got {len(f)}')
        name, domain, cat, aliases = (x.strip() for x in f)
        if cat not in CATS:
            sys.exit(f'sites.tsv:{n}: unknown category {cat}')
        if name.lower() in seen:
            sys.exit(f'sites.tsv:{n}: duplicate {name}')
        seen.add(name.lower())
        for ch in '|\n"\\$':
            if ch in name + domain + aliases:
                sys.exit(f'sites.tsv:{n}: {ch!r} not allowed')
        out.append((name, domain.lower(), cat, ' '.join(aliases.lower().split())))
    return out


def splice(path, body):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    new, k = re.subn(r'(// SITES:BEGIN[^\n]*\n).*?([ \t]*// SITES:END)', lambda m: m.group(1) + body + '\n' + m.group(2), s, flags=re.S)
    if k != 1:
        sys.exit(f'{path}: markers not found')
    p.write_text(new, encoding='utf-8')


def main():
    r = rows()
    # app.js: one string, "name|domain|category|aliases" per line, split at load
    js = '  const SITES = `' + '\n'.join('|'.join(x) for x in r) + '`.split(\'\\n\').map(l => { const [n, d, c, a] = l.split(\'|\'); return [n, d, c, \'\', \'\', a || \'\']; });'
    splice('app.js', js)
    sw = '\n'.join(f'    Preset(name: {json.dumps(n, ensure_ascii=False)}, domain: "{d}", category: "{c}", color: "", aliases: {json.dumps(a, ensure_ascii=False)}),' for n, d, c, a in r)
    splice('native/Presets.swift', '  static let directory: [Preset] = [\n' + sw + '\n  ]')
    kt = '\n'.join(f'    Preset({json.dumps(n, ensure_ascii=False)}, "{d}", "{c}", "", {json.dumps(a, ensure_ascii=False)}),' for n, d, c, a in r)
    splice('android/app/src/main/java/dev/huichen/atlas/Presets.kt', '  val directory: List<Preset> = listOf(\n' + kt + '\n  )')
    print(f'{len(r)} sites written')


if __name__ == '__main__':
    main()
