# ZARA AI — COMPLETE OPTIONS, FEATURES, UI BUTTONS & FUTURE ROADMAP REPORT

**Project**: ZARA AI (AetherX)  
**Lead Architect**: Mohanraj (@Hexvora777) | Pudukkottai, Tamil Nadu, India 🇮🇳  
**Target Platform**: Android 16 (API 36) / Vivo OriginOS 6 (Vivo T5 Pro)  
**Current Milestone Version**: `v5.4.0`  
**Date**: November 23, 2026  

---

## 1. Executive Overview

ZARA AI is a next-generation personal AI assistant designed with full-duplex conversational voice intelligence (Gemini Live), native Android OS deep automation (Accessibility Service & Telecom), genuine punch-hole integration (Vivo Origin Island), and strict user-controlled security and voice locks.

This master report details:
1. **Current System Capabilities & Backend Toolset** (What Zara can do).
2. **Screen-by-Screen UI & Button Architecture** (Every interactive button and control).
3. **Recommended Future Enhancements & Feature Roadmap** (What to build next).

---

## 2. Current Options & Core Features (What Zara Can Do)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ZARA AI CORE CAPABILITIES                       │
├──────────────────────────┬─────────────────────────────────────────────┤
│ 1. Voice Intelligence    │ • Gemini Live Full-Duplex PCM Streaming     │
│                          │ • Unlimited Continuous Conversation         │
│                          │ • Multi-Lingual Natural Voice (Tamil/Eng)   │
│                          │ • Strict Voice Gender Lock (Aoede / Puck)   │
├──────────────────────────┼─────────────────────────────────────────────┤
│ 2. System Automation     │ • Autonomous Screen OCR & UI Navigation     │
│                          │ • Native App Launching (Zero Chrome bypass) │
│                          │ • WhatsApp Direct Messaging & In-Line Reply │
│                          │ • Telecom Phone Calling & Cellular Guard    │
├──────────────────────────┼─────────────────────────────────────────────┤
│ 3. Vivo Hardware Bridge  │ • Genuine Origin Island Punch-Hole Pill     │
│                          │ • Expanded Dynamic Activity Capsule         │
│                          │ • Android 16 Promoted Ongoing Integration   │
├──────────────────────────┼─────────────────────────────────────────────┤
│ 4. Personal Productivity │ • Long-Term Memory Vault & Recall           │
│                          │ • Notification Reader & Privacy Filtering   │
│                          │ • Daily Briefing Engine (Weather, Alarms)   │
│                          │ • Universal Media Controls (Spotify/YT)     │
└──────────────────────────┴─────────────────────────────────────────────┘
```

### Complete 32-Tool Backend Action Registry

| # | Action Tool Name | Category | What Zara Does When Commanded |
| :---: | :--- | :--- | :--- |
| **1** | `open_app` | Navigation | Opens any installed Android application directly by package name. |
| **2** | `open_website` | Web | Launches websites in Chrome with smart anti-reload protection. |
| **3** | `search_in_chrome` | Web Search | Executes web searches on Google Chrome. |
| **4** | `search_and_play_youtube` | Media | Searches YouTube and automatically plays the top video match. |
| **5** | `search_youtube` | Media | Searches YouTube and shows relevant video listings. |
| **6** | `youtube_accessibility_action`| Automation | Autonomously clicks: `skip_ad`, `like`, `subscribe`, `fullscreen`. |
| **7** | `media_playback_control` | Media | Controls music: Play, Pause, Next Track, Previous Track. |
| **8** | `send_whatsapp_message` | Communication | Sends WhatsApp messages protected by Single Confirmation Gate. |
| **9** | `whatsapp_call` | Communication | Initiates WhatsApp voice or video calls directly to contacts. |
| **10**| `call_contact` | Telecom | Places direct cellular calls via Android TelecomManager. |
| **11**| `set_volume` | System Hardware| Adjusts device media, ringtone, or alarm volume levels (0-100%). |
| **12**| `set_brightness` | System Hardware| Adjusts display brightness percentage. |
| **13**| `set_flashlight` | System Hardware| Turns device torch / flashlight ON or OFF. |
| **14**| `go_to_background` | System UI | Minimizes Zara into quiet background standby. |
| **15**| `click_ui_element` | Autonomous Agent| Clicks any button, icon, or coordinate on screen via Accessibility. |
| **16**| `type_text` | Autonomous Agent| Types text, credentials, or prompts into active input fields. |
| **17**| `scroll_screen` | Autonomous Agent| Scrolls up, down, left, or right on any open application. |
| **18**| `press_key` | System Nav | Triggers Back, Home, Recents, or Enter keyboard actions. |
| **19**| `inspect_screen` | Vision AI | Captures screen context using OCR and Accessibility tree inspection. |
| **20**| `task_completed` | Autonomous Agent| Marks multi-step autonomous task as completed with verification. |
| **21**| `manage_alarm` | Organizer | Sets, lists, and cancels clock alarms. |
| **22**| `manage_timer` | Organizer | Sets countdown timers with alert sound. |
| **23**| `manage_reminder` | Organizer | Schedules time-based reminders and notifications. |
| **24**| `manage_note` | Organizer | Creates, searches, and organizes personal quick notes. |
| **25**| `manage_calendar` | Organizer | Reads and creates Google Calendar events. |
| **26**| `manage_task` | Organizer | Manages personal To-Do list items. |
| **27**| `get_daily_briefing` | Assistant | Delivers morning/evening brief: weather, battery, notifications. |
| **28**| `manage_memory` | Intelligence | Saves, searches, lists, and deletes persistent user memories. |
| **29**| `manage_maps` | Navigation | Searches nearby places and starts turn-by-turn navigation on Maps. |
| **30**| `get_notifications` | Intelligence | Reads unread notifications from WhatsApp, SMS, and apps. |
| **31**| `reply_to_notification` | Communication | Replies directly to notification sender via Android RemoteInput. |
| **32**| `builtin_chrome_search` | Search | Quick fallback web lookup. |

---

## 3. Screen-by-Screen UI & Button Architecture

### Screen 1: Home Dashboard (`fragment_home.xml`)
The main command center of ZARA AI featuring modern glassmorphism styling and glowing aura effects.

```
┌────────────────────────────────────────────────────────┐
│  🟣 ZARA AI                       [ 💬 Chat ] [ ⚙️ Set ]│ ◄── Top Quick Jump Buttons
│                                                        │
│                    ╭───────────╮                       │
│                    │  🟣 🔵 🟣 │                       │
│                    │ 3D WEBGL  │ ◄── Center Glowing Orb
│                    │   AURA    │     (Tap: Talk | Hold: PTT)
│                    ╰───────────╯                       │
│              "Standby • Say Hey Zara"                  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 🌤️ 29°C  Pudukkottai • Clear Sky                 │  │ ◄── Live Weather Card
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  [ 📱 Screen Share ]  [ 🧠 Memories ]  [ 🌐 Lang ]      │ ◄── Quick Action Grid
│                                                        │
│  ════════════════════════════════════════════════════  │
│  [ 🏠 Home ]  [ 💬 Chat ]  [ 🎙️ ORB ]  [ ⚙️ Settings ] │ ◄── Floating Bottom Dock
└────────────────────────────────────────────────────────┘
```

* **Top Bar Buttons**:
  1. `btnTopChat`: Instant jump to full-screen Chat interface.
  2. `btnTopSettings`: Instant jump to Settings & Preferences.
* **Interactive Center Orb (`orbContainer` / `orbWebView`)**:
  * 3D WebGL / Canvas interactive particle visualizer with dynamic pulse.
  * **Single Tap**: Toggles active conversation on/off (`LISTENING` ↔ `STANDBY`).
  * **Long Press**: Push-to-Talk mode.
* **Live Weather Widget (`cardWeather`)**:
  * Displays user's city (e.g. Pudukkottai), current temperature, condition, and high/low details.
  * Tapping refreshes weather data.
* **Quick Action Grid Buttons**:
  1. `btnScreenShareQuick`: Instantly toggles Screen Share / Visual Assistant mode for real-time screen inspection.
  2. `btnSavedMemories`: Opens the Memory Vault screen showing everything Zara has learned about the user.
  3. `btnConversationLanguageQuick`: Quick dropdown to force a spoken language (Auto, Tamil, English, Hindi, Telugu, etc.).
* **Error Notification Action**:
  * `btnFixLiveError`: One-click button to resolve API key or connection issues.
  * `btnDismissLiveError`: Closes error banner.

---

### Screen 2: Bottom Navigation Dock (`activity_main.xml`)
Persistent, curved bottom dock providing one-touch access to the app's core sections:

1. `tabHome`: Returns to Home Dashboard with 3D Orb.
2. `tabChat`: Opens textual/multimodal Chat conversation.
3. `tabMic` (Center Elevated Action Button): Direct microphone toggle — pulses with purple glow during speech.
4. `tabSettings`: Opens full app preferences, security, and developer options.
5. `tabUpdates`: What's New changelog and release update notifications (with unread badge).

---

### Screen 3: Multimodal Chat Interface (`fragment_chat.xml`)
Full conversational chat room supporting text, markdown, code highlighting, and vision inspection:

* **Header Controls**:
  1. `btnChatMenu`: Opens Chat History Bottom Sheet (`sheet_chat_history.xml`) to switch or search past sessions.
  2. `btnChatNew`: Clears context and starts a fresh new conversation thread.
* **Interactive Filter Chips**:
  1. `chipPersona`: Swaps AI persona on the fly:
     * *Zara Pro* (Balanced, witty, genius assistant)
     * *Girlfriend / Companion Mode* ("Pari" — warm, sweet, caring)
     * *Boyfriend Mode* ("Kabir" — protective, affectionate, charismatic)
     * *TechGPT-X* (Razor-sharp technical architect mode)
     * *Professional / Focus* (Executive, concise English)
  2. `chipMode`: Toggles between Spoken Audio Output and Text-Only Silent Output.
* **Message Input Bar (`cardInputContainer`)**:
  1. `etMessageInput`: Multi-line text field for typing queries.
  2. `btnAttach`: Opens file/gallery picker to attach screenshots or images for Gemini Vision analysis.
  3. `btnSendMessage`: Submits prompt to Gemini.

---

### Screen 4: Settings & Customization Hub (`fragment_settings.xml`)
Comprehensive system management grouped into clean visual cards:

* **Profile & Identity**:
  1. `cardProfile`: Displays User's Name and active personality.
  2. `rowOwnerVoice`: Voice Biometric Enrollment (`activity_voice_enrollment.xml`) — trains Vosk speaker vectors so Zara responds **only to Mohanraj's voice**.
* **Audio & Personality**:
  1. `rowLanguage`: Spoken Language Mandate (Select from 13 Indian & International languages).
  2. `rowPersonaVoice`: Strict Voice Selection Lock:
     * **Female**: `Aoede` (Default, melodic, warm) / `Kore`
     * **Male**: `Puck` (Crisp, engaging) / `Charon` / `Fenrir`
  3. `switchSilentMode`: Silent Task Execution Toggle (Executes app launches and automations silently without speaking).
  4. `switchAlwaysReady`: Low-power background standby toggle ("Hey Zara" wake word).
* **Hardware & System**:
  1. `rowOriginIsland`: Vivo OriginOS 6 Origin Island configuration & test launcher.
  2. `rowDefaultAssistant`: One-tap intent to set Zara as the Android Default Assist App (replaces Google Assistant on swipe/power button).
  3. `rowOemBackgroundGuide`: Step-by-step Vivo/OriginOS battery whitelist wizard to prevent background killing.
  4. `rowPermissions`: Centralized Permission Hub (`activity_permission.xml`) managing Mic, Accessibility, Contacts, Phone, and Storage.
  5. `rowOverlayAvatars`: Floating Avatar selection (`activity_overlay_avatar.xml`).
  6. `rowAppTheme`: Theme selector (`bottom_sheet_theme_selection.xml`) — Dark, Cyberpunk Purple, Light, OLED Pure Black.
* **Developer & AI Backend**:
  1. `rowApiConnection`: BYOK Gemini API Key entry and Model Picker (`activity_api_connection.xml`).
  2. `rowVoiceDiagnostics`: Real-time VAD acoustic graph and latency diagnostics (`activity_voice_diagnostics.xml`).
  3. `rowMemory`: Persistent Memory Vault (`activity_memory.xml`).
  4. `rowAppIdentity`: Verified Creator Identity for Mohanraj (@Hexvora777).
  5. `btnSocialTelegram`, `btnSocialInstagram`, `btnSocialYoutube`: Direct developer social connection links.

---

### Screen 5: Vivo Origin Island Integration (Native Punch-Hole Pill)
Engineered natively for Vivo T5 Pro / OriginOS 6:

* **Compact Camera Pill**:
  * Centered around the physical punch-hole camera.
  * Real-time cyan/purple audio waveform animation.
  * Displays short critical state: `"Listening"`, `"Thinking"`, `"Speaking"`, `"Playing"`.
  * Shows device battery percentage (`74% ⚡`).
* **Expanded Island Card (Long Press / Tap)**:
  * `btnPause`: Pauses current voice turn or agent task.
  * `btnStop`: Closes session and returns to standby.
  * **Music Playback Bar**: Previous, Play/Pause, Next Track controls for active Spotify/YouTube streams.
  * **Open Assistant Button**: Seamlessly launches MainActivity to the foreground.

---

## 4. What Should Be Added Next? (Future Feature Roadmap)

To transform ZARA AI from a cutting-edge assistant into an undisputed, world-class personal AI OS, here are the **Top 10 High-Impact Features** to engineer next:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   TOP 10 FEATURE UPGRADE ROADMAP                       │
├────────────────────────────────────────────────────────────────────────┤
│ 1. 📷 Real-Time Camera Vision (Live Video to Gemini Live WebSocket)   │
│ 2. ⚡ On-Device Small Language Model (SLM) for 100% Offline Chat       │
│ 3. 🎙️ Custom Wake Word Personalization ("Hey Mohan", "Jarvis")         │
│ 4. 📞 AI Call Screener & Answering Machine (Truecaller AI style)       │
│ 5. 🏠 Smart Home / IoT Control (Home Assistant & Matter Integration)   │
│ 6. 🕒 Morning Routine & Scheduled Automations ("Wake me up at 7 AM")  │
│ 7. 🔊 WhatsApp Voice Message Transcriber & Summarizer                  │
│ 8. 🗣️ Native Tamil High-Fidelity Neural TTS Voice                      │
│ 9. 👆 Edge Swipe & Double-Tap Back Gesture Activation                 │
│ 10. ⌚ WearOS / Smartwatch Companion Node                              │
└────────────────────────────────────────────────────────────────────────┘
```

### Detailed Breakdown of Recommended Enhancements:

#### 1. Real-Time Camera Vision (Live Video Feed to Gemini Live)
* **What it is**: Currently, Zara inspects what is on your screen (`inspect_screen`). Adding a live camera mode allows you to point your phone at physical objects, hardware circuits, documents, or road signs and talk about them in real-time.
* **Button to add**: `btnLiveCamera` on the Home screen and in Chat.
* **User Experience**: *"Zara, look at this Tamil book page and translate it to English"*, or *"Zara, look at my bike engine and tell me where the oil dipstick is"*.

#### 2. On-Device Offline SLM Fallback (Gemma 2B / Qwen 2.5 1.5B)
* **What it is**: When mobile data or Wi-Fi is unavailable, Zara currently matches basic device intents (torch, volume). By embedding a lightweight quantized Small Language Model (SLM) via Google MediaPipe or ONNX, Zara will hold full intelligent conversations even in flight mode or remote areas with zero internet!

#### 3. Custom Wake Word Personalization
* **What it is**: Currently Zara listens for "Hey Zara" via Vosk. Allow the user in Settings to train their own custom wake word, e.g., *"Hey Mohan"*, *"Jarvis"*, or *"Vannakam Zara"*.
* **Button to add**: `rowCustomWakeWord` in Settings under Voice Enrollment.

#### 4. AI Call Screener & Automated Answering Machine
* **What it is**: When an unknown or spam phone call rings, Zara answers in the background:
  *"Hello, Mohanraj is currently busy. Please state your name and why you are calling."*
  Zara transcribes the caller's response in real-time, displays it on the Vivo Origin Island, and lets you decide whether to take the call or let Zara decline it!

#### 5. Smart Home & IoT Appliance Hub
* **What it is**: Connect Zara to Home Assistant, Tuya, or Matter devices via local Wi-Fi or Webhooks.
* **Commands**: *"Zara, turn off the bedroom AC"*, *"Zara, dim lights to 30%"*, *"Zara, turn on the living room fan"*.
* **UI to add**: A dedicated `tabSmartHome` or quick toggle tiles on the Home Dashboard.

#### 6. Scheduled Autonomous Routines ("Good Morning Routine")
* **What it is**: User-defined automation sequences triggered by alarms or time of day.
* **Example**: At 7:00 AM every morning:
  1. Gently rings alarm with favorite song (`Tadipaar`).
  2. Greets Mohanraj in Tamil: *"காலை வணக்கம் மோகன்ராஜ் சார்!"*.
  3. Reads Pudukkottai weather, calendar events, and top 3 unread WhatsApp messages.
* **Button to add**: `btnRoutines` on the Home Dashboard.

#### 7. WhatsApp Voice Note Transcriber & Summarizer
* **What it is**: When friends or family send a 3-minute voice message on WhatsApp, Zara detects it, runs offline speech-to-text, and posts a 1-sentence Tamil/English summary directly to the Origin Island!

#### 8. Native High-Fidelity Tamil Neural TTS
* **What it is**: While Google TTS supports Tamil, integrating a dedicated Indian AI voice engine (like Bhashini / IndicTTS) will give Zara an authentic Tamil regional intonation and crystal-clear pronunciation.

#### 9. Edge-Swipe & Back-Tap Quick Triggers
* **What it is**: A subtle floating edge trigger (like Samsung Edge Panels) or double-tapping the back of the phone to trigger Zara's listening mode instantly over games (PUBG, BGMI), YouTube, or Netflix without opening the app.

#### 10. WearOS Smartwatch Companion
* **What it is**: A micro-companion app on your smartwatch to trigger Zara, check battery, and give voice commands directly from your wrist.

---

## 5. Summary Recommendation for Mohanraj

| Priority Tier | Feature | Implementation Effort | Impact on User Experience |
| :---: | :--- | :---: | :---: |
| **P0 (Immediate)** | 📷 Live Rear Camera Vision Feed | Medium (CameraX + Live WebSocket) | 🔥 **Massive** (True Multi-modal AI) |
| **P0 (Immediate)** | 🕒 Morning Daily Briefing Routine | Low (Existing DailyBriefingEngine) | 🚀 **Very High** (Daily habit builder) |
| **P1 (Next Sprint)** | 📞 AI Call Screener & Origin Island Answering | Medium (Telecom + In-Call Service) | 🌟 **Game Changer** |
| **P1 (Next Sprint)** | ⚡ On-Device Offline Gemma 2B Fallback | High (MediaPipe GenAI SDK) | 🛡️ **Zero Downtime** |
| **P2 (Future)** | 🏠 Smart Home / IoT Integration | Medium (HTTP/MQTT Webhooks) | 💡 **Smart Ecosystem** |

---

*Report authored for Mohanraj (@Hexvora777) — Founder & Lead Architect of ZARA AI.*  
*"From Tamil soil to a world of possibilities. My journey, my vision, my creation."*
