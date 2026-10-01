package ir.pocora.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

// The app stores children in Iran use, most used first. An install suggestion opens the first one on the phone.
object AppStores {
    private val STORES =
        listOf(
            "com.farsitel.bazaar" to "bazaar://details?id=",
            "ir.mservices.market" to "myket://details?id=",
            "com.android.vending" to "market://details?id=",
        )

    fun detailsIntent(
        context: Context,
        packageName: String,
    ): Intent {
        for ((store, prefix) in STORES) {
            if (isInstalled(
                    context,
                    store,
                )
            ) {
                return Intent(Intent.ACTION_VIEW, Uri.parse(prefix + packageName)).setPackage(store)
            }
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    }

    private fun isInstalled(
        context: Context,
        packageName: String,
    ): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
