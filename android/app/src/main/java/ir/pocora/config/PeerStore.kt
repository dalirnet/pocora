package ir.pocora.config

import android.content.Context
import android.content.SharedPreferences
import ir.pocora.Role
import ir.pocora.model.Peer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// The paired phones: children on the parent's phone, parents on the child's phone.
class PeerStore(
    context: Context,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(ConfigStore.FILE_NAME, Context.MODE_PRIVATE)
    private val key = if (Role.current == Role.PARENT) KEY_CHILDREN else KEY_PARENTS
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Peer.serializer())

    companion object {
        private const val KEY_CHILDREN = "children"
        private const val KEY_PARENTS = "parents"
    }

    @Synchronized
    fun all(): List<Peer> {
        val stored = preferences.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString(serializer, stored)
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    fun find(fingerprint: String): Peer? = all().firstOrNull { it.fingerprint == fingerprint }

    // Pairing the same phone again replaces its old entry.
    @Synchronized
    fun save(peer: Peer) {
        val peers = all().filter { it.id != peer.id && it.fingerprint != peer.fingerprint } + peer
        preferences.edit().putString(key, json.encodeToString(serializer, peers)).apply()
    }

    @Synchronized
    fun remove(id: String) {
        val peers = all().filter { it.id != id }
        preferences.edit().putString(key, json.encodeToString(serializer, peers)).apply()
    }
}
