package pt.nunosid.tgmedialibrary.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val ReplayInk = Color(0xFF111111)
val ReplayPaper = Color(0xFFF3F0E8)
val ReplayPanel = Color(0xFFFFFDF7)
val ReplayMuted = Color(0xFF6F6B62)
val ReplaySoftLine = Color(0xFFC9C4B8)
val ReplayAcid = Color(0xFFE7FF3F)
val ReplayCyan = Color(0xFF62E7FF)
val ReplayPink = Color(0xFFFF6FAE)
val ReplayDanger = Color(0xFFFF4C4C)

private val AppColors = lightColorScheme(
    primary = ReplayAcid,
    onPrimary = ReplayInk,
    primaryContainer = ReplayAcid,
    onPrimaryContainer = ReplayInk,
    secondary = ReplayCyan,
    onSecondary = ReplayInk,
    secondaryContainer = ReplayCyan,
    onSecondaryContainer = ReplayInk,
    tertiary = ReplayPink,
    onTertiary = ReplayInk,
    tertiaryContainer = ReplayPink,
    onTertiaryContainer = ReplayInk,
    error = ReplayDanger,
    onError = Color.White,
    background = ReplayPaper,
    onBackground = ReplayInk,
    surface = ReplayPanel,
    onSurface = ReplayInk,
    surfaceVariant = Color(0xFFECE8DE),
    onSurfaceVariant = ReplayMuted,
    outline = ReplayInk,
    outlineVariant = ReplaySoftLine
)

private val Square = RoundedCornerShape(0.dp)

private val ReplayShapes = Shapes(
    extraSmall = Square,
    small = Square,
    medium = Square,
    large = Square,
    extraLarge = Square
)

@Composable
fun TelegramMediaLibraryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        shapes = ReplayShapes,
        content = content
    )
}
