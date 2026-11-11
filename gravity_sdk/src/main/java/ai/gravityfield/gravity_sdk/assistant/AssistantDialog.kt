package ai.gravityfield.gravity_sdk.assistant

import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductBlock

internal sealed interface AssistantChatItem {
    val id: String

    data class UserMessage(override val id: String, val text: String) : AssistantChatItem
    data class AssistantMessage(override val id: String, val text: String) : AssistantChatItem
    data class ProductBlockItem(
        override val id: String,
        val block: AssistantProductBlock,
    ) : AssistantChatItem
}

internal data class AssistantDialogState(
    val items: List<AssistantChatItem> = emptyList(),
    val suggests: List<String> = emptyList(),
    val isSending: Boolean = false,
    val errorText: String? = null,
)
