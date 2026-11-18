import SwiftUI

// identities on the left, apps on the right, joined by threads in the identity's colour
struct MapView: View {
  @EnvironmentObject var store: Store
  @State private var focus: String? = nil

  let cardH: CGFloat = 64, cardGap: CGFloat = 12, appH: CGFloat = 46, appGap: CGFloat = 8, groupGap: CGFloat = 30

  var body: some View {
    NavigationStack {
      ScrollView {
        if store.data.identities.isEmpty {
          Text("添加邮箱或手机号后，这里会画出它们和网站之间的关系。").foregroundStyle(.secondary).padding(40)
        } else {
          GeometryReader { geo in content(width: geo.size.width) }
            .frame(height: height)
            .padding(.top, 10).padding(.bottom, 40)
        }
      }
      .scrollIndicators(.hidden)
      .navigationTitle("关系图")
      .toolbar { TopButtons() }
      .background(Glow().ignoresSafeArea())
      .sensoryFeedback(.selection, trigger: focus)
    }
  }

  var ids: [Identity] { store.data.identities.filter { $0.type == "email" } + store.data.identities.filter { $0.type == "phone" } }
  // apps follow the first identity they belong to, so threads cross as little as possible
  var apps: [Account] {
    let rank = Dictionary(uniqueKeysWithValues: ids.enumerated().map { ($0.element.id, $0.offset) })
    return store.data.accounts.sorted {
      let a = $0.identityIds.compactMap { rank[$0] }.min() ?? 999, b = $1.identityIds.compactMap { rank[$0] }.min() ?? 999
      return a != b ? a < b : $0.name.localizedStandardCompare($1.name) == .orderedAscending
    }
  }
  func idY(_ k: Int) -> CGFloat {
    let emails = store.data.identities.filter { $0.type == "email" }.count
    return CGFloat(k) * (cardH + cardGap) + (k >= emails && emails > 0 ? groupGap : 0) + 22
  }
  func appY(_ k: Int) -> CGFloat { CGFloat(k) * (appH + appGap) + 22 }
  var height: CGFloat {
    max(idY(max(ids.count - 1, 0)) + cardH, appY(max(apps.count - 1, 0)) + appH) + 10
  }

  func content(width: CGFloat) -> some View {
    let L = min(160, width * 0.42), rx = width - 16 - (width * 0.4)
    let ids = self.ids, apps = self.apps
    let appIndex = Dictionary(uniqueKeysWithValues: apps.enumerated().map { ($0.element.id, $0.offset) })
    let linked = Set(apps.filter { a in focus.map { a.identityIds.contains($0) } ?? true }.map(\.id))
    return ZStack(alignment: .topLeading) {
      Canvas { ctx, _ in
        for (k, i) in ids.enumerated() {
          let on = focus == nil || focus == i.id
          let y0 = idY(k) + cardH / 2
          for a in apps where a.identityIds.contains(i.id) {
            guard let j = appIndex[a.id] else { continue }
            let y1 = appY(j) + appH / 2
            var p = Path()
            p.move(to: CGPoint(x: 16 + L, y: y0))
            let mx = (16 + L + rx) / 2
            p.addCurve(to: CGPoint(x: rx, y: y1), control1: CGPoint(x: mx, y: y0), control2: CGPoint(x: mx, y: y1))
            ctx.stroke(p, with: .color(Color(hex: i.color).opacity(on ? 0.75 : 0.08)), lineWidth: on && focus != nil ? 2 : 1.3)
          }
        }
      }
      .allowsHitTesting(false)
      .animation(.easeInOut(duration: 0.3), value: focus)

      // group labels
      if ids.contains(where: { $0.type == "email" }) { label("邮箱").offset(x: 18, y: 0) }
      if let k = ids.firstIndex(where: { $0.type == "phone" }) { label("手机号").offset(x: 18, y: idY(k) - 22) }

      ForEach(Array(ids.enumerated()), id: \.element.id) { e in
        let k = e.offset, i = e.element
        Button {
          withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
            if focus == i.id { store.route = .identity(i.id) } else { focus = i.id }
          }
        } label: { mini(i, width: L) }
          .buttonStyle(PressStyle())
          .opacity(focus == nil || focus == i.id ? 1 : 0.4)
          .scaleEffect(focus == i.id ? 1.04 : 1)
          .offset(x: 16, y: idY(k))
      }
      ForEach(Array(apps.enumerated()), id: \.element.id) { e in
        let j = e.offset, a = e.element
        Button { store.route = .account(a.id) } label: {
          HStack(spacing: 8) {
            AppIcon(name: a.name, domain: a.domain, size: 30)
            Text(a.handle.map { store.handleText($0) } ?? a.name).font(.system(size: 13, weight: .medium)).lineLimit(1)
            Spacer(minLength: 0)
          }
          .padding(.horizontal, 8).frame(width: width - 16 - rx, height: appH)
          .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(PressStyle())
        .foregroundStyle(.primary)
        .opacity(linked.contains(a.id) ? 1 : 0.3)
        .offset(x: rx, y: appY(j))
      }
    }
    .contentShape(Rectangle())
    .onTapGesture { withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) { focus = nil } }
  }

  func label(_ s: String) -> some View {
    Text(s).font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary)
  }
  func mini(_ i: Identity, width: CGFloat) -> some View {
    let n = store.accounts(of: i.id).count
    return VStack(alignment: .leading, spacing: 2) {
      HStack(spacing: 5) {
        Image(systemName: i.isPhone ? "simcard.fill" : "envelope.fill").font(.system(size: 11, weight: .semibold))
        Text("\(n)").font(.system(size: 13, weight: .bold, design: .rounded))
      }.opacity(0.85)
      Text(store.title(i)).font(.system(size: 14, weight: .semibold)).lineLimit(1)
      if !i.label.isEmpty { Text(store.display(i)).font(.system(size: 11)).lineLimit(1).opacity(0.75) }
    }
    .foregroundStyle(.white)
    .padding(.horizontal, 11)
    .frame(width: width, height: cardH, alignment: .leading)
    .background(skinGradient(i.color), in: CardShape(sim: i.isPhone))
    .shadow(color: Color(hex: i.color).opacity(0.4), radius: 10, y: 5)
  }
}
