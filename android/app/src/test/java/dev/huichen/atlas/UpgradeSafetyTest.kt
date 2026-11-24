package dev.huichen.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpgradeSafetyTest {
  @Test fun gameOnTencentSubdomainIsNotQQ() {
    assertNull(presetMatch("王者荣耀", "pvp.qq.com", byName = false))
    assertNull(presetMatch("金铲铲之战", "jcc.qq.com", byName = false))
  }

  @Test fun exactAndNamedMatchesStillWork() {
    assertEquals("QQ", presetMatch("QQ", "qq.com", byName = false)?.name)
    assertEquals("企业微信", presetMatch("企业微信", "work.weixin.qq.com", byName = false)?.name)
    assertEquals("QQ", presetMatch("QQ", "im.qq.com", byName = false)?.name)
    assertEquals("微信", presetFor("微信", "")?.name)
  }

  @Test fun oneBrokenEntryDoesNotEmptyTheList() {
    val text = """{"identities":[{"id":"a","type":"email","value":"x@y.com"},{"type":"phone"}],
      "accounts":[{"id":"b","name":"GitHub","domain":"github.com","identityIds":["a"]},{"name":"no id"}],"deleted":{}}"""
    val d = decodeAtlas(text)
    assertEquals(listOf("a"), d.identities.map { it.id })
    assertEquals(listOf("b"), d.accounts.map { it.id })
  }
}
