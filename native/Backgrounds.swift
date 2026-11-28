import SwiftUI
import UIKit
import CryptoKit

// 卡片背景 (docs/card-backgrounds.md): a wallet card can wear a picture instead of its colour.
// identity.bg = an official picture shipped in the bundle (backgrounds/<id>.jpg, copied there by ios.yml);
// identity.pic = the user's own picture by content id, kept in Application Support/pics/<id>.jpg.
// The colour stays for the map, glow and shadows; only the big wallet card face changes.

struct OfficialBackground: Identifiable, Hashable {
  let id: String     // never reused: synced data keeps pointing at it
  let name: String
  var url: URL { Bundle.main.bundleURL.appendingPathComponent("backgrounds/\(id).jpg") }
}

enum OfficialBackgrounds {
  /// same ids, order and names as BGS in app.js and Backgrounds.kt. Empty for now; the pictures come later.
  static let all: [OfficialBackground] = []
  static func find(_ id: String?) -> OfficialBackground? { id.flatMap { i in all.first { $0.id == i } } }
}

/// The own-picture store and the decoded-image cache for every card face.
@MainActor
@Observable
final class CardPics {
  static let shared = CardPics()
  /// bumped when a picture arrives (picked, or downloaded by sync): cards that read it draw again
  private(set) var rev = 0
  @ObservationIgnored private var cache: [String: UIImage] = [:]
  @ObservationIgnored private var missing: Set<String> = []   // so a card with a picture from another device doesn't hit the disk every frame

  nonisolated static var dir: URL {
    FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("pics", isDirectory: true)
  }
  nonisolated static func file(_ id: String) -> URL { dir.appendingPathComponent(id + ".jpg") }
  /// ids are hex only; anything else (a hand-edited sync file) never becomes a path
  nonisolated static func validID(_ id: String) -> Bool { id.count == 32 && id.allSatisfy { $0.isHexDigit && !$0.isUppercase } }
  nonisolated static func exists(_ id: String) -> Bool { validID(id) && FileManager.default.fileExists(atPath: file(id).path) }

  /// what a card shows: its own picture if the file is on this device, else the official one this build knows, else nil (the colour)
  func image(for i: Identity) -> UIImage? {
    _ = rev
    if let p = i.pic, let im = own(p) { return im }
    if let b = OfficialBackgrounds.find(i.bg) { return load("bg:" + b.id, b.url) }
    return nil
  }
  func own(_ id: String) -> UIImage? {
    _ = rev
    guard CardPics.validID(id) else { return nil }
    return load(id, CardPics.file(id))
  }
  func official(_ b: OfficialBackground) -> UIImage? { load("bg:" + b.id, b.url) }
  private func load(_ key: String, _ url: URL) -> UIImage? {
    if let im = cache[key] { return im }
    if missing.contains(key) { return nil }
    // decoded now, once, so the coverflow never decodes a JPEG mid-swipe
    guard let im = UIImage(contentsOfFile: url.path)?.preparingForDisplay() ?? UIImage(contentsOfFile: url.path) else { missing.insert(key); return nil }
    cache[key] = im
    return im
  }

  /// stores processed JPEG bytes under their content id; nil when the disk refused them
  @discardableResult func store(_ jpeg: Data) -> String? {
    let id = CardPics.id(of: jpeg)
    do {
      try FileManager.default.createDirectory(at: CardPics.dir, withIntermediateDirectories: true)
      if !CardPics.exists(id) { try jpeg.write(to: CardPics.file(id), options: .atomic) }
    } catch { return nil }
    missing.remove(id); cache[id] = nil
    rev &+= 1
    return id
  }

  /// on launch: files no card references any more go (not at delete time, so 撤销 still brings the picture back)
  nonisolated static func prune(keep: Set<String>) {
    let fm = FileManager.default
    guard let names = try? fm.contentsOfDirectory(atPath: dir.path) else { return }
    for n in names where n.hasSuffix(".jpg") && !keep.contains(String(n.dropLast(4))) { try? fm.removeItem(at: dir.appendingPathComponent(n)) }
  }

  nonisolated static func id(of jpeg: Data) -> String {
    SHA256.hash(data: jpeg).map { String(format: "%02x", $0) }.joined().prefix(32).lowercased()
  }

  /// any picture -> center-cropped to the card ratio (1.586:1), 1080 px wide, JPEG 0.82. Off the main thread.
  nonisolated static func process(_ data: Data) async -> Data? {
    await Task.detached(priority: .userInitiated) { () -> Data? in
      guard let src = UIImage(data: data) else { return nil }
      let w = src.size.width, h = src.size.height   // already in display orientation
      guard w > 0, h > 0 else { return nil }
      let R: CGFloat = 1.586, W: CGFloat = 1080, H = (W / R).rounded()
      var sw = w, sh = w / R
      if sh > h { sh = h; sw = h * R }
      let scale = W / sw
      let fmt = UIGraphicsImageRendererFormat()
      fmt.scale = 1; fmt.opaque = true
      let out = UIGraphicsImageRenderer(size: CGSize(width: W, height: H), format: fmt).image { _ in
        src.draw(in: CGRect(x: -(w - sw) / 2 * scale, y: -(h - sh) / 2 * scale, width: w * scale, height: h * scale))
      }
      return out.jpegData(compressionQuality: 0.82)
    }.value
  }
}

/// darkens the top and bottom of a picture card where its white text sits (same stops as styles.css .has-bg)
struct CardScrim: View {
  var body: some View {
    LinearGradient(stops: [
      .init(color: .black.opacity(0.38), location: 0), .init(color: .clear, location: 0.38),
      .init(color: .clear, location: 0.48), .init(color: .black.opacity(0.55), location: 1),
    ], startPoint: .top, endPoint: .bottom)
  }
}
