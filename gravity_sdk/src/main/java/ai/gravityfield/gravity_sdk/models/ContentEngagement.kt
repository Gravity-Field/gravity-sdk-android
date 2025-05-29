package ai.gravityfield.gravity_sdk.models

import ai.gravityfield.gravity_sdk.network.Campaign

sealed class ContentEngagement(val content: CampaignContent, val campaign: Campaign)

class ContentImpressionEngagement(
    content: CampaignContent,
    campaign: Campaign
) : ContentEngagement(content, campaign)

class ContentVisibleImpressionEngagement(
    content: CampaignContent,
    campaign: Campaign
) : ContentEngagement(content, campaign)

class ContentCloseEngagement(
    content: CampaignContent,
    campaign: Campaign
) : ContentEngagement(content, campaign)
