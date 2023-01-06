package com.segment.analytics.kotlin.destinations.optimizelyfull

import android.app.Application
import android.content.Context
import com.optimizely.ab.OptimizelyUserContext
import com.optimizely.ab.android.sdk.OptimizelyClient
import com.optimizely.ab.android.sdk.OptimizelyManager
import com.optimizely.ab.notification.NotificationCenter
import com.optimizely.ab.optimizelydecision.OptimizelyDecision
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.kotlin.core.utilities.getString
import io.mockk.*
import io.mockk.impl.annotations.MockK
import junit.framework.Assert.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OptimizelyFullDestinationTests {
    @MockK
    lateinit var mockApplication: Application

    @MockK
    lateinit var mockedContext: Context

    @MockK(relaxUnitFun = true)
    lateinit var mockedAnalytics: Analytics

    @MockK(relaxUnitFun = true)
    lateinit var mockedOptimizelyClient: OptimizelyClient

    @MockK(relaxUnitFun = true)
    lateinit var mockedOptimizelyManager: OptimizelyManager

    @MockK(relaxUnitFun = true)
    lateinit var mockedOptimizelyUserContext: OptimizelyUserContext

    @MockK(relaxUnitFun = true)
    lateinit var mockedOptimizelyDecision: OptimizelyDecision

    @MockK(relaxUnitFun = true)
    lateinit var mockedNotificationCenter: NotificationCenter

    private lateinit var optimizelyFullDestination: OptimizelyFullDestination
    private val sampleOptimizelyFullSettings: Settings = LenientJson.decodeFromString(
        """
            {
              "integrations": {
                "Optimizely X": {
                  "listen": false,
                  "nonInteraction": false,
                  "trackKnownUsers": false
                }
              }
            }
        """.trimIndent()
    )

    init {
        MockKAnnotations.init(this)
    }

    @Before
    fun setUp() {
        every { mockedOptimizelyManager.optimizely } answers { mockedOptimizelyClient }
        every { mockedOptimizelyManager.initialize(any(), null) } answers { mockedOptimizelyClient }
        every { mockedOptimizelyClient.isValid } answers { true }
        optimizelyFullDestination = OptimizelyFullDestination(mockedOptimizelyManager)
        every { mockedAnalytics.configuration.application } returns mockApplication
        every { mockApplication.applicationContext } returns mockedContext
        mockedAnalytics.configuration.application = mockedContext
        optimizelyFullDestination.analytics = mockedAnalytics
        every { mockedOptimizelyClient.notificationCenter } answers { mockedNotificationCenter }
    }

    @Test
    fun `settings are updated correctly`() {
        // An adjust example settings
        val optimizelyFullSettings: Settings = sampleOptimizelyFullSettings

        optimizelyFullDestination.update(optimizelyFullSettings, Plugin.UpdateType.Initial)

        /* assertions Adjust config */
        Assertions.assertNotNull(optimizelyFullDestination.optimizelyFullSettings)
        with(optimizelyFullDestination.optimizelyFullSettings!!) {
            assertEquals(optimizelyFullDestination.optimizelyFullSettings!!.listen, false)
            assertEquals(optimizelyFullDestination.optimizelyFullSettings!!.nonInteraction, false)
            assertEquals(optimizelyFullDestination.optimizelyFullSettings!!.trackKnownUsers, false)
        }
    }

    @Test
    fun `identify is handled correctly`() {
        val sampleIdentifyEvent = IdentifyEvent(
            userId = "optimizely-UserID-123",
            traits = buildJsonObject {
                put("userId", "UserID-123")
                put("gender", "F")
            }
        )

        val identifyEvent = optimizelyFullDestination.identify(sampleIdentifyEvent)
        Assertions.assertNotNull(identifyEvent)

        with(identifyEvent as IdentifyEvent) {
            Assertions.assertEquals("optimizely-UserID-123", userId)
            with(traits) {
                Assertions.assertEquals("UserID-123", getString("userId"))
                Assertions.assertEquals("F", getString("gender"))
            }
        }
    }

    @Test
    fun `track is handled correctly`() {
        val sampleTrackEvent = TrackEvent(
            event = "event_android",
            properties = buildJsonObject {
            }
        ).apply {
            anonymousId = "anonymous_UserID-123"
            userId = "user_UserID-123"
        }
        val trackEvent = optimizelyFullDestination.track(sampleTrackEvent)
        every {
            mockedOptimizelyClient.createUserContext(
                "anonymous_UserID-123",
                any()
            )
        } answers { mockedOptimizelyUserContext }

        Assertions.assertNotNull(trackEvent)
    }

    @Test
    fun `track known user is handled correctly`() {
        val sampleOptimizelyFullSettings: Settings = LenientJson.decodeFromString(
            """
            {
              "integrations": {
                "Optimizely X": {
                  "listen": false,
                  "nonInteraction": false,
                  "trackKnownUsers": true
                }
              }
            }
        """.trimIndent()
        )
        optimizelyFullDestination.update(sampleOptimizelyFullSettings, Plugin.UpdateType.Initial)
        val sampleTrackEvent = TrackEvent(
            event = "event_android",
            properties = buildJsonObject {
            }
        ).apply {
            userId = "user_UserID-123"
            anonymousId = "anonymous_UserID-123"
        }

        val sampleIdentifyEvent = IdentifyEvent(
            userId = "optimizely-UserID-123",
            traits = buildJsonObject {
                put("userId", "UserID-123")
                put("gender", "F")
            }
        )

        val identifyEvent = optimizelyFullDestination.identify(sampleIdentifyEvent)
        Assertions.assertNotNull(identifyEvent)

        every {
            mockedOptimizelyClient.createUserContext(
                "user_UserID-123",
                any()
            )
        } answers { mockedOptimizelyUserContext }

        every {
            mockedOptimizelyUserContext.decide(any()
            )
        } answers { mockedOptimizelyDecision }

        val trackEvent = optimizelyFullDestination.track(sampleTrackEvent)
        Assertions.assertNotNull(trackEvent)
    }


    @Test
    fun `track unknown user is handled correctly`() {
        val sampleTrackEvent = TrackEvent(
            event = "event_android",
            properties = buildJsonObject {
            }
        ).apply {
            userId = "user_UserID-123"
            anonymousId = "anonymous_UserID-123"
        }

        val sampleIdentifyEvent = IdentifyEvent(
            userId = "optimizely-UserID-123",
            traits = buildJsonObject {
                put("userId", "UserID-123")
                put("gender", "F")
            }
        )

        val identifyEvent = optimizelyFullDestination.identify(sampleIdentifyEvent)
        Assertions.assertNotNull(identifyEvent)

        every {
            mockedOptimizelyClient.createUserContext(
                "anonymous_UserID-123",
                any()
            )
        } answers { mockedOptimizelyUserContext }

        val trackEvent = optimizelyFullDestination.track(sampleTrackEvent)
        Assertions.assertNotNull(trackEvent)
    }

    @Test
    fun `reset is handled correctly`() {
        optimizelyFullDestination.update(sampleOptimizelyFullSettings, Plugin.UpdateType.Initial)
        optimizelyFullDestination.reset()
        verify { mockedNotificationCenter.clearAllNotificationListeners() }
    }
}