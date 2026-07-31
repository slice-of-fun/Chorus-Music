

package pushkar.chorus.music.ui.utils

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.UriHandler
import pushkar.chorus.music.R


fun UriHandler.safeOpenUri(context: Context, uri: String) {
    if (uri.isBlank()) return
    
    runCatching {
        openUri(uri)
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.error_no_stream).replace("stream", "app"), 
            Toast.LENGTH_SHORT
        ).show()
    }
}
