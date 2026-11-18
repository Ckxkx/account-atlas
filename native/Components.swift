import SwiftUI
import UIKit

// MARK: - colour

extension Color {
  init(hex: String) {
    var s = hex.trimmingCharacters(in: .whitespaces); if s.hasPrefix("#") { s.removeFirst() }
    if s.count == 3 { s = s.map { "\($0)\($0)" }.joined() }
    let v = UInt64(s, radix: 16) ?? 0x3a3a3c
    self.init(red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255)
  }
}

struct Skin { let name: String; let stops: [(String, Double)] }
let SKINS: [(String, Skin)] = [
  ("#0a84ff", Skin(name: "深海", stops: [("#56c6ff", 0), ("#0a84ff", 0.42), ("#3b2fd0", 1)])),
  ("#30d158", Skin(name: "青柠", stops: [("#d2f56a", 0), ("#30d158", 0.48), ("#0b7f6a", 1)])),
  ("#ff9f0a", Skin(name: "落日", stops: [("#ffdf5c", 0), ("#ff9f0a", 0.42), ("#ff3d5a", 1)])),
  ("#ff375f", Skin(name: "莓果", stops: [("#ff9bb8", 0), ("#ff375f", 0.46), ("#9e1470", 1)])),
  ("#bf5af2", Skin(name: "极光", stops: [("#ff86dc", 0), ("#bf5af2", 0.44), ("#4636ee", 1)])),
  ("#64d2ff", Skin(name: "冰川", stops: [("#9df0ff", 0), ("#4cc3fb", 0.40), ("#2a6cf0", 1)])),
  ("#ff453a", Skin(name: "珊瑚", stops: [("#ffa36b", 0), ("#ff453a", 0.46), ("#c1185f", 1)])),
  ("#5e5ce6", Skin(name: "靛蓝", stops: [("#9a98ff", 0), ("#5e5ce6", 0.46), ("#1f1b78", 1)])),
  ("#00c7be", Skin(name: "海湾", stops: [("#8af5d6", 0), ("#00c7be", 0.44), ("#0a62b0", 1)])),
  ("#3a3a3c", Skin(name: "曜石", stops: [("#76767c", 0), ("#3a3a3c", 0.46), ("#0d0d10", 1)])),
]
let COLORS = SKINS.map(\.0)

func skinGradient(_ c: String) -> LinearGradient {
  let stops: [Gradient.Stop]
  if let s = SKINS.first(where: { $0.0 == c })?.1 { stops = s.stops.map { Gradient.Stop(color: Color(hex: $0.0), location: $0.1) } }
  else { let k = Color(hex: c); stops = [.init(color: k.opacity(0.7), location: 0), .init(color: k, location: 0.5), .init(color: k, location: 1)] }
  // 145deg in CSS
  return LinearGradient(stops: stops, startPoint: UnitPoint(x: 0.21, y: 0), endPoint: UnitPoint(x: 0.79, y: 1))
}

func brandColor(name: String, domain: String) -> Color {
  if let p = presetFor(name: name, domain: domain) { return Color(hex: p.color) }
  var h: UInt32 = 0
  let key = cleanDomain(domain).isEmpty ? name.lowercased() : cleanDomain(domain)
  for u in key.unicodeScalars { h = h &* 31 &+ u.value }
  return Color(hue: Double(h % 360) / 360, saturation: 0.62, brightness: 0.74)
}

// MARK: - app icon

final class IconCache {
  static var local: [String: UIImage] = [:]
  static func image(name: String, domain: String) -> UIImage? {
    let d = cleanDomain(domain), n = name.lowercased()
    guard let p = Presets.all.first(where: { (!d.isEmpty && (d == $0.domain || d.hasSuffix("." + $0.domain))) || (d.isEmpty && $0.name.lowercased() == n) }) else { return nil }
    if let i = local[p.domain] { return i }
    guard let path = Bundle.main.path(forResource: p.domain, ofType: "png", inDirectory: "icons"), let i = UIImage(contentsOfFile: path) else { return nil }
    local[p.domain] = i
    return i
  }
}

struct AppIcon: View {
  let name: String
  let domain: String
  var size: CGFloat = 52
  var body: some View {
    let r = RoundedRectangle(cornerRadius: size * 0.235, style: .continuous)
    content
      .frame(width: size, height: size)
      .clipShape(r)
      .overlay(r.strokeBorder(.white.opacity(0.12), lineWidth: 0.5))
  }
  @ViewBuilder var content: some View {
    if let img = IconCache.image(name: name, domain: domain) {
      Image(uiImage: img).resizable().interpolation(.high).scaledToFill()
    } else if !cleanDomain(domain).isEmpty, let u = URL(string: "https://favicon.im/\(cleanDomain(domain))?larger=true") {
      AsyncImage(url: u) { phase in
        if case .success(let img) = phase {
          ZStack { Color.white; img.resizable().interpolation(.high).scaledToFit().padding(size * 0.14) }
        } else { letter }
      }
    } else { letter }
  }
  var letter: some View {
    let b = brandColor(name: name, domain: domain)
    return ZStack {
      LinearGradient(colors: [b.opacity(0.78), b], startPoint: .top, endPoint: .bottom)
      Text(String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased())
        .font(.system(size: size * 0.44, weight: .semibold, design: .rounded)).foregroundStyle(.white)
    }
  }
}

// MARK: - wallet card

struct SIMShape: Shape {
  var cut: CGFloat = 26
  var radius: CGFloat = 22
  func path(in r: CGRect) -> Path {
    var p = Path()
    p.move(to: CGPoint(x: r.minX + radius, y: r.minY))
    p.addLine(to: CGPoint(x: r.maxX - cut, y: r.minY))
    p.addLine(to: CGPoint(x: r.maxX, y: r.minY + cut))
    p.addLine(to: CGPoint(x: r.maxX, y: r.maxY - radius))
    p.addQuadCurve(to: CGPoint(x: r.maxX - radius, y: r.maxY), control: CGPoint(x: r.maxX, y: r.maxY))
    p.addLine(to: CGPoint(x: r.minX + radius, y: r.maxY))
    p.addQuadCurve(to: CGPoint(x: r.minX, y: r.maxY - radius), control: CGPoint(x: r.minX, y: r.maxY))
    p.addLine(to: CGPoint(x: r.minX, y: r.minY + radius))
    p.addQuadCurve(to: CGPoint(x: r.minX + radius, y: r.minY), control: CGPoint(x: r.minX, y: r.minY))
    p.closeSubpath()
    return p
  }
}

struct CardShape: Shape {
  var sim: Bool
  func path(in r: CGRect) -> Path {
    sim ? SIMShape(cut: r.width * 0.085, radius: r.width * 0.065).path(in: r)
        : RoundedRectangle(cornerRadius: r.width * 0.065, style: .continuous).path(in: r)
  }
}

struct Chip: View {
  var body: some View {
    ZStack {
      RoundedRectangle(cornerRadius: 5, style: .continuous)
        .fill(LinearGradient(colors: [Color(hex: "#f7e3a1"), Color(hex: "#d4a84b"), Color(hex: "#f1d78a")], startPoint: .topLeading, endPoint: .bottomTrailing))
      Path { p in
        p.move(to: CGPoint(x: 0, y: 8)); p.addLine(to: CGPoint(x: 9, y: 8))
        p.move(to: CGPoint(x: 0, y: 14)); p.addLine(to: CGPoint(x: 9, y: 14))
        p.move(to: CGPoint(x: 23, y: 8)); p.addLine(to: CGPoint(x: 32, y: 8))
        p.move(to: CGPoint(x: 23, y: 14)); p.addLine(to: CGPoint(x: 32, y: 14))
        p.addRoundedRect(in: CGRect(x: 9, y: 4, width: 14, height: 14), cornerSize: CGSize(width: 3, height: 3))
      }.stroke(Color(hex: "#8a6a24").opacity(0.55), lineWidth: 0.8)
    }
    .frame(width: 32, height: 22)
  }
}

struct WalletCard: View {
  @EnvironmentObject var store: Store
  let identity: Identity
  var body: some View {
    let i = identity
    let accs = store.accounts(of: i.id)
    let shape = CardShape(sim: i.isPhone)
    ZStack(alignment: .topLeading) {
      skinGradient(i.color)
      poster(accs.sorted { $0.createdAt > $1.createdAt })
      // soft light from the top-left, like a printed card
      RadialGradient(colors: [.white.opacity(0.32), .clear], center: UnitPoint(x: 0.12, y: 0), startRadius: 0, endRadius: 260)
        .blendMode(.softLight)
      VStack(alignment: .leading, spacing: 0) {
        HStack(alignment: .top) {
          HStack(spacing: 8) {
            if i.isPhone { Chip() } else { Image(systemName: "envelope").font(.system(size: 17, weight: .medium)) }
            Text(i.label.isEmpty ? (i.isPhone ? "手机号" : "邮箱") : i.label).font(.system(size: 16, weight: .semibold))
          }
          Spacer()
          VStack(alignment: .trailing, spacing: 0) {
            Text("\(accs.count)").font(.system(size: 34, weight: .bold, design: .rounded)).contentTransition(.numericText())
            Text("个网站与 App").font(.system(size: 11, weight: .medium)).opacity(0.75)
          }
        }
        Spacer(minLength: 6)
        Text(store.display(i))
          .font(.system(size: 21, weight: .semibold, design: .rounded)).monospacedDigit()
          .lineLimit(1).minimumScaleFactor(0.6)
        HStack(alignment: .bottom) {
          Text(footNote(i)).font(.system(size: 12.5, weight: .medium)).opacity(0.75).lineLimit(1)
          Spacer()
          IconStack(accounts: accs)
        }.padding(.top, 8)
      }
      .foregroundStyle(.white)
      .shadow(color: .black.opacity(0.18), radius: 1, y: 0.5)
      .padding(18)
    }
    .aspectRatio(1.586, contentMode: .fit)
    .clipShape(shape)
    .overlay(shape.stroke(.white.opacity(0.22), lineWidth: 0.6))
    .shadow(color: Color(hex: i.color).opacity(0.45), radius: 22, y: 14)
  }
  func footNote(_ i: Identity) -> String {
    if i.isPhone {
      let c = carrier(i.value)
      let parts = [(!c.isEmpty && !i.notes.contains(String(c.dropFirst(2)))) ? c : "", i.notes].filter { !$0.isEmpty }
      return parts.isEmpty ? "手机号" : parts.joined(separator: " · ")
    }
    return i.notes.isEmpty ? "邮箱" : i.notes
  }
  // a blurred mosaic of the brand colours of what this identity unlocks
  func poster(_ accs: [Account]) -> some View {
    let cols = Array(repeating: GridItem(.flexible(), spacing: 0), count: 3)
    return LazyVGrid(columns: cols, spacing: 0) {
      ForEach(0..<9, id: \.self) { k in
        let a = accs.isEmpty ? nil : accs[k % accs.count]
        Rectangle().fill(a.map { brandColor(name: $0.name, domain: $0.domain) } ?? .clear).aspectRatio(1.586, contentMode: .fit)
      }
    }
    .blur(radius: 30).opacity(accs.isEmpty ? 0 : 0.35).blendMode(.overlay)
  }
}

struct IconStack: View {
  let accounts: [Account]
  var body: some View {
    HStack(spacing: -9) {
      ForEach(accounts.prefix(4)) { a in
        AppIcon(name: a.name, domain: a.domain, size: 28)
          .overlay(RoundedRectangle(cornerRadius: 28 * 0.235, style: .continuous).stroke(.white.opacity(0.55), lineWidth: 1.5))
      }
      if accounts.count > 4 {
        Text("+\(accounts.count - 4)").font(.system(size: 11, weight: .bold, design: .rounded))
          .frame(height: 28).padding(.horizontal, 7).background(.ultraThinMaterial, in: Capsule()).padding(.leading, 12)
      }
    }
  }
}

// MARK: - rows

struct IdentityCaps: View {
  @EnvironmentObject var store: Store
  let ids: [String]
  var body: some View {
    let list = ids.compactMap { store.identity($0) }
    HStack(spacing: 5) {
      if list.isEmpty { Text("未关联").font(.caption).foregroundStyle(.secondary) }
      ForEach(list) { i in
        HStack(spacing: 4) {
          Circle().fill(Color(hex: i.color)).frame(width: 6, height: 6)
          Text(store.title(i)).lineLimit(1)
        }
        .font(.system(size: 12, weight: .medium))
        .padding(.horizontal, 7).padding(.vertical, 3)
        .background(Color(hex: i.color).opacity(0.16), in: Capsule())
      }
    }
  }
}

struct ReelRow: View {
  @EnvironmentObject var store: Store
  let account: Account
  var meta: String? = nil
  var body: some View {
    let a = account, b = brandColor(name: a.name, domain: a.domain)
    HStack(spacing: 12) {
      AppIcon(name: a.name, domain: a.domain, size: 44)
      VStack(alignment: .leading, spacing: 4) {
        HStack(spacing: 6) {
          Text(a.name).font(.system(size: 16, weight: .semibold)).lineLimit(1)
          if let h = a.handle { Text(store.handleText(h)).font(.system(size: 13)).foregroundStyle(.secondary).lineLimit(1) }
        }
        IdentityCaps(ids: a.identityIds)
      }
      Spacer(minLength: 4)
      if let meta { Text(meta).font(.system(size: 12, weight: .medium)).foregroundStyle(.secondary) }
      else { Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(.tertiary) }
    }
    .padding(.horizontal, 12).padding(.vertical, 10)
    .background {
      ZStack {
        Rectangle().fill(.ultraThinMaterial)
        b.opacity(0.14)
      }
      .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
    .contentShape(Rectangle())
  }
}

// press feedback that feels like a card being pushed
struct PressStyle: ButtonStyle {
  var scale: CGFloat = 0.96
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .scaleEffect(configuration.isPressed ? scale : 1)
      .animation(.spring(response: 0.3, dampingFraction: 0.6), value: configuration.isPressed)
  }
}
