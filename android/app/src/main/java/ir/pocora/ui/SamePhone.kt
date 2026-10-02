package ir.pocora.ui

import android.content.Context
import android.content.Intent
import java.util.UUID

// Both apps on one phone: a parent trying Pocora first, or a family that shares a phone.
// A camera cannot scan its own screen, so the parent app hands its pairing code to the child app,
// which pairs in the background. The parent is the one holding the phone and already tapped the button,
// so the request that carries the handed token is accepted without asking again.
object SamePhone {
    private const val CHILD_APPLICATION_ID = "ir.pocora.child"

    // The child app's door for a handed code. The manifest lets only an app signed with Pocora's key use it.
    private const val PAIR_HERE_RECEIVER = "ir.pocora.service.PairHereReceiver"
    const val EXTRA_PAIRING_CODE = "pairing_code"
    const val EXTRA_TOKEN = "token"

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
}
