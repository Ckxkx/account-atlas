import SwiftUI

// MARK: - 2.5 操作演示: a hands-on tour on the real screens. A spotlight sits on the control to use and says
// what to tap; the step moves on when the user does it (add -> pick -> next -> who -> save -> map). It runs
// on sandbox data (one 示例邮箱): nothing is saved or synced, and the real data is back the moment it ends.
// 「跳过演示」 is always there. Shown once on a fresh install, and from 设置 › 操作演示.

extension Store {
  static let pickerTourSteps: Set<String> = ["pick", "next", "who", "save"]
  static let tourOrder = ["add", "pick", "next", "who", "save", "map"]
}

struct TourText { let target: String?; let title: String; let body: String }
func tourText(_ step: String) -> TourText {
  switch step {
  case "add": return TourText(target: "add", title: "点右上角的 +", body: "先添加一个 App 试试。这是演示，不会保存。")
  case "pick": return TourText(target: "tile", title: "点一个 App 选上它", body: "也可以在上面搜索，搜不到的会从 App Store 找。")
  case "next": return TourText(target: "next", title: "点「下一步」", body: "接下来选它怎么登录。")
  case "who": return TourText(target: "who", title: "点「示例邮箱」", body: "选这个 App 用哪个邮箱、手机号、微信或 QQ 登录，可以多选。")
  case "save": return TourText(target: "save", title: "点「保存」", body: "就记下来了。")
  default: return TourText(target: nil, title: "它已经连到关系图上了", body: "以后换手机号、注销邮箱前，来这里看一眼牵连了哪些 App。演示数据会在结束时清掉。")
  }
}

/// marks a control the tour can point at (its frame on screen)
struct TourAnchor: View {
  @Environment(Store.self) var store
  let id: String
  var body: some View {
    GeometryReader { g in
      let r = g.frame(in: .global)
      Color.clear
        .onAppear { store.setTourRect(id, r) }
        .onChange(of: r) { _, n in store.setTourRect(id, n) }
    }
    .allowsHitTesting(false)
  }
}

/// The dim layer with a hole over the control to use. It never takes a touch, so the tap lands on the real
/// control; only the tip card is interactive. One instance on the main screen, one inside the picker sheet.
struct TourLayer: View {
  @Environment(Store.self) var store
  let inPicker: Bool
  @State private var pulse = false

  var body: some View {
    if let step = store.tourStep, Store.pickerTourSteps.contains(step) == inPicker {
      let t = tourText(step)
      GeometryReader { g in
        let o = g.frame(in: .global).origin
        let hole: CGRect? = t.target.flatMap { store.tourRects[$0] }.map { $0.offsetBy(dx: -o.x, dy: -o.y).insetBy(dx: -8, dy: -8) }
        ZStack {
          Path { p in
            p.addRect(CGRect(x: -2000, y: -2000, width: 4000 + g.size.width, height: 4000 + g.size.height))
            if let h = hole { p.addRoundedRect(in: h, cornerSize: CGSize(width: 16, height: 16), style: .continuous) }
          }
          .fill(Color.black.opacity(hole == nil ? 0.45 : 0.58), style: FillStyle(eoFill: true))
          .allowsHitTesting(false)
          if let h = hole {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
              .stroke(Color.white.opacity(pulse ? 0.35 : 0.9), lineWidth: 2)
              .frame(width: h.width + (pulse ? 10 : 2), height: h.height + (pulse ? 10 : 2))
              .position(x: h.midX, y: h.midY)
              .allowsHitTesting(false)
          }
          let top = hole.map { $0.midY > g.size.height / 2 } ?? true
          VStack {
            if !top { Spacer(minLength: 0) }
            tip(step, t)
            if top { Spacer(minLength: 0) }
          }
          .padding(.horizontal, 18).padding(.top, 64).padding(.bottom, 96)
        }
        .animation(Motion.smooth, value: hole)
      }
      .ignoresSafeArea()
      .transition(.opacity)
      .onAppear { withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { pulse = true } }
    }
  }

  func tip(_ step: String, _ t: TourText) -> some View {
    let n = (Store.tourOrder.firstIndex(of: step) ?? 0) + 1
    let last = step == "map"
    return VStack(alignment: .leading, spacing: 6) {
      HStack(alignment: .firstTextBaseline) {
        Image(systemName: last ? "info.circle.fill" : "hand.tap.fill").foregroundStyle(Color.accentColor)
        Text(t.title).font(.system(size: 17, weight: .semibold))
        Spacer()
        Text("\(n) / \(Store.tourOrder.count)").font(.system(size: 13)).foregroundStyle(.secondary)
      }
      Text(t.body).font(.system(size: 14)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
      HStack(spacing: 10) {
        Spacer()
        if last {
          if store.tourFirstRun && !Store.hasReal(store.realData) && !store.syncOn {
            Button("载入示例看看") { withAnimation(Motion.smooth) { store.endTour(loadDemo: true) } }.font(.system(size: 15, weight: .medium))
          }
          Button { withAnimation(Motion.smooth) { store.endTour() } } label: {
            Text("完成").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
              .padding(.horizontal, 18).padding(.vertical, 8).background(Color.accentColor, in: Capsule())
          }
        } else {
          Button("跳过演示") { withAnimation(Motion.smooth) { store.endTour() } }.font(.system(size: 15, weight: .medium)).foregroundStyle(.secondary)
        }
      }.padding(.top, 6)
    }
    .padding(16)
    .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color(.secondarySystemBackground)))
    .shadow(color: .black.opacity(0.35), radius: 24, y: 10)
    .id(step)
    .transition(.opacity.combined(with: .move(edge: .bottom)))
  }
}

/// over the wallet while sample data is on the device, so nobody wonders where it came from
struct DemoBanner: View {
  @Environment(Store.self) var store
  var body: some View {
    if store.hasDemo && !store.sandbox {
      HStack(spacing: 10) {
        Image(systemName: "info.circle.fill").foregroundStyle(Color.accentColor)
        Text("现在显示的有示例数据").font(.system(size: 14, weight: .medium))
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
