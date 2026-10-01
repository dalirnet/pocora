package ir.pocora.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.ui.AppColors
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import kotlinx.coroutines.delay

// What the app says back: empty, loading, sending, saved, and could not reach.

// Nothing to show yet: a large soft icon, a title, one line on why or what to do, and an action when there is one.
// The compact form fits inside a card.
@Composable
fun EmptyState(
    icon: ImageVector,
    color: Color,
    title: String,
    text: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val circle = if (compact) 64.dp else 112.dp
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = if (compact) Dimens.small else Dimens.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) Dimens.small else Dimens.row),
    ) {
        Box(
            modifier = Modifier.size(circle).background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(circle * 0.62f).background(color.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(circle * 0.36f))
            }
        }
        Text(
            text = title,
            color = palette.text,
            fontSize = if (compact) Dimens.body else Dimens.heading,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (text != null) {
            Text(
                text = text,
                color = palette.muted,
                fontSize = Dimens.caption,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
        if (action != null) {
            SmallButton(
                text = action,
                onClick = onAction,
                filled = true,
                modifier = Modifier.padding(top = Dimens.tiny),
            )
        }
    }
}

// A grey block that softly pulses where content will appear.
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val palette = LocalPalette.current
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLISECONDS, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(modifier = modifier.height(height).alpha(pulse).background(palette.limited, RoundedCornerShape(height / 2)))
}

// Cards in the shape of what is loading: an icon, a title, two lines.
@Composable
fun LoadingCards(count: Int = 2) {
    repeat(count) {
        Card {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.row),
            ) {
                Skeleton(Modifier.size(44.dp), 44.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
                    Skeleton(Modifier.fillMaxWidth(0.6f), 16.dp)
                    Skeleton(Modifier.fillMaxWidth(0.4f), 12.dp)
                }
            }
            Skeleton(Modifier.fillMaxWidth(), 10.dp)
            Skeleton(Modifier.fillMaxWidth(0.8f), 10.dp)
        }
    }
}

// A small floating pill while a change is on its way to the other phone.
@Composable
fun BusyPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Row(
        modifier =
            modifier
                .shadow(8.dp, RoundedCornerShape(50), spotColor = palette.shadow)
                .background(palette.card, RoundedCornerShape(50))
                .padding(horizontal = Dimens.inside, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = palette.brand, strokeWidth = 2.dp)
        Text(text = text, color = palette.text, fontSize = Dimens.caption, fontWeight = FontWeight.Bold)
    }
}

private const val PULSE_MILLISECONDS = 800

// A short message that rises at the bottom and goes by itself: "Saved", "Sent". One at a time; a new one replaces it.
class ToastMessage(
    val text: String,
    val kind: Kind = Kind.DONE,
    // Tells two equal messages in a row apart, so the second one shows too.
    val id: Long = System.nanoTime(),
) {
    enum class Kind { DONE, PROBLEM }
}

object Toasts {
    var current by mutableStateOf<ToastMessage?>(null)
        private set

    fun show(
        text: String,
        kind: ToastMessage.Kind = ToastMessage.Kind.DONE,
    ) {
        current = ToastMessage(text, kind)
    }

    fun hide() {
        current = null
    }
}

// Draws the current toast over the screen. Place it last, inside the screen's root Box.
@Composable
fun BoxScope.ToastHost() {
    val message = Toasts.current
    LaunchedEffect(message?.id) {
        if (message != null) {
            delay(SHOWN_MILLISECONDS)
            Toasts.hide()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier =
            Modifier
                .align(
                    Alignment.BottomCenter,
                ).navigationBarsPadding()
                .padding(bottom = 96.dp, start = Dimens.edge, end = Dimens.edge),
    ) {
        // Keeps the last text while it slides away.
        val shown = message ?: return@AnimatedVisibility
        Toast(shown)
    }
}

@Composable
private fun Toast(message: ToastMessage) {
    val shade =
        when (message.kind) {
            ToastMessage.Kind.DONE -> listOf(AppColors.green, AppColors.teal)
            ToastMessage.Kind.PROBLEM -> listOf(AppColors.orange, AppColors.red)
        }
    val shape = RoundedCornerShape(50)
    Text(
        text = message.text,
        color = AppColors.onColor,
        fontSize = Dimens.label,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .widthIn(max = 420.dp)
                .shadow(12.dp, shape, spotColor = LocalPalette.current.shadow)
                .background(Brush.horizontalGradient(shade), shape)
                .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

// Keeps a Box around the whole app so the toast floats over every screen.
@Composable
fun WithToasts(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        ToastHost()
    }
}

private const val SHOWN_MILLISECONDS = 2_200L
