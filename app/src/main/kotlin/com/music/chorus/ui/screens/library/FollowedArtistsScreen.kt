package pushkar.chorus.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import pushkar.chorus.music.LocalPlayerAwareWindowInsets
import pushkar.chorus.music.R
import pushkar.chorus.music.constants.ArtistSortDescendingKey
import pushkar.chorus.music.constants.ArtistSortType
import pushkar.chorus.music.constants.ArtistSortTypeKey
import pushkar.chorus.music.constants.ArtistViewTypeKey
import pushkar.chorus.music.constants.CONTENT_TYPE_ARTIST
import pushkar.chorus.music.constants.CONTENT_TYPE_HEADER
import pushkar.chorus.music.constants.GridItemSize
import pushkar.chorus.music.constants.GridItemsSizeKey
import pushkar.chorus.music.constants.GridThumbnailHeight
import pushkar.chorus.music.constants.LibraryViewType
import pushkar.chorus.music.ui.component.EmptyPlaceholder
import pushkar.chorus.music.ui.component.LibraryArtistGridItem
import pushkar.chorus.music.ui.component.LibraryArtistListItem
import pushkar.chorus.music.ui.component.LocalMenuState
import pushkar.chorus.music.ui.component.SortHeader
import pushkar.chorus.music.utils.rememberEnumPreference
import pushkar.chorus.music.utils.rememberPreference
import pushkar.chorus.music.viewmodels.LibraryArtistsViewModel

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun FollowedArtistsScreen(
    navController: NavController,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    var viewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.GRID)

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        ArtistSortTypeKey,
        ArtistSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSortDescendingKey, true)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    var searchQuery by remember { mutableStateOf("") }
    val allArtists by viewModel.allArtists.collectAsState()
    val followedArtists = remember(allArtists, searchQuery) {
        allArtists
            .filter { it.artist.bookmarkedAt != null }
            .filter { it.artist.name.contains(searchQuery, ignoreCase = true) }
    }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Followed Artists") },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search followed artists...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        )

        val headerContent = @Composable {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp),
            ) {
                SortHeader(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                    sortTypeText = { type ->
                        when (type) {
                            ArtistSortType.CREATE_DATE -> R.string.sort_by_create_date
                            ArtistSortType.NAME -> R.string.sort_by_name
                            ArtistSortType.SONG_COUNT -> R.string.sort_by_song_count
                            ArtistSortType.PLAY_TIME -> R.string.sort_by_play_time
                        }
                    },
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = pluralStringResource(
                        R.plurals.n_artist,
                        followedArtists.size,
                        followedArtists.size
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                FlowRow(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    LibraryViewType.entries.forEachIndexed { index, type ->
                        ToggleButton(
                            checked = viewType == type,
                            onCheckedChange = { viewType = type },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                LibraryViewType.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.semantics { role = Role.RadioButton },
                        ) {
                            Icon(
                                painter = painterResource(
                                    when (type) {
                                        LibraryViewType.LIST -> R.drawable.list
                                        LibraryViewType.GRID -> R.drawable.grid_view
                                    }
                                ),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.weight(1f),
        ) {
            when (viewType) {
                LibraryViewType.LIST ->
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    ) {
                        item(
                            key = "header",
                            contentType = CONTENT_TYPE_HEADER,
                        ) {
                            headerContent()
                        }

                        if (followedArtists.isEmpty()) {
                            item(key = "empty_placeholder") {
                                EmptyPlaceholder(
                                    icon = R.drawable.artist,
                                    text = "No followed artists found",
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        items(
                            items = followedArtists.distinctBy { it.id },
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ARTIST },
                        ) { artist ->
                            LibraryArtistListItem(
                                navController = navController,
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                modifier = Modifier.animateItem(),
                                artist = artist
                            )
                        }
                    }

                LibraryViewType.GRID ->
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Adaptive(
                            minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp,
                        ),
                        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    ) {
                        item(
                            key = "header",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = CONTENT_TYPE_HEADER,
                        ) {
                            headerContent()
                        }

                        if (followedArtists.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyPlaceholder(
                                    icon = R.drawable.artist,
                                    text = "No followed artists found",
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        items(
                            items = followedArtists.distinctBy { it.id },
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ARTIST },
                        ) { artist ->
                            LibraryArtistGridItem(
                                navController = navController,
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                modifier = Modifier.animateItem(),
                                artist = artist
                            )
                        }
                    }
            }
        }
    }
}
