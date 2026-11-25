import Foundation
import CryptoKit
import CommonCrypto

/// 2.6 password-protected backups. Same file as the web app (app.js BackupCrypto) and Android (Crypto.kt):
///   {"atlasEncrypted":1,"app":"atlas","kdf":"PBKDF2-SHA256","iter":210000,"salt":b64(16),"iv":b64(12),"ct":b64(ciphertext || tag[16])}
///   key = PBKDF2-HMAC-SHA256(UTF-8 password, salt, iter, 32 bytes) via CommonCrypto, AES-256-GCM via CryptoKit.
///   The plaintext is exactly the plain backup file (UTF-8), so a decrypted backup imports like a plain one.
/// Only Foundation / CryptoKit / CommonCrypto here: CI also compiles this file on its own for the test vector.
enum BackupCrypto {
  static let iterations = 210_000
  enum Failure: Error { case badFormat, wrongPassword }

  /// true for anything that claims to be an encrypted backup (so it is never mistaken for a plain one)
  static func isEnvelope(_ d: Data) -> Bool {
    guard let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { return false }
    return o["atlasEncrypted"] != nil
  }

  static func derive(_ pass: String, salt: Data, iterations: Int) -> SymmetricKey? {
    var out = [UInt8](repeating: 0, count: 32)
    let pw = Array(pass.utf8)
    let len = pw.count
    let buf = pw.isEmpty ? [UInt8(0)] : pw   // never a nil pointer for an empty password (length stays 0)
    let saltBytes = [UInt8](salt)
    let rc = buf.withUnsafeBufferPointer { p in
      p.baseAddress!.withMemoryRebound(to: Int8.self, capacity: buf.count) { cp in
        CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), cp, len, saltBytes, saltBytes.count,
                             CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), UInt32(iterations), &out, 32)
      }
    }
    return rc == Int32(kCCSuccess) ? SymmetricKey(data: out) : nil
  }

  static func encrypt(_ plain: Data, pass: String) throws -> Data {
    let salt = SymmetricKey(size: .bits128).withUnsafeBytes { Data($0) }   // 16 random bytes
    guard let key = derive(pass, salt: salt, iterations: iterations) else { throw Failure.badFormat }
    let box = try AES.GCM.seal(plain, using: key, nonce: AES.GCM.Nonce())   // 12-byte random nonce
    let iv = box.nonce.withUnsafeBytes { Data($0) }
    let ct = box.ciphertext + box.tag   // ciphertext || tag, like WebCrypto and Java
    // written by hand so the keys keep the agreed order (base64 needs no JSON escaping)
    let text = "{\n  \"atlasEncrypted\": 1,\n  \"app\": \"atlas\",\n  \"kdf\": \"PBKDF2-SHA256\",\n  \"iter\": \(iterations),\n" +
      "  \"salt\": \"\(salt.base64EncodedString())\",\n  \"iv\": \"\(iv.base64EncodedString())\",\n  \"ct\": \"\(ct.base64EncodedString())\"\n}\n"
    return Data(text.utf8)
  }

  /// Throws .badFormat for a broken / unknown file and .wrongPassword when the password doesn't open it.
  static func decrypt(_ d: Data, pass: String) throws -> Data {
    guard let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { throw Failure.badFormat }
    func b64(_ k: String) -> Data? { (o[k] as? String).flatMap { Data(base64Encoded: $0.trimmingCharacters(in: .whitespacesAndNewlines)) } }
    guard (o["atlasEncrypted"] as? Int) == 1, (o["kdf"] as? String) == "PBKDF2-SHA256",
          let iter = o["iter"] as? Int, iter >= 1000, iter <= 10_000_000,
          let salt = b64("salt"), salt.count >= 8, let iv = b64("iv"), iv.count == 12,
          let ct = b64("ct"), ct.count > 16 else { throw Failure.badFormat }
    guard let key = derive(pass, salt: salt, iterations: iter),
          let nonce = try? AES.GCM.Nonce(data: iv),
          let box = try? AES.GCM.SealedBox(nonce: nonce, ciphertext: ct.prefix(ct.count - 16), tag: ct.suffix(16)) else { throw Failure.badFormat }
    do { return try AES.GCM.open(box, using: key) } catch { throw Failure.wrongPassword }
  }
}
