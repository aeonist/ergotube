package app.freshtuber.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.domain.model.Comment
import app.freshtuber.ui.components.ChoiceMenu
import app.freshtuber.ui.feed.LoadErrorState
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(state: CommentsUiState, viewModel: VideoViewModel, onOpenChannel: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.comments.size) {
        if (nearEnd && state.comments.isNotEmpty()) viewModel.loadMoreComments()
    }

    ModalBottomSheet(onDismissRequest = viewModel::closeComments, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.92f).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.video_comments), style = MaterialTheme.typography.titleMedium)
                    state.countText?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ChoiceMenu(
                    titles = state.sortOptions.map { it.title },
                    selected = state.sortOptions.indexOfFirst { it.selected },
                    onSelect = { index -> viewModel.sortComments(state.sortOptions[index]) },
                )
            }

            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.error != null -> LoadErrorState(state.error, viewModel::openComments, null)
                    else -> LazyColumn(state = listState) {
                        items(state.comments, key = { it.id }) { comment ->
                          Column {
                            CommentItem(
                                comment = comment,
                                liked = state.likedOverrides[comment.id] ?: comment.isLiked,
                                onLike = { viewModel.toggleCommentLike(comment) },
                                onReply = if (comment.replyCommand != null) {
                                    { replyTo = comment }
                                } else {
                                    null
                                },
                                onOpenChannel = onOpenChannel,
                            )
                            val replies = state.replies[comment.id]
                            if (replies != null) {
                                replies.forEach { reply ->
                                    CommentItem(
                                        comment = reply,
                                        liked = state.likedOverrides[reply.id] ?: reply.isLiked,
                                        onLike = { viewModel.toggleCommentLike(reply) },
                                        onReply = null,
                                        onOpenChannel = onOpenChannel,
                                        indent = true,
                                    )
                                }
                            } else if (comment.repliesContinuation != null && comment.replyCountText != null) {
                                TextButton(
                                    onClick = { viewModel.loadReplies(comment) },
                                    modifier = Modifier.padding(start = 56.dp),
                                ) {
                                    Text(stringResource(R.string.comment_show_replies, comment.replyCountText))
                                }
                            }
                          }
                        }
                        if (state.loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (state.createCommand != null) {
                ComposeBar(
                    draft = draft,
                    onDraft = { draft = it },
                    replyTo = replyTo,
                    posting = state.posting,
                    onCancelReply = { replyTo = null },
                    onSend = {
                        viewModel.postComment(draft, replyTo)
                        draft = ""
                        replyTo = null
                    },
                )
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    liked: Boolean,
    onLike: () -> Unit,
    onReply: (() -> Unit)?,
    onOpenChannel: (String) -> Unit,
    indent: Boolean = false,
) {
    val likeCount = if (liked && !comment.isLiked) comment.likeCountTextLiked else comment.likeCountText
    Row(Modifier.fillMaxWidth().padding(start = if (indent) 56.dp else 16.dp, end = 8.dp, top = 10.dp)) {
        AsyncImage(
            model = comment.authorAvatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(if (indent) 28.dp else 36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(enabled = comment.authorChannelId != null) { comment.authorChannelId?.let(onOpenChannel) },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(comment.authorName, comment.publishedText).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (comment.isByCreator) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(comment.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = stringResource(R.string.video_like),
                        modifier = Modifier.size(18.dp),
                    )
                }
                likeCount?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onReply != null) {
                    TextButton(onClick = onReply) { Text(stringResource(R.string.comment_reply)) }
                }
            }
        }
    }
}

@Composable
private fun ComposeBar(
    draft: String,
    onDraft: (String) -> Unit,
    replyTo: Comment?,
    posting: Boolean,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        replyTo?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.comment_replying_to, it.authorName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancelReply) { Text(stringResource(R.string.action_cancel)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft,
                onValueChange = onDraft,
                placeholder = { Text(stringResource(R.string.comment_hint)) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            if (posting) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
            } else {
                IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.comment_send))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(state: PlaylistPickerState, viewModel: VideoViewModel) {
    ModalBottomSheet(onDismissRequest = viewModel::closePlaylistPicker) {
        Text(
            stringResource(R.string.save_to_playlist),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.error != null -> LoadErrorState(state.error, viewModel::openPlaylistPicker, null)
            else -> LazyColumn(Modifier.padding(bottom = 24.dp)) {
                items(state.options, key = { it.id }) { option ->
                    ListItem(
                        headlineContent = { Text(option.title) },
                        trailingContent = {
                            Checkbox(checked = option.containsVideo, onCheckedChange = { viewModel.togglePlaylist(option) })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .clickable { viewModel.togglePlaylist(option) },
                    )
                }
            }
        }
    }
}
