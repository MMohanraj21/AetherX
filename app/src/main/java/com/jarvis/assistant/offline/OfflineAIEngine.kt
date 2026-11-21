package com.jarvis.assistant.offline

import android.content.Context
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.automation.command.JarvisCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local AI engine executing classified offline actions without internet access.
 */
class OfflineAIEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)

    fun executeLocally(intent: OfflineIntentClassifier.OfflineIntentResult, onFeedback: (String) -> Unit): Boolean {
        val app = context.applicationContext as? JarvisApp ?: return false
        val phoneControl = app.commandExecutor

        return when (intent.action) {
            OfflineCapabilityRegistry.OfflineAction.FLASHLIGHT_ON -> {
                scope.launch { phoneControl.execute(JarvisCommand.Flash(true)) }
                onFeedback("Flashlight turned on.")
                true
            }
            OfflineCapabilityRegistry.OfflineAction.FLASHLIGHT_OFF -> {
                scope.launch { phoneControl.execute(JarvisCommand.Flash(false)) }
                onFeedback("Flashlight turned off.")
                true
            }
            OfflineCapabilityRegistry.OfflineAction.VOLUME_UP -> {
                scope.launch { phoneControl.execute(JarvisCommand.Volume(1)) }
                onFeedback("Volume increased.")
                true
            }
            OfflineCapabilityRegistry.OfflineAction.VOLUME_DOWN -> {
                scope.launch { phoneControl.execute(JarvisCommand.Volume(-1)) }
                onFeedback("Volume decreased.")
                true
            }
            OfflineCapabilityRegistry.OfflineAction.TELL_TIME -> {
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                onFeedback("The time is $timeStr.")
                true
            }
            OfflineCapabilityRegistry.OfflineAction.OPEN_APP -> {
                scope.launch { phoneControl.execute(JarvisCommand.OpenApp(intent.target)) }
                onFeedback("Opening ${intent.target}.")
                true
            }
            else -> false
        }
    }
}
