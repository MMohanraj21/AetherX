# AETHER X — JARVIS APK STATIC ANALYSIS & REVERSE ENGINEERING REPORT

**Classification**: Technical Architectural Audit & Reconstruction Specification  
**Target Subject**: `JARVIS-Part-7.apk` (v1.8.7, Build 143)  
**Original Reference Repository**: `https://github.com/techgptx/Playlist` (Parts 1–7)  
**Target Platform**: Android 8.0+ (API 26) through Android 14+ (API 34)  
**Target Architecture**: AETHER X (Modular Hybrid AI Assistant)  
**Report Date**: November 2026  

---

## 1. Executive Summary

This report presents a thorough static analysis and security architecture audit of the reference application `JARVIS-Part-7.apk` and its associated open-source repository `techgptx/Playlist`. The reference application represents a full-featured voice assistant integrating Google's experimental Gemini Live bidirectional streaming WebSocket protocol, offline wake-word recognition via Vosk/Kaldi, and Android Accessibility Service automation for social applications.

While functionally innovative, our decompilation and static audit identified critical architectural vulnerabilities, aggressive anti-clone enforcement, and security risks in automated action execution. This audit forms the foundation for **AETHER X**—an independently engineered, enterprise-grade, cyberpunk-themed Android AI assistant operating on a clean modular architecture, Keystore-backed encryption, human-in-the-loop safety gating, and a FastAPI/LangGraph multi-agent backend.

---

## 2. APK Metadata & Manifest Analysis

### 2.1 Binary Metadata
| Metric | Value |
| :--- | :--- |
| **Package Name / Application ID** | `com.jarvis.assistant` |
| **Version Code** | `143` |
| **Version Name** | `1.8.7` |
| **Minimum SDK** | `API 26` (Android 8.0 Oreo) |
| **Target SDK** | `API 34` (Android 14 UpsideDownCake) |
| **Compile SDK** | `API 34` |
| **APK Binary Size** | ~94.4 MB (99,002,975 bytes) |
| **Primary Native Binaries** | `libvosk.so`, `libjnidispatch.so` (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) |
| **Primary Assets Footprint** | `assets/model-en-us` (~80 MB Kaldi acoustic and language model), `assets/hud/`, `assets/orb.html` |

### 2.2 Declared Permissions
The APK requests 24 Android permissions, reflecting deep system integration:
```xml
<!-- Audio & Speech Processing -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<!-- UI Overlays & System Windows -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- System Automation & Accessibility -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Telephony & Communication Interop -->
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />

<!-- Vision & Camera -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Network & Diagnostics -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

### 2.3 Component Inventory
1. **Application Class**:
   - `com.jarvis.assistant.JarvisApp`: Initializes Vosk models on startup, configures notification channels, and checks identity pinning.
2. **Foreground & Background Services**:
   - `com.jarvis.assistant.background.JarvisConversationService`: Core foreground service managing audio sessions, microphone feeds, WebSocket streams, and persistent notification channel (`channel_conversation_service`).
   - `com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService`: Extends `AccessibilityService`. Intercepts window events, searches node hierarchies by text/view-id, and performs automated clicks for WhatsApp and YouTube.
3. **Core Activities**:
   - `ui.home.MainActivity`: Root UI host with curved dock line and WebView HUD.
   - `ui.settings.SettingsActivity`: Preference categories (Audio, Persona, API, Privacy).
   - `ui.settings.ApiConnectionActivity`: Live validation of Gemini API keys.
   - `ui.settings.PersonaActivity`: Personality prompt configuration.
   - `ui.settings.PrivacyShieldActivity`: Local mute and privacy controls.
   - `ui.settings.OverlayAvatarActivity`: Configuration of floating avatar styles.
   - `ui.memory.MemoryActivity`: Visual inspection of Room DB episodic memories.
   - `ui.voice.VoiceEnrollmentActivity`: Enrolls user voice profile for speaker recognition.
   - `ui.permission.PermissionActivity`: Multi-step permission acquisition flow.

---

## 3. Gemini Live Bidirectional WebSocket Protocol

### 3.1 Network Architecture
The reference app utilizes OkHttp3 (`OkHttpClient`) to maintain a persistent full-duplex WebSocket connection to Google's Gemini Live endpoint:
- **Base URI**: `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key={API_KEY}`
- **Heartbeat & Resiliency**: Ping interval 60s, connect timeout 5s, zero read timeout (streaming).

### 3.2 Handshake & Session Setup
Upon `onOpen`, the client immediately transmits a `GeminiBidiSetup` payload:
```json
{
  "setup": {
    "model": "models/gemini-3.1-flash-live-preview",
    "generationConfig": {
      "responseModalities": ["AUDIO"],
      "speechConfig": {
        "voiceConfig": {
          "prebuiltVoiceConfig": {
            "voiceName": "Puck"
          }
        }
      }
    },
    "inputAudioTranscription": {},
    "outputAudioTranscription": {},
    "systemInstruction": {
      "parts": [
        {
          "text": "You are JARVIS, an advanced AI assistant..."
        }
      ]
    }
  }
}
```

### 3.3 Audio Pipeline Specifications
1. **Uplink (Microphone to Gemini)**:
   - Sample rate: `16,000 Hz` (16 kHz)
   - Encoding: `PCM 16-bit Linear`, Mono
   - Chunk format: Base64-encoded binary wrapped in `realtimeInput.audio`:
     ```json
     {
       "realtimeInput": {
         "audio": {
           "mimeType": "audio/pcm;rate=16000",
           "data": "<BASE64_PCM_CHUNK>"
         }
       }
     }
     ```
   - End-of-Turn Signaling: `audioStreamEnd: true` sent when local VAD detects silence.

2. **Downlink (Gemini to Speaker)**:
   - Sample rate: `24,000 Hz` (24 kHz)
   - Encoding: `PCM 16-bit Linear`, Mono
   - Payload: Received via `serverContent.modelTurn.parts[].inlineData`:
     ```json
     {
       "serverContent": {
         "modelTurn": {
           "parts": [
             {
               "inlineData": {
                 "mimeType": "audio/pcm;rate=24000",
                 "data": "<BASE64_AUDIO>"
               }
             }
           ]
         },
         "turnComplete": false,
         "interrupted": false
       }
     }
     ```
   - Playback Engine: `AudioTrack` instantiated with `AudioAttributes.USAGE_MEDIA` and `AudioFormat.ENCODING_PCM_16BIT`.

3. **Session Lifetime**:
   - Google limits Gemini Live WebSocket sessions to approximately 10 minutes.
   - The reference implementation implements a 9-minute renewal timer (`SESSION_RENEWAL_MS = 540,000 ms`), seamlessly tearing down and establishing a new WebSocket socket before truncation.

---

## 4. Automation & Accessibility Engine

### 4.1 Implementation Mechanism
`JarvisAccessibilityService` registers for `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED` across all packages. When invoked via internal singletons (`JarvisAccessibilityService.triggerWhatsAppSend()`), it executes an state-machine polling loop:
1. Opens target application intent (e.g., `whatsapp://send?phone=...`).
2. Scans root `AccessibilityNodeInfo` hierarchy recursively for editable text inputs (`android.widget.EditText`).
3. Injects text via `ACTION_SET_TEXT` or clipboard paste.
4. Searches for buttons matching content descriptions (`"Send"`, `"Call"`, `"Subscribe"`).
5. Dispatches `ACTION_CLICK`.

### 4.2 Security Weaknesses & Vulnerabilities Identified
1. **Zero-Confirmation Automation**:
   - *Vulnerability*: The accessibility service dispatches external phone calls, SMS/WhatsApp messages, and web subscriptions autonomously without showing an unbypassable confirmation dialog to the user.
   - *Attack Vector*: A malicious prompt injection or audio replay attack through the assistant could send unapproved messages or make rogue calls.
   - *AETHER X Remediation*: Introduces `ActionGatekeeper` and `SafetyConfirmationDialog`. Any sensitive action (telephony, messaging, deletion, permission elevation) requires biometric or physical on-screen approval.

2. **Aggressive Anti-Tamper Crash Mechanisms**:
   - *Analysis*: In `SecurityManager.kt`, the reference app enforces:
     ```kotlin
     if (context.packageName != "com.jarvis.assistant") {
         showTamperAlert(...)
         exitProcess(1)
     }
     ```
   - *Issue*: This prevents standard white-labeling, testing, and modular separation.
   - *AETHER X Remediation*: Replaces rigid package name enforcement with cryptographic integrity verification (`AetherSecurityManager`), validating certificate fingerprints and runtime integrity without artificial crash locks.

3. **Plaintext Credential Storage**:
   - *Vulnerability*: API keys and persona configuration are stored in standard `SharedPreferences` XML files (`/data/data/com.jarvis.assistant/shared_prefs/`).
   - *AETHER X Remediation*: Integrates Android Keystore AES-256-GCM via `EncryptedSharedPreferences`, with proxying support through the local AETHER X FastAPI backend.

---

## 5. UI/UX Architecture & HUD Overlay

- **Orb Visualization**: Rendered via an Android `WebView` executing `assets/hud/index.html` and `assets/orb.html` utilizing Three.js (`three.min.js`). Audio energy levels are polled from `AudioRecord` / `AudioTrack` and transmitted into the webview via `evaluateJavascript("updateIntensity($energy);", null)`.
- **Dock & Interaction**: Native Android View `CurvedDockLineView` draws dynamic Bézier curves anchored to screen coordinates, providing a holographic Iron Man HUD aesthetic.

---

## 6. Legal & Attribution Compliance

The reference project is published under the **Tech Automation & Media Copyright License v1.0 (TAMCL v1.0)** authored by **Tech GPTx (Rehaan)**.
- **Requirement**: Content creators and derivative projects MUST prominently credit Tech GPTx and link to `https://github.com/techgptx/Playlist`.
- **AETHER X Stance**: AETHER X is an independent clean-room re-architecture featuring an entirely distinct codebase, Jetpack Compose UI, FastAPI/LangGraph backend, and novel security architecture. All original research and tutorial contributions by Tech GPTx are formally recognized in `docs/THIRD_PARTY_NOTICES.md` and the app's About / Legal UI.

---

## 7. AETHER X Architecture Specification

AETHER X modernizes the reference concept into a production-grade 8-module architecture:

```
┌────────────────────────────────────────────────────────┐
│                      AETHER X                          │
├───────────────┬────────────────────────┬───────────────┤
│  :app         │ Cyberpunk Compose UI   │ HUD Overlays  │
├───────────────┼────────────────────────┼───────────────┤
│  :ai-engine   │ Gemini Live WebSocket  │ LangGraph API │
├───────────────┼────────────────────────┼───────────────┤
│  :voice-engine│ PCM 16k in / 24k out   │ Vosk Offline  │
├───────────────┼────────────────────────┼───────────────┤
│  :vision-eng  │ CameraX / Grounding    │ Screen OCR    │
├───────────────┼────────────────────────┼───────────────┤
│  :automation  │ ActionGatekeeper       │ Accessibility │
├───────────────┼────────────────────────┼───────────────┤
│  :memory-eng  │ Encrypted Room DB      │ Semantic Sync │
├───────────────┼────────────────────────┼───────────────┤
│  :security    │ Keystore AES-GCM       │ AppVerifier   │
├───────────────┼────────────────────────┼───────────────┤
│  :networking  │ OkHttp3 / SSE Client   │ Token Manager │
└───────────────┴────────────────────────┴───────────────┘
                                ▲
                                │ Local Loopback (8899)
                                ▼
┌────────────────────────────────────────────────────────┐
│               AETHER X Python Backend                  │
│       FastAPI + LangGraph + google-genai SDK           │
└────────────────────────────────────────────────────────┘
```
