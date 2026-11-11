package ai.gravityfield.gravity_sdk.assistant

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.CustomEvent
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Chat lifecycle events, sent by the SDK only.
 * Their [eventName] values are part of the public analytics contract.
 */
internal enum class AssistantLifecycleEvent(val eventName: String) {

    DialogOpen("chat_dialog_open"),

    /** A new question is accepted. Retrying the same question does not count. */
    MessageSent("assistant_message_sent"),

    /** A received answer is shown for the first time, including after the chat is reopened. */
    ResponseReceived("assistant_response_received"),

    /** Sent with [ResponseReceived] when the answer contains a non-empty product block. */
    ProductSelectionReceived("assistant_product_selection_received"),

    /** An error is shown in the open chat. Cancellation and superseded requests do not count. */
    Error("chat_error"),

    /** The chat is removed from the screen; the dialog itself continues. */
    DialogClose("chat_dialog_close"),

    /** The dialog is ended by "New chat" or an identity reset. */
    DialogEnd("chat_dialog_end"),
}

/**
 * Snapshot of the context an event is sent with: page, customer, assistant campaign and thread.
 * Captured when the event happens, so events of a previous dialog are never sent on behalf of a new account.
 */
internal data class AssistantEventScope(
    val pageContext: PageContext,
    val customerUser: User?,
    val campaignId: String?,
    val threadId: String?,
)

/**
 * Sends chat lifecycle events as custom events.
 * Failed sends are isolated from each other and from the dialog. Only identifiers are sent, never chat text.
 */
internal class AssistantAnalytics private constructor() {

    companion object {
        private const val TAG = "AssistantAnalytics"

        val instance = AssistantAnalytics()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun track(
        event: AssistantLifecycleEvent,
        eventScope: AssistantEventScope,
        messageId: String? = null,
    ) {
        val raisedAt = Date()
        val options = GravitySDK.instance.currentOptions

        // Resolve the anonymous user now: by send time prefs may already hold another user.
        val customerUser = eventScope.customerUser
            ?: GravityRepository.instance.userForRequest(null)

        val props = buildMap<String, String> {
            eventScope.threadId?.let { put("threadId", it) }
            messageId?.let { put("messageId", it) }
            eventScope.campaignId?.let { put("campaignId", it) }
        }

        scope.launch {
            // Campaigns in the response are intentionally ignored: chat analytics must not trigger campaign shows.
            val response = GravityRepository.instance.event(
                events = listOf(
                    CustomEvent(
                        type = event.eventName,
                        name = event.eventName,
                        customProps = props.takeIf { it.isNotEmpty() },
                    )
                ),
                raisedAt = raisedAt,
                pageContext = eventScope.pageContext,
                options = options,
                customerUser = customerUser,
            )

            val outcome = if (response != null) "sent" else "failed"
            Logger.d(TAG, "${event.eventName} $props $outcome")
        }
    }
}
