package pushkar.chorus.music.chorusmusiccanvas

import pushkar.chorus.music.canvas.CanvasArtwork
import kotlinx.serialization.Serializable

@Serializable
data class chorusmusicCanvasManifest(
    val items: List<chorusmusicCanvasItem> = emptyList()
)

@Serializable
data class chorusmusicCanvasItem(
    val song: String,
    val artist: String,
    val url: String
)

object chorusmusicCanvasProvider {
    suspend fun fetchCanvases(): List<chorusmusicCanvasItem> {
        return emptyList()
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
    ): CanvasArtwork? {
        return null
    }
}
