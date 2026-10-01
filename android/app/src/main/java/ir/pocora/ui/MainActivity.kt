package ir.pocora.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import ir.pocora.PocoraApp
import ir.pocora.Role
import ir.pocora.config.ConfigStore
import ir.pocora.config.Language
import ir.pocora.config.Look
import ir.pocora.protocol.PairingCode
import ir.pocora.ui.child.ChildApp
import ir.pocora.ui.component.WithToasts
import ir.pocora.ui.parent.LockScreen
import ir.pocora.ui.parent.ParentApp

class MainActivity : ComponentActivity() {
    companion object {
        // A pairing code handed in instead of scanned, for a virtual phone with no camera to point.
        // Debug builds only: in a release build, only a person holding the phone can start pairing.
        private const val EXTRA_PAIRING_CODE = "pairing_code"

        // A parent's request, from its notification, and the child's answer when a button was tapped.
        const val EXTRA_REQUEST = "request"
        const val EXTRA_APPROVE = "approve"

        // A parent's notification: the child, and what to open on the child's page.
        const val EXTRA_CHILD = "child"
        const val EXTRA_OPEN = "open"

        // Kept when the activity is rebuilt, as a language change does, so that does not ask for the password again.
        // Leaving the app locks it before this is saved, so a state Android hands back later is always locked.
        private const val STATE_LOCKED = "locked"
    }

    // Where a parent's notification asked to go: the child's id and the part to open.
    private val opened = mutableStateOf<Pair<String, String?>?>(null)
    private val look = mutableStateOf(Look.SYSTEM)

    // The request to show on the Requests tab, when its notification was tapped without an answer.
    private val shownRequest = mutableStateOf<String?>(null)

    // The parent app opens locked, and locks again each time it leaves the screen: the child may use the same phone.
    private val locked = mutableStateOf(Role.current == Role.PARENT)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Language.wrap(newBase, ConfigStore(newBase).language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        savedInstanceState?.let { locked.value = it.getBoolean(STATE_LOCKED, locked.value) }
        val debugCode = if (isDebuggable()) intent.getStringExtra(EXTRA_PAIRING_CODE) else null
        handleRequest(intent)
        handleOpen(intent)
        look.value = (application as PocoraApp).configStore.look
        setContent {
            PocoraTheme(look.value) {
                WithToasts {
                    if (Role.current == Role.CHILD) {
                        ChildApp(
                            handedCode = debugCode?.let(PairingCode::parse),
                            shownRequest = shownRequest.value,
                            onRequestShown = { shownRequest.value = null },
                            onLanguage = ::setLanguage,
                            onLook = ::setLook,
                        )
                    } else if (locked.value) {
                        LockScreen(onUnlock = { locked.value = false })
                    } else {
                        ParentApp(
                            opened = opened.value,
                            onOpened = { opened.value = null },
                            onLanguage = ::setLanguage,
                            onLook = ::setLook,
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCKED, locked.value)
    }

    // Home, another app, the child app, or the screen going off. Rebuilding for a language change is not leaving.
    override fun onStop() {
        super.onStop()
        if (Role.current == Role.PARENT && !isChangingConfigurations) locked.value = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRequest(intent)
        handleOpen(intent)
    }

    private fun handleOpen(intent: Intent) {
        if (Role.current != Role.PARENT) return
        val child = intent.getStringExtra(EXTRA_CHILD) ?: return
        opened.value = child to intent.getStringExtra(EXTRA_OPEN)
        intent.removeExtra(EXTRA_CHILD)
    }

    private fun setLook(value: Look) {
        (application as PocoraApp).configStore.look = value
        look.value = value
    }

    private fun handleRequest(intent: Intent) {
        if (Role.current != Role.CHILD) return
        val id = intent.getStringExtra(EXTRA_REQUEST) ?: return
        intent.removeExtra(EXTRA_REQUEST)
        val agent = (application as PocoraApp).agent
        val request = agent.requests.all().firstOrNull { it.id == id } ?: return
        if (intent.hasExtra(EXTRA_APPROVE)) {
            agent.answer(this, request, intent.getBooleanExtra(EXTRA_APPROVE, false))
        } else {
            shownRequest.value = id
        }
    }

    // The language is read when the activity is created, so switching it starts the activity again.
    private fun setLanguage(code: String) {
        val configStore = (application as PocoraApp).configStore
        if (configStore.language == code) return
        configStore.language = code
        recreate()
    }

    private fun isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
