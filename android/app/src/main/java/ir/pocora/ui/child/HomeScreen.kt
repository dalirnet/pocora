package ir.pocora.ui.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.agent.AgentStatus
import ir.pocora.model.Snapshot
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.HomeHeader
import ir.pocora.ui.common.LayeredHome
import ir.pocora.ui.common.ScreenTimeSection
import ir.pocora.ui.common.StatusPanel
import ir.pocora.ui.common.Tile
import ir.pocora.ui.common.TileGrid
import ir.pocora.ui.common.internetSentence
import ir.pocora.ui.common.rememberPresets
import ir.pocora.ui.component.AppIcon
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.LoadingCards
import ir.pocora.ui.component.ProgressLine
import ir.pocora.ui.component.SmallButton
import ir.pocora.ui.rememberFormat
import ir.pocora.ui.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val CONTACT_FRESH_MILLISECONDS = 3 * 60_000L

// The same layout as the parent's Home: who and whether the parent is in touch, how the internet is now,
// the features as tiles, then today's screen time on the sheet.
@Composable
fun HomeScreen(
    onSetup: () -> Unit,
    onTimes: () -> Unit,
    onUsage: () -> Unit,
    onSees: () -> Unit,
    bottom: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocoraApp
    val agent = app.agent
    val palette = LocalPalette.current
    val format = rememberFormat()
    val presets = rememberPresets()
    val updates by agent.updates.collectAsState()
    val status = remember(updates) { agent.status }
    val missingSetup = remember(updates) { !SetupStep.allDone(context) }
    // Building a snapshot reads Android's usage history, so it is done off the main thread.
    val snapshot by produceState<Snapshot?>(null, updates) { value = withContext(Dispatchers.IO) { agent.snapshot() } }
    val ready = updates > 0L
    val online =
        status.lastParentContact != 0L &&
            System.currentTimeMillis() - status.lastParentContact < CONTACT_FRESH_MILLISECONDS

    LayeredHome(
        top = {
            HomeHeader(
                picture = { AppIcon(Dimens.avatar) },
                title = status.childName ?: stringResource(R.string.app_name),
                online = online,
                status =
                    when {
                        online -> stringResource(R.string.child_connected)
                        status.lastParentContact == 0L -> stringResource(R.string.no_contact_yet)
                        else -> stringResource(R.string.last_contact, format.time(status.lastParentContact))
                    },
            ) {}
            Status(status, ready)
            TileGrid(
                listOf(
                    Tile(
                        AppIcons.AccessTime,
                        AppColors.violet,
                        stringResource(R.string.tile_times),
                        status.rules != null,
                        onClick = onTimes,
                    ),
                    Tile(
                        AppIcons.DataUsage,
                        AppColors.green,
                        stringResource(R.string.tile_usage),
                        onClick = onUsage,
                    ),
                    Tile(
                        AppIcons.Visibility,
                        AppColors.blue,
                        stringResource(R.string.tile_parent_sees),
                        onClick = onSees,
                    ),
                ),
            )
        },
        sheet = {
            if (missingSetup) {
                Card(onClick = onSetup) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.row),
                    ) {
                        IconTile(AppIcons.Tune, AppColors.orange, Dimens.rowIcon)
                        Text(
                            text = stringResource(R.string.setup_not_finished),
                            color = palette.text,
                            fontSize = Dimens.body,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        SmallButton(text = stringResource(R.string.continue_setup), onClick = onSetup, filled = true)
                    }
                }
            }
            snapshot?.let { ScreenTimeSection(presets, it, onUsage) } ?: LoadingCards(1)
        },
        bottom = bottom,
        onRefresh = { suspendCoroutine { done -> agent.pull { done.resume(Unit) } } },
    )
}

// How the internet is now: one sentence, until when, today's times, and this half hour's data.
@Composable
private fun Status(
    status: AgentStatus,
    ready: Boolean,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val presets = rememberPresets()
    val scheduleName = status.rules?.let { presets.schedule(it.schedule).name.text() }
    when {
        !ready -> {
            LoadingCards(1)
        }

        !status.hasRules -> {
            StatusPanel(
                AppIcons.HourglassEmpty,
                palette.muted,
                stringResource(R.string.waiting_for_rules),
                null,
                null,
            )
        }

        status.paused -> {
            StatusPanel(
                AppIcons.PowerSettingsNew,
                palette.muted,
                stringResource(R.string.pocora_paused),
                scheduleName,
                null,
            )
        }

        else -> {
            StatusPanel(
                if (status.allowed) AppIcons.Wifi else AppIcons.WifiOff,
                if (status.allowed) palette.allowed else palette.muted,
                internetSentence(status.allowed, status.until, toChild = true),
                scheduleName,
                status.today,
                status.changes,
            ) {
                val perMark = status.bytesPerMark
                when {
                    status.quotaReached -> {
                        Text(
                            text = stringResource(R.string.data_used_up),
                            color = palette.alert,
                            fontSize = Dimens.caption,
                        )
                    }

                    !status.allowed -> {
                        Text(
                            text = stringResource(R.string.offline_apps_work),
                            color = palette.muted,
                            fontSize = Dimens.caption,
                        )
                    }

                    perMark != null -> {
                        Text(
                            text =
                                stringResource(
                                    R.string.data_this_half_hour,
                                    format.megabytes(status.markBytes),
                                    format.megabytes(perMark),
                                ),
                            color = palette.muted,
                            fontSize = Dimens.caption,
                        )
                        ProgressLine(share = status.markBytes.toFloat() / perMark, color = AppColors.green)
                    }
                }
            }
        }
    }
}
