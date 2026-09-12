package pt.nunosid.tgmedialibrary.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = lightColorScheme(
    primary = Color(0xFF0B74DE),
    onPrimary = Color.White,
    surface = Color(0xFFF8FAFC),
    background = Color.White,
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827)
)

@Composable
fun TelegramMediaLibraryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}
