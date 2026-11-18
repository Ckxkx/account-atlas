/* Atlas · 账号星图 — local-first account relationship manager */
(() => {
  'use strict';

  // ---------------- constants ----------------
  const KEY = 'atlas.v1';
  const COLORS = ['#8b5cf6', '#22d3ee', '#f472b6', '#fbbf24', '#34d399', '#fb7185', '#60a5fa', '#a3e635'];
  const CATS = ['社交', '购物', '支付金融', '开发', 'AI', '娱乐', '游戏', '出行', '工具', '加密', '其他'];
  const PRESETS = [
    ['微信', 'weixin.qq.com', '社交'], ['QQ', 'qq.com', '社交'], ['微博', 'weibo.com', '社交'], ['小红书', 'xiaohongshu.com', '社交'],
    ['知乎', 'zhihu.com', '社交'], ['抖音', 'douyin.com', '娱乐'], ['哔哩哔哩', 'bilibili.com', '娱乐'], ['网易云音乐', 'music.163.com', '娱乐'],
    ['淘宝', 'taobao.com', '购物'], ['京东', 'jd.com', '购物'], ['拼多多', 'pinduoduo.com', '购物'], ['闲鱼', 'goofish.com', '购物'],
    ['美团', 'meituan.com', '出行'], ['携程', 'ctrip.com', '出行'], ['12306', '12306.cn', '出行'], ['高德地图', 'amap.com', '出行'],
    ['支付宝', 'alipay.com', '支付金融'], ['百度', 'baidu.com', '工具'], ['GitHub', 'github.com', '开发'], ['Gitee', 'gitee.com', '开发'],
    ['Cloudflare', 'cloudflare.com', '开发'], ['Vercel', 'vercel.com', '开发'], ['Google', 'google.com', '工具'], ['Apple ID', 'apple.com', '工具'],
    ['Microsoft', 'microsoft.com', '工具'], ['Notion', 'notion.so', '工具'], ['Figma', 'figma.com', '工具'], ['ChatGPT', 'chatgpt.com', 'AI'],
    ['Claude', 'claude.ai', 'AI'], ['Gemini', 'gemini.google.com', 'AI'], ['DeepSeek', 'deepseek.com', 'AI'], ['Kimi', 'kimi.com', 'AI'],
    ['Steam', 'steampowered.com', '游戏'], ['Epic Games', 'epicgames.com', '游戏'], ['米哈游', 'mihoyo.com', '游戏'], ['Discord', 'discord.com', '社交'],
    ['Telegram', 'telegram.org', '社交'], ['X (Twitter)', 'x.com', '社交'], ['Instagram', 'instagram.com', '社交'], ['Netflix', 'netflix.com', '娱乐'],
    ['Spotify', 'spotify.com', '娱乐'], ['YouTube', 'youtube.com', '娱乐'], ['Binance', 'binance.com', '加密'], ['OKX', 'okx.com', '加密'],
    ['MetaMask', 'metamask.io', '加密'], ['PayPal', 'paypal.com', '支付金融'], ['Amazon', 'amazon.com', '购物'],
  ];

  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);
  const esc = (s = '') => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const debounce = (fn, ms) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };

  // ---------------- state ----------------
  let state = load();
  let ui = { tab: 'identities', idFilter: 'all', cat: '全部', privacy: localStorage.getItem('atlas.privacy') === '1' };

  function load() {
    try {
      const raw = localStorage.getItem(KEY);
      if (raw) return JSON.parse(raw);
    } catch (e) { /* ignore */ }
    return null;
  }
  function save() { localStorage.setItem(KEY, JSON.stringify(state)); }

  function demo() {
    const ids = [
      { id: uid(), type: 'email', value: 'me@gmail.com', label: '主力邮箱', color: COLORS[0], notes: '' },
      { id: uid(), type: 'email', value: 'shopping.fun@qq.com', label: '购物 / 小号', color: COLORS[2], notes: '' },
      { id: uid(), type: 'email', value: 'dev@outlook.com', label: '开发专用', color: COLORS[4], notes: '' },
      { id: uid(), type: 'phone', value: '+86 138 0000 1234', label: '主力手机号', color: COLORS[1], notes: '移动 · 实名' },
      { id: uid(), type: 'phone', value: '+86 170 0000 5678', label: '副卡', color: COLORS[3], notes: '' },
    ];
    const [g, q, o, p1, p2] = ids.map(i => i.id);
    const mk = (name, domain, category, identityIds, notes = '') => ({ id: uid(), name, domain, category, identityIds, notes, createdAt: Date.now() });
    return {
      identities: ids,
      accounts: [
        mk('GitHub', 'github.com', '开发', [o, p1], '已开 2FA'),
        mk('Cloudflare', 'cloudflare.com', '开发', [o]),
        mk('ChatGPT', 'chatgpt.com', 'AI', [g]),
        mk('Claude', 'claude.ai', 'AI', [g]),
        mk('微信', 'weixin.qq.com', '社交', [p1]),
        mk('支付宝', 'alipay.com', '支付金融', [p1]),
        mk('淘宝', 'taobao.com', '购物', [q, p1]),
        mk('京东', 'jd.com', '购物', [q, p2]),
        mk('哔哩哔哩', 'bilibili.com', '娱乐', [p2]),
        mk('小红书', 'xiaohongshu.com', '社交', [p2]),
        mk('Steam', 'steampowered.com', '游戏', [q], '令牌在主力手机上'),
        mk('Apple ID', 'apple.com', '工具', [g, p1]),
        mk('Notion', 'notion.so', '工具', [g]),
        mk('Binance', 'binance.com', '加密', [o, p1], 'KYC 已认证'),
      ],
      demo: true,
    };
  }

  // ---------------- helpers ----------------
  const idById = id => state.identities.find(i => i.id === id);
  const accById = id => state.accounts.find(a => a.id === id);
  const accountsOf = idId => state.accounts.filter(a => a.identityIds.includes(idId));

  function mask(v, type) {
    if (!ui.privacy) return v;
    if (type === 'email') {
      const [u, d = ''] = v.split('@');
      return (u.length <= 2 ? u[0] + '•' : u.slice(0, 2) + '•'.repeat(Math.min(6, u.length - 2))) + (d ? '@' + d : '');
    }
    const digits = v.replace(/\D/g, '');
    return digits.length > 7 ? digits.slice(0, 3) + ' •••• ' + digits.slice(-4) : '••••';
  }
  const idText = i => mask(i.value, i.type);

  function cleanDomain(d = '') {
    return d.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/^www\./, '').split(/[/?#]/)[0];
  }
  // favicon with graceful fallbacks (some sources are blocked in some regions)
  function favSources(domain) {
    const d = cleanDomain(domain);
    if (!d) return [];
    return [
      `https://favicon.im/${d}?larger=true`,
      `https://${d}/favicon.ico`,
      `https://icons.duckduckgo.com/ip3/${d}.ico`,
      `https://www.google.com/s2/favicons?domain=${d}&sz=64`,
    ];
  }
  function favHTML(acc, cls = 'fav') {
    const src = favSources(acc.domain);
    const letter = esc((acc.name || '?').trim().slice(0, 1).toUpperCase());
    if (!src.length) return `<div class="${cls} letter">${letter}</div>`;
    return `<div class="${cls}"><img loading="lazy" alt="" src="${src[0]}" data-src='${JSON.stringify(src.slice(1))}' data-letter="${letter}" onerror="AtlasFav(this)"></div>`;
  }
  window.AtlasFav = img => {
    const rest = JSON.parse(img.dataset.src || '[]');
    if (rest.length) { img.dataset.src = JSON.stringify(rest.slice(1)); img.src = rest[0]; return; }
    const box = img.parentElement; box.classList.add('letter'); box.textContent = img.dataset.letter;
  };
  const stackImg = acc => {
    const src = favSources(acc.domain);
    return src.length ? `<img alt="" src="${src[0]}" data-src='${JSON.stringify(src.slice(1))}' data-letter="" onerror="AtlasFavStack(this)" title="${esc(acc.name)}">` : '';
  };
  window.AtlasFavStack = img => {
    const rest = JSON.parse(img.dataset.src || '[]');
    if (rest.length) { img.dataset.src = JSON.stringify(rest.slice(1)); img.src = rest[0]; } else img.remove();
  };

  const pill = i => `<span class="pill" style="--c:${i.color}"><i></i>${esc(idText(i))}</span>`;
  const idGlyph = i => i.type === 'email'
    ? '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="m4 7 8 6 8-6"/></svg>'
    : '<svg viewBox="0 0 24 24"><rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M11 18h2"/></svg>';

  // ---------------- toasts ----------------
  function toast(msg, action) {
    const t = document.createElement('div');
    t.className = 'toast';
    t.innerHTML = `<i></i><span>${esc(msg)}</span>${action ? `<button>${esc(action.label)}</button>` : ''}`;
    $('#toasts').appendChild(t);
    if (action) t.querySelector('button').onclick = () => { action.fn(); kill(); };
    const kill = () => { t.classList.add('out'); setTimeout(() => t.remove(), 350); };
    setTimeout(kill, action ? 5000 : 2600);
  }

  // ---------------- counters ----------------
  const shown = {};
  function animateCount(el, to) {
    const key = el.dataset.count, from = shown[key] || 0;
    shown[key] = to;
    const t0 = performance.now(), dur = 900;
    const step = now => {
      const p = Math.min(1, (now - t0) / dur), e = 1 - Math.pow(1 - p, 4);
      el.textContent = Math.round(from + (to - from) * e);
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
  function renderStats() {
    const v = {
      emails: state.identities.filter(i => i.type === 'email').length,
      phones: state.identities.filter(i => i.type === 'phone').length,
      accounts: state.accounts.length,
      links: state.accounts.reduce((n, a) => n + a.identityIds.length, 0),
    };
    $$('[data-count]').forEach(el => animateCount(el, v[el.dataset.count]));
  }

  // ---------------- identities view ----------------
  function renderIdentities() {
    const list = state.identities.filter(i => ui.idFilter === 'all' || i.type === ui.idFilter);
    const grid = $('#identityGrid');
    if (!state.identities.length) {
      grid.innerHTML = `<div class="empty"><div class="orb"></div><div style="font-size:17px;color:#fff;font-weight:650">还没有任何身份</div><p style="margin-top:6px">先添加一个邮箱或手机号，再把网站挂到它下面。</p><button class="btn primary" style="margin-top:18px" data-open="identity">添加第一个身份</button></div>`;
      return;
    }
    grid.innerHTML = list.map((i, n) => {
      const accs = accountsOf(i.id);
      const imgs = accs.slice(0, 4).map(stackImg).join('');
      const more = accs.length > 4 ? `<span class="more">+${accs.length - 4}</span>` : '';
      return `<article class="card" data-id="${i.id}" style="--c:${i.color};--i:${n}">
        <div class="card-top">
          <div class="id-icon">${idGlyph(i)}</div>
          <div style="min-width:0">
            <div class="id-label">${esc(i.label || (i.type === 'email' ? '邮箱' : '手机号'))}<span class="tag">${i.type === 'email' ? '邮箱' : '手机'}</span></div>
            <div class="id-value mono">${esc(idText(i))}</div>
          </div>
        </div>
        <div class="card-foot">
          <div class="count"><b>${accs.length}</b>个网站 / App</div>
          <div class="stack">${imgs}${more}</div>
        </div>
      </article>`;
    }).join('') + `<article class="card add-card" data-open="identity" style="--i:${list.length}"><div><svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>添加邮箱或手机号</div></article>`;
    bindTilt();
  }

  function bindTilt() {
    $$('#identityGrid .card').forEach(card => {
      card.onmousemove = e => {
        const r = card.getBoundingClientRect();
        const x = e.clientX - r.left, y = e.clientY - r.top;
        card.style.setProperty('--x', x + 'px'); card.style.setProperty('--y', y + 'px');
        const rx = ((y / r.height) - .5) * -6, ry = ((x / r.width) - .5) * 8;
        card.style.transform = `perspective(900px) rotateX(${rx}deg) rotateY(${ry}deg) translateY(-3px)`;
      };
      card.onmouseleave = () => { card.style.transform = ''; };
    });
  }

  // ---------------- accounts view ----------------
  function renderCats() {
    const counts = {};
    state.accounts.forEach(a => counts[a.category] = (counts[a.category] || 0) + 1);
    const cats = ['全部', ...CATS.filter(c => counts[c])];
    if (!cats.includes(ui.cat)) ui.cat = '全部';
    $('#catChips').innerHTML = cats.map(c => `<button class="chip ${c === ui.cat ? 'active' : ''}" data-cat="${c}">${c}<small>${c === '全部' ? state.accounts.length : counts[c]}</small></button>`).join('');
  }
  function accountRow(a, n = 0) {
    const ids = a.identityIds.map(idById).filter(Boolean);
    return `<div class="row" data-acc="${a.id}" style="--i:${n}">
      ${favHTML(a)}
      <div class="row-main">
        <div class="row-name">${esc(a.name)}<span class="tag">${esc(a.category)}</span></div>
        <div class="row-sub">${esc(a.domain || '—')}${a.notes ? ' · ' + esc(a.notes) : ''}</div>
      </div>
      <div class="row-ids">${ids.map(pill).join('') || '<span class="tag">未关联</span>'}</div>
    </div>`;
  }
  function renderAccounts() {
    renderCats();
    const list = state.accounts
      .filter(a => ui.cat === '全部' || a.category === ui.cat)
      .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
    $('#accountList').innerHTML = list.length ? list.map(accountRow).join('')
      : `<div class="empty"><div class="orb"></div><div style="font-size:17px;color:#fff;font-weight:650">这里还空着</div><p style="margin-top:6px">添加一个网站或 App，选上它绑定的邮箱和手机号。</p><button class="btn primary" style="margin-top:18px" data-open="account">添加网站</button></div>`;
  }

  function renderAll() {
    renderStats(); renderIdentities(); renderAccounts();
    if (ui.tab === 'graph') Graph.rebuild();
  }

  // ---------------- tabs ----------------
  function moveIndicator() {
    const act = $('.tab.active'), ind = $('.tab-indicator');
    ind.style.width = act.offsetWidth + 'px';
    ind.style.transform = `translateX(${act.offsetLeft}px)`;
  }
  function setTab(tab) {
    if (tab === ui.tab) return;
    ui.tab = tab;
    $$('.tab').forEach(t => t.classList.toggle('active', t.dataset.tab === tab));
    $$('.view').forEach(v => v.classList.toggle('active', v.dataset.view === tab));
    moveIndicator();
    if (tab === 'graph') Graph.start(); else Graph.stop();
  }

  // ---------------- drawer ----------------
  let drawerCtx = null;
  function openDrawer(html, color = '#8b5cf6') {
    const d = $('#drawer');
    d.style.setProperty('--c', color);
    d.innerHTML = html;
    d.classList.add('open'); d.setAttribute('aria-hidden', 'false');
    $('#scrim').classList.add('show');
  }
  function closeDrawer() {
    $('#drawer').classList.remove('open'); $('#drawer').setAttribute('aria-hidden', 'true');
    $('#scrim').classList.remove('show'); drawerCtx = null;
  }
  const closeBtn = `<button class="drawer-close" data-close-drawer><svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18"/></svg></button>`;

  function showIdentity(id) {
    const i = idById(id); if (!i) return;
    drawerCtx = { kind: 'identity', id };
    const accs = accountsOf(id).sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
    openDrawer(`
      <div class="drawer-head">${closeBtn}
        <div class="id-icon" style="--c:${i.color}">${idGlyph(i)}</div>
        <div class="drawer-title mono">${esc(idText(i))}</div>
        <div class="drawer-meta"><span>${esc(i.label || '')}</span><span class="tag">${i.type === 'email' ? '邮箱' : '手机号'}</span>
          <button class="tag" data-copy="${esc(i.value)}" style="cursor:pointer">复制</button></div>
      </div>
      <div class="drawer-body stagger">
        ${i.notes ? `<h4>备注</h4><div class="note">${esc(i.notes)}</div>` : ''}
        <h4>注册 / 绑定了 ${accs.length} 个网站与 App</h4>
        ${accs.map((a, n) => accountRow(a, n)).join('') || '<div class="note" style="color:var(--muted)">还没有关联任何网站。</div>'}
        <button class="btn ghost" style="margin-top:12px;width:100%;justify-content:center" data-open="account" data-preset-id="${i.id}">
          <svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>给它添加网站</button>
      </div>
      <div class="drawer-actions">
        <button class="btn" data-edit-identity="${i.id}">编辑</button>
        <button class="btn danger" data-del-identity="${i.id}">删除</button>
      </div>`, i.color);
  }

  function showAccount(id) {
    const a = accById(id); if (!a) return;
    drawerCtx = { kind: 'account', id };
    const ids = a.identityIds.map(idById).filter(Boolean);
    const color = ids[0]?.color || '#8b5cf6';
    openDrawer(`
      <div class="drawer-head">${closeBtn}
        ${favHTML(a)}
        <div class="drawer-title">${esc(a.name)}</div>
        <div class="drawer-meta"><span class="tag">${esc(a.category)}</span>
          ${a.domain ? `<a href="https://${esc(cleanDomain(a.domain))}" target="_blank" rel="noopener" style="color:#c4b5fd;text-decoration:none">${esc(a.domain)} ↗</a>` : ''}</div>
      </div>
      <div class="drawer-body stagger">
        <h4>使用的身份</h4>
        ${ids.map((i, n) => `<div class="row" data-id-open="${i.id}" style="--i:${n}">
            <div class="id-icon" style="--c:${i.color};width:34px;height:34px;border-radius:11px">${idGlyph(i)}</div>
            <div class="row-main"><div class="row-name mono" style="font-size:14px">${esc(idText(i))}</div><div class="row-sub">${esc(i.label || '')}</div></div>
          </div>`).join('') || '<div class="note" style="color:var(--muted)">未关联任何邮箱或手机号。</div>'}
        ${a.notes ? `<h4>备注</h4><div class="note">${esc(a.notes)}</div>` : ''}
        <h4>添加于</h4><div class="note" style="color:var(--muted)">${new Date(a.createdAt || Date.now()).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })}</div>
      </div>
      <div class="drawer-actions">
        <button class="btn" data-edit-account="${a.id}">编辑</button>
        <button class="btn danger" data-del-account="${a.id}">删除</button>
      </div>`, color);
  }

  function refreshDrawer() {
    if (!drawerCtx) return;
    if (drawerCtx.kind === 'identity') idById(drawerCtx.id) ? showIdentity(drawerCtx.id) : closeDrawer();
    else accById(drawerCtx.id) ? showAccount(drawerCtx.id) : closeDrawer();
  }

  // ---------------- modals ----------------
  function openModal(html) {
    $('#modal').innerHTML = html;
    $('#modalLayer').classList.add('show');
    setTimeout(() => $('#modal input')?.focus(), 250);
  }
  const closeModal = () => $('#modalLayer').classList.remove('show');

  function identityForm(existing) {
    const i = existing || { type: 'email', value: '', label: '', color: COLORS[state.identities.length % COLORS.length], notes: '' };
    openModal(`
      <h3>${existing ? '编辑身份' : '添加身份'}</h3>
      <div class="sub">一个邮箱或一个手机号，就是一个“身份”。</div>
      <div class="field"><label>类型</label>
        <div class="seg" id="fType"><button type="button" data-v="email" class="${i.type === 'email' ? 'active' : ''}">邮箱</button><button type="button" data-v="phone" class="${i.type === 'phone' ? 'active' : ''}">手机号</button></div></div>
      <div class="field"><label id="fValueLabel">${i.type === 'email' ? '邮箱地址' : '手机号'}</label>
        <input class="input mono" id="fValue" value="${esc(i.value)}" placeholder="${i.type === 'email' ? 'name@example.com' : '+86 138 0000 0000'}"></div>
      <div class="field"><label>标签</label><input class="input" id="fLabel" value="${esc(i.label)}" placeholder="主力 / 小号 / 工作 / 游戏…"></div>
      <div class="field"><label>颜色</label><div class="swatches" id="fColor">${COLORS.map(c => `<button type="button" class="swatch ${c === i.color ? 'active' : ''}" style="--c:${c}" data-c="${c}"></button>`).join('')}</div></div>
      <div class="field"><label>备注（可选）</label><textarea class="input" id="fNotes" placeholder="运营商、是否实名、找回方式…（请勿填写密码）">${esc(i.notes || '')}</textarea></div>
      <div class="modal-actions"><button class="btn ghost" data-close-modal>取消</button><button class="btn primary" id="fSave">${existing ? '保存' : '添加'}</button></div>`);
    let type = i.type, color = i.color;
    $$('#fType button').forEach(b => b.onclick = () => {
      type = b.dataset.v; $$('#fType button').forEach(x => x.classList.toggle('active', x === b));
      $('#fValueLabel').textContent = type === 'email' ? '邮箱地址' : '手机号';
      $('#fValue').placeholder = type === 'email' ? 'name@example.com' : '+86 138 0000 0000';
    });
    $$('#fColor .swatch').forEach(s => s.onclick = () => { color = s.dataset.c; $$('#fColor .swatch').forEach(x => x.classList.toggle('active', x === s)); });
    $('#fSave').onclick = () => {
      const value = $('#fValue').value.trim();
      const ok = type === 'email' ? /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) : value.replace(/\D/g, '').length >= 5;
      if (!ok) { const f = $('#fValue'); f.classList.remove('shake'); void f.offsetWidth; f.classList.add('shake'); f.focus(); return; }
      const dup = state.identities.find(x => x.value.toLowerCase() === value.toLowerCase() && x.id !== existing?.id);
      if (dup) { toast('这个身份已经存在了'); return; }
      const data = { type, value, label: $('#fLabel').value.trim(), color, notes: $('#fNotes').value.trim() };
      if (existing) Object.assign(existing, data); else state.identities.push({ id: uid(), ...data });
      save(); closeModal(); renderAll(); refreshDrawer();
      toast(existing ? '已保存' : '身份已添加');
    };
  }

  function accountForm(existing, presetIdentity) {
    if (!state.identities.length) { toast('先添加一个邮箱或手机号吧'); identityForm(); return; }
    const a = existing || { name: '', domain: '', category: '其他', identityIds: presetIdentity ? [presetIdentity] : [], notes: '' };
    const sel = new Set(a.identityIds);
    openModal(`
      <h3>${existing ? '编辑网站 / App' : '添加网站 / App'}</h3>
      <div class="sub">输入名称会自动补全常见网站。</div>
      <div class="field"><label>名称</label><input class="input" id="aName" list="presetList" value="${esc(a.name)}" placeholder="GitHub、淘宝、微信…">
        <datalist id="presetList">${PRESETS.map(p => `<option value="${p[0]}">${p[1]}</option>`).join('')}</datalist></div>
      <div class="field"><label>域名（用于显示图标，可选）</label><input class="input mono" id="aDomain" value="${esc(a.domain)}" placeholder="github.com"></div>
      <div class="field"><label>分类</label><div class="chips" id="aCat">${CATS.map(c => `<button type="button" class="chip ${c === a.category ? 'active' : ''}" data-c="${c}">${c}</button>`).join('')}</div></div>
      <div class="field"><label>用哪些邮箱 / 手机号注册或绑定的</label>
        <div class="pick" id="aIds">${state.identities.map(i => `<button type="button" class="pill ${sel.has(i.id) ? 'on' : ''}" style="--c:${i.color}" data-id="${i.id}"><i></i>${esc(idText(i))}</button>`).join('')}</div>
        <div class="hint">可多选，比如邮箱注册 + 手机号绑定。</div></div>
      <div class="field"><label>备注（可选）</label><textarea class="input" id="aNotes" placeholder="是否开了 2FA、用户名、会员到期…（请勿填写密码）">${esc(a.notes || '')}</textarea></div>
      <div class="modal-actions"><button class="btn ghost" data-close-modal>取消</button><button class="btn primary" id="aSave">${existing ? '保存' : '添加'}</button></div>`);
    let cat = a.category;
    const setCat = c => { cat = c; $$('#aCat .chip').forEach(x => x.classList.toggle('active', x.dataset.c === c)); };
    $$('#aCat .chip').forEach(b => b.onclick = () => setCat(b.dataset.c));
    $$('#aIds .pill').forEach(b => b.onclick = () => { const id = b.dataset.id; sel.has(id) ? sel.delete(id) : sel.add(id); b.classList.toggle('on'); });
    $('#aName').addEventListener('input', e => {
      const p = PRESETS.find(p => p[0].toLowerCase() === e.target.value.trim().toLowerCase());
      if (p) { if (!$('#aDomain').value) $('#aDomain').value = p[1]; setCat(p[2]); }
    });
    $('#aSave').onclick = () => {
      const name = $('#aName').value.trim();
      if (!name) { const f = $('#aName'); f.classList.remove('shake'); void f.offsetWidth; f.classList.add('shake'); f.focus(); return; }
      const data = { name, domain: cleanDomain($('#aDomain').value), category: cat, identityIds: [...sel], notes: $('#aNotes').value.trim() };
      if (existing) Object.assign(existing, data); else state.accounts.push({ id: uid(), createdAt: Date.now(), ...data });
      save(); closeModal(); renderAll(); refreshDrawer();
      toast(existing ? '已保存' : `已添加 ${name}`);
    };
  }

  function confirmBox(title, text, okLabel, fn) {
    openModal(`<h3>${esc(title)}</h3><div class="sub" style="margin-top:10px;line-height:1.6">${text}</div>
      <div class="modal-actions"><button class="btn ghost" data-close-modal>取消</button><button class="btn danger" id="cOk">${esc(okLabel)}</button></div>`);
    $('#cOk').onclick = () => { closeModal(); fn(); };
  }

  // ---------------- delete with undo ----------------
  function snapshot() { return JSON.stringify(state); }
  function undoable(msg) {
    const snap = undoable.snap;
    toast(msg, { label: '撤销', fn: () => { state = JSON.parse(snap); save(); renderAll(); } });
  }
  function delIdentity(id) {
    undoable.snap = snapshot();
    state.identities = state.identities.filter(i => i.id !== id);
    state.accounts.forEach(a => a.identityIds = a.identityIds.filter(x => x !== id));
    save(); closeDrawer(); renderAll(); undoable('已删除身份');
  }
  function delAccount(id) {
    undoable.snap = snapshot();
    state.accounts = state.accounts.filter(a => a.id !== id);
    save(); closeDrawer(); renderAll(); undoable('已删除网站');
  }

  // ---------------- command palette ----------------
  let palSel = 0, palItems = [];
  function openPalette() {
    $('#paletteLayer').classList.add('show');
    const inp = $('#paletteInput'); inp.value = ''; renderPalette('');
    setTimeout(() => inp.focus(), 60);
  }
  const closePalette = () => $('#paletteLayer').classList.remove('show');
  function hl(text, q) {
    const s = esc(text); if (!q) return s;
    const i = text.toLowerCase().indexOf(q.toLowerCase());
    return i < 0 ? s : esc(text.slice(0, i)) + '<mark>' + esc(text.slice(i, i + q.length)) + '</mark>' + esc(text.slice(i + q.length));
  }
  function renderPalette(q) {
    q = q.trim();
    const ql = q.toLowerCase(), qd = ql.replace(/\D/g, '');
    const accs = state.accounts.filter(a => !q || a.name.toLowerCase().includes(ql) || (a.domain || '').includes(ql) || a.category.includes(q)).slice(0, 8);
    const ids = state.identities.filter(i => !q || i.value.toLowerCase().includes(ql) || (i.label || '').toLowerCase().includes(ql) || (qd.length >= 3 && i.value.replace(/\D/g, '').includes(qd))).slice(0, 6);
    palItems = [...accs.map(a => ({ k: 'a', id: a.id })), ...ids.map(i => ({ k: 'i', id: i.id }))];
    palSel = 0;
    let n = 0, html = '';
    if (accs.length) html += `<div class="pr-group">网站 / App</div>` + accs.map(a => {
      const who = a.identityIds.map(idById).filter(Boolean).map(i => idText(i)).join('、') || '未关联';
      return `<div class="pr" data-k="a" data-id="${a.id}" style="--i:${n++}">${favHTML(a)}<div class="pr-main"><div class="pr-title">${hl(a.name, q)}</div><div class="pr-sub">用 ${esc(who)}</div></div></div>`;
    }).join('');
    if (ids.length) html += `<div class="pr-group">邮箱 / 手机号</div>` + ids.map(i => {
      const c = accountsOf(i.id).length;
      return `<div class="pr" data-k="i" data-id="${i.id}" style="--i:${n++}"><div class="id-icon" style="--c:${i.color};width:32px;height:32px;border-radius:10px">${idGlyph(i)}</div><div class="pr-main"><div class="pr-title mono">${ui.privacy ? esc(idText(i)) : hl(i.value, q)}</div><div class="pr-sub">${esc(i.label || '')} · ${c} 个网站</div></div></div>`;
    }).join('');
    $('#paletteResults').innerHTML = html || `<div class="pr-empty">没找到 “${esc(q)}”<br><button class="btn primary" style="margin-top:14px" data-quick-add="${esc(q)}">把 “${esc(q)}” 添加为网站</button></div>`;
    markSel();
  }
  function markSel() { $$('.pr').forEach((el, i) => el.classList.toggle('sel', i === palSel)); $$('.pr')[palSel]?.scrollIntoView({ block: 'nearest' }); }
  function choosePal(k, id) { closePalette(); setTimeout(() => k === 'a' ? showAccount(id) : showIdentity(id), 120); }

  // ---------------- import / export ----------------
  function exportData() {
    const blob = new Blob([JSON.stringify({ app: 'atlas', version: 1, exportedAt: new Date().toISOString(), ...state }, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = `atlas-backup-${new Date().toISOString().slice(0, 10)}.json`; a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    toast('备份已导出');
  }
  function importData(file) {
    const r = new FileReader();
    r.onload = () => {
      try {
        const d = JSON.parse(r.result);
        if (!Array.isArray(d.identities) || !Array.isArray(d.accounts)) throw 0;
        undoable.snap = snapshot();
        state = { identities: d.identities, accounts: d.accounts };
        save(); renderAll(); undoable(`已导入 ${d.identities.length} 个身份、${d.accounts.length} 个网站`);
      } catch { toast('文件格式不对'); }
    };
    r.readAsText(file);
  }

  // ---------------- global events ----------------
  document.addEventListener('click', e => {
    const t = e.target.closest('button, .card, .row, .pr, [data-open]');
    const menu = $('#menu');
    if (!e.target.closest('.top-actions')) menu.classList.remove('open');
    if (!t) return;
    const d = t.dataset;

    if (t.id === 'menuBtn') { menu.classList.toggle('open'); return; }
    if (d.act) {
      menu.classList.remove('open');
      if (d.act === 'export') exportData();
      if (d.act === 'import') $('#fileInput').click();
      if (d.act === 'privacy') { ui.privacy = !ui.privacy; localStorage.setItem('atlas.privacy', ui.privacy ? '1' : '0'); syncPrivacyLabel(); renderAll(); refreshDrawer(); toast(ui.privacy ? '隐私模式已开启' : '隐私模式已关闭'); }
      if (d.act === 'demo') { undoable.snap = snapshot(); state = demo(); save(); renderAll(); undoable('已载入示例数据'); }
      if (d.act === 'wipe') confirmBox('清空全部数据？', '所有邮箱、手机号和网站记录都会被删除。建议先导出备份。', '清空', () => { undoable.snap = snapshot(); state = { identities: [], accounts: [] }; save(); renderAll(); undoable('已清空'); });
      return;
    }
    if (t.classList.contains('tab')) { setTab(d.tab); return; }
    if (t.closest('#idFilter') && d.f) { ui.idFilter = d.f; $$('#idFilter button').forEach(b => b.classList.toggle('active', b === t)); renderIdentities(); return; }
    if (d.cat) { ui.cat = d.cat; renderAccounts(); return; }
    if (d.open === 'identity') { identityForm(); return; }
    if (d.open === 'account') { accountForm(null, d.presetId); return; }
    if (d.closeModal !== undefined) { closeModal(); return; }
    if (d.closeDrawer !== undefined) { closeDrawer(); return; }
    if (d.copy) { navigator.clipboard?.writeText(d.copy).then(() => toast('已复制')); return; }
    if (d.editIdentity) { identityForm(idById(d.editIdentity)); return; }
    if (d.editAccount) { accountForm(accById(d.editAccount)); return; }
    if (d.delIdentity) {
      const i = idById(d.delIdentity), n = accountsOf(i.id).length;
      confirmBox('删除这个身份？', `<span class="mono">${esc(i.value)}</span> 会从 ${n} 个网站的关联中移除，网站本身会保留。`, '删除', () => delIdentity(i.id));
      return;
    }
    if (d.delAccount) { delAccount(d.delAccount); return; }
    if (d.quickAdd !== undefined) { closePalette(); setTimeout(() => { accountForm(); $('#aName').value = d.quickAdd; $('#aName').dispatchEvent(new Event('input')); }, 150); return; }
    if (t.classList.contains('pr')) { choosePal(d.k, d.id); return; }
    if (d.idOpen) { showIdentity(d.idOpen); return; }
    if (t.classList.contains('card') && d.id) { showIdentity(d.id); return; }
    if (t.classList.contains('row') && d.acc) { showAccount(d.acc); return; }
  });

  function syncPrivacyLabel() {
    let b = $('#menu [data-act="privacy"]');
    if (!b) { b = document.createElement('button'); b.dataset.act = 'privacy'; $('#menu').insertBefore(b, $('#menu hr')); }
    b.textContent = ui.privacy ? '关闭隐私模式' : '开启隐私模式（打码显示）';
  }

  $('#scrim').onclick = closeDrawer;
  $('#modalLayer').addEventListener('mousedown', e => { if (e.target.id === 'modalLayer') closeModal(); });
  $('#paletteLayer').addEventListener('mousedown', e => { if (e.target.id === 'paletteLayer') closePalette(); });
  $('#openPalette').onclick = openPalette;
  $('#fileInput').onchange = e => { const f = e.target.files[0]; if (f) importData(f); e.target.value = ''; };
  $('#paletteInput').addEventListener('input', e => renderPalette(e.target.value));
  $('#paletteInput').addEventListener('keydown', e => {
    if (e.key === 'ArrowDown') { palSel = Math.min(palItems.length - 1, palSel + 1); markSel(); e.preventDefault(); }
    if (e.key === 'ArrowUp') { palSel = Math.max(0, palSel - 1); markSel(); e.preventDefault(); }
    if (e.key === 'Enter') { const it = palItems[palSel]; if (it) choosePal(it.k, it.id); else $('[data-quick-add]')?.click(); }
  });
  document.addEventListener('keydown', e => {
    const typing = /INPUT|TEXTAREA/.test(document.activeElement?.tagName);
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); openPalette(); }
    else if (e.key === '/' && !typing) { e.preventDefault(); openPalette(); }
    else if (e.key === 'Escape') {
      if ($('#paletteLayer').classList.contains('show')) closePalette();
      else if ($('#modalLayer').classList.contains('show')) closeModal();
      else closeDrawer();
    } else if (e.key === 'Enter' && (e.metaKey || e.ctrlKey) && $('#modalLayer').classList.contains('show')) {
      ($('#fSave') || $('#aSave'))?.click();
    }
  });
  window.addEventListener('pointermove', e => {
    document.documentElement.style.setProperty('--mx', e.clientX + 'px');
    document.documentElement.style.setProperty('--my', e.clientY + 'px');
  }, { passive: true });
  window.addEventListener('resize', debounce(() => { moveIndicator(); Stars.resize(); Graph.resize(); }, 120));

  // ---------------- starfield ----------------
  const Stars = (() => {
    const c = $('#stars'), ctx = c.getContext('2d');
    let stars = [], w, h, dpr;
    function resize() {
      dpr = Math.min(2, devicePixelRatio || 1); w = innerWidth; h = innerHeight;
      c.width = w * dpr; c.height = h * dpr; c.style.width = w + 'px'; c.style.height = h + 'px';
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      stars = Array.from({ length: Math.round(w * h / 9000) }, () => ({ x: Math.random() * w, y: Math.random() * h, r: Math.random() * 1.1 + .2, p: Math.random() * 6.28, s: Math.random() * .02 + .005, v: Math.random() * .08 + .01 }));
    }
    function frame() {
      ctx.clearRect(0, 0, w, h);
      for (const s of stars) {
        s.p += s.s; s.y -= s.v; if (s.y < -2) { s.y = h + 2; s.x = Math.random() * w; }
        ctx.globalAlpha = .25 + Math.sin(s.p) * .25 + .25;
        ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(s.x, s.y, s.r, 0, 6.283); ctx.fill();
      }
      requestAnimationFrame(frame);
    }
    resize();
    if (!matchMedia('(prefers-reduced-motion: reduce)').matches) requestAnimationFrame(frame);
    return { resize };
  })();

  // ---------------- force graph ----------------
  const Graph = (() => {
    const c = $('#graph'), ctx = c.getContext('2d'), tip = $('#graphTip');
    let nodes = [], edges = [], running = false, w = 0, h = 0, dpr = 1, hover = null, drag = null, t = 0, alpha = 1;
    const imgCache = {};
    function img(domain) {
      const d = cleanDomain(domain); if (!d) return null;
      if (imgCache[d]) return imgCache[d];
      const im = new Image();
      const srcs = favSources(d); let k = 0;
      im.onerror = () => { k++; if (k < srcs.length) im.src = srcs[k]; else im.dead = true; };
      im.src = srcs[0]; imgCache[d] = im; return im;
    }
    function resize() {
      const r = c.getBoundingClientRect(); if (!r.width) return;
      dpr = Math.min(2, devicePixelRatio || 1); w = r.width; h = r.height;
      c.width = w * dpr; c.height = h * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    function rebuild() {
      resize();
      const old = Object.fromEntries(nodes.map(n => [n.id, n]));
      const cx = w / 2, cy = h / 2;
      const idN = state.identities.map((i, k) => {
        const a = (k / Math.max(1, state.identities.length)) * Math.PI * 2;
        return old[i.id] || { id: i.id, kind: 'i', ref: i, x: cx + Math.cos(a) * 120, y: cy + Math.sin(a) * 120, vx: 0, vy: 0 };
      });
      idN.forEach(n => { n.ref = idById(n.id); n.r = 15 + Math.min(10, accountsOf(n.id).length * 1.2); n.color = n.ref.color; });
      const accN = state.accounts.map(a => old[a.id] || { id: a.id, kind: 'a', ref: a, x: cx + (Math.random() - .5) * 300, y: cy + (Math.random() - .5) * 300, vx: 0, vy: 0 });
      accN.forEach(n => { n.ref = accById(n.id); n.r = 11; n.img = img(n.ref.domain); });
      nodes = [...idN, ...accN];
      const map = Object.fromEntries(nodes.map(n => [n.id, n]));
      edges = [];
      state.accounts.forEach(a => a.identityIds.forEach(iid => map[iid] && edges.push({ s: map[iid], t: map[a.id], off: Math.random() })));
      alpha = 1;
    }
    function tick() {
      const cx = w / 2, cy = h / 2;
      alpha = Math.max(.02, alpha * .992);
      for (let i = 0; i < nodes.length; i++) {
        const a = nodes[i];
        for (let j = i + 1; j < nodes.length; j++) {
          const b = nodes[j];
          let dx = b.x - a.x, dy = b.y - a.y, d2 = dx * dx + dy * dy || 1;
          const rep = (a.kind === 'i' && b.kind === 'i' ? 16000 : 3200) / d2;
          const d = Math.sqrt(d2); dx /= d; dy /= d;
          a.vx -= dx * rep * alpha; a.vy -= dy * rep * alpha; b.vx += dx * rep * alpha; b.vy += dy * rep * alpha;
        }
        a.vx += (cx - a.x) * .0018 * alpha * (a.kind === 'i' ? 2 : 1);
        a.vy += (cy - a.y) * .0018 * alpha * (a.kind === 'i' ? 2 : 1);
      }
      for (const e of edges) {
        const dx = e.t.x - e.s.x, dy = e.t.y - e.s.y, d = Math.hypot(dx, dy) || 1;
        const f = (d - 125) * .02 * alpha;
        e.s.vx += dx / d * f; e.s.vy += dy / d * f; e.t.vx -= dx / d * f; e.t.vy -= dy / d * f;
      }
      for (const n of nodes) {
        if (n === drag) { n.vx = n.vy = 0; continue; }
        n.vx *= .82; n.vy *= .82; n.x += n.vx; n.y += n.vy;
        n.x = Math.max(n.r + 4, Math.min(w - n.r - 4, n.x)); n.y = Math.max(n.r + 4, Math.min(h - n.r - 30, n.y));
      }
    }
    const linked = (n, m) => edges.some(e => (e.s === n && e.t === m) || (e.t === n && e.s === m));
    function draw() {
      ctx.clearRect(0, 0, w, h);
      t += 1;
      const focus = hover;
      for (const e of edges) {
        const on = !focus || e.s === focus || e.t === focus;
        const g = ctx.createLinearGradient(e.s.x, e.s.y, e.t.x, e.t.y);
        g.addColorStop(0, e.s.color + (on ? 'cc' : '22')); g.addColorStop(1, 'rgba(255,255,255,' + (on ? .25 : .04) + ')');
        ctx.strokeStyle = g; ctx.lineWidth = on && focus ? 1.8 : 1;
        ctx.beginPath(); ctx.moveTo(e.s.x, e.s.y); ctx.lineTo(e.t.x, e.t.y); ctx.stroke();
        if (on) { // flowing particle
          const p = ((t * .006) + e.off) % 1;
          const x = e.s.x + (e.t.x - e.s.x) * p, y = e.s.y + (e.t.y - e.s.y) * p;
          ctx.fillStyle = e.s.color; ctx.shadowColor = e.s.color; ctx.shadowBlur = 10;
          ctx.beginPath(); ctx.arc(x, y, 1.8, 0, 6.283); ctx.fill(); ctx.shadowBlur = 0;
        }
      }
      for (const n of nodes) {
        const dim = focus && n !== focus && !linked(n, focus);
        ctx.globalAlpha = dim ? .18 : 1;
        if (n.kind === 'i') {
          const pulse = 1 + Math.sin(t * .04 + n.x) * .06;
          ctx.shadowColor = n.color; ctx.shadowBlur = 28 * pulse;
          const g = ctx.createRadialGradient(n.x - n.r * .3, n.y - n.r * .3, 1, n.x, n.y, n.r);
          g.addColorStop(0, '#fff'); g.addColorStop(.25, n.color); g.addColorStop(1, n.color + '88');
          ctx.fillStyle = g; ctx.beginPath(); ctx.arc(n.x, n.y, n.r, 0, 6.283); ctx.fill(); ctx.shadowBlur = 0;
          ctx.strokeStyle = n.color + '55'; ctx.lineWidth = 1;
          ctx.beginPath(); ctx.arc(n.x, n.y, n.r + 6 + Math.sin(t * .05) * 2, 0, 6.283); ctx.stroke();
          ctx.fillStyle = '#e9e7f5'; ctx.font = '600 11.5px Inter, PingFang SC, sans-serif'; ctx.textAlign = 'center';
          ctx.fillText(n.ref.label || idText(n.ref), n.x, n.y + n.r + 18);
        } else {
          ctx.fillStyle = '#fff'; ctx.shadowColor = 'rgba(0,0,0,.6)'; ctx.shadowBlur = 8;
          ctx.beginPath(); ctx.arc(n.x, n.y, n.r, 0, 6.283); ctx.fill(); ctx.shadowBlur = 0;
          if (n.img && n.img.complete && n.img.naturalWidth && !n.img.dead) {
            ctx.save(); ctx.beginPath(); ctx.arc(n.x, n.y, n.r - 2.5, 0, 6.283); ctx.clip();
            try { ctx.drawImage(n.img, n.x - n.r + 3, n.y - n.r + 3, (n.r - 3) * 2, (n.r - 3) * 2); } catch { }
            ctx.restore();
          } else {
            ctx.fillStyle = '#4c1d95'; ctx.font = '700 11px Inter, sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
            ctx.fillText((n.ref.name || '?').slice(0, 1), n.x, n.y + .5); ctx.textBaseline = 'alphabetic';
          }
          if (focus === n || (focus && linked(n, focus))) {
            ctx.fillStyle = '#cfcde0'; ctx.font = '500 11px Inter, PingFang SC, sans-serif'; ctx.textAlign = 'center';
            ctx.fillText(n.ref.name, n.x, n.y + n.r + 14);
          }
        }
        ctx.globalAlpha = 1;
      }
    }
    function loop() { if (!running) return; tick(); draw(); requestAnimationFrame(loop); }
    function at(x, y) { for (let i = nodes.length - 1; i >= 0; i--) { const n = nodes[i]; if (Math.hypot(n.x - x, n.y - y) < n.r + 4) return n; } return null; }
    const pos = e => { const r = c.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };
    let moved = false;
    c.addEventListener('pointerdown', e => { const [x, y] = pos(e); drag = at(x, y); moved = false; if (drag) { c.setPointerCapture(e.pointerId); c.classList.add('dragging'); } });
    c.addEventListener('pointermove', e => {
      const [x, y] = pos(e);
      if (drag) { drag.x = x; drag.y = y; moved = true; alpha = Math.max(alpha, .3); }
      const n = drag || at(x, y); hover = n;
      if (n) {
        const label = n.kind === 'i' ? `${idText(n.ref)} · ${accountsOf(n.id).length} 个网站` : `${n.ref.name} · ${n.ref.identityIds.length} 个身份`;
        tip.textContent = label; tip.style.left = Math.min(w - 10 - tip.offsetWidth, x + 14) + 'px'; tip.style.top = (y - 38) + 'px'; tip.classList.add('show');
      } else tip.classList.remove('show');
    });
    c.addEventListener('pointerup', () => {
      c.classList.remove('dragging');
      if (drag && !moved) drag.kind === 'i' ? showIdentity(drag.id) : showAccount(drag.id);
      drag = null;
    });
    c.addEventListener('pointerleave', () => { hover = null; tip.classList.remove('show'); });
    return {
      start() { requestAnimationFrame(() => { rebuild(); if (!running) { running = true; loop(); } }); },
      stop() { running = false; },
      rebuild, resize() { if (running) resize(); },
    };
  })();

  // ---------------- boot ----------------
  if (!state) { state = demo(); save(); setTimeout(() => toast('已载入示例数据，可在右上角菜单清空', { label: '清空', fn: () => { state = { identities: [], accounts: [] }; save(); renderAll(); } }), 1200); }
  syncPrivacyLabel();
  renderAll();
  requestAnimationFrame(moveIndicator);
  document.fonts?.ready.then(moveIndicator);
})();
