package ir.pocora.agent

import android.content.Context
import android.net.TrafficStats
import ir.pocora.config.JsonFile
import ir.pocora.model.DayRecord
import ir.pocora.model.Event
import ir.pocora.model.EventKind
import ir.pocora.model.Mark
import ir.pocora.model.Peer
import ir.pocora.model.Rules
import ir.pocora.model.Snapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.util.UUID

// What the agent keeps on the child's phone, one JSON file each, and its traffic meter.

// The child's rules. This file is the only copy that counts.
class RulesStore(
    context: Context,
) {
    private val file = JsonFile(context, "rules.json", Rules.serializer())

    fun read(): Rules? = file.read()

    fun write(rules: Rules) = file.write(rules)

    fun delete() = file.delete()
}

// Parents this phone disconnected from, still to be told. Each is tried until it hears, or for 7 days.
class GoodbyeStore(
    context: Context,
) {
    companion object {
        private const val KEPT_MILLISECONDS = 7 * 86_400_000L
    }

    private val file = JsonFile(context, "goodbyes.json", ListSerializer(Goodbye.serializer()))

    fun pending(now: Long): List<Peer> =
        file
            .read()
            .orEmpty()
            .filter { now - it.since < KEPT_MILLISECONDS }
            .map { it.parent }

    fun add(
        parents: List<Peer>,
        now: Long,
    ) {
        file.update(emptyList()) { goodbyes ->
            goodbyes.filter { old -> parents.none { it.id == old.parent.id } } +
                parents.map { Goodbye(it, now) }
        }
    }

    fun remove(parentId: String) {
        file.update(emptyList()) { goodbyes -> goodbyes.filter { it.parent.id != parentId } }
    }
}

@Serializable
private data class Goodbye(
    val parent: Peer,
    val since: Long,
)

// What happened on the child's phone, kept for 7 days. Never cleared on send: each parent gets all of it.
class EventLog(
    context: Context,
) {
    private val file = JsonFile(context, "events.json", ListSerializer(Event.serializer()))

    fun all(): List<Event> = file.read() ?: emptyList()

    fun add(
        kind: EventKind,
        start: Long,
        end: Long? = null,
        app: String? = null,
        appName: String? = null,
    ): Event {
        val event = Event(UUID.randomUUID().toString(), kind, start, end, app, appName)
        file.update(emptyList()) { it + event }
        return event
    }

    // An episode that lasts, such as Pocora being off. Opening one that is already open changes nothing.
    fun open(
        kind: EventKind,
        start: Long,
    ): Boolean {
        if (isOpen(kind)) return false
        add(kind, start)
        return true
    }

    fun close(
        kind: EventKind,
        end: Long,
    ): Boolean {
        if (!isOpen(kind)) return false
        file.update(emptyList()) { events ->
            events.map {
                if (it.kind == kind &&
                    it.end == null
                ) {
                    it.copy(end = end)
                } else {
                    it
                }
            }
        }
        return true
    }

    fun isOpen(kind: EventKind): Boolean = all().any { it.kind == kind && it.end == null }

    fun lastOf(
        kind: EventKind,
        app: String?,
    ): Event? = all().lastOrNull { it.kind == kind && it.app == app }

    fun prune(now: Long) {
        val oldest = Snapshot.keptSince(now)
        file.update(emptyList()) { events -> events.filter { (it.end ?: now) >= oldest } }
    }

    fun delete() = file.delete()
}

// Each day's marks as they were applied, and the data each mark used, for the last 7 days.
class DaysLog(
    context: Context,
) {
    private val file = JsonFile(context, "days.json", ListSerializer(DayRecord.serializer()))

    fun all(): List<DayRecord> = file.read() ?: emptyList()

    fun record(
        date: LocalDate,
        mark: Int,
        allowed: Boolean,
        bytes: Long,
    ) {
        val day = date.toEpochDay()
        file.update(emptyList()) { days ->
            val current =
                days.firstOrNull { it.date == day }
                    ?: DayRecord(day, "0".repeat(Mark.MARKS_PER_DAY), List(Mark.MARKS_PER_DAY) { 0L })
            val labels = StringBuilder(current.allowed).apply { setCharAt(mark, if (allowed) '1' else '0') }.toString()
            val counts = current.bytes.toMutableList().apply { this[mark] = this[mark] + bytes }
            val updated = DayRecord(day, labels, counts)
            (days.filter { it.date != day } + updated)
                .filter { it.date > day - Snapshot.DAYS_KEPT }
                .sortedBy { it.date }
        }
    }

    fun markBytes(
        date: LocalDate,
        mark: Int,
    ): Long = all().firstOrNull { it.date == date.toEpochDay() }?.bytes?.getOrNull(mark) ?: 0

    fun delete() = file.delete()
}

// The data the whole phone moved since the last reading. Android's counters restart at boot, so a drop counts as zero.
class TrafficMeter {
    private var last: Long = total()

    @Synchronized
    fun sample(): Long {
        val now = total()
        val moved = (now - last).coerceAtLeast(0)
        last = now
        return moved
    }

    private fun total(): Long {
        val received = TrafficStats.getTotalRxBytes()
        val sent = TrafficStats.getTotalTxBytes()
        return if (received == TrafficStats.UNSUPPORTED.toLong() ||
            sent == TrafficStats.UNSUPPORTED.toLong()
        ) {
            0
        } else {
            received + sent
        }
    }
}
