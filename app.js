/* Atlas v3 — local-first account relationship manager */
(() => {
  'use strict';

  const KEY = 'atlas.v1';
  const COLORS = ['#0a84ff', '#30d158', '#ff9f0a', '#ff375f', '#bf5af2', '#64d2ff', '#ff453a', '#5e5ce6'];
  const CATS = ['社交', '购物', '支付金融', '开发', 'AI', '娱乐', '游戏', '出行', '工具', '加密', '其他'];
  // name, domain, category, brand colour
  const PRESETS = [
    ['微信', 'weixin.qq.com', '社交', '#07c160'], ['QQ', 'qq.com', '社交', '#12b7f5'], ['微博', 'weibo.com', '社交', '#e6162d'], ['小红书', 'xiaohongshu.com', '社交', '#ff2442'],
    ['知乎', 'zhihu.com', '社交', '#0066ff'], ['抖音', 'douyin.com', '娱乐', '#fe2c55'], ['哔哩哔哩', 'bilibili.com', '娱乐', '#fb7299'], ['网易云音乐', 'music.163.com', '娱乐', '#e60026'],
    ['淘宝', 'taobao.com', '购物', '#ff5000'], ['京东', 'jd.com', '购物', '#e1251b'], ['拼多多', 'pinduoduo.com', '购物', '#e02e24'], ['闲鱼', 'goofish.com', '购物', '#ffda44'],
    ['美团', 'meituan.com', '出行', '#ffc300'], ['携程', 'ctrip.com', '出行', '#2577e3'], ['12306', '12306.cn', '出行', '#3b6fd8'], ['高德地图', 'amap.com', '出行', '#0091ff'],
    ['支付宝', 'alipay.com', '支付金融', '#1677ff'], ['百度', 'baidu.com', '工具', '#2932e1'], ['GitHub', 'github.com', '开发', '#6e40c9'], ['Gitee', 'gitee.com', '开发', '#c71d23'],
    ['Cloudflare', 'cloudflare.com', '开发', '#f38020'], ['Vercel', 'vercel.com', '开发', '#555555'], ['Google', 'google.com', '工具', '#4285f4'], ['Apple ID', 'apple.com', '工具', '#8e8e93'],
    ['Microsoft', 'microsoft.com', '工具', '#00a4ef'], ['Notion', 'notion.so', '工具', '#6b6b6b'], ['Figma', 'figma.com', '工具', '#a259ff'], ['ChatGPT', 'chatgpt.com', 'AI', '#10a37f'],
    ['Claude', 'claude.ai', 'AI', '#d97757'], ['Gemini', 'gemini.google.com', 'AI', '#4796e3'], ['DeepSeek', 'deepseek.com', 'AI', '#4d6bfe'], ['Kimi', 'kimi.com', 'AI', '#1f1f1f'],
    ['Steam', 'steampowered.com', '游戏', '#1b2838'], ['Epic Games', 'epicgames.com', '游戏', '#313131'], ['米哈游', 'mihoyo.com', '游戏', '#2aa4f4'], ['Discord', 'discord.com', '社交', '#5865f2'],
    ['Telegram', 'telegram.org', '社交', '#26a5e4'], ['X', 'x.com', '社交', '#3a3a3a'], ['Instagram', 'instagram.com', '社交', '#e1306c'], ['Netflix', 'netflix.com', '娱乐', '#e50914'],
    ['Spotify', 'spotify.com', '娱乐', '#1db954'], ['YouTube', 'youtube.com', '娱乐', '#ff0000'], ['Binance', 'binance.com', '加密', '#f0b90b'], ['OKX', 'okx.com', '加密', '#3a3a3a'],
    ['MetaMask', 'metamask.io', '加密', '#f6851b'], ['PayPal', 'paypal.com', '支付金融', '#003087'], ['Amazon', 'amazon.com', '购物', '#ff9900'],
  ];

  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);
  const esc = (s = '') => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const zh = (a, b) => a.localeCompare(b, 'zh-CN');
  const DAY = 864e5;

  // ---------- state ----------
  let state = (() => { try { return JSON.parse(localStorage.getItem(KEY)); } catch { return null; } })();
  const ui = { tab: 'home', cat: '全部', privacy: localStorage.getItem('atlas.privacy') === '1', active: 0 };
  const save = () => localStorage.setItem(KEY, JSON.stringify(state));

  function demo() {
    const ids = [
      { id: uid(), type: 'email', value: 'me@gmail.com', label: '主力邮箱', color: COLORS[0], notes: '' },
      { id: uid(), type: 'phone', value: '+86 138 0000 1234', label: '主力手机号', color: COLORS[1], notes: '移动，已实名' },
      { id: uid(), type: 'email', value: 'shopping.fun@qq.com', label: '购物小号', color: COLORS[3], notes: '' },
      { id: uid(), type: 'email', value: 'dev@outlook.com', label: '开发专用', color: COLORS[4], notes: '' },
      { id: uid(), type: 'phone', value: '+86 170 0000 5678', label: '副卡', color: COLORS[2], notes: '' },
    ];
    const [g, p1, q, o, p2] = ids.map(i => i.id);
    const now = Date.now();
    const mk = (name, domain, category, identityIds, ago, notes = '') => ({ id: uid(), name, domain, category, identityIds, notes, createdAt: now - ago * DAY - Math.random() * 3e6 });
    return {
      identities: ids,
      accounts: [
        mk('GitHub', 'github.com', '开发', [o, p1], 0, '已开两步验证'), mk('Claude', 'claude.ai', 'AI', [g], 0),
        mk('Cloudflare', 'cloudflare.com', '开发', [o], 1), mk('ChatGPT', 'chatgpt.com', 'AI', [g], 1),
        mk('小红书', 'xiaohongshu.com', '社交', [p2], 3), mk('Binance', 'binance.com', '加密', [o, p1], 3, '已完成 KYC'),
        mk('淘宝', 'taobao.com', '购物', [q, p1], 9), mk('京东', 'jd.com', '购物', [q, p2], 12),
        mk('微信', 'weixin.qq.com', '社交', [p1], 30), mk('支付宝', 'alipay.com', '支付金融', [p1], 30),
        mk('哔哩哔哩', 'bilibili.com', '娱乐', [p2], 40), mk('Steam', 'steampowered.com', '游戏', [q], 60, '令牌在主力手机上'),
        mk('Apple ID', 'apple.com', '工具', [g, p1], 90), mk('Notion', 'notion.so', '工具', [g], 120), mk('网易云音乐', 'music.163.com', '娱乐', [p2], 150),
      ],
    };
  }

  const idById = id => state.identities.find(i => i.id === id);
  const accById = id => state.accounts.find(a => a.id === id);
  const accountsOf = id => state.accounts.filter(a => a.identityIds.includes(id)).sort((a, b) => zh(a.name, b.name));

  function display(i) {
    const v = i.value;
    if (!ui.privacy) return v;
    if (i.type === 'email') { const [u, d = ''] = v.split('@'); return u.slice(0, 2) + '•••' + (d ? '@' + d : ''); }
    const digits = v.replace(/\D/g, '');
    return digits.length > 7 ? '••• •••• ' + digits.slice(-4) : '••••';
  }
  const cleanDomain = d => (d || '').trim().toLowerCase().replace(/^https?:\/\//, '').replace(/^www\./, '').split(/[/?#]/)[0];
  const favSources = d => { d = cleanDomain(d); return d ? [`https://favicon.im/${d}?larger=true`, `https://${d}/apple-touch-icon.png`, `https://${d}/favicon.ico`, `https://www.google.com/s2/favicons?domain=${d}&sz=128`] : []; };
  function brand(a) {
    const d = cleanDomain(a.domain), n = (a.name || '').toLowerCase();
    const p = PRESETS.find(p => (d && (d === p[1] || d.endsWith('.' + p[1]))) || p[0].toLowerCase() === n);
    if (p) return p[3];
    let h = 0; for (const ch of (d || n || '?')) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    return `hsl(${h % 360} 62% 48%)`;
  }

  // ---------- icons ----------
  const ICON = {
    email: '<svg viewBox="0 0 24 24"><rect x="3" y="5.5" width="18" height="13" rx="2.5"/><path d="m4 7.5 8 5.5 8-5.5"/></svg>',
    phone: '<svg viewBox="0 0 24 24"><rect x="7" y="2.5" width="10" height="19" rx="2.6"/><path d="M10.5 5h3"/></svg>',
    chev: '<svg class="chev" viewBox="0 0 9 15"><path d="M1.5 1.5 7.5 7.5l-6 6"/></svg>',
    plus: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
    tick: '<svg class="tick" viewBox="0 0 24 24" style="stroke-width:2.4"><path d="m5 12.5 4.5 4.5L19 7.5"/></svg>',
    edit: '<svg viewBox="0 0 24 24"><path d="M4 20h4L19 9l-4-4L4 16v4Z"/><path d="m13.5 6.5 4 4"/></svg>',
    copy: '<svg viewBox="0 0 24 24"><rect x="8" y="8" width="12" height="12" rx="2.5"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/></svg>',
    trash: '<svg viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>',
    open: '<svg viewBox="0 0 24 24"><path d="M14 4h6v6M20 4l-9 9M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4"/></svg>',
    x: '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18"/></svg>',
    check: '<svg viewBox="0 0 24 24"><path d="m5 12.5 4.5 4.5L19 7.5"/></svg>',
  };
  const idIcon = (i, size = '') => `<span class="icon ${size}" style="--c:${i.color}">${ICON[i.type]}</span>`;
  function favIcon(a, size = '') {
    const src = favSources(a.domain), letter = esc((a.name || '?').trim().slice(0, 1).toUpperCase());
    const b = brand(a), style = `style="--lc1:color-mix(in srgb,${b} 78%,#fff);--lc2:${b}"`;
    if (!src.length) return `<span class="icon fav letter ${size}" ${style}>${letter}</span>`;
    return `<span class="icon fav ${size}" ${style}><img alt="" loading="lazy" src="${src[0]}" data-rest='${JSON.stringify(src.slice(1))}' data-letter="${letter}" onerror="AtlasFav(this)"></span>`;
  }
  window.AtlasFav = img => {
    const rest = JSON.parse(img.dataset.rest || '[]');
    if (rest.length) { img.dataset.rest = JSON.stringify(rest.slice(1)); img.src = rest[0]; return; }
    const box = img.parentElement; if (!box) return; box.classList.add('letter'); box.textContent = img.dataset.letter;
  };
  document.addEventListener('load', e => {
    const im = e.target;
    if (im.tagName === 'IMG' && im.dataset.rest !== undefined && im.naturalWidth && im.naturalWidth < 12) window.AtlasFav(im);
  }, true);
  // blurred "poster" made of the brand colours (and icons, when they load) of a set of accounts
  function poster(accs, n, cls = 'poster') {
    const list = accs.length ? accs : [];
    const tiles = [];
    for (let k = 0; k < n; k++) {
      const a = list[k % Math.max(1, list.length)];
      if (!a) { tiles.push('<b style="--pc:transparent"></b>'); continue; }
      const src = favSources(a.domain)[0];
      tiles.push(`<b style="--pc:${brand(a)};position:relative">${src && k < list.length ? `<img alt="" src="${src}" onerror="this.remove()" style="position:absolute;inset:18%;width:64%;height:64%;object-fit:contain">` : ''}</b>`);
    }
    return `<div class="${cls}" aria-hidden="true">${tiles.join('')}</div>`;
  }
  const caps = ids => ids.map(idById).filter(Boolean).map(i => `<span class="cap" style="--c:${i.color}"><i></i>${esc(i.label || display(i))}</span>`).join('');

  // ---------- toast (notification banner) ----------
  let toastTimer;
  function toast(msg, action) {
    const t = $('#toast');
    t.innerHTML = `<span class="ti">${ICON.check}</span><span class="tt">${esc(msg)}</span>${action ? `<button>${esc(action.label)}</button>` : ''}`;
    if (action) t.querySelector('button').onclick = () => { action.fn(); t.classList.remove('show'); };
    t.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), action ? 4500 : 2200);
  }
  let undoSnap = null;
  const snap = () => { undoSnap = JSON.stringify(state); };
  const undoToast = msg => toast(msg, { label: '撤销', fn: () => { state = JSON.parse(undoSnap); save(); render(); } });

  // ---------- wallet card ----------
  function walletCard(i, extra = '') {
    const accs = accountsOf(i.id), n = accs.length;
    const stack = accs.slice(0, 4).map(a => favIcon(a)).join('');
    return `<button class="wcard" ${extra} data-identity="${i.id}" style="--c:${i.color}">
      ${poster(accs.slice().sort((a, b) => b.createdAt - a.createdAt), 9)}
      <span class="sheen"></span>
      <span class="wc-top"><span class="wc-kind">${ICON[i.type]}${esc(i.label || (i.type === 'email' ? '邮箱' : '手机号'))}</span>
        <span class="wc-count"><b>${n}</b><small>个网站与 App</small></span></span>
      <span class="wc-value">${esc(display(i))}</span>
      <span class="wc-foot"><span class="wc-note">${esc(i.notes || (i.type === 'email' ? '邮箱' : '手机号'))}</span>
        <span class="stack">${n > 4 ? `<span class="more">+${n - 4}</span>` : ''}${stack}</span></span>
    </button>`;
  }
  // order the stack so the first app sits on top (row-reverse)
  const fixStack = root => $$('.stack', root).forEach(s => { const els = [...s.children]; const more = els.find(e => e.classList.contains('more')); const icons = els.filter(e => e !== more); s.innerHTML = ''; if (more) s.append(more); icons.reverse().forEach(e => s.append(e)); });

  // ---------- render ----------
  function renderSummary() {
    const e = state.identities.filter(i => i.type === 'email').length, p = state.identities.length - e, a = state.accounts.length;
    $('#summary').textContent = state.identities.length || a ? `${e} 个邮箱、${p} 个手机号，关联着 ${a} 个网站与 App` : '先添加一个邮箱或手机号';
    $('#sitesSummary').textContent = a ? `共 ${a} 个` : '';
  }

  function renderWallet() {
    const w = $('#wallet');
    w.innerHTML = state.identities.map(i => walletCard(i)).join('') +
      `<button class="wcard ghost" data-new="identity"><div>${ICON.plus}添加邮箱或手机号</div></button>`;
    fixStack(w);
    $('#dots').innerHTML = [...state.identities, 0].map((_, k) => `<i class="${k === ui.active ? 'on' : ''}"></i>`).join('');
    ui.active = Math.min(ui.active, state.identities.length);
    requestAnimationFrame(() => {
      const cards = $$('.wcard', w), c = cards[ui.active];
      if (c) w.scrollLeft = cardX(w, c);
      coverflow();
    });
  }

  const cardX = (w, c) => c.offsetLeft - w.offsetLeft - (w.clientWidth - c.offsetWidth) / 2;
  function centerCard(w, c) { w.scrollTo({ left: cardX(w, c), behavior: 'smooth' }); }
  let rafCF = 0;
  function coverflow() {
    rafCF = 0;
    const w = $('#wallet'); if (!w.offsetParent) return;
    const mid = w.scrollLeft + w.offsetLeft + w.clientWidth / 2;
    let best = 0, bestD = 1e9;
    $$('.wcard', w).forEach((c, k) => {
      const d = (c.offsetLeft + c.offsetWidth / 2 - mid) / c.offsetWidth;
      const ad = Math.min(1.6, Math.abs(d));
      c.style.transform = `perspective(1100px) rotateY(${Math.max(-1.6, Math.min(1.6, d)) * -16}deg) scale(${1 - ad * .12})`;
      c.style.opacity = 1 - ad * .35;
      c.style.setProperty('--sx', `${-60 + d * -120}%`);
      if (Math.abs(d) < bestD) { bestD = Math.abs(d); best = k; }
    });
    if (best !== ui.active) { ui.active = best; onActiveChange(); }
  }
  function onActiveChange() {
    $$('#dots i').forEach((d, k) => d.classList.toggle('on', k === ui.active));
    const i = state.identities[ui.active];
    document.documentElement.style.setProperty('--accent', i ? i.color : '#3a3a3c');
    animateGlow(i ? i.color : '#3a3a3c');
    renderGrid();
  }
  let glowColor = null;
  function animateGlow(col) {
    const g = $('#glow');
    if (glowColor === col) return; glowColor = col;
    g.animate([{ opacity: .35 }, { opacity: .75 }], { duration: 600, easing: 'ease-out' });
  }

  function renderGrid() {
    const i = state.identities[ui.active];
    const g = $('#grid');
    $('#gridTitle').textContent = i ? `用${i.label || display(i)}的` : '全部网站与 App';
    const list = i ? accountsOf(i.id) : state.accounts.slice().sort((a, b) => zh(a.name, b.name));
    // FLIP: remember where each icon was
    const before = {};
    $$('.app[data-account]', g).forEach(el => before[el.dataset.account] = el.getBoundingClientRect());
    g.innerHTML = list.map(a => `<button class="app" data-account="${a.id}">${favIcon(a)}<span>${esc(a.name)}</span></button>`).join('') +
      (i ? `<button class="app add" data-new="account" data-preset="${i.id}"><span class="icon">${ICON.plus}</span><span>添加</span></button>` : '') +
      (!list.length && !i ? '<div class="grid-empty">还没有网站，从右上角的 + 开始。</div>' : '');
    let k = 0;
    $$('.app', g).forEach(el => {
      const r0 = before[el.dataset.account];
      if (r0) {
        const r1 = el.getBoundingClientRect(), dx = r0.left - r1.left, dy = r0.top - r1.top;
        if (dx || dy) el.animate([{ transform: `translate(${dx}px,${dy}px)` }, { transform: 'none' }], { duration: 520, easing: 'cubic-bezier(.32,.72,0,1)' });
      } else {
        el.animate([{ opacity: 0, transform: 'scale(.4)' }, { opacity: 1, transform: 'none' }], { duration: 480, delay: Math.min(k++, 12) * 22, easing: 'cubic-bezier(.34,1.45,.64,1)', fill: 'backwards' });
      }
    });
  }

  function dayLabel(t) {
    const d0 = new Date(); d0.setHours(0, 0, 0, 0);
    const d = new Date(t); d.setHours(0, 0, 0, 0);
    const diff = Math.round((d0 - d) / DAY);
    if (diff === 0) return ['', '今天', true];
    if (diff === 1) return ['', '昨天', true];
    return [`${d.getMonth() + 1}月`, String(d.getDate()), false];
  }
  function reel(a, meta = '') {
    return `<button class="reel" data-account="${a.id}">
      ${poster([a], 1, 'blur')}${favIcon(a)}
      <span class="reel-main"><span class="reel-title">${esc(a.name)}</span><span class="caps">${caps(a.identityIds) || '<span class="cap dim">未关联</span>'}</span></span>
      ${meta ? `<span class="reel-meta">${meta}</span>` : ICON.chev}
    </button>`;
  }
  function renderTimeline() {
    const t = $('#timeline');
    const list = state.accounts.slice().sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0)).slice(0, 6);
    if (!list.length) { t.innerHTML = '<div class="grid-empty">添加的网站会按时间出现在这里。</div>'; return; }
    const groups = [];
    list.forEach(a => { const key = new Date(a.createdAt).toDateString(); const g = groups.find(g => g.key === key); g ? g.items.push(a) : groups.push({ key, t: a.createdAt, items: [a] }); });
    t.innerHTML = groups.map(g => { const [m, d, today] = dayLabel(g.t); return `<div class="day"><div class="day-date ${today ? 'today' : ''}"><small>${m}</small><b>${d}</b></div><div class="day-items">${g.items.map(a => reel(a, a.category)).join('')}</div></div>`; }).join('');
  }

  function renderSites() {
    const v = $('#siteList');
    if (!state.accounts.length) { v.innerHTML = `<div class="empty"><h3>还没有网站</h3><p>记下一个网站或 App，选上它用的是哪个邮箱、哪个手机号。</p><button class="btn-filled" data-new="account">添加网站或 App</button></div>`; $('#filters').innerHTML = ''; return; }
    const counts = {};
    state.accounts.forEach(a => counts[a.category] = (counts[a.category] || 0) + 1);
    const cats = CATS.filter(c => counts[c]);
    if (ui.cat !== '全部' && !counts[ui.cat]) ui.cat = '全部';
    $('#filters').innerHTML = ['全部', ...cats].map(c => `<button class="${c === ui.cat ? 'active' : ''}" data-cat="${c}">${c}</button>`).join('');
    const shown = ui.cat === '全部' ? cats : [ui.cat];
    v.innerHTML = shown.map(c => {
      const list = state.accounts.filter(a => a.category === c).sort((a, b) => zh(a.name, b.name));
      return `<div class="cat-title">${c}<small>${list.length}</small></div><div class="cat-list">${list.map(a => reel(a)).join('')}</div>`;
    }).join('');
    $$('#siteList .reel').forEach((el, k) => el.animate([{ opacity: 0, transform: 'translateY(10px)' }, { opacity: 1, transform: 'none' }], { duration: 420, delay: Math.min(k, 10) * 25, easing: 'cubic-bezier(.32,.72,0,1)', fill: 'backwards' }));
  }

  function render() {
    renderSummary(); renderWallet(); renderGrid(); renderTimeline(); renderSites();
    $('#menu [data-act="privacy"] span').textContent = ui.privacy ? '显示完整号码' : '隐藏号码';
    if (ui.tab === 'graph') Graph.rebuild();
  }

  // ---------- tabs ----------
  const TABS = ['home', 'sites', 'graph'];
  function setTab(tab) {
    if (tab === ui.tab) { scrollTo({ top: 0, behavior: 'smooth' }); return; }
    ui.tab = tab;
    const idx = TABS.indexOf(tab);
    $$('#tabbar button').forEach((b, k) => b.classList.toggle('active', k === idx));
    $('#tabThumb').style.transform = `translateX(${idx * 100}%)`;
    $$('.view').forEach(v => v.classList.toggle('active', v.dataset.view === tab));
    $('#topTitle').textContent = { home: '账号', sites: '网站与 App', graph: '关系图' }[tab];
    scrollTo(0, 0);
    tab === 'graph' ? Graph.start() : Graph.stop();
    if (tab === 'home') requestAnimationFrame(coverflow);
  }

  // ---------- sheet ----------
  const sheet = $('#sheet'), inner = $('#sheetInner');
  let sheetCtx = null;
  function openSheet(html, ctx = null) {
    const wasOpen = sheet.classList.contains('open');
    sheetCtx = ctx;
    inner.innerHTML = html;
    fixStack(inner);
    if (wasOpen) {
      inner.animate([{ opacity: 0, transform: 'translateX(18px)' }, { opacity: 1, transform: 'none' }], { duration: 320, easing: 'cubic-bezier(.32,.72,0,1)' });
    } else {
      sheet.style.transform = '';
      requestAnimationFrame(() => { sheet.classList.add('open'); $('#scrim').classList.add('show'); });
    }
    document.body.style.overflow = 'hidden';
  }
  function closeSheet() {
    sheet.classList.remove('open'); $('#scrim').classList.remove('show'); sheet.style.transform = '';
    document.body.style.overflow = ''; sheetCtx = null;
  }
  function refreshSheet() {
    if (!sheetCtx) return;
    if (sheetCtx.kind === 'identity') idById(sheetCtx.id) ? showIdentity(sheetCtx.id) : closeSheet();
    if (sheetCtx.kind === 'account') accById(sheetCtx.id) ? showAccount(sheetCtx.id) : closeSheet();
  }
  (() => {
    let y0 = null, dy = 0;
    sheet.addEventListener('touchstart', e => {
      const body = $('.sheet-body', sheet);
      if (innerWidth >= 700 || (body && body.scrollTop > 0 && !e.target.closest('.sheet-bar,.grabber'))) return;
      if (e.target.closest('input,textarea,.suggest')) return;
      y0 = e.touches[0].clientY; dy = 0; sheet.classList.add('dragging');
    }, { passive: true });
    sheet.addEventListener('touchmove', e => {
      if (y0 === null) return;
      dy = Math.max(0, e.touches[0].clientY - y0);
      sheet.style.transform = `translateY(${dy}px)`;
    }, { passive: true });
    sheet.addEventListener('touchend', () => {
      if (y0 === null) return;
      sheet.classList.remove('dragging'); y0 = null;
      if (dy > 110) closeSheet(); else sheet.style.transform = '';
    });
  })();

  const bar = (left, title, right, rightAttr = '') => `<div class="sheet-bar"><button data-sheet-left>${left}</button><span class="t">${title}</span><button ${rightAttr}>${right}</button></div>`;
  const closeX = `<div class="sheet-bar float"><button class="x" data-close aria-label="关闭">${ICON.x}</button></div>`;

  function showIdentity(id) {
    const i = idById(id); if (!i) return;
    const accs = accountsOf(id);
    openSheet(`${closeX}
      <div class="sheet-body">
        <div class="cine idcine" style="--c:${i.color}">
          ${poster(accs, 4, 'backdrop')}
          ${walletCard(i, 'tabindex="-1" style="--c:' + i.color + ';pointer-events:none"')}
        </div>
        <div class="actions">
          <button class="action" data-copy="${esc(i.value)}">${ICON.copy}复制</button>
          <button class="action" data-edit-identity="${i.id}">${ICON.edit}编辑</button>
          <button class="action destructive" data-del-identity="${i.id}">${ICON.trash}删除</button>
        </div>
        ${i.notes ? `<div class="sec-title">备注</div><div class="notes">${esc(i.notes)}</div>` : ''}
        <div class="sec-title">用它注册或绑定的 ${accs.length} 个网站与 App</div>
        <div class="cat-list" style="padding:0">${accs.map(a => reel(a)).join('')}
          <button class="reel" data-new="account" data-preset="${i.id}" style="color:var(--tint)"><span class="icon" style="background:rgba(255,255,255,.08);color:var(--tint)">${ICON.plus}</span><span class="reel-main"><span class="reel-title" style="font-weight:500">添加网站或 App</span></span></button>
        </div>
      </div>`, { kind: 'identity', id });
  }

  function showAccount(id) {
    const a = accById(id); if (!a) return;
    const ids = a.identityIds.map(idById).filter(Boolean);
    const d = cleanDomain(a.domain);
    openSheet(`${closeX}
      <div class="sheet-body">
        <div class="cine">
          ${poster([a, a, a, a], 4, 'backdrop')}
          ${favIcon(a, 'lg')}
          <h2>${esc(a.name)}</h2>
          <div class="caps"><span class="cap dim">${esc(a.category)}</span>${d ? `<span class="cap dim">${esc(d)}</span>` : ''}</div>
        </div>
        <div class="actions">
          ${d ? `<a class="action" href="https://${esc(d)}" target="_blank" rel="noopener">${ICON.open}打开网站</a>` : `<button class="action" disabled style="opacity:.35">${ICON.open}打开网站</button>`}
          <button class="action" data-edit-account="${a.id}">${ICON.edit}编辑</button>
          <button class="action destructive" data-del-account="${a.id}">${ICON.trash}删除</button>
        </div>
        <div class="sec-title">注册或绑定用的</div>
        <div class="group">${ids.map(i => `<button class="cell" data-identity="${i.id}">${idIcon(i)}<span class="cell-main"><span class="cell-title">${esc(display(i))}</span>${i.label ? `<span class="cell-sub">${esc(i.label)}</span>` : ''}</span>${ICON.chev}</button>`).join('') || '<div class="cell" style="color:var(--secondary)">还没有选择邮箱或手机号</div>'}</div>
        ${a.notes ? `<div class="sec-title">备注</div><div class="notes">${esc(a.notes)}</div>` : ''}
        <p class="hint" style="margin-top:16px">添加于 ${new Date(a.createdAt || Date.now()).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })}</p>
      </div>`, { kind: 'account', id });
  }

  // ---------- forms ----------
  function identityForm(existing, presetType) {
    const i = existing || { type: presetType || 'email', value: '', label: '', color: COLORS[state.identities.length % COLORS.length], notes: '' };
    let type = i.type, color = i.color;
    openSheet(`${bar('取消', existing ? '编辑' : '新身份', existing ? '保存' : '添加', 'id="saveIdentity"')}
      <div class="sheet-body form">
        <div class="preview" id="preview"></div>
        <div class="seg-inline ${type === 'phone' ? 'right' : ''}" id="typeSeg"><span class="thumb"></span><button data-v="email">邮箱</button><button data-v="phone">手机号</button></div>
        <div class="group" style="margin-top:16px">
          <div class="field" id="valueField"><label id="valueLabel">${type === 'email' ? '地址' : '号码'}</label><input id="fValue" value="${esc(i.value)}" inputmode="${type === 'email' ? 'email' : 'tel'}" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="${type === 'email' ? 'name@example.com' : '+86 138 0000 0000'}"></div>
          <div class="field"><label>标签</label><input id="fLabel" value="${esc(i.label)}" placeholder="主力、小号、工作…"></div>
        </div>
        <div class="sec-title">颜色</div>
        <div class="group"><div class="colors" id="colors">${COLORS.map(c => `<button class="color ${c === color ? 'active' : ''}" style="--c:${c}" data-c="${c}" aria-label="颜色"></button>`).join('')}</div></div>
        <div class="sec-title">备注</div>
        <div class="group"><div class="field stack"><textarea id="fNotes" placeholder="运营商、是否实名、找回方式">${esc(i.notes || '')}</textarea></div></div>
        <p class="hint">不要在这里填写密码。</p>
      </div>`, { kind: 'form' });
    setTimeout(() => {
      const preview = () => {
        const tmp = { id: existing?.id || '_new', type, value: $('#fValue').value.trim() || (type === 'email' ? 'name@example.com' : '+86 138 0000 0000'), label: $('#fLabel').value.trim(), color, notes: $('#fNotes').value.trim() };
        const html = walletCard(tmp, 'tabindex="-1" style="--c:' + color + ';pointer-events:none"');
        const box = $('#preview'); box.innerHTML = html; fixStack(box);
      };
      preview();
      ['#fValue', '#fLabel', '#fNotes'].forEach(s => $(s).addEventListener('input', preview));
      $$('#typeSeg button').forEach(b => b.onclick = () => {
        type = b.dataset.v; $('#typeSeg').classList.toggle('right', type === 'phone'); preview();
        $('#valueLabel').textContent = type === 'email' ? '地址' : '号码';
        const f = $('#fValue'); f.placeholder = type === 'email' ? 'name@example.com' : '+86 138 0000 0000'; f.inputMode = type === 'email' ? 'email' : 'tel';
      });
      $$('#colors .color').forEach(b => b.onclick = () => { color = b.dataset.c; $$('#colors .color').forEach(x => x.classList.toggle('active', x === b)); preview(); });
      $('#saveIdentity').onclick = () => {
        const value = $('#fValue').value.trim();
        const ok = type === 'email' ? /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) : value.replace(/\D/g, '').length >= 5;
        if (!ok) { const f = $('#valueField'); f.classList.remove('invalid'); void f.offsetWidth; f.classList.add('invalid'); $('#fValue').focus(); toast(type === 'email' ? '邮箱地址格式不对' : '手机号至少要 5 位数字'); return; }
        if (state.identities.some(x => x.value.toLowerCase() === value.toLowerCase() && x.id !== existing?.id)) { toast('这个身份已经添加过了'); return; }
        const data = { type, value, label: $('#fLabel').value.trim(), color, notes: $('#fNotes').value.trim() };
        let id;
        if (existing) { Object.assign(existing, data); id = existing.id; } else { id = uid(); state.identities.push({ id, ...data }); }
        save(); render(); showIdentity(id); toast(existing ? '已保存' : '已添加');
      };
      if (!existing) $('#fValue').focus();
    }, 0);
  }

  function accountForm(existing, presetId, presetName = '') {
    if (!state.identities.length) { identityForm(); toast('先添加一个邮箱或手机号'); return; }
    const a = existing || { name: presetName, domain: '', category: '其他', identityIds: presetId ? [presetId] : [], notes: '' };
    const sel = new Set(a.identityIds);
    let cat = a.category;
    openSheet(`${bar('取消', existing ? '编辑' : '新网站或 App', existing ? '保存' : '添加', 'id="saveAccount"')}
      <div class="sheet-body form">
        <div class="group">
          <div class="field" id="nameField"><label>名称</label><input id="aName" value="${esc(a.name)}" placeholder="GitHub、淘宝、微信…" autocomplete="off"></div>
          <div class="suggest" id="suggest"></div>
          <div class="field"><label>网址</label><input id="aDomain" value="${esc(a.domain)}" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="github.com（用来显示图标）"></div>
        </div>
        <div class="sec-title">用哪个邮箱或手机号注册、绑定的</div>
        <div class="group" id="idPicks">${state.identities.map(i => `<button class="check-cell ${sel.has(i.id) ? 'on' : ''}" data-id="${i.id}" style="--c:${i.color}"><i></i><span class="cell-main"><span class="cell-title">${esc(display(i))}</span></span>${ICON.tick}</button>`).join('')}</div>
        <p class="hint">可以多选，比如用邮箱注册、再绑定手机号。</p>
        <div class="sec-title">分类</div>
        <div class="group"><div class="cats" id="catPicks">${CATS.map(c => `<button class="${c === cat ? 'active' : ''}" data-c="${c}">${c}</button>`).join('')}</div></div>
        <div class="sec-title">备注</div>
        <div class="group"><div class="field stack"><textarea id="aNotes" placeholder="是否开了两步验证、用户名、会员到期日">${esc(a.notes || '')}</textarea></div></div>
        <p class="hint">不要在这里填写密码。</p>
      </div>`, { kind: 'form' });
    setTimeout(() => {
      const setCat = c => { cat = c; $$('#catPicks button').forEach(x => x.classList.toggle('active', x.dataset.c === c)); };
      $$('#catPicks button').forEach(b => b.onclick = () => setCat(b.dataset.c));
      $$('#idPicks .check-cell').forEach(b => b.onclick = () => { const id = b.dataset.id; sel.has(id) ? sel.delete(id) : sel.add(id); b.classList.toggle('on'); });
      const applyPreset = p => { $('#aName').value = p[0]; $('#aDomain').value = p[1]; setCat(p[2]); $('#suggest').innerHTML = ''; };
      const suggest = () => {
        const q = $('#aName').value.trim().toLowerCase();
        const exact = PRESETS.find(p => p[0].toLowerCase() === q);
        if (exact) { if (!$('#aDomain').value) $('#aDomain').value = exact[1]; setCat(exact[2]); }
        const list = q && !exact ? PRESETS.filter(p => p[0].toLowerCase().includes(q) || p[1].includes(q)).slice(0, 8) : [];
        $('#suggest').innerHTML = list.map((p, k) => `<button data-k="${PRESETS.indexOf(p)}">${favIcon({ name: p[0], domain: p[1] })}${esc(p[0])}</button>`).join('');
        $$('#suggest button').forEach(b => b.onclick = () => applyPreset(PRESETS[b.dataset.k]));
      };
      $('#aName').addEventListener('input', suggest);
      if (presetName) suggest();
      $('#saveAccount').onclick = () => {
        const name = $('#aName').value.trim();
        if (!name) { const f = $('#nameField'); f.classList.remove('invalid'); void f.offsetWidth; f.classList.add('invalid'); $('#aName').focus(); toast('填一下名称'); return; }
        const data = { name, domain: cleanDomain($('#aDomain').value), category: cat, identityIds: [...sel], notes: $('#aNotes').value.trim() };
        let id;
        if (existing) { Object.assign(existing, data); id = existing.id; } else { id = uid(); state.accounts.push({ id, createdAt: Date.now(), ...data }); }
        save(); render(); showAccount(id); toast(existing ? '已保存' : `已添加 ${name}`);
      };
      if (!existing && !presetName) $('#aName').focus();
    }, 0);
  }

  function confirmSheet(title, text, label, fn) {
    openSheet(`<div class="sheet-body"><div class="alert"><h3>${esc(title)}</h3><p>${text}</p></div>
      <div class="alert-actions"><button class="destructive" id="cOk">${esc(label)}</button><button data-close>取消</button></div></div>`, { kind: 'confirm' });
    setTimeout(() => { $('#cOk').onclick = () => { closeSheet(); fn(); }; }, 0);
  }

  // ---------- spotlight ----------
  let spotItems = [], spotSel = -1;
  function openSpot() { $('#spotLayer').classList.add('show'); const i = $('#spotInput'); i.value = ''; renderSpot(''); setTimeout(() => i.focus(), 60); }
  const closeSpot = () => { $('#spotLayer').classList.remove('show'); $('#spotInput').blur(); };
  function hl(text, q) {
    const s = String(text); if (!q) return esc(s);
    const k = s.toLowerCase().indexOf(q.toLowerCase());
    return k < 0 ? esc(s) : esc(s.slice(0, k)) + '<mark>' + esc(s.slice(k, k + q.length)) + '</mark>' + esc(s.slice(k + q.length));
  }
  function renderSpot(q) {
    q = q.trim(); const ql = q.toLowerCase(), qd = q.replace(/\D/g, '');
    const accs = q ? state.accounts.filter(a => a.name.toLowerCase().includes(ql) || (a.domain || '').includes(ql)).slice(0, 8) : [];
    const ids = q ? state.identities.filter(i => i.value.toLowerCase().includes(ql) || (i.label || '').toLowerCase().includes(ql) || (qd.length >= 3 && i.value.replace(/\D/g, '').includes(qd))).slice(0, 6) : [];
    spotItems = [...accs.map(a => ['account', a.id]), ...ids.map(i => ['identity', i.id])]; spotSel = spotItems.length ? 0 : -1;
    const box = $('#spotResults');
    if (!q) { box.innerHTML = `<div class="spot-empty">输入网站名，看它用的是哪个邮箱。<br>输入邮箱或号码，看它绑定了哪些 App。</div>`; return; }
    if (!spotItems.length) { box.innerHTML = `<div class="spot-empty">没有找到“${esc(q)}”<br><button class="btn-filled" data-quick="${esc(q)}">添加“${esc(q)}”</button></div>`; return; }
    box.innerHTML =
      (accs.length ? `<div class="sec-title">网站与 App</div>${accs.map(a => reel(a).replace(`<span class="reel-title">${esc(a.name)}</span>`, `<span class="reel-title">${hl(a.name, q)}</span>`)).join('')}` : '') +
      (ids.length ? `<div class="sec-title">邮箱与手机号</div><div class="group">${ids.map(i => `<button class="cell" data-identity="${i.id}">${idIcon(i)}<span class="cell-main"><span class="cell-title">${ui.privacy ? esc(display(i)) : hl(i.value, q)}</span><span class="cell-sub">${esc(i.label || '')}${i.label ? '，' : ''}${accountsOf(i.id).length} 个网站与 App</span></span>${ICON.chev}</button>`).join('')}</div>` : '');
    markSpot();
    $$('#spotResults .reel, #spotResults .cell').forEach((el, k) => el.animate([{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'none' }], { duration: 300, delay: k * 20, easing: 'ease-out', fill: 'backwards' }));
  }
  function markSpot() { $$('#spotResults .reel, #spotResults .cell').forEach((c, k) => c.classList.toggle('sel', k === spotSel)); }

  // ---------- import / export ----------
  function exportData() {
    const blob = new Blob([JSON.stringify({ app: 'atlas', version: 1, exportedAt: new Date().toISOString(), identities: state.identities, accounts: state.accounts }, null, 2)], { type: 'application/json' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = `账号备份-${new Date().toISOString().slice(0, 10)}.json`; a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1500); toast('备份已导出');
  }
  function importData(file) {
    const r = new FileReader();
    r.onload = () => {
      try {
        const d = JSON.parse(r.result);
        if (!Array.isArray(d.identities) || !Array.isArray(d.accounts)) throw 0;
        snap(); state = { identities: d.identities, accounts: d.accounts }; save(); render();
        undoToast(`已导入 ${d.identities.length} 个身份和 ${d.accounts.length} 个网站`);
      } catch { toast('这不是 Atlas 的备份文件'); }
    };
    r.readAsText(file);
  }

  // ---------- events ----------
  document.addEventListener('click', e => {
    const menu = $('#menu');
    if (!e.target.closest('#menu,#menuBtn')) menu.classList.remove('open');
    const t = e.target.closest('button,a,[data-account],[data-identity]'); if (!t) return;
    const d = t.dataset;
    if (t.id === 'menuBtn') return menu.classList.toggle('open');
    if (t.id === 'addBtn') return ui.tab === 'home' && !state.identities.length ? identityForm() : accountForm(null, ui.tab === 'home' ? state.identities[ui.active]?.id : null);
    if (t.id === 'gridAdd') return accountForm(null, state.identities[ui.active]?.id);
    if (t.id === 'openSearch') return openSpot();
    if (t.id === 'spotCancel') return closeSpot();
    if (d.act) {
      menu.classList.remove('open');
      if (d.act === 'privacy') { ui.privacy = !ui.privacy; localStorage.setItem('atlas.privacy', ui.privacy ? '1' : '0'); render(); refreshSheet(); toast(ui.privacy ? '号码已隐藏' : '号码已显示'); }
      if (d.act === 'export') exportData();
      if (d.act === 'import') $('#fileInput').click();
      if (d.act === 'demo') { snap(); state = demo(); save(); ui.active = 0; render(); undoToast('已载入示例'); }
      if (d.act === 'wipe') confirmSheet('清空全部数据？', '所有邮箱、手机号和网站记录都会从这台设备上删除。建议先导出备份。', '清空全部数据', () => { snap(); state = { identities: [], accounts: [] }; save(); render(); undoToast('已清空'); });
      return;
    }
    if (t.closest('#tabbar')) return setTab(d.tab);
    if (d.cat) { ui.cat = d.cat; renderSites(); return; }
    if (d.close !== undefined || d.sheetLeft !== undefined) {
      if (d.sheetLeft !== undefined && !t.textContent.trim()) return;
      return closeSheet();
    }
    if (d.new === 'identity') return identityForm(null, d.type);
    if (d.new === 'account') return accountForm(null, d.preset);
    if (d.quick !== undefined) { closeSpot(); return accountForm(null, null, d.quick); }
    if (d.copy) { navigator.clipboard?.writeText(d.copy).then(() => toast('已复制'), () => toast('复制失败')); return; }
    if (d.editIdentity) return identityForm(idById(d.editIdentity));
    if (d.editAccount) return accountForm(accById(d.editAccount));
    if (d.delIdentity) {
      const i = idById(d.delIdentity), n = accountsOf(i.id).length;
      return confirmSheet(`删除 ${display(i)}？`, n ? `它会从 ${n} 个网站与 App 的记录里移除，这些网站本身会保留。` : '删除后几秒内可以撤销。', '删除', () => {
        snap(); state.identities = state.identities.filter(x => x.id !== i.id); state.accounts.forEach(a => a.identityIds = a.identityIds.filter(x => x !== i.id)); save(); render(); undoToast('已删除');
      });
    }
    if (d.delAccount) {
      const a = accById(d.delAccount);
      return confirmSheet(`删除 ${a.name}？`, '只删除这条记录，不影响你在那个网站上的账号。', '删除', () => { snap(); state.accounts = state.accounts.filter(x => x.id !== a.id); save(); render(); undoToast('已删除'); });
    }
    if (d.account) { if ($('#spotLayer').classList.contains('show')) closeSpot(); return showAccount(d.account); }
    if (d.identity) {
      if ($('#spotLayer').classList.contains('show')) closeSpot();
      // tapping a side card in the wallet brings it to the front first
      const w = t.closest('#wallet');
      if (w) { const k = $$('.wcard', w).indexOf(t); if (k !== ui.active) { centerCard(w, t); return; } }
      return showIdentity(d.identity);
    }
  });

  $('#scrim').onclick = closeSheet;
  $('#fileInput').onchange = e => { const f = e.target.files[0]; if (f) importData(f); e.target.value = ''; };
  $('#spotInput').addEventListener('input', e => renderSpot(e.target.value));
  $('#spotInput').addEventListener('keydown', e => {
    if (e.key === 'ArrowDown' && spotItems.length) { spotSel = Math.min(spotItems.length - 1, spotSel + 1); markSpot(); e.preventDefault(); }
    if (e.key === 'ArrowUp' && spotItems.length) { spotSel = Math.max(0, spotSel - 1); markSpot(); e.preventDefault(); }
    if (e.key === 'Enter') { const it = spotItems[spotSel]; if (it) { closeSpot(); it[0] === 'account' ? showAccount(it[1]) : showIdentity(it[1]); } else $('[data-quick]')?.click(); }
  });
  document.addEventListener('keydown', e => {
    const typing = /INPUT|TEXTAREA/.test(document.activeElement?.tagName);
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); openSpot(); }
    else if (e.key === '/' && !typing) { e.preventDefault(); openSpot(); }
    else if (e.key === 'Escape') { if ($('#spotLayer').classList.contains('show')) closeSpot(); else closeSheet(); $('#menu').classList.remove('open'); }
    else if (!typing && ui.tab === 'home' && (e.key === 'ArrowRight' || e.key === 'ArrowLeft')) {
      const w = $('#wallet'), cards = $$('.wcard', w), c = cards[Math.max(0, Math.min(cards.length - 1, ui.active + (e.key === 'ArrowRight' ? 1 : -1)))];
      if (c) centerCard(w, c);
    }
  });
  $('#wallet').addEventListener('scroll', () => { if (!rafCF) rafCF = requestAnimationFrame(coverflow); }, { passive: true });
  const top = $('#top');
  addEventListener('scroll', () => top.classList.toggle('scrolled', scrollY > 30), { passive: true });
  addEventListener('resize', () => { Graph.resize(); coverflow(); });

  // ---------- relationship graph ----------
  const Graph = (() => {
    const c = $('#graph'), ctx = c.getContext('2d');
    let nodes = [], edges = [], w = 0, h = 0, running = false, alpha = 0, hover = null, drag = null, moved = false;
    const imgs = {};
    const dark = () => true;
    function loadImg(d) {
      d = cleanDomain(d); if (!d) return null; if (imgs[d]) return imgs[d];
      const im = new Image(), src = favSources(d); let k = 0;
      im.onerror = () => { if (++k < src.length) im.src = src[k]; else im.dead = true; };
      im.onload = () => { if (im.naturalWidth < 12 && ++k < src.length) im.src = src[k]; kick(.05); };
      im.src = src[0]; return imgs[d] = im;
    }
    function resize() {
      const r = c.getBoundingClientRect(); if (!r.width) return;
      const dpr = Math.min(3, devicePixelRatio || 1); w = r.width; h = r.height;
      c.width = w * dpr; c.height = h * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0); kick(.1);
    }
    function rebuild() {
      resize();
      const old = Object.fromEntries(nodes.map(n => [n.id, n])), cx = w / 2, cy = h / 2;
      const I = state.identities.map((i, k) => {
        const ang = k / Math.max(1, state.identities.length) * Math.PI * 2;
        const n = old[i.id] || { id: i.id, x: cx + Math.cos(ang) * 110, y: cy + Math.sin(ang) * 110, vx: 0, vy: 0 };
        return Object.assign(n, { kind: 'i', ref: i, r: 20 + Math.min(8, accountsOf(i.id).length) });
      });
      const A = state.accounts.map(a => {
        const n = old[a.id] || { id: a.id, x: cx + (Math.random() - .5) * 260, y: cy + (Math.random() - .5) * 260, vx: 0, vy: 0 };
        return Object.assign(n, { kind: 'a', ref: a, r: 15, img: loadImg(a.domain) });
      });
      nodes = [...I, ...A];
      const map = Object.fromEntries(nodes.map(n => [n.id, n]));
      edges = [];
      state.accounts.forEach(a => a.identityIds.forEach(id => map[id] && edges.push([map[id], map[a.id]])));
      kick(1);
    }
    function kick(a) { alpha = Math.max(alpha, a); if (running && !kick.raf) kick.raf = requestAnimationFrame(loop); }
    function step() {
      const cx = w / 2, cy = h / 2;
      for (let i = 0; i < nodes.length; i++) {
        const a = nodes[i];
        for (let j = i + 1; j < nodes.length; j++) {
          const b = nodes[j]; let dx = b.x - a.x, dy = b.y - a.y; const d2 = dx * dx + dy * dy || 1, d = Math.sqrt(d2);
          const f = (a.kind === 'i' && b.kind === 'i' ? 22000 : 3600) / d2 * alpha; dx /= d; dy /= d;
          a.vx -= dx * f; a.vy -= dy * f; b.vx += dx * f; b.vy += dy * f;
        }
        a.vx += (cx - a.x) * .004 * alpha; a.vy += (cy - a.y) * .004 * alpha;
      }
      for (const [s, t] of edges) {
        const dx = t.x - s.x, dy = t.y - s.y, d = Math.hypot(dx, dy) || 1, f = (d - 90) * .03 * alpha;
        s.vx += dx / d * f; s.vy += dy / d * f; t.vx -= dx / d * f; t.vy -= dy / d * f;
      }
      for (const n of nodes) {
        if (n === drag) { n.vx = n.vy = 0; continue; }
        n.vx *= .78; n.vy *= .78; n.x += n.vx; n.y += n.vy;
        n.x = Math.max(n.r + 6, Math.min(w - n.r - 6, n.x)); n.y = Math.max(n.r + 6, Math.min(h - n.r - 40, n.y));
      }
      alpha *= .985;
    }
    const near = (n, m) => edges.some(([s, t]) => (s === n && t === m) || (s === m && t === n));
    function rr(x, y, s, r) { ctx.beginPath(); ctx.roundRect ? ctx.roundRect(x, y, s, s, r) : ctx.rect(x, y, s, s); }
    function draw() {
      const dk = dark(), label = dk ? '#f5f5f7' : '#1d1d1f', sec = dk ? '#98989d' : '#6e6e73';
      ctx.clearRect(0, 0, w, h);
      ctx.lineCap = 'round';
      for (const [s, t] of edges) {
        const on = !hover || s === hover || t === hover;
        ctx.strokeStyle = s.ref.color; ctx.globalAlpha = hover ? (on ? .75 : .06) : .28; ctx.lineWidth = on && hover ? 2 : 1.4;
        ctx.beginPath(); ctx.moveTo(s.x, s.y); ctx.lineTo(t.x, t.y); ctx.stroke();
      }
      ctx.globalAlpha = 1;
      for (const n of nodes) {
        const dim = hover && n !== hover && !near(n, hover);
        ctx.globalAlpha = dim ? .2 : 1;
        if (n.kind === 'i') {
          const s = n.r * 2;
          ctx.shadowColor = 'rgba(0,0,0,.18)'; ctx.shadowBlur = 10; ctx.shadowOffsetY = 3;
          const g = ctx.createLinearGradient(0, n.y - n.r, 0, n.y + n.r); g.addColorStop(0, n.ref.color + 'cc'); g.addColorStop(1, n.ref.color);
          ctx.fillStyle = g; rr(n.x - n.r, n.y - n.r, s, s * .26); ctx.fill();
          ctx.shadowColor = 'transparent';
          ctx.strokeStyle = '#fff'; ctx.lineWidth = 1.8; ctx.lineJoin = 'round';
          const k = n.r * .5;
          if (n.ref.type === 'email') { ctx.beginPath(); ctx.roundRect?.(n.x - k, n.y - k * .7, k * 2, k * 1.4, 3); ctx.stroke(); ctx.beginPath(); ctx.moveTo(n.x - k * .85, n.y - k * .45); ctx.lineTo(n.x, n.y + k * .15); ctx.lineTo(n.x + k * .85, n.y - k * .45); ctx.stroke(); }
          else { ctx.beginPath(); ctx.roundRect?.(n.x - k * .55, n.y - k, k * 1.1, k * 2, 3); ctx.stroke(); }
          ctx.fillStyle = label; ctx.font = '600 12px -apple-system, "PingFang SC", sans-serif'; ctx.textAlign = 'center';
          ctx.fillText(n.ref.label || display(n.ref), n.x, n.y + n.r + 17);
        } else {
          const s = n.r * 2;
          ctx.shadowColor = 'rgba(0,0,0,.14)'; ctx.shadowBlur = 6; ctx.shadowOffsetY = 2;
          ctx.fillStyle = '#fff'; rr(n.x - n.r, n.y - n.r, s, s * .26); ctx.fill(); ctx.shadowColor = 'transparent';
          if (n.img && n.img.complete && n.img.naturalWidth >= 12 && !n.img.dead) {
            try { ctx.drawImage(n.img, n.x - n.r * .62, n.y - n.r * .62, n.r * 1.24, n.r * 1.24); } catch { }
          } else {
            ctx.fillStyle = brand(n.ref); rr(n.x - n.r, n.y - n.r, s, s * .26); ctx.fill();
            ctx.fillStyle = '#fff'; ctx.font = '600 13px -apple-system, "PingFang SC", sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
            ctx.fillText((n.ref.name || '?').slice(0, 1).toUpperCase(), n.x, n.y + .5); ctx.textBaseline = 'alphabetic';
          }
          if (hover && (n === hover || near(n, hover))) {
            ctx.fillStyle = sec; ctx.font = '500 11px -apple-system, "PingFang SC", sans-serif'; ctx.textAlign = 'center';
            ctx.fillText(n.ref.name, n.x, n.y + n.r + 14);
          }
        }
      }
      ctx.globalAlpha = 1;
    }
    function loop() {
      kick.raf = null; if (!running) return;
      step(); draw();
      if (alpha > .004 || drag) kick.raf = requestAnimationFrame(loop);
    }
    const at = (x, y) => { for (let k = nodes.length - 1; k >= 0; k--) { const n = nodes[k]; if (Math.abs(n.x - x) < n.r + 6 && Math.abs(n.y - y) < n.r + 6) return n; } return null; };
    const pos = e => { const r = c.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };
    let start = null;
    c.addEventListener('pointerdown', e => { const [x, y] = pos(e); drag = at(x, y); start = [x, y]; moved = false; hover = drag; if (drag) c.setPointerCapture(e.pointerId); kick(.05); draw(); });
    c.addEventListener('pointermove', e => {
      const [x, y] = pos(e);
      if (drag) { if (Math.hypot(x - start[0], y - start[1]) > 4) moved = true; drag.x = x; drag.y = y; kick(.25); }
      else if (e.pointerType === 'mouse') { const n = at(x, y); if (n !== hover) { hover = n; c.style.cursor = n ? 'pointer' : 'default'; draw(); } }
    });
    c.addEventListener('pointerup', () => {
      const n = drag; drag = null;
      if (n && !moved) n.kind === 'i' ? showIdentity(n.id) : showAccount(n.id);
      if (!matchMedia('(hover:hover)').matches) hover = null;
      draw();
    });
    c.addEventListener('pointerleave', () => { if (!drag) { hover = null; draw(); } });
    matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', () => draw());
    return {
      start() { running = true; requestAnimationFrame(rebuild); },
      stop() { running = false; },
      rebuild() { if (running) rebuild(); },
      resize() { if (running) resize(); },
    };
  })();

  // ---------- boot ----------
  if (!state) {
    state = demo(); save();
    setTimeout(() => toast('这是示例数据', { label: '清空', fn: () => { state = { identities: [], accounts: [] }; save(); render(); } }), 1400);
  }
  render();
  const first = state.identities[0];
  document.documentElement.style.setProperty('--accent', first ? first.color : '#3a3a3c');
  // one orchestrated entrance: the cards deal in from the right
  const W = $('#wallet'); W.style.scrollSnapType = 'none';
  $$('#wallet .wcard').forEach((c, k) => c.animate([{ translate: '60px 0', opacity: 0 }, { translate: '0 0', opacity: 1 }], { duration: 800, delay: 80 + k * 70, easing: 'cubic-bezier(.32,.72,0,1)', fill: 'backwards' }));
  setTimeout(() => { W.style.scrollSnapType = ''; const c = $$('.wcard', W)[ui.active]; if (c) W.scrollLeft = cardX(W, c); coverflow(); }, 80 + $$('.wcard', W).length * 70 + 820);
})();
