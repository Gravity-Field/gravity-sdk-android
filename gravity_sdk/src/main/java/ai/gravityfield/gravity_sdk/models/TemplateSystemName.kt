package ai.gravityfield.gravity_sdk.models

enum class TemplateSystemName {
    SNACKBAR_1,
    SNACKBAR_2,
    UNKNOWN;

    companion object {
        fun fromString(value: String): TemplateSystemName {
            return when (value) {
                "snackbar-1" -> SNACKBAR_1
                "snackbar-2" -> SNACKBAR_2
                else -> UNKNOWN
            }
        }
    }
}
