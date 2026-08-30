

package pushkar.chorus.music.ui.screens.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import pushkar.chorus.music.LocalDatabase
import pushkar.chorus.music.LocalPlayerAwareWindowInsets
import pushkar.chorus.music.LocalPlayerConnection
import pushkar.chorus.music.R
import pushkar.chorus.music.constants.AppBarHeight
import pushkar.chorus.music.constants.HideExplicitKey
import pushkar.chorus.music.constants.ShowArtistDescriptionKey
import pushkar.chorus.music.constants.ShowArtistSubscriberCountKey
import pushkar.chorus.music.constants.ShowMonthlyListenersKey
import pushkar.chorus.music.db.entities.ArtistEntity
import pushkar.chorus.music.extensions.toMediaItem
import pushkar.chorus.music.models.toMediaMetadata
import pushkar.chorus.music.playback.queues.ListQueue
import pushkar.chorus.music.playback.queues.YouTubeQueue
import pushkar.chorus.music.ui.component.AlbumGridItem
import pushkar.chorus.music.ui.component.ExpandableText
import pushkar.chorus.music.ui.component.HideOnScrollFAB
import pushkar.chorus.music.ui.component.IconButton
import pushkar.chorus.music.ui.component.LinkSegment
import pushkar.chorus.music.ui.component.LocalMenuState
import pushkar.chorus.music.ui.component.NavigationTitle
import pushkar.chorus.music.ui.component.SongListItem
import pushkar.chorus.music.ui.component.YouTubeGridItem
import pushkar.chorus.music.ui.component.YouTubeListItem
import pushkar.chorus.music.ui.component.shimmer.ButtonPlaceholder
import pushkar.chorus.music.ui.component.shimmer.ListItemPlaceHolder
import pushkar.chorus.music.ui.component.shimmer.ShimmerHost
import pushkar.chorus.music.ui.component.shimmer.TextPlaceholder
import pushkar.chorus.music.ui.menu.AlbumMenu
import pushkar.chorus.music.ui.menu.SongMenu
import pushkar.chorus.music.ui.menu.YouTubeAlbumMenu
import pushkar.chorus.music.ui.menu.YouTubeArtistMenu
import pushkar.chorus.music.ui.menu.YouTubePlaylistMenu
import pushkar.chorus.music.ui.menu.YouTubeSongMenu
import pushkar.chorus.music.ui.utils.backToMain
import pushkar.chorus.music.ui.utils.fadingEdge
import pushkar.chorus.music.ui.utils.isScrollingUp
import pushkar.chorus.music.ui.utils.resize
import pushkar.chorus.music.utils.listItemShape
import pushkar.chorus.music.utils.rememberPreference
import pushkar.chorus.music.viewmodels.ArtistViewModel
import com.valentinilk.shimmer.shimmer
import pushkar.chorus.music.artistvideo.ArtistVideo
import pushkar.chorus.music.constants.ShowArtistVideoKey
import pushkar.chorus.music.constants.ShowArtistBackgroundVideoKey
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pushkar.chorus.music.canvas.AppleMusicArtistBackgroundProvider

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isGuest = false
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()
    val artistVideoUrl by viewModel.artistVideoUrl.collectAsState()
    val artistVideoSong by viewModel.artistVideoSong.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val showArtistDescription by rememberPreference(key = ShowArtistDescriptionKey, defaultValue = true)
    val showArtistSubscriberCount by rememberPreference(key = ShowArtistSubscriberCountKey, defaultValue = true)
    val showMonthlyListeners by rememberPreference(key = ShowMonthlyListenersKey, defaultValue = true)
    val dataSaverEnabled by rememberPreference(key = pushkar.chorus.music.constants.DataSaverEnabledKey, defaultValue = false)
    val showArtistVideoPref by rememberPreference(key = ShowArtistVideoKey, defaultValue = true)
    val showArtistVideo = if (dataSaverEnabled) false else showArtistVideoPref
    val showArtistBackgroundVideoPref by rememberPreference(key = ShowArtistBackgroundVideoKey, defaultValue = true)
    val showArtistBackgroundVideo = if (dataSaverEnabled) false else showArtistBackgroundVideoPref

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current

    
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val headerOffset = with(density) {
        -(systemBarsTopPadding + AppBarHeight).roundToPx()
    }

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    LaunchedEffect(libraryArtist) {
        
        showLocal = libraryArtist?.artist?.isLocal == true
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (artistPage == null && !showLocal) {
                item(key = "shimmer") {
                    ShimmerHost (
                        modifier = Modifier
                    ) {
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shimmer()
                                    .background(MaterialTheme.colorScheme.onSurface)
                                    .fadingEdge(
                                        top = systemBarsTopPadding + AppBarHeight,
                                        bottom = 200.dp,
                                    ),
                            )
                        }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .padding(bottom = 16.dp)
                            )

                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                
                                ButtonPlaceholder(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(52.dp)
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    
                                    ButtonPlaceholder(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(52.dp)
                                    )

                                    
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .shimmer()
                                            .background(
                                                MaterialTheme.colorScheme.onSurface,
                                                RoundedCornerShape(26.dp)
                                            )
                                    )
                                }
                            }
                        }
                        
                        repeat(6) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            } else {
                item(key = "header") {
                    val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl
                    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name

                    var backgroundVideoUrl by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(artistName, showArtistBackgroundVideo) {
                        if (artistName != null && showArtistBackgroundVideo) {
                            withContext(Dispatchers.IO) {
                                backgroundVideoUrl = AppleMusicArtistBackgroundProvider.getByArtistName(artistName)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (thumbnail != null || backgroundVideoUrl != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            ) {
                                if (thumbnail != null) {
                                    AsyncImage(
                                        model = thumbnail.resize(1200, 1200),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().blur(50.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        alignment = Alignment.Center
                                    )
                                    AsyncImage(
                                        model = thumbnail.resize(1200, 1200),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        alignment = Alignment.Center
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        MaterialTheme.colorScheme.background
                                                    )
                                                )
                                            )
                                    )
                                }
                                if (backgroundVideoUrl != null && showArtistBackgroundVideo) {
                                    ArtistVideo(
                                        videoUrl = backgroundVideoUrl!!,
                                        modifier = Modifier.fillMaxSize(),
                                        onClick = { }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
                            ) {
                                    if (showArtistVideo && !(showArtistBackgroundVideo && backgroundVideoUrl != null)) {
                                        artistVideoUrl?.let { videoUrl ->
                                            artistPage?.artist?.radioEndpoint?.let { radioEndpoint ->
                                                Spacer(modifier = Modifier.width(5.dp))
                                                ArtistVideo(
                                                    videoUrl = videoUrl,
                                                    modifier = Modifier
                                                        .size(45.dp),
                                                    onClick = {
                                                        val watchEndpoint = artistVideoSong?.endpoint
                                                            ?: artistPage.artist.radioEndpoint
                                                        watchEndpoint?.let {
                                                            playerConnection.playQueue(YouTubeQueue(it))
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(5.dp))

                                    Text(
                                        text = artistName ?: "Unknown",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 32.sp,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }

                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                ) {
                                    if (showArtistSubscriberCount) {
                                        artistPage?.subscriberCountText?.takeIf { it.isNotBlank() }?.let { subscribers ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.artist_screen),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "$subscribers ${stringResource(R.string.subscribers)}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))

                                    artistPage?.monthlyListenerCount?.takeIf { it.isNotBlank() }?.let { monthlyListeners ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.graphic_eq),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "$monthlyListeners ${stringResource(R.string.monthly_listeners)}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 24.dp)
                                ) {
                                    if (!showLocal && !isGuest) {
                                        artistPage?.artist?.radioEndpoint?.let { radioEndpoint ->
                                            OutlinedButton(
                                                onClick = { playerConnection.playQueue(YouTubeQueue(radioEndpoint)) },
                                                modifier = Modifier.size(48.dp),
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.radio),
                                                    contentDescription = stringResource(R.string.radio),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    val canShuffle = if (showLocal) librarySongs.isNotEmpty() && !isGuest else !isGuest && artistPage?.artist?.shuffleEndpoint != null
                                    if (canShuffle) {
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                if (showLocal) {
                                                    val shuffledSongs = librarySongs.shuffled()
                                                    if (shuffledSongs.isNotEmpty()) {
                                                        playerConnection.playQueue(
                                                            ListQueue(
                                                                title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                                                items = shuffledSongs.map { it.toMediaItem() }
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    artistPage?.artist?.shuffleEndpoint?.let { shuffleEndpoint ->
                                                        playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp)
                                                .size(56.dp),
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.shuffle),
                                                contentDescription = stringResource(R.string.shuffle),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }

                                    val isFollowed = libraryArtist?.artist?.bookmarkedAt != null

                                    if (isFollowed) {
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                database.query {
                                                    val artist = libraryArtist?.artist
                                                    if (artist != null) {
                                                        update(artist.toggleLike())
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.subscribed),
                                                contentDescription = stringResource(R.string.subscribed),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                database.query {
                                                    val artist = libraryArtist?.artist
                                                    if (artist != null) {
                                                        update(artist.toggleLike())
                                                    } else {
                                                        artistPage?.artist?.let {
                                                            insert(
                                                                pushkar.chorus.music.db.entities.ArtistEntity(
                                                                    id = it.id,
                                                                    name = it.title,
                                                                    channelId = it.channelId,
                                                                    thumbnailUrl = it.thumbnail,
                                                                ).toggleLike()
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.subscribe),
                                                contentDescription = stringResource(R.string.subscribe),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }


                if (showLocal) {
                    if (librarySongs.isNotEmpty()) {
                        item(key = "local_songs_title") {
                            NavigationTitle(
                                title = stringResource(R.string.songs),
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                }
                            )
                        }

                        val filteredLibrarySongs = if (hideExplicit) {
                            librarySongs.filter { !it.song.explicit }
                        } else {
                            librarySongs
                        }
                        itemsIndexed(
                            items = filteredLibrarySongs,
                            key = { index, item -> "local_song_${item.id}_$index" }
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                showInLibraryIcon = true,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                shape = listItemShape(index, filteredLibrarySongs.size),
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                                        items = librarySongs.map { it.toMediaItem() },
                                                        startIndex = index
                                                    )
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }

                    if (libraryAlbums.isNotEmpty()) {
                        item(key = "local_albums_title") {
                            NavigationTitle(
                                title = stringResource(R.string.albums),
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/albums")
                                }
                            )
                        }

                        item(key = "local_albums_list") {
                            val filteredLibraryAlbums = if (hideExplicit) {
                                libraryAlbums.filter { !it.album.explicit }
                            } else {
                                libraryAlbums
                            }
                            LazyRow(
                                contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                            ) {
                                items(
                                    items = filteredLibraryAlbums,
                                    key = { "local_album_${it.id}_${filteredLibraryAlbums.indexOf(it)}" }
                                ) { album ->
                                    AlbumGridItem(
                                        album = album,
                                        isActive = mediaMetadata?.album?.id == album.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("album/${album.id}")
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        AlbumMenu(
                                                            originalAlbum = album,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                } else {
                    artistPage?.sections?.fastForEach { section ->
                        if (section.items.isNotEmpty()) {
                            item(key = "section_${section.title}") {
                                NavigationTitle(
                                    title = section.title,
                                    modifier = Modifier.animateItem(),
                                    onClick = section.moreEndpoint?.let {
                                        {
                                            navController.navigate(
                                                "artist/${viewModel.artistId}/items?browseId=${it.browseId}?params=${it.params}",
                                            )
                                        }
                                    } ?: { },
                                )
                            }
                        }

                        if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                            itemsIndexed(
                                items = section.items.distinctBy { it.id },
                                key = { _, it -> "youtube_song_${it.id}" },
                            ) { index, song ->
                                YouTubeListItem(
                                    item = song as SongItem,
                                    isActive = mediaMetadata?.id == song.id,
                                    isPlaying = isPlaying,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = listItemShape(index, section.items.distinctBy { it.id }.size),
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = {
                                                if (song.id == mediaMetadata?.id) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue(
                                                            WatchEndpoint(videoId = song.id),
                                                            song.toMediaMetadata()
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        )
                                        .animateItem(),
                                )
                            }
                        } else {
                            item(key = "section_list_${section.title}") {
                                LazyRow(
                                    contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                                ) {
                                    items(
                                        items = section.items.distinctBy { it.id },
                                        key = { "youtube_album_${it.id}" },
                                    ) { item ->
                                        YouTubeGridItem(
                                            item = item,
                                            isActive = when (item) {
                                                is SongItem -> mediaMetadata?.id == item.id
                                                is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                else -> false
                                            },
                                            isPlaying = isPlaying,
                                            coroutineScope = coroutineScope,
                                            thumbnailRatio = 1f, 
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        when (item) {
                                                            is SongItem ->
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue(
                                                                        WatchEndpoint(videoId = item.id),
                                                                        item.toMediaMetadata()
                                                                    ),
                                                                )

                                                            is AlbumItem -> navController.navigate("album/${item.id}")
                                                            is ArtistItem -> navController.navigate("artist/${item.id}")
                                                            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            when (item) {
                                                                is SongItem ->
                                                                    YouTubeSongMenu(
                                                                        song = item,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss,
                                                                    )

                                                                is AlbumItem ->
                                                                    YouTubeAlbumMenu(
                                                                        albumItem = item,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss,
                                                                    )

                                                                is ArtistItem ->
                                                                    YouTubeArtistMenu(
                                                                        artist = item,
                                                                        onDismiss = menuState::dismiss,
                                                                    )

                                                                is PlaylistItem ->
                                                                    YouTubePlaylistMenu(
                                                                        playlist = item,
                                                                        coroutineScope = coroutineScope,
                                                                        onDismiss = menuState::dismiss,
                                                                    )
                                                            }
                                                        }
                                                    },
                                                )
                                                .animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!showLocal && showArtistDescription && artistPage != null) {
                    val description = artistPage.description
                    val descriptionRuns = artistPage.descriptionRuns
                    
                    if (!description.isNullOrEmpty() || !descriptionRuns.isNullOrEmpty()) {
                        item(key = "section_description") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                ExpandableText(
                                    text = description.orEmpty(),
                                    runs = descriptionRuns?.map {
                                        LinkSegment(
                                            text = it.text,
                                            url = it.navigationEndpoint?.urlEndpoint?.url
                                        )
                                    },
                                    collapsedMaxLines = 3
                                )
                            }
                        }
                    }
                }
            }
        }

        val isScrollingUp = lazyListState.isScrollingUp()
        val showLocalFab = librarySongs.isNotEmpty() && libraryArtist?.artist?.isLocal != true
        
        
        HideOnScrollFAB(
            visible = showLocalFab,
            lazyListState = lazyListState,
            icon = if (showLocal) R.drawable.language else R.drawable.library_music,
            onClick = {
                showLocal = showLocal.not()
                if (!showLocal && artistPage == null) viewModel.fetchArtistsFromYTM()
            }
        )
        
        
        val canPlayAll = !isGuest && (
            (showLocal && librarySongs.isNotEmpty()) || 
            (!showLocal && artistPage?.sections?.any { 
                (it.items.firstOrNull() as? SongItem)?.album != null 
            } == true)
        )

        if (canPlayAll) {
             androidx.compose.animation.AnimatedVisibility(
                visible = isScrollingUp,
                enter = androidx.compose.animation.slideInVertically { it * 2 },
                exit = androidx.compose.animation.slideOutVertically { it * 2 },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    )
                    
                    
                    .padding(bottom = if (showLocalFab) 120.dp else 0.dp)
            ) {
                val onPlayAllClick: () -> Unit = {
                     if (showLocal) {
                         if (librarySongs.isNotEmpty()) {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                    items = librarySongs.map { it.toMediaItem() }
                                )
                            )
                        }
                    } else if (artistPage != null) {
                        val songSection = artistPage.sections.find { section ->
                            (section.items.firstOrNull() as? SongItem)?.album != null
                        }
                        
                        val moreEndpoint = songSection?.moreEndpoint
                        if (moreEndpoint != null) {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val result = YouTube.artistItems(moreEndpoint).getOrNull()
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (result != null && result.items.isNotEmpty()) {
                                        val songs = result.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artistPage.artist.title,
                                                items = songs
                                            )
                                        )
                                    } else {
                                        
                                        val songs = songSection.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                                        if (songs.isNotEmpty()) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = artistPage.artist.title,
                                                    items = songs
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            } else if (songSection != null) {
                            
                            val songs = songSection.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                            playerConnection.playQueue(
                                ListQueue(
                                    title = artistPage.artist.title,
                                    items = songs
                                )
                            )
                        } else {
                            
                            val shuffleEndpoint = artistPage.artist.shuffleEndpoint
                            if (shuffleEndpoint != null) {
                                val endpoint = if (shuffleEndpoint.playlistId != null) {
                                    WatchEndpoint(
                                        playlistId = shuffleEndpoint.playlistId,
                                        params = null, 
                                        videoId = null 
                                    )
                                } else {
                                    shuffleEndpoint
                                }
                                playerConnection.playQueue(YouTubeQueue(endpoint))
                            }
                        }
                    }
                }

                androidx.compose.material3.FloatingActionButton(
                    modifier = Modifier.padding(16.dp),
                    onClick = onPlayAllClick
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = "Play All",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }


        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }

    TopAppBar(
        title = { if (!transparentAppBar) Text(artistPage?.artist?.title.orEmpty()) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = {
            IconButton(
                onClick = {
                    viewModel.artistPage?.artist?.shareLink?.let { link ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Artist Link", link)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(
                    painterResource(R.drawable.link),
                    contentDescription = null,
                )
            }
        },
        colors = if (transparentAppBar) {
            TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        } else {
            TopAppBarDefaults.topAppBarColors()
        }
    )
}
