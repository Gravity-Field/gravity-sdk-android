package ai.gravityfield.gravity_sdk.models.external

import ai.gravityfield.gravity_sdk.models.CampaignContent

sealed class ContentEngagement

class ContentImpressionEngagement(val content: CampaignContent) : ContentEngagement()

class ContentVisibleImpressionEngagement(val content: CampaignContent) : ContentEngagement()

class ContentCloseEngagement(val content: CampaignContent) : ContentEngagement()
