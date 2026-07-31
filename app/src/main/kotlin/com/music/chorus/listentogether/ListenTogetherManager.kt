package pushkar.chorus.music.listentogether

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class RoomRole {
    NONE,
    HOST,
    GUEST
}

class ListenTogetherManager {
    val guestPlaybackRestricted: Flow<Boolean> = flowOf(false)
    val isGuestPlaybackRestricted: Boolean = false
    val role: Flow<RoomRole> = flowOf(RoomRole.NONE)
    
    fun suggestTrack(trackInfo: Any?) {}
}
