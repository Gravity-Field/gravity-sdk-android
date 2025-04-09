package ai.gravityfield.gravity_sdk.models

enum class DeliveryMethod {
    SNACK_BAR,
    MODAL,
    BOTTOM_SHEET,
    FULL_SCREEN,
    INLINE,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): DeliveryMethod {
            return when (value) {
                "snackBar" -> SNACK_BAR
                "modal" -> MODAL
                "bottomSheet" -> BOTTOM_SHEET
                "fullScreen" -> FULL_SCREEN
                "inline" -> INLINE
                else -> UNKNOWN
            }
        }
    }
}
