package dev.huichen.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** WebDAV 同步: older gist configs still decode, and the server address is normalized */
class WebDavSyncTest {
  @Test fun oldGistConfDecodes() {
    val old = """{"provider":"gitee","token":"t","salt":"c2FsdA==","key":"a2V5","gistId":"abc","at":1.0}"""
    val c = JSON.decodeFromString(SyncConf.serializer(), old)
    assertEquals("abc", c.gistId)
    assertNull(c.url); assertNull(c.user)
  }

  @Test fun davConfRoundTrips() {
    val c = SyncConf("webdav", "app-pass", "c2FsdA==", "a2V5", null, 0.0, Sync.DAV_JIANGUOYUN, "me@example.com")
    val back = JSON.decodeFromString(SyncConf.serializer(), JSON.encodeToString(SyncConf.serializer(), c))
    assertEquals(c, back)
  }

  @Test fun davBase() {
    assertEquals("https://dav.jianguoyun.com/dav/", Sync.davBase(" https://dav.jianguoyun.com/dav "))
    assertEquals("https://dav.jianguoyun.com/dav/", Sync.davBase("dav.jianguoyun.com/dav/"))
    assertEquals("http://192.168.1.2:5005/", Sync.davBase("http://192.168.1.2:5005"))
    assertThrows(SyncError::class.java) { Sync.davBase("  ") }
    assertEquals("dav.jianguoyun.com", Sync.davHost(Sync.DAV_JIANGUOYUN))
    assertEquals("http://nas.local:5005/", Sync.davBase("http://nas.local:5005"))
    assertEquals("http://[fd00::2]:5005/", Sync.davBase("http://[fd00::2]:5005/"))
    assertEquals("https://app.koofr.net/dav/Koofr/", Sync.davBase("https://app.koofr.net/dav/Koofr"))
    assertThrows(SyncError::class.java) { Sync.davBase("http://dav.example.com/dav/") }   // cleartext to a public host
    assertThrows(SyncError::class.java) { Sync.davBase("https://NAS地址:5006/") }          // template left as is
  }

  @Test fun davPresets() {
    assertEquals(listOf("坚果云", "群晖 NAS", "Nextcloud", "AList", "InfiniCLOUD", "Koofr", "pCloud", "自定义"), Sync.DAV_PRESETS.map { it.name })
    Sync.DAV_PRESETS.filter { !it.template && it.url.isNotEmpty() }.forEach { Sync.davBase(it.url) }
    Sync.DAV_PRESETS.filter { it.template }.forEach { p -> assertThrows(SyncError::class.java) { Sync.davBase(p.url) } }
  }
}
