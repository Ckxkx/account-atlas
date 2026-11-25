package dev.huichen.atlas

// 关系图: what the map draws, derived from the data alone (no Compose), so a delete can never leave a node,
// a thread or the focus pointing at something that is gone. Unit-tested in MapModelTest.

/** Deleting an identity: it goes, every App forgets it and every 微信 / QQ bound to it loses that binding.
 *  The one delete path (the detail sheet, whether opened from the 关系图, the wallet or a list). */
fun AtlasData.withoutIdentity(id: String): AtlasData = copy(
  identities = identities.filter { it.id != id }.map { if (id in it.bindIds) it.withBinds(it.bindIds - id) else it },
  accounts = accounts.map { a -> if (id in a.identityIds) a.copy(identityIds = a.identityIds.filter { it != id }) else a },
)

/** Deleting an App / website record. */
fun AtlasData.withoutAccount(id: String): AtlasData = copy(accounts = accounts.filter { it.id != id })

class MapModel(data: AtlasData) {
  /** one node per distinct email / phone (and 微信 / QQ), in groups: 邮箱, 手机号, then the rest */
  val ids: List<Identity>
  /** node id -> the identity ids it stands for (duplicates that weren't merged share one node) */
  val members: Map<String, List<String>>
  /** apps, ordered by the first node they belong to so threads cross as little as possible */
  val apps: List<Account>
  /** threads: node id -> app id (only App ↔ identity; a 微信 / QQ binding is a tag on its node) */
  val threads: List<Pair<String, String>>

  init {
    val all = data.identities
    val ordered = all.filter { it.type == "email" } + all.filter { it.type == "phone" } + all.filter { it.type != "email" && it.type != "phone" }
    val m = LinkedHashMap<String, MutableList<String>>(); val keyNode = HashMap<String, String>()
    for (i in ordered) { val k = idKey(i) ?: i.id; val n = keyNode[k]; if (n != null) m[n]!!.add(i.id) else { keyNode[k] = i.id; m[i.id] = mutableListOf(i.id) } }
    members = m
    ids = ordered.filter { it.id in m }
    val rank = HashMap<String, Int>().apply { ids.forEachIndexed { k, i -> m[i.id]!!.forEach { put(it, k) } } }
    apps = data.accounts.sortedWith(Comparator { x, y ->
      val a = x.identityIds.mapNotNull { rank[it] }.minOrNull() ?: 999; val b = y.identityIds.mapNotNull { rank[it] }.minOrNull() ?: 999
      if (a != b) a - b else nameOrder.compare(x.name, y.name)
    })
    threads = ids.flatMap { i -> apps.filter { inNode(it, i.id) }.map { i.id to it.id } }
  }

  fun inNode(a: Account, node: String): Boolean { val g = members[node] ?: return false; return a.identityIds.any { it in g } }

  /** the focused node after a change: kept while it is a node; an identity that now sits inside another node
   *  (a duplicate) moves to that node; anything deleted means no focus (never a focus on something that is gone) */
  fun resolve(focus: String?): String? {
    if (focus == null || focus in members) return focus
    return members.entries.firstOrNull { focus in it.value }?.key
  }
}
