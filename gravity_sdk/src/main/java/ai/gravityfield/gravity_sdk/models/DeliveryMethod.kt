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
                "snackBar", "snackbar", "snack_bar" -> SNACK_BAR
                "modal" -> MODAL
                "bottomSheet", "bottom-sheet", "BOTTOM_SHEET", "bottom_sheet" -> BOTTOM_SHEET
                "fullscreen", "full_screen" -> FULL_SCREEN
                "inline" -> INLINE
                else -> UNKNOWN
            }
        }
    }
}
