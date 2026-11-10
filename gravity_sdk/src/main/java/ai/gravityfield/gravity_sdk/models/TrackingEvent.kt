package ai.gravityfield.gravity_sdk.models

import ai.gravityfield.gravity_sdk.network.Campaign

sealed class TrackingEvent(val campaign: Campaign)

/**
 * [delayTime] carries the campaign delay in milliseconds only for content returned by
 * trackViewNoShow or triggerEventNoShow, matching the accepted response.
 * It is 0 for getContent requests and automatic presentation, even when the SDK
 * itself waits for a campaign delay before showing it.
 */
class ContentLoadEvent @JvmOverloads constructor(
    val content: CampaignContent, campaign: Campaign, delayTime: Int = 0,
) : TrackingEvent(campaign) {
    /** Non-negative campaign delay; negative constructor arguments are normalized to 0. */
    val delayTime: Int = delayTime.coerceAtLeast(0)
}

class ContentImpressionEvent(
    val content: CampaignContent, campaign: Campaign
) : TrackingEvent(campaign)

class ContentVisibleImpressionEvent(
    val content: CampaignContent, campaign: Campaign
) : TrackingEvent(campaign)

class ContentCloseEvent(
    val content: CampaignContent, campaign: Campaign
) : TrackingEvent(campaign)

class CopyEvent(
    val copiedValue: String, val content: CampaignContent, campaign: Campaign
) : TrackingEvent(campaign)

class CancelEvent(
    val content: CampaignContent, campaign: Campaign
) : TrackingEvent(campaign)

class FollowUrlEvent(
    val url: String,
    val type: FollowUrlType,
    val content: CampaignContent,
    campaign: Campaign,
) : TrackingEvent(campaign)

class FollowDeeplinkEvent(
    val deeplink: String, val content: CampaignContent, campaign: Campaign
) : TrackingEvent(campaign)

class RequestPushEvent(
    val content: CampaignContent, campaign: Campaign
) : TrackingEvent(campaign)

class ProductImpressionEvent(
    val slot: Slot, val content: CampaignContent, campaign: Campaign
) : TrackingEvent(campaign)

class ProductClickEvent(
    val slot: Slot, val content: CampaignContent, campaign: Campaign
) : TrackingEvent(campaign)
