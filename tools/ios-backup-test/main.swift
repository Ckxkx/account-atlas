// iOS side of the 2.6 backup-encryption tests (there is no Xcode test target). CI (ios.yml) compiles
// native/BackupCrypto.swift together with this file for macOS and runs it:
//   backuptest <tools/testvectors> <out.json>
// It opens the WebCrypto-made vector, checks a wrong password is reported as such, and writes a file of
// its own (password 1234) that the web test then opens (node tools/test-backup-crypto.mjs <out.json> 1234).
import Foundation

func check(_ ok: Bool, _ what: String) { if !ok { print("FAIL: \(what)"); exit(1) } }
let dir = URL(fileURLWithPath: CommandLine.arguments[1])
let env = try! Data(contentsOf: dir.appendingPathComponent("backup-enc-v1.json"))
let plain = try! Data(contentsOf: dir.appendingPathComponent("backup-plain-v1.json"))
let pass = try! String(contentsOf: dir.appendingPathComponent("backup-password.txt"), encoding: .utf8)

check(BackupCrypto.isEnvelope(env), "vector is an envelope")
check(!BackupCrypto.isEnvelope(plain), "plain backup is not an envelope")
check((try? BackupCrypto.decrypt(env, pass: pass)) == plain, "vector opens to the plain file byte for byte")
for bad in [pass + "x", "星图-backup 1234", ""] {
  do { _ = try BackupCrypto.decrypt(env, pass: bad); check(false, "opened with a wrong password") }
  catch BackupCrypto.Failure.wrongPassword {} catch { check(false, "wrong password reported as \(error)") }
}
do { _ = try BackupCrypto.decrypt(Data("{\"atlasEncrypted\":1}".utf8), pass: pass); check(false, "broken file opened") }
catch BackupCrypto.Failure.badFormat {} catch { check(false, "broken file reported as \(error)") }
let mine = try! BackupCrypto.encrypt(plain, pass: "1234")
check((try? BackupCrypto.decrypt(mine, pass: "1234")) == plain, "round trip")
let o = try! JSONSerialization.jsonObject(with: mine) as! [String: Any]
check(o["iter"] as? Int == 210_000 && Data(base64Encoded: o["salt"] as! String)?.count == 16 && Data(base64Encoded: o["iv"] as! String)?.count == 12, "envelope shape")
check(Data(base64Encoded: o["ct"] as! String)?.count == plain.count + 16, "ct = ciphertext || 16-byte tag")
if CommandLine.arguments.count > 2 { try! mine.write(to: URL(fileURLWithPath: CommandLine.arguments[2])) }
print("backup crypto (iOS): all passed")
