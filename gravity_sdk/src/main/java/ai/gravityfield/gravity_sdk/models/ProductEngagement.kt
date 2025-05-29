package ai.gravityfield.gravity_sdk.models

import ai.gravityfield.gravity_sdk.network.Campaign

sealed class ProductEngagement(val slot: Slot, val content: CampaignContent, val campaign: Campaign)

class ProductClickEngagement(
    slot: Slot,
    content: CampaignContent,
    campaign: Campaign
) : ProductEngagement(slot, content, campaign)

class ProductVisibleImpressionEngagement(
    slot: Slot,
    content: CampaignContent,
    campaign: Campaign
) : ProductEngagement(slot, content, campaign)
