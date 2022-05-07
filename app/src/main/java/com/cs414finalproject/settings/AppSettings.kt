package com.cs414finalproject.settings

data class AppSettings(
    var driveSpeedScale: Float = 0.50f,
    var turnSpeedScale: Float = 0.115f,
    var parentalOverride: Boolean = false
) {
    fun toggleParentalOverride() {
        parentalOverride = !parentalOverride
    }
}