# ZARA AI v5.3.0 — Comprehensive Bug Report & Defect Resolution Audit

**Project:** ZARA AI Assistant  
**Target:** Vivo T5 Pro / OriginOS 6 / Android 16 (API 36)  
**Version:** 5.3.0 (Build 530)  
**Report Date:** November 22, 2026  
**Lead Architect:** Mohanraj ([@Hexvora777](https://t.me/Hexvora777))  
**Architect Motto:** *"Every identity has an origin. Mine is Mohanraj."*  

---

## 1. Defect Overview & Resolution Summary

During development and field validation of ZARA AI (v4.0 to v5.3.0), eight critical bugs were identified across voice output concurrency, confirmation loops, background activity launching, and system state lifecycles.

All 8 primary defects have been **COMPLETELY RESOLVED** in the v5.3.0 release codebase.

| Bug ID | Severity | Defect Description | Root Cause | Status | Resolution File |
| :---: | :---: | :--- | :--- | :---: | :--- |
| **BUG-01** | **P0 (Critical)** | Double Voice Output (Simultaneous Gemini Audio + Android TTS) | Tool callbacks and transcript updates invoked TTS while Gemini Live streamed audio | **RESOLVED** | `JarvisConversationService.kt` |
| **BUG-02** | **P0 (Critical)** | Repeated / Looping Confirmation Questions | Multiple engines (`ReasoningEngine`, `MessageGate`, Gemini system prompt) asked separately | **RESOLVED** | `IntelligentConfirmationManager.kt`, `MessageExecutionGate.kt` |
| **BUG-03** | **P1 (High)** | Duplicate Action Execution on AI Tool Retries | Lack of action idempotency tracking caused duplicate message sends | **RESOLVED** | `IntelligentConfirmationManager.kt` |
| **BUG-04** | **P1 (High)** | Opening MainActivity Stops Ongoing Voice Session | `onResume()` unconditionally dispatched `ACTION_ENTER_STANDBY` | **RESOLVED** | `MainActivity.kt` |
| **BUG-05** | **P1 (High)** | ZARA Stuck in "Thinking" Mode Indefinitely | Network jitter or silent Gemini Live turns without timeout recovery | **RESOLVED** | `JarvisConversationService.kt` |
| **BUG-06** | **P2 (Medium)** | Floating Window Overlay Violates OriginOS 6 Island Guidelines | Using `WindowManager.addView` overlay instead of native Promoted Ongoing Notification | **RESOLVED** | `ZaraIslandManager.kt`, `OriginIslandController.kt` |
| **BUG-07** | **P2 (Medium)** | Native App Commands Hijacked as Chrome Web URLs | Domain regex matched common words or lacked native package resolution | **RESOLVED** | `CommandParser.kt`, `PhoneControlManager.kt` |
| **BUG-08** | **P2 (Medium)** | Chat History Displays "Spoken user query" Dummy Text | Placeholder text sent to repository before STT transcript finalized | **RESOLVED** | `JarvisConversationService.kt` |

---

## 2. Exhaustive Defect Root Cause & Resolution Analysis

### BUG-01: Double Voice Output (Overlapping Gemini Audio & TTS)
- **Problem Statement:** When Zara responded to user queries or tool calls, users heard two distinct voices talking at the same time: Gemini Live's synthesized voice and Android system TTS reading the transcript text.
- **Root Cause:**
  1. In `JarvisConversationService.kt`, when Gemini Live returned a tool call (such as `send_whatsapp_message`), the tool execution handler immediately called `speakImmediateVoiceResponse(vocalFeedback)` (Android TTS) while also sending the tool response back to Gemini Live over WebSocket.
  2. Gemini Live received the tool result and generated an audio response answering the turn, while Android TTS was already halfway through reading the string.
  3. Furthermore, when Gemini Live streamed text transcripts alongside PCM chunks, text observers triggered TTS without checking if AudioTrack was currently playing.
- **Architectural Fix:**
  - Implemented the **Single Voice Output Arbiter** with a dedicated mutual exclusion lock (`voiceOutputLock`).
  - Added state flags `isGeminiLiveAudioActive` and `hasLiveAudioInCurrentTurn`.
  - In `onAudioDataReceived` / `onPlaybackStateChanged(isPlaying = true)`: Immediately halts any running TTS via `tts?.stop()` and suppresses any secondary TTS request.
  - In `speakImmediateVoiceResponse`: Inspects `audioPlayer?.isPlaying() == true` or `hasLiveAudioInCurrentTurn == true`. If active, secondary TTS is suppressed immediately.
  - Tool call responses check if Gemini Live WebSocket is active; if so, TTS is bypassed and Gemini Live speaks authoritatively.
- **Verification:** Acceptance Test 1 and Test 10 in `SingleVoiceAndConfirmationTest.kt` passed with 0 collisions.

---

### BUG-02: Repeated / Looping Confirmation Questions
- **Problem Statement:** When sending a WhatsApp message or notification reply, Zara repeatedly asked: *"Rahul-kku message send pannava?"*, followed by *"Should I send this message to Rahul?"*, followed by *"Are you sure?"*.
- **Root Cause:**
  - Decentralized confirmation logic: `ConversationalReasoningEngine`, `MessageExecutionGate`, `ProtectedToolExecutor`, and the Gemini system prompt prompt instructions all independently asked for confirmation at different stages of the turn pipeline.
- **Architectural Fix:**
  - Centralized all confirmation policies into `IntelligentConfirmationManager.kt`.
  - Established the **Risk-Based Confirmation Policy**:
    - **Category A (Zero Confirmation):** App launch, YouTube search, flashlight, time, battery, media controls execute directly in < 50ms.
    - **Category B (Single Confirmation):** WhatsApp messages, SMS, notification replies ask **EXACTLY ONCE** using the standardized prompt: `"$recipient-kku WhatsApp message send pannava?"`.
    - **Category C (Strong Confirmation):** Sensitive/destructive operations only.
  - Removed duplicate prompts in `ConversationalReasoningEngine.kt` to delegate directly to `MessageExecutionGate.stageDraft()`.
  - Added unified affirmative/negative voice turn parsing ("yes", "ama", "sari", "send pannu", "no", "illai", "vendaam", "cancel").
- **Verification:** Acceptance Tests 4, 5, 6 in `SingleVoiceAndConfirmationTest.kt` passed.

---

### BUG-03: Duplicate Action Execution on AI Tool Retries
- **Problem Statement:** When Gemini Live received a retry or when user gave verbal confirmation, duplicate message dispatches occasionally occurred, sending the message twice.
- **Root Cause:**
  - Lack of idempotency tracking: the application executed actions whenever an authorized tool call or gate result fired without checking if the identical recipient and content had already been dispatched within the preceding seconds.
- **Architectural Fix:**
  - Added a thread-safe 30-second TTL Idempotency Cache (`recentlyExecutedActions: ConcurrentHashMap<String, Long>`) in `IntelligentConfirmationManager.kt`.
  - Staging and execution calculate a SHA-like action fingerprint: `"$actionType:$recipient:$content"`.
  - If identical action arrives within 30 seconds, returns `ConfirmationResult.AlreadyExecuted` and blocks redundant dispatch.
  - Single-use authentication token invalidated immediately upon dispatch.
- **Verification:** Acceptance Test 7 in `SingleVoiceAndConfirmationTest.kt` passed.

---

### BUG-04: Opening MainActivity Stops Ongoing Voice Session
- **Problem Statement:** When user spoke to Zara via wake-word or floating bubble and then opened the Zara app to view the screen, the voice conversation suddenly died.
- **Root Cause:**
  - In `MainActivity.onResume()`, the activity unconditionally called `startForegroundService(ACTION_ENTER_STANDBY)`, which reset the active conversation session in `JarvisConversationService`.
- **Architectural Fix:**
  - Updated `MainActivity.onResume()` to inspect `JarvisConversationService.isSessionOn.value` and `JarvisConversationService.isStandby.value`.
  - If a voice session is already active (`isLive == true`), `ACTION_ENTER_STANDBY` is **skipped**, allowing the ongoing voice session to continue smoothly in the foreground.
- **Verification:** Manual and unit test verification confirmed session continuity across activity lifecycle transitions.

---

### BUG-05: ZARA Stuck in "Thinking" Mode Indefinitely
- **Problem Statement:** In weak network conditions, Zara occasionally transitioned to "Thinking" state and remained stuck there forever without speaking or resetting.
- **Root Cause:**
  - WebSocket disconnects, packet loss, or silent Gemini turns failed to trigger `onTurnComplete`, leaving `ConversationState.THINKING` unfinalized.
- **Architectural Fix:**
  - Implemented `thinkingTimeoutJob` with a strict 6000ms watchdog timer in `JarvisConversationService.kt`.
  - If state is still `THINKING` and no audio has been received after 6 seconds:
    1. Calls `liveWebSocket?.abortTurn()` to kill the stalled turn.
    2. Automatically executes REST fallback via `GeminiRestClient.generateContent()`.
    3. If REST succeeds, speaks answer via authoritative Android TTS and transitions cleanly to `SPEAKING` -> `IDLE`.
    4. If network is completely unavailable, speaks friendly offline message and resets cleanly to `IDLE`.
- **Verification:** Zero stuck states observed in 20-command acceptance testing.

---

### BUG-06: Floating Window Overlay Violates OriginOS 6 Island Guidelines
- **Problem Statement:** Early versions injected a custom `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` view to simulate an island, which conflicted with OriginOS 6's native punch-hole island.
- **Root Cause:**
  - Attempting to draw custom floating views over the status bar rather than leveraging native system framework integration.
- **Architectural Fix:**
  - Strictly disabled all custom floating window overlays in `ZaraIslandManager.kt` (`attachOverlayView()` is no-op).
  - Implemented `OriginIslandController.kt` to dock into native OriginOS 6 / Android 16 via **Promoted Ongoing Notification (Notification ID 41)** on the active Microphone Foreground Service.
  - Added Android 16 API 36 `setShortCriticalText` and `MediaStyle` notification actions.
- **Verification:** Compliant with Vivo OriginOS 6 system architecture without overlay permission abuse.

---

### BUG-07: Native App Commands Hijacked as Chrome Web URLs
- **Problem Statement:** Saying "open instagram" or "open chatgpt" occasionally opened Chrome with web search or URL instead of launching the native installed Android app.
- **Root Cause:**
  - Greedy URL regex in `CommandParser.kt` and `PhoneControlManager.kt` matched generic tokens, or `openApp` fell back to `openUrl()` prematurely.
- **Architectural Fix:**
  - Hardcoded Category A priority parsing in `ZaraAIOrchestrator.kt` and `CommandParser.kt` (< 50ms).
  - Explicitly mapped popular app names (`instagram`, `chatgpt`, `whatsapp`, `youtube`, `spotify`, `camera`, `settings`, `gallery`) directly to native package intents.
  - Domain regex strictly requires explicit top-level domains (e.g. `.com`, `.dev`, `.org`) or protocols (`http://`, `https://`).
- **Verification:** Acceptance tests in `CommandParserAppLaunchTest.kt` passed for all 11 native apps.

---

### BUG-08: Chat History Displays "Spoken user query" Dummy Text
- **Problem Statement:** In the chat UI, previous turns showed "Spoken user query" instead of the words the user actually spoke.
- **Root Cause:**
  - An early placeholder string was passed to `chatRepository.addTurn()` before Vosk STT or Gemini turn recognition finalized.
- **Architectural Fix:**
  - Completely eliminated the dummy string. Verified zero occurrences in codebase.
  - Real-time recognized transcript from `_liveUserTranscript` and `VoskModelManager.transcribe()` is now passed directly to `chatRepository.addTurn()`.
- **Verification:** Grep search confirmed zero occurrences of "Spoken user query" in source code.

---

## 3. Residual Known Limitations

| Limitation | Impact | Recommended Handling |
| :--- | :--- | :--- |
| **Vosk Initial Cold-Start Delay (~1.2s on first launch)** | First utterance recognition may take slightly longer while `am/final.mdl` is mapped to memory | Pre-warmed during `JarvisApp.onCreate()` in background IO thread |
| **Android 16 Background Activity Launch Restrictions** | Opening apps from background requires Accessibility Service or Overlay Permission | `ActivityLauncherHelper` automatically leverages Accessibility Service or Promoted Full-Screen Intent fallback |

---

*Certified Defect Resolution Report by Autonomous Systems QA.*  
*Architect: Mohanraj ([@Hexvora777](https://t.me/Hexvora777)) — "Every identity has an origin. Mine is Mohanraj."*
