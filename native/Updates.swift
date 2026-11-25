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

/// 检查更新 (on by default, 设置 → 自动检查更新). When the app comes to the front, at most once a day, a
/// plain GET of a static file on the website (no account data, no ids, no cookies). A newer version shows
/// one alert: 去下载 / 跳过此版本 / 以后再说 (again the next day). Errors and 404 are ignored. The alert
/// waits while the 应用锁, the 操作演示 or a sheet is up (see RootView).
@MainActor @Observable final class Updater {
  static let shared = Updater()
  private let d = UserDefaults.standard
  private(set) var auto: Bool = UserDefaults.standard.object(forKey: "atlas.updateCheck") as? Bool ?? true
  var pending: UpdateInfo? = nil
  /// the pending one came from the 检查更新 row: shown by the settings sheet
  private(set) var manual = false
  private(set) var checking = false
  private(set) var status: String? = nil
  let installed: String = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0"

  func setAuto(_ v: Bool) { auto = v; d.set(v, forKey: "atlas.updateCheck") }

  func maybeCheck() {
    guard auto, !checking, pending == nil, VersionCmp.isRelease(installed) else { return }
    let last = d.double(forKey: "atlas.updateLast")
    let now = Date().timeIntervalSince1970
    if now - last >= 0 && now - last < 86_400 { return }
    checking = true
    Task {
      let info = await Self.fetch()
      checking = false
      guard let info else { return }   // offline / 404 / bad file: quietly try again next time
      d.set(Date().timeIntervalSince1970, forKey: "atlas.updateLast")
      if VersionCmp.isNewer(info.version, than: installed) && d.string(forKey: "atlas.updateSkip") != info.version { manual = false; pending = info }
    }
  }

  /// the 检查更新 row: always asks, and shows the alert even for a skipped version
  func checkNow(_ store: Store) {
    guard !checking else { return }
    checking = true; status = nil
    Task {
      let info = await Self.fetch()
      checking = false
      guard let info else { store.show("检查失败，请稍后再试"); return }
      d.set(Date().timeIntervalSince1970, forKey: "atlas.updateLast")
      if VersionCmp.isNewer(info.version, than: installed) || !VersionCmp.isRelease(installed) { manual = true; pending = info }
      else { status = "已是最新版本"; store.show("已是最新版本") }
    }
  }

  // the buttons get the version they were shown for: the alert's dismissal may clear `pending` first
  func skip(_ p: UpdateInfo) { d.set(p.version, forKey: "atlas.updateSkip"); pending = nil }
  /// 以后再说: the check ran today, so the next one (and the alert) comes after a day
  func later() { pending = nil }
  func download(_ p: UpdateInfo) {
    if !p.password.isEmpty { UIPasteboard.general.string = p.password }
    pending = nil
    if let u = URL(string: p.url) { UIApplication.shared.open(u) }
  }

  var title: String { "发现新版本 \(pending?.version ?? "")" }
  var message: String {
    guard let p = pending else { return "" }
    var s = p.notes.map { "• " + $0 }.joined(separator: "\n")
    if !s.isEmpty { s += "\n\n" }
    s += p.password.isEmpty ? "在蓝奏云下载，直接覆盖安装，数据不会丢失。" : "蓝奏云密码 \(p.password)，点「去下载」会自动复制。"
    if !VersionCmp.isRelease(installed) { s += "\n\n（当前是测试版）" }
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
