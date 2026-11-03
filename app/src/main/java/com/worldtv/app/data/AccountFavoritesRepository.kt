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
import kotlinx.coroutines.flow.flowOf
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
        if (user == null) {
            flowOf(emptyList())
        } else {
            local.entries.map { items -> items.map { it.entry } }
        }
    }

    fun start(scope: CoroutineScope) {
        scope.launchCollecting()
    }

    override suspend fun toggle(entry: MediaEntry): FavoriteResult {
        val op = mutex.withLock {
            val uid = auth.currentUser.value?.uid ?: return FavoriteResult.AuthRequired
            val id = FavoriteIds.of(entry)
            val current = local.entries.value
            if (current.any { it.channelId == id }) {
                pending[id] = PendingWrite(clock(), null)
                local.write(current.filterNot { it.channelId == id })
                CloudOp.Delete(uid, id)
            } else {
                val favorite = CachedFavorite(entry, clock())
                pending[id] = PendingWrite(favorite.addedAtMillis, favorite)
                local.write(listOf(favorite) + current)
                CloudOp.Upsert(uid, favorite)
            }
        }
        push(op)
        return FavoriteResult.Changed
    }

    override suspend fun add(entry: MediaEntry): FavoriteResult {
        val op = mutex.withLock {
            val uid = auth.currentUser.value?.uid ?: return FavoriteResult.AuthRequired
            val id = FavoriteIds.of(entry)
            val current = local.entries.value
            if (current.any { it.channelId == id }) return@withLock null
            val favorite = CachedFavorite(entry, clock())
            pending[id] = PendingWrite(favorite.addedAtMillis, favorite)
            local.write(listOf(favorite) + current)
            CloudOp.Upsert(uid, favorite)
        } ?: return FavoriteResult.Changed
        push(op)
        return FavoriteResult.Changed
    }

    override suspend fun isFavorite(key: String): Boolean {
        if (auth.currentUser.value == null) return false
        return local.entries.value.any { it.entry.key == key }
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
