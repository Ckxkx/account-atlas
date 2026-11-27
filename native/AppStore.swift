import SwiftUI
import UIKit
import PhotosUI

// MARK: - custom icons: square, ≤128 px, stored on the account as a data URL

enum IconCodec {
  static let side: CGFloat = 128
  static let budget = 24 * 1024

  /// the bytes inside a data:image/...;base64, URL
  static func data(fromDataURL s: String) -> Data? {
    guard s.hasPrefix("data:image/"), let r = s.range(of: ";base64,") else { return nil }
    return Data(base64Encoded: String(s[r.upperBound...]), options: .ignoreUnknownCharacters)
  }

  /// center-crops any image to a square, downscales it to 128x128 and encodes it (PNG, or JPEG
  /// q 0.85 when the PNG is over budget). Runs fine off the main thread.
  static func dataURL(from raw: Data) -> String? {
    guard let src = UIImage(data: raw), let cg = normalized(src) else { return nil }
    let w = CGFloat(cg.width), h = CGFloat(cg.height), m = min(w, h)
    guard m >= 1, let sq = cg.cropping(to: CGRect(x: ((w - m) / 2).rounded(.down), y: ((h - m) / 2).rounded(.down), width: m, height: m)) else { return nil }
    let out = min(side, m)
    let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1; fmt.opaque = false
    let img = UIGraphicsImageRenderer(size: CGSize(width: out, height: out), format: fmt).image { ctx in
      ctx.cgContext.interpolationQuality = .high
      UIImage(cgImage: sq).draw(in: CGRect(x: 0, y: 0, width: out, height: out))
    }
    if let png = img.pngData(), png.count <= budget { return "data:image/png;base64," + png.base64EncodedString() }
    // JPEG has no alpha: flatten onto white so transparent corners don't turn black
    let flat = UIGraphicsImageRenderer(size: img.size, format: { let f = UIGraphicsImageRendererFormat(); f.scale = 1; f.opaque = true; return f }()).image { _ in
      UIColor.white.setFill(); UIRectFill(CGRect(origin: .zero, size: img.size)); img.draw(at: .zero)
    }
    guard let jpg = flat.jpegData(compressionQuality: 0.85) else { return nil }
    return "data:image/jpeg;base64," + jpg.base64EncodedString()
  }

  /// bakes in the EXIF orientation of photos, so the crop is taken from what the user sees
  private static func normalized(_ i: UIImage) -> CGImage? {
    if i.imageOrientation == .up, let cg = i.cgImage { return cg }
    let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1
    return UIGraphicsImageRenderer(size: i.size, format: fmt).image { _ in i.draw(at: .zero) }.cgImage
  }

  static func dataURL(downloading url: URL) async -> String? {
    guard let res = try? await URLSession.shared.data(from: url), ((res.1 as? HTTPURLResponse)?.statusCode ?? 0) == 200 else { return nil }
    let d = res.0
    return await Task.detached(priority: .userInitiated) { IconCodec.dataURL(from: d) }.value
  }

  static func dataURL(from item: PhotosPickerItem) async -> String? {
    guard let d = try? await item.loadTransferable(type: Data.self) else { return nil }
    return await Task.detached(priority: .userInitiated) { IconCodec.dataURL(from: d) }.value
  }
}

// MARK: - App Store search (iTunes Search API: free, no key)

struct AppStoreApp: Decodable, Hashable, Identifiable {
  let trackId: Int
  let trackName: String
  let sellerUrl: String?
  let primaryGenreName: String?
  let genres: [String]?
  let artworkUrl100: String?
  let artworkUrl512: String?
  let contentAdvisoryRating: String?
  let trackContentRating: String?
  let advisories: [String]?
  var id: Int { trackId }
  /// 18+ / sexual content: never shown in search (see isAdultApp)
  var adult: Bool { isAdultApp(name: trackName, genres: [primaryGenreName ?? ""] + (genres ?? []), ratings: [contentAdvisoryRating, trackContentRating], advisories: advisories ?? []) }

  /// "支付宝-你的AI生活伙伴" -> "支付宝": cut at the first dash when what's left is at least 2 chars
  var cleanName: String {
    let t = trackName.trimmingCharacters(in: .whitespaces)
    guard let r = t.rangeOfCharacter(from: CharacterSet(charactersIn: "-–—")) else { return t }
    let head = t[..<r.lowerBound].trimmingCharacters(in: .whitespaces)
    return head.count >= 2 ? head : t
  }
  var domain: String { sellerUrl.flatMap { URL(string: $0)?.host }.map { cleanDomain($0) } ?? "" }
  var category: String {
    let map: [String: String] = [
      "Social Networking": "社交", "社交": "社交",
      "Shopping": "购物", "购物": "购物",
      "Finance": "支付金融", "财务": "支付金融",
      "Developer Tools": "开发", "开发者工具": "开发",
      "Games": "游戏", "游戏": "游戏",
      "Entertainment": "娱乐", "Music": "娱乐", "Video": "娱乐", "Photo & Video": "娱乐",
      "娱乐": "娱乐", "音乐": "娱乐", "摄影与录像": "娱乐",
      "Travel": "出行", "Navigation": "出行", "旅游": "出行", "导航": "出行",
      "Lifestyle": "生活", "Food & Drink": "生活", "生活": "生活", "美食佳饮": "生活",
    ]
    let hits = ([primaryGenreName].compactMap { $0 } + (genres ?? [])).compactMap { map[$0] }
    // 生活 (Lifestyle, Food & Drink) is a catch-all genre: only when nothing more specific fits
    return hits.first { $0 != "生活" } ?? hits.first ?? "工具"
  }
  var thumbURL: String { artworkUrl100 ?? "" }
  var bigURL: URL? {
    if let s = artworkUrl512, let u = URL(string: s) { return u }
    return artworkUrl100.flatMap { URL(string: $0.replacingOccurrences(of: "100x100", with: "256x256")) }
  }
  func iconDataURL() async -> String? {
    guard let u = bigURL else { return nil }
    return await IconCodec.dataURL(downloading: u)
  }
}

struct SiteHit: Hashable { let label: String; let domain: String }

/// no online domain lookup: the app stays offline apart from the App Store search
enum SiteLookup { static func find(_ q: String) async -> [SiteHit] { [] } }

/// 2.5: adult apps never show up in App Store search. An 18+ rating, a frequent / intense (or graphic)
/// sexual-content or nudity advisory, or an adult word in the name or genre. Mild advisories (common on
/// social apps and games) are fine. Mirrors app.js / Model.kt.
func isAdultApp(name: String, genres: [String] = [], ratings: [String?] = [], advisories: [String] = []) -> Bool {
  func has(_ s: String, _ pattern: String) -> Bool { s.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil }
  if ratings.contains(where: { ($0 ?? "").trimmingCharacters(in: .whitespaces).hasPrefix("18") }) { return true }
  let sexual = "sexual|nudity|性内容|性相关|裸露|色情", strong = "\\b(frequent|intense|graphic)\\b|频繁|强烈|极端|大量"
  if advisories.contains(where: { has($0, sexual) && has($0, strong) }) { return true }
  let words = "\\b(sex|sexy|porn|porno|nsfw|hentai|adult|xxx|nude|nudes|onlyfans|camgirl|hookup)\\b|18\\s*\\+|18禁|性爱|色情|约炮|裸聊|福利姬|情色|黄片|一夜情|成年人|av女优|成人(?!教育|高考|自考|学历|英语|本科|大学|学习)"
  return ([name] + genres).contains { has($0, words) }
}

enum AppStoreSearch {
  private struct Reply: Decodable { let results: [AppStoreApp] }
  /// nil when offline or anything else goes wrong: callers just show no App Store section
  static func search(_ q: String) async -> [AppStoreApp]? {
    var c = URLComponents(string: "https://itunes.apple.com/search")!
    c.queryItems = [.init(name: "term", value: q), .init(name: "country", value: "cn"), .init(name: "entity", value: "software"), .init(name: "limit", value: "10"), .init(name: "explicit", value: "No")]
    guard let u = c.url, let res = try? await URLSession.shared.data(from: u), ((res.1 as? HTTPURLResponse)?.statusCode ?? 0) == 200 else { return nil }
    return (try? JSONDecoder().decode(Reply.self, from: res.0))?.results.filter { !$0.adult }
  }
}

@MainActor extension View {
  /// debounced App Store lookup for `query` (≥1 char). `.task(id:)` cancels the previous run, so an
  /// answer to an older query is never applied after a newer one.
  func appStoreResults(for query: String, into results: Binding<[AppStoreApp]>) -> some View {
    task(id: query) {
      let q = query.trimmingCharacters(in: .whitespaces)
      guard !q.isEmpty else { results.wrappedValue = []; return }
      try? await Task.sleep(for: .milliseconds(350))
      guard !Task.isCancelled else { return }
      let r = await AppStoreSearch.search(q)
      guard !Task.isCancelled else { return }
      withAnimation(Motion.snappy) { results.wrappedValue = r ?? [] }
    }
  }
}

/// the "App Store" label shown over store results
struct AppStoreBadge: View {
  var body: some View {
    Label("App Store", systemImage: "apple.logo").font(.system(size: 13, weight: .semibold)).foregroundStyle(.secondary)
  }
}

/// 更换图标 › 从 App Store 搜索 (edit form): a search prefilled with the App's name; tapping a result hands
/// it back (the caller shows its artwork right away and stores the downscaled icon). 取消 changes nothing.
struct AppStoreIconSheet: View {
  @Environment(\.dismiss) var dismiss
  let onPick: (AppStoreApp) -> Void
  @State private var query: String
  @State private var results: [AppStoreApp] = []
  @State private var phase: Phase = .idle
  @State private var first = true
  @FocusState private var focused: Bool
  enum Phase { case idle, loading, done, offline }

  init(initial: String, onPick: @escaping (AppStoreApp) -> Void) {
    self.onPick = onPick
    _query = State(initialValue: initial.trimmingCharacters(in: .whitespaces))
  }

  var trimmed: String { query.trimmingCharacters(in: .whitespaces) }

  var body: some View {
    NavigationStack {
      List {
        Section {
          HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField("App 名称", text: $query).focused($focused).submitLabel(.search)
              .textInputAutocapitalization(.never).autocorrectionDisabled()
            if !query.isEmpty {
              Button { query = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.tertiary) }
                .buttonStyle(.plain).accessibilityLabel("清除")
            }
          }
        }
        Section {
          if trimmed.isEmpty {
            message("输入 App 名称搜索")
          } else if phase == .offline {
            message("网络不可用")
          } else if phase == .done && results.isEmpty {
            message("没有找到")
          } else if results.isEmpty {
            HStack { Spacer(); ProgressView(); Spacer() }.listRowBackground(Color.clear)
          } else {
            ForEach(results) { a in
              Button { onPick(a); dismiss() } label: {
                HStack(spacing: 12) {
                  AppIcon(name: a.cleanName, domain: a.domain, url: a.thumbURL, size: 40)
                  VStack(alignment: .leading, spacing: 1) {
                    Text(a.cleanName).foregroundStyle(.primary).lineLimit(1)
                    Text(a.domain.isEmpty ? a.category : a.domain).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1)
                  }
                  Spacer(minLength: 0)
                }
              }
            }
          }
        } header: { if !results.isEmpty { AppStoreBadge() } }
      }
      .task(id: trimmed) {
        let q = trimmed
        guard !q.isEmpty else { results = []; phase = .idle; return }
        phase = .loading
        // first search right away (prefilled name), later edits debounced like the add flow
        if first { first = false } else { try? await Task.sleep(for: .milliseconds(350)) }
        guard !Task.isCancelled else { return }
        let r = await AppStoreSearch.search(q)
        guard !Task.isCancelled else { return }
        withAnimation(Motion.snappy) {
          if let r { results = r; phase = .done } else { results = []; phase = .offline }
        }
      }
      .navigationTitle("从 App Store 搜索").navigationBarTitleDisplayMode(.inline)
      .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }
    }
    .presentationDetents([.medium, .large])
    .presentationDragIndicator(.visible)
  }

  func message(_ s: String) -> some View {
    Text(s).font(.system(size: 14)).foregroundStyle(.secondary).frame(maxWidth: .infinity).listRowBackground(Color.clear)
  }
}
