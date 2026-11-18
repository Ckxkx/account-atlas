/* Atlas — local-first account relationship manager */
(() => {
  'use strict';

  const KEY = 'atlas.v1';
  const COLORS = ['#007aff', '#34c759', '#ff9500', '#ff2d55', '#af52de', '#5ac8fa', '#ff3b30', '#5856d6'];
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
    ['Telegram', 'telegram.org', '社交'], ['X', 'x.com', '社交'], ['Instagram', 'instagram.com', '社交'], ['Netflix', 'netflix.com', '娱乐'],
    ['Spotify', 'spotify.com', '娱乐'], ['YouTube', 'youtube.com', '娱乐'], ['Binance', 'binance.com', '加密'], ['OKX', 'okx.com', '加密'],
    ['MetaMask', 'metamask.io', '加密'], ['PayPal', 'paypal.com', '支付金融'], ['Amazon', 'amazon.com', '购物'],
  ];

  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);
  const esc = (s = '') => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const zh = (a, b) => a.localeCompare(b, 'zh-CN');

  // ---------- state ----------
  let state = (() => { try { return JSON.parse(localStorage.getItem(KEY)); } catch { return null; } })();
  const ui = { tab: 'identities', cat: '全部', privacy: localStorage.getItem('atlas.privacy') === '1' };
  const save = () => localStorage.setItem(KEY, JSON.stringify(state));

  function demo() {
    const ids = [
      { id: uid(), type: 'email', value: 'me@gmail.com', label: '主力邮箱', color: COLORS[0], notes: '' },
      { id: uid(), type: 'email', value: 'shopping.fun@qq.com', label: '购物小号', color: COLORS[3], notes: '' },
      { id: uid(), type: 'email', value: 'dev@outlook.com', label: '开发专用', color: COLORS[4], notes: '' },
      { id: uid(), type: 'phone', value: '+86 138 0000 1234', label: '主力手机号', color: COLORS[1], notes: '移动，已实名' },
      { id: uid(), type: 'phone', value: '+86 170 0000 5678', label: '副卡', color: COLORS[2], notes: '' },
    ];
    const [g, q, o, p1, p2] = ids.map(i => i.id);
    const mk = (name, domain, category, identityIds, notes = '') => ({ id: uid(), name, domain, category, identityIds, notes, createdAt: Date.now() });
    return {
      identities: ids,
      accounts: [
        mk('GitHub', 'github.com', '开发', [o, p1], '已开两步验证'), mk('Cloudflare', 'cloudflare.com', '开发', [o]),
        mk('ChatGPT', 'chatgpt.com', 'AI', [g]), mk('Claude', 'claude.ai', 'AI', [g]),
        mk('微信', 'weixin.qq.com', '社交', [p1]), mk('支付宝', 'alipay.com', '支付金融', [p1]),
        mk('淘宝', 'taobao.com', '购物', [q, p1]), mk('京东', 'jd.com', '购物', [q, p2]),
        mk('哔哩哔哩', 'bilibili.com', '娱乐', [p2]), mk('小红书', 'xiaohongshu.com', '社交', [p2]),
        mk('Steam', 'steampowered.com', '游戏', [q], '令牌在主力手机上'), mk('Apple ID', 'apple.com', '工具', [g, p1]),
        mk('Notion', 'notion.so', '工具', [g]), mk('Binance', 'binance.com', '加密', [o, p1], '已完成 KYC'),
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
    link: '<svg viewBox="0 0 24 24"><path d="M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1"/><path d="M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1"/></svg>',
  };
  const idIcon = (i, size = '') => `<span class="icon ${size}" style="--c:${i.color}">${ICON[i.type]}</span>`;
  function favIcon(a, size = '') {
    const src = favSources(a.domain), letter = esc((a.name || '?').trim().slice(0, 1).toUpperCase());
    if (!src.length) return `<span class="icon fav letter ${size}">${letter}</span>`;
    return `<span class="icon fav ${size}"><img alt="" loading="lazy" src="${src[0]}" data-rest='${JSON.stringify(src.slice(1))}' data-letter="${letter}" onerror="AtlasFav(this)"></span>`;
  }
  window.AtlasFav = img => {
    const rest = JSON.parse(img.dataset.rest || '[]');
    if (rest.length) { img.dataset.rest = JSON.stringify(rest.slice(1)); img.src = rest[0]; return; }
    const box = img.parentElement; box.classList.add('letter'); box.textContent = img.dataset.letter;
  };
  img_ok: {
    // treat 1px / tiny placeholder favicons as failures
    document.addEventListener('load', e => {
      const im = e.target;
      if (im.tagName === 'IMG' && im.dataset.rest !== undefined && im.naturalWidth && im.naturalWidth < 12) window.AtlasFav(im);
    }, true);
  }
  const tokens = ids => ids.map(idById).filter(Boolean).map(i => `<span class="token" style="--c:${i.color}"><i></i>${esc(i.label || display(i))}</span>`).join('');

  // ---------- toast ----------
  let toastTimer;
  function toast(msg, action) {
    const t = $('#toast');
    t.innerHTML = `<span>${esc(msg)}</span>${action ? `<button>${esc(action.label)}</button>` : ''}`;
    if (action) t.querySelector('button').onclick = () => { action.fn(); t.classList.remove('show'); };
    t.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), action ? 4500 : 2000);
  }
  let undoSnap = null;
  const snap = () => { undoSnap = JSON.stringify(state); };
  const undoToast = msg => toast(msg, { label: '撤销', fn: () => { state = JSON.parse(undoSnap); save(); render(); } });

  // ---------- render ----------
  function renderSummary() {
    const e = state.identities.filter(i => i.type === 'email').length, p = state.identities.length - e, a = state.accounts.length;
    $('#summary').textContent = state.identities.length || a ? `${e} 个邮箱、${p} 个手机号，关联着 ${a} 个网站与 App` : '';
  }

  function identityCell(i) {
    const n = accountsOf(i.id).length;
    return `<button class="cell" data-identity="${i.id}">
      ${idIcon(i)}
      <span class="cell-main"><span class="cell-title">${esc(display(i))}</span>${i.label ? `<span class="cell-sub">${esc(i.label)}</span>` : ''}</span>
      <span class="cell-detail">${n}</span>${ICON.chev}
    </button>`;
  }

  function renderIdentities() {
    const v = $('#identitiesView');
    if (!state.identities.length) {
      v.innerHTML = `<div class="empty">${idIcon({ type: 'email', color: COLORS[0] }, 'lg')}<h3>从一个邮箱开始</h3><p>添加你的邮箱和手机号，再把注册过的网站挂到它们下面。</p><button class="btn-filled" data-new="identity">添加邮箱或手机号</button></div>`;
      return;
    }
    const emails = state.identities.filter(i => i.type === 'email'), phones = state.identities.filter(i => i.type === 'phone');
    const block = (title, list, type) => `
      <div class="group-title">${title}<small>${list.length}</small></div>
      <div class="group">${list.map(identityCell).join('')}<button class="cell add" data-new="identity" data-type="${type}">添加${title}</button></div>`;
    v.innerHTML = block('邮箱', emails, 'email') + block('手机号', phones, 'phone');
  }

  function accountCell(a) {
    return `<button class="cell" data-account="${a.id}" style="--inset:71px">
      ${favIcon(a)}
      <span class="cell-main"><span class="cell-title">${esc(a.name)}</span><span class="ids">${tokens(a.identityIds) || '<span class="token">未关联</span>'}</span></span>
      ${ICON.chev}
    </button>`;
  }

  function renderAccounts() {
    const v = $('#accountsView');
    if (!state.accounts.length) {
      v.innerHTML = `<div class="empty"><span class="icon lg" style="--c:#8e8e93">${ICON.link}</span><h3>还没有网站</h3><p>记下一个网站或 App，选上它用的是哪个邮箱、哪个手机号。</p><button class="btn-filled" data-new="account">添加网站或 App</button></div>`;
      return;
    }
    const counts = {};
    state.accounts.forEach(a => counts[a.category] = (counts[a.category] || 0) + 1);
    const cats = CATS.filter(c => counts[c]);
    if (ui.cat !== '全部' && !counts[ui.cat]) ui.cat = '全部';
    const shown = ui.cat === '全部' ? cats : [ui.cat];
    v.innerHTML = `<div class="filters">${['全部', ...cats].map(c => `<button class="${c === ui.cat ? 'active' : ''}" data-cat="${c}">${c}</button>`).join('')}</div>` +
      shown.map(c => {
        const list = state.accounts.filter(a => a.category === c).sort((a, b) => zh(a.name, b.name));
        return `<div class="group-title">${c}<small>${list.length}</small></div><div class="group">${list.map(accountCell).join('')}</div>`;
      }).join('') +
      `<div class="group" style="margin-top:22px"><button class="cell add" data-new="account">添加网站或 App</button></div>`;
  }

  function render() {
    renderSummary(); renderIdentities(); renderAccounts();
    $('#menu [data-act="privacy"] span').textContent = ui.privacy ? '显示完整号码' : '隐藏号码';
    if (ui.tab === 'graph') Graph.rebuild();
  }

  // ---------- tabs ----------
  function setTab(tab) {
    if (tab === ui.tab) return;
    ui.tab = tab;
    const idx = ['identities', 'accounts', 'graph'].indexOf(tab);
    $$('#tabs button').forEach((b, k) => b.classList.toggle('active', k === idx));
    $('#segThumb').style.transform = `translateX(${idx * 100}%)`;
    $$('.view').forEach(v => v.classList.toggle('active', v.dataset.view === tab));
    tab === 'graph' ? Graph.start() : Graph.stop();
  }

  // ---------- sheet ----------
  const sheet = $('#sheet'), inner = $('#sheetInner');
  let sheetCtx = null;
  function openSheet(html, ctx = null) {
    const wasOpen = sheet.classList.contains('open');
    sheetCtx = ctx;
    if (wasOpen) {
      inner.innerHTML = html;
      inner.animate([{ opacity: 0, transform: 'translateX(16px)' }, { opacity: 1, transform: 'none' }], { duration: 300, easing: 'cubic-bezier(.32,.72,0,1)' });
    } else {
      inner.innerHTML = html;
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
  // swipe down to dismiss (mobile)
  (() => {
    let y0 = null, dy = 0;
    sheet.addEventListener('touchstart', e => {
      const body = $('.sheet-body', sheet);
      if (innerWidth >= 700 || (body && body.scrollTop > 0 && !e.target.closest('.sheet-bar,.grabber'))) return;
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
      if (dy > 120) closeSheet(); else sheet.style.transform = '';
    });
  })();

  const bar = (left, title, right, rightAttr = '') => `<div class="sheet-bar"><button data-sheet-left>${left}</button><span class="t">${title}</span><button ${rightAttr}>${right}</button></div>`;

  function showIdentity(id) {
    const i = idById(id); if (!i) return;
    const accs = accountsOf(id);
    openSheet(`${bar('', '', '完成', 'data-close')}
      <div class="sheet-body">
        <div class="hero">${idIcon(i, 'lg')}<h2>${esc(display(i))}</h2><p>${esc(i.label || (i.type === 'email' ? '邮箱' : '手机号'))}</p></div>
        <div class="actions">
          <button class="action" data-copy="${esc(i.value)}">${ICON.copy}复制</button>
          <button class="action" data-edit-identity="${i.id}">${ICON.edit}编辑</button>
          <button class="action destructive" data-del-identity="${i.id}">${ICON.trash}删除</button>
        </div>
        ${i.notes ? `<div class="group-title">备注</div><div class="notes">${esc(i.notes)}</div>` : ''}
        <div class="group-title">用它注册或绑定的 ${accs.length} 个网站与 App</div>
        <div class="group">${accs.map(accountCell).join('')}<button class="cell add" data-new="account" data-preset="${i.id}">添加网站或 App</button></div>
      </div>`, { kind: 'identity', id });
  }

  function showAccount(id) {
    const a = accById(id); if (!a) return;
    const ids = a.identityIds.map(idById).filter(Boolean);
    const d = cleanDomain(a.domain);
    openSheet(`${bar('', '', '完成', 'data-close')}
      <div class="sheet-body">
        <div class="hero">${favIcon(a, 'lg')}<h2>${esc(a.name)}</h2><p>${esc(a.category)}${d ? '，' + esc(d) : ''}</p></div>
        <div class="actions">
          ${d ? `<a class="action" href="https://${esc(d)}" target="_blank" rel="noopener" style="text-decoration:none">${ICON.open}打开网站</a>` : `<button class="action" disabled style="opacity:.35">${ICON.open}打开网站</button>`}
          <button class="action" data-edit-account="${a.id}">${ICON.edit}编辑</button>
          <button class="action destructive" data-del-account="${a.id}">${ICON.trash}删除</button>
        </div>
        <div class="group-title">注册或绑定用的</div>
        <div class="group">${ids.map(identityCell).join('') || '<div class="cell" style="color:var(--secondary)">还没有选择邮箱或手机号</div>'}</div>
        ${a.notes ? `<div class="group-title">备注</div><div class="notes">${esc(a.notes)}</div>` : ''}
        <p class="hint" style="margin-top:14px">添加于 ${new Date(a.createdAt || Date.now()).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })}</p>
      </div>`, { kind: 'account', id });
  }

  // ---------- forms ----------
  function identityForm(existing, presetType) {
    const i = existing || { type: presetType || 'email', value: '', label: '', color: COLORS[state.identities.length % COLORS.length], notes: '' };
    let type = i.type, color = i.color;
    openSheet(`${bar('取消', existing ? '编辑' : '新身份', existing ? '保存' : '添加', 'id="saveIdentity"')}
      <div class="sheet-body form">
        <div class="seg-inline" id="typeSeg"><button data-v="email" class="${type === 'email' ? 'active' : ''}">邮箱</button><button data-v="phone" class="${type === 'phone' ? 'active' : ''}">手机号</button></div>
        <div class="group" style="margin-top:16px">
          <div class="field" id="valueField"><label id="valueLabel">${type === 'email' ? '地址' : '号码'}</label><input id="fValue" value="${esc(i.value)}" inputmode="${type === 'email' ? 'email' : 'tel'}" autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="${type === 'email' ? 'name@example.com' : '+86 138 0000 0000'}"></div>
          <div class="field"><label>标签</label><input id="fLabel" value="${esc(i.label)}" placeholder="主力、小号、工作…"></div>
        </div>
        <div class="group-title">颜色</div>
        <div class="group"><div class="colors" id="colors">${COLORS.map(c => `<button class="color ${c === color ? 'active' : ''}" style="--c:${c}" data-c="${c}" aria-label="颜色"></button>`).join('')}</div></div>
        <div class="group-title">备注</div>
        <div class="group"><div class="field stack"><textarea id="fNotes" placeholder="运营商、是否实名、找回方式">${esc(i.notes || '')}</textarea></div></div>
        <p class="hint">不要在这里填写密码。</p>
      </div>`, { kind: 'form' });
    setTimeout(() => {
      $$('#typeSeg button').forEach(b => b.onclick = () => {
        type = b.dataset.v; $$('#typeSeg button').forEach(x => x.classList.toggle('active', x === b));
        $('#valueLabel').textContent = type === 'email' ? '地址' : '号码';
        const f = $('#fValue'); f.placeholder = type === 'email' ? 'name@example.com' : '+86 138 0000 0000'; f.inputMode = type === 'email' ? 'email' : 'tel';
      });
      $$('#colors .color').forEach(b => b.onclick = () => { color = b.dataset.c; $$('#colors .color').forEach(x => x.classList.toggle('active', x === b)); });
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
        <div class="group-title">用哪个邮箱或手机号注册、绑定的</div>
        <div class="group" id="idPicks">${state.identities.map(i => `<button class="check-cell ${sel.has(i.id) ? 'on' : ''}" data-id="${i.id}" style="--c:${i.color}"><i></i><span class="cell-main"><span class="cell-title">${esc(display(i))}</span></span>${ICON.tick}</button>`).join('')}</div>
        <p class="hint">可以多选，比如用邮箱注册、再绑定手机号。</p>
        <div class="group-title">分类</div>
        <div class="group"><div class="cats" id="catPicks">${CATS.map(c => `<button class="${c === cat ? 'active' : ''}" data-c="${c}">${c}</button>`).join('')}</div></div>
        <div class="group-title">备注</div>
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
  function openSpot() { $('#spotLayer').classList.add('show'); const i = $('#spotInput'); i.value = ''; renderSpot(''); setTimeout(() => i.focus(), 50); }
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
    if (!q) { box.innerHTML = `<div class="spot-empty">输入网站名可以看到它用的是哪个邮箱；输入邮箱或号码，可以看到它绑定了哪些 App。</div>`; return; }
    if (!spotItems.length) { box.innerHTML = `<div class="spot-empty">没有找到“${esc(q)}”<br><button class="btn-filled" data-quick="${esc(q)}">添加“${esc(q)}”</button></div>`; return; }
    box.innerHTML =
      (accs.length ? `<div class="group-title">网站与 App</div><div class="group">${accs.map(a => `<button class="cell" data-account="${a.id}" style="--inset:71px">${favIcon(a)}<span class="cell-main"><span class="cell-title">${hl(a.name, q)}</span><span class="ids">${tokens(a.identityIds) || '<span class="token">未关联</span>'}</span></span>${ICON.chev}</button>`).join('')}</div>` : '') +
      (ids.length ? `<div class="group-title">邮箱与手机号</div><div class="group">${ids.map(i => `<button class="cell" data-identity="${i.id}">${idIcon(i)}<span class="cell-main"><span class="cell-title">${ui.privacy ? esc(display(i)) : hl(i.value, q)}</span><span class="cell-sub">${esc(i.label || '')}${i.label ? '，' : ''}${accountsOf(i.id).length} 个网站与 App</span></span>${ICON.chev}</button>`).join('')}</div>` : '');
    markSpot();
  }
  function markSpot() { $$('#spotResults .cell').forEach((c, k) => c.classList.toggle('sel', k === spotSel)); }

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
    const t = e.target.closest('button,[data-account],[data-identity]'); if (!t) return;
    const d = t.dataset;
    if (t.id === 'menuBtn') return menu.classList.toggle('open');
    if (t.id === 'addBtn') return ui.tab === 'accounts' ? accountForm() : (ui.tab === 'identities' ? identityForm() : accountForm());
    if (t.id === 'openSearch') return openSpot();
    if (t.id === 'spotCancel') return closeSpot();
    if (d.act) {
      menu.classList.remove('open');
      if (d.act === 'privacy') { ui.privacy = !ui.privacy; localStorage.setItem('atlas.privacy', ui.privacy ? '1' : '0'); render(); refreshSheet(); toast(ui.privacy ? '号码已隐藏' : '号码已显示'); }
      if (d.act === 'export') exportData();
      if (d.act === 'import') $('#fileInput').click();
      if (d.act === 'demo') { snap(); state = demo(); save(); render(); undoToast('已载入示例'); }
      if (d.act === 'wipe') confirmSheet('清空全部数据？', '所有邮箱、手机号和网站记录都会从这台设备上删除。建议先导出备份。', '清空全部数据', () => { snap(); state = { identities: [], accounts: [] }; save(); render(); undoToast('已清空'); });
      return;
    }
    if (t.closest('#tabs')) return setTab(d.tab);
    if (d.cat) { ui.cat = d.cat; renderAccounts(); return; }
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
      return confirmSheet(`删除 ${display(i)}？`, n ? `它会从 ${n} 个网站与 App 的记录里移除，这些网站本身会保留。` : '这个操作可以在几秒内撤销。', '删除', () => {
        snap(); state.identities = state.identities.filter(x => x.id !== i.id); state.accounts.forEach(a => a.identityIds = a.identityIds.filter(x => x !== i.id)); save(); render(); undoToast('已删除');
      });
    }
    if (d.delAccount) {
      const a = accById(d.delAccount);
      return confirmSheet(`删除 ${a.name}？`, '只会删除这条记录，不会影响你在那个网站上的账号。', '删除', () => { snap(); state.accounts = state.accounts.filter(x => x.id !== a.id); save(); render(); undoToast('已删除'); });
    }
    if (d.account) { if ($('#spotLayer').classList.contains('show')) closeSpot(); return showAccount(d.account); }
    if (d.identity) { if ($('#spotLayer').classList.contains('show')) closeSpot(); return showIdentity(d.identity); }
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
  });
  const nav = $('#nav');
  addEventListener('scroll', () => nav.classList.toggle('scrolled', scrollY > 40), { passive: true });
  addEventListener('resize', () => Graph.resize());

  // ---------- relationship graph ----------
  const Graph = (() => {
    const c = $('#graph'), ctx = c.getContext('2d');
    let nodes = [], edges = [], w = 0, h = 0, running = false, alpha = 0, hover = null, drag = null, moved = false;
    const imgs = {};
    const dark = () => matchMedia('(prefers-color-scheme: dark)').matches;
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
            ctx.fillStyle = '#8e8e93'; rr(n.x - n.r, n.y - n.r, s, s * .26); ctx.fill();
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
    setTimeout(() => toast('这是示例数据', { label: '清空', fn: () => { state = { identities: [], accounts: [] }; save(); render(); } }), 900);
  }
  render();
})();
