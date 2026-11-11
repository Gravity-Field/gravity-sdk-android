package ai.gravityfield.gravity_sdk.models.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

sealed interface AssistantPrepareResult {
    data object Ready : AssistantPrepareResult
    data class Unavailable(val reason: AssistantUnavailableReason) : AssistantPrepareResult
    data class Failure(val error: AssistantError) : AssistantPrepareResult
    data object Cancelled : AssistantPrepareResult
    data object Superseded : AssistantPrepareResult
}

enum class AssistantUnavailableReason {
    NoMatchingConfiguration,
    Disabled,
}

enum class AssistantErrorCode {
    InvalidConfiguration,
    Unauthorized,
    Network,
    Timeout,
    RateLimited,
    Server,
    InvalidResponse,
}

data class AssistantError(
    val code: AssistantErrorCode,
    val message: String? = null,
    val httpStatus: Int? = null,
)

enum class AssistantOpenResult {
    Opened,
    AlreadyOpen,
    NotReady,
    InvalidPresenter,
}

enum class AssistantProductAction {
    VisibleImpression,

    /** Tap on the whole product card; opens the product. */
    Tap,
}

enum class AssistantInteractionResult {
    Accepted,
    Duplicate,
    StaleReference,
    NotActionable,
}

/**
 * Opaque reference to a product occurrence in an assistant answer, issued by the SDK with the product.
 * Becomes invalid after a new chat or an identity change.
 */
class AssistantProductRef internal constructor(
    /** Chat generation at the time of issue; unrelated to the setUser session. */
    internal val epoch: Long,
    internal val itemId: String,
)

data class AssistantProduct(
    val itemId: String,
    val ref: AssistantProductRef,
    val name: String,
    val sku: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
    val price: Double? = null,
    val brand: String? = null,
    val features: String? = null,
    val categoryId: String? = null,
    val isStm: Boolean? = null,
    val canOpen: Boolean = false,
)

data class AssistantProductBlock(
    val blockId: String,
    val title: String? = null,
    val products: List<AssistantProduct> = emptyList(),
)

data class AssistantProductLayout(
    val availableWidth: Dp,
)

interface AssistantProductBlockBuilder {
    @Composable
    fun Build(block: AssistantProductBlock, layout: AssistantProductLayout)
}

sealed interface AssistantAction {
    data class OpenProduct(val product: AssistantProduct) : AssistantAction
    data class OpenSupportChat(val payload: Map<String, Any?>) : AssistantAction
    data class FollowUrl(val url: String) : AssistantAction
    data class Custom(val type: String, val payload: Map<String, Any?>) : AssistantAction
}

interface AssistantActionHandler {
    fun canHandle(action: AssistantAction): Boolean
    fun handle(action: AssistantAction)
}
