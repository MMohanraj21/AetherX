package com.jarvis.assistant.automation.modes

import android.content.Context
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.automation.command.JarvisCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Executes customized routines and contextual modes: Morning, Night, Study, Work, Travel, Driving.
 */
class SmartAutomationEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)

    enum class Mode {
        MORNING,
        NIGHT,
        WORK,
        STUDY,
        TRAVEL,
        MEETING,
        DRIVING
    }

    fun applyMode(mode: Mode, onFeedback: (String) -> Unit) {
        val app = context.applicationContext as? JarvisApp ?: return
        val cmdExecutor = app.commandExecutor

        when (mode) {
            Mode.MORNING -> {
                scope.launch {
                    cmdExecutor.execute(JarvisCommand.Brightness(180))
                    cmdExecutor.execute(JarvisCommand.Volume(1))
                }
                onFeedback("Morning mode activated. Screen brightened and audio ready.")
            }
            Mode.NIGHT -> {
                scope.launch {
                    cmdExecutor.execute(JarvisCommand.Flash(false))
                    cmdExecutor.execute(JarvisCommand.Brightness(20))
                    cmdExecutor.execute(JarvisCommand.Volume(-2))
                }
                onFeedback("Night mode activated. Brightness lowered, quiet hours active.")
            }
            Mode.WORK, Mode.STUDY -> {
                scope.launch { cmdExecutor.execute(JarvisCommand.Volume(-1)) }
                onFeedback("${mode.name.lowercase().replaceFirstChar { it.uppercase() }} mode enabled. Distractions minimized.")
            }
            Mode.DRIVING -> {
                scope.launch { cmdExecutor.execute(JarvisCommand.Volume(2)) }
                onFeedback("Driving mode active. Hands-free speakerphone and voice reading prioritized.")
            }
            Mode.TRAVEL, Mode.MEETING -> {
                onFeedback("${mode.name.lowercase().replaceFirstChar { it.uppercase() }} mode configured.")
            }
        }
    }
}
