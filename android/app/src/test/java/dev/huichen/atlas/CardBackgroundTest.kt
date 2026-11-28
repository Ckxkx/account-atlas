package dev.huichen.atlas

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 卡片背景 (docs/card-backgrounds.md): bg / pic keys, picture ids, the WebDAV .bin envelope */
class CardBackgroundTest {
  private val pid = "0123456789abcdef0123456789abcdef"

  @Test fun keysWrittenOnlyWhenSet() {
    val plain = encodeAtlas(AtlasData(identities = listOf(Identity("a", value = "x@y.z"))))
    assertFalse("\"bg\"" in plain); assertFalse("\"pic\"" in plain)
    val d = AtlasData(identities = listOf(Identity("a", value = "x@y.z", bg = "b1"), Identity("b", value = "q@y.z", pic = pid)))
    val back = decodeAtlas(encodeAtlas(d))
    assertEquals("b1", back.identities[0].bg); assertNull(back.identities[0].pic)
    assertEquals(pid, back.identities[1].pic); assertNull(back.identities[1].bg)
  }

  @Test fun blankKeysReadAsNone() {
    val d = decodeAtlas("""{"identities":[{"id":"a","type":"email","value":"x@y.z","bg":"","pic":" "}],"accounts":[]}""")
    assertNull(d.identities[0].bg); assertNull(d.identities[0].pic)
  }

  @Test fun mergeKeepsNewestBackground() {
    val a = AtlasData(identities = listOf(Identity("a", value = "x@y.z", updatedAt = 1.0)))
    val b = AtlasData(identities = listOf(Identity("a", value = "x@y.z", updatedAt = 2.0, pic = pid)))
    assertEquals(pid, Merge.merge(a, b).identities[0].pic)
    assertEquals(pid, Merge.merge(b, a).identities[0].pic)
    // a background change alone changes the signature, so it is written to the cloud
    assertTrue(Merge.sig(a) != Merge.sig(a.copy(identities = listOf(a.identities[0].copy(bg = "b1")))))
  }

  @Test fun duplicateMergeCarriesBackground() {
    val d = AtlasData(identities = listOf(Identity("a", value = "x@y.z"), Identity("b", value = "X@y.z", bg = "b2")))
    val (m, n) = Migrate24.merge(d)
    assertEquals(1, n); assertEquals("b2", m.identities.single().bg)
  }

  @Test fun picIds() {
    val id = picId("hello".toByteArray())
    assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e", id)   // sha256("hello"), first 32 hex
    assertTrue(validPicId(id)); assertFalse(validPicId("../x")); assertFalse(validPicId(id.uppercase())); assertFalse(validPicId(null))
    val d = AtlasData(identities = listOf(Identity("a", pic = id), Identity("b", pic = id), Identity("c", pic = "bad"), Identity("d")))
    assertEquals(setOf(id), picIds(d))
  }

  @Test fun binEnvelope() {
    val key = AtlasCrypto.randomBytes(32)
    val jpeg = ByteArray(5000) { (it * 7).toByte() }
    val blob = AtlasCrypto.sealBytes(jpeg, key)
    assertEquals(12 + jpeg.size + 16, blob.size)
    assertArrayEquals(jpeg, AtlasCrypto.openBytes(blob, key))
    assertThrows(javax.crypto.AEADBadTagException::class.java) { AtlasCrypto.openBytes(blob, AtlasCrypto.randomBytes(32)) }
    assertThrows(AtlasCrypto.BadFormat::class.java) { AtlasCrypto.openBytes(ByteArray(20), key) }
  }
}
