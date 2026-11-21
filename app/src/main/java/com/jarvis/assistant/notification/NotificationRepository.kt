package com.jarvis.assistant.notification

import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe single source of truth for all active notifications captured by ZARA AI.
 */
object NotificationRepository {

    private const val TAG = "NotificationRepository"

    private val activeNotifications = ConcurrentHashMap<String, ZaraNotificationItem>()

    fun syncActiveNotifications(context: Context, sbns: Array<StatusBarNotification>?) {
        if (sbns == null) return
        Log.i(TAG, "Syncing active notifications: ${sbns.size} received from system")
        for (sbn in sbns) {
            addOrUpdate(context, sbn, announce = false)
        }
    }

    fun addOrUpdate(context: Context, sbn: StatusBarNotification, announce: Boolean = false): ZaraNotificationItem? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        val pkg = sbn.packageName.orEmpty()
        // Ignore self notifications
        if (pkg == context.packageName || pkg == "com.zara.assistant" || pkg == "com.jarvis.assistant") return null
        if (sbn.isOngoing) return null

        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        if (title.isBlank() && text.isBlank()) return null

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg
        }

        val isSensitive = !NotificationPrivacyFilter.isSafeToAnnounce(pkg, title, text)
        val replyAction = NotificationReplyManager.findReplyAction(sbn)

        val item = ZaraNotificationItem(
            key = sbn.key,
            packageName = pkg,
            appName = appName,
            sender = title.ifBlank { appName },
            title = title,
            text = text,
            timestamp = sbn.postTime,
            conversationId = extras.getString(android.app.Notification.EXTRA_CONVERSATION_TITLE),
            replyAction = replyAction,
            canReply = replyAction != null,
            isRead = false,
            isSuppressed = isSensitive,
            isSensitive = isSensitive
        )

        activeNotifications[sbn.key] = item
        Log.d(TAG, "Notification added to repository: [${item.appName}] ${item.sender}")
        return item
    }

    fun remove(key: String): ZaraNotificationItem? {
        return activeNotifications.remove(key)
    }

    fun getActiveList(): List<ZaraNotificationItem> {
        return activeNotifications.values
            .filter { !it.isSuppressed }
            .sortedByDescending { it.timestamp }
    }

    fun getUnreadList(): List<ZaraNotificationItem> {
        return getActiveList().filter { !it.isRead }
    }

    fun getLatest(): ZaraNotificationItem? {
        return getActiveList().firstOrNull()
    }

    fun getByPackage(pkgSubstring: String): List<ZaraNotificationItem> {
        val lower = pkgSubstring.lowercase()
        return getActiveList().filter {
            it.packageName.lowercase().contains(lower) || it.appName.lowercase().contains(lower)
        }
    }

    fun getBySender(senderQuery: String): List<ZaraNotificationItem> {
        val lower = senderQuery.lowercase()
        return getActiveList().filter {
            it.sender.lowercase().contains(lower) || it.title.lowercase().contains(lower)
        }
    }

    fun markRead(key: String) {
        activeNotifications[key]?.isRead = true
    }

    fun markAllRead() {
        for (item in activeNotifications.values) {
            item.isRead = true
        }
    }

    fun hasNotifications(): Boolean = activeNotifications.isNotEmpty()

    fun count(): Int = getActiveList().size
}
