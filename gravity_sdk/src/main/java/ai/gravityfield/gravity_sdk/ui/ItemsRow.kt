package ai.gravityfield.gravity_sdk.ui

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.LocalScrollProvider
import ai.gravityfield.gravity_sdk.extensions.conditional
import ai.gravityfield.gravity_sdk.models.Element
import ai.gravityfield.gravity_sdk.models.Item
import ai.gravityfield.gravity_sdk.models.OnClickModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Composable
internal fun ItemsRow(
    element: Element,
    items: List<Item>,
    onClickCallback: (model: OnClickModel) -> Unit,
) {
    val style = element.style
    val height = style.size?.height
    val margin = style.margin
    val rowSpacing = style.rowSpacing?.dp ?: 0.dp

    val itemCard = element.itemCard ?: return

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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .conditional(margin != null) {
                padding(
                    start = margin!!.left.dp,
                    top = margin.top.dp,
                    end = margin.right.dp,
                    bottom = margin.bottom.dp
                )
            }
            .conditional(height != null && GravitySDK.instance.productViewBuilder == null) {
                height(height!!.dp)
            }
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .conditional(style.backgroundColor != null)
                {
                    background(color = style.backgroundColor!!)
                },
            state = listState,
            contentPadding = PaddingValues(horizontal = rowSpacing),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            items(items.size) { index ->
                val item = items[index]
                GravityItemCard(item, itemCard, onClickCallback)
            }
        }
    }
}
