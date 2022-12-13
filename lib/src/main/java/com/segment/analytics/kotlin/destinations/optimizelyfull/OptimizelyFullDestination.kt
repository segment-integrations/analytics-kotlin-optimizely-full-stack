package com.segment.analytics.kotlin.destinations.optimizelyfull

import android.content.Context
import com.optimizely.ab.OptimizelyUserContext
import com.optimizely.ab.android.sdk.OptimizelyClient
import com.optimizely.ab.android.sdk.OptimizelyManager
import com.optimizely.ab.notification.*
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.log
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OptimizelyFullDestination constructor(private val optimizelyManager: OptimizelyManager, private val experimentKey: String = "") :
    DestinationPlugin() {
    private var optimizelyClient: OptimizelyClient? = null
    internal var optimizelyFullSettings: OptimizelyFullSettings? = null
    private var optimizelyUser:OptimizelyUserContext?=null

    private var attributes: Map<String, String> = HashMap()

    private val OPTIMIZELY_FULL_KEY = "Optimizely X"
    private val experimentViewedEvent = "Experiment Viewed"

    override val key: String = OPTIMIZELY_FULL_KEY

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        this.optimizelyFullSettings = settings.destinationSettings(key, OptimizelyFullSettings.serializer())
        if (type == Plugin.UpdateType.Initial) {
            optimizelyClient = optimizelyManager.initialize(analytics.configuration.application as Context, null)
            if (optimizelyFullSettings?.listen == true) {
               addNotificationListeners()
            }
        }
    }

    override fun identify(payload: IdentifyEvent): BaseEvent? {
        attributes = payload.traits.asStringMap()
        return super.identify(payload)
    }
    override fun track(payload: TrackEvent): BaseEvent? {
        val userId: String = payload.userId

        if (optimizelyFullSettings?.trackKnownUsers == true && userId.isEmpty()) {
            analytics.log(
                "Segment will only track users associated with a userId "
                        + "when the trackKnownUsers setting is enabled.")
            return super.track(payload)
        }

        var id: String = payload.anonymousId
        val event: String = payload.event
        val properties: Map<String, String> = payload.properties.asStringMap()

        if (optimizelyFullSettings?.trackKnownUsers == true && !userId.isEmpty()) {
            id = payload.userId
        }
        optimizelyUser = optimizelyClient?.createUserContext(id, attributes)
        if(event != experimentViewedEvent) {
            optimizelyUser?.decide(experimentKey)
        }
        optimizelyUser?.trackEvent(event, properties)

        analytics.log("optimizelyUser?.trackEvent($event, $id, $attributes, $properties)")
        return super.track(payload)
    }

    override fun reset() {
        super.reset()
        optimizelyClient?.notificationCenter!!.clearAllNotificationListeners()
        analytics.log("optimizelyClient?.notificationCenter.clearAllNotificationListeners(listener)")
    }

    /**
     * Adding Notification Listeners to OptimizelyClient
     */
    private fun addNotificationListeners() {

        optimizelyClient?.addDecisionNotificationHandler { notification: DecisionNotification ->
            val properties = buildJsonObject {
                put("type", notification.type)
                put("userId", notification.userId)
                put("attributes", notification.attributes.toString())
                put("decisionInfo", notification.decisionInfo.toString())
                if (optimizelyFullSettings?.nonInteraction == true) {
                    put("nonInteraction", 1)
                }
            }
            analytics.track(
                experimentViewedEvent,
                properties
            )
        }

        optimizelyClient?.addTrackNotificationHandler {
        }
    }
}

/**
 * Optimizely Full Settings data class.
 */
@Serializable
data class OptimizelyFullSettings(
    // OptimizelyFull Segment value to Only Track Known Users
    var trackKnownUsers: Boolean = false,
    //    OptimizelyFull Segment value which Specifies the Experiment Viewed as a non-interaction event for Google Analytics
    var nonInteraction: Boolean = true,
    //    OptimizelyFull Segment value to listen Experiment activated notification
    var listen: Boolean = true
)

private fun JsonObject.asStringMap(): Map<String, String> = this.mapValues { (_, value) ->
    value.toContent().toString()
}