package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.models.assistant.AssistantAction
import ai.gravityfield.gravity_sdk.models.assistant.AssistantActionHandler
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProduct
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Demo assistant action handler: instead of navigating, emits a message for the screen to show. */
object AssistantActionHandlerImpl : AssistantActionHandler {

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Messages to show to the user; dropped if nobody is collecting. */
    val messages: SharedFlow<String> = _messages

    override fun canHandle(action: AssistantAction): Boolean {
        return when (action) {
            is AssistantAction.OpenProduct -> canOpenProduct(action.product)
            else -> false
        }
    }

    override fun handle(action: AssistantAction) {
        if (action !is AssistantAction.OpenProduct) return

        _messages.tryEmit("Открыта карточка товара ${action.product.name}")
    }

    private fun canOpenProduct(product: AssistantProduct): Boolean {
        if (!product.sku.isNullOrBlank()) return true

        val url = product.url
        return url != null && (url.startsWith("http://") || url.startsWith("https://"))
    }
}
