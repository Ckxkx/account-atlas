import SwiftUI

// MARK: - 新手教程: shown once on a fresh install (and from 设置 › 使用教程). Skippable at any page.
// Sample data is never loaded behind the user's back: the last page offers it as a clearly labelled
// choice, and the wallet then says it's sample data with a one-tap 清除.

struct TourPage: Identifiable {
  let id: Int
  let symbol: String
  let tint: Color
  let title: String
  let body: String
  let tip: String
}

let TOUR: [TourPage] = [
  TourPage(id: 0, symbol: "rectangle.stack.fill", tint: .blue, title: "欢迎使用账号星图",
           body: "记下每个网站和 App 是用哪个邮箱、哪个手机号注册的。换号、注销、找回密码时，一眼就知道要改哪些。",
           tip: "不保存任何密码，数据只在你的手机上。"),
  TourPage(id: 1, symbol: "square.grid.2x2.fill", tint: .green, title: "第一步：添加 App",
           body: "点右上角「+」，选你用的网站和 App（可以多选），再选它怎么登录：哪个邮箱、手机号，还是微信、QQ。列表里没有的，当场新建就行。",
           tip: "搜不到的 App 会从 App Store 找，带上官方图标。"),
  TourPage(id: 2, symbol: "envelope.fill", tint: .indigo, title: "卡包：每个身份一张卡",
           body: "每个邮箱、手机号、微信、QQ 都是一张卡。点开一张，就能看到它关联的全部 App，包括通过绑定它的微信 / QQ 登录的。Apple ID 是邮箱或手机号上的一个标记，不用单独建卡。",
           tip: "上方可以在 邮箱 / 手机号 / 微信/QQ 之间切换。"),
  TourPage(id: 3, symbol: "point.3.connected.trianglepath.dotted", tint: .orange, title: "关系图",
           body: "所有邮箱、手机号和 App 会自动连成一张图。点一个节点，就能看到它牵连着哪些账号。",
           tip: "准备换手机号？先在这里看看要改多少个。"),
  TourPage(id: 4, symbol: "lock.shield.fill", tint: .pink, title: "隐私与备份",
           body: "右上角可以一键打码，截图也安心。在「设置」里能导出备份，或加密同步到你自己的 Gist。",
           tip: "以后更新直接覆盖安装，数据不会丢。"),
]

struct OnboardingView: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  /// first launch: the last page also offers the sample data
  var offerDemo: Bool
  @State private var page = 0

  var last: Bool { page == TOUR.count - 1 }

  var body: some View {
    VStack(spacing: 0) {
      HStack {
        Spacer()
        if !last { Button("跳过") { finish() }.font(.system(size: 16, weight: .medium)).foregroundStyle(.secondary) }
      }
      .frame(height: 44).padding(.horizontal, 20)

      TabView(selection: $page) {
        ForEach(TOUR) { p in pageView(p).tag(p.id) }
      }
      .tabViewStyle(.page(indexDisplayMode: .never))
      .animation(Motion.smooth, value: page)

      HStack(spacing: 7) {
        ForEach(TOUR) { p in
          Capsule().fill(p.id == page ? Color.primary : Color.secondary.opacity(0.3)).frame(width: p.id == page ? 18 : 7, height: 7)
        }
      }
      .animation(Motion.snappy, value: page)
      .padding(.bottom, 22)

      VStack(spacing: 12) {
        Button {
          if last { finish() } else { withAnimation(Motion.smooth) { page += 1 } }
        } label: {
          Text(last ? "开始使用" : "下一步").font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
            .frame(maxWidth: .infinity).frame(height: 52).background(Color.accentColor, in: Capsule())
        }
        .buttonStyle(PressStyle(scale: 0.97))
        if last && offerDemo && store.data.identities.isEmpty && store.data.accounts.isEmpty {
          Button { store.loadDemo(); finish() } label: {
            Text("先载入示例数据看看").font(.system(size: 15, weight: .medium)).frame(maxWidth: .infinity).frame(height: 40)
          }
          Text("示例是虚构的邮箱和 App，随时可以一键清除。").font(.system(size: 12)).foregroundStyle(.secondary)
        }
      }
      .padding(.horizontal, 28).padding(.bottom, 24)
      .frame(minHeight: 150, alignment: .top)
    }
    .background(Glow().ignoresSafeArea())
    .sensoryFeedback(.selection, trigger: page)
    .interactiveDismissDisabled(false)
  }

  func pageView(_ p: TourPage) -> some View {
    VStack(spacing: 22) {
      Spacer(minLength: 10)
      ZStack {
        RoundedRectangle(cornerRadius: 34, style: .continuous).fill(p.tint.gradient).frame(width: 128, height: 128)
          .shadow(color: p.tint.opacity(0.45), radius: 24, y: 10)
        Image(systemName: p.symbol).font(.system(size: 54, weight: .semibold)).foregroundStyle(.white)
      }
      .padding(.bottom, 8)
      Text(p.title).font(.system(size: 26, weight: .bold)).multilineTextAlignment(.center)
      Text(p.body).font(.system(size: 16)).foregroundStyle(.secondary).multilineTextAlignment(.center).lineSpacing(4)
        .fixedSize(horizontal: false, vertical: true)
      Label(p.tip, systemImage: "lightbulb.fill").font(.system(size: 13, weight: .medium))
        .padding(.horizontal, 14).padding(.vertical, 9)
        .background(.glass, in: Capsule())
      Spacer(minLength: 10)
    }
    .padding(.horizontal, 30)
  }

  func finish() {
    UserDefaults.standard.set(true, forKey: "atlas.tourDone")
    dismiss()
  }
}

/// over the wallet while sample data is on the device, so nobody wonders where it came from
struct DemoBanner: View {
  @Environment(Store.self) var store
  var body: some View {
    if store.hasDemo {
      HStack(spacing: 10) {
        Image(systemName: "info.circle.fill").foregroundStyle(Color.accentColor)
        Text("现在显示的是示例数据").font(.system(size: 14, weight: .medium))
        Spacer()
        Button("清除示例") { withAnimation(Motion.snappy) { store.clearDemo() } }
          .font(.system(size: 14, weight: .semibold))
      }
      .padding(.horizontal, 14).padding(.vertical, 11)
      .background(.glass, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
      .transition(.opacity.combined(with: .move(edge: .top)))
    }
  }
}
