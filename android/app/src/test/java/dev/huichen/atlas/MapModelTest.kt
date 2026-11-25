package dev.huichen.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 关系图: deleting from the map leaves nothing pointing at the deleted id, and the focus never outlives its node */
class MapModelTest {
  private val data = AtlasData(
    identities = listOf(
      Identity("e1", "email", "me@gmail.com"),
      Identity("e2", "email", "Me@Gmail.com"),                  // duplicate of e1 (same node)
      Identity("p1", "phone", "+86 138 0000 1234"),
      Identity("p2", "phone", "+86 170 0000 5678"),
      Identity("w1", "login", "atlas_wx", provider = "wechat", via = "p1"),
      Identity("q1", "login", "12345", provider = "qq", via = "p1", vias = listOf("p1", "e1")),
    ),
    accounts = listOf(
      Account("a1", "12306", "12306.cn", "出行", listOf("e1", "p1"), createdAt = 1.0),
      Account("a2", "美团", "meituan.com", "生活", listOf("w1"), createdAt = 1.0),
      Account("a3", "GitHub", "github.com", "工具", listOf("e2"), createdAt = 1.0),
      Account("a4", "银行", "bank.cn", "金融", listOf("p1"), createdAt = 1.0),
    ),
  )

  private fun refs(d: AtlasData): Set<String> =
    d.identities.flatMap { it.bindIds }.toSet() + d.accounts.flatMap { it.identityIds }

  @Test fun layoutBeforeDelete() {
    val m = MapModel(data)
    assertEquals(listOf("e1", "p1", "p2", "w1", "q1"), m.ids.map { it.id })
    assertEquals(listOf("e1", "e2"), m.members["e1"])
    assertTrue("e1" to "a3" in m.threads)                       // through the duplicate
    assertTrue("p1" to "a4" in m.threads)
  }

  @Test fun deletingABoundPhoneFromTheMap() {
    val m0 = MapModel(data)
    val focus = "p1"                                             // tapped once on the map, then opened and deleted
    val d = data.withoutIdentity("p1")
    val m = MapModel(d)
    assertFalse(d.identities.any { it.id == "p1" })
    assertFalse("p1" in refs(d))                                 // no App, no 微信 / QQ binding left on it
    assertNull(d.identities.first { it.id == "w1" }.via)
    assertEquals(listOf("e1"), d.identities.first { it.id == "q1" }.bindIds)
    assertNull(d.identities.first { it.id == "q1" }.vias)        // vias only when there are two or more
    assertFalse(m.ids.any { it.id == "p1" })
    assertFalse(m.threads.any { it.first == "p1" })
    assertEquals(m0.apps.size, m.apps.size)                     // the Apps stay (a4 just has no thread now)
    assertFalse(m.threads.any { it.second == "a4" })
    assertNull(m.resolve(focus))                                 // focus dropped: nothing stays dimmed
    assertEquals("e1", m.resolve("e1"))
    assertNull(m.resolve(null))
  }

  @Test fun deletingTheNodeCardOfADuplicate() {
    val d = data.withoutIdentity("e1")
    val m = MapModel(d)
    assertEquals(listOf("e2"), m.members["e2"])                  // the duplicate becomes the node
    assertTrue("e2" to "a3" in m.threads)
    assertFalse(m.threads.any { it.second == "a1" && it.first == "e1" })
    assertFalse("e1" in refs(d))
    assertNull(m.resolve("e1"))
    // a focus on an identity that is now inside another node moves to that node
    val dup = MapModel(data)
    assertEquals("e1", dup.resolve("e2"))
  }

  @Test fun deletingALoginAndAnApp() {
    val d = data.withoutIdentity("w1").withoutAccount("a1")
    val m = MapModel(d)
    assertFalse(m.ids.any { it.id == "w1" })
    assertFalse(m.apps.any { it.id == "a1" })
    assertFalse(m.threads.any { it.second == "a1" || it.first == "w1" })
    assertFalse("w1" in refs(d))
    assertEquals(emptyList<String>(), d.accounts.first { it.id == "a2" }.identityIds)
    assertFalse(m.inNode(d.accounts.first { it.id == "a2" }, "w1"))   // a stale node id matches nothing
  }
}
