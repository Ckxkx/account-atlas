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
        .preferredColorScheme(store.theme == "light" ? .light : store.theme == "system" ? nil : .dark)
        .onAppear {
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

  var body: some View {
    TabView(selection: $tab) {
      WalletView().tag(0).tabItem { Label("卡包", systemImage: "rectangle.stack.fill") }
      SitesView().tag(1).tabItem { Label("全部 App", systemImage: "square.grid.2x2.fill") }
      MapView().tag(2).tabItem { Label("关系图", systemImage: "point.3.connected.trianglepath.dotted") }
    }
    .sensoryFeedback(.selection, trigger: tab)
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
      .preferredColorScheme(store.theme == "light" ? .light : store.theme == "system" ? nil : .dark)
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
