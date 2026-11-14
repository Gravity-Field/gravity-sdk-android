package ai.gravityfield.gravity_sdk

import ai.gravityfield.gravity_sdk.models.*
import ai.gravityfield.gravity_sdk.network.ContentResponse
import ai.gravityfield.gravity_sdk.network.GravityRepository
import ai.gravityfield.gravity_sdk.utils.Logger
import android.app.Activity
import android.os.Looper
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeadlessCampaignWireTest {
    private data class Request(val path: String, val body: JSONObject)
    private val recorded = CopyOnWriteArrayList<Request>()
    private val callbacks = CopyOnWriteArrayList<TrackingEvent>()
    private val page = PageContext(ContextType.OTHER, emptyList(), "/headless")
    private val events = listOf(CustomEvent(type = "checkout", name = "Checkout"))
    private lateinit var server: HeadlessRecordingServer
    private val started = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private var blockedPath: String? = null
    private var campaignIds = "[]"
    @Volatile private var trackingUser = """{"uid":"server-user","ses":"server-session"}"""
    private var trackingStatus = 200
    private val contents = mutableMapOf<String, String>()
    private val chooseStatus = mutableMapOf<String, Int>()
    private val disconnectedChooseIds = mutableSetOf<String>()
    private lateinit var sdk: GravitySDK
    private val url get() = server.url

    @Before fun setUp() {
        // These tests intentionally cause transport errors. Keep SDK diagnostics from
        // posting them to the production error collector; all tested traffic is local.
        Logger.errorReportingEnabled = false
        server = HeadlessRecordingServer { path, body ->
            recorded += Request(path, body)
            if (path == blockedPath) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            val id = body.optJSONArray("data")?.optJSONObject(0)?.optString("campaignId")
            if (path == "/choose" && id in disconnectedChooseIds) return@HeadlessRecordingServer null
            val response = when (path) {
                "/visit", "/event" -> """{"user":$trackingUser,"campaigns":$campaignIds}"""
                "/choose" -> contents[id?.takeIf { it.isNotEmpty() } ?: "selector"] ?: """{"data":[]}"""
                else -> "{}"
            }
            val status = if (path == "/choose") chooseStatus[id] ?: 200 else trackingStatus
            status to response
        }
        server.start()
        initialize()
        sdk.isFetchContentOnTrack = true
        sdk.staleContentTimeoutMillis = 10_000L
    }

    private fun initialize() {
        GravitySDK.initialize(RuntimeEnvironment.getApplication(), "headless-key", "headless-section", { callbacks += it })
        sdk = GravitySDK.instance
        sdk.setOptions(null, null, url)
    }

    @After fun tearDown() {
        release.countDown()
        sdk.dispose()
        server.stop()
        sdk.isFetchContentOnTrack = true
        sdk.staleContentTimeoutMillis = 10_000L
        Logger.errorReportingEnabled = true
    }

    private fun campaigns(vararg pairs: Pair<String, Int>) {
        campaignIds = pairs.joinToString(prefix = "[", postfix = "]") { (id, priority) ->
            """{"campaignId":"$id","priority":$priority,"delayTime":60000}"""
        }
    }

    private fun content(id: String, delivery: String = "modal", step: String = "null"): String = """
        {"user":{"uid":"server-user","ses":"server-session"},"unknown":null,"data":[{"selector":"custom","payload":[{
          "campaignId":"$id","experienceId":"e","variationId":"v","decisionId":"d","contents":[{
            "contentId":"content-$id","contentType":"native","deliveryMethod":"$delivery","step":$step,
            "variables":{"custom_title":"Title $id","unknown":null},
            "events":[{"type":"impression","urls":["$url/impression"]},{"type":"visible_impression","urls":["$url/visible"]}]
          }]
        }]}]}
    """.trimIndent()

    private fun chosenIds() = recorded.filter { it.path == "/choose" }
        .map { it.body.getJSONArray("data").getJSONObject(0).getString("campaignId") }

    private fun contentWithLoad(id: String, step: String = "null") = content(id, step = step)
        .replace("\"custom_title\"", """"onLoad":{"action":"load"},"onImpression":{"action":"impression"},"onVisibleImpression":{"action":"visible_impression"},"custom_title"""")
        .replace("\"events\":[", """"events":[{"type":"load","urls":["$url/load-$id"]},""")

    private fun noShowFetches(): List<suspend () -> GravityDataResponse<ContentResponse>?> = listOf(
        { sdk.trackViewNoShow(page) }, { sdk.triggerEventNoShow(events, page) },
    )

    @Test fun standardEventWireBodiesOmitAbsentOptionalFieldsAndKeepEventTime() = runBlocking {
        sdk.isFetchContentOnTrack = false
        sdk.triggerEventNoShow(listOf(
            AddToCartEvent(value = 1.0, productId = "p", quantity = 1),
            RemoveFromCartEvent(value = 1.0, productId = "p", quantity = 1),
            SyncCartEvent(value = 1.0),
            PurchaseEvent(uniqueTransactionId = "t", value = 1.0, cart = emptyList()),
            AddToWishlistEvent(value = 1.0, productId = "p"),
            SignUpEvent(cuid = "c"), LoginEvent(cuid = "c"),
            CustomEvent(type = "x", name = "X"),
        ), page)
        assertEquals(listOf("/event"), recorded.map { it.path })
        val data = recorded.single().body.getJSONArray("data")
        assertEquals(8, data.length())
        for (index in 0 until data.length()) {
            val event = data.getJSONObject(index)
            assertTrue(event.getString("eventTime").endsWith("Z"))
            for (key in listOf("currency", "cart", "hashedEmail", "cuid", "cuidType", "customProps")) {
                val explicitlySupplied = (key == "cart" && index == 3) || (key == "cuid" && index in 5..6)
                if (explicitlySupplied) {
                    assertTrue("Explicit $key must be present: $event", event.has(key))
                    assertFalse("Explicit $key must not be JSON null: $event", event.isNull(key))
                } else {
                    assertFalse("Absent $key must be omitted: $event", event.has(key))
                }
            }
        }
    }

    @Test fun chosenDelayIsNormalizedAndRawChooseJsonIsPreservedForBothMethods() = runBlocking {
        contents["101"] = content("101")
        for (fetch in noShowFetches()) {
            for ((field, expected) in listOf(
                "" to 0, ",\"delayTime\":0" to 0, ",\"delayTime\":-3" to 0,
                ",\"delayTime\":1500" to 1500, ",\"delayTime\":\" 1500.0 \"" to 1500,
                ",\"delayTime\":60000" to 60000, ",\"delayTime\":null" to 0,
                ",\"delayTime\":true" to 0, ",\"delayTime\":{}" to 0,
                ",\"delayTime\":[]" to 0, ",\"delayTime\":\"0x10\"" to 0,
            )) {
                campaignIds = """[{"campaignId":"101","priority":1$field}]"""
                callbacks.clear()
                val result = withTimeout(3000) { fetch() }!!
                assertEquals(expected, result.delayTime)
                assertEquals(ai.gravityfield.gravity_sdk.network.parseContentResponse(contents.getValue("101")).json, result.json)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(listOf(expected), callbacks.filterIsInstance<ContentLoadEvent>().map { it.delayTime })
            }
        }
    }

    @Test fun fallbackReturnsAcceptedCampaignDelayForBothMethods() = runBlocking {
        campaignIds = """[{"campaignId":"101","priority":30,"delayTime":99},
            {"campaignId":"202","priority":20,"delayTime":88},
            {"campaignId":"303","priority":10,"delayTime":77}]"""
        contents["101"] = content("101")
        chooseStatus["101"] = 503
        contents["202"] = content("202", step = "1")
        contents["303"] = content("303")
        for (fetch in noShowFetches()) {
            recorded.clear()
            callbacks.clear()
            val result = fetch()!!
            assertEquals(77, result.delayTime)
            assertEquals(listOf("101", "202", "303"), chosenIds())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(77), callbacks.filterIsInstance<ContentLoadEvent>().map { it.delayTime })
        }
    }

    @Test fun zeroDelayDoesNotChooseAgainOrPresentWithAnActiveActivity() = runBlocking {
        campaignIds = """[{"campaignId":"101","delayTime":0}]"""
        contents["101"] = content("101").replace("\"custom_title\"", "\"elements\":[{\"type\":\"text\",\"text\":\"Ready to present\"}],\"custom_title\"")
        val preparations = java.util.concurrent.atomic.AtomicInteger()
        GravitySDK.initialize(RuntimeEnvironment.getApplication(), "headless-key", "headless-section", { callbacks += it },
            inAppPreparation = InAppPreparation { _, _ -> preparations.incrementAndGet(); null })
        sdk = GravitySDK.instance
        sdk.setOptions(null, null, url)
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        controller.windowFocusChanged(true)
        try {
            // Positive control: this payload and Activity really reach automatic
            // presentation preparation. A missing Activity or empty elements cannot
            // make the NoShow assertion pass vacuously.
            sdk.trackView(page, controller.get())
            withTimeout(3000) {
                while (preparations.get() == 0) {
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(10)
                }
            }
            preparations.set(0)
            for (fetch in noShowFetches()) {
                recorded.clear()
                callbacks.clear()
                val before = org.robolectric.shadows.ShadowDialog.getLatestDialog()
                assertEquals(0, withTimeout(3000) { fetch() }!!.delayTime)
                // Drain queued presentation work while a real resumed Activity is available.
                repeat(10) {
                    shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
                    delay(10)
                }
                assertEquals("NoShow must not enter presentation preparation", 0, preparations.get())
                assertSame(before, org.robolectric.shadows.ShadowDialog.getLatestDialog())
                assertEquals(listOf("101"), chosenIds())
                assertTrue(callbacks.none { it is ContentImpressionEvent || it is ContentVisibleImpressionEvent })
                assertTrue(recorded.all { it.path in listOf("/visit", "/event", "/choose") })
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun directDetailsAndLegacyConstructorsDefaultToZeroDelay() = runBlocking {
        contents["101"] = JSONObject(content("101")).put("delayTime", 60000).toString()
        val byId = sdk.getContentByCampaignIdWithDetails("101", page)!!
        assertEquals(0, byId.delayTime)
        assertEquals(0, GravityDataResponse(byId.data, byId.json).delayTime)
        // Selector requests have no campaign ID.
        contents["selector"] = contents.getValue("101")
        val bySelector = sdk.getContentBySelectorWithDetails("custom", page)!!
        assertEquals(0, bySelector.delayTime)
        assertEquals(byId.json, bySelector.json)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(0, 0), callbacks.filterIsInstance<ContentLoadEvent>().map { it.delayTime })
    }

    @Test fun rawWireDecimalsRoundThroughDoubleBeforeIntegerConversion() = runBlocking {
        // Android JSONObject normally rounds these literals through Double first.
        campaignIds = """[{"campaignId":"101","priority":0.99999999999999999999,"delayTime":9},
            {"campaignId":"202","priority":1,"delayTime":1500.99999999999999999999}]"""
        // Rounded priorities tie at 1, so the empty first choice falls back to 202.
        contents["202"] = content("202")
        for (fetch in noShowFetches()) {
            recorded.clear()
            callbacks.clear()
            val result = fetch()!!
            assertEquals(listOf("101", "202"), chosenIds())
            assertEquals(1501, result.delayTime)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(1501), callbacks.filterIsInstance<ContentLoadEvent>().map { it.delayTime })
        }
    }

    @Test fun rawExponentVectorsSurviveVisitAndEventWithSiblingFallback() = runBlocking {
        val vectors = linkedMapOf(
            "1e128" to Int.MAX_VALUE, "1e-129" to 0, "1e-300" to 0,
            "1e400" to 0, "1e-400" to 0, "-1e400" to 0,
            "NaN" to 0, "Infinity" to 0, "-Infinity" to 0,
            "0.99999999999999999999" to 1, "1500.99999999999999999999" to 1501,
            "0" to 0, "42" to 42, "-42" to 0, "1.9" to 1, "-1.9" to 0,
        )
        contents["202"] = content("202")
        for ((token, delay) in vectors) {
            campaignIds = """[{"campaignId":"101","priority":$token,"delayTime":$token},
                {"campaignId":"202","priority":-2147483648,"delayTime":42}]"""
            for (fetch in noShowFetches()) {
                contents["101"] = content("101")
                recorded.clear()
                val result = fetch()!!
                assertEquals(token, delay, result.delayTime)
                assertEquals(token, listOf("101"), chosenIds())
                contents.remove("101")
                recorded.clear()
                assertEquals(token, 42, fetch()!!.delayTime)
                assertEquals(token, listOf("101", "202"), chosenIds())
            }
        }
    }

    @Test fun nonFiniteWireNumberKeepsCampaignAndSiblingsForBothMethods() = runBlocking {
        campaignIds = """[{"campaignId":"101","priority":1e400,"delayTime":1e400},
            {"campaignId":"202","priority":-1,"delayTime":42}]"""
        contents["202"] = content("202")
        for (fetch in noShowFetches()) {
            recorded.clear()
            val result = fetch()!!
            assertEquals(42, result.delayTime)
            assertEquals(listOf("101", "202"), chosenIds())
        }
    }

    @Test fun androidJsonDecoderTypesAndOverflowAreExplicit() {
        // Android JSONTokener.readLiteral returns Integer, Long or Double, never Float.
        // An overflowing literal becomes infinity and JSONObject.put rejects it.
        val samples = listOf("1500", "2147483648", "1500.9", "1e3")
        assertEquals(listOf(Int::class.javaObjectType, Long::class.javaObjectType,
            Double::class.javaObjectType, Double::class.javaObjectType),
            samples.map { org.json.JSONTokener(it).nextValue().javaClass })
        assertEquals(Double.POSITIVE_INFINITY, org.json.JSONTokener("1e400").nextValue())
        assertThrows(org.json.JSONException::class.java) { JSONObject("""{"priority":1e400}""") }
        val result = ai.gravityfield.gravity_sdk.network.parseCampaignIdsResponse("""
            {"campaigns":[{"campaignId":"c","priority":1e400,"delayTime":1e400},
                {"campaignId":"sibling","priority":7,"delayTime":42}]}
        """.trimIndent())
        assertEquals(2, result.campaigns.size)
        assertNull(result.campaigns[0].priority)
        assertNull(result.campaigns[0].delayTime)
        assertEquals(7, result.campaigns[1].priority)
        assertEquals(42, result.campaigns[1].delayTime)
    }

    @Test fun publicResponseStoresConstructorValuesAndLoadEventNormalizesNegatives() = runBlocking {
        contents["101"] = content("101")
        val original = sdk.getContentByCampaignIdWithDetails("101", page)!!
        val supplied = GravityDataResponse(data = original.data, json = original.json, delayTime = -1)
        assertEquals(-1, supplied.delayTime)
        assertEquals(Int.MIN_VALUE, original.copy(delayTime = Int.MIN_VALUE).delayTime)
        val (data, json, delay) = supplied
        assertSame(original.data, data)
        assertSame(original.json, json)
        assertEquals(-1, delay)
        val campaign = original.data.data.single()
        val content = campaign.payload.single().contents.single()
        assertEquals(0, ContentLoadEvent(content, campaign, -1).delayTime)
        assertEquals(0, ContentLoadEvent(content = content, campaign = campaign, delayTime = Int.MIN_VALUE).delayTime)
        assertEquals(1500, ContentLoadEvent(content, campaign, 1500).delayTime)
    }

    @Test fun headlessPassesHostOptionsUnchanged() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        for (implicit in listOf(false, true)) {
            sdk.setOptions(Options(isImplicitImpression = implicit, isReturnUserInfo = true), null, url)
            for (fetch in noShowFetches()) {
                recorded.clear()
                assertNotNull(fetch())
                assertEquals(2, recorded.size)
                recorded.forEach {
                    val options = it.body.getJSONObject("options")
                    assertEquals(implicit, options.getBoolean("isImplicitImpression"))
                    assertTrue(options.getBoolean("isReturnUserInfo"))
                }
            }
        }
    }

    @Test fun currentTimeoutShortenedDuringTrackingSuppressesChoose() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        blockedPath = "/visit"
        val pending = async(Dispatchers.IO) { sdk.trackViewNoShow(page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        sdk.staleContentTimeoutMillis = 0L
        release.countDown()
        assertNull(pending.await())
        assertTrue(chosenIds().isEmpty())
    }

    @Test fun currentTimeoutExtendedDuringChooseAcceptsContent() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        sdk.staleContentTimeoutMillis = 1000L
        blockedPath = "/choose"
        val pending = async(Dispatchers.IO) { sdk.triggerEventNoShow(events, page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        delay(1100)
        sdk.staleContentTimeoutMillis = 10_000L
        release.countDown()
        assertNotNull(pending.await())
    }

    @Test fun noCampaignsReturnNullForBothMethods() = runBlocking {
        assertNull(sdk.trackViewNoShow(page))
        assertNull(sdk.triggerEventNoShow(events, page))
        assertEquals(listOf("/visit", "/event"), recorded.map { it.path })
    }

    @Test fun highestPriorityReturnsTypedAndRawContentWithoutActivityOrImpressions() = runBlocking {
        campaigns("101" to 1, "202" to 9, "303" to 2)
        contents["202"] = content("202")
        sdk.setUser("custom-user", "custom-session")
        sdk.lockPresentation()
        val result = sdk.trackViewNoShow(page)!!
        assertEquals(listOf("202"), chosenIds())
        val content = result.data.data.single().payload.single().contents.single()
        assertEquals("content-202", content.contentId)
        assertEquals("Title 202", content.rawVariables["custom_title"])
        assertTrue(result.json.containsKey("unknown"))
        assertNull(result.json["unknown"])
        recorded.forEach {
            assertEquals("custom-user", it.body.getJSONObject("user").getString("custom"))
            assertEquals("custom-session", it.body.getJSONObject("user").getString("ses"))
            assertTrue(it.body.getJSONObject("options").getBoolean("isImplicitImpression"))
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(callbacks.none { it is ContentImpressionEvent || it is ContentVisibleImpressionEvent })
        assertEquals(listOf("/visit", "/choose"), recorded.map { it.path })
    }

    @Test fun emptyAndStepOnlyContentFallBackToNextPriority() = runBlocking {
        campaigns("101" to 30, "202" to 20, "303" to 10, "404" to 0)
        contents["202"] = content("202", step = "1")
        contents["303"] = content("303")
        assertEquals("content-303", sdk.triggerEventNoShow(events, page)!!.data.data.single().payload.single().contents.single().contentId)
        assertEquals(listOf("101", "202", "303"), chosenIds())
        assertEquals("checkout", recorded.first().body.getJSONArray("data").getJSONObject(0).getString("type"))
    }

    @Test fun allEmptyCampaignsReturnNull() = runBlocking {
        campaigns("101" to 2, "202" to 1)
        assertNull(sdk.trackViewNoShow(page))
        assertEquals(listOf("101", "202"), chosenIds())
    }

    @Test fun fetchDisabledStillTracksVisitAndEvent() = runBlocking {
        campaigns("101" to 1)
        sdk.isFetchContentOnTrack = false
        assertNull(sdk.trackViewNoShow(page))
        assertNull(sdk.triggerEventNoShow(events, page))
        assertEquals(listOf("/visit", "/event"), recorded.map { it.path })
    }

    @Test fun headlessSettingsAreIndependentClampAndPersistAcrossReinitialize() = runBlocking {
        assertTrue(sdk.isFetchContentOnTrack)
        assertEquals(10_000L, sdk.staleContentTimeoutMillis)
        sdk.setOptions(Options(isReturnUserInfo = true), null, url)
        sdk.isFetchContentOnTrack = false
        sdk.staleContentTimeoutMillis = -1
        assertFalse(sdk.isFetchContentOnTrack)
        assertEquals(0L, sdk.staleContentTimeoutMillis)
        campaigns("101" to 1)
        contents["101"] = content("101")
        assertNull(sdk.trackViewNoShow(page))
        sdk.isFetchContentOnTrack = true
        assertNull(sdk.triggerEventNoShow(events, page))
        assertTrue(chosenIds().isEmpty())
        assertEquals(listOf("/visit", "/event"), recorded.map { it.path })
        assertTrue(recorded.all { it.body.getJSONObject("options").getBoolean("isReturnUserInfo") })
        sdk.isFetchContentOnTrack = false
        initialize()
        assertFalse(sdk.isFetchContentOnTrack)
        assertEquals(0L, sdk.staleContentTimeoutMillis)
        assertNull(sdk.trackViewNoShow(page))
        sdk.isFetchContentOnTrack = true
        sdk.staleContentTimeoutMillis = 10_000L
        assertNotNull(sdk.trackViewNoShow(page))
    }

    @Test fun onlyAcceptedResponseEmitsLoadCallbacksForEveryContent() = runBlocking {
        campaigns("101" to 3, "202" to 2, "303" to 1)
        contents["101"] = content("101", step = "1")
        val accepted = JSONObject(content("202"))
        val secondCampaign = JSONObject(content("303")).getJSONArray("data").getJSONObject(0)
        val secondPayload = JSONObject(content("404", step = "2")).getJSONArray("data")
            .getJSONObject(0).getJSONArray("payload").getJSONObject(0)
        val firstCampaign = accepted.getJSONArray("data").getJSONObject(0)
        val firstPayload = firstCampaign.getJSONArray("payload").getJSONObject(0)
        val stepContent = JSONObject(content("205", step = "1")).getJSONArray("data")
            .getJSONObject(0).getJSONArray("payload").getJSONObject(0).getJSONArray("contents").getJSONObject(0)
        firstPayload.getJSONArray("contents").put(stepContent)
        firstCampaign.getJSONArray("payload").put(secondPayload)
        accepted.getJSONArray("data").put(secondCampaign)
        contents["202"] = accepted.toString()
        for (fetch in listOf<suspend () -> GravityDataResponse<ContentResponse>?>(
            { sdk.trackViewNoShow(page) }, { sdk.triggerEventNoShow(events, page) },
        )) {
            callbacks.clear()
            assertNotNull(fetch())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(4, callbacks.size)
            val loads = callbacks.filterIsInstance<ContentLoadEvent>()
            assertEquals(listOf("content-202", "content-205", "content-404", "content-303"),
                loads.map { it.content.contentId })
            assertEquals(listOf("202", "202", "202", "303"),
                loads.map { it.campaign.payload.first().campaignId })
        }
        assertEquals(listOf("101", "202", "101", "202"), chosenIds())
        assertTrue(recorded.all { it.path in listOf("/visit", "/event", "/choose") })
    }

    @Test fun chooseHttpErrorFallsBackEvenWhenBodyLooksLikeContent() = runBlocking {
        campaigns("101" to 2, "202" to 1)
        contents["101"] = content("101")
        chooseStatus["101"] = 503
        contents["202"] = content("202")
        for (fetch in noShowFetches()) {
            recorded.clear()
            assertEquals("202", fetch()!!.data.data.single().payload.single().campaignId)
            assertEquals(listOf("101", "202"), chosenIds())
        }
    }

    @Test fun trackingHttpErrorReturnsNullWithoutChoose() = runBlocking {
        campaigns("101" to 1)
        trackingStatus = 503
        assertNull(sdk.trackViewNoShow(page))
        assertNull(sdk.triggerEventNoShow(events, page))
        assertTrue(chosenIds().isEmpty())
    }

    @Test fun malformedChooseFallsBack() = runBlocking {
        campaigns("101" to 2, "202" to 1)
        contents["101"] = "invalid json"
        contents["202"] = content("202")
        for (fetch in noShowFetches()) {
            recorded.clear()
            assertEquals("202", fetch()!!.data.data.single().payload.single().campaignId)
            assertEquals(listOf("101", "202"), chosenIds())
        }
    }

    @Test fun disconnectedChooseFallsBack() = runBlocking {
        campaigns("101" to 2, "202" to 1)
        disconnectedChooseIds += "101"
        contents["202"] = content("202")
        for (fetch in noShowFetches()) {
            recorded.clear()
            assertEquals("202", fetch()!!.data.data.single().payload.single().campaignId)
            assertEquals(listOf("101", "202"), chosenIds())
        }
    }

    @Test fun allFailedCandidatesReturnNullWithoutLoadEvents() = runBlocking {
        campaigns("101" to 3, "202" to 2, "303" to 1)
        contents["101"] = contentWithLoad("101")
        chooseStatus["101"] = 503
        contents["202"] = "invalid json"
        disconnectedChooseIds += "303"
        for (fetch in noShowFetches()) {
            recorded.clear()
            assertNull(fetch())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("101", "202", "303"), chosenIds())
            assertTrue(callbacks.isEmpty())
            assertTrue(recorded.all { it.path in listOf("/visit", "/event", "/choose") })
        }
    }

    @Test fun trackingEstablishesAnonymousSessionForChoose() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        for (fetch in listOf<suspend () -> GravityDataResponse<ContentResponse>?>(
            { sdk.trackViewNoShow(page) }, { sdk.triggerEventNoShow(events, page) },
        )) {
            trackingUser = """{"uid":"tracking-user","ses":"tracking-session"}"""
            assertNotNull(fetch())
            val user = recorded.last().body.getJSONObject("user")
            assertEquals("tracking-user", user.getString("uid"))
            assertEquals("tracking-session", user.getString("ses"))
        }
    }

    @Test fun chooseResponseUpdatesAnonymousSessionForNextPublicContentRequest() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        trackingUser = """{"uid":"tracking-user","ses":"tracking-session"}"""
        for (fetch in listOf<suspend () -> GravityDataResponse<ContentResponse>?>(
            { sdk.trackViewNoShow(page) }, { sdk.triggerEventNoShow(events, page) },
        )) {
            assertNotNull(fetch())
            assertNotNull(sdk.getContentByCampaignIdWithDetails("101", page))
            val user = recorded.last().body.getJSONObject("user")
            assertEquals("server-user", user.getString("uid"))
            assertEquals("server-session", user.getString("ses"))
        }
    }

    @Test fun fallbackChooseUsesSessionSavedByPreviousChooseResponse() = runBlocking {
        campaigns("101" to 2, "202" to 1)
        contents["101"] = """{"user":{"uid":"choose-user","ses":"choose-session"},"data":[]}"""
        contents["202"] = content("202")
        for (fetch in listOf<suspend () -> GravityDataResponse<ContentResponse>?>(
            { sdk.trackViewNoShow(page) }, { sdk.triggerEventNoShow(events, page) },
        )) {
            assertNotNull(fetch())
            val user = recorded.last().body.getJSONObject("user")
            assertEquals("choose-user", user.getString("uid"))
            assertEquals("choose-session", user.getString("ses"))
        }
    }

    @Test fun missingResponseSessionUsesStoredSessionForSameUid() = runBlocking {
        for (fetch in listOf<suspend () -> GravityDataResponse<ContentResponse>?>(
            { sdk.trackViewNoShow(page) }, { sdk.triggerEventNoShow(events, page) },
        )) {
            trackingUser = """{"uid":"same-user","ses":"stored-session"}"""
            assertNotNull(GravityRepository.instance.visit(page, Options(), sdk.userIdentitySnapshot()))
            trackingUser = """{"uid":"same-user"}"""
            campaigns("101" to 1)
            contents["101"] = content("101")
            assertNotNull(fetch())
            val user = recorded.last().body.getJSONObject("user")
            assertEquals("same-user", user.getString("uid"))
            assertEquals("stored-session", user.optString("ses"))
        }
    }

    @Test fun missingResponseSessionKeepsStoredSessionWhenUidChanges() = runBlocking {
        for (fetch in listOf<suspend () -> GravityDataResponse<ContentResponse>?>(
            { sdk.trackViewNoShow(page) }, { sdk.triggerEventNoShow(events, page) },
        )) {
            trackingUser = """{"uid":"stored-user","ses":"stored-session"}"""
            assertNotNull(GravityRepository.instance.visit(page, Options(), sdk.userIdentitySnapshot()))
            trackingUser = """{"uid":"different-user"}"""
            campaigns("101" to 1)
            contents["101"] = content("101")
            assertNotNull(fetch())
            val user = recorded.last().body.getJSONObject("user")
            assertEquals("different-user", user.getString("uid"))
            assertEquals("stored-session", user.getString("ses"))
        }
    }

    @Test fun missingResponseUidUsesStoredUidAndKeepsExplicitSession() = runBlocking {
        trackingUser = """{"uid":"stored-user","ses":"stored-session"}"""
        assertNotNull(GravityRepository.instance.visit(page, Options(), sdk.userIdentitySnapshot()))
        trackingUser = """{"ses":"response-session"}"""
        campaigns("101" to 1)
        contents["101"] = content("101")
        assertNotNull(sdk.triggerEventNoShow(events, page))
        val user = recorded.last().body.getJSONObject("user")
        assertEquals("stored-user", user.optString("uid"))
        assertEquals("response-session", user.getString("ses"))
    }

    @Test fun missingResponseSessionUsesStoreAtResponseAcceptance() = runBlocking {
        trackingUser = """{"uid":"same-user","ses":"initial-session"}"""
        assertNotNull(GravityRepository.instance.visit(page, Options(), sdk.userIdentitySnapshot()))
        campaigns("101" to 1)
        contents["101"] = content("101")
        blockedPath = "/visit"
        val pending = async(Dispatchers.IO) { sdk.trackViewNoShow(page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        trackingUser = """{"uid":"same-user","ses":"accepted-session"}"""
        assertNotNull(GravityRepository.instance.event(events, Date(), page, Options(), sdk.userIdentitySnapshot()))
        trackingUser = """{"uid":"same-user"}"""
        release.countDown()
        assertNotNull(pending.await())
        val user = recorded.last().body.getJSONObject("user")
        assertEquals("same-user", user.getString("uid"))
        assertEquals("accepted-session", user.optString("ses"))
    }

    @Test fun fallbackChooseUsesSessionSavedByParallelTracking() = runBlocking {
        trackingUser = """{"uid":"first-user","ses":"first-session"}"""
        campaigns("101" to 2, "202" to 1)
        contents["202"] = content("202")
        blockedPath = "/choose"
        val pending = async(Dispatchers.IO) { sdk.trackViewNoShow(page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        trackingUser = """{"uid":"parallel-user","ses":"parallel-session"}"""
        assertNotNull(GravityRepository.instance.event(events, Date(), page, Options(), sdk.userIdentitySnapshot()))
        assertEquals(User(uid = "parallel-user", ses = "parallel-session"),
            GravityRepository.instance.userForRequest(null))
        release.countDown()
        assertNotNull(pending.await())
        assertEquals(listOf("101", "202"), chosenIds())
        val users = recorded.filter { it.path == "/choose" }.map { it.body.getJSONObject("user") }
        assertEquals("first-user", users[0].getString("uid"))
        assertEquals("first-session", users[0].getString("ses"))
        assertEquals("parallel-user", users[1].getString("uid"))
        assertEquals("parallel-session", users[1].getString("ses"))
    }

    @Test fun userChangedDuringTrackingSuppressesChoose() = runBlocking {
        campaigns("101" to 1)
        blockedPath = "/event"
        sdk.setUser("old-user", "old-session")
        val pending = async(Dispatchers.IO) { sdk.triggerEventNoShow(events, page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        sdk.setUser("new-user", "new-session")
        release.countDown()
        assertNull(pending.await())
        assertTrue(chosenIds().isEmpty())
    }

    @Test fun userChangedDuringChooseDiscardsContent() = runBlocking {
        campaigns("101" to 2, "202" to 1)
        contents["101"] = contentWithLoad("101")
        contents["202"] = contentWithLoad("202")
        blockedPath = "/choose"
        sdk.setUser("old-user", "old-session")
        val pending = async(Dispatchers.IO) { sdk.trackViewNoShow(page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        sdk.setUser("new-user", "new-session")
        release.countDown()
        assertNull(pending.await())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(callbacks.isEmpty())
        assertEquals(listOf("101"), chosenIds())
        assertTrue(recorded.none { it.path.startsWith("/load-") })
    }

    @Test fun staleTrackingSuppressesChoose() = runBlocking {
        campaigns("101" to 1)
        sdk.staleContentTimeoutMillis = 1000
        blockedPath = "/visit"
        val pending = async(Dispatchers.IO) { sdk.trackViewNoShow(page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        Thread.sleep(1100)
        release.countDown()
        assertNull(pending.await())
        assertTrue(chosenIds().isEmpty())
    }

    @Test fun staleChooseDiscardsContent() = runBlocking {
        campaigns("101" to 2, "202" to 1)
        contents["101"] = contentWithLoad("101")
        contents["202"] = contentWithLoad("202")
        sdk.staleContentTimeoutMillis = 1000
        blockedPath = "/choose"
        val pending = async(Dispatchers.IO) { sdk.trackViewNoShow(page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        Thread.sleep(1100)
        release.countDown()
        assertNull(pending.await())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(callbacks.isEmpty())
        assertEquals(listOf("101"), chosenIds())
        assertTrue(recorded.none { it.path.startsWith("/load-") })
    }

    @Test fun presentationLockDuringChooseDoesNotDiscardContent() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        blockedPath = "/choose"
        val pending = async(Dispatchers.IO) { sdk.trackViewNoShow(page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        sdk.lockPresentation()
        release.countDown()
        assertNotNull(pending.await())
    }

    @Test fun cancelledRequestPropagatesCancellation() = runBlocking {
        campaigns("101" to 1)
        blockedPath = "/event"
        val pending = async(Dispatchers.IO) { sdk.triggerEventNoShow(events, page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        pending.cancel()
        release.countDown()
        try { pending.await(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertTrue(chosenIds().isEmpty())
    }

    @Test fun disposeDiscardsAnInFlightChoose() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        blockedPath = "/choose"
        val pending = async(Dispatchers.IO) { sdk.trackViewNoShow(page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        sdk.dispose()
        release.countDown()
        assertNull(pending.await())
    }

    @Test fun reinitializeDiscardsAnInFlightChoose() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        blockedPath = "/choose"
        val pending = async(Dispatchers.IO) { sdk.trackViewNoShow(page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        initialize()
        release.countDown()
        assertNull(pending.await())
    }

    @Test fun sameUserSetDuringChooseDoesNotInvalidate() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        blockedPath = "/choose"
        sdk.setUser("same", "session")
        val pending = async(Dispatchers.IO) { sdk.trackViewNoShow(page) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        sdk.setUser("same", "session")
        release.countDown()
        assertNotNull(pending.await())
    }

    @Test fun nullPriorityIsZeroAndTiesKeepServerOrder() = runBlocking {
        campaignIds = """[{"campaignId":"101","priority":-1},{"campaignId":"202"},{"campaignId":"303","priority":0}]"""
        contents["303"] = content("303")
        assertNotNull(sdk.trackViewNoShow(page))
        assertEquals(listOf("202", "303"), chosenIds())
    }

    private fun assertOnlyReturnedContentSendsOnLoad(useEvent: Boolean) = runBlocking {
        campaigns("101" to 4, "202" to 3, "303" to 2, "404" to 1)
        contents["101"] = contentWithLoad("101")
        chooseStatus["101"] = 503
        contents["202"] = contentWithLoad("202", step = "1")
        contents["404"] = contentWithLoad("404")
        val result = if (useEvent) sdk.triggerEventNoShow(events, page) else sdk.trackViewNoShow(page)
        assertEquals("404", result!!.data.data.single().payload.single().campaignId)
        withTimeout(5000) { while (recorded.none { it.path == "/load-404" }) delay(10) }
        shadowOf(Looper.getMainLooper()).idle()
        // Give incorrectly launched duplicate or skipped engagement requests time to arrive.
        delay(200)
        assertEquals(listOf("101", "202", "303", "404"), chosenIds())
        assertEquals(listOf("/load-404"), recorded.filter { it.path.startsWith("/load-") }.map { it.path })
        assertEquals(listOf("content-404"), callbacks.filterIsInstance<ContentLoadEvent>().map { it.content.contentId })
        assertEquals(1, callbacks.size)
        assertTrue(recorded.none { it.path == "/impression" || it.path == "/visible" })
    }

    @Test fun visitReturnsContentWithOnLoadOnceWithoutImpressions() = assertOnlyReturnedContentSendsOnLoad(useEvent = false)

    @Test fun eventReturnsContentWithOnLoadOnceWithoutImpressions() = assertOnlyReturnedContentSendsOnLoad(useEvent = true)

    @Test fun disabledHeadlessFetchDoesNotChangeAutomaticTrackingOrChooseOptions() = runBlocking {
        campaigns("101" to 1)
        sdk.isFetchContentOnTrack = false
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        sdk.trackView(page, activity)
        withTimeout(5000) { while (chosenIds().size < 1) delay(10) }
        sdk.triggerEvent(events, page, activity)
        withTimeout(5000) { while (chosenIds().size < 2) delay(10) }
        assertEquals(listOf("/visit", "/choose", "/event", "/choose"), recorded.map { it.path })
        assertTrue(recorded.all { it.body.getJSONObject("options").getBoolean("isImplicitImpression") })
    }

    @Test fun eventAndChooseCarryTheSameCustomUser() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        sdk.setUser("custom-event-user", "custom-event-session")
        assertNotNull(sdk.triggerEventNoShow(events, page))
        assertEquals(listOf("/event", "/choose"), recorded.map { it.path })
        recorded.forEach {
            assertEquals("custom-event-user", it.body.getJSONObject("user").getString("custom"))
            assertEquals("custom-event-session", it.body.getJSONObject("user").getString("ses"))
        }
    }

    @Test fun anExhaustedChooseHttpErrorReturnsNull() = runBlocking {
        campaigns("101" to 1)
        chooseStatus["101"] = 503
        assertNull(sdk.triggerEventNoShow(events, page))
        assertEquals(listOf("101"), chosenIds())
    }

    @Test fun zeroStaleTimeoutStillSendsTrackingButNeverChooses() = runBlocking {
        campaigns("101" to 1)
        sdk.staleContentTimeoutMillis = 0
        assertNull(sdk.trackViewNoShow(page))
        assertNull(sdk.triggerEventNoShow(events, page))
        assertEquals(listOf("/visit", "/event"), recorded.map { it.path })
    }

    @Test fun unavailableServerReturnsNullForVisitAndEvent() = runBlocking {
        server.stop()
        assertNull(sdk.trackViewNoShow(page))
        assertNull(sdk.triggerEventNoShow(events, page))
        assertTrue(recorded.isEmpty())
    }

    @Test fun presentationReservationDoesNotBlockHeadlessFetching() = runBlocking {
        campaigns("101" to 1)
        contents["101"] = content("101")
        val request = sdk.presentationGate.beginRequest(page.location, true)
        assertNotNull(sdk.presentationGate.reserve("101", request))
        assertNotNull(sdk.trackViewNoShow(page))
    }

    private fun assertChooseCancellationPropagates(useEvent: Boolean) = runBlocking {
        campaigns("101" to 2, "202" to 1)
        contents["101"] = contentWithLoad("101")
        contents["202"] = contentWithLoad("202")
        blockedPath = "/choose"
        val propagated = CompletableDeferred<Boolean>()
        val pending = launch(Dispatchers.IO) {
            try {
                if (useEvent) sdk.triggerEventNoShow(events, page) else sdk.trackViewNoShow(page)
                propagated.complete(false)
            } catch (error: CancellationException) {
                propagated.complete(true)
                throw error
            }
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        pending.cancel()
        release.countDown()
        assertTrue("NoShow must throw cancellation instead of returning null", withTimeout(5000) { propagated.await() })
        pending.join()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("101"), chosenIds())
        assertTrue(callbacks.isEmpty())
        assertTrue(recorded.none { it.path.startsWith("/load-") })
    }

    @Test fun cancellationDuringVisitChoosePropagates() = assertChooseCancellationPropagates(useEvent = false)

    @Test fun cancellationDuringEventChoosePropagates() = assertChooseCancellationPropagates(useEvent = true)

    private fun assertActiveRequestPropagatesCancellation(useEvent: Boolean) = runBlocking {
        val expected = CancellationException("Request encoding cancelled")
        // A cancelled Job makes withContext throw on exit even if its body swallows
        // cancellation. Throw while encoding an input in an active Job to cover
        // the explicit CancellationException handler in fetchAfterTracking.
        val cancelledData = object : AbstractList<String>() {
            override val size = 1
            override fun get(index: Int): String = throw expected
        }
        val cancelledPage = page.copy(data = cancelledData)
        try {
            if (useEvent) sdk.triggerEventNoShow(events, cancelledPage) else sdk.trackViewNoShow(cancelledPage)
            fail("NoShow must propagate CancellationException even when the caller is active")
        } catch (actual: CancellationException) {
            // Coroutine stacktrace recovery may copy the exception across withContext.
            assertEquals("Request encoding cancelled", actual.message)
        }
        assertTrue(currentCoroutineContext().isActive)
        assertTrue(recorded.isEmpty())
        assertTrue(callbacks.isEmpty())
    }

    @Test fun activeVisitRequestPropagatesCancellationException() = assertActiveRequestPropagatesCancellation(useEvent = false)

    @Test fun activeEventRequestPropagatesCancellationException() = assertActiveRequestPropagatesCancellation(useEvent = true)

    @Test fun allDeliveryMethodsReturnWithoutUi() = runBlocking {
        campaigns("101" to 1)
        for (delivery in listOf("modal", "bottom_sheet", "full_screen", "snack_bar", "inline", "custom")) {
            contents["101"] = content("101", delivery)
            assertNotNull(delivery, sdk.trackViewNoShow(page))
        }
        assertTrue(recorded.all { it.path == "/visit" || it.path == "/choose" })
    }
}
