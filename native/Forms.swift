import SwiftUI
import PhotosUI

struct IdentityForm: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let existing: Identity?
  let baseColor: String
  @State private var type: String
  @State private var value: String
  @State private var label: String
  @State private var color: String
  @State private var notes: String
  @State private var esim: Bool
  @State private var apple: Bool
  @State private var provider: String
  /// 2.5: every email / phone it is bound to (a phone and the QQ mailbox, say)
  @State private var binds: [String]
  @State private var addNew = false               // a number / address not in the list yet
  @State private var viaNew = ""
  @State private var colorTouched = false   // the user picked a skin here: stop following the type / provider

  init(existing: Identity?, type: String, defaultColor: String, presetVia: String? = nil) {
    self.existing = existing
    let t = existing?.type ?? type
    // 2.4: 微信 and QQ are the only login cards; an Apple ID is a flag on its email / phone
    let p = existing?.provider == "qq" ? "qq" : "wechat"
    baseColor = defaultColor
    _type = State(initialValue: t)
    _value = State(initialValue: existing?.value ?? "")
    _label = State(initialValue: existing?.label ?? "")
    _color = State(initialValue: existing?.color ?? (t == "login" ? loginDefaultColor(p) : defaultColor))
    _notes = State(initialValue: existing?.notes ?? "")
    _esim = State(initialValue: existing?.esim == true)
    _apple = State(initialValue: existing?.apple == true)
    _provider = State(initialValue: p)
    _binds = State(initialValue: existing?.bindIDs ?? (presetVia.map { [$0] } ?? []))
  }

  var isLogin: Bool { type == "login" }
  var placeholder: String {
    switch type {
    case "email": return "name@example.com"
    case "phone": return "+86 138 0000 0000"
    default: return provider == "qq" ? "QQ 号或 QQ 邮箱" : "wxid 或自定义微信号"
    }
  }
  var sample: String {
    switch type {
    case "email": return "name@example.com"
    case "phone": return "+86 138 0000 0000"
    default: return provider == "qq" ? "10001" : "wxid_example"
    }
  }
  /// emails and phones a login card can be bound to
  var bindable: [Identity] { (store.index.emails + store.index.phones).filter { $0.id != existing?.id } }
  var trimmed: String { value.trimmingCharacters(in: .whitespaces) }
  var kindKey: String { isLogin ? provider : type }
  var dup: Identity? { store.existing(type: type, provider: isLogin ? provider : nil, value: trimmed, except: existing?.id) }
  var viaNewError: String? {
    guard isLogin, addNew else { return nil }
    let v = viaNew.trimmingCharacters(in: .whitespaces)
    return v.isEmpty ? nil : idValueError(v.contains("@") ? "email" : "phone", v)
  }
  var valid: Bool {
    if trimmed.isEmpty || dup != nil || viaNewError != nil { return false }
    return idValueError(kindKey, trimmed) == nil
  }
  func isLoginSkin(_ c: String) -> Bool { LOGIN_SKINS.contains { $0.0 == c } }

  var preview: Identity {
    var i = Identity(id: existing?.id ?? "_new", type: type, value: value.isEmpty ? sample : value, label: label, color: color, notes: notes,
                     esim: type == "phone" && esim, provider: isLogin ? provider : nil, apple: !isLogin && apple)
    if isLogin { i.setBinds(binds) }
    i.order = existing?.order
    return i
  }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          WalletCard(identity: preview, reveal: true)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .animation(Motion.smooth, value: color)
            .animation(Motion.smooth, value: type)
          Picker("类型", selection: $type) {
            Text("邮箱").tag("email")
            Text("手机号").tag("phone")
            Text("微信 / QQ").tag("login")
          }.pickerStyle(.segmented)
        }
        .listRowBackground(Color.clear).listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
        if isLogin {
          Section {
            HStack(spacing: 10) {
              providerPill("wechat", "微信")
              providerPill("qq", "QQ")
            }
            .listRowBackground(Color.clear).listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
          }
        }
        Section {
          TextField(placeholder, text: $value)
            .keyboardType(type == "email" ? .emailAddress : type == "phone" ? .phonePad : (isLogin && provider == "qq") ? .numbersAndPunctuation : .asciiCapable)
            .textInputAutocapitalization(.never).autocorrectionDisabled()
          TextField("标签：主力、小号、工作…", text: $label)
        } header: { Text(fieldTitle) } footer: {
          if let d = dup { Text("已经有这个\(d.kindName)了（\(store.title(d))），不用重复添加。") }
          else if isLogin && provider == "qq" && !trimmed.isEmpty && !valid { Text("QQ 号是 5 到 12 位数字，或填 QQ 邮箱。") }
        }
        if !isLogin {
          Section {
            Toggle(isOn: $apple.animation(Motion.smooth)) { Label("也是 Apple ID", systemImage: "apple.logo") }
          } footer: { Text("Apple ID 就是某个邮箱或手机号，打开这个开关就行，不用单独建卡。") }
        }
        if type == "phone" {
          Section {
            Toggle("eSIM", isOn: $esim.animation(Motion.smooth))
          } footer: { Text("eSIM 没有实体卡，换手机时记得转移。") }
        }
        if isLogin {
          Section {
            ForEach(bindable) { i in
              let on = binds.contains(i.id)
              Button {
                withAnimation(Motion.snappy) { if on { binds.removeAll { $0 == i.id } } else { binds.append(i.id) } }
              } label: {
                HStack(spacing: 12) {
                  IdentityGlyph(identity: i, size: 12).foregroundStyle(.white).frame(width: 36, height: 24)
                    .background(skinGradient(i.color), in: CardShape(sim: i.isPhone))
                  VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 5) {
                      Text(store.title(i)).foregroundStyle(.primary).lineLimit(1)
                      if i.isAppleID { AppleBadge() }
                    }
                    Text(i.kindName + (i.label.isEmpty ? "" : " · " + store.display(i))).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
                  }
                  Spacer()
                  Image(systemName: on ? "checkmark.circle.fill" : "circle").font(.system(size: 22))
                    .foregroundStyle(on ? Color(hex: i.color) : Color.secondary.opacity(0.5))
                }
              }
              .sensoryFeedback(.selection, trigger: on)
            }
            Toggle("新的手机号或邮箱", isOn: $addNew.animation(Motion.snappy))
            if addNew {
              TextField("手机号或邮箱", text: $viaNew).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
          } header: { Text("绑定的手机号/邮箱（可多选）") } footer: {
            if let e = viaNewError { Text(e) } else { Text("从已有的卡里选，不用再填一遍。QQ 同时绑了手机号和 QQ 邮箱，就两个都选上。") }
          }
        }
        Section("卡面") {
          LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 5), spacing: 12) {
            ForEach(isLogin ? Array(LOGIN_SKINS.prefix(2)) + SKINS : SKINS, id: \.0) { s in
              Button { colorTouched = true; withAnimation(Motion.snappy) { color = s.0 } } label: {
                VStack(spacing: 4) {
                  RoundedRectangle(cornerRadius: 8, style: .continuous).fill(skinGradient(s.0)).frame(height: 34)
                    .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(Color.primary, lineWidth: color == s.0 ? 2.5 : 0).padding(-3))
                  Text(s.1.name).font(.system(size: 11)).foregroundStyle(color == s.0 ? .primary : .secondary)
                }
              }.buttonStyle(.plain)
            }
          }.padding(.vertical, 6)
        }
        .sensoryFeedback(.selection, trigger: color)
        Section {
          TextField(isLogin ? "实名情况、找回方式" : "运营商、是否实名、找回方式", text: $notes, axis: .vertical).lineLimit(2...6)
        } header: { Text("备注") } footer: { Text("不要在这里填写密码。") }
      }
      .scrollContentBackground(.hidden)
      .background(skinGradient(color).opacity(0.18).ignoresSafeArea())
      .navigationTitle(existing == nil ? "新身份" : "编辑").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
        ToolbarItem(placement: .confirmationAction) { Button(existing == nil ? "添加" : "保存") { save() }.bold().disabled(!valid) }
      }
      .onAppear { binds = binds.filter { b in bindable.contains { $0.id == b } } }
      .onChange(of: type) { old, new in
        if colorTouched { return }
        withAnimation(Motion.snappy) {
          if new == "login" && !isLoginSkin(color) { color = loginDefaultColor(provider) }
          else if old == "login" && new != "login" && isLoginSkin(color) {
            if let e = existing, !e.isLogin { color = e.color } else { color = baseColor }
          }
        }
      }
      .onChange(of: provider) { _, new in
        if !colorTouched && isLoginSkin(color) { withAnimation(Motion.snappy) { color = loginDefaultColor(new) } }
      }
    }
  }

  var fieldTitle: String {
    switch type {
    case "email": return "地址"
    case "phone": return "号码"
    default: return provider == "qq" ? "QQ 号" : "微信号"
    }
  }

  func providerPill(_ p: String, _ name: String) -> some View {
    let on = provider == p
    return Button { withAnimation(Motion.snappy) { provider = p } } label: {
      HStack(spacing: 6) {
        LoginGlyph(provider: p, size: 15, filled: true)
        Text(name).font(.system(size: 15, weight: .semibold))
      }
      .frame(maxWidth: .infinity).frame(height: 40)
      .foregroundStyle(on ? Color.white : Color.primary)
      .background(on ? AnyShapeStyle(skinGradient(loginDefaultColor(p))) : AnyShapeStyle(.glass), in: Capsule())
    }
    .buttonStyle(PressStyle(scale: 0.96))
    .sensoryFeedback(.selection, trigger: on)
  }

  func save() {
    let v = trimmed
    let isL = isLogin
    let prov: String? = isL ? provider : nil
    var bound: [String] = isL ? binds : []
    if isL && addNew {
      let n = viaNew.trimmingCharacters(in: .whitespaces)
      if !n.isEmpty { let id = store.ensureIdentity(type: n.contains("@") ? "email" : "phone", value: n).0; if !bound.contains(id) { bound.append(id) } }
    }
    // a QQ whose QQ is an email you already have: bind it to that email too, instead of typing it twice
    if isL && provider == "qq" && v.contains("@"),
       let m = store.data.identities.first(where: { $0.type == "email" && normEmail($0.value) == normEmail(v) }), !bound.contains(m.id) { bound.append(m.id) }
    let ap = !isL && apple
    if let e = existing {
      store.mutate("已保存") { s in
        if let k = s.identities.firstIndex(where: { $0.id == e.id }) {
          s.identities[k].type = type; s.identities[k].value = v; s.identities[k].label = label.trimmingCharacters(in: .whitespaces)
          s.identities[k].color = color; s.identities[k].notes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
          s.identities[k].esim = (type == "phone" && esim) ? true : nil
          s.identities[k].apple = ap ? true : nil
          s.identities[k].provider = prov
          s.identities[k].demo = nil   // edited by the user: a sample card becomes theirs
          if isL { s.identities[k].setBinds(bound) }
        }
      }
      store.setKind(type, focus: e.id)
    } else {
      var i = Identity(type: type, value: v, label: label.trimmingCharacters(in: .whitespaces), color: color, notes: notes.trimmingCharacters(in: .whitespacesAndNewlines),
                       esim: type == "phone" && esim, provider: prov, apple: ap)
      if isL { i.setBinds(bound) }
      store.mutate("已添加") { s in
        s.identities.append(i)
      }
      store.setKind(type, focus: i.id)
    }
    dismiss()
  }
}

struct AccountForm: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let existing: Account
  @State private var name: String
  @State private var domain: String
  @State private var category: String
  @State private var handle: String
  @State private var notes: String
  @State private var ids: [String]
  @State private var icon: String?
  @State private var art: String? = nil          // App Store artwork shown while its icon downloads
  @State private var iconJob = 0                 // newest icon change wins over a slower download
  @State private var typed = false               // suggestions only after the user edits the name
  @State private var appResults: [AppStoreApp] = []
  @State private var iconMenu = false
  @State private var photos = false
  @State private var photoItem: PhotosPickerItem? = nil
  @FocusState private var nameFocused: Bool

  init(existing: Account) {
    self.existing = existing
    _name = State(initialValue: existing.name); _domain = State(initialValue: existing.domain)
    _category = State(initialValue: existing.category); _handle = State(initialValue: existing.handle ?? "")
    _notes = State(initialValue: existing.notes); _ids = State(initialValue: existing.identityIds)
    _icon = State(initialValue: existing.icon)
  }

  var query: String { typed && nameFocused ? name.trimmingCharacters(in: .whitespaces) : "" }
  var presetHits: [Preset] {
    let q = query.lowercased()
    guard !q.isEmpty else { return [] }
    return Array((Presets.all + Presets.directory).filter { siteHit($0, q) }.prefix(4))
  }
  var storeHits: [AppStoreApp] {
    let shown = Set(presetHits.map { $0.name.lowercased() })
    return appResults.filter { !shown.contains($0.cleanName.lowercased()) && !shown.contains($0.trackName.lowercased()) }
  }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          HStack(spacing: 14) {
            Button { iconMenu = true } label: {
              AppIcon(name: name, domain: domain, icon: icon, url: icon == nil ? art : nil, size: 54)
                .overlay(alignment: .bottomTrailing) {
                  Image(systemName: "camera.fill").font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 20, height: 20).background(Circle().fill(Color.accentColor)).overlay(Circle().stroke(.background, lineWidth: 2))
                    .offset(x: 5, y: 5)
                }
            }
            .buttonStyle(PressStyle(scale: 0.92))
            .accessibilityLabel("更换图标")
            VStack(spacing: 8) {
              TextField("名称", text: $name).font(.system(size: 18, weight: .semibold)).focused($nameFocused)
              TextField("域名（可选）", text: $domain).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled().foregroundStyle(.secondary)
            }
          }.padding(.vertical, 4)
          TextField("账号名（可选）", text: $handle).textInputAutocapitalization(.never).autocorrectionDisabled()
          Picker("分类", selection: $category) { ForEach(CATS, id: \.self) { Text($0).tag($0) } }
        }
        if !presetHits.isEmpty || !storeHits.isEmpty {
          Section {
            ForEach(presetHits, id: \.name) { p in
              suggestion(AppIcon(name: p.name, domain: p.domain, size: 32), p.name, p.domain) { pick(p) }
            }
            if !storeHits.isEmpty {
              AppStoreBadge().padding(.top, presetHits.isEmpty ? 0 : 4)
              ForEach(storeHits) { a in
                suggestion(AppIcon(name: a.cleanName, domain: a.domain, url: a.thumbURL, size: 32), a.cleanName, a.domain.isEmpty ? a.category : a.domain) { pick(a) }
              }
            }
          } header: { Text("建议") }
          .transition(.opacity)
        }
        Section {
          IdentityPicks(selected: $ids)
        } header: { Text("怎么登录 / 用哪个身份") } footer: { Text("可以多选：比如用邮箱注册、再绑了手机号，或者用微信、QQ 登录。") }
        Section("备注") { TextField("两步验证、找回方式…", text: $notes, axis: .vertical).lineLimit(2...6) }
      }
      .animation(Motion.snappy, value: presetHits.count + storeHits.count)
      .onChange(of: name) { if nameFocused { typed = true } }
      .appStoreResults(for: query, into: $appResults)
      .confirmationDialog("更换图标", isPresented: $iconMenu, titleVisibility: .visible) {
        Button("从相册选择") { photos = true }
        if icon != nil || art != nil { Button("使用默认图标") { setIcon(nil) } }
        Button("取消", role: .cancel) {}
      }
      .photosPicker(isPresented: $photos, selection: $photoItem, matching: .images)
      .onChange(of: photoItem) {
        guard let item = photoItem else { return }
        photoItem = nil
        iconJob &+= 1; let job = iconJob
        Task {
          let u = await IconCodec.dataURL(from: item)
          guard job == iconJob else { return }
          if let u { withAnimation(Motion.snappy) { icon = u; art = nil } } else { store.show("这张图片读不出来") }
        }
      }
      .navigationTitle("编辑").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
        ToolbarItem(placement: .confirmationAction) {
          Button("保存") {
            store.mutate("已保存") { s in
              if let k = s.accounts.firstIndex(where: { $0.id == existing.id }) {
                s.accounts[k].name = name.trimmingCharacters(in: .whitespaces); s.accounts[k].domain = cleanDomain(domain)
                s.accounts[k].category = category; s.accounts[k].notes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
                let h = handle.trimmingCharacters(in: .whitespaces); s.accounts[k].handle = h.isEmpty ? nil : h
                s.accounts[k].identityIds = ids
                s.accounts[k].icon = icon
                s.accounts[k].demo = nil   // edited by the user: a sample app becomes theirs
              }
            }
            IconStore.shared.prefetch(store.data.accounts.filter { $0.id == existing.id })
            dismiss()
          }.bold().disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
        }
      }
    }
  }

  func suggestion(_ icon: some View, _ title: String, _ sub: String, _ action: @escaping () -> Void) -> some View {
    Button(action: action) {
      HStack(spacing: 12) {
        icon
        VStack(alignment: .leading, spacing: 1) {
          Text(title).foregroundStyle(.primary).lineLimit(1)
          if !sub.isEmpty { Text(sub).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1) }
        }
        Spacer(minLength: 0)
      }
    }
  }

  func setIcon(_ v: String?) {
    iconJob &+= 1
    withAnimation(Motion.snappy) { icon = v; art = nil }
  }
  func pick(_ p: Preset) {
    typed = false; nameFocused = false
    name = p.name; domain = p.domain; category = p.category
    setIcon(nil)
  }
  func pick(_ a: AppStoreApp) {
    typed = false; nameFocused = false
    name = a.cleanName
    if !a.domain.isEmpty { domain = a.domain }
    category = a.category
    iconJob &+= 1; let job = iconJob
    withAnimation(Motion.snappy) { icon = nil; art = a.thumbURL.isEmpty ? nil : a.thumbURL }
    Task {
      let u = await a.iconDataURL()
      guard job == iconJob else { return }
      withAnimation(Motion.snappy) { icon = u; art = nil }
    }
  }
}

/// 怎么登录 / 用哪个身份: every email, phone, 微信 and QQ in one list (Apple IDs badged), and 新建 only
/// when none fits. A value that already exists is never typed in again: 新建 picks the existing card.
struct IdentityPicks: View {
  @Environment(Store.self) var store
  @Binding var selected: [String]
  @State private var creating = false
  /// 2.5: a 微信 / QQ sits right under the (first) phone or email it is bound to; unbound ones come last
  var list: [Identity] {
    let x = store.index
    let logins = x.logins.filter { !$0.isQQ } + x.logins.filter(\.isQQ)
    var under: [String: [Identity]] = [:], loose: [Identity] = []
    for l in logins { if let v = store.via(l) { under[v.id, default: []].append(l) } else { loose.append(l) } }
    return (x.emails + x.phones).flatMap { [$0] + (under[$0.id] ?? []) } + loose
  }
  var body: some View {
    ForEach(list) { i in
      let on = selected.contains(i.id)
      let nested = i.isLogin && store.via(i) != nil
      Button {
        withAnimation(Motion.snappy) { if on { selected.removeAll { $0 == i.id } } else { selected.append(i.id) } }
      } label: {
        HStack(spacing: 12) {
          IdentityGlyph(identity: i, size: 12).foregroundStyle(.white).frame(width: 36, height: 24)
            .background(skinGradient(i.color), in: CardShape(sim: i.isPhone))
          VStack(alignment: .leading, spacing: 1) {
            HStack(spacing: 5) {
              Text(i.isLogin ? i.kindName + " " + store.display(i) + (bindTags(store.binds(i)).isEmpty ? "" : " · 绑定 " + bindTags(store.binds(i))) : store.title(i)).foregroundStyle(.primary).lineLimit(1)
              if i.isAppleID { AppleBadge() }
            }
            if let s = sub(i) { Text(s).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1) }
          }
          Spacer()
          Image(systemName: on ? "checkmark.circle.fill" : "circle").font(.system(size: 22))
            .foregroundStyle(on ? Color(hex: i.color) : Color.secondary.opacity(0.5))
            .symbolEffect(.bounce, value: on)
        }
        .padding(.leading, nested ? 18 : 0)
        .background { if i.id == list.first?.id { TourAnchor(id: "who") } }
      }
      .sensoryFeedback(.selection, trigger: on)
    }
    Button { creating = true } label: {
      Label(list.isEmpty ? "新建身份（邮箱、手机号、微信或 QQ）" : "新建身份", systemImage: "plus.circle.fill")
    }
    .sheet(isPresented: $creating) {
      QuickIdentitySheet { id in withAnimation(Motion.snappy) { if !selected.contains(id) { selected.append(id) } } }
        .environment(store)
    }
  }
  func sub(_ i: Identity) -> String? {
    if i.isLogin { return i.label.isEmpty ? nil : store.labelText(i.label) }
    return i.label.isEmpty ? nil : store.display(i)
  }
}

struct AppleBadge: View {
  var body: some View {
    HStack(spacing: 2) {
      Image(systemName: "apple.logo").font(.system(size: 9, weight: .semibold))
      Text("Apple ID").font(.system(size: 10, weight: .semibold))
    }
    .foregroundStyle(.secondary)
    .padding(.horizontal, 5).padding(.vertical, 2)
    .background(Color.secondary.opacity(0.15), in: RoundedRectangle(cornerRadius: 5, style: .continuous))
  }
}

/// 新建身份 from the App flow: kind, value and (微信 / QQ) the email or phone it's bound to, picked
/// from the existing cards. If the value is already there, that card is picked instead.
struct QuickIdentitySheet: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let onDone: (String) -> Void
  @State private var kind = "email"
  @State private var value = ""
  @State private var via = ""
  @FocusState private var focused: Bool

  var isLogin: Bool { kind == "wechat" || kind == "qq" }
  var type: String { isLogin ? "login" : kind }
  var trimmed: String { value.trimmingCharacters(in: .whitespaces) }
  var hit: Identity? { store.existing(type: type, provider: isLogin ? kind : nil, value: trimmed) }
  var error: String? { trimmed.isEmpty ? nil : idValueError(kind, trimmed) }
  var placeholder: String {
    switch kind {
    case "phone": return "+86 138 0000 0000"
    case "wechat": return "wxid 或自定义微信号"
    case "qq": return "QQ 号或 QQ 邮箱"
    default: return "name@example.com"
    }
  }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          Picker("类型", selection: $kind) {
            Text("邮箱").tag("email")
            Text("手机号").tag("phone")
            Text("微信").tag("wechat")
            Text("QQ").tag("qq")
          }.pickerStyle(.segmented)
          TextField(placeholder, text: $value)
            .keyboardType(kind == "email" ? .emailAddress : kind == "phone" ? .phonePad : kind == "qq" ? .numbersAndPunctuation : .asciiCapable)
            .textInputAutocapitalization(.never).autocorrectionDisabled().focused($focused)
        } footer: {
          if let h = hit { Text("已经有这个\(h.kindName)了（\(store.title(h))），会直接选上它。") }
          else if let e = error { Text(e) }
        }
        if isLogin && hit == nil {
          Section {
            Picker("绑定的手机号/邮箱", selection: $via) {
              Text("无").tag("")
              ForEach(store.index.emails + store.index.phones) { i in Text(i.kindName + " · " + store.title(i)).tag(i.id) }
            }
          } footer: { Text("从已有的卡里选。列表里没有，可以之后在卡片里补上。") }
        }
      }
      .navigationTitle("新建身份").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
        ToolbarItem(placement: .confirmationAction) {
          Button(hit == nil ? "添加并选上" : "选上它") { done() }.bold().disabled(trimmed.isEmpty || (hit == nil && error != nil))
        }
      }
      .onAppear { focused = true }
    }
    .presentationDetents([.medium, .large])
  }

  func done() {
    if let h = hit { onDone(h.id); dismiss(); return }
    let r = store.ensureIdentity(type: type, value: trimmed, provider: isLogin ? kind : nil, via: isLogin && !via.isEmpty ? via : nil)
    onDone(r.0)
    dismiss()
  }
}

// MARK: - add apps: pick sites, then pick which email / phone each uses

struct PickerSheet: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let presetIdentity: String?
  @State private var query = ""
  @State private var cat = "常用"
  @State private var picked: [Preset] = []
  @State private var handles: [String: String] = [:]
  @State private var ids: [String] = []
  @State private var step2 = false
  @State private var manual = false
  @State private var mName = ""
  @State private var removing: Preset? = nil          // 我添加的 tile waiting on the remove confirmation
  @State private var appResults: [AppStoreApp] = []
  @State private var art: [String: String] = [:]      // App Store artwork per picked key, until its icon lands
  @State private var storeFor: [String: AppStoreApp] = [:]  // picked key -> the App Store app its icon comes from
  @State private var iconFor: String? = nil           // picked key whose icon menu is open
  @State private var uploadFor: String? = nil         // picked key the photo picker is choosing for
  @State private var photos = false
  @State private var photoItem: PhotosPickerItem? = nil

  var cats: [String] {
    (store.customApps.isEmpty ? [] : ["我添加的"]) + ["常用"] + CATS.filter { c in Presets.all.contains { $0.category == c } }
  }
  var list: [Preset] {
    let q = query.trimmingCharacters(in: .whitespaces).lowercased()
    let all = store.customApps + Presets.all
    if !q.isEmpty {
      let names = Set(all.map { $0.name.lowercased() })
      return Array((all + Presets.directory.filter { !names.contains($0.name.lowercased()) }).filter { siteHit($0, q) }.prefix(60))
    }
    switch cat {
    case "我添加的": return store.customApps
    case "常用": return Array(Presets.popular.compactMap { n in Presets.all.first { $0.name == n } }.prefix(12))
    default: return Presets.all.filter { $0.category == cat }
    }
  }
  func key(_ p: Preset) -> String { p.name.lowercased() }
  var storeHits: [AppStoreApp] {
    guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
    let shown = Set(list.map(key))
    return appResults.filter { !shown.contains($0.cleanName.lowercased()) && !shown.contains($0.trackName.lowercased()) }
  }
  func preset(_ a: AppStoreApp) -> Preset { Preset(name: a.cleanName, domain: a.domain, category: a.category, color: "") }
  /// a listed app with no icon of its own (not bundled, none uploaded) borrows the App Store artwork
  /// of the store result with the same name, instead of a blurry favicon or a parent site's logo
  func storeMatch(_ p: Preset) -> AppStoreApp? {
    guard p.icon == nil, !IconStore.shared.key(name: p.name, domain: p.domain).hasPrefix("b:") else { return nil }
    let k = key(p)
    return appResults.first { $0.cleanName.lowercased() == k || $0.trackName.lowercased() == k }
  }
  /// picked from the App Store (or matched to it): show its artwork now, store the real icon when it lands
  func fetchIcon(_ k: String, _ a: AppStoreApp) {
    if !a.thumbURL.isEmpty { art[k] = a.thumbURL }
    storeFor[k] = a
    Task {
      let u = await a.iconDataURL()
      // only if it's still picked and the user hasn't chosen another icon meanwhile
      guard art[k] != nil, let u else { art[k] = nil; return }
      setIcon(k, u)
    }
  }
  func setIcon(_ k: String, _ v: String?) {
    if let i = picked.firstIndex(where: { key($0) == k }) { picked[i].icon = v }
    art[k] = nil; storeFor[k] = nil
  }

  var body: some View {
    NavigationStack {
      Group { if step2 { chooseIdentities } else { grid } }
        .animation(Motion.smooth, value: step2)
    }
    // during the 操作演示 the identity is picked by hand, as a step of its own
    .onAppear { if let p = presetIdentity, store.tourStep == nil { ids = [p] }; store.tourAdvance("add", "pick") }
    .onChange(of: picked.count) { _, n in if n > 0 { store.tourAdvance("pick", "next") } else { store.tourAdvance("next", "pick") } }
    .onChange(of: step2) { _, on in
      if on { store.tourAdvance("next", "who") } else if store.tourStep == "who" || store.tourStep == "save" { store.tourStep = "next" }
    }
    .onChange(of: ids) { _, v in if v.isEmpty { store.tourAdvance("save", "who") } else { store.tourAdvance("who", "save") } }
    .overlay { TourLayer(inPicker: true).animation(Motion.smooth, value: store.tourStep) }
  }

  var grid: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 14) {
        ScrollView(.horizontal) {
          HStack(spacing: 8) {
            ForEach(cats, id: \.self) { c in
              Button { withAnimation(.snappy) { cat = c } } label: {
                Text(c).font(.system(size: 14, weight: .semibold)).padding(.horizontal, 13).frame(height: 32)
                  .foregroundStyle(cat == c && query.isEmpty ? Color.white : Color.primary)
                  .background(cat == c && query.isEmpty ? AnyShapeStyle(Color.accentColor) : AnyShapeStyle(.glass), in: Capsule())
              }.buttonStyle(PressStyle(scale: 0.94))
            }
          }.padding(.horizontal, 16)
        }.scrollIndicators(.hidden)
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6, alignment: .top), count: 4), spacing: 18) {
          ForEach(list, id: \.self) { p in
            if cat == "我添加的" && query.trimmingCharacters(in: .whitespaces).isEmpty {
              tile(p)
                .contextMenu {
                  Button(role: .destructive) { askRemove(p) } label: { Label("从列表移除", systemImage: "minus.circle") }
                }
                .transition(.scale(scale: 0.6).combined(with: .opacity))
            } else { tile(p) }
          }
          Button { mName = query; manual = true } label: {
            VStack(spacing: 6) {
              Image(systemName: "plus").font(.system(size: 22, weight: .medium)).frame(width: 60, height: 60)
                .background(.glass, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
              Text(query.isEmpty ? "手动添加" : "添加“\(query)”").font(.system(size: 12)).lineLimit(1).foregroundStyle(.secondary)
            }
          }.buttonStyle(PressStyle(scale: 0.9))
        }.padding(.horizontal, 12)
        if !storeHits.isEmpty {
          AppStoreBadge().padding(.horizontal, 18).padding(.top, 4)
          LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6, alignment: .top), count: 4), spacing: 18) {
            ForEach(storeHits) { a in storeTile(a) }
          }.padding(.horizontal, 12)
          .transition(.opacity)
        }
      }.padding(.top, 6).padding(.bottom, 90)
    }
    .scrollIndicators(.hidden)
    .appStoreResults(for: query, into: $appResults)
    .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "搜索 App 或网站")
    .navigationTitle("添加网站或 App").navigationBarTitleDisplayMode(.inline)
    .toolbar {
      ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
      ToolbarItem(placement: .confirmationAction) { Button(picked.isEmpty ? "下一步" : "下一步 (\(picked.count))") { step2 = true }.bold().disabled(picked.isEmpty) }
    }
    .safeAreaInset(edge: .bottom) { if !picked.isEmpty { pickedBar } }
    .sensoryFeedback(.selection, trigger: picked.count)
    .sheet(isPresented: $manual) {
      ManualAppSheet(initialName: mName) { p in
        store.rememberApp(p)
        withAnimation(Motion.snappy) {
          if let i = picked.firstIndex(where: { key($0) == key(p) }) { picked[i] = p } else { picked.append(p) }
        }
        mName = ""; query = ""
      }
    }
    .confirmationDialog("从列表移除“\(removing?.name ?? "")”？", isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }), titleVisibility: .visible) {
      Button("从列表移除", role: .destructive) { if let p = removing { remove(p) }; removing = nil }
      Button("取消", role: .cancel) { removing = nil }
    } message: { Text("只会从这个列表移除，已记录的账号不受影响。") }
    .onChange(of: store.customApps.isEmpty) { _, empty in
      if empty && cat == "我添加的" { withAnimation(Motion.snappy) { cat = "常用" } }
    }
  }

  func hasAccounts(_ p: Preset) -> Bool { store.data.accounts.contains { $0.name.lowercased() == p.name.lowercased() } }
  func askRemove(_ p: Preset) {
    if hasAccounts(p) { removing = p } else { remove(p) }
  }
  func remove(_ p: Preset) {
    withAnimation(Motion.snappy) {
      store.hideApp(p.name)
      picked.removeAll { key($0) == key(p) }
      if store.customApps.isEmpty && cat == "我添加的" { cat = "常用" }
    }
  }

  func tile(_ p: Preset) -> some View {
    let on = picked.contains { key($0) == key(p) }
    let has = hasAccounts(p)
    let match = storeMatch(p)
    return Button {
      withAnimation(Motion.snappy) {
        if on { picked.removeAll { key($0) == key(p) }; art[key(p)] = nil; storeFor[key(p)] = nil; return }
        picked.append(p)
        if let match { fetchIcon(key(p), match) }
      }
    } label: {
      VStack(spacing: 6) {
        AppIcon(name: p.name, domain: p.domain, icon: p.icon, url: match?.thumbURL, size: 60)
          .overlay(alignment: .topTrailing) {
            if on {
              Image(systemName: "checkmark.circle.fill").font(.system(size: 22)).symbolRenderingMode(.palette)
                .foregroundStyle(.white, Color.accentColor).background(Circle().fill(.white).padding(3)).offset(x: 7, y: -7)
                .transition(.scale.combined(with: .opacity))
            }
          }
          .scaleEffect(on ? 0.92 : 1)
        Text(p.name).font(.system(size: 12)).lineLimit(1).foregroundStyle(.primary)
        if has { Text("已记录").font(.system(size: 9.5, weight: .medium)).foregroundStyle(.secondary).padding(.top, -4) }
      }
      .background { if key(p) == list.first.map(key) { TourAnchor(id: "tile") } }
    }.buttonStyle(.plain)
  }

  func storeTile(_ a: AppStoreApp) -> some View {
    let p = preset(a), k = key(p)
    let on = picked.contains { key($0) == k }
    return Button {
      withAnimation(Motion.snappy) {
        if on { picked.removeAll { key($0) == k }; art[k] = nil; storeFor[k] = nil; return }
        picked.append(p)
        fetchIcon(k, a)
      }
    } label: {
      VStack(spacing: 6) {
        AppIcon(name: p.name, domain: p.domain, url: a.thumbURL, size: 60)
          .overlay(alignment: .topTrailing) {
            if on {
              Image(systemName: "checkmark.circle.fill").font(.system(size: 22)).symbolRenderingMode(.palette)
                .foregroundStyle(.white, Color.accentColor).background(Circle().fill(.white).padding(3)).offset(x: 7, y: -7)
                .transition(.scale.combined(with: .opacity))
            }
          }
          .scaleEffect(on ? 0.92 : 1)
        Text(p.name).font(.system(size: 12)).lineLimit(1).foregroundStyle(.primary)
      }
    }.buttonStyle(.plain)
  }
  func pickedIcon(_ p: Preset, size: CGFloat) -> AppIcon {
    AppIcon(name: p.name, domain: p.domain, icon: p.icon, url: p.icon == nil ? art[key(p)] : nil, size: size)
  }

  // the picked icons, plus its own 下一步: while the search field is active iOS hides the navigation
  // bar's buttons, so without this there was no way forward from a search
  var pickedBar: some View {
    HStack(spacing: 0) {
      ScrollView(.horizontal) {
        HStack(spacing: 10) {
          ForEach(picked, id: \.name) { p in
            pickedIcon(p, size: 38).transition(.scale.combined(with: .opacity))
          }
        }.padding(.horizontal, 16).padding(.vertical, 10)
      }
      .scrollIndicators(.hidden)
      Button { step2 = true } label: {
        Text("下一步 (\(picked.count))").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
          .padding(.horizontal, 16).frame(height: 38).background(Color.accentColor, in: Capsule())
          .background(TourAnchor(id: "next"))
      }
      .buttonStyle(PressStyle(scale: 0.94)).padding(.trailing, 16)
    }
    .background(.bar)
  }

  var chooseIdentities: some View {
    Form {
      Section {
        ForEach(picked, id: \.name) { p in
          HStack(spacing: 12) {
            Button { iconFor = key(p) } label: { pickedIcon(p, size: 36) }
              .buttonStyle(PressStyle(scale: 0.9)).accessibilityLabel("更换图标")
            VStack(alignment: .leading, spacing: 2) {
              Text(p.name).font(.system(size: 16, weight: .semibold))
              TextField("账号名（可选）", text: Binding(get: { handles[key(p)] ?? "" }, set: { handles[key(p)] = $0 }))
                .font(.system(size: 14)).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
          }
        }
      } header: { Text("\(picked.count) 个网站与 App") } footer: { Text("同一个网站有几个账号，可以分别记一条，用账号名区分。点图标可以换成相册里的图片。") }
      Section {
        IdentityPicks(selected: $ids)
      } header: { Text("怎么登录 / 用哪个身份") } footer: { Text("可以多选：比如用邮箱注册、再绑了手机号，或者用微信、QQ 登录。列表里没有的，点「新建身份」。") }
    }
    .confirmationDialog("更换图标", isPresented: Binding(get: { iconFor != nil }, set: { if !$0 { iconFor = nil } }), titleVisibility: .visible) {
      Button("从相册选择") { uploadFor = iconFor; iconFor = nil; photos = true }
      if let k = iconFor, let p = picked.first(where: { key($0) == k }), p.icon != nil || art[k] != nil {
        Button("使用默认图标") { withAnimation(Motion.snappy) { setIcon(k, nil) }; iconFor = nil }
      }
      Button("取消", role: .cancel) { iconFor = nil }
    }
    .photosPicker(isPresented: $photos, selection: $photoItem, matching: .images)
    .onChange(of: photoItem) {
      guard let item = photoItem, let k = uploadFor else { return }
      photoItem = nil
      art[k] = nil; storeFor[k] = nil   // a pending App Store download no longer applies
      Task {
        if let u = await IconCodec.dataURL(from: item) { withAnimation(Motion.snappy) { setIcon(k, u) } } else { store.show("这张图片读不出来") }
      }
    }
    .navigationTitle("怎么登录").navigationBarTitleDisplayMode(.inline)
    .toolbar {
      ToolbarItem(placement: .cancellationAction) { Button { step2 = false } label: { Label("返回", systemImage: "chevron.left") } }
      ToolbarItem(placement: .confirmationAction) { Button { save() } label: { Text("保存").bold().background(TourAnchor(id: "save")) } }
    }
  }

  func save() {
    let now = nowMS()
    let made: [Account] = picked.enumerated().map { e in
      let k = e.offset, p = e.element
      return Account(name: p.name, domain: p.domain, category: p.category, identityIds: ids,
              handle: (handles[key(p)] ?? "").trimmingCharacters(in: .whitespaces).isEmpty ? nil : handles[key(p)]!.trimmingCharacters(in: .whitespaces),
              createdAt: now - Double(k), icon: p.icon)
    }
    store.unhideApps(made.map(\.name))
    store.mutate(made.count == 1 ? "已添加 \(made[0].name)" : "已添加 \(made.count) 个网站与 App") { s in
      s.accounts.append(contentsOf: made)
    }
    IconStore.shared.prefetch(made)
    store.tourSaved()
    // App Store icons still downloading when 保存 was tapped: attach them to the new accounts when they land
    let late = made.compactMap { m in m.icon == nil ? storeFor[m.name.lowercased()].map { (m.id, $0) } : nil }
    if !late.isEmpty {
      let st = store
      Task { @MainActor in
        for (id, a) in late {
          guard let u = await a.iconDataURL() else { continue }
          guard st.data.accounts.contains(where: { $0.id == id && $0.icon == nil }) else { continue }
          st.mutate(nil, undo: false) { s in
            if let i = s.accounts.firstIndex(where: { $0.id == id }) { s.accounts[i].icon = u }
          }
          IconStore.shared.prefetch(st.data.accounts.filter { $0.id == id })
        }
      }
    }
    dismiss()
  }
}

// MARK: - 手动添加: name, domain and an icon slot (photo or the default letter / favicon)

struct ManualAppSheet: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let onAdd: (Preset) -> Void
  @State private var name: String
  @State private var domain = ""
  @State private var icon: String? = nil
  @State private var iconMenu = false
  @State private var photos = false
  @State private var photoItem: PhotosPickerItem? = nil
  @State private var iconJob = 0
  @State private var webHits: [SiteHit] = []          // official sites found online for a name we don't know
  @FocusState private var nameFocused: Bool

  init(initialName: String, onAdd: @escaping (Preset) -> Void) {
    self.onAdd = onAdd
    _name = State(initialValue: initialName.trimmingCharacters(in: .whitespaces))
  }

  var trimmed: String { name.trimmingCharacters(in: .whitespaces) }
  /// sites we know that match the name, so the user never has to look the domain up
  var localHits: [SiteHit] {
    let q = trimmed.lowercased()
    guard !q.isEmpty, cleanDomain(domain).isEmpty else { return [] }
    return Array((Presets.all + Presets.directory).filter { siteHit($0, q) }.prefix(3)).map { SiteHit(label: $0.name, domain: $0.domain) }
  }
  var domainHits: [SiteHit] { cleanDomain(domain).isEmpty ? (localHits.isEmpty ? webHits : localHits) : [] }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          VStack(spacing: 10) {
            Button { iconMenu = true } label: {
              AppIcon(name: trimmed.isEmpty ? "?" : trimmed, domain: cleanDomain(domain), icon: icon, size: 72)
                .overlay(alignment: .bottomTrailing) {
                  Image(systemName: icon == nil ? "camera.fill" : "pencil").font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 24, height: 24).background(Circle().fill(Color.accentColor)).overlay(Circle().stroke(.background, lineWidth: 2))
                    .offset(x: 6, y: 6)
                }
            }
            .buttonStyle(PressStyle(scale: 0.92))
            .accessibilityLabel("更换图标")
            Text("点图标可以换成相册里的图片").font(.system(size: 12)).foregroundStyle(.secondary)
          }
          .frame(maxWidth: .infinity).padding(.vertical, 6)
        }
        .listRowBackground(Color.clear)
        Section {
          TextField("名称", text: $name).font(.system(size: 17, weight: .semibold)).focused($nameFocused)
            .submitLabel(.done).onSubmit { add() }
          TextField("网址（可选）", text: $domain).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
        } footer: { Text(domainHits.isEmpty ? "名称必填。网址用来找图标，不知道可以不填。" : "不知道网址？点下面的官网就行。") }
        if !domainHits.isEmpty {
          Section("官网") {
            ForEach(domainHits, id: \.self) { h in
              Button { withAnimation(Motion.snappy) { domain = h.domain } } label: {
                HStack(spacing: 12) {
                  AppIcon(name: h.label, domain: h.domain, size: 28)
                  Text(h.label).foregroundStyle(.primary).lineLimit(1)
                  Spacer()
                  Text(h.domain).font(.system(size: 14)).foregroundStyle(.secondary).lineLimit(1)
                }
              }
            }
          }
        }
      }
      .animation(Motion.snappy, value: icon)
      .animation(Motion.snappy, value: domainHits)
      .task(id: trimmed) {
        // nothing we know by that name: look its official site up online (debounced; a newer name cancels this)
        webHits = []
        guard !trimmed.isEmpty, localHits.isEmpty, cleanDomain(domain).isEmpty else { return }
        try? await Task.sleep(nanoseconds: 600_000_000)
        guard !Task.isCancelled else { return }
        let found = await SiteLookup.find(trimmed)
        if !Task.isCancelled { webHits = found }
      }
      .confirmationDialog("更换图标", isPresented: $iconMenu, titleVisibility: .visible) {
        Button("从相册选择") { photos = true }
        if icon != nil { Button("使用默认图标") { iconJob &+= 1; withAnimation(Motion.snappy) { icon = nil } } }
        Button("取消", role: .cancel) {}
      }
      .photosPicker(isPresented: $photos, selection: $photoItem, matching: .images)
      .onChange(of: photoItem) {
        guard let item = photoItem else { return }
        photoItem = nil
        iconJob &+= 1; let job = iconJob
        Task {
          let u = await IconCodec.dataURL(from: item)
          guard job == iconJob else { return }
          if let u { withAnimation(Motion.snappy) { icon = u } } else { store.show("这张图片读不出来") }
        }
      }
      .navigationTitle("手动添加").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
        ToolbarItem(placement: .confirmationAction) { Button("添加") { add() }.bold().disabled(trimmed.isEmpty) }
      }
      .onAppear { if trimmed.isEmpty { nameFocused = true } }
    }
    .presentationDetents([.medium, .large])
    .presentationDragIndicator(.visible)
  }

  func add() {
    let n = trimmed; guard !n.isEmpty else { return }
    let d = cleanDomain(domain)
    let cat = presetFor(name: n, domain: domain)?.category ?? Presets.directory.first { (!d.isEmpty && $0.domain == d) || $0.name.lowercased() == n.lowercased() }?.category ?? "其他"
    let p = Preset(name: n, domain: d, category: cat, color: "", icon: icon)
    onAdd(p)
    dismiss()
  }
}
