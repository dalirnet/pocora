package ir.pocora.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.pocora.R
import ir.pocora.model.Peer
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.Avatar

private val BUBBLE = 58.dp
private val RING = 2.5.dp
private val GAP = 3.dp
private val OUTER = BUBBLE + (RING + GAP) * 2
private val DOT = 18.dp
private const val DIAGONAL = 0.7071f
private val ITEM_WIDTH = 72.dp

// The children along the top of Home: an avatar each, the name under it, and a dot for whether the phone is
// connected. The child shown has a violet ring. The last bubble adds a child. Tapping a child switches at once.
@Composable
fun ChildRow(
    children: List<Peer>,
    selected: String?,
    online: (String) -> Boolean,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
        items(children, key = { it.id }) { child ->
            ChildBubble(child.name, child.id == selected, online(child.id)) { onSelect(child.id) }
        }
        item { AddBubble(onAdd) }
    }
}

@Composable
private fun ChildBubble(
    name: String,
    selected: Boolean,
    online: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        modifier =
            Modifier
                .width(ITEM_WIDTH)
                .clickable(
                    interactionSource =
                        remember {
                            MutableInteractionSource()
                        },
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The ring, a small gap, then the avatar. The dot is drawn last, over the ring, so the ring never hides it.
        Box(modifier = Modifier.size(OUTER), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(OUTER)
                        .border(RING, if (selected) palette.brand else Color.Transparent, CircleShape),
            )
            Avatar(name, BUBBLE)
            OnlineDot(
                online = online,
                // On the ring's lower edge at 45 degrees: the outer radius times the cosine of 45.
                modifier = Modifier.offset(x = OUTER / 2 * DIAGONAL, y = OUTER / 2 * DIAGONAL),
            )
        }
        Text(
            text = name,
            color = if (selected) palette.text else palette.muted,
            fontSize = Dimens.caption,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddBubble(onClick: () -> Unit) {
    val palette = LocalPalette.current
    val label = stringResource(R.string.add_short)
    Column(
        modifier = Modifier.width(ITEM_WIDTH).clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(OUTER), contentAlignment = Alignment.Center) {
            // A dashed circle with a plus: room for one more child.
            Box(
                modifier =
                    Modifier
                        .size(BUBBLE)
                        .drawBehind {
                            val stroke = 2.dp.toPx()
                            drawCircle(
                                palette.brand.copy(alpha = 0.6f),
                                radius = size.minDimension / 2 - stroke / 2,
                                center = Offset(size.width / 2, size.height / 2),
                                style =
                                    Stroke(
                                        stroke,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                                    ),
                            )
                        }.background(palette.brand.copy(alpha = 0.06f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = palette.brand, modifier = Modifier.size(26.dp))
            }
        }
        Text(text = label, color = palette.brand, fontSize = Dimens.caption, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// Connected: a solid green dot. Not connected: a hollow grey ring, so the two differ by shape, not only colour.
// A halo in the background colour sets it apart from the avatar and the ring under it.
@Composable
private fun OnlineDot(
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier.size(DOT).background(palette.backgroundTop, CircleShape).padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(DOT)
                    .let {
                        if (online) {
                            it.background(
                                palette.done,
                                CircleShape,
                            )
                        } else {
                            it.border(2.5.dp, palette.muted, CircleShape)
                        }
                    },
        )
    }
}
