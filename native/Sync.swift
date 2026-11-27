import Foundation
import CryptoKit
import CommonCrypto
import SwiftUI

// Same encrypted file as the web app: PBKDF2-SHA256 (310k) -> AES-GCM, stored in a private gist (Gitee or GitHub)
// or, apps only, on a WebDAV server (坚果云 etc.) as <server>/AccountAtlas/atlas.enc.json.
// WebDAV: provider "webdav", url = server folder (ends with /), user = account, token = 应用密码; gistId stays nil.
// url / user are optional so configs saved by older builds still decode.
struct SyncConf: Codable {
  var provider: String?
  var token: String
  var salt: String
  var key: String
  var gistId: String?
  var at: Double?
  var url: String? = nil
  var user: String? = nil
}

enum SyncError: LocalizedError {
  case msg(String)
  var errorDescription: String? { if case .msg(let s) = self { return s }; return nil }
}

@MainActor
final class Sync: ObservableObject {
  static let FILE = "atlas.enc.json"
  static let DESC = "账号星图 · 加密数据（只有同步密码能解开）"
  @Published var state = "off"     // off | idle | syncing | error
  @Published var err = ""
  @Published var at: Double = 0
  private(set) var conf: SyncConf?
  private var key: SymmetricKey?
  private var busy = false, again = false
  private var timer: DispatchWorkItem?
  weak var store: Store?

  var on: Bool { conf != nil }
  var provider: String { conf?.provider ?? "github" }
  /// where the file lives, for the status line: 「存放在 …」
  var place: String {
    switch provider {
    case "gitee": return "Gitee 私有 Gist"
    case "webdav":
      let h = Sync.davHost(conf?.url)
      return h.contains("jianguoyun") ? "坚果云 WebDAV" : "WebDAV · " + h
    default: return "GitHub 私有 Gist"
    }
  }

  init() {
    if let s = UserDefaults.standard.string(forKey: "atlas.sync"), let d = s.data(using: .utf8), let c = try? JSONDecoder().decode(SyncConf.self, from: d) {
      conf = c; at = c.at ?? 0; state = "idle"
      if let k = Data(base64Encoded: c.key) { key = SymmetricKey(data: k) } else { state = "error"; err = "本机的同步信息损坏，请重新开启" }
    }
  }
  private func persistConf() {
    if let c = conf, let d = try? JSONEncoder().encode(c) { UserDefaults.standard.set(String(data: d, encoding: .utf8), forKey: "atlas.sync") }
    else { UserDefaults.standard.removeObject(forKey: "atlas.sync") }
  }

  // MARK: crypto
  nonisolated static func derive(_ pass: String, salt: Data) -> SymmetricKey {
    var out = [UInt8](repeating: 0, count: 32)
    let pw = Array(pass.utf8)
    let saltBytes = [UInt8](salt)
    pw.withUnsafeBufferPointer { p in
      p.baseAddress!.withMemoryRebound(to: Int8.self, capacity: max(pw.count, 1)) { cp in
        _ = CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), cp, pw.count, saltBytes, saltBytes.count,
                                 CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 310_000, &out, 32)
      }
    }
    return SymmetricKey(data: out)
  }
  private func seal(_ d: AtlasData, key k: SymmetricKey, salt: String) throws -> String {
    let plain = try JSONEncoder().encode(d)
    let box = try AES.GCM.seal(plain, using: k)
    let iv = box.nonce.withUnsafeBytes { Data($0) }
    let ct = box.ciphertext + box.tag
    let env: [String: Any] = ["v": 1, "salt": salt, "iv": iv.base64EncodedString(), "ct": ct.base64EncodedString(), "at": nowMS()]
    return String(data: try JSONSerialization.data(withJSONObject: env), encoding: .utf8) ?? ""
  }
  private static func open(_ text: String, key k: SymmetricKey) throws -> AtlasData {
    guard let o = try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any],
          let ivs = o["iv"] as? String, let cts = o["ct"] as? String,
          let iv = Data(base64Encoded: ivs), let ct = Data(base64Encoded: cts), ct.count > 16 else { throw SyncError.msg("同步文件格式不对") }
    let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: ct.prefix(ct.count - 16), tag: ct.suffix(16))
    return try JSONDecoder().decode(AtlasData.self, from: try AES.GCM.open(box, using: k))
  }

  // MARK: api
  private func api(_ path: String, method: String = "GET", body: [String: Any]? = nil, token: String, prov: String) async throws -> Any {
    let gitee = prov == "gitee"
    var s = (gitee ? "https://gitee.com/api/v5" : "https://api.github.com") + path
    if gitee && method == "GET" { s += (s.contains("?") ? "&" : "?") + "access_token=" + (token.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? token) }
    var r = URLRequest(url: URL(string: s)!)
    r.httpMethod = method; r.cachePolicy = .reloadIgnoringLocalCacheData; r.timeoutInterval = 20
    if gitee {
      if method != "GET" {
        var b = body ?? [:]; b["access_token"] = token
        r.httpBody = try JSONSerialization.data(withJSONObject: b); r.setValue("application/json", forHTTPHeaderField: "Content-Type")
      }
    } else {
      r.setValue("Bearer " + token, forHTTPHeaderField: "Authorization")
      r.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
      if let body { r.httpBody = try JSONSerialization.data(withJSONObject: body); r.setValue("application/json", forHTTPHeaderField: "Content-Type") }
    }
    let name = gitee ? "Gitee" : "GitHub"
    let (data, resp): (Data, URLResponse)
    do { (data, resp) = try await URLSession.shared.data(for: r) }
    catch { throw SyncError.msg("连不上 \(name)" + (gitee ? "" : "，国内网络建议改用 Gitee")) }
    let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
    if code == 401 { throw SyncError.msg("令牌无效或已过期") }
    if code == 403 || code == 404 { throw SyncError.msg(gitee ? "令牌没有 gists 权限" : "令牌没有 gist 权限") }
    if !(200..<300).contains(code) { throw SyncError.msg("\(name) 返回 \(code)") }
    return try JSONSerialization.jsonObject(with: data)
  }
  private func readGist(_ id: String, token: String, prov: String) async throws -> String? {
    guard let g = try await api("/gists/" + id, token: token, prov: prov) as? [String: Any],
          let files = g["files"] as? [String: Any], let f = files[Sync.FILE] as? [String: Any] else { return nil }
    if let c = f["content"] as? String, (f["truncated"] as? Bool) != true { return c }
    if let raw = f["raw_url"] as? String, let u = URL(string: raw) { return String(data: try await URLSession.shared.data(from: u).0, encoding: .utf8) }
    return nil
  }

  // MARK: WebDAV
  // GET / PUT with HTTP Basic auth; a strong ETag goes back as If-Match so two devices can't overwrite each other.
  static let DAV_DIR = "AccountAtlas/"
  static let DAV_JIANGUOYUN = "https://dav.jianguoyun.com/dav/"
  /// 服务器地址 shortcuts on the WebDAV form, same order and wording as Sync.kt. A template's placeholder
  /// (NAS地址 / 你的域名 / 用户名 / 节点名 / 地址) is for the user to replace; 账号 and 密码 are never prefilled.
  struct DavPreset: Hashable { let id: String; let name: String; let url: String; let template: Bool; let hint: String }
  static let DAV_PRESETS: [DavPreset] = [
    DavPreset(id: "jianguoyun", name: "坚果云", url: DAV_JIANGUOYUN, template: false, hint: ""),
    DavPreset(id: "synology", name: "群晖 NAS", url: "https://NAS地址:5006/", template: true, hint: "在群晖套件中心安装 WebDAV Server 并开启 HTTPS（默认 5006 端口），账号密码用 DSM 账号；局域网也可以用 http://NAS地址:5005/。"),
    DavPreset(id: "nextcloud", name: "Nextcloud", url: "https://你的域名/remote.php/dav/files/用户名/", template: true, hint: "把「你的域名」「用户名」换成自己的；密码建议用 Nextcloud 设置 › 安全 里生成的应用密码。"),
    DavPreset(id: "alist", name: "AList", url: "http://地址:5244/dav/", template: true, hint: "在 /dav/ 后面接上 AList 里挂载的存储路径；AList 用户权限里要打开 WebDAV 读取和 WebDAV 管理。"),
    DavPreset(id: "infinicloud", name: "InfiniCLOUD", url: "https://节点名.teracloud.jp/dav/", template: true, hint: "完整地址在 InfiniCLOUD 我的页面 › 外部应用连接 里查看；账号填连接 ID，密码填应用密码。"),
    DavPreset(id: "koofr", name: "Koofr", url: "https://app.koofr.net/dav/Koofr", template: false, hint: "账号填 Koofr 登录邮箱；密码要用 Koofr 网页版 偏好设置 › 密码 里生成的应用密码。"),
    DavPreset(id: "pcloud", name: "pCloud", url: "https://webdav.pcloud.com", template: false, hint: "账号密码就是 pCloud 登录邮箱和密码；欧洲区账号把地址改成 https://ewebdav.pcloud.com；开了两步验证的话，首次连接要在邮件里确认。"),
    DavPreset(id: "custom", name: "自定义", url: "", template: false, hint: ""),
  ]
  private static let DAV_PLACEHOLDERS = ["NAS地址", "你的域名", "用户名", "节点名", "地址"]
  /// http:// only where ATS allows it with NSAllowsLocalNetworking: IP addresses, .local names, single-label names
  static func davLocalHost(_ host: String) -> Bool {
    let h = host.trimmingCharacters(in: CharacterSet(charactersIn: "[]")).lowercased()
    return h.contains(":") || h.range(of: #"^\d{1,3}(\.\d{1,3}){3}$"#, options: .regularExpression) != nil || h.hasSuffix(".local") || !h.contains(".")
  }
  static func davHost(_ url: String?) -> String { URL(string: url ?? "")?.host ?? "WebDAV" }
  private struct Remote { var text: String?; var etag: String? = nil }

  /// normalizes what the user typed: adds https:// when missing, always ends with /
  static func davBase(_ raw: String) throws -> String {
    var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    if s.isEmpty { throw SyncError.msg("请填写 WebDAV 服务器地址") }
    if !s.lowercased().hasPrefix("http://") && !s.lowercased().hasPrefix("https://") { s = "https://" + s }
    if !s.hasSuffix("/") { s += "/" }
    if let ph = DAV_PLACEHOLDERS.first(where: { s.contains($0) }) { throw SyncError.msg("把服务器地址里的「\(ph)」换成你自己的") }
    guard let u = URL(string: s), let host = u.host, !host.isEmpty else { throw SyncError.msg("服务器地址格式不对") }
    if u.scheme?.lowercased() == "http" && !davLocalHost(host) { throw SyncError.msg("外网服务器请用 https://（http 只能用于局域网地址，如 192.168.x.x 或 nas.local）") }
    return s
  }
  private func dav(_ url: String, method: String = "GET", body: Data? = nil, user: String, pass: String, ifMatch: String? = nil) async throws -> (Data, HTTPURLResponse) {
    guard let u = URL(string: url) else { throw SyncError.msg("服务器地址格式不对") }
    var r = URLRequest(url: u)
    r.httpMethod = method; r.cachePolicy = .reloadIgnoringLocalCacheData; r.timeoutInterval = 20
    r.setValue("Basic " + Data((user + ":" + pass).utf8).base64EncodedString(), forHTTPHeaderField: "Authorization")
    r.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
    if let body { r.httpBody = body; r.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type") }
    // weak ETags (W/"…") never match If-Match, so only strong ones are sent back
    if let ifMatch, !ifMatch.hasPrefix("W/") { r.setValue(ifMatch, forHTTPHeaderField: "If-Match") }
    do {
      let (data, resp) = try await URLSession.shared.data(for: r)
      guard let h = resp as? HTTPURLResponse else { throw SyncError.msg("连不上 WebDAV 服务器，检查服务器地址和网络") }
      return (data, h)
    } catch let e as SyncError { throw e }
    catch let e as URLError where e.code == .appTransportSecurityRequiresSecureConnection {
      throw SyncError.msg("外网服务器请用 https://（http 只能用于局域网地址，如 192.168.x.x 或 nas.local）")
    }
    catch { throw SyncError.msg("连不上 WebDAV 服务器，检查服务器地址和网络") }
  }
  private static func davFail(_ code: Int, _ base: String) -> SyncError {
    switch code {
    case 401:
      let h = davHost(base)
      return .msg(h.contains("jianguoyun") ? "账号或应用密码不对（坚果云要用第三方应用密码，不是登录密码）" : "账号或密码不对（\(h)；有的服务要用应用密码，不是登录密码）")
    case 403: return .msg("WebDAV 拒绝访问（没有权限，或请求太频繁，稍后再试）")
    case 404, 409: return .msg("服务器地址不对，找不到这个目录")
    case 507: return .msg("WebDAV 空间已满")
    default: return .msg("WebDAV 返回 \(code)")
    }
  }
  /// nil text = no file yet
  private func davRead(_ base: String, user: String, pass: String) async throws -> Remote {
    let (data, h) = try await dav(base + Sync.DAV_DIR + Sync.FILE, user: user, pass: pass)
    // 坚果云 answers 409 (not 404) when the AccountAtlas folder itself doesn't exist yet
    if h.statusCode == 404 || h.statusCode == 409 { return Remote(text: nil) }
    if !(200..<300).contains(h.statusCode) { throw Sync.davFail(h.statusCode, base) }
    return Remote(text: String(decoding: data, as: UTF8.self), etag: h.value(forHTTPHeaderField: "ETag"))
  }
  /// MKCOL AccountAtlas/; 405 = it already exists
  private func davMkdir(_ base: String, user: String, pass: String) async throws {
    let (_, h) = try await dav(base + Sync.DAV_DIR, method: "MKCOL", user: user, pass: pass)
    if !(200..<300).contains(h.statusCode) && h.statusCode != 405 { throw Sync.davFail(h.statusCode, base) }
  }
  /// false = 412, someone else wrote in between (read, merge and try again)
  private func davWrite(_ base: String, user: String, pass: String, content: String, etag: String?, mkdir: Bool = true) async throws -> Bool {
    let (_, h) = try await dav(base + Sync.DAV_DIR + Sync.FILE, method: "PUT", body: Data(content.utf8), user: user, pass: pass, ifMatch: etag)
    if h.statusCode == 412 { return false }
    // folder missing (deleted on the server, or first write): create it once and retry
    if (h.statusCode == 404 || h.statusCode == 409) && mkdir {
      try await davMkdir(base, user: user, pass: pass)
      return try await davWrite(base, user: user, pass: pass, content: content, etag: etag, mkdir: false)
    }
    if !(200..<300).contains(h.statusCode) { throw Sync.davFail(h.statusCode, base) }
    return true
  }

  private func readRemote(_ c: SyncConf) async throws -> Remote {
    if c.provider == "webdav" { return try await davRead(c.url ?? "", user: c.user ?? "", pass: c.token) }
    return Remote(text: try await readGist(c.gistId ?? "", token: c.token, prov: c.provider ?? "github"))
  }
  private func writeRemote(_ c: SyncConf, content: String, etag: String?) async throws -> Bool {
    if c.provider == "webdav" { return try await davWrite(c.url ?? "", user: c.user ?? "", pass: c.token, content: content, etag: etag) }
    _ = try await api("/gists/" + (c.gistId ?? ""), method: "PATCH", body: ["description": Sync.DESC, "files": [Sync.FILE: ["content": content]]], token: c.token, prov: c.provider ?? "github")
    return true
  }

  // MARK: merge (per item, newest wins, tombstones)
  static func merge(_ a: AtlasData, _ b: AtlasData) -> AtlasData {
    var del = a.deleted
    for (id, t) in b.deleted { del[id] = max(del[id] ?? 0, t) }
    func pick<T: Identifiable>(_ xs: [T], _ ys: [T], _ up: (T) -> Double) -> [T] where T.ID == String {
      var order: [String] = [], map: [String: T] = [:]
      for x in xs + ys {
        if let c = map[x.id] { if up(x) > up(c) { map[x.id] = x } } else { map[x.id] = x; order.append(x.id) }
      }
      return order.compactMap { map[$0] }.filter { !((del[$0.id] ?? -1) >= up($0)) }
    }
    var out = AtlasData()
    out.identities = pick(a.identities, b.identities) { $0.updatedAt ?? 0 }
    out.accounts = pick(a.accounts, b.accounts) { $0.updatedAt ?? 0 }
    for id in del.keys where out.identities.contains(where: { $0.id == id }) || out.accounts.contains(where: { $0.id == id }) { del[id] = nil }
    out.identities = out.identities.enumerated().sorted { ($0.element.order ?? 1e9, Double($0.offset)) < ($1.element.order ?? 1e9, Double($1.offset)) }.map(\.element)
    let ids = Set(out.identities.map(\.id))
    for k in out.accounts.indices { out.accounts[k].identityIds = out.accounts[k].identityIds.filter { ids.contains($0) } }
    let old = nowMS() - 90 * 86_400_000
    del = del.filter { $0.value >= old }
    out.deleted = del
    // the category list is one value: the side that changed it last wins (a tie takes the larger text, so
    // both sides agree). Categories still used by an account but missing from it are shown at the end.
    let ca = a.categoriesAt ?? 0, cb = b.categoriesAt ?? 0
    let w = ca > cb ? a : cb > ca ? b : (Sync.joined(a.categories).lexicographicallyPrecedes(Sync.joined(b.categories)) ? b : a)
    out.categories = w.categories; out.categoriesAt = w.categoriesAt
    return out
  }
  /// compared as UTF-16, the way the web and Android clients compare strings
  private static func joined(_ c: [String]?) -> [UInt16] { Array((c ?? []).joined(separator: "\n").utf16) }
  private static func sig(_ d: AtlasData) -> String {
    let e = JSONEncoder(); e.outputFormatting = .sortedKeys
    let a = (try? e.encode(d.identities)) ?? Data(), b = (try? e.encode(d.accounts)) ?? Data(), c = (try? e.encode(d.deleted)) ?? Data()
    let k = (try? e.encode(d.categories ?? [])) ?? Data()
    return String(decoding: a + b + c + k, as: UTF8.self) + "|\(d.categoriesAt ?? 0)"
  }

  // MARK: run
  func schedule() {
    guard conf != nil else { return }
    timer?.cancel()
    let w = DispatchWorkItem { [weak self] in Task { await self?.run() } }
    timer = w
    DispatchQueue.main.asyncAfter(deadline: .now() + 1.2, execute: w)
  }

  func run() async {
    guard let c = conf, let k = key, let store else { return }
    if c.provider == "webdav" { guard c.url != nil, c.user != nil else { return } } else if c.gistId == nil { return }
    if busy { again = true; return }
    busy = true; state = "syncing"
    do {
      // read-merge-write; on WebDAV a 412 (another device wrote in between) reads again, the 3rd try writes without If-Match
      var tries = 0
      while true {
        let r = try await readRemote(c)
        let text = r.text
        let remote = try text.map { try Sync.open($0, key: k) } ?? AtlasData()
        // during the 操作演示 the screen shows sandbox data: sync only ever sees (and updates) the real data
        let local = store.realData
        let merged = Sync.merge(local, remote)
        if Sync.sig(merged) != Sync.sig(local) { store.adopt(merged) }
        if text == nil || Sync.sig(merged) != Sync.sig(remote) {
          let content = try seal(merged, key: k, salt: c.salt)
          let ok = try await writeRemote(c, content: content, etag: tries < 2 ? r.etag : nil)
          if !ok { tries += 1; continue }
        }
        break
      }
      state = "idle"; err = ""; at = nowMS(); conf?.at = at; persistConf()
    } catch {
      state = "error"; err = (error as? SyncError)?.errorDescription ?? (error is CryptoKitError ? "同步密码不对" : "同步失败")
    }
    busy = false
    if again { again = false; await run() }
  }

  /// returns true when existing data was found in the cloud
  /// WebDAV: `raw` is the 应用密码, `url` / `user` the server and account
  func enable(token raw: String, pass: String, prov: String, url: String? = nil, user: String? = nil) async throws -> Bool {
    guard let store else { return false }
    let token = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    let isDav = prov == "webdav"
    let base = try isDav ? Sync.davBase(url ?? "") : ""
    let u = (user ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    if isDav && u.isEmpty { throw SyncError.msg("请填写 WebDAV 账号") }
    var gid: String? = nil
    var existing: String? = nil
    if isDav {
      existing = try await davRead(base, user: u, pass: token).text
    } else {
      let list = (try await api("/gists?per_page=100", token: token, prov: prov) as? [[String: Any]]) ?? []
      if let g = list.first(where: { (($0["files"] as? [String: Any])?[Sync.FILE]) != nil }),
         let id = (g["id"] as? String) ?? (g["id"] as? Int).map({ String($0) }) {
        gid = id
        guard let t = try await readGist(id, token: token, prov: prov) else { throw SyncError.msg("云端的同步文件损坏") }
        existing = t
      }
    }
    var salt: String, k: SymmetricKey
    var foundRemote = false
    if let text = existing {
      guard let env = (try? JSONSerialization.jsonObject(with: Data(text.utf8))) as? [String: Any], let s = env["salt"] as? String,
            let sd = Data(base64Encoded: s) else { throw SyncError.msg("云端的同步文件损坏") }
      salt = s
      k = await Task.detached { Sync.derive(pass, salt: sd) }.value
      do { _ = try Sync.open(text, key: k) } catch { throw SyncError.msg("同步密码不对（和第一次设置的不一样）") }
      foundRemote = true
    } else {
      var b = [UInt8](repeating: 0, count: 16); _ = SecRandomCopyBytes(kSecRandomDefault, 16, &b)
      salt = Data(b).base64EncodedString()
      let sd = Data(b)
      k = await Task.detached { Sync.derive(pass, salt: sd) }.value
    }
    // sample data stays on this device: only the marked items are taken out (no tombstones) before the first merge
    var real = store.realData
    let demo = Set(real.identities.filter(\.isDemo).map(\.id) + real.accounts.filter { $0.demo == true }.map(\.id))
    if !demo.isEmpty { Store.strip(&real, demo); store.adopt(real) }
    if isDav {
      // first device on this server: create AccountAtlas/ and put the file there now, so a bad address shows up here
      if existing == nil {
        try await davMkdir(base, user: u, pass: token)
        _ = try await davWrite(base, user: u, pass: token, content: try seal(store.realData, key: k, salt: salt), etag: nil)
      }
    } else if gid == nil {
      let content = try seal(store.realData, key: k, salt: salt)
      let made = try await api("/gists", method: "POST", body: ["description": Sync.DESC, "public": false, "files": [Sync.FILE: ["content": content]]], token: token, prov: prov) as? [String: Any]
      gid = (made?["id"] as? String) ?? (made?["id"] as? Int).map({ String($0) })
    }
    let keyData = k.withUnsafeBytes { Data($0) }
    conf = SyncConf(provider: prov, token: token, salt: salt, key: keyData.base64EncodedString(), gistId: gid, at: 0, url: isDav ? base : nil, user: isDav ? u : nil)
    key = k; persistConf(); state = "idle"
    await run()
    return foundRemote
  }

  func disable() {
    conf = nil; key = nil; state = "off"; err = ""; persistConf()
  }
}
