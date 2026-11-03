package com.worldtv.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.worldtv.app.domain.CachedFavorite
import com.worldtv.app.domain.FavoriteIds
import com.worldtv.app.domain.MediaEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await

class FirestoreFavoriteCloud : FavoriteCloud {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance().also { firestore ->
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
    }

    override fun observe(uid: String): Flow<CloudSnapshot> = callbackFlow {
        val registration = db.collection("users").document(uid).collection("favorites")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    accountLog("favorites listen failed: ${error.javaClass.simpleName}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { document ->
                    val kind = FavoriteIds.kind(document.getString("kind").orEmpty()) ?: return@mapNotNull null
                    val nanoid = document.getString("nanoid")?.trim().orEmpty()
                    val name = document.getString("name")?.trim().orEmpty()
                    if (nanoid.isEmpty() || name.isEmpty()) return@mapNotNull null
                    CachedFavorite(
                        entry = MediaEntry(
                            id = nanoid,
                            kind = kind,
                            name = name,
                            countryCode = document.getString("country")?.trim().orEmpty().lowercase(),
                            languages = emptyList(),
                            geoBlocked = false,
                            streams = emptyList(),
                            youtube = emptyList(),
                        ),
                        addedAtMillis = document.getTimestamp("addedAt")?.toDate()?.time ?: 0L,
                    )
                }
                trySend(CloudSnapshot(items, fromServer = !snapshot.metadata.isFromCache))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun upsert(uid: String, favorite: CachedFavorite) {
        val entry = favorite.entry
        db.collection("users").document(uid).collection("favorites")
            .document(FavoriteIds.of(entry))
            .set(
                mapOf(
                    "kind" to entry.kind.directory,
                    "nanoid" to entry.id,
                    "name" to entry.name,
                    "country" to entry.countryCode,
                    "addedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun delete(uid: String, channelId: String) {
        db.collection("users").document(uid).collection("favorites").document(channelId).delete().await()
    }

    override suspend fun deleteAll(uid: String) {
        val user = db.collection("users").document(uid)
        val favorites = user.collection("favorites")
        while (true) {
            val snap = favorites.limit(400).get().await()
            if (snap.isEmpty) break
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            if (snap.size() < 400) break
        }
        user.delete().await()
    }
}

class UnavailableFavoriteCloud : FavoriteCloud {
    override fun observe(uid: String): Flow<CloudSnapshot> = emptyFlow()

    override suspend fun upsert(uid: String, favorite: CachedFavorite) = Unit

    override suspend fun delete(uid: String, channelId: String) = Unit

    override suspend fun deleteAll(uid: String) = Unit
}
