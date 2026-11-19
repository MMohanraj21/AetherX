package com.aetherx.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PrivacyShield {
    data class PrivacyState(
        val microphoneMuted: Boolean = false,
        val cameraShieldActive: Boolean = false,
        val screenCaptureBlocked: Boolean = false,
        val telemetryDisabled: Boolean = true,
        val localOnlyMode: Boolean = false
    )

    private val _state = MutableStateFlow(PrivacyState())
    val state: StateFlow<PrivacyState> = _state.asStateFlow()

    fun setMicrophoneMuted(muted: Boolean) {
        _state.value = _state.value.copy(microphoneMuted = muted)
    }

    fun setCameraShield(active: Boolean) {
        _state.value = _state.value.copy(cameraShieldActive = active)
    }

    fun setScreenCaptureBlocked(blocked: Boolean) {
        _state.value = _state.value.copy(screenCaptureBlocked = blocked)
    }

    fun setLocalOnlyMode(localOnly: Boolean) {
        _state.value = _state.value.copy(localOnlyMode = localOnly)
    }

    fun toggleMicrophone(): Boolean {
        val next = !_state.value.microphoneMuted
        setMicrophoneMuted(next)
        return next
    }
}
