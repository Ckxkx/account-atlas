import SwiftUI
import UIKit

// MARK: - 2.5 分享我的星图: a card image of which apps the user has, grouped by identity. By default no
// email, number, QQ or 微信号 appears on it at all; the user picks which identities go in.

struct ShareSheet: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  @Environment(\.colorScheme) var scheme
  @State private var hide = true
  @State private var chosen: Set<String> = []
  @State private var ready = false
  @State private var image: UIImage? = nil

  var withApps: [Identity] {
    (store.index.emails + store.index.phones + store.index.logins).filter { !store.accounts(of: $0.id).isEmpty }
  }
  var picked: [Identity] { withApps.filter { chosen.contains($0.id) } }
  var renderKey: String { "\(hide)|\(scheme == .dark)|" + picked.map(\.id).joined(separator: ",") }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          ShareCard(ids: picked, hideValues: hide, dark: scheme == .dark)
            .frame(maxWidth: .infinity)
            .shadow(color: .black.opacity(0.3), radius: 18, y: 8)
        }
        .listRowBackground(Color.clear)
        Section {
          Toggle("隐藏所有账号", isOn: $hide.animation(Motion.smooth))
        } footer: { Text("打开时卡片上只有身份的种类和数量，不出现任何邮箱、手机号、QQ 号或微信号。") }
        Section {
          ForEach(withApps) { i in
            let on = chosen.contains(i.id)
            Button {
              withAnimation(Motion.snappy) { if on { chosen.remove(i.id) } else { chosen.insert(i.id) } }
            } label: {
              HStack(spacing: 12) {
                IdentityGlyph(identity: i, size: 12).foregroundStyle(.white).frame(width: 36, height: 24)
                  .background(skinGradient(i.color), in: CardShape(sim: i.isPhone))
                VStack(alignment: .leading, spacing: 1) {
                  Text(store.title(i)).foregroundStyle(.primary).lineLimit(1)
                  Text(i.kindName + " · \(store.accounts(of: i.id).count) 个 App").font(.system(size: 12)).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: on ? "checkmark.circle.fill" : "circle").font(.system(size: 22))
                  .foregroundStyle(on ? Color(hex: i.color) : Color.secondary.opacity(0.5))
              }
            }
          }
          if withApps.isEmpty { Text("还没有关联了 App 的身份。").foregroundStyle(.secondary) }
        } header: { Text("包含哪些身份") }
      }
      .navigationTitle("分享我的星图").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
        ToolbarItem(placement: .confirmationAction) {
          if let img = image, !picked.isEmpty {
            ShareLink(item: Image(uiImage: img), preview: SharePreview("我的账号星图", image: Image(uiImage: img))) { Text("分享").bold() }
          } else {
            Text("分享").foregroundStyle(.tertiary)
          }
        }
      }
      .onAppear { if !ready { chosen = Set(withApps.map(\.id)); ready = true } }
      .task(id: renderKey) { await render() }
    }
  }

  /// rendered natively (ImageRenderer) at 3x, with the theme the user is looking at
  @MainActor func render() {
    let card = ShareCard(ids: picked, hideValues: hide, dark: scheme == .dark)
      .environment(store)
      .environment(\.colorScheme, scheme)
    let r = ImageRenderer(content: card)
    r.scale = 3
    image = r.uiImage
  }
}

/// The image itself: fixed width, plain fills (no glass, no live blur) so it renders the same off screen.
struct ShareCard: View {
  @Environment(Store.self) var store
  let ids: [Identity]
  let hideValues: Bool
  let dark: Bool

  var body: some View {
    let fg: Color = dark ? .white : Color(hex: "#1c1c1e")
    let sub: Color = dark ? Color.white.opacity(0.62) : Color(hex: "#3c3c43").opacity(0.7)
    let accs = store.data.accounts.filter { a in a.identityIds.contains { x in ids.contains { $0.id == x } } }
    let cats = Dictionary(grouping: accs, by: \.category).map { ($0.key, $0.value.count) }.sorted { $0.1 > $1.1 }
    let accent = Color(hex: ids.first?.color ?? "#0a84ff")
    VStack(alignment: .leading, spacing: 14) {
      HStack(spacing: 12) {
        Image(uiImage: UIImage(named: "AppIcon60x60") ?? UIImage(systemName: "rectangle.stack.fill")!)
          .resizable().frame(width: 40, height: 40).clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        VStack(alignment: .leading, spacing: 2) {
          Text("我的账号星图").font(.system(size: 22, weight: .bold)).foregroundStyle(fg)
          Text(Date().formatted(.dateTime.year().month().day())).font(.system(size: 12)).foregroundStyle(sub)
        }
        Spacer(minLength: 0)
      }
      HStack(spacing: 8) {
        stat("\(ids.count)", "个身份", fg, sub)
        stat("\(accs.count)", "个 App", fg, sub)
        stat("\(cats.count)", "个分类", fg, sub)
      }
      if !cats.isEmpty {
        Text(cats.map { "\($0.0) \($0.1)" }.joined(separator: "  ·  ")).font(.system(size: 12, weight: .medium)).foregroundStyle(sub).lineLimit(2)
      }
      ForEach(ids) { i in identityBlock(i) }
      if ids.isEmpty { Text("选几个身份放进卡片里。").font(.system(size: 14)).foregroundStyle(sub) }
      HStack {
        Text("账号星图").font(.system(size: 13, weight: .semibold)).foregroundStyle(fg)
        Spacer()
        Text("huichen.dev/account-atlas").font(.system(size: 12)).foregroundStyle(sub)
      }.padding(.top, 2)
    }
    .padding(22)
    .frame(width: 360)
    .background {
      ZStack {
        LinearGradient(colors: dark ? [Color(hex: "#15151b"), Color(hex: "#07070a")] : [.white, Color(hex: "#eef0f6")], startPoint: .top, endPoint: .bottom)
        RadialGradient(colors: [accent.opacity(dark ? 0.45 : 0.25), .clear], center: UnitPoint(x: 0.15, y: 0), startRadius: 0, endRadius: 330)
      }
    }
    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
  }

  func stat(_ n: String, _ l: String, _ fg: Color, _ sub: Color) -> some View {
    VStack(spacing: 2) {
      Text(n).font(.system(size: 22, weight: .bold, design: .rounded)).foregroundStyle(fg)
      Text(l).font(.system(size: 11)).foregroundStyle(sub)
    }
    .frame(maxWidth: .infinity).padding(.vertical, 10)
    .background(fg.opacity(0.07), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
  }

  func identityBlock(_ i: Identity) -> some View {
    let list = store.accounts(of: i.id)
    let shown = Array(list.prefix(14))
    let more = list.count > shown.count
    let head = i.kindName + (hideValues ? (i.label.isEmpty || store.hideNames ? "" : " · " + i.label) : " " + maskValue(i))
    let rows = stride(from: 0, to: shown.count, by: 7).map { Array(shown[$0..<min($0 + 7, shown.count)]) }
    return VStack(alignment: .leading, spacing: 10) {
      HStack(spacing: 8) {
        IdentityGlyph(identity: i, size: 15)
        Text(head).font(.system(size: 15, weight: .semibold)).lineLimit(1)
        Spacer(minLength: 4)
        Text("→ \(list.count) 个 App").font(.system(size: 13, weight: .semibold)).opacity(0.9)
      }
      ForEach(Array(rows.enumerated()), id: \.offset) { r in
        HStack(spacing: 8) {
          ForEach(Array(r.element.enumerated()), id: \.element.id) { e in
            if more && r.offset == rows.count - 1 && e.offset == r.element.count - 1 {
              Text("+\(list.count - shown.count + 1)").font(.system(size: 12, weight: .bold, design: .rounded))
                .frame(width: 34, height: 34).background(Color.white.opacity(0.25), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            } else {
              AppIcon(name: e.element.name, domain: e.element.domain, icon: e.element.icon, size: 34)
            }
          }
        }
      }
    }
    .foregroundStyle(.white)
    .padding(14)
    .frame(maxWidth: .infinity, alignment: .leading)
    .background(skinGradient(i.color), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(.white.opacity(0.22), lineWidth: 0.6))
  }
}
