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
  @State private var exporting = false
  @State private var importing = false
  @State private var askWipe = false
  @State private var askDemo = false
  @State private var askRestore = false
  @State private var pendingImport: Data? = nil
  @State private var doc = BackupDoc(data: Data())

  var body: some View {
    NavigationStack {
      Form {
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
          Toggle(isOn: Binding(get: { store.maskValues }, set: { v in store.maskValues = v }).animation(Motion.smooth)) { Label("默认隐藏账号", systemImage: "eye.slash") }
          Toggle(isOn: Binding(get: { store.hideNames }, set: { v in store.hideNames = v }).animation(Motion.smooth)) { Label("隐藏用户名", systemImage: "person.crop.circle.badge.xmark") }
          Picker(selection: Binding(get: { store.theme }, set: { ThemeFX.set($0, store: store) })) {
            Text("深色").tag("dark")
            Text("浅色").tag("light")
            Text("跟随系统").tag("system")
          } label: { Label("外观", systemImage: "circle.lefthalf.filled") }
          NavigationLink { OrderView() } label: { Label("调整卡片顺序", systemImage: "arrow.up.arrow.down") }
          Button { store.startTour(firstRun: false) } label: { Label("操作演示", systemImage: "hand.tap") }
        } footer: { Text("账号默认打码，卡片上的眼睛可以临时显示，App 退到后台会重新打码。") }
        Section {
          Button { store.route = .share } label: { Label("分享我的星图", systemImage: "square.and.arrow.up.on.square") }
        } footer: { Text("生成一张卡片图片：有哪些 App、各用哪个身份。默认不显示任何邮箱和号码。") }
        Section {
          Button { doc = BackupDoc(data: store.backupJSON()); exporting = true } label: { Label("导出备份", systemImage: "square.and.arrow.up") }
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
        Section {
          Link(destination: URL(string: "https://wwbsj.lanzoup.com/b01i61dn1a")!) {
            HStack {
              Label("下载新版本", systemImage: "arrow.up.forward.app")
              Spacer()
              Text((Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String).map { "当前 " + $0 } ?? "").foregroundStyle(.secondary)
            }
          }
        } footer: { Text("在蓝奏云下载，密码 0874。直接覆盖安装，数据不会丢失。") }
        Section {
          Button(role: .destructive) { askWipe = true } label: { Label("清空全部数据", systemImage: "trash") }
        }
        Section {
          VStack(spacing: 6) {
            Text("账号星图").font(.system(size: 15, weight: .semibold))
            Text("不保存任何密码 · 数据只在本机和你自己的私有 Gist").font(.system(size: 12)).foregroundStyle(.secondary)
          }.frame(maxWidth: .infinity)
        }.listRowBackground(Color.clear)
      }
      .tint(.accentColor)
      .navigationTitle("设置").navigationBarTitleDisplayMode(.inline)
      .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() }.bold() } }
      .fileExporter(isPresented: $exporting, document: doc, contentType: .json, defaultFilename: "账号备份-\(Date().formatted(.iso8601.year().month().day()))") { r in
        if case .success = r { store.show("备份已导出") }
      }
      .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { r in
        guard case .success(let url) = r else { return }
        let ok = url.startAccessingSecurityScopedResource(); defer { if ok { url.stopAccessingSecurityScopedResource() } }
        guard let d = try? Data(contentsOf: url), store.readBackup(d) != nil else { store.show("这不是账号星图的备份文件"); return }
        // replacing what is there is asked first; the old data is kept as a restore point either way
        if store.data.identities.isEmpty && store.data.accounts.isEmpty { if store.importBackup(d) { dismiss() } } else { pendingImport = d }
      }
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

struct SyncView: View {
  @Environment(Store.self) var store
  @EnvironmentObject var sync: Sync
  @State private var prov = "gitee"
  @State private var token = ""
  @State private var pass = ""
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
              Text("存放在 " + (sync.provider == "gitee" ? "Gitee" : "GitHub") + " 私有 Gist" + (sync.at > 0 ? " · " + Date(timeIntervalSince1970: sync.at / 1000).formatted(date: .omitted, time: .shortened) : ""))
                .font(.system(size: 13)).foregroundStyle(.secondary)
            }
          }.padding(.vertical, 4)
          Button("立即同步") { Task { await sync.run() } }.disabled(sync.state == "syncing")
        }
        Section { Button("关闭同步", role: .destructive) { askOff = true } } footer: { Text("关闭后这台设备不再同步，云端和本机的数据都会保留。") }
      } else {
        Section {
          Picker("存放在", selection: $prov) {
            Text("Gitee · 国内直连").tag("gitee")
            Text("GitHub").tag("github")
          }.pickerStyle(.inline).labelsHidden()
        } header: { Text("存放在") }
        Section {
          SecureField(prov == "gitee" ? "Gitee 私人令牌（勾选 gists）" : "GitHub 令牌（gist 权限）", text: $token).textInputAutocapitalization(.never).autocorrectionDisabled()
          SecureField("同步密码", text: $pass)
          Link(prov == "gitee" ? "去 Gitee 创建令牌" : "去 GitHub 创建令牌", destination: URL(string: prov == "gitee" ? "https://gitee.com/profile/personal_access_tokens/new" : "https://github.com/settings/tokens/new?scopes=gist&description=账号星图")!)
        } footer: { Text("数据先用同步密码加密，再存进你自己的私有 Gist，别人看不到内容。每台设备填同一个令牌和同步密码即可。同步密码忘了就解不开。") }
        if !error.isEmpty { Section { Text(error).foregroundStyle(.red) } }
        Section {
          Button {
            busy = true; error = ""
            Task {
              do {
                let found = try await sync.enable(token: token, pass: pass, prov: prov)
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
          }.disabled(busy || token.trimmingCharacters(in: .whitespaces).isEmpty || pass.count < 4)
        }
      }
    }
    .navigationTitle("加密同步").navigationBarTitleDisplayMode(.inline)
    .confirmationDialog("关闭同步？", isPresented: $askOff, titleVisibility: .visible) {
      Button("关闭同步", role: .destructive) { sync.disable() }
    }
  }
}
