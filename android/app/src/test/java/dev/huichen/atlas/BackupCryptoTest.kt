package dev.huichen.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 2.6: tools/testvectors/backup-enc-v1.json was written by Node's WebCrypto (tools/backup-vector.mjs),
 * the same API the web app uses. Android must open it to the plain file byte for byte, and what Android
 * writes must have the same shape (so web / iOS open it the same way).
 */
class BackupCryptoTest {
  private fun res(n: String) = File(javaClass.classLoader!!.getResource(n)!!.toURI()).readText()
  private val pass get() = res("backup-password.txt")

  @Test fun opensWebVector() {
    val plain = BackupCrypto.decrypt(res("backup-enc-v1.json"), pass)
    assertEquals(res("backup-plain-v1.json"), plain)
    // and it is a backup the existing import path reads
    val d = decodeAtlas(plain)
    assertEquals(3, d.identities.size)
    assertEquals("主力邮箱", d.identities[0].label)
  }

  @Test fun wrongPasswordIsReportedAsSuch() {
    for (p in listOf(pass + "x", "星图-backup 1234", "")) {
      try { BackupCrypto.decrypt(res("backup-enc-v1.json"), p); fail("opened with a wrong password") }
      catch (e: BackupCrypto.WrongPassword) { }
    }
  }

  @Test fun brokenEnvelopesAreBadFormat() {
    val env = res("backup-enc-v1.json")
    for (bad in listOf(env.replace("\"kdf\": \"PBKDF2-SHA256\"", "\"kdf\": \"scrypt\""), env.replace("\"atlasEncrypted\": 1", "\"atlasEncrypted\": 2"), "{\"atlasEncrypted\":1}", "not json")) {
      try { BackupCrypto.decrypt(bad, pass); fail("accepted a broken file") } catch (e: BackupCrypto.BadFormat) { }
    }
  }

  @Test fun detectsEnvelopeOnly() {
    assertTrue(BackupCrypto.isEnvelope(res("backup-enc-v1.json")))
    assertFalse(BackupCrypto.isEnvelope(res("backup-plain-v1.json")))
    assertFalse(BackupCrypto.isEnvelope("[]"))
  }

  @Test fun roundTripWithAgreedShape() {
    val plain = res("backup-plain-v1.json")
    val out = BackupCrypto.encrypt(plain, "1234")
    val o = kotlinx.serialization.json.Json.parseToJsonElement(out) as kotlinx.serialization.json.JsonObject
    assertEquals(listOf("atlasEncrypted", "app", "kdf", "iter", "salt", "iv", "ct"), o.keys.toList())
    assertEquals("210000", o["iter"].toString())
    assertEquals(16, AtlasCrypto.unb64(o["salt"]!!.toString().trim('"')).size)
    assertEquals(12, AtlasCrypto.unb64(o["iv"]!!.toString().trim('"')).size)
    assertEquals(plain.toByteArray().size + 16, AtlasCrypto.unb64(o["ct"]!!.toString().trim('"')).size)
    assertEquals(plain, BackupCrypto.decrypt(out, "1234"))
  }
}
