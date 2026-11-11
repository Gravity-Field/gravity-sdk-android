package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.assistant.AssistantChatItem
import ai.gravityfield.gravity_sdk.assistant.AssistantDialogState
import ai.gravityfield.gravity_sdk.assistant.AssistantStartScreen
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductBlockBuilder
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductLayout
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TitleColor = Color(0xFF101828)
private val SubtitleColor = Color(0xFF667085)
private val SuggestBackground = Color(0xFFF2F4F7)
private val SuggestTextColor = Color(0xFF344054)
private val AvatarColor = Color(0xFFFF8A00)
private val UserBubbleColor = Color(0xFF7F56D9)
private val AssistantBubbleColor = Color(0xFFF2F4F7)
private val LoaderColor = Color(0xFFFE7316)
private val ErrorColor = Color(0xFFB42318)
private val ErrorBubbleColor = Color(0xFFFEE4E2)
private val RetryIconColor = Color(0xFFF04438)
private val DividerColor = Color(0xFFEAECF0)

private val ContentPadding = 16.dp
private val ItemSpacing = 12.dp

/** Extra bottom scroll space so the last message does not stick to the system bar. */
private val BottomScrollExtra = 16.dp
private val WelcomeAvatarSize = 88.dp
private val MessageAvatarSize = 28.dp

/** Fallback loader height used until the chat suggests have been measured. */
private val LoaderMinHeight = 40.dp

private const val InputMaxLines = 4

/**
 * Keyboard padding matching the window mode GravitySDK sets during the chat:
 * applied on API 30+, where the window is not resized; on older APIs the system
 * resizes the window itself.
 */
private fun Modifier.assistantImePadding(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) imePadding() else this

/**
 * Assistant screen: the start screen before the first message and the chat after it.
 * Product blocks are rendered by the app via [AssistantProductBlockBuilder].
 *
 * [startScreen] is `null` while preparing or after preparation failed;
 * an already started chat is shown regardless.
 */
@Composable
internal fun GravityAssistantContent(
    startScreen: AssistantStartScreen?,
    isPreparing: Boolean,
    startScreenError: String?,
    state: AssistantDialogState,
    productBlockBuilder: AssistantProductBlockBuilder?,
    onSuggestClick: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onRetry: () -> Unit,
    onNewChat: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .assistantImePadding()
        ) {
            AssistantHeader(
                showNewChat = state.items.isNotEmpty() && startScreen != null,
                onNewChat = onNewChat,
                onClose = onClose,
            )

            AssistantDivider()

            if (state.items.isEmpty()) {
                AssistantWelcome(
                    modifier = Modifier.weight(1f),
                    startScreen = startScreen,
                    isPreparing = isPreparing,
                    errorText = startScreenError,
                    onSuggestClick = onSuggestClick,
                )
            } else {
                AssistantDialog(
                    modifier = Modifier.weight(1f),
                    state = state,
                    productBlockBuilder = productBlockBuilder,
                    onSuggestClick = onSuggestClick,
                    onRetry = onRetry,
                )
            }

            AssistantDivider()

            AssistantInput(
                enabled = !state.isSending &&
                    state.errorText == null &&
                    (state.items.isNotEmpty() || startScreen != null),
                onSend = onSendMessage,
            )
        }
    }
}

@Composable
private fun AssistantDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor),
    )
}

@Composable
private fun AssistantHeader(
    showNewChat: Boolean,
    onNewChat: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showNewChat) {
            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New chat",
                    tint = TitleColor,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = TitleColor,
            )
        }
    }
}

@Composable
private fun AssistantWelcome(
    modifier: Modifier,
    startScreen: AssistantStartScreen?,
    isPreparing: Boolean,
    errorText: String?,
    onSuggestClick: (String) -> Unit,
) {
    if (isPreparing) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = LoaderColor)
        }
        return
    }

    if (startScreen == null) {
        if (errorText != null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ErrorBubbleColor,
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        text = errorText,
                        color = ErrorColor,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return
        }

        Spacer(modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AssistantAvatar(size = WelcomeAvatarSize)

        if (startScreen.title.isNotBlank()) {
            Text(
                modifier = Modifier.padding(top = 24.dp),
                text = startScreen.title,
                color = TitleColor,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            )
        }

        if (startScreen.subtitle.isNotBlank()) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = startScreen.subtitle,
                color = SubtitleColor,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }

        if (startScreen.suggests.isNotEmpty()) {
            AssistantSuggests(
                modifier = Modifier.padding(top = 24.dp),
                suggests = startScreen.suggests,
                centered = true,
                onSuggestClick = onSuggestClick,
            )
        }
    }
}

@Composable
private fun AssistantDialog(
    modifier: Modifier,
    state: AssistantDialogState,
    productBlockBuilder: AssistantProductBlockBuilder?,
    onSuggestClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.items.lastIndex.coerceAtLeast(0),
    )
    val density = LocalDensity.current
    var isRestored by remember { mutableStateOf(false) }
    // Kept outside LazyColumn: the heights must outlive the items they measure.
    var suggestsHeight by remember { mutableStateOf(0.dp) }
    var lastQuestionHeight by remember { mutableStateOf(0.dp) }
    val lastQuestionIndex = state.items.indexOfLast { it is AssistantChatItem.UserMessage }

    LaunchedEffect(lastQuestionIndex, state.isSending) {
        if (!isRestored) {
            isRestored = true
            return@LaunchedEffect
        }

        if (lastQuestionIndex >= 0) {
            val offset = with(density) { (ContentPadding - ItemSpacing).roundToPx() }
            listState.animateScrollToItem(lastQuestionIndex, offset)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(
            top = ContentPadding,
            bottom = ContentPadding + BottomScrollExtra,
        ),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        itemsIndexed(items = state.items, key = { _, item -> item.id }) { index, item ->
            when (item) {
                is AssistantChatItem.UserMessage -> MessageBubble(
                    modifier = if (index == lastQuestionIndex) {
                        Modifier.onSizeChanged { size ->
                            lastQuestionHeight = with(density) { size.height.toDp() }
                        }
                    } else {
                        Modifier
                    },
                    text = item.text,
                    alignment = Alignment.CenterEnd,
                    backgroundColor = UserBubbleColor,
                    textColor = Color.White,
                )

                is AssistantChatItem.AssistantMessage -> AssistantMessageRow(text = item.text)

                is AssistantChatItem.ProductBlockItem -> ProductBlock(
                    item = item,
                    productBlockBuilder = productBlockBuilder,
                )
            }
        }

        if (state.isSending) {
            item(key = "sending") {
                val loaderHeight = (suggestsHeight - lastQuestionHeight - ItemSpacing)
                    .coerceAtLeast(LoaderMinHeight)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(loaderHeight)
                        .padding(horizontal = ContentPadding),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Row(
                        modifier = Modifier.height(LoaderMinHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = LoaderColor,
                            strokeWidth = 2.dp,
                        )

                        Spacer(modifier = Modifier.width(ItemSpacing))

                        Text(
                            text = "Изучаю вопрос...",
                            color = SubtitleColor,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }

        val error = state.errorText
        if (error != null && !state.isSending) {
            item(key = "error") {
                ErrorRow(text = error, onRetry = onRetry)
            }
        }

        if (state.suggests.isNotEmpty() && !state.isSending) {
            item(key = "suggests") {
                AssistantSuggests(
                    modifier = Modifier
                        .padding(horizontal = ContentPadding)
                        .onSizeChanged { size ->
                            suggestsHeight = with(density) { size.height.toDp() }
                        },
                    suggests = state.suggests,
                    centered = false,
                    onSuggestClick = onSuggestClick,
                )
            }
        }
    }
}

/** Product block spanning the full chat width; horizontal padding is up to the app's builder. */
@Composable
private fun ProductBlock(
    item: AssistantChatItem.ProductBlockItem,
    productBlockBuilder: AssistantProductBlockBuilder?,
) {
    if (productBlockBuilder == null) {
        Text(
            modifier = Modifier.padding(horizontal = ContentPadding),
            text = "Не удалось показать подборку товаров",
            color = ErrorColor,
            fontSize = 14.sp,
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        productBlockBuilder.Build(
            block = item.block,
            layout = AssistantProductLayout(availableWidth = maxWidth),
        )
    }
}

@Composable
private fun AssistantMessageRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ContentPadding),
        verticalAlignment = Alignment.Top,
    ) {
        AssistantAvatar(size = MessageAvatarSize)

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AssistantBubbleColor,
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                text = text,
                color = TitleColor,
                fontSize = 15.sp,
            )
        }
    }
}

/** Request error bubble with a retry button in place of the avatar. */
@Composable
private fun ErrorRow(text: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = ContentPadding),
        verticalAlignment = Alignment.Top,
    ) {
        // Not an IconButton: its enforced minimum size breaks the alignment with the bubble.
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f),
            shape = CircleShape,
            color = Color.Transparent,
            onClick = onRetry,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    modifier = Modifier.fillMaxSize(0.6f),
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry",
                    tint = RetryIconColor,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ErrorBubbleColor,
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                text = text,
                color = ErrorColor,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun AssistantInput(
    enabled: Boolean,
    onSend: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val canSend = enabled && draft.isNotBlank()

    val send = {
        if (canSend) {
            onSend(draft)
            draft = ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ContentPadding, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = SuggestBackground,
        ) {
            BasicTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                value = draft,
                onValueChange = { draft = it },
                enabled = enabled,
                textStyle = TextStyle(color = TitleColor, fontSize = 15.sp),
                cursorBrush = SolidColor(UserBubbleColor),
                maxLines = InputMaxLines,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) {
                            Text(
                                text = "Спросите ассистента",
                                color = SubtitleColor,
                                fontSize = 15.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        IconButton(
            onClick = send,
            enabled = canSend,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (canSend) UserBubbleColor else SubtitleColor,
            )
        }
    }
}

@Composable
private fun AssistantAvatar(size: Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = AvatarColor,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                modifier = Modifier.size(size * 0.6f),
                imageVector = Icons.Filled.Face,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    alignment: Alignment,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ContentPadding),
        contentAlignment = alignment,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = backgroundColor,
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                text = text,
                color = textColor,
                fontSize = 15.sp,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantSuggests(
    modifier: Modifier,
    suggests: List<String>,
    centered: Boolean,
    onSuggestClick: (String) -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            8.dp,
            if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggests.forEach { suggest ->
            Surface(
                shape = CircleShape,
                color = SuggestBackground,
                onClick = { onSuggestClick(suggest) },
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    text = suggest,
                    color = SuggestTextColor,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
