package ir.pocora.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

// The Myket build's Update button. Myket shows its own update dialog when a newer version is out, and nothing
// when this one is the newest. Without the Myket app, the app's page on the Myket website opens instead.
object Myket {
    fun checkUpdate(context: Context) {
        val id = context.packageName
        try {
            open(context, "myket://check-update?id=$id")
        } catch (_: ActivityNotFoundException) {
            open(context, "https://myket.ir/app/$id")
        }
    }

    private fun open(
        context: Context,
        uri: String,
    ) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
