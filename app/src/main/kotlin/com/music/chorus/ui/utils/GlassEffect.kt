package pushkar.chorus.music.ui.utils

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.glassEffect(
    isDark: Boolean,
    alpha: Float = 0.5f,
    blurRadius: Float = 32f
): Modifier = composed {
    val backgroundColor = if (isDark) {
        Color.Black.copy(alpha = alpha)
    } else {
        Color.White.copy(alpha = alpha)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this
            .background(backgroundColor)
            .blur(blurRadius.dp)
    } else {
        this.background(backgroundColor)
    }
}
