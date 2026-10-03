package ir.pocora.ui.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.config.Look
import ir.pocora.model.Snapshot
import ir.pocora.model.timeline
import ir.pocora.transport.PairingResult
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.AppVersion
import ir.pocora.ui.common.EventList
import ir.pocora.ui.common.LanguageAndLook
import ir.pocora.ui.common.TemplateCard
import ir.pocora.ui.common.UsageContent
import ir.pocora.ui.common.WeekCard
import ir.pocora.ui.common.rememberPresets
import ir.pocora.ui.component.ButtonPair
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.LinkRow
import ir.pocora.ui.component.LoadingCards
import ir.pocora.ui.component.Screen
import ir.pocora.ui.component.SectionTitle
import ir.pocora.ui.component.Sheet
import ir.pocora.ui.rememberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The child's read-only screens: internet times, usage, what the parent sees, and settings.

// The same week the parent sees, to read only.
@Composable
fun TimesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val agent = (context.applicationContext as PocoraApp).agent
    val presets = rememberPresets()
    val updates by agent.updates.collectAsState()
    val rules = remember(updates) { agent.status.rules }
    Screen(title = stringResource(R.string.tile_times), onBack = onBack) {
        if (rules == null) {
            LoadingCards(2)
            return@Screen
        }
        TemplateCard(presets, rules)
        WeekCard(presets, rules, stringResource(R.string.only_parent_changes), byParent = true)
    }
}

// The child's own usage: exactly what the parent sees, so nothing is hidden.
@Composable
fun UsageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val agent = (context.applicationContext as PocoraApp).agent
    val presets = rememberPresets()
    val updates by agent.updates.collectAsState()
    val snapshot by produceState<Snapshot?>(null, updates) { value = withContext(Dispatchers.IO) { agent.snapshot() } }
    Screen(title = stringResource(R.string.tile_usage), onBack = onBack) {
        snapshot?.let { UsageContent(it, presets) } ?: LoadingCards(3)
    }
}

// The second tab: the same list the parent sees about this phone, so nothing about the child is hidden from them.
@Composable
fun EventsScreen(bottom: @Composable () -> Unit) {
    val agent = (LocalContext.current.applicationContext as PocoraApp).agent
    val updates by agent.updates.collectAsState()
    val events = remember(updates) { agent.events.all().timeline() }
    Screen(title = stringResource(R.string.events_title), bottom = bottom, centered = events.isEmpty()) {
        if (events.isEmpty()) {
            EmptyState(
                AppIcons.CheckCircle,
                AppColors.green,
                stringResource(R.string.all_quiet),
                stringResource(R.string.parent_sees_these),
            )
        } else {
            Text(
                text = stringResource(R.string.parent_sees_these),
                color = LocalPalette.current.muted,
                fontSize = Dimens.caption,
            )
            EventList(events)
        }
    }
}

private val SEEN =
    listOf(
        R.string.seen_apps_time,
        R.string.seen_apps_data,
        R.string.seen_installed,
        R.string.seen_off,
        R.string.seen_restart,
    )
private val NOT_SEEN = listOf(R.string.not_seen_messages, R.string.not_seen_typing, R.string.not_seen_sites)

// What is seen and what is not, so nothing is hidden from the child.
@Composable
fun SeesScreen(onBack: () -> Unit) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val app = LocalContext.current.applicationContext as PocoraApp
    val parents = remember { app.peerStore.all().size }
    Screen(title = stringResource(R.string.what_my_parent_sees), onBack = onBack) {
        SectionTitle(stringResource(R.string.parent_sees))
        Card { for (line in SEEN) Line(AppIcons.Visibility, AppColors.violet, stringResource(line)) }
        SectionTitle(stringResource(R.string.parent_never_sees))
        Card { for (line in NOT_SEEN) Line(AppIcons.VisibilityOff, AppColors.green, stringResource(line)) }
        Text(
            text =
                if (parents > 1) {
                    stringResource(R.string.connected_parents, format.number(parents))
                } else {
                    stringResource(R.string.connected_one_parent)
                },
            color = palette.muted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun Line(
    icon: ImageVector,
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.row)) {
        IconTile(icon, color, 32.dp)
        Text(text = text, color = LocalPalette.current.text, fontSize = Dimens.body)
    }
}

// Language and theme for everyone. Once paired: setting up the phone, connecting another parent, and disconnecting.
// There is no button to turn Pocora off: a child can still stop the VPN in Android's settings, and the parent sees that.
@Composable
fun SettingsScreen(
    paired: Boolean,
    failure: PairingResult?,
    onBack: (() -> Unit)?,
    onSetup: () -> Unit,
    onAddParent: () -> Unit,
    onLanguage: (String) -> Unit,
    onLook: (Look) -> Unit,
    onDisconnect: () -> Unit,
    bottom: (@Composable () -> Unit)? = null,
) {
    var disconnecting by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        Screen(title = stringResource(R.string.settings), onBack = onBack, bottom = bottom) {
            failure?.let { PairingFailure(it) }
            if (paired) {
                Card {
                    LinkRow(
                        title = stringResource(R.string.set_up_this_phone),
                        icon = AppIcons.Tune,
                        iconColor = AppColors.blue,
                        onClick = onSetup,
                    )
                    LinkRow(
                        title = stringResource(R.string.add_another_parent),
                        subtitle = stringResource(R.string.add_another_parent_text),
                        icon = AppIcons.FamilyRestroom,
                        iconColor = AppColors.violet,
                        onClick = onAddParent,
                    )
                }
            }
            LanguageAndLook(onLanguage, onLook)
            if (paired) {
                Card {
                    LinkRow(
                        title = stringResource(R.string.disconnect_from_parent),
                        icon = AppIcons.LinkOff,
                        iconColor = AppColors.orange,
                        onClick = { disconnecting = true },
                    )
                }
            }
            AppVersion()
        }
        if (disconnecting) {
            Sheet(
                onDismiss = { disconnecting = false },
                title = stringResource(R.string.disconnect_title),
                icon = AppIcons.LinkOff,
                color = AppColors.orange,
                subtitle = stringResource(R.string.disconnect_text),
            ) {
                ButtonPair(
                    stringResource(
                        R.string.cancel,
                    ),
                    { disconnecting = false },
                    stringResource(R.string.disconnect),
                    {
                        disconnecting = false
                        onDisconnect()
                    },
                    danger = true,
                )
            }
        }
    }
}
