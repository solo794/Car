package com.dfshine.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Powers two features at once:
 *  - Notification badges (a small dot/count on app icons in the home grid
 *    and dock).
 *  - Access to the system's active [android.media.session.MediaSessionManager]
 *    sessions, which the floating mini player in [FloatingPipService] needs
 *    (Android requires notification-listener access to read media sessions
 *    system-wide).
 *
 * Badge counts are exposed as a simple in-process [StateFlow] keyed by
 * package name so Compose screens can collect it directly - no extra IPC
 * needed since it's the same process.
 */
class NotificationBadgeService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        rebuildCounts(activeNotifications)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        rebuildCounts(activeNotifications)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        rebuildCounts(activeNotifications)
    }

    private fun rebuildCounts(notifications: Array<StatusBarNotification>?) {
        val counts = notifications
            ?.filter { !it.isOngoing }
            ?.groupingBy { it.packageName }
            ?.eachCount()
            ?: emptyMap()
        badgeCounts.update { counts }
    }

    companion object {
        private val badgeCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val badgeCountsFlow: StateFlow<Map<String, Int>> = badgeCounts
    }
}
