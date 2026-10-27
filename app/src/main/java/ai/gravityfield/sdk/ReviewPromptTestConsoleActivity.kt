package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.models.CampaignContent
import ai.gravityfield.gravity_sdk.models.DeliveryMethod
import ai.gravityfield.gravity_sdk.models.Products
import ai.gravityfield.gravity_sdk.models.Slot
import ai.gravityfield.gravity_sdk.network.Campaign
import ai.gravityfield.gravity_sdk.ui.product_view_builder.ProductViewBuilder
import ai.gravityfield.sdk.ui.theme.GravitySDKTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manual + scenario test console for [ReviewPromptIntegration] (EGO-247).
 *
 * Exercises the real integration code against synthetic campaign content, with no dependency on
 * a live backend campaign: "Запустить проверки" runs pass/fail scenario checks, "Получить
 * рекомендации" runs the eligibility pipeline and renders the resulting product cards with the
 * real review button wired to `sendProductEngagement`/`dismissInApp`.
 */
class ReviewPromptTestConsoleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GravitySDKTheme {
                ReviewPromptTestConsoleScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun ReviewPromptTestConsoleScreen(onBack: () -> Unit) {
    val model = remember { ReviewPromptTestConsoleModel(ReviewPromptCoordinator.SELECTOR) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Review Prompt Test Console",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }

            ScenarioSection(model)
            RecommendationsSection(model)
            RealCampaignSection()
        }
    }

    ReviewPromptCoordinator.reviewFormSku?.let { sku ->
        ReviewFormDialog(
            sku = sku,
            onDismiss = { ReviewPromptCoordinator.reviewFormSku = null },
            onSubmit = { rating, comment ->
                ReviewPromptCoordinator.didSubmitReview(sku, rating, comment)
                ReviewPromptCoordinator.reviewFormSku = null
            },
        )
    }
}

@Composable
private fun ScenarioSection(model: ReviewPromptTestConsoleModel) {
    val scope = rememberCoroutineScope()
    ConsoleCard(title = "Автоматические проверки") {
        Text(
            "Фильтрация, исключение уже отправленных отзывов, пустые/ошибочные ответы, чужие кампании.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = { scope.launch { model.runScenarios() } },
            enabled = !model.isRunningScenarios,
        ) { Text("Запустить проверки") }

        if (model.isRunningScenarios) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        }

        if (model.scenarioResults.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                model.scenarioResults.forEach { result ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (result.passed) "✅" else "❌")
                        Column {
                            Text(result.name, style = MaterialTheme.typography.labelMedium)
                            Text(
                                result.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            val passed = model.scenarioResults.count { it.passed }
            Text(
                "$passed/${model.scenarioResults.size} пройдено",
                fontWeight = FontWeight.Bold,
                color = if (passed == model.scenarioResults.size) Color(0xFF2E7D32) else Color(0xFFC62828),
            )
        }
    }
}

@Composable
private fun RecommendationsSection(model: ReviewPromptTestConsoleModel) {
    val scope = rememberCoroutineScope()
    ConsoleCard(title = "Демо рекомендаций") {
        Text(
            "Синтетические SKU-1…5: SKU-2/SKU-4 недоступны для отзыва, SKU-5 уже отправлен.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = { scope.launch { model.loadRecommendations() } },
            enabled = !model.isLoadingRecommendations,
        ) { Text("Получить рекомендации") }

        if (model.isLoadingRecommendations) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        }

        model.recommendationsError?.let {
            Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
        }

        if (model.recommendedSlots.isNotEmpty()) {
            Text(
                "Клик по карточке шлёт ProductClickEngagement и вызывает dismissInApp; здесь нет " +
                    "реального in-app, поэтому оно вернёт false и форма не откроется — это " +
                    "защитное поведение, не баг.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                model.recommendedSlots.forEach { entry ->
                    model.integration.productViewBuilder.Build(entry.slot, entry.content, entry.campaign)
                }
            }
        } else if (model.didLoadRecommendations) {
            Text("Нет рекомендованных товаров — все отфильтрованы.", style = MaterialTheme.typography.bodySmall)
        }

        LogView(model.log)
    }
}

@Composable
private fun RealCampaignSection() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ConsoleCard(title = "Реальная кампания") {
        Text(
            "selector \"${ReviewPromptCoordinator.SELECTOR}\" · триггер должен быть настроен в " +
                "дашборде Gravity на нужный вызов trackView/triggerEvent",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { scope.launch { ReviewPromptCoordinator.fetchAndPreviewBySelector() } }) {
                Text("Получить и показать по selector")
            }
            Text(
                "Детерминировано: без конкуренции с другими кампаниями на том же триггере.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (ReviewPromptCoordinator.preview.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReviewPromptCoordinator.preview.forEach { entry ->
                    SelectorPreviewCard(entry)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { ReviewPromptCoordinator.triggerRealCampaign(context) }) {
                Text("Запустить триггер (trackView, Search page)")
            }
            Text(
                "Настоящий in-app SDK, но конкурирует по приоритету с другими кампаниями на этом " +
                    "триггере — если видите не ту кампанию, используйте кнопку выше.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = { ReviewPromptCoordinator.resetReviewStatus() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
            ) { Text("Сбросить статус отзывов") }
            Text(
                "Отправленные отзывы сохраняются и переживают перезапуск — сброс только для " +
                    "повторного тестирования демо.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LogView(ReviewPromptCoordinator.log)
    }
}

/** Section container giving the console a consistent card look instead of a flat wall of text. */
@Composable
private fun ConsoleCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Bounded, scrollable log output instead of an ever-growing plain list. */
@Composable
private fun LogView(lines: List<String>) {
    if (lines.isNotEmpty()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                lines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Product card for the "Получить и показать по selector" preview. Visually matches the real
 *  [ReviewPromptIntegration] card, but routes the click through [ReviewPromptCoordinator.reviewFromPreview]
 *  instead of the real `ProductViewBuilder`, since there is no live SDK-presented in-app here for
 *  `dismissInApp` to close. */
@Composable
private fun SelectorPreviewCard(entry: ReviewPromptCoordinator.PreviewSlot) {
    val name = entry.slot.item["name"] as? String ?: entry.slot.item["sku"] as? String ?: ""
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.heightIn(min = 120.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(name, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    ReviewPromptCoordinator.reviewFromPreview(entry.slot, entry.content, entry.campaign)
                },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) { Text("Оставить отзыв", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** Only for rendering — [integration]'s `productViewBuilder` needs *some* instance, but doesn't
 *  touch `api`. Actual eligibility checks always use a fresh [makeIntegration] pair — see
 *  [runScenarios]/[loadRecommendations] — so concurrent runs can never share or race on mutable
 *  API state (the exact bug `d32b023` fixed on iOS). */
private class ReviewPromptTestConsoleModel(private val selector: String) {
    data class ScenarioResult(val name: String, val passed: Boolean, val detail: String)
    data class SlotEntry(val slotId: String, val slot: Slot, val content: CampaignContent, val campaign: Campaign)

    val integration: ReviewPromptIntegration = ReviewPromptIntegration(
        api = TestConsoleReviewAPI(),
        selector = selector,
        fallbackProductViewBuilder = TestConsoleFallbackProductViewBuilder(),
        onOpenReview = {},
    )

    var scenarioResults by mutableStateOf<List<ScenarioResult>>(emptyList())
    var recommendedSlots by mutableStateOf<List<SlotEntry>>(emptyList())
    var isRunningScenarios by mutableStateOf(false)
    var isLoadingRecommendations by mutableStateOf(false)
    var didLoadRecommendations by mutableStateOf(false)
    var recommendationsError by mutableStateOf<String?>(null)
    val log = mutableStateListOf<String>()

    /** A fresh [ReviewPromptIntegration] + [TestConsoleReviewAPI] pair, owned solely by the
     *  caller. Each scenario and [loadRecommendations] run gets its own — sharing one mutable
     *  `api` across concurrent runs would let one run's `shouldThrow`/`ineligibleSkus` leak into
     *  another's in-flight request. */
    private fun makeIntegration(
        ineligibleSkus: Set<String> = emptySet(),
        shouldThrow: Boolean = false,
    ): Pair<ReviewPromptIntegration, TestConsoleReviewAPI> {
        val api = TestConsoleReviewAPI(ineligibleSkus, shouldThrow)
        val integration = ReviewPromptIntegration(
            api = api,
            selector = selector,
            fallbackProductViewBuilder = TestConsoleFallbackProductViewBuilder(),
            onOpenReview = {},
        )
        return integration to api
    }

    suspend fun runScenarios() {
        if (isRunningScenarios) return
        isRunningScenarios = true
        val results = mutableListOf<ScenarioResult>()
        try {
            // 1. Eligible-only subset survives filtering.
            run {
                val (integration, _) = makeIntegration(ineligibleSkus = setOf("SKU-2"))
                val (content, campaign) = fixture(listOf("SKU-1", "SKU-2", "SKU-3"), selector)
                val filtered = integration.recommendedContent(content, campaign)
                val skus = filtered?.products?.slots.orEmpty().mapNotNull { it.item["sku"] as? String }
                results += ScenarioResult(
                    "Фильтрует по доступности отзыва",
                    skus == listOf("SKU-1", "SKU-3"),
                    "Ожидали [SKU-1, SKU-3], получили $skus",
                )
            }

            // 2. Already-submitted review excluded even if eligible.
            run {
                val (integration, _) = makeIntegration()
                val (content, campaign) = fixture(listOf("SKU-1", "SKU-3"), selector)
                integration.didSubmitReview("SKU-3")
                val filtered = integration.recommendedContent(content, campaign)
                val skus = filtered?.products?.slots.orEmpty().mapNotNull { it.item["sku"] as? String }
                results += ScenarioResult(
                    "Скрывает уже отправленные отзывы",
                    skus == listOf("SKU-1"),
                    "После didSubmitReview(SKU-3) ожидали [SKU-1], получили $skus",
                )
            }

            // 3. No surviving products skips the in-app entirely (null).
            run {
                val (integration, _) = makeIntegration(ineligibleSkus = setOf("SKU-1", "SKU-2"))
                val (content, campaign) = fixture(listOf("SKU-1", "SKU-2"), selector)
                val filtered = integration.recommendedContent(content, campaign)
                results += ScenarioResult(
                    "Пропускает показ, если нет доступных товаров",
                    filtered == null,
                    if (filtered == null) "null, как и ожидалось" else "ожидали null, получили контент",
                )
            }

            // 4. A campaign with a different selector passes content through untouched.
            run {
                val (integration, api) = makeIntegration()
                val (content, campaign) = fixture(listOf("SKU-1", "SKU-2"), "sdk_cart_reco")
                val result = integration.recommendedContent(content, campaign)
                val unchanged = result?.products?.slots?.size == content.products?.slots?.size
                val noApiCall = api.requestedSkuBatches.isEmpty()
                results += ScenarioResult(
                    "Игнорирует кампании с другим selector",
                    unchanged && noApiCall,
                    "contentPassedThrough=$unchanged, apiCalled=${!noApiCall}",
                )
            }

            // 5. Slots without a recognizable sku skip the in-app without calling the API.
            run {
                val (integration, api) = makeIntegration()
                val (content, campaign) = fixture(emptyList(), selector)
                val result = integration.recommendedContent(content, campaign)
                results += ScenarioResult(
                    "Нет SKU — показ пропускается без вызова API",
                    result == null && api.requestedSkuBatches.isEmpty(),
                    "result=${if (result == null) "null" else "non-null"}, apiCalled=${api.requestedSkuBatches.isNotEmpty()}",
                )
            }

            // 6. Duplicate SKUs are de-duplicated before calling the eligibility API.
            run {
                val (integration, api) = makeIntegration()
                val (content, campaign) = fixture(listOf("SKU-1", "SKU-1", "SKU-3"), selector)
                integration.recommendedContent(content, campaign)
                val requested = api.requestedSkuBatches.lastOrNull()?.toSet() ?: emptySet()
                results += ScenarioResult(
                    "SKU дедуплицируются перед вызовом API",
                    requested == setOf("SKU-1", "SKU-3"),
                    "Запрошено: ${requested.sorted()}",
                )
            }

            // 7. An API error propagates (InAppPreparation.prepare maps this to a skipped in-app).
            run {
                val (integration, _) = makeIntegration(shouldThrow = true)
                val (content, campaign) = fixture(listOf("SKU-1"), selector)
                var threw = false
                try {
                    integration.recommendedContent(content, campaign)
                } catch (e: Exception) {
                    threw = true
                }
                results += ScenarioResult(
                    "Ошибка API выбрасывается наверх (SDK использует это, чтобы пропустить показ)",
                    threw,
                    if (threw) "ошибка выброшена, как и ожидалось" else "ошибка не выброшена",
                )
            }
        } finally {
            scenarioResults = results
            isRunningScenarios = false
        }
    }

    suspend fun loadRecommendations() {
        if (isLoadingRecommendations) return
        isLoadingRecommendations = true
        recommendationsError = null
        didLoadRecommendations = false
        try {
            val (integration, _) = makeIntegration(ineligibleSkus = setOf("SKU-2", "SKU-4"))
            val names = mapOf(
                "SKU-1" to "Кроссовки", "SKU-2" to "Рюкзак", "SKU-3" to "Термокружка",
                "SKU-4" to "Наушники", "SKU-5" to "Чехол",
            )
            val (content, campaign) = fixture(
                listOf("SKU-1", "SKU-2", "SKU-3", "SKU-4", "SKU-5"), selector, names,
            )
            integration.didSubmitReview("SKU-5")
            val filtered = integration.recommendedContent(content, campaign)
            val slots = filtered?.products?.slots.orEmpty()
            recommendedSlots = slots.mapIndexed { index, slot ->
                SlotEntry(slot.slotId ?: "slot-$index", slot, filtered ?: content, campaign)
            }
            log.add(
                "recommendedContent → ${recommendedSlots.size} товар(ов): " +
                    "${recommendedSlots.mapNotNull { it.slot.item["sku"] as? String }}"
            )
            didLoadRecommendations = true
        } catch (e: Exception) {
            recommendationsError = "Ошибка: $e"
        } finally {
            isLoadingRecommendations = false
        }
    }

    private fun fixture(
        skus: List<String>,
        selector: String?,
        names: Map<String, String> = emptyMap(),
    ): Pair<CampaignContent, Campaign> {
        val slots = skus.mapIndexed { index, sku ->
            Slot(mapOf("sku" to sku, "name" to (names[sku] ?: sku)), false, 1, "slot-$index", null)
        }
        val content = CampaignContent(
            "console-content", null, DeliveryMethod.BOTTOM_SHEET, "products", null,
            null, Products("recently-purchased", false, "recently purchased", slots, 1, 1),
            null, null, null,
        )
        return content to Campaign(selector, emptyList())
    }
}

/** Configured once at construction and never mutated afterward — each caller (one scenario, or
 *  one [ReviewPromptTestConsoleModel.loadRecommendations] run) owns its own instance via
 *  `makeIntegration`, so there's no shared mutable state for concurrent runs to race on. */
private class TestConsoleReviewAPI(
    private val ineligibleSkus: Set<String> = emptySet(),
    private val shouldThrow: Boolean = false,
) : ReviewEligibilityAPI {
    class SimulatedFailure : Exception("Simulated API failure")

    private val _requestedSkuBatches = mutableListOf<List<String>>()
    val requestedSkuBatches: List<List<String>> get() = _requestedSkuBatches

    override suspend fun eligibleSkus(skus: List<String>): Set<String> {
        _requestedSkuBatches.add(skus)
        delay(150)
        if (shouldThrow) throw SimulatedFailure()
        return skus.toSet() - ineligibleSkus
    }
}

private class TestConsoleFallbackProductViewBuilder : ProductViewBuilder {
    @Composable
    override fun Build(slot: Slot, content: CampaignContent, campaign: Campaign) {
        Text(slot.item["name"] as? String ?: "")
    }
}
