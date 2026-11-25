package dev.huichen.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 检查更新: version comparison and the version.json reader */
class UpdateCheckTest {
  @Test fun compare() {
    assertTrue(VersionCmp.isNewer("2.7", "2.6"))
    assertTrue(VersionCmp.isNewer("2.6.1", "2.6"))
    assertTrue(VersionCmp.isNewer("2.10", "2.9"))      // numeric, not string order
    assertTrue(VersionCmp.isNewer("3.0", "2.99.9"))
    assertTrue(VersionCmp.isNewer("v2.7", "2.6"))
    assertFalse(VersionCmp.isNewer("2.6", "2.6"))
    assertFalse(VersionCmp.isNewer("2.6.0", "2.6"))
    assertFalse(VersionCmp.isNewer("2.6", "2.6.1"))
    assertFalse(VersionCmp.isNewer("2.5", "2.6"))
    assertEquals(0, VersionCmp.compare("2.6", "2.6.0"))
    assertEquals(-1, Integer.signum(VersionCmp.compare("2.3.3", "2.3.10")!!))
  }

  @Test fun notVersions() {
    assertNull(VersionCmp.compare("2.7", "dev"))
    assertNull(VersionCmp.compare("2.7-beta", "2.6"))
    assertNull(VersionCmp.compare("", "2.6"))
    assertNull(VersionCmp.compare("2..6", "2.6"))
    assertNull(VersionCmp.compare(null, "2.6"))
    assertFalse(VersionCmp.isNewer("abc", "2.6"))
    assertFalse(VersionCmp.isNewer("2.7", "dev"))      // not comparable: no popup
  }

  @Test fun releaseBuilds() {
    assertTrue(VersionCmp.isRelease("2.6"))
    assertTrue(VersionCmp.isRelease("2.3.3"))
    assertFalse(VersionCmp.isRelease("dev"))           // Android CI test build
    assertFalse(VersionCmp.isRelease("0.0"))           // iOS CI test build
    assertFalse(VersionCmp.isRelease(null))
  }

  @Test fun parseVersionJson() {
    val i = UpdateInfo.parse("""{"version":"2.7","notes":["新功能","修复"],"url":"https://wwbsj.lanzoup.com/b01i61dn1a","password":"0874","github":"https://github.com/Ckxkx/account-atlas/releases/latest","extra":{"x":1}}""")
    assertNotNull(i)
    assertEquals("2.7", i!!.version)
    assertEquals(listOf("新功能", "修复"), i.notes)
    assertEquals("0874", i.password)
    assertEquals("https://github.com/Ckxkx/account-atlas/releases/latest", i.github)
    // missing / unsafe fields fall back, a bad version means no update info at all
    val j = UpdateInfo.parse("""{"version":"v2.8","url":"http://evil.example","notes":"x"}""")!!
    assertEquals("2.8", j.version)
    assertEquals(UpdateInfo.DEFAULT_URL, j.url)
    assertEquals(UpdateInfo.DEFAULT_PASSWORD, j.password)
    assertTrue(j.notes.isEmpty())
    assertNull(UpdateInfo.parse("""{"version":"latest"}"""))
    assertNull(UpdateInfo.parse("<html>404</html>"))
    assertNull(UpdateInfo.parse(""))
  }

  @Test fun repoVersionJsonIsReadable() {
    // the file the website serves (repo root) must stay readable by the app
    val f = listOf("../../version.json", "../version.json", "version.json").map { java.io.File(it) }.firstOrNull { it.exists() } ?: return
    val i = UpdateInfo.parse(f.readText())
    assertNotNull("version.json in the repo is not readable by the app", i)
  }

  @Test fun onceADayOnlyAfterASuccessfulFetch() {
    val day = UpdateRules.DAY
    val t0 = 1_700_000_000_000L
    assertTrue(UpdateRules.due(true, "2.6", 0L, t0))                 // never checked
    assertFalse(UpdateRules.due(false, "2.6", 0L, t0))               // 自动检查更新 off
    assertFalse(UpdateRules.due(true, "dev", 0L, t0))                // test builds never check by themselves
    assertFalse(UpdateRules.due(true, "2.6", t0, t0 + day - 1))      // checked less than a day ago
    assertTrue(UpdateRules.due(true, "2.6", t0, t0 + day))
    assertTrue(UpdateRules.due(true, "2.6", t0 + day, t0))           // clock moved back: check
    // a failed check (offline / 404 / bad file) keeps the old date, so the next launch tries again
    assertEquals(t0, UpdateRules.lastAfter(false, t0, t0 + 2 * day))
    assertTrue(UpdateRules.due(true, "2.6", UpdateRules.lastAfter(false, 0L, t0), t0 + 1))
    // a successful one counts as the day's check
    assertEquals(t0 + 5, UpdateRules.lastAfter(true, t0, t0 + 5))
    assertFalse(UpdateRules.due(true, "2.6", UpdateRules.lastAfter(true, 0L, t0), t0 + 1))
  }

  @Test fun popupRespectsSkip() {
    val i = UpdateInfo.parse("""{"version":"2.7"}""")!!
    assertTrue(UpdateRules.popup(i, "2.6", null))
    assertFalse(UpdateRules.popup(i, "2.6", "2.7"))                  // 跳过此版本
    assertTrue(UpdateRules.popup(i, "2.6", "2.6.1"))
    assertFalse(UpdateRules.popup(i, "2.7", null))
  }

  @Test fun updatePageStates() {
    val v27 = UpdateInfo.parse("""{"version":"2.7","notes":["新功能"]}""")!!
    assertEquals(UpdateScreen.Failed, UpdateScreen.of(null, "2.6"))
    assertEquals(UpdateScreen.Newer(v27), UpdateScreen.of(v27, "2.6"))
    // up to date: the notes only when version.json is about the installed version
    assertEquals(UpdateScreen.Latest(listOf("新功能")), UpdateScreen.of(v27, "2.7"))
    assertEquals(UpdateScreen.Latest(listOf("新功能")), UpdateScreen.of(v27, "2.7.0"))
    assertEquals(UpdateScreen.Latest(emptyList()), UpdateScreen.of(v27, "2.8"))
    // CI test builds always see the latest release
    assertEquals(UpdateScreen.TestBuild(v27), UpdateScreen.of(v27, "dev"))
    assertEquals(UpdateScreen.Failed, UpdateScreen.of(null, "dev"))
  }
}
