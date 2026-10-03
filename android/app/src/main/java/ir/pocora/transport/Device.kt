package ir.pocora.transport

import android.content.Context
import android.os.Build

// How this phone describes itself to the other one when pairing.
object Device {
    val name: String
        get() {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val namesManufacturer = Build.MODEL.startsWith(manufacturer, ignoreCase = true)
            return if (namesManufacturer) Build.MODEL else "$manufacturer ${Build.MODEL}"
        }

    val androidVersion: String
        get() = Build.VERSION.RELEASE

    // Pocora's own version, which both apps must share: they are built and released together.
    fun appVersion(context: Context): String =
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()
}
