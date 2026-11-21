package com.jarvis.assistant.automation.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserAppLaunchTest {

    @Test
    fun testNativeAppCommandsAreNotHijackedAsWebUrls() {
        val testCases = mapOf(
            "open instagram" to "instagram",
            "open chatgpt" to "chatgpt",
            "open whatsapp" to "whatsapp",
            "open youtube" to "youtube",
            "open spotify" to "spotify",
            "open settings" to "settings",
            "open camera" to "camera",
            "open gallery" to "gallery",
            "open chrome" to "chrome",
            "launch instagram" to "instagram",
            "launch chatgpt" to "chatgpt"
        )

        for ((input, expectedApp) in testCases) {
            val result = CommandParser.parse(input)
            assertTrue("Expected OpenApp for '$input', but was: ${result.command}", result.command is JarvisCommand.OpenApp)
            val openAppCmd = result.command as JarvisCommand.OpenApp
            assertEquals("Expected target app '$expectedApp' for input '$input'", expectedApp, openAppCmd.packageName.lowercase())
        }
    }

    @Test
    fun testExplicitWebCommandsRouteToWebUrls() {
        val webCases = listOf(
            "open vercel.com",
            "open website vercel",
            "open 21st.dev",
            "browse github.com",
            "in chrome open vercel"
        )

        for (input in webCases) {
            val result = CommandParser.parse(input)
            assertTrue("Expected OpenWebUrls for '$input', but was: ${result.command}", result.command is JarvisCommand.OpenWebUrls)
        }
    }
}
