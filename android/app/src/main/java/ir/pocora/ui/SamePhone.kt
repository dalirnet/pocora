package ir.pocora.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.UUID

// Both apps on one phone: a parent trying Pocora first, or a family that shares a phone.
// A camera cannot scan its own screen, so the parent app hands its pairing code to the child app,
// which pairs in the background. The parent is the one holding the phone and already tapped the button,
// so the request that carries the handed token is accepted without asking again.
// Once paired, the parent app can pause the child app here, to use the phone itself, and turn it back on.
object SamePhone {
    private const val CHILD_APPLICATION_ID = "ir.pocora.child"

    // The child app's door for a handed code. The manifest lets only an app signed with Pocora's key use it.
    private const val PAIR_HERE_RECEIVER = "ir.pocora.service.PairHereReceiver"
    const val EXTRA_PAIRING_CODE = "pairing_code"
    const val EXTRA_TOKEN = "token"

    // The child app's door for pausing, guarded the same way.
    private const val PAUSE_HERE_RECEIVER = "ir.pocora.service.PauseHereReceiver"
    const val EXTRA_PAUSED = "paused"
    const val EXTRA_CHILD_ID = "child_id"

    // The child app on this phone, as it answered: which child it is, and whether it is paused.
    data class Here(
        val childId: String,
        val paused: Boolean,
    )

    fun hasChildApp(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(CHILD_APPLICATION_ID) != null

    // A new random token for each handover, so only this one request is let in without asking.
    fun newToken(): String = UUID.randomUUID().toString()

    fun handCode(
        context: Context,
        code: String,
        token: String,
    ) {
        val intent =
            Intent()
                .setClassName(CHILD_APPLICATION_ID, PAIR_HERE_RECEIVER)
                .putExtra(EXTRA_PAIRING_CODE, code)
                .putExtra(EXTRA_TOKEN, token)
        context.sendBroadcast(intent)
    }

    // Asks the child app here, pausing or resuming it first when pause is given. answer runs on the main thread,
    // with null when there is no paired child app here.
    fun pauseHere(
        context: Context,
        pause: Boolean?,
        answer: (Here?) -> Unit,
    ) {
        val intent =
            Intent()
                .setClassName(CHILD_APPLICATION_ID, PAUSE_HERE_RECEIVER)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        if (pause != null) intent.putExtra(EXTRA_PAUSED, pause)
        val result =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val extras = getResultExtras(false)
                    val id = extras?.getString(EXTRA_CHILD_ID)
                    val ok = resultCode == Activity.RESULT_OK && id != null
                    answer(if (ok) Here(id, extras.getBoolean(EXTRA_PAUSED)) else null)
                }
            }
        context.sendOrderedBroadcast(intent, null, result, null, Activity.RESULT_CANCELED, null, null)
    }
}
