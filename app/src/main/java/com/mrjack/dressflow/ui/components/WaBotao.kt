package com.mrjack.dressflow.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.mrjack.dressflow.ui.navigation.LocalNavController
import com.mrjack.dressflow.ui.navigation.Screen
import com.mrjack.dressflow.ui.navigation.WaDeeplink

/** Ícone WhatsApp desenhado como vetor (logo oficial público). */
private val WhatsAppIcon: ImageVector
    get() = ImageVector.Builder(
        name = "WhatsApp",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f, strokeAlpha = 1f,
            strokeLineWidth = 0f, strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter, strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero,
        ) {
            // WhatsApp logo path (simplified)
            moveTo(12f, 2f)
            curveTo(6.477f, 2f, 2f, 6.477f, 2f, 12f)
            curveTo(2f, 13.89f, 2.525f, 15.657f, 3.438f, 17.168f)
            lineTo(2.05f, 21.95f)
            lineTo(6.947f, 20.585f)
            curveTo(8.413f, 21.447f, 10.147f, 21.937f, 12f, 21.937f)
            curveTo(17.523f, 21.937f, 22f, 17.46f, 22f, 11.937f)
            curveTo(22f, 6.477f, 17.523f, 2f, 12f, 2f)
            close()
            // Inner phone path
            moveTo(16.735f, 15.305f)
            curveTo(16.495f, 15.967f, 15.553f, 16.52f, 14.81f, 16.683f)
            curveTo(14.303f, 16.793f, 13.645f, 16.88f, 11.46f, 15.98f)
            curveTo(8.673f, 14.847f, 6.855f, 12.013f, 6.715f, 11.827f)
            curveTo(6.58f, 11.643f, 5.633f, 10.38f, 5.633f, 9.073f)
            curveTo(5.633f, 7.767f, 6.3f, 7.127f, 6.567f, 6.853f)
            curveTo(6.787f, 6.627f, 7.147f, 6.52f, 7.487f, 6.52f)
            curveTo(7.6f, 6.52f, 7.7f, 6.527f, 7.793f, 6.533f)
            curveTo(8.06f, 6.547f, 8.193f, 6.567f, 8.367f, 6.993f)
            curveTo(8.58f, 7.52f, 9.1f, 8.827f, 9.16f, 8.947f)
            curveTo(9.22f, 9.073f, 9.26f, 9.22f, 9.18f, 9.38f)
            curveTo(9.107f, 9.547f, 9.027f, 9.62f, 8.9f, 9.767f)
            curveTo(8.773f, 9.913f, 8.653f, 10.027f, 8.527f, 10.18f)
            curveTo(8.413f, 10.313f, 8.28f, 10.46f, 8.427f, 10.727f)
            curveTo(8.573f, 10.987f, 9.093f, 11.82f, 9.847f, 12.493f)
            curveTo(10.82f, 13.36f, 11.62f, 13.64f, 11.913f, 13.753f)
            curveTo(12.133f, 13.84f, 12.393f, 13.82f, 12.547f, 13.653f)
            curveTo(12.74f, 13.44f, 12.98f, 13.08f, 13.22f, 12.727f)
            curveTo(13.393f, 12.473f, 13.613f, 12.44f, 13.847f, 12.527f)
            curveTo(14.087f, 12.607f, 15.387f, 13.253f, 15.647f, 13.38f)
            curveTo(15.907f, 13.507f, 16.08f, 13.567f, 16.14f, 13.68f)
            curveTo(16.2f, 13.793f, 16.2f, 14.313f, 15.96f, 14.92f)
            close()
        }
    }.build()

@Composable
fun WaBotao(telefone: String?, modifier: Modifier = Modifier) {
    if (telefone.isNullOrBlank()) return
    val navController = LocalNavController.current
    val icon = remember { WhatsAppIcon }
    IconButton(
        onClick = {
            val digits = telefone.filter { it.isDigit() }
            if (digits.isNotBlank()) {
                WaDeeplink.targetPhone.value = digits
                navController.navigate(Screen.WhatsApp.route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "WhatsApp",
            tint = Color(0xFF25D366),
            modifier = Modifier.size(26.dp),
        )
    }
}
