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
    val c = SyncConf("webdav", "app-pass", "c2FsdA==", "a2V5", null, 0.0, Sync.DAV_PRESET, "me@example.com")
    val back = JSON.decodeFromString(SyncConf.serializer(), JSON.encodeToString(SyncConf.serializer(), c))
    assertEquals(c, back)
  }

  @Test fun davBase() {
    assertEquals("https://dav.jianguoyun.com/dav/", Sync.davBase(" https://dav.jianguoyun.com/dav "))
    assertEquals("https://dav.jianguoyun.com/dav/", Sync.davBase("dav.jianguoyun.com/dav/"))
    assertEquals("http://192.168.1.2:5005/", Sync.davBase("http://192.168.1.2:5005"))
    assertThrows(SyncError::class.java) { Sync.davBase("  ") }
    assertEquals("dav.jianguoyun.com", Sync.davHost(Sync.DAV_PRESET))
  }
}
