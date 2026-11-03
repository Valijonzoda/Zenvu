package com.worldtv.app.domain

data class CachedFavorite(
    val entry: MediaEntry,
    val addedAtMillis: Long,
) {
    val channelId: String get() = FavoriteIds.of(entry)

    fun withPlaybackFrom(other: CachedFavorite?): CachedFavorite {
        if (other == null) return this
        val extra = other.entry
        return copy(
            entry = entry.copy(
                streams = entry.streams.ifEmpty { extra.streams },
                youtube = entry.youtube.ifEmpty { extra.youtube },
                languages = entry.languages.ifEmpty { extra.languages },
                geoBlocked = entry.geoBlocked || extra.geoBlocked,
            ),
        )
    }
}

data class PendingWrite(
    val at: Long,
    val favorite: CachedFavorite?,
)

data class MergeOutcome(
    val entries: List<CachedFavorite>,
    val acked: Set<String>,
)

object FavoriteIds {
    fun of(entry: MediaEntry): String = of(entry.kind, entry.id)

    fun of(kind: ContentKind, nanoid: String): String = "${kind.directory}_${nanoid.trim()}"

    fun kind(directory: String): ContentKind? =
        ContentKind.entries.firstOrNull { it.directory == directory }
}

object FavoriteMerger {
    const val SKEW_MS = 5_000L

    fun mergeLegacy(
        account: List<CachedFavorite>,
        legacy: List<CachedFavorite>,
        now: Long,
    ): List<CachedFavorite> {
        val map = LinkedHashMap<String, CachedFavorite>()
        account.forEach { map[it.channelId] = it }
        legacy.forEach { item ->
            val current = map[item.channelId]
            if (current == null) {
                val stamped = if (item.addedAtMillis > 0L) item else item.copy(addedAtMillis = now)
                map[item.channelId] = stamped
            } else {
                map[item.channelId] = current.withPlaybackFrom(item)
            }
        }
        return map.values.sortedByDescending { it.addedAtMillis }
    }

    fun applySnapshot(
        local: List<CachedFavorite>,
        remote: List<CachedFavorite>,
        pending: Map<String, PendingWrite>,
        fromServer: Boolean,
    ): MergeOutcome {
        val localById = local.associateBy { it.channelId }
        val remoteById = remote.associateBy { it.channelId }
        val ids = LinkedHashSet<String>()
        ids.addAll(localById.keys)
        ids.addAll(remoteById.keys)
        ids.addAll(pending.keys)
        val acked = LinkedHashSet<String>()
        val chosen = ArrayList<CachedFavorite>()
        for (id in ids) {
            val write = pending[id]
            val remoteItem = remoteById[id]
            val localItem = localById[id]
            val pendingWins = write != null && wins(write, remoteItem)
            val item = when {
                pendingWins -> {
                    if (write.favorite == null && remoteItem == null) acked.add(id)
                    write.favorite?.withPlaybackFrom(remoteItem ?: localItem)
                }
                remoteItem != null -> remoteItem.withPlaybackFrom(localItem).also {
                    if (write != null) acked.add(id)
                }
                !fromServer && write == null -> localItem
                else -> null
            }
            if (item != null) chosen.add(item)
        }
        return MergeOutcome(chosen.sortedByDescending { it.addedAtMillis }, acked)
    }

    private fun wins(write: PendingWrite, remote: CachedFavorite?): Boolean {
        if (remote == null) return true
        if (remote.addedAtMillis <= 0L) return true
        return write.at > remote.addedAtMillis + SKEW_MS
    }
}
