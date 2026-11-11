package ai.gravityfield.gravity_sdk.assistant

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.ContentSettings
import ai.gravityfield.gravity_sdk.models.Options
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.models.User
import ai.gravityfield.gravity_sdk.models.assistant.AssistantAction
import ai.gravityfield.gravity_sdk.models.assistant.AssistantActionHandler
import ai.gravityfield.gravity_sdk.models.assistant.AssistantError
import ai.gravityfield.gravity_sdk.models.assistant.AssistantErrorCode
import ai.gravityfield.gravity_sdk.models.assistant.AssistantInteractionResult
import ai.gravityfield.gravity_sdk.models.assistant.AssistantPrepareResult
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProduct
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductAction
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductBlock
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductBlockBuilder
import ai.gravityfield.gravity_sdk.models.assistant.AssistantProductRef
import ai.gravityfield.gravity_sdk.models.assistant.AssistantUnavailableReason
import ai.gravityfield.gravity_sdk.models.assistant.ShoppingContent
import ai.gravityfield.gravity_sdk.models.assistant.ShoppingEngagementEvent
import ai.gravityfield.gravity_sdk.models.assistant.ShoppingProduct
import ai.gravityfield.gravity_sdk.network.AssistantRepository
import ai.gravityfield.gravity_sdk.network.ContentResponse
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.network.ShoppingGenerateOutcome
import ai.gravityfield.gravity_sdk.network.ShoppingGenerateRequest
import ai.gravityfield.gravity_sdk.network.ShoppingMessage
import ai.gravityfield.gravity_sdk.network.ShoppingRuntimeContext
import ai.gravityfield.gravity_sdk.utils.Logger
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Assistant greeting taken from the campaign matched by assistantSelector. */
internal data class AssistantStartScreen(
    val title: String,
    val subtitle: String,
    val suggests: List<String>,
)

/**
 * User identity and page context sent with dialog questions.
 * Captured on the first question and kept until the dialog ends.
 */
internal data class AssistantDialogContext(
    /** Canonical Gravity uid, not the id passed to setUser. */
    val uid: String,
    val pageContext: PageContext,

    /** Campaign of the start screen, reported in dialog analytics. */
    val campaignId: String?,

    /** User that dialog events are attributed to, with the canonical [uid]. */
    val eventUser: User,
)

/** Result of the last successful preparation: the start screen and the context for the next dialog. */
internal data class AssistantConfig(
    val startScreen: AssistantStartScreen,
    val context: AssistantDialogContext,
)

/**
 * Outcome of validating a product action: the result for the app, the action
 * for the handler, and whether the chat must be closed first.
 */
internal class AssistantProductActionPlan(
    val result: AssistantInteractionResult,
    val action: AssistantAction? = null,
    val closeChat: Boolean = false,
)

/**
 * Holds assistant preparation and dialog state. Preparation only affects the
 * start screen; an ongoing dialog is independent of it.
 */
internal class AssistantController {

    companion object {
        private const val TAG = "Assistant"
        private const val DEFAULT_TITLE = "Помощник"

        private const val EVENT_CLICK = "click"
        private const val EVENT_VISIBLE_IMPRESSION = "visible_impression"

        /** Event types for a shown response: the SDK name and its Shopping API name. */
        private val RESPONSE_SHOWN_EVENTS = arrayOf(EVENT_VISIBLE_IMPRESSION, "WRIMP")

        private const val REQUEST_ERROR = "Не удалось получить ответ, попробуйте ещё раз"
        private const val PREPARE_ERROR = "Не удалось загрузить ассистента, попробуйте позже"
    }

    /** Product shown in the dialog together with its tracking events. */
    private class TrackedProduct(
        val product: AssistantProduct,
        val events: List<ShoppingEngagementEvent>,
    )

    private val repository = GravityRepository.instance
    private val assistantRepository = AssistantRepository.instance
    private val eventService = AssistantEventService.instance
    private val analytics = AssistantAnalytics.instance
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Preparation counter; keeps an older preparation from overwriting a newer result. */
    private val generation = AtomicLong(0)

    /** Reset counter; a reset yields Cancelled, while a newer preparation yields Superseded. */
    private val resets = AtomicLong(0)

    /** Dialog epoch; incremented on a new chat or identity change to invalidate product refs. */
    private var epoch = 0L

    /** In-flight assistant request; cancelled when the dialog is cleared. */
    private var requestJob: Job? = null

    /** Dialog step number used to build unique message and block ids. */
    private var turn = 0L
    private var threadId: String? = null

    /** Last question asked; resent by [retryLastQuestion]. */
    private var lastQuestion: String? = null

    /** Block ids already used in this dialog; shelf_id may repeat across steps. */
    private val usedBlockIds = mutableSetOf<String>()

    /** Shown products by itemId, used to resolve product refs. */
    private val trackedProducts = mutableMapOf<String, TrackedProduct>()

    /** Impressions already sent while the chat is open, keyed by epoch and itemId. */
    private val reportedImpressions = mutableSetOf<String>()

    /** Item id of the product whose transition is in progress; repeated taps on it are ignored. */
    private var pendingTap: String? = null

    /** Whether the chat is on screen; display events are deferred until it is. */
    private var isPresented = false

    /** Sends deferred until the chat is shown. */
    private val pendingDisplay = mutableListOf<() -> Unit>()

    /** Current start screen; cleared as soon as a new preparation starts. */
    var config by mutableStateOf<AssistantConfig?>(null)
        private set

    var isPreparing by mutableStateOf(false)
        private set

    /** Error text shown in place of the greeting when preparation fails. */
    var startScreenError by mutableStateOf<String?>(null)
        private set

    /** Context of the current dialog; captured on the first question. */
    private var dialogContext: AssistantDialogContext? = null

    /** Context captured when the chat was opened; used for events before the first question. */
    private var openContext: AssistantDialogContext? = null

    var productBlockBuilder: AssistantProductBlockBuilder? = null
    var actionHandler: AssistantActionHandler? = null

    var dialogState by mutableStateOf(AssistantDialogState())
        private set

    /** Fully resets the assistant on identity change or dispose. Must be called on the main thread. */
    fun reset() {
        resets.incrementAndGet()
        config = null
        isPreparing = false
        startScreenError = null
        endDialog()
        turn = 0
    }

    /** Called when the chat is shown. Product impressions are counted anew for each opening. */
    fun onAssistantOpened() {
        reportedImpressions.clear()
        pendingTap = null
        isPresented = true

        openContext = dialogContext ?: config?.context

        track(AssistantLifecycleEvent.DialogOpen)

        val pending = pendingDisplay.toList()
        pendingDisplay.clear()
        pending.forEach { it() }
    }

    /** Called when the chat is hidden. The dialog is kept. */
    fun onAssistantClosed() {
        isPresented = false
        track(AssistantLifecycleEvent.DialogClose)
        openContext = null
    }

    /** Ends the current dialog and returns to the start screen. */
    fun startNewChat() {
        endDialog()
    }

    /** Ends the dialog; DialogEnd must be tracked before clearing, which discards the dialog scope. */
    private fun endDialog() {
        if (threadId != null) track(AssistantLifecycleEvent.DialogEnd)
        clearDialog()
    }

    /** Clears the dialog; the new epoch invalidates issued product refs and pending responses. */
    private fun clearDialog() {
        requestJob?.cancel()
        requestJob = null

        epoch++
        threadId = null
        lastQuestion = null
        dialogContext = null
        usedBlockIds.clear()
        trackedProducts.clear()
        pendingDisplay.clear()
        dialogState = AssistantDialogState()
    }

    /** Analytics scope of the dialog, falling back to the open context and then the prepared one. */
    private fun eventScope(): AssistantEventScope? {
        val context = dialogContext ?: openContext ?: config?.context ?: return null
        return AssistantEventScope(
            pageContext = context.pageContext,
            customerUser = context.eventUser,
            campaignId = context.campaignId,
            threadId = threadId,
        )
    }

    private fun track(event: AssistantLifecycleEvent, messageId: String? = null) {
        val eventScope = eventScope()
        if (eventScope == null) {
            Logger.i(TAG, "No dialog context for ${event.eventName}")
            return
        }
        analytics.track(event, eventScope, messageId)
    }

    /** Tracks the event when the chat is shown, using the scope captured at call time. */
    private fun trackOnDisplay(event: AssistantLifecycleEvent, messageId: String? = null) {
        val eventScope = eventScope()
        if (eventScope == null) {
            Logger.i(TAG, "No dialog context for ${event.eventName}")
            return
        }
        onDisplay { analytics.track(event, eventScope, messageId) }
    }

    /** Runs [send] now if the chat is shown, otherwise when it is next opened. */
    private fun onDisplay(send: () -> Unit) {
        if (isPresented) send() else pendingDisplay += send
    }

    fun sendMessage(text: String) {
        val message = text.trim()
        if (message.isEmpty() || dialogState.isSending) return

        val messageId = UUID.randomUUID().toString()
        lastQuestion = message
        dialogState = dialogState.copy(
            items = dialogState.items + AssistantChatItem.UserMessage(
                id = messageId,
                text = message,
            ),
            suggests = emptyList(),
            isSending = true,
            errorText = null,
        )

        if (request(message)) {
            track(AssistantLifecycleEvent.MessageSent, messageId)
        }
    }

    /** Resends the last question after an error without adding or tracking it as a new message. */
    fun retryLastQuestion() {
        val message = lastQuestion ?: return
        if (dialogState.isSending) return

        dialogState = dialogState.copy(isSending = true, errorText = null)
        request(message)
    }

    /** Starts a request for [message]. Returns false if there is no prepared context. */
    private fun request(message: String): Boolean {
        val context = dialogContext ?: config?.context
        if (context == null) {
            Logger.i(TAG, "Message is sent without prepared context")
            failRequest()
            return false
        }
        dialogContext = context

        val currentThreadId = threadId ?: UUID.randomUUID().toString().also { threadId = it }

        val requestEpoch = epoch
        val requestTurn = ++turn

        requestJob = scope.launch {
            try {
                val outcome = assistantRepository.generate(
                    ShoppingGenerateRequest(
                        threadId = currentThreadId,
                        resourceId = context.uid,
                        messages = listOf(ShoppingMessage(role = "user", content = message)),
                        runtimeContext = ShoppingRuntimeContext(
                            tenantId = GravitySDK.instance.section,
                            uid = context.uid,
                            ctx = context.pageContext,
                        ),
                    )
                )

                if (requestEpoch != epoch) return@launch

                if (outcome !is ShoppingGenerateOutcome.Success) {
                    failRequest()
                    return@launch
                }

                val items = outcome.response.contents.toChatItems(requestEpoch, requestTurn)
                if (items.isEmpty()) {
                    Logger.i(TAG, "Assistant response has nothing to render")
                    failRequest()
                    return@launch
                }

                dialogState = dialogState.copy(
                    items = dialogState.items + items,
                    suggests = outcome.response.suggests,
                    isSending = false,
                )

                val messageId = outcome.response.assistantMessageId
                trackOnDisplay(AssistantLifecycleEvent.ResponseReceived, messageId)

                val hasProducts = items.any {
                    it is AssistantChatItem.ProductBlockItem && it.block.products.isNotEmpty()
                }
                if (hasProducts) {
                    trackOnDisplay(AssistantLifecycleEvent.ProductSelectionReceived, messageId)
                }

                val responseEvents = outcome.response.events
                onDisplay { eventService.track(responseEvents, *RESPONSE_SHOWN_EVENTS) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Response parsing calls app code; its exceptions must not crash the process.
                Logger.e(TAG, "Assistant request failed: ${e.message}", e)

                if (requestEpoch == epoch) failRequest()
            }
        }

        return true
    }

    /** Ends sending with an error. Must be called on every failure, otherwise input stays blocked. */
    private fun failRequest() {
        dialogState = dialogState.copy(
            isSending = false,
            errorText = REQUEST_ERROR,
        )
        trackOnDisplay(AssistantLifecycleEvent.Error)
    }

    private fun List<ShoppingContent>.toChatItems(
        refEpoch: Long,
        turn: Long,
    ): List<AssistantChatItem> {
        return mapIndexedNotNull { index, content ->
            when (content) {
                is ShoppingContent.Message -> AssistantChatItem.AssistantMessage(
                    id = "message-$turn-$index",
                    text = content.value,
                )

                is ShoppingContent.Products -> {
                    val blockId = content.shelfId
                        ?.takeIf { it.isNotBlank() && usedBlockIds.add(it) }
                        ?: "block-$turn-$index"

                    AssistantChatItem.ProductBlockItem(
                        id = blockId,
                        block = AssistantProductBlock(
                            blockId = blockId,
                            title = content.title,
                            products = content.products.toAssistantProducts(refEpoch, blockId),
                        ),
                    )
                }

                is ShoppingContent.Button -> {
                    Logger.i(TAG, "Button content ${content.action} is not rendered yet")
                    null
                }
            }
        }
    }

    private fun List<ShoppingProduct>.toAssistantProducts(
        refEpoch: Long,
        blockId: String,
    ): List<AssistantProduct> {
        return mapIndexed { index, product ->
            val itemId = "$blockId:$index"
            val ref = AssistantProductRef(epoch = refEpoch, itemId = itemId)

            val assistantProduct = AssistantProduct(
                itemId = itemId,
                ref = ref,
                name = product.name,
                sku = product.sku,
                url = product.url,
                imageUrl = product.imageUrl,
                price = product.price,
                brand = product.brand,
                features = product.features,
                categoryId = product.categoryId,
                isStm = product.isStm,
            )

            assistantProduct.copy(canOpen = canOpen(assistantProduct)).also {
                trackedProducts[itemId] = TrackedProduct(it, product.events)
            }
        }
    }

    /**
     * Validates a product action and tracks it if accepted. The caller is
     * responsible for closing the chat and invoking the handler.
     */
    fun planProductAction(
        ref: AssistantProductRef,
        action: AssistantProductAction,
    ): AssistantProductActionPlan {
        if (ref.epoch != epoch) {
            Logger.i(TAG, "Product ref from epoch ${ref.epoch} is stale")
            return AssistantProductActionPlan(AssistantInteractionResult.StaleReference)
        }

        val tracked = trackedProducts[ref.itemId]
        if (tracked == null) {
            Logger.i(TAG, "Unknown product ref ${ref.itemId}")
            return AssistantProductActionPlan(AssistantInteractionResult.StaleReference)
        }

        if (action == AssistantProductAction.VisibleImpression) {
            if (!reportedImpressions.add("${ref.epoch}:${ref.itemId}")) {
                Logger.d(TAG, "Product ${ref.itemId} is already seen in this chat")
                return AssistantProductActionPlan(AssistantInteractionResult.Duplicate)
            }

            Logger.d(TAG, "Product ${ref.itemId} became visible")
            eventService.track(tracked.events, EVENT_VISIBLE_IMPRESSION)
            return AssistantProductActionPlan(AssistantInteractionResult.Accepted)
        }

        if (pendingTap == ref.itemId) {
            Logger.d(TAG, "Transition to product ${ref.itemId} is already started")
            return AssistantProductActionPlan(AssistantInteractionResult.Duplicate)
        }

        val assistantAction = AssistantAction.OpenProduct(tracked.product)

        val handler = actionHandler
        if (handler == null || !canHandle(handler, assistantAction)) {
            Logger.i(TAG, "No handler for $action on ${ref.itemId}")
            return AssistantProductActionPlan(AssistantInteractionResult.NotActionable)
        }

        Logger.d(TAG, "Product ${ref.itemId} is tapped")
        pendingTap = ref.itemId
        eventService.track(tracked.events, EVENT_CLICK)

        return AssistantProductActionPlan(
            result = AssistantInteractionResult.Accepted,
            action = assistantAction,
            closeChat = true,
        )
    }

    private fun canOpen(product: AssistantProduct): Boolean {
        val hasTarget = !product.sku.isNullOrBlank() || isHttpUrl(product.url)
        if (!hasTarget) return false

        val handler = actionHandler ?: return false
        return canHandle(handler, AssistantAction.OpenProduct(product))
    }

    /** Asks the app handler whether it supports [action]; handler exceptions are logged and treated as false. */
    private fun canHandle(handler: AssistantActionHandler, action: AssistantAction): Boolean {
        return try {
            handler.canHandle(action)
        } catch (e: Exception) {
            Logger.e(TAG, "Assistant action handler failed: ${e.message}", e)
            false
        }
    }

    private fun isHttpUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.startsWith("http://") || url.startsWith("https://")
    }

    suspend fun prepare(
        assistantSelector: String,
        pageContext: PageContext,
        timeoutMillis: Long,
        options: Options,
        contentSettings: ContentSettings,
        customerUser: User?,
    ): AssistantPrepareResult {
        if (assistantSelector.isBlank()) {
            return invalidConfiguration("assistantSelector is blank")
        }
        if (timeoutMillis <= 0) {
            return invalidConfiguration("timeoutMillis must be positive")
        }

        val myGeneration = generation.incrementAndGet()
        val myResets = resets.get()
        config = null
        startScreenError = null
        isPreparing = true

        // Staleness check and result must run together on the main thread, where reset() also runs.
        suspend fun finish(result: () -> AssistantPrepareResult): AssistantPrepareResult =
            withContext(Dispatchers.Main) {
                outdated(myGeneration, myResets) ?: result().also {
                    reportPrepareFailure(it, pageContext, customerUser)
                }
            }

        try {
            val outcome = try {
                withTimeoutOrNull(timeoutMillis) {
                    ChooseOutcome(
                        repository.chooseBySelectorWithDetails(
                            selector = assistantSelector,
                            options = options,
                            contentSettings = contentSettings,
                            customerUser = customerUser,
                            pageContext = pageContext,
                        )?.data
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.e(TAG, "prepareAssistant failed: ${e.message}", e)
                return finish {
                    AssistantPrepareResult.Failure(
                        AssistantError(AssistantErrorCode.Network, e.message)
                    )
                }
            }

            outdated(myGeneration, myResets)?.let { return it }

            if (outcome == null) {
                return finish {
                    AssistantPrepareResult.Failure(
                        AssistantError(AssistantErrorCode.Timeout, "Preparation timed out")
                    )
                }
            }

            val response = outcome.response
            if (response == null) {
                return finish {
                    AssistantPrepareResult.Failure(
                        AssistantError(
                            AssistantErrorCode.Network,
                            "No response for $assistantSelector"
                        )
                    )
                }
            }

            val variations = response.data.asSequence().flatMap { it.payload.asSequence() }

            val rawConfigs = variations
                .flatMap { it.contents.asSequence() }
                .mapNotNull { it.custom?.json }
                .toList()

            if (rawConfigs.isEmpty()) {
                return finish {
                    AssistantPrepareResult.Unavailable(
                        AssistantUnavailableReason.NoMatchingConfiguration
                    )
                }
            }

            if (rawConfigs.distinct().size > 1) {
                return finish {
                    invalidConfiguration("Several different configurations for $assistantSelector")
                }
            }

            val json = try {
                JSONObject(rawConfigs.first())
            } catch (e: Throwable) {
                return finish {
                    invalidConfiguration("Malformed assistant custom.json: ${e.message}")
                }
            }

            if (!json.has("enabled")) {
                return finish {
                    invalidConfiguration("Assistant configuration has no enabled flag")
                }
            }

            if (!json.optBoolean("enabled", false)) {
                return finish {
                    AssistantPrepareResult.Unavailable(AssistantUnavailableReason.Disabled)
                }
            }

            val knownUser = repository.userForRequest(customerUser)
            val uid = response.user?.uid?.takeIf { it.isNotBlank() }
                ?: knownUser.uid?.takeIf { it.isNotBlank() }
            if (uid == null) {
                return finish {
                    AssistantPrepareResult.Failure(
                        AssistantError(
                            AssistantErrorCode.InvalidResponse,
                            "No user uid for $assistantSelector",
                        )
                    )
                }
            }

            val suggests = json.optJSONArray("suggests")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() }
                }
            }.orEmpty()

            val campaignId = variations
                .firstOrNull { variation -> variation.contents.any { it.custom?.json != null } }
                ?.campaignId

            val prepared = AssistantConfig(
                startScreen = AssistantStartScreen(
                    title = json.optString("title").takeIf { it.isNotBlank() } ?: DEFAULT_TITLE,
                    subtitle = json.optString("subtitle"),
                    suggests = suggests,
                ),
                context = AssistantDialogContext(
                    uid = uid,
                    pageContext = pageContext,
                    campaignId = campaignId,
                    eventUser = User(
                        uid = uid,
                        custom = customerUser?.custom,
                        ses = customerUser?.ses ?: response.user?.ses ?: knownUser.ses,
                    ),
                ),
            )

            return finish {
                config = prepared
                isPreparing = false
                AssistantPrepareResult.Ready
            }
        } finally {
            if (myGeneration == generation.get()) {
                isPreparing = false
            }
        }
    }

    /**
     * Shows the preparation error on the start screen and tracks Error
     * only if the chat is open and has no dialog yet.
     */
    private fun reportPrepareFailure(
        result: AssistantPrepareResult,
        pageContext: PageContext,
        customerUser: User?,
    ) {
        if (result !is AssistantPrepareResult.Failure) return

        startScreenError = PREPARE_ERROR

        if (!isPresented || dialogState.items.isNotEmpty()) return

        analytics.track(
            event = AssistantLifecycleEvent.Error,
            eventScope = AssistantEventScope(
                pageContext = pageContext,
                customerUser = customerUser,
                campaignId = null,
                threadId = null,
            ),
        )
    }

    /** Returns Cancelled after a reset, Superseded after a newer preparation, or null if still current. */
    private fun outdated(myGeneration: Long, myResets: Long): AssistantPrepareResult? = when {
        resets.get() != myResets -> AssistantPrepareResult.Cancelled
        generation.get() != myGeneration -> AssistantPrepareResult.Superseded
        else -> null
    }

    private class ChooseOutcome(val response: ContentResponse?)

    private fun invalidConfiguration(message: String): AssistantPrepareResult {
        Logger.i(TAG, message)
        return AssistantPrepareResult.Failure(
            AssistantError(AssistantErrorCode.InvalidConfiguration, message)
        )
    }
}
