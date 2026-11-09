package com.worldtv.app.data

import com.worldtv.app.domain.AuthRepository
import com.worldtv.app.domain.CachedFavorite
import com.worldtv.app.domain.FavoriteIds
import com.worldtv.app.domain.FavoriteMerger
import com.worldtv.app.domain.FavoriteResult
import com.worldtv.app.domain.FavoritesRepository
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.PendingWrite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CloudSnapshot(
    val entries: List<CachedFavorite>,
    val fromServer: Boolean,
)

interface FavoriteCloud {
    fun observe(uid: String): Flow<CloudSnapshot>

    suspend fun upsert(uid: String, favorite: CachedFavorite)

    suspend fun delete(uid: String, channelId: String)

    suspend fun deleteAll(uid: String)
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountFavoritesRepository(
    private val local: LocalFavoriteCache,
    private val legacy: LegacyFavoriteStore,
    private val guest: LocalFavoriteCache,
    private val cloud: FavoriteCloud,
    private val auth: AuthRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : FavoritesRepository {
    private val mutex = Mutex()
    private val pending = LinkedHashMap<String, PendingWrite>()
    private val migration = MutableStateFlow<List<MediaEntry>>(emptyList())
    private var dismissed = false

    val migrationOffer: StateFlow<List<MediaEntry>> = migration.asStateFlow()

    override val favorites: Flow<List<MediaEntry>> = auth.currentUser.flatMapLatest { user ->
        val source = if (user == null) guest.entries else local.entries
        source.map { items -> items.map { it.entry } }
    }

    fun start(scope: CoroutineScope) {
        scope.launchCollecting()
    }

    override suspend fun toggle(entry: MediaEntry): FavoriteResult {
        val uid = auth.currentUser.value?.uid
        if (uid == null) {
            val id = FavoriteIds.of(entry)
            mirrorGuest(entry, present = guest.entries.value.none { it.channelId == id })
            return FavoriteResult.Changed
        }
        val op = mutex.withLock {
            val id = FavoriteIds.of(entry)
            val current = local.entries.value
            if (current.any { it.channelId == id }) {
                pending[id] = PendingWrite(clock(), null)
                local.write(current.filterNot { it.channelId == id })
                mirrorGuest(entry, present = false)
                CloudOp.Delete(uid, id)
            } else {
                val favorite = CachedFavorite(entry, clock())
                pending[id] = PendingWrite(favorite.addedAtMillis, favorite)
                local.write(listOf(favorite) + current)
                mirrorGuest(entry, present = true)
                CloudOp.Upsert(uid, favorite)
            }
        }
        push(op)
        return FavoriteResult.Changed
    }

    override suspend fun add(entry: MediaEntry): FavoriteResult {
        val uid = auth.currentUser.value?.uid
        if (uid == null) {
            mirrorGuest(entry, present = true)
            return FavoriteResult.Changed
        }
        val op = mutex.withLock {
            val id = FavoriteIds.of(entry)
            val current = local.entries.value
            if (current.any { it.channelId == id }) return@withLock null
            val favorite = CachedFavorite(entry, clock())
            pending[id] = PendingWrite(favorite.addedAtMillis, favorite)
            local.write(listOf(favorite) + current)
            mirrorGuest(entry, present = true)
            CloudOp.Upsert(uid, favorite)
        } ?: return FavoriteResult.Changed
        push(op)
        return FavoriteResult.Changed
    }

    override suspend fun isFavorite(key: String): Boolean {
        val source = if (auth.currentUser.value == null) guest.entries.value else local.entries.value
        return source.any { it.entry.key == key }
    }

    fun dismissLegacy() {
        dismissed = true
        migration.value = emptyList()
    }

    suspend fun acceptLegacy() {
        val uid = auth.currentUser.value?.uid ?: return
        val saved = legacy.read()
        if (saved.isEmpty()) {
            migration.value = emptyList()
            return
        }
        val existing = local.entries.value.map { it.channelId }.toSet()
        val added = FavoriteMerger.mergeLegacy(emptyList(), saved, clock()).filter { it.channelId !in existing }
        mutex.withLock {
            added.forEach { pending[it.channelId] = PendingWrite(it.addedAtMillis, it) }
        }
        var failed = false
        for (item in added) {
            try {
                cloud.upsert(uid, item)
            } catch (error: Exception) {
                failed = true
                accountLog("favorite sync failed: ${error.javaClass.simpleName}")
            }
        }
        if (failed) return
        mutex.withLock {
            local.write(FavoriteMerger.mergeLegacy(local.entries.value, saved, clock()))
        }
        legacy.clearLegacy()
        dismissed = true
        migration.value = emptyList()
    }

    private fun CoroutineScope.launchCollecting() {
        launch {
            var previous: String? = null
            auth.currentUser.collectLatest { user ->
                val uid = user?.uid
                if (previous != null && uid == null) {
                    mutex.withLock {
                        pending.clear()
                        local.clear()
                    }
                    dismissed = false
                    migration.value = emptyList()
                }
                previous = uid
                if (uid == null) return@collectLatest
                mergeGuest(uid)
                if (!dismissed) {
                    val saved = legacy.read()
                    migration.value = saved.map { it.entry }
                }
                cloud.observe(uid).collect { snapshot ->
                    apply(uid, snapshot)
                }
            }
        }
    }

    private suspend fun mirrorGuest(entry: MediaEntry, present: Boolean) {
        val id = FavoriteIds.of(entry)
        val current = guest.entries.value
        val next = if (present) {
            if (current.any { it.channelId == id }) {
                current
            } else {
                listOf(CachedFavorite(entry, clock())) + current
            }
        } else {
            current.filterNot { it.channelId == id }
        }
        if (next != current) guest.write(next)
    }

    private suspend fun mergeGuest(uid: String) {
        val saved = guest.entries.value
        if (saved.isEmpty()) return
        val existing = local.entries.value.map { it.channelId }.toSet()
        val added = saved.filter { it.channelId !in existing }
        mutex.withLock {
            added.forEach { item ->
                pending[item.channelId] = PendingWrite(item.addedAtMillis.coerceAtLeast(1L), item)
            }
        }
        for (item in added) {
            try {
                cloud.upsert(uid, item)
            } catch (error: Exception) {
                accountLog("favorite sync failed: ${error.javaClass.simpleName}")
            }
        }
        mutex.withLock {
            if (auth.currentUser.value?.uid != uid) return
            local.write(FavoriteMerger.mergeLegacy(local.entries.value, saved, clock()))
        }
    }

    private suspend fun apply(uid: String, snapshot: CloudSnapshot) {
        mutex.withLock {
            if (auth.currentUser.value?.uid != uid) return
            val outcome = FavoriteMerger.applySnapshot(
                local = local.entries.value,
                remote = snapshot.entries,
                pending = pending.toMap(),
                fromServer = snapshot.fromServer,
            )
            outcome.acked.forEach { pending.remove(it) }
            local.write(outcome.entries)
        }
    }

    private suspend fun push(op: CloudOp) {
        try {
            when (op) {
                is CloudOp.Upsert -> cloud.upsert(op.uid, op.favorite)
                is CloudOp.Delete -> cloud.delete(op.uid, op.channelId)
            }
        } catch (error: Exception) {
            accountLog("favorite sync failed: ${error.javaClass.simpleName}")
        }
    }

    private sealed interface CloudOp {
        data class Upsert(val uid: String, val favorite: CachedFavorite) : CloudOp
        data class Delete(val uid: String, val channelId: String) : CloudOp
    }
}

internal fun accountLog(message: String) {
    runCatching { android.util.Log.i("Zenvu", message) }
}
