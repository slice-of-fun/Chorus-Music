

package pushkar.chorus.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import pushkar.chorus.music.constants.HideVideoSongsKey
import pushkar.chorus.music.constants.PlaylistSongSortDescendingKey
import pushkar.chorus.music.constants.PlaylistSongSortType
import pushkar.chorus.music.constants.PlaylistSongSortTypeKey
import pushkar.chorus.music.db.MusicDatabase
import pushkar.chorus.music.db.entities.PlaylistSong
import pushkar.chorus.music.extensions.reversed
import pushkar.chorus.music.extensions.toEnum
import pushkar.chorus.music.models.toMediaMetadata
import pushkar.chorus.music.utils.SyncUtils
import pushkar.chorus.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    private val importRepository: pushkar.chorus.music.spotifyimport.SpotifyImportRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist =
        database
            .playlist(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            database.playlistSongs(playlistId),
            context.dataStore.data
                .map {
                    Triple(
                        it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM),
                        it[PlaylistSongSortDescendingKey] ?: true,
                        it[HideVideoSongsKey] ?: false
                    )
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending, hideVideoSongs) ->
            val filteredSongs = if (hideVideoSongs) {
                songs.filter { !it.song.song.isVideo }
            } else {
                songs
            }
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> filteredSongs
                PlaylistSongSortType.CREATE_DATE -> filteredSongs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(compareBy(collator) { it.song.song.title })
                }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs
                        .sortedWith(compareBy(collator) { song -> song.song.artists.joinToString("") { it.name } })
                        .groupBy { it.song.album?.title }
                        .flatMap { (_, songsByAlbum) ->
                            songsByAlbum.sortedBy {
                                it.song.artists.joinToString(
                                    ""
                                ) { it.name }
                            }
                        }
                }

                PlaylistSongSortType.PLAY_TIME -> filteredSongs.sortedBy { it.song.song.totalPlayTime }
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            
            playlist.first { it != null }?.playlist?.browseId?.let { browseId ->
                syncUtils.syncPlaylist(browseId, playlistId)
            }
        }

        viewModelScope.launch {
            val sortedSongs =
                playlistSongs.first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, playlistSong ->
                    if (playlistSong.map.position != index) {
                        update(playlistSong.map.copy(position = index))
                    }
                }
            }
        }
    }

    private val _suggestions = MutableStateFlow<List<SongItem>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private var hasFetchedSuggestions = false

    fun fetchSuggestions() {
        if (hasFetchedSuggestions) return
        hasFetchedSuggestions = true

        viewModelScope.launch(Dispatchers.IO) {
            val songs = playlistSongs.value
            if (songs.isNotEmpty()) {
                val lastSong = songs.last().song.song
                if (lastSong.id.isNotEmpty()) {
                    YouTube.next(WatchEndpoint(videoId = lastSong.id)).onSuccess { nextResult ->
                        val existingIds = songs.map { it.song.song.id }.toSet()
                        _suggestions.value = nextResult.items
                            .filterIsInstance<SongItem>()
                            .filterNot { it.id in existingIds }
                            .take(10)
                    }
                }
            }
        }
    }

    fun addSuggestedSong(song: SongItem) {
        viewModelScope.launch(Dispatchers.IO) {
            playlist.value?.let { currentPlaylist ->
                database.transaction {
                    insert(song.toMediaMetadata())
                    addSongToPlaylist(currentPlaylist, listOf(song.id))
                }
                _suggestions.value = _suggestions.value.filter { it.id != song.id }
            }
        }
    }
    fun syncPlaylist(onProgress: (pushkar.chorus.music.spotifyimport.SpotifyImportProgressUi) -> Unit = {}, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = playlist.value?.playlist ?: return@launch
            try {
                if (p.syncUrl != null) {
                    val source = importRepository.addPlaylistByUrl(p.syncUrl)
                    val modifiedSource = when (source) {
                        is pushkar.chorus.music.spotifyimport.SpotifyImportSource.Playlist -> source.copy(playlist = source.playlist.copy(id = p.id.removePrefix("UNIVERSAL_PLAYLIST_").removePrefix("SPOTIFY_PLAYLIST_")))
                        is pushkar.chorus.music.spotifyimport.SpotifyImportSource.UniversalPlaylist -> source.copy(playlist = source.playlist.copy(id = p.id.removePrefix("UNIVERSAL_PLAYLIST_")))
                        else -> source
                    }
                    importRepository.importSources(listOf(source), onProgress)
                } else if (p.browseId != null) {
                    val playlistPage = YouTube.playlist(p.browseId)
                        .getOrNull() ?: return@launch
                    database.transaction {
                        clearPlaylist(p.id)
                        playlistPage.songs
                            .map(SongItem::toMediaMetadata)
                            .onEach { insert(it) }
                            .mapIndexed { position, song ->
                                pushkar.chorus.music.db.entities.PlaylistSongMap(
                                    songId = song.id,
                                    playlistId = p.id,
                                    position = position,
                                    setVideoId = song.setVideoId
                                )
                            }
                            .forEach { insert(it) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }
}
