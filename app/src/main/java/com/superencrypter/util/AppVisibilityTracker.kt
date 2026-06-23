package com.superencrypter.util

object AppVisibilityTracker {
    @Volatile
    private var startedActivities = 0

    val isInForeground: Boolean
        get() = startedActivities > 0

    fun activityStarted() {
        startedActivities += 1
    }

    fun activityStopped() {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }
}
