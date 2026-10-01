package ir.pocora.preset

import android.content.Context
import ir.pocora.config.Language
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Everything in assets/presets.json, written by `make presets` from ../preset/.
@Serializable
data class Presets(
    val schedules: List<SchedulePreset>,
    val appsLists: List<AppsListPreset>,
    val groups: List<AppGroup>,
    val quotas: List<QuotaPreset>,
    val holidays: List<Holiday>,
    val ramadan: List<Period>,
) {
    companion object {
        const val ASSET = "presets.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Presets = json.decodeFromString(serializer(), text)
    }

    // Unknown ids fall back to the first preset, so rules from a newer app version still work.
    fun schedule(id: String): SchedulePreset = schedules.firstOrNull { it.id == id } ?: schedules.first()

    fun appsList(id: String): AppsListPreset = appsLists.firstOrNull { it.id == id } ?: appsLists.first()

    fun quota(id: String): QuotaPreset = quotas.firstOrNull { it.id == id } ?: quotas.first()

    fun group(id: String): AppGroup = groups.firstOrNull { it.id == id } ?: groups.last()

    // The group the shipped lists put this app in, if any.
    fun knownGroupOf(packageName: String): String? =
        groups
            .firstOrNull { group ->
                group.apps.any {
                    it.`package` ==
                        packageName
                }
            }?.id
}

// Reads the shipped presets once.
object PresetStore {
    @Volatile
    private var loaded: Presets? = null

    fun get(context: Context): Presets =
        loaded ?: synchronized(this) {
            loaded ?: Presets
                .parse(
                    context.assets
                        .open(Presets.ASSET)
                        .bufferedReader()
                        .use { it.readText() },
                ).also { loaded = it }
        }
}

// A name in both languages, as the presets carry it.
@Serializable
data class Names(
    val en: String,
    val fa: String,
) {
    fun of(language: String): String = if (language == Language.PERSIAN) fa else en
}

// A stretch of the Iranian year, both ends included. Month-day as "07-01", or a full date as "1405-11-19".
@Serializable
data class Period(
    val from: String,
    val to: String,
)

// An official holiday. The date is in the Iranian calendar, as "1405-01-01".
@Serializable
data class Holiday(
    val date: String,
    val name: Names,
)

// An app the presets list by its Android id, so its group never depends on what the developer declared.
@Serializable
data class KnownApp(
    val `package`: String,
    val name: Names,
)

@Serializable
data class AppGroup(
    val id: String,
    val name: Names,
    val apps: List<KnownApp>,
) {
    companion object {
        const val SYSTEM = "system"
        const val SCHOOL = "school"
        const val LEARNING = "learning"
        const val KIDS = "kids"
        const val MESSAGING = "messaging"
        const val DAILY_TOOLS = "daily-tools"
        const val MUSIC = "music"
        const val VIDEO = "video"
        const val GAMES = "games"
        const val STORES = "stores"
        const val BROWSER = "browser"
        const val SOCIAL = "social"
        const val OTHER = "other"
    }
}

// A set of app groups that have internet in an Allowed mark.
@Serializable
data class AppsListPreset(
    val id: String,
    val name: Names,
    val section: String,
    val ages: List<Int>? = null,
    val groups: List<String>,
) {
    fun fitsAge(age: Int?): Boolean = age != null && ages != null && age in ages[0]..ages[1]
}

// Data one Allowed mark may use. No limit has no number.
@Serializable
data class QuotaPreset(
    val id: String,
    val name: Names,
    val megabytesPerMark: Int? = null,
) {
    val bytesPerMark: Long?
        get() = megabytesPerMark?.let { it * BYTES_PER_MEGABYTE }

    companion object {
        private const val BYTES_PER_MEGABYTE = 1_000_000L
    }
}

// One ready-made week. week[0] is Saturday, and each day is its Allowed blocks as [first mark, end mark).
@Serializable
data class SchedulePreset(
    val id: String,
    val name: Names,
    val section: String,
    val week: List<List<List<Int>>>,
    val seasons: List<Period> = emptyList(),
) {
    companion object {
        const val SECTION_SCHOOL_YEAR = "school_year"
        const val SECTION_HOLIDAYS = "holidays"
        const val SECTION_SIMPLE_RULES = "simple_rules"
    }
}
