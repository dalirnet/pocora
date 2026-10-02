package ir.pocora.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import ir.pocora.PocoraApp
import ir.pocora.Role
import ir.pocora.config.ConfigStore
import ir.pocora.config.Language
import ir.pocora.config.Look
import ir.pocora.protocol.PairingCode
import ir.pocora.ui.child.ChildApp
import ir.pocora.ui.component.LocalLocked
import ir.pocora.ui.component.WithToasts
import ir.pocora.ui.parent.LockScreen
import ir.pocora.ui.parent.ParentApp

class MainActivity : ComponentActivity() {
    companion object {
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

    // The parent app opens locked, and locks again each time it leaves the screen: the child may use the same phone.
    private val locked = mutableStateOf(Role.current == Role.PARENT)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Language.wrap(newBase, ConfigStore(newBase).language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        savedInstanceState?.let { locked.value = it.getBoolean(STATE_LOCKED, locked.value) }
        val handedCode = if (isHandedCode()) intent.getStringExtra(SamePhone.EXTRA_PAIRING_CODE) else null
        handleOpen(intent)
        look.value = (application as PocoraApp).configStore.look
        setContent {
            PocoraTheme(look.value) {
                WithToasts {
                    if (Role.current == Role.CHILD) {
                        ChildApp(
                            handedCode = handedCode?.let(PairingCode::parse),
                            onLanguage = ::setLanguage,
                            onLook = ::setLook,
                        )
                    } else {
                        // The lock covers the screens instead of replacing them, so a pairing code left open
                        // keeps waiting while the child app is opened on this same phone.
                        Box {
                            Box(modifier = if (locked.value) Modifier.clearAndSetSemantics {} else Modifier) {
                                CompositionLocalProvider(LocalLocked provides locked.value) {
                                    ParentApp(
                                        opened = opened.value,
                                        onOpened = { opened.value = null },
                                        onLanguage = ::setLanguage,
                                        onLook = ::setLook,
                                    )
                                }
                            }
                            if (locked.value) LockScreen(onUnlock = { locked.value = false })
                        }
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

    // The language is read when the activity is created, so switching it starts the activity again.
    private fun setLanguage(code: String) {
        val configStore = (application as PocoraApp).configStore
        if (configStore.language == code) return
        configStore.language = code
        recreate()
    }

    // A code handed in instead of scanned, in a debug build only: by a script, for a virtual phone with no camera.
    // In a release build only a person holding the phone can start pairing.
    private fun isHandedCode(): Boolean = isDebuggable()

    private fun isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
