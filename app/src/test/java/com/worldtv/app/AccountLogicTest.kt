package com.worldtv.app

import com.worldtv.app.data.AccountFavoritesRepository
import com.worldtv.app.data.CloudSnapshot
import com.worldtv.app.data.FavoriteCloud
import com.worldtv.app.data.FavoritesCodec
import com.worldtv.app.data.FileFavoriteCache
import com.worldtv.app.data.LegacyFavoriteStore
import com.worldtv.app.data.LocalFavoriteCache
import com.worldtv.app.domain.AuthError
import com.worldtv.app.domain.AuthInput
import com.worldtv.app.domain.AuthRepository
import com.worldtv.app.domain.AuthResult
import com.worldtv.app.domain.CachedFavorite
import com.worldtv.app.domain.ContentKind
import com.worldtv.app.domain.FavoriteIds
import com.worldtv.app.domain.FavoriteMerger
import com.worldtv.app.domain.FavoriteResult
import com.worldtv.app.domain.MediaEntry
import com.worldtv.app.domain.PendingWrite
import com.worldtv.app.domain.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AccountLogicTest {
    @Test
    fun guestSavesFavoriteOnDevice() = runTest {
        val local = MemoryCache()
        val guest = MemoryCache()
        val cloud = MemoryCloud()
        val repo = repository(user = null, local = local, guest = guest, cloud = cloud)
        val entry = sample("a")
        assertEquals(FavoriteResult.Changed, repo.toggle(entry))
        assertEquals(listOf(entry.key), repo.favorites.first().map { it.key })
        assertEquals(listOf(entry.key), guest.entries.value.map { it.entry.key })
        assertTrue(local.entries.value.isEmpty())
        assertTrue(cloud.upserts.isEmpty())
        assertEquals(FavoriteResult.Changed, repo.toggle(entry))
        assertTrue(repo.favorites.first().isEmpty())
    }

    @Test
    fun signInKeepsDeviceFavorites() = runTest {
        val users = MutableStateFlow<User?>(null)
        val guest = MemoryCache()
        val local = MemoryCache()
        val cloud = MemoryCloud()
        val repo = repository(users = users, local = local, guest = guest, cloud = cloud, now = 4_000L)
        assertEquals(FavoriteResult.Changed, repo.toggle(sample("a")))
        repo.start(backgroundScope)
        runCurrent()
        users.value = user()
        runCurrent()
        assertEquals(listOf("tv_a"), cloud.upserts.map { it.channelId })
        assertEquals(listOf("tv_a"), local.entries.value.map { it.channelId })
        assertEquals(listOf("tv_a"), repo.favorites.first().map { FavoriteIds.of(it) })
    }

    @Test
    fun signedInUserCanAddAndRemove() = runTest {
        val cloud = MemoryCloud()
        val repo = repository(user = user(), cloud = cloud, now = 5_000L)
        val entry = sample("a")
        assertEquals(FavoriteResult.Changed, repo.add(entry))
        assertEquals(listOf(entry.key), repo.favorites.first().map { it.key })
        assertEquals(1, cloud.upserts.size)
        assertEquals(FavoriteResult.Changed, repo.toggle(entry))
        assertTrue(repo.favorites.first().isEmpty())
        assertEquals(listOf(FavoriteIds.of(entry)), cloud.deletes)
    }

    @Test
    fun legacyMergeUploadsOnlyNewKeys() = runTest {
        val local = MemoryCache()
        local.write(listOf(cached(sample("a"), streams = listOf("https://cdn/a"), at = 100)))
        val legacy = MemoryLegacy(
            listOf(
                cached(sample("a")),
                cached(sample("b", kind = ContentKind.RADIO)),
                cached(sample("a", kind = ContentKind.RADIO)),
            ),
        )
        val cloud = MemoryCloud()
        val repo = repository(user = user(), local = local, legacy = legacy, cloud = cloud, now = 9_000L)
        repo.acceptLegacy()
        assertEquals(setOf("radio_b", "radio_a"), cloud.upserts.map { it.channelId }.toSet())
        assertEquals(setOf("tv_a", "radio_b", "radio_a"), local.entries.value.map { it.channelId }.toSet())
        assertEquals(listOf("https://cdn/a"), local.entries.value.first { it.channelId == "tv_a" }.entry.streams)
        assertTrue(legacy.read().isEmpty())
        assertTrue(repo.migrationOffer.value.isEmpty())
    }

    @Test
    fun serverSnapshotDropsLocalItemUnlessPendingIsNewer() {
        val local = listOf(cached(sample("a"), at = 1_000))
        val dropped = FavoriteMerger.applySnapshot(local, emptyList(), emptyMap(), fromServer = true)
        assertTrue(dropped.entries.isEmpty())
        val keptOffline = FavoriteMerger.applySnapshot(local, emptyList(), emptyMap(), fromServer = false)
        assertEquals(listOf("tv_a"), keptOffline.entries.map { it.channelId })
        val pending = mapOf("tv_a" to PendingWrite(10_000L, cached(sample("a", name = "New"), at = 10_000)))
        val olderRemote = FavoriteMerger.applySnapshot(
            local,
            listOf(cached(sample("a", name = "Old"), at = 1_000)),
            pending,
            fromServer = true,
        )
        assertEquals("New", olderRemote.entries.single().entry.name)
        assertTrue(olderRemote.acked.isEmpty())
        val acked = FavoriteMerger.applySnapshot(
            local,
            listOf(cached(sample("a", name = "Remote"), streams = emptyList(), at = 10_000)),
            pending,
            fromServer = true,
        )
        assertEquals("Remote", acked.entries.single().entry.name)
        assertEquals(setOf("tv_a"), acked.acked)
    }

    @Test
    fun signOutClearsCachedFavorites() = runTest {
        val users = MutableStateFlow<User?>(user())
        val local = MemoryCache()
        local.write(listOf(cached(sample("a"), at = 10)))
        val repo = repository(users = users, local = local)
        repo.start(backgroundScope)
        runCurrent()
        users.value = null
        runCurrent()
        assertTrue(local.entries.value.isEmpty())
        assertTrue(repo.favorites.first().isEmpty())
    }

    @Test
    fun existingFileBecomesLegacyOnce() {
        val dir = File.createTempFile("fav", "dir")
        dir.delete()
        dir.mkdirs()
        val file = File(dir, "favorites.json")
        val entry = sample("a")
        file.writeText(FavoritesCodec.encode(listOf(entry)))
        val first = FileFavoriteCache(file)
        assertTrue(first.entries.value.isEmpty())
        assertEquals(listOf(entry), first.read().map { it.entry })
        file.writeText(FavoritesCodec.encode(listOf(entry, sample("b"))))
        val second = FileFavoriteCache(file)
        assertEquals(2, second.entries.value.size)
        assertEquals(1, second.read().size)
    }

    @Test
    fun authRejectsBadInputAndMapsFirebaseCodes() = runTest {
        val auth = ScriptedAuth()
        assertEquals(AuthError.INVALID_EMAIL, (auth.signInEmail("not-an-email", "secret123") as AuthResult.Failure).error)
        assertEquals(AuthError.WEAK_PASSWORD, (auth.signUpEmail("a@b.co", "short") as AuthResult.Failure).error)
        assertTrue(auth.currentUser.value == null)
        auth.failure = AuthError.WRONG_PASSWORD
        assertEquals(AuthError.WRONG_PASSWORD, (auth.signInEmail("a@b.co", "secret123") as AuthResult.Failure).error)
        auth.failure = AuthError.EMAIL_IN_USE
        assertEquals(AuthError.EMAIL_IN_USE, (auth.signUpEmail("a@b.co", "secret123") as AuthResult.Failure).error)
        auth.failure = AuthError.NETWORK
        assertEquals(AuthError.NETWORK, (auth.sendPasswordReset("a@b.co") as AuthResult.Failure).error)
        auth.failure = null
        assertTrue(auth.signUpEmail("a@b.co", "secret123") is AuthResult.Success)
        auth.failure = AuthError.RECENT_LOGIN_REQUIRED
        assertEquals(AuthError.RECENT_LOGIN_REQUIRED, (auth.deleteAccount(null) as AuthResult.Failure).error)
        assertEquals(AuthError.WRONG_PASSWORD, AuthInput.fromCode("ERROR_WRONG_PASSWORD"))
        assertEquals(AuthError.EMAIL_IN_USE, AuthInput.fromCode("email-already-in-use"))
        assertEquals(AuthError.NETWORK, AuthInput.fromCode("ERROR_NETWORK_REQUEST_FAILED"))
    }

    private fun repository(
        user: User? = null,
        users: MutableStateFlow<User?> = MutableStateFlow(user),
        local: MemoryCache = MemoryCache(),
        legacy: MemoryLegacy = MemoryLegacy(),
        guest: MemoryCache = MemoryCache(),
        cloud: MemoryCloud = MemoryCloud(),
        now: Long = 1_000L,
    ) = AccountFavoritesRepository(local, legacy, guest, cloud, ScriptedAuth(users), clock = { now })

    private fun user() = User("uid", "a@b.co", null, passwordProvider = true)

    private fun sample(
        id: String,
        kind: ContentKind = ContentKind.TV,
        name: String = "Name $id",
        streams: List<String> = listOf("https://example.com/$id"),
    ) = MediaEntry(id, kind, name, "us", listOf("eng"), false, streams, emptyList())

    private fun cached(
        entry: MediaEntry,
        streams: List<String> = entry.streams,
        at: Long = 0L,
    ) = CachedFavorite(entry.copy(streams = streams), at)
}

private class MemoryCache : LocalFavoriteCache {
    private val state = MutableStateFlow<List<CachedFavorite>>(emptyList())
    override val entries: StateFlow<List<CachedFavorite>> = state
    override suspend fun write(entries: List<CachedFavorite>) {
        state.value = entries
    }
    override suspend fun clear() {
        state.value = emptyList()
    }
}

private class MemoryLegacy(initial: List<CachedFavorite> = emptyList()) : LegacyFavoriteStore {
    private val items = initial.toMutableList()
    override fun read(): List<CachedFavorite> = items.toList()
    override fun clearLegacy() {
        items.clear()
    }
}

private class MemoryCloud : FavoriteCloud {
    private val snapshots = MutableStateFlow(CloudSnapshot(emptyList(), fromServer = false))
    val upserts = mutableListOf<CachedFavorite>()
    val deletes = mutableListOf<String>()
    override fun observe(uid: String): Flow<CloudSnapshot> = snapshots
    override suspend fun upsert(uid: String, favorite: CachedFavorite) {
        upserts += favorite
    }
    override suspend fun delete(uid: String, channelId: String) {
        deletes += channelId
    }
    override suspend fun deleteAll(uid: String) = Unit
}

private class ScriptedAuth(
    override val currentUser: MutableStateFlow<User?> = MutableStateFlow(null),
) : AuthRepository {
    override val accountsEnabled = true
    override val googleSignInAvailable = true
    var failure: AuthError? = null

    override suspend fun signInEmail(email: String, password: String) = authenticate(email, password, false)

    override suspend fun signUpEmail(email: String, password: String) = authenticate(email, password, true)

    override suspend fun signInGoogle(): AuthResult {
        failure?.let { return AuthResult.Failure(it) }
        currentUser.value = User("google", "g@example.com", "G", passwordProvider = false)
        return AuthResult.Success
    }

    override suspend fun sendPasswordReset(email: String): AuthResult {
        AuthInput.validate(email, null, false)?.let { return AuthResult.Failure(it) }
        failure?.let { return AuthResult.Failure(it) }
        return AuthResult.Success
    }

    override suspend fun signOut() {
        currentUser.value = null
    }

    override suspend fun deleteAccount(password: String?): AuthResult {
        failure?.let { return AuthResult.Failure(it) }
        currentUser.value = null
        return AuthResult.Success
    }

    private fun authenticate(email: String, password: String, register: Boolean): AuthResult {
        AuthInput.validate(email, password, register)?.let { return AuthResult.Failure(it) }
        failure?.let { return AuthResult.Failure(it) }
        currentUser.value = User("uid", email.trim(), null, passwordProvider = true)
        return AuthResult.Success
    }
}
