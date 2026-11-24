package dev.huichen.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.4: Apple ID is a flag on an email / phone, one identity per value. Same cases as the web test of app.js. */
class Migrate24Test {
  private fun sample() = decodeAtlas("""{"identities":[
    {"id":"e1","type":"email","value":"Me@Gmail.com ","label":"","color":"#0a84ff","notes":"a"},
    {"id":"p1","type":"phone","value":"+86 138 0000 1234","label":"主力","color":"#30d158","notes":""},
    {"id":"ap","type":"login","provider":"apple","value":"me@gmail.com","label":"Apple","color":"#1d1d1f","notes":"b","via":"p1"},
    {"id":"ap2","type":"login","provider":"apple","value":"+86 139 1111 2222","color":"#1d1d1f"},
    {"id":"p2","type":"phone","value":"13800001234","color":"#ffffff","esim":true},
    {"id":"w1","type":"login","provider":"wechat","value":"wx","via":"p2","color":"#07c160"},
    {"id":"q1","type":"login","provider":"qq","value":"ME@gmail.com","color":"#12b7f5"}],
    "accounts":[{"id":"a","name":"A","identityIds":["ap","e1"]},{"id":"b","name":"B","identityIds":["p2","w1"]},{"id":"c","name":"C","identityIds":["ap2"]}],"deleted":{}}""")

  @Test fun normalizes() {
    assertEquals("13800001234", normPhone("+86 138 0000 1234"))
    assertEquals("13800001234", normPhone("0086-13800001234"))
    assertEquals("13800001234", normPhone("138 0000 1234"))
    assertEquals("e:me@gmail.com", idKey("email", null, " Me@Gmail.com"))
    assertNull(idKey("login", "apple", "me@icloud.com"))
    assertEquals("q:10001", idKey("login", "qq", "100 01"))
  }

  @Test fun appleLoginBecomesFlaggedIdentityInPlace() {
    val d = Migrate24.autoFix(sample())
    val ap = d.identities.first { it.id == "ap" }
    assertEquals("email", ap.type); assertEquals(true, ap.apple); assertNull(ap.provider)
    assertEquals("p1", ap.via)                                   // kept, so nothing is lost
    assertEquals("phone", d.identities.first { it.id == "ap2" }.type)
    assertEquals("e1", d.identities.first { it.id == "q1" }.via)  // QQ email reuses the email card
    assertSame(d, Migrate24.autoFix(d))                          // idempotent
    assertEquals(listOf("ap", "e1"), d.accounts[0].identityIds)  // ids kept: no references move
  }

  @Test fun mergeKeepsTheOlderOneAndRepoints() {
    val d0 = Migrate24.autoFix(sample())
    assertEquals(2, Migrate24.dupCount(d0))
    val (d, n) = Migrate24.merge(d0)
    assertEquals(2, n)
    assertEquals(listOf("e1", "p1", "ap2", "w1", "q1"), d.identities.map { it.id })
    val e1 = d.identities[0]
    assertEquals(true, e1.apple); assertEquals("Apple", e1.label); assertEquals("a\nb", e1.notes)
    assertEquals(true, d.identities[1].esim)
    assertEquals("p1", d.identities.first { it.id == "w1" }.via)
    assertEquals(listOf("e1"), d.accounts[0].identityIds)
    assertEquals(listOf("p1", "w1"), d.accounts[1].identityIds)
    assertEquals(listOf("ap2"), d.accounts[2].identityIds)
    assertEquals(0, Migrate24.merge(d).second)
    assertEquals(0, Migrate24.dupCount(d))
  }

  @Test fun appleFlagRoundTripAndOmittedWhenFalse() {
    val d = AtlasData(identities = listOf(Identity("e", "email", "a@b.co", apple = true), Identity("p", "phone", "1380000", apple = false)))
    val text = encodeAtlas(decodeAtlas(encodeAtlas(d)))
    assertEquals(1, Regex("\"apple\"").findAll(text).count())
    assertTrue(text.contains("\"apple\":true"))
    val back = decodeAtlas(text)
    assertTrue(back.identities[0].isAppleID); assertFalse(back.identities[1].isAppleID)
    assertNull(back.identities[1].apple)
  }

  @Test fun webBlobWithAppleKeyDecodes() {
    // what app.js / the iOS app write in 2.4; an older Android build (ignoreUnknownKeys) still reads it
    val d = decodeAtlas("""{"identities":[{"id":"e","type":"email","value":"me@icloud.com","color":"#1d1d1f","apple":true,"future":{"x":1}}],"accounts":[],"deleted":{}}""")
    assertTrue(d.identities[0].isAppleID)
    assertEquals(0, Migrate24.dupCount(d))
    assertSame(d, Migrate24.autoFix(d))
  }

  @Test fun bindTagNeverShowsTheFullValue() {
    assertEquals("尾号 1234", bindTag(Identity("p", "phone", "+86 138 0000 1234")))
    assertEquals("me•••@qq.com", bindTag(Identity("e", "email", "meow@qq.com")))
  }

  @Test fun valueErrors() {
    assertNull(idValueError("email", "a@b.co")); assertEquals("邮箱地址格式不对", idValueError("email", "a@b"))
    assertNull(idValueError("phone", "13800001234")); assertTrue(idValueError("phone", "123") != null)
    assertNull(idValueError("qq", "10001")); assertTrue(idValueError("qq", "abc") != null)
    assertNull(idValueError("wechat", "wx")); assertTrue(idValueError("wechat", " ") != null)
  }
}
