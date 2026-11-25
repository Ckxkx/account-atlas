import SwiftUI
import UIKit
import LocalAuthentication

/// 2.6 应用锁 (off by default). Face ID / Touch ID with the device passcode as fallback
/// (.deviceOwnerAuthentication).
/// - launch: locked; back from the background after the chosen delay (立即 / 1 分钟 / 5 分钟): locked again
/// - with the lock on, the screen is covered while the app is inactive (app switcher snapshot)
/// - with the lock on, the eye (and turning 默认隐藏账号 off) asks once per foreground session
/// The lock screen lives in its own window above everything, so open sheets and alerts are covered too.
@MainActor @Observable final class AppLock {
  static let shared = AppLock()
  static let relockOptions = [0, 60, 300]
  static func relockName(_ s: Int) -> String { s == 0 ? "立即" : s == 60 ? "离开 1 分钟后" : "离开 5 分钟后" }

  private(set) var enabled = UserDefaults.standard.bool(forKey: "atlas.lock")
  private(set) var relockSec: Int = { let v = UserDefaults.standard.integer(forKey: "atlas.lockAfter"); return [0, 60, 300].contains(v) ? v : 0 }()
  private(set) var locked = false
  private(set) var covered = false
  @ObservationIgnored private(set) var revealOK = false
  @ObservationIgnored private var authing = false
  @ObservationIgnored private var autoAsked = false
  @ObservationIgnored private var bgAt: Date? = nil
  @ObservationIgnored var toast: ((String) -> Void)? = nil

  init() { if enabled { locked = true } }

  var blocking: Bool { enabled && (locked || covered) }
  var needsAuthToReveal: Bool { enabled && !revealOK }

  /// a passcode (and maybe Face ID / Touch ID) is set up on this device
  static func available() -> Bool {
    var e: NSError?
    return LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: &e)
  }
  static var biometryName: String {
    let c = LAContext(); var e: NSError?
    _ = c.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &e)
    switch c.biometryType {
    case .faceID: return "面容 ID"
    case .touchID: return "触控 ID"
    default: return "锁屏密码"
    }
  }

  func authenticate(_ reason: String) async -> Bool {
    guard !authing else { return false }
    authing = true
    defer { authing = false }
    let ctx = LAContext()
    ctx.localizedCancelTitle = "取消"
    do { return try await ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) } catch { return false }
  }

  private func lock() { locked = true; autoAsked = false; revealOK = false }

  func scenePhase(_ p: ScenePhase) {
    switch p {
    case .active:
      if enabled, let t = bgAt, Date().timeIntervalSince(t) >= Double(relockSec) { lock() }
      bgAt = nil; covered = false
      LockWindow.sync()
      if locked { Task { await autoUnlock() } }
    case .inactive:
      // our own Face ID / passcode sheet also makes the app inactive: no cover for that
      if enabled && !authing { covered = true }
      LockWindow.sync()
    case .background:
      if enabled { covered = true }
      if !authing && bgAt == nil { bgAt = Date(); revealOK = false }
      LockWindow.sync()
    @unknown default: break
    }
  }

  /// the lock screen asks by itself once each time it appears (not again after a cancel: there's the button)
  func autoUnlock() async {
    guard locked, !autoAsked, UIApplication.shared.applicationState == .active else { return }
    autoAsked = true
    await unlock()
  }

  func unlock() async {
    guard locked else { return }
    if !Self.available() {
      // the passcode was turned off in Settings: never shut the owner out of their own data
      locked = false; revealOK = true; LockWindow.sync()
      toast?("系统锁屏密码已关闭，应用锁暂时不起作用")
      return
    }
    if await authenticate("解锁账号星图") { locked = false; revealOK = true; LockWindow.sync() }
  }

  /// the eye / 默认隐藏账号 off: once per foreground session while the lock is on
  func reveal(_ ok: @escaping () -> Void) {
    guard needsAuthToReveal else { ok(); return }
    Task {
      var passed = true
      if Self.available() { passed = await authenticate("查看完整账号") }
      if passed { revealOK = true; ok() }
    }
  }

  /// for actions that always ask (导出备份). With no passcode on the device there is nothing to ask.
  func require(_ reason: String) async -> Bool {
    guard Self.available() else { return true }
    return await authenticate(reason)
  }

  func setEnabled(_ on: Bool) async {
    guard on != enabled else { return }
    let avail = Self.available()
    if on && !avail { toast?("请先在系统设置中设置锁屏密码"); return }
    if avail { guard await authenticate(on ? "开启应用锁" : "关闭应用锁") else { return } }
    enabled = on; revealOK = true
    UserDefaults.standard.set(on, forKey: "atlas.lock")
    toast?(on ? "应用锁已开启" : "应用锁已关闭")
  }
  func setRelock(_ s: Int) { relockSec = s; UserDefaults.standard.set(s, forKey: "atlas.lockAfter") }

  /// One plain line, once ever: with 3+ of their own identities / apps and the lock off, suggest it.
  /// Tapping it opens 设置. No address, number or name in it. Returns true when it was shown.
  func maybeTip(_ store: Store) -> Bool {
    let d = UserDefaults.standard
    guard !enabled, !d.bool(forKey: "atlas.lockTip"), !store.firstRun, !store.recovered, !store.sandbox, store.tourStep == nil else { return false }
    let demo = store.demoIDs
    let n = store.data.identities.filter { !demo.contains($0.id) }.count + store.data.accounts.filter { !demo.contains($0.id) }.count
    guard n >= 3, Self.available() else { return false }
    d.set(true, forKey: "atlas.lockTip")
    store.tip("建议开启应用锁，保护你的账号信息") { [weak store] in store?.route = .settings }
    return true
  }
}

/// The window the lock screen / cover is shown in, above sheets, alerts and the keyboard's owner.
@MainActor enum LockWindow {
  static var win: UIWindow? = nil
  static func sync() {
    guard AppLock.shared.blocking else { win?.isHidden = true; return }
    if win == nil {
      let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
      guard let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first else { return }
      let w = UIWindow(windowScene: scene)
      w.windowLevel = .alert + 1
      let host = UIHostingController(rootView: LockScreen())
      host.view.backgroundColor = .clear
      w.rootViewController = host
      w.overrideUserInterfaceStyle = ThemeFX.style(UserDefaults.standard.string(forKey: "atlas.theme") ?? "dark")
      win = w
    }
    // a text field in a sheet would keep its keyboard above the lock screen
    _ = UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    win?.isHidden = false
  }
}

/// Plain cover (app switcher) or the lock screen with 解锁.
struct LockScreen: View {
  var body: some View {
    let lock = AppLock.shared
    ZStack {
      Color(uiColor: .systemBackground).ignoresSafeArea()
      VStack(spacing: 0) {
        Image(uiImage: UIImage(named: "AppIcon60x60") ?? UIImage(systemName: "lock.fill")!)
          .resizable().frame(width: 76, height: 76).clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
        Text("账号星图").font(.system(size: 20, weight: .semibold)).padding(.top, 16)
        if lock.locked {
          Text("已锁定").font(.system(size: 14)).foregroundStyle(.secondary).padding(.top, 6)
          Button { Task { await lock.unlock() } } label: {
            Label("解锁", systemImage: "lock.open.fill").font(.system(size: 17, weight: .semibold))
              .padding(.horizontal, 36).padding(.vertical, 13)
              .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
              .foregroundStyle(.white)
          }
          .buttonStyle(.plain)
          .padding(.top, 28)
        }
      }
    }
  }
}
