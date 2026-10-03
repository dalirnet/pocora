package ir.pocora.service

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import ir.pocora.R
import ir.pocora.ui.AppColors
import kotlin.math.ceil
import kotlin.math.min

// The status notification's words and data bar, drawn in the app's font and colours. Android lays notifications out
// without an app's own fonts, so they travel as pictures; the plain text is set beside them for screen readers.
// Notifications are light before Android 10, and follow the phone's dark theme from it, so the palette does too.
class StatusPicture(
    private val context: Context,
    private val rtl: Boolean,
) {
    // A line's role: the bold sentence, a muted line under it, or an orange one once the data ran out.
    enum class Kind(
        val sizeSp: Float,
    ) {
        TITLE(15f),
        DETAIL(13f),
        ALERT(13f),
    }

    // This half hour's data on the unfolded card, as Home shows it: a line and a bar under it.
    class Data(
        val text: String,
        val share: Float,
        val alert: Boolean,
    )

    private val metrics = context.resources.displayMetrics
    private val palette =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isNight()) AppColors.dark else AppColors.light

    // The card's width inside its margins. A picture wider than the room Android gives is scaled to fit.
    private val width = (metrics.widthPixels - dp(SIDES_DP)).toInt()

    fun line(
        text: String,
        kind: Kind,
    ): Bitmap {
        val paint = textPaint(kind)
        val layout = layout(text, paint, ceil(min(paint.measureText(text), width.toFloat())).toInt())
        return picture(layout.width, layout.height.toFloat()) { layout.draw(it) }
    }

    fun data(data: Data): Bitmap {
        val text = layout(data.text, textPaint(if (data.alert) Kind.ALERT else Kind.DETAIL), width)
        val barTop = text.height + dp(GAP_DP)
        return picture(width, barTop + dp(BAR_DP)) {
            text.draw(it)
            drawBar(it, barTop, data.share, data.alert)
        }
    }

    private fun picture(
        width: Int,
        height: Float,
        draw: (Canvas) -> Unit,
    ): Bitmap =
        Bitmap
            .createBitmap(
                width.coerceAtLeast(1),
                ceil(height).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            ).also {
                draw(Canvas(it))
            }

    // As ProgressLine: a pale track and a filled share, green while there is data and orange once it ran out.
    // It fills from the side the words start on.
    private fun drawBar(
        canvas: Canvas,
        top: Float,
        share: Float,
        alert: Boolean,
    ) {
        val bottom = top + dp(BAR_DP)
        val radius = dp(BAR_DP) / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.limited.toArgb() }
        canvas.drawRoundRect(0f, top, width.toFloat(), bottom, radius, radius, paint)
        val filled = width * share.coerceIn(MIN_SHARE, 1f)
        val start = if (rtl) width - filled else 0f
        paint.color = (if (alert) palette.alert else palette.done).toArgb()
        canvas.drawRoundRect(start, top, start + filled, bottom, radius, radius, paint)
    }

    private fun textPaint(kind: Kind) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface =
                ResourcesCompat.getFont(context, if (kind == Kind.TITLE) R.font.dana_bold else R.font.dana_regular)
            // In sp, so the phone's font size setting still counts.
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, kind.sizeSp, metrics)
            color =
                when (kind) {
                    Kind.TITLE -> palette.text
                    Kind.DETAIL -> palette.muted
                    Kind.ALERT -> palette.alert
                }.toArgb()
        }

    // One line, ending in an ellipsis when it is too long, laid out in the app's direction.
    private fun layout(
        text: String,
        paint: TextPaint,
        width: Int,
    ): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(true)
            .build()

    private fun dp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)

    private fun isNight(): Boolean =
        Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private companion object {
        // The notification card's own margins, both sides together.
        const val SIDES_DP = 40f
        const val BAR_DP = 6f
        const val GAP_DP = 6f
        const val MIN_SHARE = 0.02f
    }
}
