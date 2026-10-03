package ir.pocora.agent

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import ir.pocora.config.ConfigStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// The child's clock. At every sync it takes the parent's time and counts forward on the uptime clock,
// so changing the phone's clock does not move the schedule. After a reboot it uses the phone's clock until the next sync.
class AgentClock(
    private val context: Context,
    private val configStore: ConfigStore,
) {
    fun now(): Long {
        val offset = configStore.clockOffset(bootCount()) ?: return System.currentTimeMillis()
        return SystemClock.elapsedRealtime() + offset
    }

    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now()), ZoneId.systemDefault())

    // The other way, for a time sent to the parent.
    fun epochOf(time: LocalDateTime): Long = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun setFromParent(parentTime: Long) =
        configStore.setClockOffset(
            bootCount(),
            parentTime - SystemClock.elapsedRealtime(),
        )

    // Which boot this is. It changes only when the phone restarts, unlike a boot time worked out from the clock.
    fun bootCount(): Long = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0).toLong()

    // When this boot started, by the agent's clock.
    fun bootTime(): Long = now() - SystemClock.elapsedRealtime()
}
