package ir.pocora.ui.parent

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.config.Password
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.Background
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.ToastMessage
import ir.pocora.ui.component.Toasts
import ir.pocora.ui.rememberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", DELETE)
private const val DELETE = "delete"
private const val KEYS_PER_ROW = 3

// The keys are tiles, rounded as the app's cards are.
private val KEY_SHAPE = RoundedCornerShape(Dimens.tileCorner)

private suspend fun PointerInputScope.swallowTouches() {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}

// Covers the parent app until the password is typed. On first run it asks for a new one, twice.
// It lies over the screens, which stay as they were: Back leaves the app and touches stop here, instead of reaching them.
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val configStore = (context.applicationContext as PocoraApp).configStore
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val creating = remember { !configStore.hasPassword }
    var first by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    val wrongText = stringResource(R.string.lock_wrong)
    val mismatchText = stringResource(R.string.lock_mismatch)

    BackHandler { (context as? Activity)?.moveTaskToBack(true) }

    // Hashing takes a moment, so it runs off the main thread.
    fun complete(password: String) {
        checking = true
        scope.launch {
            val chosen = first
            when {
                !creating -> {
                    val right = withContext(Dispatchers.Default) { configStore.checkPassword(password) }
                    if (right) onUnlock() else Toasts.show(wrongText, ToastMessage.Kind.PROBLEM)
                }

                chosen == null -> {
                    first = password
                }

                chosen == password -> {
                    withContext(Dispatchers.Default) { configStore.setPassword(password) }
                    onUnlock()
                }

                else -> {
                    first = null
                    Toasts.show(mismatchText, ToastMessage.Kind.PROBLEM)
                }
            }
            entered = ""
            checking = false
        }
    }

    fun press(key: String) {
        if (checking) return
        entered =
            when (key) {
                DELETE -> entered.dropLast(1)
                else -> if (entered.length < Password.LENGTH) entered + key else entered
            }
        if (entered.length == Password.LENGTH) complete(entered)
    }

    Background(modifier = Modifier.pointerInput(Unit) { swallowTouches() }) {
        // The keypad keeps its place at the bottom while the words above it change.
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(Dimens.edge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.section),
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = AppIcons.Lock,
                    color = palette.brand,
                    title =
                        stringResource(
                            when {
                                !creating -> R.string.lock_enter
                                first == null -> R.string.lock_create
                                else -> R.string.lock_repeat
                            },
                        ),
                    text = if (creating && first == null) stringResource(R.string.lock_create_text) else null,
                )
            }
            Dots(entered.length)
            Keypad(::press)
            Spacer(modifier = Modifier.height(Dimens.section))
        }
    }
}

// Fills left to right in both languages, as the digits are typed on the keypad. A typed digit grows into a pill.
@Composable
private fun Dots(filled: Int) {
    val palette = LocalPalette.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.inside)) {
            repeat(Password.LENGTH) { index ->
                Box(
                    modifier =
                        Modifier
                            .size(width = if (index < filled) 28.dp else 14.dp, height = 14.dp)
                            .background(if (index < filled) palette.brand else palette.limited, CircleShape),
                )
            }
        }
    }
}

// Digits keep the phone keypad's order in both languages. Delete sits in a soft tile, apart from the digits.
@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.row)) {
            for (row in KEYS.chunked(KEYS_PER_ROW)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.row)) {
                    for (key in row) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 84.dp, height = 64.dp)
                                    .let {
                                        if (key.isEmpty()) {
                                            it
                                        } else {
                                            it
                                                .shadow(
                                                    6.dp,
                                                    KEY_SHAPE,
                                                    ambientColor = palette.shadow,
                                                    spotColor = palette.shadow,
                                                ).background(
                                                    if (key ==
                                                        DELETE
                                                    ) {
                                                        palette.limited
                                                    } else {
                                                        palette.card
                                                    },
                                                    KEY_SHAPE,
                                                ).clip(KEY_SHAPE)
                                                .clickable { onKey(key) }
                                        }
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (key) {
                                "" -> {}

                                DELETE -> {
                                    Icon(
                                        AppIcons.Backspace,
                                        stringResource(R.string.lock_delete),
                                        tint = palette.muted,
                                    )
                                }

                                else -> {
                                    Text(
                                        text = format.number(key.toInt()),
                                        color = palette.text,
                                        fontSize = Dimens.title,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
