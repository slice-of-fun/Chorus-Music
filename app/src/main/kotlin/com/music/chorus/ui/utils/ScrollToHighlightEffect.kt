package pushkar.chorus.music.ui.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun Modifier.scrollToOnHighlight(
    scrollState: ScrollState,
    isHighlighted: Boolean,
    delayMs: Long = 300L
): Modifier {
    val targetScroll = remember { mutableStateOf<Int?>(null) }
    
    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    
    
    val targetScreenY = screenHeightPx / 2f

    LaunchedEffect(isHighlighted, targetScroll.value) {
        if (isHighlighted && targetScroll.value != null) {
            delay(delayMs) 
            scrollState.animateScrollTo(targetScroll.value!!)
        }
    }

    return if (isHighlighted) {
        this.onGloballyPositioned { coordinates ->
            if (targetScroll.value == null) {
                
                val currentScreenY = coordinates.positionInWindow().y
                
                
                val scrollDelta = currentScreenY - targetScreenY
                
                
                var newScroll = scrollState.value + scrollDelta.toInt()
                if (newScroll < 0) newScroll = 0
                
                targetScroll.value = newScroll
            }
        }
    } else {
        this
    }
}
