package com.jarvis.assistant.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Manages replying to notifications using official Android RemoteInput APIs.
 * Requires user confirmation before execution.
 */
object NotificationReplyManager {

    private const val TAG = "NotificationReplyMgr"

    data class ReplyableAction(
        val remoteInput: RemoteInput,
        val actionIntent: PendingIntent,
        val resultKey: String
    )

    /**
     * Finds a suitable RemoteInput action in the given notification.
     */
    fun findReplyAction(sbn: StatusBarNotification): ReplyableAction? {
        val notification = sbn.notification ?: return null
        val actions = notification.actions ?: return null

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (ri in remoteInputs) {
                if (ri.allowFreeFormInput) {
                    return ReplyableAction(
                        remoteInput = ri,
                        actionIntent = action.actionIntent,
                        resultKey = ri.resultKey
                    )
                }
            }
        }
        return null
    }

    /**
     * Executes the reply by attaching the confirmed text to RemoteInput and firing the pending intent.
     */
    fun sendReply(context: Context, replyAction: ReplyableAction, replyMessage: String): Boolean {
        return try {
            val intent = Intent()
            val bundle = Bundle().apply {
                putCharSequence(replyAction.resultKey, replyMessage)
            }
            RemoteInput.addResultsToIntent(arrayOf(replyAction.remoteInput), intent, bundle)
            replyAction.actionIntent.send(context, 0, intent)
            Log.i(TAG, "Notification reply successfully dispatched via RemoteInput")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch notification reply: ${e.message}", e)
            false
        }
    }
}
