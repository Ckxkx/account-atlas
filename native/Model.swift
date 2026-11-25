import Foundation
import SwiftUI
import UIKit
import Observation
import UniformTypeIdentifiers

// MARK: - data (same JSON shape as the web app, so backups and sync interoperate)

/// Any JSON value: fields this build doesn't know (written by a newer web/Android client) are kept
/// as-is and written back, so a sync or backup round trip never drops them.
indirect enum JSONValue: Codable, Hashable {
  case null, bool(Bool), number(Double), string(String), array([JSONValue]), object([String: JSONValue])
  init(from d: Decoder) throws {
    let c = try d.singleValueContainer()
    if c.decodeNil() { self = .null }
    else if let b = try? c.decode(Bool.self) { self = .bool(b) }
    else if let n = try? c.decode(Double.self) { self = .number(n) }
    else if let s = try? c.decode(String.self) { self = .string(s) }
    else if let a = try? c.decode([JSONValue].self) { self = .array(a) }
    else { self = .object(try c.decode([String: JSONValue].self)) }
  }
  func encode(to e: Encoder) throws {
    var c = e.singleValueContainer()
    switch self {
    case .null: try c.encodeNil()
    case .bool(let b): try c.encode(b)
    case .number(let n): try c.encode(n)
    case .string(let s): try c.encode(s)
    case .array(let a): try c.encode(a)
    case .object(let o): try c.encode(o)
    }
  }
}

struct AnyKey: CodingKey {
  var stringValue: String
  var intValue: Int? { nil }
  init(_ s: String) { stringValue = s }
  init?(stringValue: String) { self.stringValue = stringValue }
  init?(intValue: Int) { return nil }
}

/// reads every key of a JSON object that isn't in `known`
func unknownFields(_ d: Decoder, known: Set<String>) -> [String: JSONValue] {
  guard let c = try? d.container(keyedBy: AnyKey.self) else { return [:] }
  var out: [String: JSONValue] = [:]
  for k in c.allKeys where !known.contains(k.stringValue) {
    if let v = try? c.decode(JSONValue.self, forKey: k) { out[k.stringValue] = v }
  }
  return out
}

struct Identity: Codable, Identifiable, Hashable {
  var id: String
  var type: String            // "email" | "phone" | "login"
  var value: String
  var label: String = ""
  var color: String
  var notes: String = ""
  var updatedAt: Double? = nil
  var order: Double? = nil
  /// phone only: an eSIM (no physical card). Written only when true; the key is omitted otherwise.
  var esim: Bool? = nil
  /// login only: "wechat" | "qq" ("apple" only in data older than 2.4; converted on load)
  var provider: String? = nil
  /// email / phone: this address or number is also an Apple ID (2.4). Written only when true.
  var apple: Bool? = nil
  /// login only: id of the email / phone identity it is bound to (the first one). Omitted when empty; a dangling id counts as none.
  var via: String? = nil
  /// 2.5, login only: every binding when there are two or more (via stays the first, for older clients).
  /// Read as the union of via and vias. Omitted when there is at most one.
  var vias: [String]? = nil
  /// 2.5: part of the sample data (「清除示例数据」 removes exactly these). Written only when true.
  var demo: Bool? = nil
  /// fields from newer clients, written back untouched
  var extra: [String: JSONValue] = [:]

  static let knownKeys: Set<String> = ["id", "type", "value", "label", "color", "notes", "updatedAt", "order", "esim", "provider", "via", "apple", "vias", "demo"]

  init(id: String = newID(), type: String, value: String, label: String = "", color: String, notes: String = "", esim: Bool = false, provider: String? = nil, via: String? = nil, apple: Bool = false) {
    self.id = id; self.type = type; self.value = value; self.label = label; self.color = color; self.notes = notes
    self.esim = esim ? true : nil
    self.apple = apple ? true : nil
    self.provider = (provider?.isEmpty ?? true) ? nil : provider
    self.via = (via?.isEmpty ?? true) ? nil : via
  }
  init(from d: Decoder) throws {
    let c = try d.container(keyedBy: AnyKey.self)
    id = try c.decode(String.self, forKey: AnyKey("id"))
    type = (try? c.decode(String.self, forKey: AnyKey("type"))) ?? "email"
    value = (try? c.decode(String.self, forKey: AnyKey("value"))) ?? ""
    label = (try? c.decode(String.self, forKey: AnyKey("label"))) ?? ""
    color = (try? c.decode(String.self, forKey: AnyKey("color"))) ?? "#0a84ff"
    notes = (try? c.decode(String.self, forKey: AnyKey("notes"))) ?? ""
    updatedAt = try? c.decode(Double.self, forKey: AnyKey("updatedAt"))
    order = try? c.decode(Double.self, forKey: AnyKey("order"))
    esim = (try? c.decode(Bool.self, forKey: AnyKey("esim"))) == true ? true : nil
    apple = (try? c.decode(Bool.self, forKey: AnyKey("apple"))) == true ? true : nil
    let p = try? c.decode(String.self, forKey: AnyKey("provider"))
    provider = (p?.isEmpty ?? true) ? nil : p
    let v = try? c.decode(String.self, forKey: AnyKey("via"))
    via = (v?.isEmpty ?? true) ? nil : v
    let vs = (try? c.decode([String].self, forKey: AnyKey("vias")))?.filter { !$0.isEmpty }
    vias = (vs?.isEmpty ?? true) ? nil : vs
    demo = (try? c.decode(Bool.self, forKey: AnyKey("demo"))) == true ? true : nil
    extra = unknownFields(d, known: Identity.knownKeys)
  }
  func encode(to e: Encoder) throws {
    var c = e.container(keyedBy: AnyKey.self)
    for (k, v) in extra where !Identity.knownKeys.contains(k) { try c.encode(v, forKey: AnyKey(k)) }
    try c.encode(id, forKey: AnyKey("id"))
    try c.encode(type, forKey: AnyKey("type"))
    try c.encode(value, forKey: AnyKey("value"))
    try c.encode(label, forKey: AnyKey("label"))
    try c.encode(color, forKey: AnyKey("color"))
    try c.encode(notes, forKey: AnyKey("notes"))
    try c.encodeIfPresent(updatedAt, forKey: AnyKey("updatedAt"))
    try c.encodeIfPresent(order, forKey: AnyKey("order"))
    if esim == true { try c.encode(true, forKey: AnyKey("esim")) }
    if apple == true { try c.encode(true, forKey: AnyKey("apple")) }
    if let provider, !provider.isEmpty { try c.encode(provider, forKey: AnyKey("provider")) }
    if let via, !via.isEmpty { try c.encode(via, forKey: AnyKey("via")) }
    if let vias, vias.count > 1 { try c.encode(vias, forKey: AnyKey("vias")) }
    if demo == true { try c.encode(true, forKey: AnyKey("demo")) }
  }
  /// 2.5: every email / phone id this 微信 / QQ is bound to, via first (union of via and vias, no repeats)
  var bindIDs: [String] {
    var out: [String] = []
    if let via, !via.isEmpty { out.append(via) }
    for x in vias ?? [] where !x.isEmpty && !out.contains(x) { out.append(x) }
    return out
  }
  /// bound to exactly these ids: via = the first, vias only when there are two or more
  mutating func setBinds(_ ids: [String]) {
    var u: [String] = []
    for x in ids where !x.isEmpty && x != id && !u.contains(x) { u.append(x) }
    via = u.first
    vias = u.count > 1 ? u : nil
  }
  var isDemo: Bool { demo == true }
  var isPhone: Bool { type == "phone" }
  var isESim: Bool { isPhone && esim == true }
  var isLogin: Bool { type == "login" }
  /// a pre-2.4 Apple login card (converted to its email / phone on load)
  var isApple: Bool { isLogin && provider == "apple" }
  /// 2.4: an email / phone that is also an Apple ID
  var isAppleID: Bool { !isLogin && apple == true }
  var isQQ: Bool { isLogin && provider == "qq" }
  /// "wechat" | "qq" | "apple" for a login card (anything unknown reads as 微信)
  var loginProvider: String { isApple ? "apple" : isQQ ? "qq" : "wechat" }
  /// 微信 / QQ (login cards), 手机号, 邮箱
  var kindName: String { isLogin ? (isApple ? "Apple 账户" : isQQ ? "QQ" : "微信") : isPhone ? "手机号" : "邮箱" }
}

// MARK: - 2.4: one identity per email / phone; Apple ID is a flag on it (mirrors app.js / Model.kt)

func normEmail(_ v: String) -> String { v.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
func normPhone(_ v: String) -> String {
  var d = v.filter { $0.isASCII && $0.isNumber }
  if d.hasPrefix("0086") { d.removeFirst(4) } else if d.hasPrefix("86") && d.count == 13 { d.removeFirst(2) }
  return d
}
/// the key two identities share when they are the same thing; nil = never merged
func idKey(type: String, provider: String?, value: String) -> String? {
  let v = value.trimmingCharacters(in: .whitespacesAndNewlines)
  if v.isEmpty { return nil }
  switch type {
  case "email": return "e:" + normEmail(v)
  case "phone": let d = normPhone(v); return d.isEmpty ? nil : "p:" + d
  case "login":
    if provider == "apple" { return nil }
    if provider == "qq" { return "q:" + (v.contains("@") ? normEmail(v) : v.filter { $0.isASCII && $0.isNumber }) }
    return "w:" + v.lowercased()
  default: return nil
  }
}
func idKey(_ i: Identity) -> String? { idKey(type: i.type, provider: i.provider, value: i.value) }

/// Silent, lossless fix-ups (ids kept): an Apple login card becomes the email / phone it is, flagged
/// apple (its `via` stays in the JSON, unused); a QQ card whose QQ is an email you already have points
/// at that email. Returns true if anything changed.
@discardableResult func autoFix24(_ d: inout AtlasData) -> Bool {
  var changed = false
  for k in d.identities.indices where d.identities[k].type == "login" && d.identities[k].provider == "apple" {
    d.identities[k].type = d.identities[k].value.contains("@") ? "email" : "phone"
    d.identities[k].provider = nil
    d.identities[k].apple = true
    changed = true
  }
  for k in d.identities.indices {
    let i = d.identities[k]
    guard i.type == "login", i.provider == "qq", i.bindIDs.isEmpty, i.value.contains("@") else { continue }
    if let m = d.identities.first(where: { $0.type == "email" && normEmail($0.value) == normEmail(i.value) }) {
      d.identities[k].via = m.id; changed = true
    }
  }
  return changed
}
/// groups of identity ids that are the same email / phone / 微信 / QQ; the first (oldest) is kept
func dupGroups24(_ d: AtlasData) -> [[String]] {
  var order: [String] = [], by: [String: [String]] = [:]
  for i in d.identities {
    guard let k = idKey(i) else { continue }
    if by[k] == nil { order.append(k) }
    by[k, default: []].append(i.id)
  }
  return order.compactMap { by[$0] }.filter { $0.count > 1 }
}
func dupCount24(_ d: AtlasData) -> Int { dupGroups24(d).reduce(0) { $0 + $1.count - 1 } }
/// Merges every group into its first identity: label, notes, flags and unknown fields carry over,
/// every account and binding is repointed. Removed ids become tombstones when the change is stamped.
@discardableResult func mergeDups24(_ d: inout AtlasData) -> Int {
  var to: [String: String] = [:]
  for g in dupGroups24(d) {
    guard let kk = d.identities.firstIndex(where: { $0.id == g[0] }) else { continue }
    for oid in g.dropFirst() {
      guard let o = d.identities.first(where: { $0.id == oid }) else { continue }
      to[oid] = g[0]
      var keep = d.identities[kk]
      if keep.label.isEmpty && !o.label.isEmpty { keep.label = o.label }
      if !o.notes.isEmpty && !keep.notes.contains(o.notes) { keep.notes = keep.notes.isEmpty ? o.notes : keep.notes + "\n" + o.notes }
      if o.apple == true { keep.apple = true }
      if o.esim == true && keep.type == "phone" { keep.esim = true }
      if o.bindIDs.contains(where: { $0 != keep.id && !keep.bindIDs.contains($0) }) { keep.setBinds(keep.bindIDs + o.bindIDs) }
      if keep.demo == true && o.demo != true { keep.demo = nil }   // merged with a real one: real
      if keep.order == nil { keep.order = o.order }
      for (k, v) in o.extra where keep.extra[k] == nil { keep.extra[k] = v }
      d.identities[kk] = keep
    }
  }
  if to.isEmpty { return 0 }
  d.identities.removeAll { to[$0.id] != nil }
  for k in d.identities.indices where d.identities[k].bindIDs.contains(where: { to[$0] != nil }) {
    d.identities[k].setBinds(d.identities[k].bindIDs.map { to[$0] ?? $0 })
  }
  for k in d.accounts.indices {
    var out: [String] = []
    for x in d.accounts[k].identityIds { let y = to[x] ?? x; if !out.contains(y) { out.append(y) } }
    if out != d.accounts[k].identityIds { d.accounts[k].identityIds = out }
  }
  return to.count
}
/// a 微信 / QQ card's binding without the full number: 尾号 1234, or ab•••@qq.com
func bindTag(_ v: Identity) -> String {
  if v.type == "phone" { let d = v.value.filter(\.isNumber); return d.count >= 4 ? "尾号 " + String(d.suffix(4)) : "" }
  let parts = v.value.split(separator: "@", maxSplits: 1).map(String.init)
  return String((parts.first ?? "").prefix(2)) + "•••" + (parts.count > 1 ? "@" + parts[1] : "")
}
/// 2.5 map / picker tag of every binding: the first one, then "+1" for each more
func bindTags(_ binds: [Identity]) -> String {
  guard let f = binds.first else { return "" }
  let t = bindTag(f)
  return t.isEmpty || binds.count < 2 ? t : t + " +\(binds.count - 1)"
}
/// 2.5 level 1: the value with most of it hidden: ab•••@gmail.com, ••• •••• 1234, wx•••, •••45 (mirrors Model.kt)
func maskValue(_ i: Identity) -> String {
  let v = i.value
  if i.type == "email" || (i.isLogin && v.contains("@")) {
    let parts = v.split(separator: "@", maxSplits: 1).map(String.init)
    return String((parts.first ?? "").prefix(2)) + "•••" + (parts.count > 1 ? "@" + parts[1] : "")
  }
  if i.isLogin && !i.isQQ && !i.isApple { return String(v.prefix(2)) + "•••" }
  let digits = v.filter(\.isNumber)
  if digits.count > 7 { return "••• •••• " + String(digits.suffix(4)) }
  return digits.count >= 4 ? "•••" + String(digits.suffix(2)) : "••••"
}
/// 2.5 level 2: a label or username with all but its first character hidden
func maskName(_ s: String) -> String { s.isEmpty ? s : String(s.prefix(1)) + "•••" }

/// what's wrong with a value for this kind ("email" | "phone" | "wechat" | "qq"), or nil when it's fine
func idValueError(_ kind: String, _ value: String) -> String? {
  let v = value.trimmingCharacters(in: .whitespaces)
  let mail = v.range(of: "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", options: .regularExpression) != nil
  switch kind {
  case "email": return mail ? nil : "邮箱地址格式不对"
  case "phone": return v.filter(\.isNumber).count >= 5 ? nil : "手机号至少要 5 位数字"
  case "qq": return loginValueValid(provider: "qq", value: v) ? nil : "QQ 号是 5 到 12 位数字，或填 QQ 邮箱"
  default: return v.isEmpty ? "填一下微信号" : nil
  }
}

let KINDS = ["email", "phone", "login"]
func loginDefaultColor(_ provider: String) -> String { provider == "apple" ? "#1d1d1f" : provider == "qq" ? "#12b7f5" : "#07c160" }
/// 微信号: at least one character. QQ: a 5–12 digit number or an email. Apple ID: an email, or a phone number (≥5 digits).
func loginValueValid(provider: String, value: String) -> Bool {
  let v = value.trimmingCharacters(in: .whitespaces)
  if v.isEmpty { return false }
  if provider == "qq" {
    let d = v.filter { $0 != " " }
    return (d.count >= 5 && d.count <= 12 && d.allSatisfy(\.isNumber)) || v.range(of: "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", options: .regularExpression) != nil
  }
  if provider != "apple" { return true }
  if v.range(of: "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", options: .regularExpression) != nil { return true }
  return v.allSatisfy { $0.isNumber || " +-()".contains($0) } && v.filter(\.isNumber).count >= 5
}

struct Account: Codable, Identifiable, Hashable {
  var id: String
  var name: String
  var domain: String = ""
  var category: String = "其他"
  var identityIds: [String] = []
  var notes: String = ""
  var handle: String? = nil
  var createdAt: Double = nowMS()
  var updatedAt: Double? = nil
  /// custom / App Store icon: a data:image/png|jpeg;base64 URL (square, ≤128 px). nil = preset/favicon.
  var icon: String? = nil
  /// 2.5: part of the sample data. Written only when true.
  var demo: Bool? = nil
  /// fields from newer clients, written back untouched
  var extra: [String: JSONValue] = [:]

  init(id: String = newID(), name: String, domain: String, category: String, identityIds: [String], notes: String = "", handle: String? = nil, createdAt: Double = nowMS(), icon: String? = nil) {
    self.id = id; self.name = name; self.domain = domain; self.category = category; self.identityIds = identityIds; self.notes = notes; self.handle = handle; self.createdAt = createdAt
    self.icon = (icon?.isEmpty ?? true) ? nil : icon
  }
  init(from d: Decoder) throws {
    let c = try d.container(keyedBy: CodingKeys.self)
    id = try c.decode(String.self, forKey: .id)
    name = (try? c.decode(String.self, forKey: .name)) ?? "?"
    domain = (try? c.decode(String.self, forKey: .domain)) ?? ""
    category = (try? c.decode(String.self, forKey: .category)) ?? "其他"
    identityIds = (try? c.decode([String].self, forKey: .identityIds)) ?? []
    notes = (try? c.decode(String.self, forKey: .notes)) ?? ""
    let h = try? c.decode(String.self, forKey: .handle)
    handle = (h?.isEmpty ?? true) ? nil : h
    createdAt = (try? c.decode(Double.self, forKey: .createdAt)) ?? nowMS()
    updatedAt = try? c.decode(Double.self, forKey: .updatedAt)
    let ic = try? c.decode(String.self, forKey: .icon)
    icon = (ic?.isEmpty ?? true) ? nil : ic
    demo = (try? c.decode(Bool.self, forKey: .demo)) == true ? true : nil
    extra = unknownFields(d, known: Set(CodingKeys.allCases.map(\.rawValue)))
  }
  enum CodingKeys: String, CodingKey, CaseIterable { case id, name, domain, category, identityIds, notes, handle, createdAt, updatedAt, icon, demo }
  // written by hand so an empty icon is never written (old clients just see the key missing)
  func encode(to e: Encoder) throws {
    if !extra.isEmpty {
      let known = Set(CodingKeys.allCases.map(\.rawValue))
      var x = e.container(keyedBy: AnyKey.self)
      for (k, v) in extra where !known.contains(k) { try x.encode(v, forKey: AnyKey(k)) }
    }
    var c = e.container(keyedBy: CodingKeys.self)
    try c.encode(id, forKey: .id)
    try c.encode(name, forKey: .name)
    try c.encode(domain, forKey: .domain)
    try c.encode(category, forKey: .category)
    try c.encode(identityIds, forKey: .identityIds)
    try c.encode(notes, forKey: .notes)
    try c.encodeIfPresent(handle, forKey: .handle)
    try c.encode(createdAt, forKey: .createdAt)
    try c.encodeIfPresent(updatedAt, forKey: .updatedAt)
    if let icon, !icon.isEmpty { try c.encode(icon, forKey: .icon) }
    if demo == true { try c.encode(true, forKey: .demo) }
  }
}

struct AtlasData: Codable {
  var identities: [Identity] = []
  var accounts: [Account] = []
  var deleted: [String: Double] = [:]
  var demo: Bool? = nil
  init(identities: [Identity] = [], accounts: [Account] = [], deleted: [String: Double] = [:], demo: Bool? = nil) {
    self.identities = identities; self.accounts = accounts; self.deleted = deleted; self.demo = demo
  }
  init(from d: Decoder) throws {
    let c = try d.container(keyedBy: CodingKeys.self)
    // element by element: one damaged entry is skipped instead of emptying the whole list
    identities = (try? c.decode(Lossy<Identity>.self, forKey: .identities))?.items ?? []
    accounts = (try? c.decode(Lossy<Account>.self, forKey: .accounts))?.items ?? []
    deleted = (try? c.decode([String: Double].self, forKey: .deleted)) ?? [:]
    demo = try? c.decode(Bool.self, forKey: .demo)
  }
}

/// decodes an array, dropping only the elements that fail
struct Lossy<T: Decodable>: Decodable {
  var items: [T] = []
  init(from d: Decoder) throws {
    var c = try d.unkeyedContainer()
    while !c.isAtEnd {
      // a failed decode doesn't move the cursor: step over the element as raw JSON
      if let x = try? c.decode(T.self) { items.append(x) } else if (try? c.decode(JSONValue.self)) == nil { break }
    }
  }
}

func newID() -> String {
  let a = "abcdefghijklmnopqrstuvwxyz0123456789"
  let r = String((0..<8).map { _ in a.randomElement()! })
  let t = String(Int(Date().timeIntervalSince1970 * 1000), radix: 36)
  return r + String(t.suffix(4))
}
func nowMS() -> Double { (Date().timeIntervalSince1970 * 1000).rounded() }

let CATS = ["社交", "购物", "支付金融", "开发", "AI", "娱乐", "游戏", "出行", "工具", "加密", "其他"]

struct Preset: Hashable { let name: String; let domain: String; let category: String; let color: String; var icon: String? = nil; var aliases: String = "" }

/// search match: name or domain contains the (lowercased) query, or one of the aliases starts with it
func siteHit(_ p: Preset, _ q: String) -> Bool {
  p.name.lowercased().contains(q) || p.domain.contains(q) || (!p.aliases.isEmpty && (" " + p.aliases).contains(" " + q))
}

// MARK: - helpers

func cleanDomain(_ d: String) -> String {
  var s = d.trimmingCharacters(in: .whitespaces).lowercased()
  for p in ["https://", "http://"] where s.hasPrefix(p) { s.removeFirst(p.count) }
  if s.hasPrefix("www.") { s.removeFirst(4) }
  return String(s.split(whereSeparator: { "/?#".contains($0) }).first ?? "")
}

func presetFor(name: String, domain: String) -> Preset? { presetMatch(name: name, domain: domain, byName: true) }

/// The built-in app an account is. Exact domain first, then (byName) the name, then a parent domain,
/// the longest one, and only when the names agree too: 王者荣耀 on pvp.qq.com is not QQ, and
/// 企业微信 on work.weixin.qq.com is not 微信.
func presetMatch(name: String, domain: String, byName: Bool) -> Preset? {
  let d = cleanDomain(domain)
  func norm(_ s: String) -> String { s.lowercased().filter { !$0.isWhitespace } }
  let n = norm(name)
  if !d.isEmpty, let p = Presets.all.first(where: { $0.domain == d }) { return p }
  if byName, !n.isEmpty, let p = Presets.all.first(where: { norm($0.name) == n }) { return p }
  guard !d.isEmpty, !n.isEmpty else { return nil }
  return Presets.all.filter { d.hasSuffix("." + $0.domain) && n.contains(norm($0.name)) }.max { $0.domain.count < $1.domain.count }
}

func carrier(_ v: String) -> String {
  var d = v.filter(\.isNumber)
  if d.hasPrefix("0086") { d.removeFirst(4) } else if d.hasPrefix("86") && d.count == 13 { d.removeFirst(2) }
  guard d.count == 11, d.hasPrefix("1") else {
    let t = v.trimmingCharacters(in: .whitespaces)
    if t.hasPrefix("+") { let cc = t.dropFirst().prefix(while: \.isNumber).prefix(3); if !cc.isEmpty && cc != "86" { return "+" + cc } }
    return ""
  }
  let p3 = String(d.prefix(3)), p4 = String(d.prefix(4))
  let mobile: Set<String> = ["134","135","136","137","138","139","147","148","150","151","152","157","158","159","172","178","182","183","184","187","188","195","197","198"]
  let unicom: Set<String> = ["130","131","132","145","146","155","156","166","167","171","175","176","185","186","196"]
  let telecom: Set<String> = ["133","149","153","173","174","177","180","181","189","190","191","193","199"]
  if p4 == "1349" { return "中国电信" }
  if mobile.contains(p3) { return "中国移动" }
  if unicom.contains(p3) { return "中国联通" }
  if telecom.contains(p3) { return "中国电信" }
  if p3 == "192" { return "中国广电" }
  return ""
}

// MARK: - store

struct Toast: Identifiable, Equatable {
  let id = UUID()
  var text: String
  var action: String? = nil
  static func == (a: Toast, b: Toast) -> Bool { a.id == b.id }
}

enum Route: Identifiable, Equatable {
  case identity(String), account(String), newIdentity(String), editIdentity(String), editAccount(String), picker(String?), settings
  /// 2.5: a new 微信 / QQ already bound to this email / phone
  case newBound(String)
  case share
  var id: String {
    switch self {
    case .identity(let s): return "i" + s
    case .account(let s): return "a" + s
    case .newIdentity(let s): return "n" + s
    case .editIdentity(let s): return "ei" + s
    case .editAccount(let s): return "ea" + s
    case .picker(let s): return "p" + (s ?? "")
    case .settings: return "s"
    case .newBound(let s): return "nb" + s
    case .share: return "sh"
    }
  }
}

/// Everything views read that is derived from `data`, built once per change instead of on every
/// body evaluation (sorting with localizedStandardCompare in every card/row was the main cost).
struct AtlasIndex {
  var byIdentity: [String: [Account]] = [:]     // sorted by name
  var identityByID: [String: Identity] = [:]
  var accountByID: [String: Account] = [:]
  var byName: [Account] = []
  var recent: [Account] = []                     // newest 5
  var emails: [Identity] = []
  var phones: [Identity] = []
  var logins: [Identity] = []                    // 微信 / QQ / Apple
  var boundLogins: [String: [Identity]] = [:]    // email/phone id -> login cards bound to it
  var categoryCount: [String: Int] = [:]
  var categories: [String] = []
  var byCategory: [String: [Account]] = [:]      // sorted by name
  init() {}
  init(_ d: AtlasData) {
    byName = d.accounts.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    for i in d.identities {
      identityByID[i.id] = i
      if i.type == "email" { emails.append(i) } else if i.type == "phone" { phones.append(i) } else if i.type == "login" { logins.append(i) }
    }
    for l in logins {
      for v in l.bindIDs {
        if let t = identityByID[v], t.type == "email" || t.type == "phone" { boundLogins[v, default: []].append(l) }
      }
    }
    for a in byName {
      accountByID[a.id] = a
      for id in a.identityIds { byIdentity[id, default: []].append(a) }
      categoryCount[a.category, default: 0] += 1
      byCategory[a.category, default: []].append(a)
    }
    recent = Array(d.accounts.sorted { $0.createdAt > $1.createdAt }.prefix(5))
    let have = Set(categoryCount.keys)
    categories = CATS.filter { have.contains($0) } + have.filter { !CATS.contains($0) }.sorted()
  }
}

@MainActor
@Observable
final class Store {
  // Observation tracks each property separately, so swiping the card deck (activeID) or showing a
  // toast no longer invalidates every view of every tab the way one ObservableObject did.
  var data = AtlasData() { didSet { index = AtlasIndex(data) } }
  private(set) var index = AtlasIndex()
  /// 2.5 level 1: account values are masked everywhere unless turned off (默认隐藏账号, on by default)
  var maskValues: Bool = UserDefaults.standard.object(forKey: "atlas.maskValues") as? Bool ?? true { didSet { UserDefaults.standard.set(maskValues, forKey: "atlas.maskValues"); revealed = [] } }
  /// 2.5 level 2: labels and usernames are hidden too (隐藏用户名, off by default)
  var hideNames: Bool = UserDefaults.standard.bool(forKey: "atlas.hideNames") { didSet { UserDefaults.standard.set(hideNames, forKey: "atlas.hideNames") } }
  /// cards opened with the eye; forgotten when the app goes to the background
  var revealed: Set<String> = []
  /// 操作演示: the screen shows sandbox data; nothing is saved or synced until it ends
  private(set) var sandbox = false
  @ObservationIgnored private var realSnap: AtlasData? = nil
  /// 操作演示 step: add -> pick -> next -> who -> save -> map; nil = off
  var tourStep: String? = nil
  var tourFirstRun = false
  var tourRects: [String: CGRect] = [:]
  /// the restore point offered in 设置 (demo | import | wipe | restore)
  var restoreWhy: String? = nil
  var restoreAt: Double = UserDefaults.standard.double(forKey: "atlas.restoreAt")
  var kind: String = Store.savedKind() { didSet { UserDefaults.standard.set(kind, forKey: "atlas.kind") } }
  var theme: String = UserDefaults.standard.string(forKey: "atlas.theme") ?? "dark" { didSet { UserDefaults.standard.set(theme, forKey: "atlas.theme") } }
  var activeID: String? = nil
  var route: Route? = nil
  var toast: Toast? = nil
  var demoFocus: String? = nil
  /// true when there was nothing saved on this device: the app starts empty and offers the sample data once
  var firstRun = false
  /// the data file couldn't be read at launch: it was set aside (atlas.unreadable-*.json) and the last good copy loaded
  @ObservationIgnored var recovered = false
  /// 2.4: how many duplicate identities wait on the one-time 合并 question (0 = don't ask)
  var mergeAsk = 0
  @ObservationIgnored var toastAction: (() -> Void)? = nil
  @ObservationIgnored var onSaved: (() -> Void)? = nil

  @ObservationIgnored private var seenI: [String: Identity] = [:]
  @ObservationIgnored private var seenA: [String: Account] = [:]
  @ObservationIgnored private var undoSnap: AtlasData? = nil
  private let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("atlas.json")
  private let io = DispatchQueue(label: "atlas.persist", qos: .utility)

  init() {
    let raw = try? Data(contentsOf: url)
    if let d = raw, let s = try? JSONDecoder().decode(AtlasData.self, from: d) {
      data = s
      // keep the last copy that opened fine, so an update can never be the only copy of the data
      if !s.identities.isEmpty || !s.accounts.isEmpty { try? d.write(to: url.deletingLastPathComponent().appendingPathComponent("atlas.prev.json"), options: .atomic) }
    } else if let d = raw, !d.isEmpty {
      // the file is there but can't be read: never start over on top of it. Set it aside, then fall
      // back to the last good copy; the user is told in 设置 via the toast below.
      let stamp = Int(Date().timeIntervalSince1970)
      try? d.write(to: url.deletingLastPathComponent().appendingPathComponent("atlas.unreadable-\(stamp).json"), options: .atomic)
      let prev = url.deletingLastPathComponent().appendingPathComponent("atlas.prev.json")
      if let p = try? Data(contentsOf: prev), let s = try? JSONDecoder().decode(AtlasData.self, from: p) { data = s; recovered = true }
      else { data = AtlasData(); recovered = true }
    } else if UserDefaults.standard.object(forKey: "atlasSeed") != nil || UserDefaults.standard.string(forKey: "atlasDemo") != nil {
      var d = Store.demo()   // CI screenshots / promo clips
      for k in d.identities.indices { d.identities[k].demo = true }
      for k in d.accounts.indices { d.accounts[k].demo = true }
      data = d
    } else {
      data = AtlasData()
      firstRun = true
    }
    index = AtlasIndex(data)
    remember()
    restoreWhy = FileManager.default.fileExists(atPath: restoreURL.path) ? UserDefaults.standard.string(forKey: "atlas.restoreWhy") : nil
    // 2.5: sample data is marked per item. Older builds kept a device list of sample ids (and a whole-file
    // flag): exactly the listed items get the marker, never anything else.
    adoptLegacyDemo()
    // 2.4 (atlas.prev.json above is still the untouched copy): Apple login cards become their email /
    // phone, silently and losslessly. Duplicates are merged only after the user says yes, asked once.
    migrate24()
    if hasReal && UserDefaults.standard.string(forKey: "atlas.merge24") != "no" { mergeAsk = dupCount24(data) }
    activeID = deck.first?.id
    // decode every icon the first screens will show, off the main thread, before they're needed
    IconStore.shared.prefetch(data.accounts)
  }

  static func savedKind() -> String {
    let k = UserDefaults.standard.string(forKey: "atlas.kind") ?? "email"
    return KINDS.contains(k) ? k : "email"
  }
  var deck: [Identity] {
    switch kind {
    case "phone": return index.phones
    case "email": return index.emails
    case "login": return index.logins
    default: return data.identities.filter { $0.type == kind }
    }
  }
  /// every email / phone a login card is bound to (2.5: can be several); dangling or wrong-typed ids count as none
  func binds(_ i: Identity) -> [Identity] {
    guard i.isLogin else { return [] }
    return i.bindIDs.compactMap { index.identityByID[$0] }.filter { ($0.type == "email" || $0.type == "phone") && $0.id != i.id }
  }
  /// the first binding (what older clients show)
  func via(_ i: Identity) -> Identity? { binds(i).first }
  /// login cards bound to this email / phone
  func boundLogins(_ id: String) -> [Identity] { index.boundLogins[id] ?? [] }
  var active: Identity? { deck.first { $0.id == activeID } ?? (activeID == "add" ? nil : deck.first) }
  func identity(_ id: String) -> Identity? { index.identityByID[id] }
  func account(_ id: String) -> Account? { index.accountByID[id] }
  func accounts(of id: String) -> [Account] { index.byIdentity[id] ?? [] }

  /// the value as shown: masked by default (2.5 level 1) unless revealed with the eye
  func display(_ i: Identity) -> String { isRevealed(i.id) ? i.value : maskValue(i) }
  func isRevealed(_ id: String) -> Bool { !maskValues || revealed.contains(id) }
  func toggleReveal(_ id: String) { if revealed.contains(id) { revealed.remove(id) } else { revealed.insert(id) } }
  /// the app went to the background: every value is masked again
  func remask() { if !revealed.isEmpty { revealed = [] } }
  /// labels and usernames: shown unless 隐藏用户名 is on (2.5 level 2)
  func labelText(_ s: String) -> String { hideNames ? maskName(s) : s }
  func handleText(_ h: String) -> String { labelText(h) }
  func title(_ i: Identity) -> String { i.label.isEmpty ? display(i) : labelText(i.label) }

  // every change goes through here: snapshot for undo, stamp, persist, sync.
  // Works on a copy and assigns once, so observers and the index update exactly once per change.
  func mutate(_ message: String? = nil, undo: Bool = true, _ f: (inout AtlasData) -> Void) {
    if undo { undoSnap = data }   // a silent fix-up (a late icon) must not replace what 撤销 restores
    var d = data
    f(&d)
    d.demo = nil
    stamp(&d)
    data = d
    afterSave()
    if let message { show(message, action: undo ? "撤销" : nil) { [weak self] in self?.undo() } }
  }
  func undo() {
    guard let s = undoSnap else { return }
    data = s; save()
  }

  private static func plain(_ x: Identity) -> Identity { var y = x; y.updatedAt = nil; return y }
  private static func plain(_ x: Account) -> Account { var y = x; y.updatedAt = nil; return y }
  func remember() {
    seenI = [:]; seenA = [:]
    for x in data.identities { seenI[x.id] = Store.plain(x) }
    for x in data.accounts { seenA[x.id] = Store.plain(x) }
  }
  /// stamps changed records (compared field by field, ignoring updatedAt) and tombstones removed ones
  private func stamp(_ d: inout AtlasData) {
    let now = nowMS()
    var live = Set<String>()
    for k in d.identities.indices {
      let x = d.identities[k]; live.insert(x.id)
      if seenI[x.id] != Store.plain(x) || x.updatedAt == nil { d.identities[k].updatedAt = now }
      d.deleted[x.id] = nil
    }
    for k in d.accounts.indices {
      let x = d.accounts[k]; live.insert(x.id)
      if seenA[x.id] != Store.plain(x) || x.updatedAt == nil { d.accounts[k].updatedAt = now }
      d.deleted[x.id] = nil
    }
    for id in seenI.keys where !live.contains(id) { d.deleted[id] = now }
    for id in seenA.keys where !live.contains(id) { d.deleted[id] = now }
  }
  func save() {
    var d = data
    stamp(&d)
    data = d
    afterSave()
  }
  private func afterSave() {
    remember()
    persist()
    if !sandbox { onSaved?() }
  }
  /// encoding is cheap; the file write happens off the main thread so a tap never waits on disk
  func persist() {
    if sandbox { return }   // 操作演示: nothing reaches the file
    guard let d = try? JSONEncoder().encode(data) else { return }
    let u = url
    io.async { try? d.write(to: u, options: .atomic) }
  }
  /// the silent part of the 2.4 migration; safe to run any time (idempotent)
  func migrate24() {
    var probe = data
    guard autoFix24(&probe) else { return }
    let demo = data.demo
    mutate(nil, undo: false) { _ = autoFix24(&$0) }
    if demo == true { data.demo = true; persist() }
  }
  func mergeDuplicates() {
    mergeAsk = 0
    var n = 0
    mutate(nil) { n = mergeDups24(&$0) }
    show("已合并 \(n) 个重复的身份", action: "撤销") { [weak self] in self?.undo() }
    setKind(kind)
  }
  func declineMerge() {
    mergeAsk = 0
    UserDefaults.standard.set("no", forKey: "atlas.merge24")
  }
  /// the identity with this value, or a new one (reuse first: no value is ever typed in twice).
  /// Returns the id and whether it already existed.
  @discardableResult func ensureIdentity(type: String, value: String, provider: String? = nil, via: String? = nil) -> (String, Bool) {
    ensureIdentity(type: type, value: value, provider: provider, binds: via.map { [$0] } ?? [])
  }
  @discardableResult func ensureIdentity(type: String, value: String, provider: String?, binds: [String]) -> (String, Bool) {
    let v = value.trimmingCharacters(in: .whitespaces)
    if let k = idKey(type: type, provider: provider, value: v), let hit = data.identities.first(where: { idKey($0) == k }) { return (hit.id, true) }
    var bound = binds
    // a QQ whose QQ is an email you already have is bound to that email too, without typing it twice
    if type == "login", provider == "qq", v.contains("@"),
       let m = data.identities.first(where: { $0.type == "email" && normEmail($0.value) == normEmail(v) }), !bound.contains(m.id) { bound.append(m.id) }
    let color = type == "login" ? loginDefaultColor(provider ?? "wechat") : COLORS[data.identities.count % COLORS.count]
    var i = Identity(type: type, value: v, color: color, provider: type == "login" ? provider : nil)
    if type == "login" { i.setBinds(bound) }
    mutate(nil, undo: false) { $0.identities.append(i) }
    return (i.id, false)
  }
  /// the email / phone that already has this value (other than `except`)
  func existing(type: String, provider: String?, value: String, except: String? = nil) -> Identity? {
    guard let k = idKey(type: type, provider: provider, value: value) else { return nil }
    return data.identities.first { $0.id != except && idKey($0) == k }
  }

  // replace everything (sync merge), without new stamps
  func adopt(_ d: AtlasData) {
    if sandbox { realSnap = d; writeReal(d); return }   // the real data changed under the 操作演示
    data = d; remember(); persist()
    migrate24()   // an older client may still add an Apple login card
    IconStore.shared.prefetch(d.accounts)
    if active == nil || !deck.contains(where: { $0.id == activeID }) { activeID = deck.first?.id }
  }

  /// 小提示: at most once a week, the card that hasn't had a new site in two weeks or more
  func nudge() {
    let d = UserDefaults.standard, now = nowMS(), day = 86_400_000.0
    guard hasReal, !sandbox, now - d.double(forKey: "atlas.nudgeAt") >= 7 * day else { return }
    let demo = demoIDs
    var pick: Identity? = nil, oldest = Double.infinity
    for i in data.identities where !demo.contains(i.id) {
      guard let t = data.accounts.filter({ $0.identityIds.contains(i.id) }).map(\.createdAt).max() else { continue }
      if t < oldest { oldest = t; pick = i }
    }
    guard let p = pick, now - oldest >= 14 * day else { return }
    d.set(now, forKey: "atlas.nudgeAt")
    _ = p; show("好久没来记录新账号啦，要不要来看看呢～")   // plain text only: no address, number or name on screen
  }

  func show(_ text: String, action: String? = nil, _ fn: (() -> Void)? = nil) {
    toastAction = fn
    withAnimation(Motion.smooth) { toast = Toast(text: text, action: action) }
    let t = toast
    DispatchQueue.main.asyncAfter(deadline: .now() + (action == nil ? 2.2 : 4.5)) { [weak self] in
      if self?.toast == t { withAnimation(.easeInOut(duration: 0.3)) { self?.toast = nil } }
    }
  }

  func setKind(_ k: String, focus: String? = nil) {
    kind = k
    activeID = focus ?? deck.first?.id ?? "add"
  }

  // apps the user typed in by hand come back in the picker.
  // atlas.custom entries are [name, domain, category, "", icon?] (same shape as the web app);
  // atlas.custom.hidden holds lowercase names the user removed from 我添加的 (local only, not synced).
  var customRev = 0   // UserDefaults isn't observed: bump this so views re-read customApps
  var customApps: [Preset] {
    let _ = customRev
    var out: [Preset] = [], seenN = Set<String>()
    let hidden = Set((UserDefaults.standard.stringArray(forKey: "atlas.custom.hidden") ?? []).map { $0.lowercased() })
    let saved = (UserDefaults.standard.array(forKey: "atlas.custom") as? [[String]]) ?? []
    let all = saved + data.accounts.reversed().map { [$0.name, $0.domain, $0.category, "", $0.icon ?? ""] }
    for t in all where t.count >= 1 {
      let k = t[0].lowercased()
      if k.isEmpty || seenN.contains(k) || hidden.contains(k) || Presets.all.contains(where: { $0.name.lowercased() == k }) { continue }
      seenN.insert(k)
      let ic = t.count > 4 && t[4].hasPrefix("data:image/") ? t[4] : nil
      out.append(Preset(name: t[0], domain: t.count > 1 ? t[1] : "", category: t.count > 2 ? t[2] : "其他", color: "", icon: ic))
    }
    return out
  }
  func rememberApp(_ p: Preset) {
    unhideApps([p.name])
    if Presets.all.contains(where: { $0.name.lowercased() == p.name.lowercased() }) { return }
    var saved = (UserDefaults.standard.array(forKey: "atlas.custom") as? [[String]]) ?? []
    saved.removeAll { $0.first?.lowercased() == p.name.lowercased() }
    var t = [p.name, p.domain, p.category]
    if let ic = p.icon, !ic.isEmpty { t += ["", ic] }
    saved.insert(t, at: 0)
    UserDefaults.standard.set(Array(saved.prefix(80)), forKey: "atlas.custom")
    customRev &+= 1
  }
  /// takes an app out of 我添加的; accounts already recorded for it stay untouched
  func hideApp(_ name: String) {
    let k = name.lowercased()
    var saved = (UserDefaults.standard.array(forKey: "atlas.custom") as? [[String]]) ?? []
    saved.removeAll { $0.first?.lowercased() == k }
    UserDefaults.standard.set(saved, forKey: "atlas.custom")
    var hidden = UserDefaults.standard.stringArray(forKey: "atlas.custom.hidden") ?? []
    if !hidden.contains(k) { hidden.append(k) }
    UserDefaults.standard.set(hidden, forKey: "atlas.custom.hidden")
    customRev &+= 1
  }
  /// adding an app again (by hand or from the App Store) brings it back
  func unhideApps(_ names: [String]) {
    let ks = Set(names.map { $0.lowercased() })
    let hidden = UserDefaults.standard.stringArray(forKey: "atlas.custom.hidden") ?? []
    let kept = hidden.filter { !ks.contains($0) }
    guard kept.count != hidden.count else { return }
    UserDefaults.standard.set(kept, forKey: "atlas.custom.hidden")
    customRev &+= 1
  }

  /// Accounts saved before 2.3.1 with no icon of their own and no bundled one (王者荣耀, 金铲铲之战 …)
  /// were drawn with a parent site's logo or a 32 px favicon. Look each name up on the App Store once
  /// and keep the artwork of an exact-name match. A few names per launch; each name is tried only once.
  func backfillIcons() async {
    guard hasReal, !sandbox else { return }
    let triedKey = "atlas.iconTried"
    var tried = Set(UserDefaults.standard.stringArray(forKey: triedKey) ?? [])
    var names: [String] = []
    for a in data.accounts where (a.icon ?? "").isEmpty {
      let k = a.name.lowercased()
      guard !k.isEmpty, !tried.contains(k), !names.contains(k),
            !IconStore.shared.key(name: a.name, domain: a.domain).hasPrefix("b:") else { continue }
      names.append(k)
      if names.count == 6 { break }
    }
    for k in names {
      guard let hits = await AppStoreSearch.search(k) else { return }   // offline: try again next launch
      tried.insert(k)
      UserDefaults.standard.set(Array(tried), forKey: triedKey)
      guard let a = hits.first(where: { $0.cleanName.lowercased() == k || $0.trackName.lowercased() == k }),
            let u = await a.iconDataURL() else { continue }
      let ids = data.accounts.filter { $0.name.lowercased() == k && ($0.icon ?? "").isEmpty }.map(\.id)
      guard !ids.isEmpty else { continue }
      mutate(nil, undo: false) { s in
        for i in s.accounts.indices where ids.contains(s.accounts[i].id) { s.accounts[i].icon = u }
      }
      IconStore.shared.prefetch(data.accounts.filter { ids.contains($0.id) })
    }
  }

  func backupJSON() -> Data {
    let iso = ISO8601DateFormatter().string(from: Date())
    let ids = (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(data.identities))) ?? []
    let accs = (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(data.accounts))) ?? []
    let o: [String: Any] = ["app": "atlas", "version": 1, "exportedAt": iso, "identities": ids, "accounts": accs]
    return (try? JSONSerialization.data(withJSONObject: o, options: [.prettyPrinted, .sortedKeys])) ?? Data()
  }
  /// a backup file, read but not applied (nil when it isn't one)
  func readBackup(_ d: Data) -> AtlasData? {
    guard let b = try? JSONDecoder().decode(AtlasData.self, from: d),
          let raw = try? JSONSerialization.jsonObject(with: d) as? [String: Any], raw["identities"] is [Any], raw["accounts"] is [Any] else { return nil }
    return b
  }
  func importBackup(_ d: Data) -> Bool {
    guard let b = readBackup(d) else { return false }
    saveRestorePoint("import")   // the data it replaces can always be brought back from 设置
    mutate("已导入 \(b.identities.count) 个身份和 \(b.accounts.count) 个网站") { s in s.identities = b.identities; s.accounts = b.accounts }
    setKind(kind)
    return true
  }

  // 2.5 sample data: every sample item carries demo: true. It is only ever added next to what is there (and
  // only while there is nothing of the user's own), and 「清除示例数据」 removes exactly the marked items.
  private static let demoKey = "atlas.demoIds"
  var demoIDs: Set<String> { Set(data.identities.filter(\.isDemo).map(\.id) + data.accounts.filter { $0.demo == true }.map(\.id)) }
  var hasDemo: Bool { data.identities.contains(where: \.isDemo) || data.accounts.contains { $0.demo == true } }
  /// anything the user made themselves
  var hasReal: Bool { Store.hasReal(data) }
  static func hasReal(_ d: AtlasData) -> Bool { d.identities.contains { !$0.isDemo } || d.accounts.contains { $0.demo != true } }
  private func adoptLegacyDemo() {
    let list = Set(UserDefaults.standard.stringArray(forKey: Store.demoKey) ?? [])
    guard !list.isEmpty || data.demo != nil else { return }
    var d = data
    for k in d.identities.indices where list.contains(d.identities[k].id) { d.identities[k].demo = true }
    for k in d.accounts.indices where list.contains(d.accounts[k].id) { d.accounts[k].demo = true }
    d.demo = nil
    data = d; save()
    UserDefaults.standard.removeObject(forKey: Store.demoKey)
  }
  /// sync is on on this device (sample data is never loaded then)
  var syncOn: Bool { UserDefaults.standard.string(forKey: "atlas.sync") != nil }
  /// 载入示例 is offered only while there is nothing of the user's own
  var canLoadDemo: Bool { !sandbox && !syncOn && !hasReal && !hasDemo }
  func setTourRect(_ id: String, _ r: CGRect) { if tourRects[id] != r { tourRects[id] = r } }
  func loadDemo() {
    if hasReal { show("已有自己的数据，不能载入示例"); return }
    if syncOn { show("开着同步时不能载入示例"); return }
    saveRestorePoint("demo")
    var d = Store.demo()
    for k in d.identities.indices { d.identities[k].demo = true }
    for k in d.accounts.indices { d.accounts[k].demo = true }
    let have = Set(data.identities.map(\.id) + data.accounts.map(\.id))
    mutate("已载入示例，可在「设置」里清除") { s in
      s.identities += d.identities.filter { !have.contains($0.id) }
      s.accounts += d.accounts.filter { !have.contains($0.id) }
    }
    IconStore.shared.prefetch(data.accounts)
    setKind("email")
  }
  func clearDemo() {
    let d = demoIDs
    mutate("已清除示例数据") { s in Store.strip(&s, d) }
    setKind(kind)
  }
  /// takes these items out; real items just lose their links to them
  static func strip(_ s: inout AtlasData, _ d: Set<String>) {
    s.identities = s.identities.filter { !d.contains($0.id) }.map { i in
      var i = i; if i.bindIDs.contains(where: { d.contains($0) }) { i.setBinds(i.bindIDs.filter { !d.contains($0) }) }; return i
    }
    s.accounts = s.accounts.filter { !d.contains($0.id) }.map { a in
      var a = a; a.identityIds.removeAll { d.contains($0) }; return a
    }
  }

  // MARK: restore point: written before anything that replaces data (sample, import, wipe, restore)

  private var restoreURL: URL { url.deletingLastPathComponent().appendingPathComponent("atlas.restore.json") }
  func saveRestorePoint(_ why: String) {
    guard !sandbox, !(data.identities.isEmpty && data.accounts.isEmpty), let d = try? JSONEncoder().encode(data) else { return }
    guard (try? d.write(to: restoreURL, options: .atomic)) != nil else { return }
    restoreWhy = why; restoreAt = nowMS()
    UserDefaults.standard.set(why, forKey: "atlas.restoreWhy"); UserDefaults.standard.set(restoreAt, forKey: "atlas.restoreAt")
  }
  var restoreTitle: String? {
    switch restoreWhy {
    case nil: return nil
    case "demo": return "恢复导入示例前的数据"
    case "import": return "恢复导入备份前的数据"
    case "wipe": return "恢复清空前的数据"
    default: return "撤回刚才的恢复"
    }
  }
  func restore() {
    guard let raw = try? Data(contentsOf: restoreURL), let b = try? JSONDecoder().decode(AtlasData.self, from: raw) else { show("备份读不出来了"); return }
    let keep = try? JSONEncoder().encode(data)
    mutate("已恢复 \(b.identities.count) 个身份和 \(b.accounts.count) 个网站") { s in s.identities = b.identities; s.accounts = b.accounts }
    // the data that was just replaced becomes the new restore point, so a restore can be taken back too
    if let keep, (try? keep.write(to: restoreURL, options: .atomic)) != nil {
      restoreWhy = "restore"; restoreAt = nowMS()
      UserDefaults.standard.set("restore", forKey: "atlas.restoreWhy"); UserDefaults.standard.set(restoreAt, forKey: "atlas.restoreAt")
    }
    IconStore.shared.prefetch(data.accounts)
    setKind(kind)
  }

  // MARK: 操作演示 sandbox: the tour runs on the real screens with throwaway data

  /// what is really saved, even while the 操作演示 shows sandbox data
  var realData: AtlasData { sandbox ? (realSnap ?? data) : data }
  private func writeReal(_ d: AtlasData) {
    guard let b = try? JSONEncoder().encode(d) else { return }
    let u = url
    io.async { try? b.write(to: u, options: .atomic) }
  }
  func startTour(firstRun: Bool) {
    guard tourStep == nil else { return }
    route = nil
    realSnap = data
    sandbox = true
    undoSnap = nil
    var sample = Identity(type: "email", value: "me@example.com", label: "示例邮箱", color: COLORS[0])
    sample.demo = true
    data = AtlasData(identities: [sample])
    remember()
    setKind("email", focus: sample.id)
    tourFirstRun = firstRun
    tourStep = "add"
  }
  func tourAdvance(_ from: String, _ to: String) { if tourStep == from { tourStep = to } }
  /// the sandbox app was saved: show it on the map
  func tourSaved() { if tourStep == "save" { tourStep = "map" } }
  func endTour(loadDemo demoAfter: Bool = false) {
    guard tourStep != nil else { return }
    tourStep = nil
    route = nil
    if let r = realSnap { data = r }
    realSnap = nil; sandbox = false; undoSnap = nil
    remember()
    setKind(kind)
    UserDefaults.standard.set(true, forKey: "atlas.tourDone")
    onSaved?()
    if demoAfter { loadDemo() }
  }

  // MARK: 2.5 copying an account value: local only, expires after 60 s

  func copySecret(_ i: Identity) {
    UIPasteboard.general.setItems([[UTType.plainText.identifier: i.value]], options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(60)])
    show("已复制 \(maskValue(i)) · 60 秒后自动清除")
  }

  static func demo() -> AtlasData {
    let ids = [
      Identity(type: "email", value: "me@gmail.com", label: "主力邮箱", color: "#0a84ff"),
      Identity(type: "phone", value: "+86 138 0000 1234", label: "主力手机号", color: "#30d158", notes: "移动，已实名"),
      Identity(type: "email", value: "shopping.fun@qq.com", label: "购物小号", color: "#ff375f"),
      Identity(type: "email", value: "dev@outlook.com", label: "开发专用", color: "#bf5af2"),
      Identity(type: "phone", value: "+86 170 0000 5678", label: "副卡", color: "#ff9f0a", esim: true),
    ]
    let g = ids[0].id, p1 = ids[1].id, q = ids[2].id, o = ids[3].id, p2 = ids[4].id
    let wx = Identity(type: "login", value: "atlas_wx2024", label: "", color: loginDefaultColor("wechat"), provider: "wechat", via: p1)
    let ap = Identity(type: "email", value: "me@icloud.com", label: "Apple ID", color: "#1d1d1f", apple: true)
    let w = wx.id, a = ap.id
    let day = 86_400_000.0, now = nowMS()
    func mk(_ n: String, _ d: String, _ c: String, _ i: [String], _ ago: Double, _ notes: String = "") -> Account {
      Account(name: n, domain: d, category: c, identityIds: i, notes: notes, createdAt: now - ago * day - Double.random(in: 0..<3e6))
    }
    return AtlasData(identities: ids + [wx, ap], accounts: [
      mk("GitHub", "github.com", "开发", [o, p1], 0, "已开两步验证"), mk("Claude", "claude.ai", "AI", [g], 0),
      mk("Cloudflare", "cloudflare.com", "开发", [o], 1), mk("ChatGPT", "chatgpt.com", "AI", [g], 1),
      mk("小红书", "xiaohongshu.com", "社交", [p2, w], 3), mk("Binance", "binance.com", "加密", [o, p1], 3, "已完成 KYC"),
      mk("淘宝", "taobao.com", "购物", [q, p1], 9), mk("京东", "jd.com", "购物", [q, p2], 12),
      mk("微信", "weixin.qq.com", "社交", [p1], 30), mk("支付宝", "alipay.com", "支付金融", [p1], 30),
      mk("哔哩哔哩", "bilibili.com", "娱乐", [p2], 40), mk("Steam", "steampowered.com", "游戏", [q], 60, "令牌在主力手机上"),
      mk("Apple ID", "apple.com", "工具", [g, p1], 90), mk("Notion", "notion.so", "工具", [a], 120), mk("网易云音乐", "music.163.com", "娱乐", [p2], 150),
      mk("美团", "meituan.com", "出行", [w], 20), mk("拼多多", "pinduoduo.com", "购物", [w], 25),
    ])
  }
}
