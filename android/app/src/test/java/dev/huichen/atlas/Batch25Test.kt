package dev.huichen.atlas

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.5: adult filter, non-destructive sample data, QQ / 微信 bound to several identities, masking. */
class Batch25Test {

  // MARK: 1. App Store search never shows adult apps

  private fun hit(name: String, rating: String = "4+", advisories: List<String> = emptyList(), genre: String = "Games") =
    """{"trackName":"$name","primaryGenreName":"$genre","genres":["$genre"],"contentAdvisoryRating":"$rating","trackContentRating":"$rating",
       "advisories":[${advisories.joinToString(",") { "\"$it\"" }}],"artworkUrl100":"https://is1-ssl.mzstatic.com/a.png"}"""

  @Test fun adultAppsAreDropped() {
    val json = """{"resultCount":7,"results":[
      ${hit("无畏契约", "12+", listOf("Infrequent/Mild Cartoon or Fantasy Violence", "Infrequent/Mild Sexual Content and Nudity"))},
      ${hit("成人性爱交友-同城约会", "18+")},
      ${hit("某某直播", "17+", listOf("Frequent/Intense Sexual Content or Nudity"))},
      ${hit("夜色", "17+", listOf("频繁/强烈的性内容或裸露"))},
      ${hit("Hentai Reader", "12+")},
      ${hit("成人高考题库", "4+", genre = "Education")},
      ${hit("Instagram", "12+", listOf("Infrequent/Mild Sexual Content and Nudity", "Infrequent/Mild Mature/Suggestive Themes"), "Photo & Video")}
    ]}"""
    val names = parseAppStore(json).map { it.trackName }
    assertEquals(listOf("无畏契约", "成人高考题库", "Instagram"), names)
  }

  @Test fun adultWords() {
    assertTrue(isAdultApp("Porn Hub"))
    assertTrue(isAdultApp("NSFW gallery"))
    assertTrue(isAdultApp("约炮神器"))
    assertTrue(isAdultApp("福利姬视频"))
    assertTrue(isAdultApp("小游戏", genre = "18+"))
    assertTrue(isAdultApp("App", rating = "18+"))
    assertFalse(isAdultApp("Essex Bus"))          // a word boundary, not a substring
    assertFalse(isAdultApp("哔哩哔哩", rating = "17+"))
    assertFalse(isAdultApp("成人教育在线"))
    assertTrue(appStoreURL("x").contains("explicit=No"))
  }

  // MARK: 4. sample data is added next to real data and removed by its marker only

  private val real = AtlasData(
    identities = listOf(Identity("r1", "email", "me@real.com"), Identity("r2", "phone", "13900001111")),
    accounts = listOf(Account("ra", "GitHub", identityIds = listOf("r1"))),
  )

  @Test fun demoIsAddedNotReplacing() {
    val sample = AtlasData(listOf(Identity("d1", "email", "me@gmail.com")), listOf(Account("da", "淘宝", identityIds = listOf("d1"))), demo = true)
    val d = Demo.add(real, sample)
    assertEquals(listOf("r1", "r2", "d1"), d.identities.map { it.id })
    assertEquals(listOf("ra", "da"), d.accounts.map { it.id })
    assertTrue(d.identities.last().isDemo && d.accounts.last().demo == true)
    assertNull("never a whole-file flag", d.demo)
    assertTrue(d.identities.take(2).none { it.isDemo })
    // a real account that the user pointed at a sample identity keeps itself, minus that link
    val linked = d.copy(accounts = d.accounts.map { if (it.id == "ra") it.copy(identityIds = listOf("r1", "d1")) else it })
    val c = Demo.clear(linked)
    assertEquals(listOf("r1", "r2"), c.identities.map { it.id })
    assertEquals(listOf("ra"), c.accounts.map { it.id })
    assertEquals(listOf("r1"), c.accounts[0].identityIds)
    assertFalse(Demo.has(c)); assertTrue(Demo.hasReal(c))
  }

  @Test fun legacyDemoListMarksOnlyListedItems() {
    // an old whole-file flag left on by mistake must never turn real data into sample data
    val d = real.copy(identities = real.identities + Identity("d1", "email", "me@gmail.com"), demo = true)
    val m = Demo.adoptLegacy(d, setOf("d1", "gone"))
    assertEquals(setOf("d1"), Demo.ids(m))
    assertNull(m.demo)
    assertEquals(listOf("r1", "r2"), Demo.clear(m).identities.map { it.id })
    val untouched = Demo.adoptLegacy(real, emptySet())
    assertSame(real, untouched)
  }

  @Test fun demoMarkerRoundTrips() {
    val d = AtlasData(listOf(Identity("d1", "email", "a@b.c", demo = true), Identity("r1", "email", "x@y.z")), listOf(Account("a", "X", demo = true)))
    val text = encodeAtlas(d)
    assertFalse("only written when true", Regex("\"demo\":false").containsMatchIn(text))
    val back = decodeAtlas(text)
    assertEquals(setOf("d1", "a"), Demo.ids(back))
    assertFalse(text.contains("\"demo\":null"))
  }

  // MARK: 6. a QQ bound to a phone and its QQ email

  @Test fun viasInterop() {
    // old clients write only via
    val old = decodeAtlas("""{"identities":[{"id":"q","type":"login","provider":"qq","value":"12345","via":"p"},
      {"id":"p","type":"phone","value":"138"},{"id":"e","type":"email","value":"12345@qq.com"}],"accounts":[]}""")
    assertEquals(listOf("p"), old.identities[0].bindIds)
    // two bindings: via stays the first one (what older clients read), vias has both
    val two = old.identities[0].withBinds(listOf("p", "e"))
    assertEquals("p", two.via); assertEquals(listOf("p", "e"), two.vias)
    val text = encodeAtlas(old.copy(identities = listOf(two) + old.identities.drop(1)))
    val o = (kotlinx.serialization.json.Json.parseToJsonElement(text) as JsonObject)["identities"] as JsonArray
    val q = o[0] as JsonObject
    assertEquals("p", q["via"]!!.jsonPrimitive.content)
    assertEquals(2, (q["vias"] as JsonArray).size)
    // read both, union: a via an older client changed is still a binding
    val mixed = decodeAtlas("""{"identities":[{"id":"q","type":"login","provider":"qq","value":"1","via":"x","vias":["p","e"]}],"accounts":[]}""")
    assertEquals(listOf("x", "p", "e"), mixed.identities[0].bindIds)
    // one binding: no vias key at all
    val one = two.withBinds(listOf("e"))
    assertEquals("e", one.via); assertNull(one.vias)
    assertFalse(encodeAtlas(AtlasData(listOf(one))).contains("vias"))
    // none
    val none = two.withBinds(emptyList())
    assertNull(none.via); assertNull(none.vias)
    assertTrue(bindTags(listOf(Identity("p", "phone", "13800007553"), Identity("e", "email", "12345@qq.com"))).endsWith("+1"))
  }

  @Test fun mergeRepointsEveryBinding() {
    val d = AtlasData(listOf(
      Identity("p1", "phone", "+86 138 0000 1234"), Identity("p2", "phone", "13800001234"),
      Identity("e1", "email", "a@qq.com"),
      Identity("q", "login", "10001", provider = "qq").withBinds(listOf("p2", "e1")),
    ))
    val (m, n) = Migrate24.merge(d)
    assertEquals(1, n)
    assertEquals(listOf("p1", "e1"), m.identities.first { it.id == "q" }.bindIds)
  }

  @Test fun clearingSampleDropsOnlyItsBindings() {
    val d = AtlasData(listOf(
      Identity("p", "phone", "139"), Identity("dp", "phone", "138", demo = true),
      Identity("q", "login", "10001", provider = "qq").withBinds(listOf("dp", "p")),
    ))
    val c = Demo.clear(d)
    assertEquals(listOf("p"), c.identities.first { it.id == "q" }.bindIds)
    assertEquals("p", c.identities.first { it.id == "q" }.via)
  }

  // MARK: 7. masking

  @Test fun masking() {
    assertEquals("ab•••@gmail.com", maskValue(Identity("1", "email", "abcdef@gmail.com")))
    assertEquals("••• •••• 7553", maskValue(Identity("2", "phone", "+86 138 0000 7553")))
    assertEquals("wx•••", maskValue(Identity("3", "login", "wxid_abc", provider = "wechat")))
    assertEquals("•••45", maskValue(Identity("4", "login", "12345", provider = "qq")))
    assertEquals("12•••@qq.com", maskValue(Identity("5", "login", "12345@qq.com", provider = "qq")))
    assertEquals("主•••", maskName("主力邮箱"))
    assertEquals("", maskName(""))
    val all = listOf("abcdef@gmail.com", "+86 138 0000 7553").map { maskValue(Identity("x", if (it.contains('@')) "email" else "phone", it)) }
    assertTrue(all.none { it.contains("cdef") || it.contains("0000") })
  }
}
