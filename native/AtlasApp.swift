import SwiftUI

@main
struct AtlasApp: App {
  @StateObject private var store = Store()
  @StateObject private var sync = Sync()
  @Environment(\.scenePhase) private var phase

  var body: some Scene {
    WindowGroup {
      RootView()
        .environmentObject(store)
        .environmentObject(sync)
        .onAppear {
          ThemeFX.apply(store.theme, animated: false)
          sync.store = store
          let s = sync
          store.onSaved = { [weak s] in s?.schedule() }
          if store.data.demo == true {
            store.show("这是示例数据", action: "清空") {
              store.mutate(nil) { s in s.identities = []; s.accounts = [] }
              store.setKind(store.kind)
            }
          }
          Task { await sync.run() }
        }
        .onChange(of: phase) { _, p in if p == .active { Task { await sync.run() } } }
    }
  }
}

struct RootView: View {
  @EnvironmentObject var store: Store
  @State private var tab = 0
  @State private var dir: CGFloat = 1

  var body: some View {
    TabView(selection: Binding(get: { tab }, set: { t in dir = t >= tab ? 1 : -1; tab = t })) {
      WalletView().tabEnter(0, tab: tab, dir: dir).tag(0).tabItem { Label("卡包", systemImage: "rectangle.stack.fill") }
      SitesView().tabEnter(1, tab: tab, dir: dir).tag(1).tabItem { Label("全部 App", systemImage: "square.grid.2x2.fill") }
      MapView().tabEnter(2, tab: tab, dir: dir).tag(2).tabItem { Label("关系图", systemImage: "point.3.connected.trianglepath.dotted") }
    }
    .environment(\.selectedTab, tab)
    .sensoryFeedback(.selection, trigger: tab)
    .onChange(of: store.theme) { _, t in ThemeFX.apply(t, animated: true) }
    .sheet(item: $store.route) { r in sheet(r) }
    .overlay(alignment: .top) { ToastView() }
    .onAppear {
      // launch arguments used by the CI preview screenshots
      let d = UserDefaults.standard
      tab = d.integer(forKey: "atlasTab")
      switch d.string(forKey: "atlasRoute") {
      case "picker": store.route = .picker(store.active?.id)
      case "identity": if let i = store.active { store.route = .identity(i.id) }
      case "settings": store.route = .settings
      case "new": store.route = .newIdentity("email")
      default: break
      }
    }
  }

  func sheet(_ r: Route) -> some View {
    SheetHost(route: r)
      .environmentObject(store)
      .presentationDragIndicator(.visible)
      .presentationCornerRadius(34)
  }
}

struct SheetHost: View {
  @EnvironmentObject var store: Store
  let route: Route
  var body: some View {
    switch route {
    case .identity(let id): IdentityDetail(id: id)
    case .account(let id): AccountDetail(id: id)
    case .newIdentity(let t): IdentityForm(existing: nil, type: t, defaultColor: COLORS[store.data.identities.count % COLORS.count])
    case .editIdentity(let id): IdentityForm(existing: store.identity(id), type: "email", defaultColor: COLORS[0])
    case .editAccount(let id): AccountFormHost(id: id)
    case .picker(let pre): PickerSheet(presetIdentity: pre)
    case .settings: SettingsSheet()
    }
  }
}

struct AccountFormHost: View {
  @EnvironmentObject var store: Store
  let id: String
  var body: some View {
    if let a = store.account(id) { AccountForm(existing: a) }
  }
}

struct ToastView: View {
  @EnvironmentObject var store: Store
  var body: some View {
    if let t = store.toast {
      HStack(spacing: 12) {
        Image(uiImage: UIImage(named: "AppIcon60x60") ?? UIImage(systemName: "rectangle.stack.fill")!)
          .resizable().frame(width: 38, height: 38).clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
        VStack(alignment: .leading, spacing: 1) {
          Text("账号星图").font(.system(size: 13, weight: .semibold)).foregroundStyle(.secondary)
          Text(t.text).font(.system(size: 15, weight: .semibold))
        }
        Spacer(minLength: 8)
        if let a = t.action {
          Button(a) { store.toastAction?(); withAnimation { store.toast = nil } }.font(.system(size: 15, weight: .semibold))
        }
      }
      .padding(.horizontal, 14).padding(.vertical, 11)
      .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
      .shadow(color: .black.opacity(0.25), radius: 20, y: 8)
      .padding(.horizontal, 14)
      .transition(.move(edge: .top).combined(with: .opacity))
      .id(t.id)
      .gesture(DragGesture().onEnded { v in if v.translation.height < -10 { withAnimation { store.toast = nil } } })
      .sensoryFeedback(.success, trigger: t.id)
    }
  }
}

// MARK: - Motion

private struct SelectedTabKey: EnvironmentKey { static let defaultValue = 0 }
extension EnvironmentValues {
  var selectedTab: Int { get { self[SelectedTabKey.self] } set { self[SelectedTabKey.self] = newValue } }
}

/// The page you switch to slides in a little from the side you came from and settles with a
/// critically-damped spring, like a navigation push but lighter. Hidden state is set while the
/// page is off screen, so it never flashes before animating.
struct TabEnter: ViewModifier {
  let index: Int, tab: Int, dir: CGFloat
  @State private var shown: Bool
  init(index: Int, tab: Int, dir: CGFloat) { self.index = index; self.tab = tab; self.dir = dir; _shown = State(initialValue: index == tab) }
  func body(content: Content) -> some View {
    // Scoped animation: only the opacity/offset animate. The page's own layout (large title,
    // scroll insets) is never pulled into the spring, so the title doesn't slide around.
    content
      .animation(.spring(duration: 0.4, bounce: 0)) {
        $0.opacity(shown ? 1 : 0.35).offset(x: shown ? 0 : dir * 28)
      }
      .onChange(of: tab) { old, t in
        if t == index { shown = true }
        else if old == index {
          var tr = Transaction(); tr.disablesAnimations = true
          withTransaction(tr) { shown = false }
        }
      }
  }
}
extension View {
  func tabEnter(_ index: Int, tab: Int, dir: CGFloat) -> some View { modifier(TabEnter(index: index, tab: tab, dir: dir)) }
}

/// Theme switch done the way iOS apps do it smoothly: freeze a snapshot of the window, flip the
/// window's interface style underneath in one go, then open a circular hole in the snapshot
/// from the button with Core Animation. Nothing in SwiftUI re-renders per frame.
@MainActor enum ThemeFX {
  static var origin: CGPoint? = nil
  static func style(_ t: String) -> UIUserInterfaceStyle { t == "light" ? .light : t == "system" ? .unspecified : .dark }
  static func apply(_ theme: String, animated: Bool) {
    let wins = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
    guard let win = wins.first(where: \.isKeyWindow) ?? wins.first else { return }
    let st = style(theme)
    guard animated, win.overrideUserInterfaceStyle != st, !UIAccessibility.isReduceMotionEnabled,
          let snap = win.snapshotView(afterScreenUpdates: false) else {
      wins.forEach { $0.overrideUserInterfaceStyle = st }; return
    }
    win.addSubview(snap)
    wins.forEach { $0.overrideUserInterfaceStyle = st }
    let b = win.bounds
    let c = origin ?? CGPoint(x: b.midX, y: b.minY + 60)
    let r = hypot(max(c.x, b.width - c.x), max(c.y, b.height - c.y)) + 4
    func hole(_ rad: CGFloat) -> CGPath {
      let p = UIBezierPath(rect: b)
      p.append(UIBezierPath(arcCenter: c, radius: rad, startAngle: 0, endAngle: .pi * 2, clockwise: true))
      return p.cgPath
    }
    let mask = CAShapeLayer()
    mask.fillRule = .evenOdd
    mask.path = hole(r)
    snap.layer.mask = mask
    let a = CABasicAnimation(keyPath: "path")
    a.fromValue = hole(0.5); a.toValue = hole(r)
    a.duration = 0.55
    a.timingFunction = CAMediaTimingFunction(controlPoints: 0.4, 0, 0.2, 1)
    CATransaction.begin()
    CATransaction.setCompletionBlock { snap.removeFromSuperview() }
    mask.add(a, forKey: "reveal")
    CATransaction.commit()
  }
}

/// Records where a control sits on screen, so the theme reveal starts from it.
struct ThemeOrigin: View {
  var body: some View {
    GeometryReader { g in
      Color.clear.preference(key: OriginKey.self, value: CGPoint(x: g.frame(in: .global).midX, y: g.frame(in: .global).midY))
    }
    .onPreferenceChange(OriginKey.self) { p in Task { @MainActor in ThemeFX.origin = p } }
  }
}
struct OriginKey: PreferenceKey {
  static var defaultValue: CGPoint = .zero
  static func reduce(value: inout CGPoint, nextValue: () -> CGPoint) { value = nextValue() }
}
