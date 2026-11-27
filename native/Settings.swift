import SwiftUI
import UniformTypeIdentifiers

struct BackupDoc: FileDocument {
  static var readableContentTypes: [UTType] { [.json] }
  var data: Data
  init(data: Data) { self.data = data }
  init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
  func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct SettingsSheet: View {
  @Environment(Store.self) var store
  @EnvironmentObject var sync: Sync
  @Environment(\.dismiss) var dismiss
  @State private var importing = false
  @State private var askWipe = false
  @State private var askDemo = false
  @State private var askRestore = false
  @State private var pendingImport: Data? = nil
  // 2.6 password-protected backups
  @State private var exportForm = false
  @State private var encImport: Data? = nil
  @State private var askPass = false
  @State private var encPass = ""
  @State private var encError = ""
  private var lock: AppLock { AppLock.shared }

  var body: some View {
    // split into parts: one long chain was too much for the type checker
    NavigationStack { dataDialogs(importFlow(settingsForm)) }
  }
  private var settingsForm: some View {
    Form {
      topSections
      lockSection
      midSections
      endSections
    }
    .tint(.accentColor)
    .navigationTitle("设置").navigationBarTitleDisplayMode(.inline)
    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() }.bold() } }
    .navigationDestination(isPresented: $exportForm) { ExportView() }
  }

  @ViewBuilder private var topSections: some View {
    Section {
      NavigationLink { SyncView() } label: {
        HStack {
          Label("加密同步", systemImage: "icloud")
          Spacer()
          Text(syncStatus).foregroundStyle(.secondary).font(.system(size: 14))
        }
      }
    }
    Section {
      // 2.6: with the 应用锁 on, showing every value (turning this off) asks for Face ID / passcode first
      Toggle(isOn: Binding(get: { store.maskValues }, set: { v in
        if v { withAnimation(Motion.smooth) { store.maskValues = true } } else { lock.reveal { withAnimation(Motion.smooth) { store.maskValues = false } } }
      })) { Label("默认隐藏账号", systemImage: "eye.slash") }
      Toggle(isOn: Binding(get: { store.hideNames }, set: { v in store.hideNames = v }).animation(Motion.smooth)) { Label("隐藏用户名", systemImage: "person.crop.circle.badge.xmark") }
      Picker(selection: Binding(get: { store.theme }, set: { ThemeFX.set($0, store: store) })) {
        Text("深色").tag("dark")
        Text("浅色").tag("light")
        Text("跟随系统").tag("system")
      } label: { Label("外观", systemImage: "circle.lefthalf.filled") }
      NavigationLink { OrderView() } label: { Label("调整卡片顺序", systemImage: "arrow.up.arrow.down") }
      NavigationLink { CategoriesView() } label: { Label("分类管理", systemImage: "folder") }
      Button { store.startTour(firstRun: false) } label: { Label("操作演示", systemImage: "hand.tap") }
    } footer: { Text("账号默认打码，卡片上的眼睛可以临时显示，App 退到后台会重新打码。") }
  }
  @ViewBuilder private var midSections: some View {
    Section {
      Button { store.route = .share } label: { Label("分享我的星图", systemImage: "square.and.arrow.up.on.square") }
    } footer: { Text("生成一张卡片图片：有哪些 App、各用哪个身份。默认不显示任何邮箱和号码。") }
    Section {
      // 2.6: exporting always asks for Face ID / passcode first, then offers a password for the file
      Button { Task { if await lock.require("导出备份") { exportForm = true } } } label: { Label("导出备份", systemImage: "square.and.arrow.up") }
      Button { importing = true } label: { Label("导入备份", systemImage: "square.and.arrow.down") }
      if store.hasDemo {
        Button(role: .destructive) { store.clearDemo() } label: { Label("清除示例数据", systemImage: "xmark.bin") }
      } else {
        Button { askDemo = true } label: { Label("载入示例", systemImage: "text.badge.plus") }.disabled(!store.canLoadDemo)
      }
      if let t = store.restoreTitle {
        Button { askRestore = true } label: {
          HStack {
            Label(t, systemImage: "arrow.counterclockwise")
            Spacer()
            Text(Date(timeIntervalSince1970: store.restoreAt / 1000).formatted(date: .abbreviated, time: .shortened)).font(.system(size: 13)).foregroundStyle(.secondary)
          }
        }
      }
    } footer: {
      Text("备份是 JSON 文件，和网页版通用。" + (store.hasDemo ? "" : sync.on ? "开着同步时不能载入示例。" : store.hasReal ? "已经有自己的数据时不能载入示例，免得和真实数据混在一起。" : ""))
    }
  }
  @ViewBuilder private var endSections: some View {
    Section {
      Link(destination: URL(string: "https://wwbsj.lanzoup.com/b01i61dn1a")!) {
        HStack {
          Label("下载新版本", systemImage: "arrow.up.forward.app")
          Spacer()
          Text((Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String).map { "当前 " + $0 } ?? "").foregroundStyle(.secondary)
        }
      }
      // 软件更新: its own page (checks when opened; 自动检查更新 is there too)
      NavigationLink { SoftwareUpdateView() } label: { Label("软件更新", systemImage: "arrow.triangle.2.circlepath") }
    } footer: { Text("在蓝奏云下载，密码 0874。直接覆盖安装，数据不会丢失。") }
    Section {
      Link(destination: URL(string: "https://github.com/Ckxkx/account-atlas")!) {
        HStack {
          Label("源代码 / GitHub 仓库", systemImage: "chevron.left.forwardslash.chevron.right")
          Spacer()
          Image(systemName: "arrow.up.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(.tertiary)
        }
      }
    }
    Section {
      Button(role: .destructive) { askWipe = true } label: { Label("清空全部数据", systemImage: "trash") }
    }
    Section {
      VStack(spacing: 6) {
        Text("账号星图").font(.system(size: 15, weight: .semibold))
        Text("不保存任何密码 · 数据只在本机和你自己的私有 Gist / WebDAV").font(.system(size: 12)).foregroundStyle(.secondary)
      }.frame(maxWidth: .infinity)
    }.listRowBackground(Color.clear)
  }

  private func importFlow<V: View>(_ v: V) -> some View {
    v
    .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { r in
      guard case .success(let url) = r else { return }
      let ok = url.startAccessingSecurityScopedResource(); defer { if ok { url.stopAccessingSecurityScopedResource() } }
      guard let d = try? Data(contentsOf: url) else { store.show("这不是账号星图的备份文件"); return }
      // 2.6: a password-protected backup is opened first, then goes down the same path
      if BackupCrypto.isEnvelope(d) { encImport = d; encPass = ""; encError = ""; askPass = true; return }
      importPlain(d)
    }
    .alert("加密的备份", isPresented: $askPass) {
      SecureField("备份密码", text: $encPass)
      Button("解锁") { openEncrypted() }
      Button("取消", role: .cancel) { encImport = nil; encPass = "" }
    } message: { Text(encError.isEmpty ? "这份备份设置了密码，输入导出时设的密码。" : encError) }
  }
  private func dataDialogs<V: View>(_ v: V) -> some View {
    v
    .confirmationDialog("清空全部数据？", isPresented: $askWipe, titleVisibility: .visible) {
      Button("清空全部数据", role: .destructive) {
        store.saveRestorePoint("wipe")
        store.mutate("已清空") { s in s.identities = []; s.accounts = [] }
        store.setKind(store.kind); dismiss()
      }
    } message: { Text(sync.on ? "所有邮箱、手机号和网站记录都会删除，开了同步的其他设备也会一起清空。建议先导出备份。" : "所有邮箱、手机号和网站记录都会从这台设备上删除。建议先导出备份。") }
    .alert("导入备份？", isPresented: Binding(get: { pendingImport != nil }, set: { if !$0 { pendingImport = nil } })) {
      Button("导入") { if let d = pendingImport, store.importBackup(d) { dismiss() }; pendingImport = nil }
      Button("取消", role: .cancel) { pendingImport = nil }
    } message: { Text("导入会替换现在的 \(store.data.identities.count) 个身份和 \(store.data.accounts.count) 个网站与 App。替换前的数据会先存一份，可以在设置里恢复。") }
    .alert("载入示例数据？", isPresented: $askDemo) {
      Button("载入") { store.loadDemo(); dismiss() }
      Button("取消", role: .cancel) {}
    } message: { Text("会加入几个虚构的邮箱、手机号和 App，都带示例标记，「清除示例数据」只会删掉它们。") }
    .alert(store.restoreTitle ?? "恢复数据", isPresented: $askRestore) {
      Button("恢复") { store.restore(); dismiss() }
      Button("取消", role: .cancel) {}
    } message: { Text("会把数据换回那时的样子。现在的数据也会先存一份，可以再换回来。") }
  }
  /// the existing import path: a plain backup (straight from the file, or just decrypted)
  func importPlain(_ d: Data) {
    guard store.readBackup(d) != nil else { store.show("这不是账号星图的备份文件"); return }
    // replacing what is there is asked first; the old data is kept as a restore point either way
    if store.data.identities.isEmpty && store.data.accounts.isEmpty { if store.importBackup(d) { dismiss() } } else { pendingImport = d }
  }
  func openEncrypted() {
    guard let env = encImport else { return }
    let pw = encPass
    encPass = ""
    Task {
      let r = await Task.detached { () -> Result<Data, BackupCrypto.Failure> in
        do { return .success(try BackupCrypto.decrypt(env, pass: pw)) }
        catch let f as BackupCrypto.Failure { return .failure(f) }
        catch { return .failure(.badFormat) }
      }.value
      switch r {
      case .success(let plain):
        encImport = nil; encError = ""
        try? await Task.sleep(for: .milliseconds(350))   // let the password alert go before the next one
        importPlain(plain)
      case .failure(.wrongPassword):
        // 密码错误: ask again
        encError = "密码错误"
        try? await Task.sleep(for: .milliseconds(350))
        askPass = true
      case .failure:
        encImport = nil
        store.show("这不是账号星图的备份文件")
      }
    }
  }

  // 2.6 应用锁
  @ViewBuilder var lockSection: some View {
    let ok = AppLock.available()
    Section {
      Toggle(isOn: Binding(get: { lock.enabled }, set: { v in Task { await lock.setEnabled(v) } })) { Label("应用锁", systemImage: "lock") }
        .disabled(!ok && !lock.enabled)
      if lock.enabled {
        Picker(selection: Binding(get: { lock.relockSec }, set: { lock.setRelock($0) })) {
          ForEach(AppLock.relockOptions, id: \.self) { s in Text(AppLock.relockName(s)).tag(s) }
        } label: { Label("重新锁定", systemImage: "timer") }
      }
    } footer: {
      Text(ok ? "打开账号星图、或离开后再回来时，要验证\(AppLock.biometryName == "锁屏密码" ? "" : AppLock.biometryName + "或")锁屏密码。开启后，查看完整账号也要先验证。" : "请先在系统设置中设置锁屏密码")
    }
  }

  var syncStatus: String {
    switch sync.state {
    case "off": return "未开启"
    case "syncing": return "同步中…"
    case "error": return "出错了"
    default: return sync.at > 0 ? "已同步 " + Date(timeIntervalSince1970: sync.at / 1000).formatted(.relative(presentation: .named)) : "已开启"
    }
  }
}

struct OrderView: View {
  @Environment(Store.self) var store
  var body: some View {
    List {
      group("email", "邮箱")
      group("phone", "手机号")
      group("login", "微信 / QQ")
    }
    .environment(\.editMode, .constant(.active))
    .navigationTitle("卡片顺序").navigationBarTitleDisplayMode(.inline)
  }
  @ViewBuilder func group(_ type: String, _ title: String) -> some View {
    let list = store.data.identities.filter { $0.type == type }
    if !list.isEmpty {
      Section {
        ForEach(list) { i in
          HStack(spacing: 12) {
            IdentityGlyph(identity: i, size: 13).foregroundStyle(.white).frame(width: 46, height: 30)
              .background(skinGradient(i.color), in: CardShape(sim: i.isPhone))
              .shadow(color: Color(hex: i.color).opacity(0.35), radius: 5, y: 2)
            VStack(alignment: .leading, spacing: 1) {
              Text(store.title(i)).font(.system(size: 16, weight: .medium))
              if !i.label.isEmpty { Text(store.display(i)).font(.system(size: 13)).foregroundStyle(.secondary) }
            }
          }.padding(.vertical, 2)
        }
        .onMove { from, to in move(type, from, to) }
      } header: { Text(title) } footer: { if type == lastType { Text("拖动右侧把手排序，排在第一位的卡会在卡包里最先出现。") } }
    }
  }
  /// the footer hint goes under the last group shown
  var lastType: String { ["login", "phone", "email"].first { t in store.data.identities.contains { $0.type == t } } ?? "email" }
  func move(_ type: String, _ from: IndexSet, _ to: Int) {
    store.mutate(nil) { s in
      var list = s.identities.filter { $0.type == type }
      list.move(fromOffsets: from, toOffset: to)
      for k in list.indices { list[k].order = Double(k) }
      let others = s.identities.filter { $0.type != type }
      func of(_ t: String) -> [Identity] { t == type ? list : others.filter { $0.type == t } }
      s.identities = of("email") + of("phone") + of("login") + others.filter { !KINDS.contains($0.type) }
    }
  }
}

/// 分类管理: add, rename, delete, drag to reorder. Every change is saved right away (with 撤销);
/// 其他 is always last and can't be renamed or deleted.
struct CategoriesView: View {
  @Environment(Store.self) var store
  @State private var adding = false
  @State private var renaming: String? = nil
  @State private var deleting: String? = nil
  @State private var error = ""
  /// sorting is its own mode: a List that is always in edit mode and also opens a text alert from a
  /// row tap could freeze after a drag (the alert came up but nothing responded)
  @State private var mode: EditMode = .inactive

  var body: some View {
    let all = store.index.allCategories
    let sorting = mode == .active
    List {
      Section {
        ForEach(Array(all.dropLast()), id: \.self) { c in
          row(c, chevron: !sorting)
            .contentShape(Rectangle())
            .onTapGesture { if !sorting { renaming = c } }
        }
        .onMove { from, to in store.moveCategories(from, to) }
        .onDelete { s in if let k = s.first { deleting = all[k] } }
        row(OTHER_CAT, chevron: false).moveDisabled(true).deleteDisabled(true)
      } footer: { Text(sorting ? "拖动右侧把手调整顺序，排在前面的分类在「全部 App」里也排在前面。「其他」总在最后。" : "点分类改名，左滑删除，右上角「排序」调整顺序。「其他」不能改名或删除，总在最后。") }
      if !sorting {
        Section {
          Button { adding = true } label: { Label("添加分类", systemImage: "plus") }
        }
      }
    }
    .environment(\.editMode, $mode)
    .toolbar {
      ToolbarItem(placement: .topBarTrailing) {
        Button(sorting ? "完成" : "排序") { withAnimation { mode = sorting ? .inactive : .active } }.fontWeight(sorting ? .semibold : .regular)
      }
    }
    .navigationTitle("分类管理").navigationBarTitleDisplayMode(.inline)
    .sheet(isPresented: $adding) {
      CategoryNameSheet(title: "添加分类", initial: "", note: "新分类会排在「其他」前面。", action: "添加") { store.addCategory($0) }
    }
    .sheet(item: Binding(get: { renaming.map(CatName.init) }, set: { renaming = $0?.id })) { r in
      CategoryNameSheet(title: "重命名分类", initial: r.id, note: "用这个分类的 App 会一起改过去。", action: "保存") { store.renameCategory(r.id, to: $0) }
    }
    .confirmationDialog(deleting.map { "删除分类「\($0)」？" } ?? "", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
      Button("删除", role: .destructive) { if let c = deleting { store.deleteCategory(c) }; deleting = nil }
      Button("取消", role: .cancel) { deleting = nil }
    } message: {
      let n = deleting.flatMap { store.index.categoryCount[$0] } ?? 0
      Text(n > 0 ? "其中的 \(n) 个 App 会移到「其他」。" : "这个分类里还没有 App。")
    }
  }

  func row(_ c: String, chevron: Bool) -> some View {
    HStack {
      Text(c).foregroundStyle(.primary)
      Spacer()
      Text("\(store.index.categoryCount[c] ?? 0) 个 App").font(.system(size: 14)).foregroundStyle(.secondary)
      if chevron { Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(.tertiary) }
    }
  }
}

private struct CatName: Identifiable { let id: String }

/// add / rename in a small sheet: the field is focused right away and a refused name shows its
/// reason in place (an alert had to be dismissed and re-shown, which is where it could get stuck)
struct CategoryNameSheet: View {
  let title: String, initial: String, note: String, action: String
  let submit: (String) -> String?
  @Environment(\.dismiss) private var dismiss
  @State private var text = ""
  @State private var error = ""
  @FocusState private var focused: Bool

  var body: some View {
    NavigationStack {
      Form {
        Section {
          TextField("分类名，最多 \(CAT_MAX) 个字", text: $text)
            .focused($focused).submitLabel(.done).onSubmit(save)
        } footer: { Text(error.isEmpty ? note : error).foregroundStyle(error.isEmpty ? Color.secondary : Color.red) }
      }
      .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
        ToolbarItem(placement: .confirmationAction) {
          Button(action) { save() }.disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
        }
      }
    }
    .presentationDetents([.height(240)])
    .onAppear { text = initial; focused = true }
  }
  func save() {
    if let p = submit(text) { error = p } else { dismiss() }
  }
}

struct SyncView: View {
  @Environment(Store.self) var store
  @EnvironmentObject var sync: Sync
  @State private var prov = "gitee"
  @State private var token = ""
  @State private var pass = ""
  @State private var davUrl = ""
  @State private var davPick = ""
  @State private var davUser = ""
  @FocusState private var urlFocused: Bool
  @State private var busy = false
  @State private var error = ""
  @State private var askOff = false

  var body: some View {
    Form {
      if sync.on {
        Section {
          HStack {
            Image(systemName: sync.state == "error" ? "exclamationmark.icloud.fill" : sync.state == "syncing" ? "arrow.triangle.2.circlepath.icloud.fill" : "checkmark.icloud.fill")
              .font(.system(size: 30)).foregroundStyle(sync.state == "error" ? Color.orange : Color.accentColor)
              .symbolEffect(.pulse, isActive: sync.state == "syncing")
            VStack(alignment: .leading, spacing: 2) {
              Text(sync.state == "error" ? sync.err : sync.state == "syncing" ? "正在同步…" : "已开启加密同步").font(.system(size: 16, weight: .semibold))
              Text("存放在 " + sync.place + (sync.at > 0 ? " · " + Date(timeIntervalSince1970: sync.at / 1000).formatted(date: .omitted, time: .shortened) : ""))
                .font(.system(size: 13)).foregroundStyle(.secondary)
            }
          }.padding(.vertical, 4)
          Button("立即同步") { Task { await sync.run() } }.disabled(sync.state == "syncing")
        }
        Section { Button("关闭同步", role: .destructive) { askOff = true } } footer: { Text("关闭后这台设备不再同步，云端和本机的数据都会保留。") }
      } else {
        Section {
          Picker("同步方式", selection: $prov) {
            Text("Gitee · 国内直连").tag("gitee")
            Text("GitHub Gist").tag("github")
            Text("WebDAV · 坚果云、NAS 等").tag("webdav")
          }.pickerStyle(.inline).labelsHidden()
        } header: { Text("同步方式") }
        if prov == "webdav" {
          let nut = davUrl.contains("jianguoyun")
          let hint = Sync.DAV_PRESETS.first(where: { $0.id == davPick })?.hint ?? ""
          let footer: String = nut ? "坚果云要用「第三方应用密码」，不是登录密码：网页版 账户信息 › 安全选项，或坚果云 App 设置 › 第三方应用管理 里添加。数据先用同步密码加密，再存到服务器的 AccountAtlas/atlas.enc.json，别人看不到内容。每台设备填同样的账号、应用密码和同步密码即可。同步密码忘了就解不开。"
            : (hint.isEmpty ? "" : hint + "\n") + "数据先用同步密码加密，再存到服务器的 AccountAtlas/atlas.enc.json，别人看不到内容。每台设备填同样的服务器地址、账号、密码和同步密码即可。同步密码忘了就解不开。"
          Section {
            ScrollView(.horizontal) {
              HStack(spacing: 8) {
                ForEach(Sync.DAV_PRESETS, id: \.id) { p in
                  Button {
                    davPick = p.id; davUrl = p.url; error = ""
                    if p.template || p.url.isEmpty { urlFocused = true }
                  } label: {
                    Text(p.name).font(.system(size: 14, weight: .semibold)).padding(.horizontal, 13).frame(height: 32)
                      .foregroundStyle(davPick == p.id ? Color.white : Color.primary)
                      .background(davPick == p.id ? AnyShapeStyle(Color.accentColor) : AnyShapeStyle(.glass), in: Capsule())
                  }.buttonStyle(PressStyle(scale: 0.94))
                }
              }.padding(.horizontal, 16).padding(.vertical, 4)
            }.scrollIndicators(.hidden).listRowInsets(EdgeInsets())
            TextField("https://example.com/dav/", text: $davUrl).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
              .focused($urlFocused)
            TextField(nut ? "账号（坚果云登录邮箱）" : "账号", text: $davUser).keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
            SecureField(nut || davUrl.contains("koofr") ? "应用密码" : "密码", text: $token).textInputAutocapitalization(.never).autocorrectionDisabled()
            SecureField("同步密码", text: $pass)
            if nut { Link("怎么获取坚果云应用密码", destination: URL(string: "https://help.jianguoyun.com/?p=2064")!) }
          } footer: { Text(footer) }
        } else {
        Section {
          SecureField(prov == "gitee" ? "Gitee 私人令牌（勾选 gists）" : "GitHub 令牌（gist 权限）", text: $token).textInputAutocapitalization(.never).autocorrectionDisabled()
          SecureField("同步密码", text: $pass)
          Link(prov == "gitee" ? "去 Gitee 创建令牌" : "去 GitHub 创建令牌", destination: URL(string: prov == "gitee" ? "https://gitee.com/profile/personal_access_tokens/new" : "https://github.com/settings/tokens/new?scopes=gist&description=账号星图")!)
        } footer: { Text("数据先用同步密码加密，再存进你自己的私有 Gist，别人看不到内容。每台设备填同一个令牌和同步密码即可。同步密码忘了就解不开。") }
        }
        if !error.isEmpty { Section { Text(error).foregroundStyle(.red) } }
        Section {
          Button {
            busy = true; error = ""
            Task {
              do {
                let found = try await sync.enable(token: token, pass: pass, prov: prov, url: davUrl, user: davUser)
                store.setKind(store.kind)
                store.show(found ? "已连接，云端数据已合并" : "同步已开启")
              } catch { self.error = (error as? SyncError)?.errorDescription ?? error.localizedDescription }
              busy = false
            }
          } label: {
            HStack {
              Spacer()
              if busy { ProgressView() } else { Text("开启同步").bold() }
              Spacer()
            }
          }.disabled(busy || token.trimmingCharacters(in: .whitespaces).isEmpty || pass.count < 4
                     || (prov == "webdav" && (davUrl.trimmingCharacters(in: .whitespaces).isEmpty || davUser.trimmingCharacters(in: .whitespaces).isEmpty)))
        }
      }
    }
    .navigationTitle("加密同步").navigationBarTitleDisplayMode(.inline)
    .confirmationDialog("关闭同步？", isPresented: $askOff, titleVisibility: .visible) {
      Button("关闭同步", role: .destructive) { sync.disable() }
    }
  }
}

/// 2.6 导出备份: 「设置密码加密」 is on by default; off exports the plain JSON exactly as before.
struct ExportView: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  @State private var encrypt = true
  @State private var p1 = ""
  @State private var p2 = ""
  @State private var error = ""
  @State private var busy = false
  @State private var exporting = false
  @State private var doc = BackupDoc(data: Data())

  var body: some View {
    Form {
      Section {
        Toggle(isOn: $encrypt.animation(Motion.smooth)) { Label("设置密码加密", systemImage: "lock.doc") }
      } footer: {
        if !encrypt { Text("不加密的备份是明文 JSON，拿到文件的人能看到里面所有的邮箱和手机号。") }
      }
      if encrypt {
        Section {
          SecureField("密码（至少 4 位）", text: $p1).textContentType(.newPassword)
          SecureField("再输一次", text: $p2).textContentType(.newPassword)
        } footer: {
          VStack(alignment: .leading, spacing: 6) {
            if !error.isEmpty { Text(error).foregroundStyle(.red) }
            Text("忘记密码将无法恢复这份备份，我们也无法找回").foregroundStyle(.orange)
          }
        }
      }
      Section {
        Button { go() } label: {
          HStack { Spacer(); if busy { ProgressView() } else { Text("导出").bold() }; Spacer() }
        }.disabled(busy)
      }
    }
    .navigationTitle("导出备份").navigationBarTitleDisplayMode(.inline)
    .fileExporter(isPresented: $exporting, document: doc, contentType: .json, defaultFilename: "账号备份-\(Date().formatted(.iso8601.year().month().day()))") { r in
      if case .success = r { store.show("备份已导出"); dismiss() }
    }
  }

  func go() {
    let plain = store.backupJSON()
    guard encrypt else { doc = BackupDoc(data: plain); exporting = true; return }
    guard p1.count >= 4 else { error = "密码至少 4 位"; return }
    guard p1 == p2 else { error = "两次输入的密码不一样"; return }
    error = ""; busy = true
    let pw = p1
    Task {
      let out = await Task.detached { try? BackupCrypto.encrypt(plain, pass: pw) }.value
      busy = false
      if let out { doc = BackupDoc(data: out); exporting = true } else { error = "加密失败" }
    }
  }
}
