package dev.huichen.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoriesTest {
  private fun acc(id: String, cat: String) = Account(id, "App $id", category = cat, updatedAt = 1.0)

  @Test fun noListMeansTheDefaultsWithOtherLast() {
    val l = catList(AtlasData())
    assertEquals(DEFAULT_CATS, l)
    assertEquals("生活", l[l.indexOf("出行") + 1])
    assertEquals(OTHER_CAT, l.last())
  }

  @Test fun usedButMissingCategoriesAreAppendedBeforeOther() {
    val d = AtlasData(accounts = listOf(acc("a", "学习"), acc("b", "AI"), acc("c", "其他")), categories = listOf("其他", "AI", "社交", "AI"))
    assertEquals(listOf("AI", "社交", "学习", OTHER_CAT), catList(d))
  }

  @Test fun mergeTakesTheListChangedLast() {
    val a = AtlasData(categories = listOf("社交", "工作", "其他"), categoriesAt = 200.0)
    val b = AtlasData(categories = listOf("社交", "其他"), categoriesAt = 100.0)
    assertEquals(listOf("社交", "工作", "其他"), Merge.merge(a, b).categories)
    assertEquals(listOf("社交", "工作", "其他"), Merge.merge(b, a).categories)
    assertEquals(200.0, Merge.merge(b, a).categoriesAt)
    // an older client drops the keys: the list that has a stamp wins
    assertEquals(listOf("社交", "其他"), Merge.merge(AtlasData(), b).categories)
    assertNull(Merge.merge(AtlasData(), AtlasData()).categories)
    // a tie gives the same answer on both sides
    val x = AtlasData(categories = listOf("甲", "其他"), categoriesAt = 5.0)
    val y = AtlasData(categories = listOf("乙", "其他"), categoriesAt = 5.0)
    assertEquals(Merge.merge(x, y).categories, Merge.merge(y, x).categories)
  }

  @Test fun listSurvivesEncodeAndOldFilesStillRead() {
    val d = AtlasData(categories = listOf("工作", "其他"), categoriesAt = 1700000000000.0)
    val back = decodeAtlas(encodeAtlas(d))
    assertEquals(d.categories, back.categories)
    assertEquals(d.categoriesAt, back.categoriesAt)
    assertNull(decodeAtlas("""{"identities":[],"accounts":[],"deleted":{}}""").categories)
  }

  @Test fun names() {
    val l = catList(AtlasData())
    assertEquals("分类名不能为空", catProblem("", l))
    assertEquals("分类名最多 8 个字", catProblem("一二三四五六七八九", l))
    assertEquals("这个名字不能用", catProblem("全部", l))
    assertEquals("已经有这个分类了", catProblem("ai", l))
    assertNull(catProblem("AI", l, except = "AI"))
    assertNull(catProblem("学习", l))
  }
}
