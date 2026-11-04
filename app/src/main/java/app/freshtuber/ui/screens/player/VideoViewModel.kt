package app.freshtuber.ui.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.extras.CommunityExtras
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.data.video.VideoRepository
import app.freshtuber.domain.model.Comment
import app.freshtuber.domain.model.CommentSortOption
import app.freshtuber.domain.model.LikeStatus
import app.freshtuber.domain.model.PlaylistOption
import app.freshtuber.domain.model.VideoDetails
import app.freshtuber.innertube.InnerTubeCommand
import app.freshtuber.player.PlayerController
import app.freshtuber.player.PlayerEvent
import app.freshtuber.player.PlayerState
import androidx.media3.common.Player
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import app.freshtuber.ui.feed.LoadError
import app.freshtuber.ui.feed.toLoadError
import app.freshtuber.ui.navigation.PlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoUiState(
    val details: VideoDetails? = null,
    val loading: Boolean = true,
    val error: LoadError? = null,
    val likeStatus: LikeStatus = LikeStatus.Indifferent,
    val subscribed: Boolean? = null,
    val dislikes: Long? = null,
    val localSubscribed: Boolean = false,
    val localNotifications: Boolean = false,
)

data class CommentsUiState(
    val opened: Boolean = false,
    val loading: Boolean = false,
    val error: LoadError? = null,
    val countText: String? = null,
    val sortOptions: List<CommentSortOption> = emptyList(),
    val createCommand: InnerTubeCommand? = null,
    val comments: List<Comment> = emptyList(),
    val continuation: String? = null,
    val loadingMore: Boolean = false,
    val posting: Boolean = false,
    /** Replies by parent comment id, loaded when the user expands them. */
    val replies: Map<String, List<Comment>> = emptyMap(),
    val likedOverrides: Map<String, Boolean> = emptyMap(),
)

data class PlaylistPickerState(
    val opened: Boolean = false,
    val loading: Boolean = false,
    val options: List<PlaylistOption> = emptyList(),
    val error: LoadError? = null,
)

/** One-off problems shown as a snackbar. */
enum class VideoMessage { NeedsSignIn, ActionFailed, TooManyActions, CommentPosted, DownloadQueued, DownloadExists, WatchLaterAdded }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class VideoViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val videos: VideoRepository,
    private val accounts: AccountRepository,
    private val extras: CommunityExtras,
    private val settings: AppSettings,
    val controller: PlayerController,
    private val localSubscriptions: app.freshtuber.data.subscriptions.LocalSubscriptions,
    private val downloads: app.freshtuber.data.downloads.Downloads,
) : ViewModel() {

    val videoId: String = savedState.get<String>("videoId") ?: savedState.toRoute<PlayerRoute>().videoId

    private val _state = MutableStateFlow(VideoUiState())
    val state: StateFlow<VideoUiState> = _state.asStateFlow()

    private val _comments = MutableStateFlow(CommentsUiState())
    val comments: StateFlow<CommentsUiState> = _comments.asStateFlow()

    private val _picker = MutableStateFlow(PlaylistPickerState())
    val picker: StateFlow<PlaylistPickerState> = _picker.asStateFlow()

    private val _message = MutableStateFlow<VideoMessage?>(null)
    val message: StateFlow<VideoMessage?> = _message.asStateFlow()

    private val signedIn: Boolean get() = accounts.state.value is AccountState.SignedIn

    val player: Player get() = controller.player
    val playerState: StateFlow<PlayerState> = controller.state
    val playerEvents: SharedFlow<PlayerEvent> = controller.events
    val gesturesEnabled: StateFlow<Boolean> = settings.gestureControls
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val videoPinned: StateFlow<Boolean> = settings.videoPinned
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setVideoPinned(pinned: Boolean) { viewModelScope.launch { settings.setVideoPinned(pinned) } }

    fun startPlayback() {
        controller.play(videoId)
        _state.value.details?.let { controller.setQueue(it.related) }
    }

    fun setVideoOnScreen(onScreen: Boolean) {
        controller.videoOnScreen = onScreen
    }

    fun playNext() = controller.playNext()

    fun retryPlayback() = controller.retry()

    private fun withChannelFallback(details: VideoDetails): VideoDetails = details.copy(
        channel = details.channel ?: controller.state.value.nowPlaying?.takeIf { it.videoId == videoId }?.channel,
    )

    init {
        viewModelScope.launch {
            _state.map { it.details?.channel?.id }.distinctUntilChanged().flatMapLatest { id -> localSubscriptions.channel(id) }.collect { channel ->
                _state.update { it.copy(localSubscribed = channel != null, localNotifications = channel?.notifications == true,
                    subscribed = if (signedIn) it.subscribed else channel != null) }
            }
        }
        viewModelScope.launch {
            controller.state.collect { playback ->
                val channel = playback.nowPlaying?.takeIf { it.videoId == videoId }?.channel
                if (channel != null) _state.update { current ->
                    if (current.details?.channel == null) current.copy(details = current.details?.copy(channel = channel)) else current
                }
            }
        }
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val local = downloads.available(videoId)
                val saved = downloads.details(videoId)
                val loaded = if (saved != null && !downloads.isOnline()) saved else try { videos.details(videoId) }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { saved ?: throw e }
                val details = withChannelFallback(loaded)
                _state.update {
                    it.copy(details = details, loading = false, likeStatus = details.likeStatus, subscribed = if (signedIn) details.subscribed else it.localSubscribed)
                }
                if (controller.state.value.nowPlaying?.videoId == videoId || controller.state.value.loadingId == videoId) {
                    controller.setQueue(details.related)
                }
                if (local == null && settings.dislikesEnabled.first()) {
                    extras.dislikes(videoId)?.let { count -> _state.update { it.copy(dislikes = count) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toLoadError()) }
            }
        }
    }

    fun download(summary: app.freshtuber.domain.model.VideoSummary? = null) {
        viewModelScope.launch {
            try {
                val details = _state.value.details
                val now = controller.state.value.nowPlaying?.takeIf { it.videoId == videoId }
                val video = summary ?: app.freshtuber.domain.model.VideoSummary(videoId, details?.title ?: now?.title.orEmpty(),
                    channelName = details?.channel?.name ?: now?.channelName, channelId = details?.channel?.id ?: now?.channel?.id, thumbnailUrl = now?.thumbnailUrl)
                _message.value = if (downloads.enqueue(video)) VideoMessage.DownloadQueued else VideoMessage.DownloadExists
            } catch (e: CancellationException) { throw e } catch (_: Exception) { _message.value = VideoMessage.ActionFailed }
        }
    }

    fun like() { if (_state.value.likeStatus != LikeStatus.Like) rate(LikeStatus.Like) }

    fun toggleLike() = rate(if (_state.value.likeStatus == LikeStatus.Like) LikeStatus.Indifferent else LikeStatus.Like)

    fun toggleDislike() = rate(if (_state.value.likeStatus == LikeStatus.Dislike) LikeStatus.Indifferent else LikeStatus.Dislike)

    private fun rate(target: LikeStatus) {
        val details = _state.value.details ?: return
        if (!requireSignIn()) return
        val command = when (target) {
            LikeStatus.Like -> details.likeCommand ?: InnerTubeCommand.like(videoId, "LIKE")
            LikeStatus.Dislike -> details.dislikeCommand ?: InnerTubeCommand.like(videoId, "DISLIKE")
            LikeStatus.Indifferent -> details.removeLikeCommand ?: InnerTubeCommand.like(videoId, "INDIFFERENT")
        } ?: return
        val before = _state.value.likeStatus
        _state.update { it.copy(likeStatus = target) }
        perform(command) { _state.update { it.copy(likeStatus = before) } }
    }

    fun toggleSubscription() {
        val details = _state.value.details ?: return
        val channelId = details.channel?.id ?: return
        if (!signedIn) {
            viewModelScope.launch {
                try { localSubscriptions.toggle(details.channel!!); localSubscriptions.refresh() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { _message.value = e.toMessage() }
            }
            return
        }
        val subscribe = _state.value.subscribed != true
        val command = if (subscribe) {
            details.subscribeCommand ?: InnerTubeCommand.subscribe(channelId)
        } else {
            details.unsubscribeCommand ?: InnerTubeCommand.unsubscribe(channelId)
        }
        val before = _state.value.subscribed
        _state.update { it.copy(subscribed = subscribe) }
        perform(command) { _state.update { it.copy(subscribed = before) } }
    }

    fun setLocalNotifications(enabled: Boolean) {
        val channel = _state.value.details?.channel ?: return
        viewModelScope.launch {
            try { localSubscriptions.notify(channel, enabled); localSubscriptions.refresh() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.toMessage() }
        }
    }

    // --- Comments.

    fun openComments() {
        _comments.update { it.copy(opened = true) }
        if (_comments.value.comments.isEmpty() && !_comments.value.loading) {
            _state.value.details?.commentsContinuation?.let(::loadComments)
        }
    }

    fun closeComments() = _comments.update { it.copy(opened = false) }

    fun sortComments(option: CommentSortOption) {
        if (option.selected) return
        loadComments(option.token)
    }

    private fun loadComments(token: String) {
        _comments.update { it.copy(loading = true, error = null, comments = emptyList(), replies = emptyMap()) }
        viewModelScope.launch {
            try {
                val page = videos.comments(token)
                _comments.update {
                    it.copy(
                        loading = false,
                        countText = page.countText ?: it.countText,
                        sortOptions = page.sortOptions.ifEmpty { it.sortOptions },
                        createCommand = page.createCommand ?: it.createCommand,
                        comments = page.comments,
                        continuation = page.continuation,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _comments.update { it.copy(loading = false, error = e.toLoadError()) }
            }
        }
    }

    fun loadMoreComments() {
        val current = _comments.value
        val token = current.continuation ?: return
        if (current.loadingMore || current.loading) return
        _comments.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = videos.comments(token)
                _comments.update {
                    it.copy(
                        loadingMore = false,
                        comments = (it.comments + page.comments).distinctBy(Comment::id),
                        continuation = page.continuation,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _comments.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun loadReplies(comment: Comment) {
        val token = comment.repliesContinuation ?: return
        viewModelScope.launch {
            try {
                val page = videos.comments(token)
                _comments.update { it.copy(replies = it.replies + (comment.id to page.comments)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = VideoMessage.ActionFailed
            }
        }
    }

    fun toggleCommentLike(comment: Comment) {
        if (!requireSignIn()) return
        val liked = _comments.value.likedOverrides[comment.id] ?: comment.isLiked
        val command = (if (liked) comment.unlikeCommand else comment.likeCommand) ?: run {
            _message.value = VideoMessage.ActionFailed
            return
        }
        _comments.update { it.copy(likedOverrides = it.likedOverrides + (comment.id to !liked)) }
        perform(command) { _comments.update { it.copy(likedOverrides = it.likedOverrides + (comment.id to liked)) } }
    }

    fun postComment(text: String, replyTo: Comment? = null) {
        if (!requireSignIn() || text.isBlank()) return
        val base = (if (replyTo != null) replyTo.replyCommand else _comments.value.createCommand) ?: run {
            _message.value = VideoMessage.ActionFailed
            return
        }
        _comments.update { it.copy(posting = true) }
        viewModelScope.launch {
            try {
                videos.perform(base.withCommentText(text.trim()))
                _comments.update { it.copy(posting = false) }
                _message.value = VideoMessage.CommentPosted
                if (replyTo != null) loadReplies(replyTo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _comments.update { it.copy(posting = false) }
                _message.value = e.toMessage()
            }
        }
    }

    // --- Save to playlist.

    fun openPlaylistPicker() {
        if (!requireSignIn()) return
        _picker.value = PlaylistPickerState(opened = true, loading = true)
        viewModelScope.launch {
            try {
                val options = videos.playlistOptions(videoId)
                _picker.update { it.copy(loading = false, options = options) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _picker.update { it.copy(loading = false, error = e.toLoadError()) }
            }
        }
    }

    fun closePlaylistPicker() = _picker.update { it.copy(opened = false) }

    fun togglePlaylist(option: PlaylistOption) {
        val add = !option.containsVideo
        fun mark(contains: Boolean) = _picker.update { state ->
            state.copy(options = state.options.map { if (it.id == option.id) it.copy(containsVideo = contains) else it })
        }
        mark(add)
        perform(if (add) option.addCommand else option.removeCommand) { mark(!add) }
    }

    fun notInterested(command: InnerTubeCommand?, onDone: () -> Unit) {
        if (command == null || !signedIn) { onDone(); return }
        viewModelScope.launch {
            try { videos.perform(command); onDone() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.toMessage() }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun requireSignIn(): Boolean {
        if (!signedIn) _message.value = VideoMessage.NeedsSignIn
        return signedIn
    }

    private fun perform(command: InnerTubeCommand, rollback: () -> Unit) {
        viewModelScope.launch {
            try {
                videos.perform(command)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rollback()
                _message.value = e.toMessage()
            }
        }
    }

    private fun Throwable.toMessage(): VideoMessage = when (toLoadError()) {
        LoadError.TooManyActions -> VideoMessage.TooManyActions
        LoadError.SessionExpired -> VideoMessage.NeedsSignIn
        else -> VideoMessage.ActionFailed
    }
}
