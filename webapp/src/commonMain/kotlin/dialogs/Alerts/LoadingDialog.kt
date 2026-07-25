package dialogs.Alerts

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import model.LocalMongeColors
import model.darker
import serialization.SettingsManager
import ui.theme.LocalMongeDimens

@Composable
fun LoadingDialog(
    title: String = "Načítání souboru",
    fileName: String? = null,
) {
    val ui = SettingsManager.current.UIscale / 75f
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current

    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset(0, 0)
        },
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        colors.background.copy(alpha = 0.95f).darker(0.9f),
                        RoundedCornerShape(dimens.radiusMd)
                    )
                    .border(dimens.borderThin, colors.base, RoundedCornerShape(dimens.radiusMd))
                    .width(280f * ui.dp)
                    .padding(24f * ui.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16f * ui.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        fontSize = 16f * ui.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        textAlign = TextAlign.Center
                    )

                    val spinnerSize = (36f * ui).dp
                    val strokeWidth = (3f * ui).dp

                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .size(spinnerSize)
                            .rotate(rotation)
                    ) {
                        drawArc(
                            color = colors.base.copy(alpha = 0.3f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = colors.selected,
                            startAngle = 0f,
                            sweepAngle = 90f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    if (!fileName.isNullOrBlank()) {
                        Text(
                            text = fileName,
                            fontSize = 13f * ui.sp,
                            color = colors.text.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
