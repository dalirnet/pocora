package ir.pocora.ui.child

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.config.Look
import ir.pocora.protocol.PairingCode
import ir.pocora.transport.PairingResult
import ir.pocora.ui.AppIcons
import ir.pocora.ui.component.BarItem
import ir.pocora.ui.component.BottomBar

private val TABS = listOf(ChildStep.HOME, ChildStep.ACTIVITY, ChildStep.SETTINGS)

// The child app's screens and the way between them, on the same plan as the parent app's.
// First run: welcome, the scan, waiting for the parent, then the setup steps. Then Home, Activity and Settings are the bottom bar,
// and every other screen has a back arrow. A code handed in, instead of scanned, goes straight to waiting.
@Composable
fun ChildApp(
    handedCode: PairingCode?,
    onLanguage: (String) -> Unit,
    onLook: (Look) -> Unit,
) {
    val app = LocalContext.current.applicationContext as PocoraApp
    var paired by rememberSaveable { mutableStateOf(app.peerStore.all().isNotEmpty()) }
    var code by rememberSaveable { mutableStateOf(if (paired) null else handedCode?.encode()) }
    var step by rememberSaveable {
        mutableStateOf(
            when {
                paired && app.configStore.setupDone -> ChildStep.HOME
                paired -> ChildStep.SETUP
                code != null -> ChildStep.WAITING
                else -> ChildStep.WELCOME
            },
        )
    }
    var failure by rememberSaveable { mutableStateOf<PairingResult?>(null) }
    // Where a screen with a back arrow returns to.
    var parentStep by rememberSaveable { mutableStateOf(ChildStep.HOME) }

    // A parent this phone disconnected from, and not yet told, is tried again each time the app opens.
    LaunchedEffect(Unit) { app.agent.sendGoodbyes() }

    // The parent app on this phone may have paired this app in the background, while it was closed or
    // on another screen. Coming back finds the pairing and goes on to setup, and the agent starts here
    // if Android did not let it start from the background.
    LifecycleResumeEffect(Unit) {
        if (!paired && app.peerStore.all().isNotEmpty()) {
            paired = true
            failure = null
            step = ChildStep.SETUP
        }
        app.startServiceIfPaired()
        onPauseOrDispose { }
    }

    fun open(next: ChildStep) {
        parentStep = step
        step = next
    }

    BackHandler(enabled = step == ChildStep.SCAN || step == ChildStep.WAITING) { step = ChildStep.WELCOME }
    BackHandler(
        enabled =
            step in listOf(ChildStep.TIMES, ChildStep.USAGE, ChildStep.SEES) ||
                (step == ChildStep.SETUP && app.configStore.setupDone),
    ) {
        step = parentStep
    }
    BackHandler(
        enabled = step == ChildStep.ACTIVITY || (step == ChildStep.SETTINGS && paired),
    ) { step = ChildStep.HOME }
    BackHandler(enabled = step == ChildStep.SETTINGS && !paired) { step = ChildStep.WELCOME }

    val bottom: @Composable () -> Unit = {
        BottomBar(
            items =
                listOf(
                    BarItem(AppIcons.Home, stringResource(R.string.nav_home)),
                    BarItem(AppIcons.Notifications, stringResource(R.string.nav_activity)),
                    BarItem(AppIcons.Settings, stringResource(R.string.settings)),
                ),
            selected = TABS.indexOf(step).coerceAtLeast(0),
            onSelect = { step = TABS[it] },
        )
    }

    when (step) {
        ChildStep.WELCOME -> {
            WelcomeScreen(
                failure = failure,
                onScan = {
                    failure = null
                    step = ChildStep.SCAN
                },
                onSettings = { step = ChildStep.SETTINGS },
            )
        }

        ChildStep.SCAN -> {
            ScanScreen(
                onCode = {
                    code = it.encode()
                    step = ChildStep.WAITING
                },
                onBack = { step = ChildStep.WELCOME },
            )
        }

        ChildStep.WAITING -> {
            WaitingScreen(
                code = code?.let(PairingCode::parse),
                onResult = { result ->
                    val accepted = result == PairingResult.ACCEPTED
                    failure = if (accepted) null else result
                    paired = accepted
                    step = if (accepted) ChildStep.SETUP else ChildStep.WELCOME
                },
                onCancel = { step = ChildStep.WELCOME },
            )
        }

        ChildStep.SETUP -> {
            SetupScreen(onDone = { step = ChildStep.HOME })
        }

        ChildStep.HOME -> {
            HomeScreen(
                onSetup = { open(ChildStep.SETUP) },
                onTimes = { open(ChildStep.TIMES) },
                onUsage = { open(ChildStep.USAGE) },
                onSees = { open(ChildStep.SEES) },
                bottom = bottom,
            )
        }

        ChildStep.ACTIVITY -> {
            ActivityScreen(bottom = bottom)
        }

        ChildStep.SETTINGS -> {
            SettingsScreen(
                paired = paired,
                onBack = if (paired) null else ({ step = ChildStep.WELCOME }),
                onSetup = { open(ChildStep.SETUP) },
                onLanguage = onLanguage,
                onLook = onLook,
                onDisconnect = {
                    app.agent.disconnect()
                    paired = false
                    step = ChildStep.WELCOME
                },
                bottom = if (paired) bottom else null,
            )
        }

        ChildStep.TIMES -> {
            TimesScreen(onBack = { step = parentStep })
        }

        ChildStep.USAGE -> {
            UsageScreen(onBack = { step = parentStep })
        }

        ChildStep.SEES -> {
            SeesScreen(onBack = { step = parentStep })
        }
    }
}

enum class ChildStep {
    WELCOME,
    SCAN,
    WAITING,
    SETUP,
    HOME,
    ACTIVITY,
    SETTINGS,
    TIMES,
    USAGE,
    SEES,
}
