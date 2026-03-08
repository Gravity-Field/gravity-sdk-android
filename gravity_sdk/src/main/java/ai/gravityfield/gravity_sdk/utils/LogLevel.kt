package ai.gravityfield.gravity_sdk.utils

enum class LogLevel(val priority: Int) {
    DEBUG(1),
    INFO(2),
    ERROR(3),
    NONE(Int.MAX_VALUE)
}
