package ai.gravityfield.gravity_sdk.models

import ai.gravityfield.gravity_sdk.network.Campaign

sealed class TrackingEvent(val campaign: Campaign)

class ContentLoadEvent(
    val content: CampaignContent, campaign: Campaign
) : TrackingEvent(campaign)

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
