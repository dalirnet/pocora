package ir.pocora.parent

import android.content.Context
import ir.pocora.config.JsonFile
import ir.pocora.model.Contact
import ir.pocora.model.Snapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

// What the parent's phone keeps about each child, one JSON file per child and kind.

// The latest snapshot of each child, one file per child. Each sync replaces it whole.
class SnapshotStore(
    private val context: Context,
) {
    private fun file(childId: String) = JsonFile(context, "snapshot-$childId.json", Snapshot.serializer())

    fun read(childId: String): Snapshot? = file(childId).read()

    fun write(snapshot: Snapshot) = file(snapshot.childId).write(snapshot)

    fun delete(childId: String) = file(childId).delete()
}

// Which of a child's alerts were already notified, and which the parent has seen on the Events tab. One file per child.
class AlertMarks(
    private val context: Context,
) {
    @Serializable
    data class Marks(
        val notified: Set<String> = emptySet(),
        val seen: Set<String> = emptySet(),
    )

    private fun file(childId: String) = JsonFile(context, "alerts-$childId.json", Marks.serializer())

    fun read(childId: String): Marks = file(childId).read() ?: Marks()

    // Ids no longer in the snapshot are dropped, so the file never grows.
    fun notified(
        childId: String,
        ids: Collection<String>,
        current: Set<String>,
    ) {
        file(childId).update(Marks()) { Marks((it.notified + ids).intersect(current), it.seen.intersect(current)) }
    }

    fun seen(
        childId: String,
        ids: Collection<String>,
    ) {
        file(childId).update(Marks()) { it.copy(seen = it.seen + ids) }
    }

    fun delete(childId: String) = file(childId).delete()
}

// When each child's phone was in touch, for the last 7 days. One file per child.
class ContactLog(
    private val context: Context,
) {
    companion object {
        // Syncs come every minute. Three missed in a row is a gap.
        const val GAP_MILLISECONDS = 3 * 60_000L
    }

    private fun file(childId: String) = JsonFile(context, "contact-$childId.json", ListSerializer(Contact.serializer()))

    fun all(childId: String): List<Contact> = file(childId).read() ?: emptyList()

    fun lastSeen(childId: String): Long? = all(childId).lastOrNull()?.end

    fun isOnline(
        childId: String,
        now: Long,
    ): Boolean = lastSeen(childId)?.let { now - it < GAP_MILLISECONDS } ?: false

    // Notes a sync. Returns true when it ends a gap, so the child has just come home.
    fun ping(
        childId: String,
        now: Long,
    ): Boolean {
        var afterGap = false
        file(childId).update(emptyList()) { contacts ->
            val last = contacts.lastOrNull()
            val oldest = Snapshot.keptSince(now)
            val kept = contacts.filter { it.end >= oldest }
            if (last != null && now - last.end < GAP_MILLISECONDS) {
                kept.dropLast(1) + last.copy(end = now)
            } else {
                afterGap = true
                kept + Contact(now, now)
            }
        }
        return afterGap
    }

    fun delete(childId: String) = file(childId).delete()
}
