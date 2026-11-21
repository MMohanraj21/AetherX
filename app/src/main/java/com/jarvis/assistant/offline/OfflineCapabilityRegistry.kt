package com.jarvis.assistant.offline

/**
 * Registry of system capabilities that can execute 100% locally on-device without cloud connectivity.
 */
object OfflineCapabilityRegistry {

    enum class OfflineAction {
        FLASHLIGHT_ON,
        FLASHLIGHT_OFF,
        VOLUME_UP,
        VOLUME_DOWN,
        OPEN_APP,
        CREATE_NOTE,
        SET_TIMER,
        SET_ALARM,
        CHECK_BATTERY,
        TELL_TIME,
        QUERY_MEMORY
    }

    val SUPPORTED_OFFLINE_APPS = setOf(
        "whatsapp", "instagram", "youtube", "chrome", "settings", "camera", "gallery", "clock", "calculator", "maps"
    )
}
