package ir.pocora.config

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale
import java.util.UUID

// Small settings and markers, in preferences. Anything larger is a JsonFile.
class ConfigStore(
    context: Context,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    companion object {
        const val FILE_NAME = "pocora_config"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LOOK = "look"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ALIVE_TIME = "alive_time"
        private const val KEY_BOOT_TIME = "boot_time"
        private const val KEY_CLOCK_OFFSET = "clock_offset"
        private const val KEY_CLOCK_BOOT = "clock_boot"
        private const val KEY_LAST_PARENT_CONTACT = "last_parent_contact"
        private const val KEY_CHILD_NAME = "child_name"
        private const val KEY_NOTIFY_HOME = "notify_home"
        private const val KEY_NOTIFY_ALERTS = "notify_alerts"
        private const val KEY_NOTIFY_SUGGESTIONS = "notify_suggestions"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_ALWAYS_ON_OPENED = "always_on_opened"
        private const val KEY_DONE_ONCE = "done_once"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val DONE_ONCE_KEPT = 50
    }

    var language: String
        get() = preferences.getString(KEY_LANGUAGE, Language.PERSIAN) ?: Language.PERSIAN
        set(value) = preferences.edit().putString(KEY_LANGUAGE, value).apply()

    var look: Look
        get() =
            preferences.getString(KEY_LOOK, null)?.let { runCatching { Look.valueOf(it) }.getOrNull() }
                ?: Look.SYSTEM
        set(value) = preferences.edit().putString(KEY_LOOK, value.name).apply()

    // The random id this phone announces on the network. Made once, on first use.
    val deviceId: String
        @Synchronized get() =
            preferences.getString(KEY_DEVICE_ID, null)
                ?: UUID.randomUUID().toString().also { preferences.edit().putString(KEY_DEVICE_ID, it).apply() }

    // --- Child ---

    // The last minute the agent was running, to find the gaps when it was stopped.
    var aliveTime: Long
        get() = preferences.getLong(KEY_ALIVE_TIME, 0)
        set(value) = preferences.edit().putLong(KEY_ALIVE_TIME, value).apply()

    // The boot time at the last start. A different one means the phone restarted.
    var bootTime: Long
        get() = preferences.getLong(KEY_BOOT_TIME, 0)
        set(value) = preferences.edit().putLong(KEY_BOOT_TIME, value).apply()

    // The parent's time minus this phone's uptime clock, valid for the boot it was taken in.
    fun clockOffset(bootTime: Long): Long? =
        if (preferences.getLong(KEY_CLOCK_BOOT, -1) == bootTime) preferences.getLong(KEY_CLOCK_OFFSET, 0) else null

    fun setClockOffset(
        bootTime: Long,
        offset: Long,
    ) = preferences
        .edit()
        .putLong(KEY_CLOCK_BOOT, bootTime)
        .putLong(KEY_CLOCK_OFFSET, offset)
        .apply()

    var lastParentContact: Long
        get() = preferences.getLong(KEY_LAST_PARENT_CONTACT, 0)
        set(value) = preferences.edit().putLong(KEY_LAST_PARENT_CONTACT, value).apply()

    // The name the parent gave this child. Null until a parent sends it, and again once no parent is left.
    var childName: String?
        get() = preferences.getString(KEY_CHILD_NAME, null)
        set(value) = preferences.edit().putString(KEY_CHILD_NAME, value).apply()

    var setupDone: Boolean
        get() = preferences.getBoolean(KEY_SETUP_DONE, false)
        set(value) = preferences.edit().putBoolean(KEY_SETUP_DONE, value).apply()

    // Android does not tell an app whether it is the Always-on VPN, so the step counts as done once its settings were opened.
    var alwaysOnOpened: Boolean
        get() = preferences.getBoolean(KEY_ALWAYS_ON_OPENED, false)
        set(value) = preferences.edit().putBoolean(KEY_ALWAYS_ON_OPENED, value).apply()

    // --- Parent ---

    // True the first time a key is given, false after: one notification per suggestion or holiday.
    @Synchronized
    fun once(key: String): Boolean {
        val done = preferences.getString(KEY_DONE_ONCE, "")!!.split('\n').filter { it.isNotEmpty() }
        if (key in done) return false
        preferences.edit().putString(KEY_DONE_ONCE, (done + key).takeLast(DONE_ONCE_KEPT).joinToString("\n")).apply()
        return true
    }

    var notifyHome: Boolean
        get() = preferences.getBoolean(KEY_NOTIFY_HOME, true)
        set(value) = preferences.edit().putBoolean(KEY_NOTIFY_HOME, value).apply()

    var notifyAlerts: Boolean
        get() = preferences.getBoolean(KEY_NOTIFY_ALERTS, true)
        set(value) = preferences.edit().putBoolean(KEY_NOTIFY_ALERTS, value).apply()

    var notifySuggestions: Boolean
        get() = preferences.getBoolean(KEY_NOTIFY_SUGGESTIONS, true)
        set(value) = preferences.edit().putBoolean(KEY_NOTIFY_SUGGESTIONS, value).apply()

    // The password that opens the parent app. None until the parent chooses one on first run.
    val hasPassword: Boolean
        get() = preferences.contains(KEY_PASSWORD_HASH)

    fun setPassword(password: String) {
        val (salt, hash) = Password.create(password)
        preferences
            .edit()
            .putString(KEY_PASSWORD_SALT, salt)
            .putString(KEY_PASSWORD_HASH, hash)
            .apply()
    }

    fun checkPassword(password: String): Boolean {
        val salt = preferences.getString(KEY_PASSWORD_SALT, null) ?: return false
        val hash = preferences.getString(KEY_PASSWORD_HASH, null) ?: return false
        return Password.matches(password, salt, hash)
    }
}

// The theme setting: follow the phone, or always light or dark.
enum class Look {
    SYSTEM,
    LIGHT,
    DARK,
}

object Language {
    const val PERSIAN = "fa"
    const val ENGLISH = "en"

    fun wrap(
        context: Context,
        code: String,
    ): Context {
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
