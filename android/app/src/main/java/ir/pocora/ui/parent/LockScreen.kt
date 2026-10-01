package ir.pocora.ui.parent

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.config.Password
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.Background
import ir.pocora.ui.component.ToastMessage
import ir.pocora.ui.component.Toasts
import ir.pocora.ui.rememberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", DELETE)
private const val DELETE = "delete"
private const val KEYS_PER_ROW = 3

// Covers the parent app until the password is typed. On first run it asks for a new one, twice.
// Back leaves the app instead of reaching the screens under it.
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

    Background {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(Dimens.edge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.section, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier.size(88.dp).background(palette.brand.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, null, tint = palette.brand, modifier = Modifier.size(40.dp))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.small),
            ) {
                Text(
                    text =
                        stringResource(
                            when {
                                !creating -> R.string.lock_enter
                                first == null -> R.string.lock_create
                                else -> R.string.lock_repeat
                            },
                        ),
                    color = palette.text,
                    fontSize = Dimens.title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (creating && first == null) {
                    Text(
                        text = stringResource(R.string.lock_create_text),
                        color = palette.muted,
                        fontSize = Dimens.caption,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Dots(entered.length)
            Keypad(::press)
        }
    }
}

// Fills left to right in both languages, as the digits are typed on the keypad.
@Composable
private fun Dots(filled: Int) {
    val palette = LocalPalette.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.inside)) {
            repeat(Password.LENGTH) { index ->
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .background(if (index < filled) palette.brand else palette.limited, CircleShape),
                )
            }
        }
    }
}

// Digits keep the phone keypad's order in both languages.
@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.row)) {
            for (row in KEYS.chunked(KEYS_PER_ROW)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.section)) {
                    for (key in row) {
                        Box(
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .let {
                                        if (key.isEmpty()) {
                                            it
                                        } else {
                                            it.background(palette.card).clickable { onKey(key) }
                                        }
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (key) {
                                "" -> {}

                                DELETE -> {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Backspace,
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
