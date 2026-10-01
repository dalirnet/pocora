package ir.pocora.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import ir.pocora.model.InstalledApp
import ir.pocora.preset.AppGroup
import ir.pocora.preset.Presets

// The apps on the child's phone, each with its group: the shipped list first, then what Android knows, then Other.
class AppCatalog(
    private val context: Context,
    private val presets: Presets,
) {
    companion object {
        private const val CACHE_MILLISECONDS = 60_000L
        private const val OWN_PACKAGE_PREFIX = "ir.pocora."
    }

    private val packageManager = context.packageManager
    private var cached: List<InstalledApp> = emptyList()
    private var cachedAt = 0L

    // The apps that can use the internet or can be opened. Pocora's own apps are left out.
    @Synchronized
    fun installed(): List<InstalledApp> {
        val now = System.currentTimeMillis()
        if (now - cachedAt < CACHE_MILLISECONDS && cached.isNotEmpty()) return cached
        val launchable = launchablePackages()
        val browsers = browserPackages()
        val vpns = vpnPackages()
        cached =
            packageManager
                .getInstalledApplications(0)
                .asSequence()
                .filter { !it.packageName.startsWith(OWN_PACKAGE_PREFIX) }
                .filter { it.packageName in launchable || usesInternet(it.packageName) }
                .map { info -> describe(info, info.packageName in launchable, browsers, vpns) }
                .sortedBy { it.name.lowercase() }
                .toList()
        cachedAt = now
        return cached
    }

    // A package was added or removed. The next reading starts fresh.
    @Synchronized
    fun invalidate() {
        cachedAt = 0
    }

    fun find(packageName: String): InstalledApp? = installed().firstOrNull { it.`package` == packageName }

    // Packages that can use the network at all. Only these are ever put in the tunnel.
    fun withInternet(): List<String> = installed().map { it.`package` }.filter(::usesInternet)

    private fun describe(
        info: ApplicationInfo,
        launchable: Boolean,
        browsers: Set<String>,
        vpns: Set<String>,
    ): InstalledApp {
        val name = info.loadLabel(packageManager).toString()
        val version =
            try {
                packageManager.getPackageInfo(info.packageName, 0).versionName ?: ""
            } catch (_: PackageManager.NameNotFoundException) {
                ""
            }
        // A preinstalled app the child opens, such as YouTube, is judged by its group like any other.
        // Only the parts of Android with no screen of their own count as System.
        val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && !launchable
        return InstalledApp(
            info.packageName,
            name,
            version,
            groupOf(info, system, browsers),
            system,
            info.packageName in vpns,
        )
    }

    private fun groupOf(
        info: ApplicationInfo,
        system: Boolean,
        browsers: Set<String>,
    ): String =
        presets.knownGroupOf(info.packageName)
            ?: when {
                system -> AppGroup.SYSTEM
                info.packageName in browsers -> AppGroup.BROWSER
                else -> categoryGroup(info.category)
            }

    private fun categoryGroup(category: Int): String =
        when (category) {
            ApplicationInfo.CATEGORY_GAME -> AppGroup.GAMES
            ApplicationInfo.CATEGORY_AUDIO -> AppGroup.MUSIC
            ApplicationInfo.CATEGORY_VIDEO -> AppGroup.VIDEO
            ApplicationInfo.CATEGORY_SOCIAL -> AppGroup.SOCIAL
            ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppGroup.DAILY_TOOLS
            else -> AppGroup.OTHER
        }

    private fun usesInternet(packageName: String): Boolean =
        packageManager.checkPermission(Manifest.permission.INTERNET, packageName) == PackageManager.PERMISSION_GRANTED

    private fun launchablePackages(): Set<String> =
        packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }
            .toSet()

    private fun browserPackages(): Set<String> =
        packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
                PackageManager.MATCH_ALL,
            ).map { it.activityInfo.packageName }
            .toSet()

    // Every app that offers a VPN service. Pocora's own is left out above.
    private fun vpnPackages(): Set<String> =
        packageManager
            .queryIntentServices(Intent(VpnService.SERVICE_INTERFACE), 0)
            .map { it.serviceInfo.packageName }
            .toSet()
}
