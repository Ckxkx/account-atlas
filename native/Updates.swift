import SwiftUI
import UIKit

/// 检查更新: version numbers like 2.6 / 2.6.1 (an optional leading v). Anything else is not comparable.
enum VersionCmp {
  static func parse(_ v: String?) -> [Int]? {
    guard var s = v?.trimmingCharacters(in: .whitespaces), !s.isEmpty else { return nil }
    if s.hasPrefix("v") || s.hasPrefix("V") { s.removeFirst() }
    let parts = s.split(separator: ".", omittingEmptySubsequences: false)
    guard !parts.isEmpty, parts.count <= 4 else { return nil }
    var out: [Int] = []
    for p in parts {
      guard !p.isEmpty, p.count <= 6, p.allSatisfy({ $0 >= "0" && $0 <= "9" }), let n = Int(p) else { return nil }
      out.append(n)
    }
    return out
  }
  static func compare(_ a: String?, _ b: String?) -> Int? {
    guard let x = parse(a), let y = parse(b) else { return nil }
    for i in 0..<max(x.count, y.count) {
      let l = i < x.count ? x[i] : 0, r = i < y.count ? y[i] : 0
      if l != r { return l < r ? -1 : 1 }
    }
    return 0
  }
  static func isNewer(_ remote: String?, than installed: String?) -> Bool { (compare(remote, installed) ?? 0) > 0 }
  /// CI test builds are "0.0" (iOS) / "dev" (Android) and never check by themselves
  static func isRelease(_ installed: String?) -> Bool { parse(installed)?.contains(where: { $0 > 0 }) == true }
}

/// what https://huichen.dev/account-atlas/version.json says. Unknown fields are ignored.
struct UpdateInfo: Equatable {
  static let defaultURL = "https://wwbsj.lanzoup.com/b01i61dn1a"
  static let defaultPassword = "0874"
  var version: String
  var notes: [String]
  var url: String
  var password: String
  var github: String?

  static func parse(_ d: Data) -> UpdateInfo? {
    guard d.count < 20_000, let o = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] else { return nil }
    func str(_ k: String) -> String? { (o[k] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }
    guard var v = str("version") else { return nil }
    if v.hasPrefix("v") { v.removeFirst() }
    guard VersionCmp.parse(v) != nil else { return nil }
    let notes = ((o["notes"] as? [Any]) ?? []).compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) }
      .filter { !$0.isEmpty }.prefix(10).map { String($0.prefix(200)) }
    let url = str("url").flatMap { $0.hasPrefix("https://") && $0.count < 300 ? $0 : nil } ?? defaultURL
    let pw = str("password").flatMap { (1...16).contains($0.count) ? $0 : nil } ?? (o["password"] == nil ? defaultPassword : "")
    let gh = str("github").flatMap { $0.hasPrefix("https://") ? $0 : nil }
    return UpdateInfo(version: v, notes: Array(notes), url: url, password: pw, github: gh)
  }
}

/// what the 软件更新 page shows
enum UpdateScreen: Equatable {
  case checking, failed
  /// up to date: the notes only when version.json is about this very version
  case latest([String])
  /// a newer version (a skipped one too: the page always shows it)
  case newer(UpdateInfo)
  /// CI test build: the latest release, whatever its number
  case testBuild(UpdateInfo)
  static func of(_ info: UpdateInfo?, installed: String) -> UpdateScreen {
    guard let info else { return .failed }
    if !VersionCmp.isRelease(installed) { return .testBuild(info) }
    if VersionCmp.isNewer(info.version, than: installed) { return .newer(info) }
    return .latest(VersionCmp.compare(info.version, installed) == 0 ? info.notes : [])
  }
}

/// 检查更新 (on by default, 设置 → 软件更新 → 自动检查更新). When the app comes to the front, at most once a day, a
/// plain GET of a static file on the website (no account data, no ids, no cookies). A newer version shows
/// one alert: 去下载 / 跳过此版本 / 以后再说 (again the next day). Errors and 404 are ignored and don't count as the
/// day's check, so the next launch tries again. The alert waits while the 应用锁, the 操作演示 or a sheet is up
/// (see RootView). 设置 → 软件更新 (SoftwareUpdateView) asks every time it opens and shows the answer on the page.
@MainActor @Observable final class Updater {
  static let shared = Updater()
  private let d = UserDefaults.standard
  private(set) var auto: Bool = UserDefaults.standard.object(forKey: "atlas.updateCheck") as? Bool ?? true
  var pending: UpdateInfo? = nil
  private(set) var checking = false
  /// what the 软件更新 page shows
  private(set) var screen: UpdateScreen = .checking
  private var pageRun = 0
  let installed: String = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0"

  func setAuto(_ v: Bool) { auto = v; d.set(v, forKey: "atlas.updateCheck") }
  /// only a readable version.json counts as the day's check
  private func record(_ info: UpdateInfo?) { if info != nil { d.set(Date().timeIntervalSince1970, forKey: "atlas.updateLast") } }

  func maybeCheck() {
    guard auto, !checking, pending == nil, VersionCmp.isRelease(installed) else { return }
    let last = d.double(forKey: "atlas.updateLast")
    let now = Date().timeIntervalSince1970
    if now - last >= 0 && now - last < 86_400 { return }
    checking = true
    Task {
      let info = await Self.fetch()
      checking = false
      record(info)   // offline / 404 / bad file: not recorded, so the next launch tries again
      guard let info else { return }
      if VersionCmp.isNewer(info.version, than: installed) && d.string(forKey: "atlas.updateSkip") != info.version { pending = info }
    }
  }

  /// the 软件更新 page: asks every time it opens (and on 重试); a skipped version is shown anyway
  func checkPage() {
    pageRun += 1
    let run = pageRun
    screen = .checking
    Task {
      let info = await Self.fetch()
      record(info)
      if run == pageRun { screen = UpdateScreen.of(info, installed: installed) }
    }
  }

  // the buttons get the version they were shown for: the alert's dismissal may clear `pending` first
  func skip(_ p: UpdateInfo) { d.set(p.version, forKey: "atlas.updateSkip"); pending = nil }
  /// 以后再说: the check ran today, so the next one (and the alert) comes after a day
  func later() { pending = nil }
  /// 去下载: copies the 蓝奏云 password and opens the download page
  func download(_ p: UpdateInfo) {
    if !p.password.isEmpty { UIPasteboard.general.string = p.password }
    if pending?.version == p.version { pending = nil }
    if let u = URL(string: p.url) { UIApplication.shared.open(u) }
  }

  var title: String { "发现新版本 \(pending?.version ?? "")" }
  var message: String {
    guard let p = pending else { return "" }
    var s = p.notes.map { "• " + $0 }.joined(separator: "\n")
    if !s.isEmpty { s += "\n\n" }
    s += p.password.isEmpty ? "在蓝奏云下载，直接覆盖安装，数据不会丢失。" : "蓝奏云密码 \(p.password)，点「去下载」会自动复制。"
    return s
  }

  nonisolated static func fetch() async -> UpdateInfo? {
    let cfg = URLSessionConfiguration.ephemeral
    cfg.timeoutIntervalForRequest = 5
    cfg.timeoutIntervalForResource = 6
    cfg.requestCachePolicy = .reloadIgnoringLocalCacheData
    cfg.httpShouldSetCookies = false
    cfg.urlCache = nil
    let session = URLSession(configuration: cfg)
    defer { session.finishTasksAndInvalidate() }
    var req = URLRequest(url: URL(string: "https://huichen.dev/account-atlas/version.json")!)
    req.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
    guard let r = try? await session.data(for: req), (r.1 as? HTTPURLResponse)?.statusCode == 200 else { return nil }
    return UpdateInfo.parse(r.0)
  }
}

extension View {
  /// the update alert with its three buttons, shown while `show` holds
  @MainActor func updateAlert(_ show: Bool) -> some View {
    let u = Updater.shared
    let p = u.pending
    return alert(u.title, isPresented: Binding(get: { show && u.pending != nil }, set: { if !$0 && u.pending != nil { u.later() } })) {
      Button("去下载") { if let p { u.download(p) } }
      Button("跳过此版本") { if let p { u.skip(p) } }
      Button("以后再说", role: .cancel) { u.later() }
    } message: { Text(u.message) }
  }
}

// MARK: 设置 → 软件更新 (like iOS 设置 → 通用 → 软件更新). Small subviews: one big body is too much for the type checker.

struct SoftwareUpdateView: View {
  var body: some View {
    let u = Updater.shared
    List {
      Section { UpdateHeader(installed: u.installed) }.listRowBackground(Color.clear)
      UpdateStatusSections(screen: u.screen)
      Section {
        Toggle(isOn: Binding(get: { Updater.shared.auto }, set: { Updater.shared.setAuto($0) })) { Label("自动检查更新", systemImage: "clock.arrow.circlepath") }
      } footer: { Text("每天最多检查一次，只读取最新版本号，不发送任何数据。") }
    }
    .navigationTitle("软件更新").navigationBarTitleDisplayMode(.inline)
    .onAppear { Updater.shared.checkPage() }
  }
}

private struct UpdateHeader: View {
  let installed: String
  var body: some View {
    VStack(spacing: 0) {
      Image(uiImage: UIImage(named: "AppIcon60x60") ?? UIImage(systemName: "app.fill")!)
        .resizable().frame(width: 76, height: 76).clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
      Text("账号星图").font(.system(size: 20, weight: .semibold)).padding(.top, 12)
      Text("当前版本 " + (VersionCmp.isRelease(installed) ? installed : "测试版")).font(.system(size: 14)).foregroundStyle(.secondary).padding(.top, 4)
    }
    .frame(maxWidth: .infinity)
  }
}

private struct UpdateStatusSections: View {
  let screen: UpdateScreen
  var body: some View {
    switch screen {
    case .checking:
      Section {
        HStack(spacing: 10) { ProgressView(); Text("正在检查更新…").foregroundStyle(.secondary) }.frame(maxWidth: .infinity)
      }
    case .failed:
      Section {
        UpdateStatusLine(icon: "exclamationmark.triangle.fill", tint: .orange, text: "检查失败，请稍后再试")
        Button("重试") { Updater.shared.checkPage() }
      }
    case .latest(let notes):
      Section { UpdateStatusLine(icon: "checkmark.circle.fill", tint: .green, text: "已是最新版本") }
      if !notes.isEmpty { UpdateNotesSection(notes: notes) }
    case .newer(let info):
      UpdateNewVersion(info: info, title: "发现新版本 \(info.version)")
    case .testBuild(let info):
      UpdateNewVersion(info: info, title: "最新版本 \(info.version)")
    }
  }
}

private struct UpdateStatusLine: View {
  let icon: String, tint: Color, text: String
  var body: some View {
    Label { Text(text).font(.system(size: 16, weight: .semibold)) } icon: { Image(systemName: icon).foregroundStyle(tint) }
  }
}

private struct UpdateNotesSection: View {
  let notes: [String]
  var body: some View {
    Section("更新日志") {
      VStack(alignment: .leading, spacing: 6) {
        ForEach(Array(notes.enumerated()), id: \.offset) { e in
          HStack(alignment: .firstTextBaseline, spacing: 8) { Text("•").foregroundStyle(.secondary); Text(e.element) }
        }
      }.padding(.vertical, 4)
    }
  }
}

private struct UpdateNewVersion: View {
  let info: UpdateInfo, title: String
  @State private var copied = false
  var body: some View {
    Section { UpdateStatusLine(icon: "arrow.down.circle.fill", tint: .accentColor, text: title) }
    if !info.notes.isEmpty { UpdateNotesSection(notes: info.notes) }
    Section {
      Button { Updater.shared.download(info); copied = !info.password.isEmpty } label: {
        Text("去下载").font(.system(size: 17, weight: .semibold)).frame(maxWidth: .infinity).frame(height: 50)
          .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14, style: .continuous)).foregroundStyle(.white)
      }
      .buttonStyle(.plain)
      .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
    } footer: { Text(hint) }
  }
  private var hint: String {
    if info.password.isEmpty { return "在蓝奏云下载，直接覆盖安装，数据不会丢失。" }
    return copied ? "已复制蓝奏云密码 \(info.password)，在下载页粘贴即可。直接覆盖安装，数据不会丢失。"
      : "蓝奏云密码 \(info.password)，点「去下载」会自动复制。直接覆盖安装，数据不会丢失。"
  }
}
