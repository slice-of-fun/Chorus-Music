

package pushkar.chorus.music.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaylist(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val images: List<SpotifyImage> = emptyList(),
    val owner: SpotifyPlaylistOwner? = null,
    val tracks: SpotifyPlaylistTracksRef? = null,
    val uri: String? = null,
    val public: Boolean? = null,
    val collaborative: Boolean = false,
    @SerialName("snapshot_id") val snapshotId: String? = null,
)

@Serializable
data class SpotifyPlaylistOwner(
    val id: String = "",
    @SerialName("display_name") val displayName: String? = null,
    val uri: String? = null,
)


@Serializable
data class SpotifyPlaylistTracksRef(
    val total: Int? = null,
    val href: String? = null,
)


@Serializable
data class SpotifyPlaylistTrack(
    @SerialName("added_at") val addedAt: String? = null,
    val track: SpotifyTrack? = null,
    @SerialName("is_local") val isLocal: Boolean = false,
    val uid: String? = null,
)
