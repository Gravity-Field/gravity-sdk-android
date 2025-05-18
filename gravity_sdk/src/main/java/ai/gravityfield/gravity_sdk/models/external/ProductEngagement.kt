package ai.gravityfield.gravity_sdk.models.external

import ai.gravityfield.gravity_sdk.models.Slot

sealed class ProductEngagement

class ProductClickEngagement(val slot: Slot) : ProductEngagement()
