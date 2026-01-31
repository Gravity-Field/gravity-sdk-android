package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.LocalScrollProvider
import ai.gravityfield.gravity_sdk.R
import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.GravityLayoutWidth
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.internal.InlineViewCache
import ai.gravityfield.gravity_sdk.models.internal.ScrollProvider
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.network.ContentResponse
import ai.gravityfield.gravity_sdk.ui.gravity_elements.GravityElements
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("UseKtx")
class GravityInlineListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private val groupSelector: String
    private val colorInt: Int
    private val cornerRadius: Float
    private val cornerRadiusTopStart: Float
    private val cornerRadiusTopEnd: Float
    private val cornerRadiusBottomStart: Float
    private val cornerRadiusBottomEnd: Float
    private val loaderLayoutResId: Int

    init {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.GravityInlineListView,
            defStyleAttr,
            0
        )
        groupSelector = typedArray.getString(R.styleable.GravityInlineListView_groupSelector)
            ?: throw Exception("GravitySDK: groupSelector must be provided to GravityInlineListView")
        colorInt = typedArray.getColor(
            R.styleable.GravityInlineListView_color,
            ContextCompat.getColor(context, android.R.color.transparent)
        )
        cornerRadius =
            typedArray.getDimension(R.styleable.GravityInlineListView_cornerRadius, -1f)
        cornerRadiusTopStart =
            typedArray.getDimension(R.styleable.GravityInlineListView_cornerRadiusTopStart, 0f)
        cornerRadiusTopEnd =
            typedArray.getDimension(R.styleable.GravityInlineListView_cornerRadiusTopEnd, 0f)
        cornerRadiusBottomStart =
            typedArray.getDimension(R.styleable.GravityInlineListView_cornerRadiusBottomStart, 0f)
        cornerRadiusBottomEnd =
            typedArray.getDimension(R.styleable.GravityInlineListView_cornerRadiusBottomEnd, 0f)
        loaderLayoutResId =
            typedArray.getResourceId(R.styleable.GravityInlineListView_loaderLayout, -1)
        typedArray.recycle()
    }

    private val pageContextProvider = PageContextProvider()

    fun init(pageContext: PageContext) {
        pageContextProvider.provide(pageContext)
    }

    @Composable
    override fun Content() {
        GravityListView(
            groupSelector,
            colorInt,
            cornerRadius,
            cornerRadiusTopStart,
            cornerRadiusTopEnd,
            cornerRadiusBottomStart,
            cornerRadiusBottomEnd,
            loaderLayoutResId,
            pageContextProvider,
        ) {
            post {
                layoutParams = layoutParams.apply {
                    height = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        it.toFloat(),
                        resources.displayMetrics
                    ).toInt()
                }
            }
        }
    }
}

@Composable
private fun GravityListView(
    groupSelector: String,
    colorInt: Int,
    cornerRadius: Float,
    cornerRadiusTopStart: Float,
    cornerRadiusTopEnd: Float,
    cornerRadiusBottomStart: Float,
    cornerRadiusBottomEnd: Float,
    loaderLayoutResId: Int,
    pageContextProvider: PageContextProvider,
    changeHeight: (Double) -> Unit,
) {
    var items by remember { mutableStateOf<List<InlineListViewItem>?>(null) }
    var pageContext by remember { mutableStateOf<PageContext?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(groupSelector) {
        pageContextProvider.setListener {
            pageContext = it

            scope.launch(Dispatchers.IO) {
                try {
                    val cache = GravitySDK.instance.getInlineViewCache(groupSelector, it)
                    val contentResponse: ContentResponse?
                    if (cache != null) {
                        contentResponse = cache.content
                    } else {
                        withContext(Dispatchers.Main) {
                            isLoading = true
                        }
                        contentResponse = GravitySDK.instance.getContentByGroupSelector(
                            groupSelector,
                            it,
                        )
                        GravitySDK.instance.putInlineViewCache(
                            groupSelector,
                            it,
                            InlineViewCache(contentResponse),
                        )
                    }

                    withContext(Dispatchers.Main) {
                        isLoading = false
                        val contentItems = mutableListOf<InlineListViewItem>()
                        val campaigns = contentResponse?.data ?: emptyList()
                        for (campaign in campaigns) {
                            val payload = campaign.payload.firstOrNull() ?: continue
                            val content = payload.contents.sortedWith(
                                compareBy(nullsLast()) { it.step }
                            ).firstOrNull() ?: continue

                            contentItems.add(InlineListViewItem(campaign, content))
                        }
                        items = contentItems
                    }
                } catch (_: Throwable) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        changeHeight(0.0)
                    }
                    GravitySDK.instance.putInlineViewCache(
                        groupSelector,
                        it,
                        InlineViewCache(),
                    )
                }
            }
        }
    }

    DisposableEffect(groupSelector) {
        onDispose {
            pageContextProvider.removeListener()
        }
    }

    val shape = if (cornerRadius != -1f) RoundedCornerShape(cornerRadius) else RoundedCornerShape(
        topStart = cornerRadiusTopStart,
        topEnd = cornerRadiusTopEnd,
        bottomStart = cornerRadiusBottomStart,
        bottomEnd = cornerRadiusBottomEnd,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(colorInt),
                shape = shape
            )
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading && loaderLayoutResId != -1 -> XmlLayoutCompose(layoutResId = loaderLayoutResId)
            items != null -> {
                val scrollPosition = GravitySDK.instance.getInlineViewCache(
                    groupSelector,
                    pageContext!!,
                )?.scrollPosition

                CompositionLocalProvider(
                    LocalScrollProvider provides ScrollProvider(
                        scrollPosition,
                        onScrollChanged = { scrollPosition ->
                            GravitySDK.instance.getInlineViewCache(
                                groupSelector,
                                pageContext!!,
                            )?.let {
                                GravitySDK.instance.putInlineViewCache(
                                    groupSelector,
                                    pageContext!!,
                                    it.copy(scrollPosition = scrollPosition),
                                )
                            }
                        },
                    )
                ) {
                    ItemListView(items!!)
                }
            }
        }
    }
}

private data class InlineListViewItem(
    val campaign: Campaign,
    val content: CampaignContent,
)

@Composable
private fun ItemListView(
    items: List<InlineListViewItem>,
) {
    val scrollProvider = LocalScrollProvider.current
    val listState = rememberLazyListState(
        scrollProvider?.scrollPosition?.index ?: 0,
        scrollProvider?.scrollPosition?.offset ?: 0
    )

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .drop(1)
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling) {
                    val index = listState.firstVisibleItemIndex
                    val offset = listState.firstVisibleItemScrollOffset
                    scrollProvider?.changeScroll(index, offset)
                }
            }
    }

    LazyRow(
        modifier = Modifier,
        state = listState,
    ) {
        items(items.size) { index ->
            val item = items[index]
            ItemView(item)
        }
    }
}

@Composable
private fun ItemView(
    item: InlineListViewItem,
) {
    val campaign = item.campaign
    val content = item.content
    val frameUi = content.variables.frameUI
    val container = frameUi?.container
    val style = container?.style
    val width = style?.size?.width
    val height = style?.size?.height
    val layoutWidth = style?.layoutWidth
    val padding = style?.padding
    val shape = RoundedCornerShape(style?.cornerRadius?.dp ?: 0.dp)
    val backgroundColor = style?.backgroundColor ?: MaterialTheme.colorScheme.surface
    val horizontalAlignment =
        style?.contentAlignment?.toHorizontalAlignment() ?: Alignment.CenterHorizontally
    val verticalArrangement =
        style?.verticalAlignment?.toVerticalArrangement() ?: Arrangement.Top
    val backgroundImage = style?.backgroundImage
    val backgroundFit = style?.backgroundFit ?: ContentScale.Crop
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .conditional(width != null) {
                width(width!!.dp)
            }
            .conditional(height != null) {
                height(height!!.dp)
            }
            .conditional(layoutWidth == GravityLayoutWidth.MATCH_PARENT) {
                width(configuration.screenWidthDp.dp)
            }
            .background(
                color = backgroundColor,
                shape = shape
            )
            .clip(shape),
        contentAlignment = Alignment.TopStart
    ) {

        if (backgroundImage != null) {
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = rememberAsyncImagePainter(model = backgroundImage),
                contentDescription = null,
                contentScale = backgroundFit,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .conditional(padding != null)
                {
                    padding(
                        start = padding!!.left.dp,
                        top = padding.top.dp,
                        end = padding.right.dp,
                        bottom = padding.bottom.dp
                    )
                },
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement
        ) {
            GravityElements(
                content,
                campaign,
                onClickCallback = { onClickModel ->
                    GravitySDK.instance.onClickHandler(
                        onClickModel,
                        content,
                        campaign,
                        context,
                    )
                }
            )
        }
    }
}
