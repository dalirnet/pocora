package ir.pocora.ui.common

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

// What both apps ask Android for, and the screens that grant it.
object Permissions {
    // Xiaomi's own Autostart switch, kept as an app op of its own.
    private const val MIUI_AUTO_START_OP = 10008
    private val MIUI_AUTO_START_PAGE =
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun isBatteryExempt(context: Context): Boolean =
        (
            context.getSystemService(
                Context.POWER_SERVICE,
            ) as PowerManager
        ).isIgnoringBatteryOptimizations(context.packageName)

    // Xiaomi phones show their own battery page in place of Android's dialog, and keep the choice to themselves.
    fun hasOwnBatterySettings(): Boolean = isXiaomi()

    // Xiaomi, Redmi and Poco phones all report Xiaomi.
    fun isXiaomi(): Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    // With Autostart off, MIUI's cleaner closes Pocora and nothing may start it again: not Android's Always-on VPN,
    // not the service's own restart. The child then sees "Disconnected from always-on VPN".
    // Null when the phone won't say.
    fun canAutoStart(context: Context): Boolean? {
        if (!isXiaomi()) return true
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val check =
                AppOpsManager::class.java.getMethod(
                    "checkOpNoThrow",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                )
            check.invoke(ops, MIUI_AUTO_START_OP, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        } catch (_: ReflectiveOperationException) {
            null
        }
    }

    // MIUI's Autostart list, or the app's own page where that list is missing.
    fun autoStartIntent(context: Context): Intent {
        val page = Intent().setComponent(MIUI_AUTO_START_PAGE)
        if (page.resolveActivity(context.packageManager) != null) return page
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }

    // Asks once, in Android's own dialog. The child's agent keeps running, which is what the exemption is for.
    @SuppressLint("BatteryLife")
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
