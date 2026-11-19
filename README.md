# AETHER X — Next-Gen Android AI Assistant (Hybrid Gemini Live + LangGraph)

[![Build & Release AETHER X APKs](https://github.com/MMohanraj21/AetherX/actions/workflows/build-apk.yml/badge.svg)](https://github.com/MMohanraj21/AetherX/actions/workflows/build-apk.yml)
[![License: TAMCL v1.0 Attributed](https://img.shields.io/badge/Attribution-TAMCL%20v1.0-blue.svg)](docs/THIRD_PARTY_NOTICES.md)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-brightgreen.svg)](https://developer.android.com)
[![Python Backend](https://img.shields.io/badge/Backend-FastAPI%20%2B%20LangGraph-blue.svg)](backend/)

AETHER X is an independently engineered, cyberpunk-inspired, enterprise-grade Android AI assistant featuring real-time bidirectional audio streaming via Google's Gemini Live protocol, multimodal computer vision, encrypted persistent memory, grounded UI automation with mandatory safety gating, and a production FastAPI/LangGraph multi-agent backend.

---

## 🏛 System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                   AETHER X Android Client (API 26–34)                  │
│       Jetpack Compose • Cyberpunk Hologram Theme • Reactive HUD        │
├───────────────────┬───────────────────┬────────────────────────────────┤
│ :app              │ UI & Screens      │ Companion, Chat, Vision, Setup │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ :ai-engine        │ Gemini Live Bidi  │ WebSockets, Tool Dispatcher    │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ :voice-engine     │ Audio Pipeline    │ 16kHz in / 24kHz out, VAD      │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ :vision-engine    │ Computer Vision   │ CameraX, Screen OCR, Grounding │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ :automation-eng   │ Device Actions    │ ActionGatekeeper, Approval UI  │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ :memory-engine    │ Encrypted Storage │ Room DB, AES-256 KeyStore      │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ :security         │ Defense & Privacy │ Tamper Audits, Privacy Shield  │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ :networking       │ Gateway Client    │ OkHttp3, SSE Stream Reader     │
└───────────────────┴───────────────────┴────────────────────────────────┘
                                    ▲
                                    │ HTTPS (Bearer Auth + SSE)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        FastAPI Backend Gateway                         │
│               Rate Limiting, Memory Sync, Security Allowlist           │
├────────────────────────────────────────────────────────────────────────┤
│                  LangGraph Multi-Agent Orchestration                   │
│   Companion • Vision • Device • Chess • Travel • Research • Analytics  │
├───────────────────────────────────┬────────────────────────────────────┤
│         Google Gemini API         │             OpenAI API             │
│ • Gemini 3.8 / 3.5 Flash          │ • GPT-4o                           │
│ • Gemini Live WebSocket (Bidi)    │ • Secondary / Fallback Gate        │
│ • Multimodal Image & Document OCR │ • Configurable Explicit Lock       │
└───────────────────────────────────┴────────────────────────────────────┘
```

---

## 📂 Repository Layout

```
AetherX/
├── .github/workflows/
│   └── build-apk.yml               # Automated GitHub Actions APK Compiler & Publisher
├── app/                            # Android Application module (Compose UI, Theme, Icons)
├── ai-engine/                      # Gemini Live WebSocket (Bidi), Tool Dispatcher
├── voice-engine/                   # 16kHz PCM capture, 24kHz AudioTrack, Audio Energy Meter
├── vision-engine/                  # CameraX frame analyzer, Screen capture, UI Grounding
├── automation-engine/              # Safe AccessibilityService, ActionGatekeeper approval
├── memory-engine/                  # Room DB with transparent Keystore encryption
├── networking/                     # OkHttp3 client, SSE streaming parser, NetworkResult
├── security/                       # Android Keystore AES-256-GCM, Integrity Auditing
├── backend/                        # FastAPI + LangGraph Multi-Agent Service (22/22 Tests Passing)
│   ├── app/                        # Providers, Agents, Orchestration, Services, Routes
│   └── tests/                      # Automated test suite (run_all_tests.py)
├── docs/
│   ├── JARVIS_APK_ANALYSIS_AND_ARCHITECTURE_REPORT.md  # Full decompilation & audit report
│   └── THIRD_PARTY_NOTICES.md      # Legal attributions (TAMCL v1.0 by Tech GPTx)
├── keystore/                       # Release keystore for automated signing
├── build.gradle.kts                # Root multi-module build script
├── settings.gradle.kts             # Subproject declarations
└── README.md
```

---

## 🚀 Key Features

### 1. Real-Time Gemini Live Audio (Bidirectional Streaming)
- Direct full-duplex WebSocket connection:
  `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent`
- **Uplink**: 16,000 Hz linear PCM 16-bit mono audio with local VAD silence signaling (`audioStreamEnd: true`).
- **Downlink**: 24,000 Hz low-latency PCM streaming to Android `AudioTrack` with real-time interruption handling.
- **Proactive Renewal**: 9-minute auto-renewal timer prevents Google's 10-minute session truncation.

### 2. Human-in-the-Loop Automation (`ActionGatekeeper`)
- Resolves the major vulnerability identified in the tutorial JARVIS codebase where accessibility services triggered calls and messages without runtime confirmation.
- Sensitive actions (initiating phone calls, sending SMS/WhatsApp messages, deleting memory, modifying system state) trigger an unbypassable `SafetyApprovalDialog`.

### 3. Hardware-Backed Keystore Encryption
- Encrypts API keys, user memories, and session tokens at rest using Android Keystore `AES-256-GCM`.
- Never stores raw keys in plaintext XML preferences.

### 4. Multimodal Vision Intelligence
- CameraX live image analysis for:
  - **Fashion & Attire**: Color matching and clothing recommendations.
  - **Landmarks & Travel**: Historical and geographic site recognition.
  - **Document OCR**: Structured data extraction.
  - **Visual QA**: Dynamic contextual reasoning over camera frames.

---

## 📱 Installation & Downloads

### Local Device APK
If you are running in Termux or on an Android device:
- The compiled, signed release APK is placed directly in your Downloads directory:
  `file:///sdcard/Download/AetherX-release.apk`
- Tap the file to install immediately via your Android Package Installer.

### Cloud CI/CD Releases
Every push to `main` triggers GitHub Actions to compile and sign:
- **AetherX-release.apk**: Production release signed with release keystore.
- **AetherX-debug.apk**: Debug build with detailed logging.
- Download artifacts directly from [GitHub Actions Runs](https://github.com/MMohanraj21/AetherX/actions).

---

## 🧪 Testing & Verification

### Python Backend Test Suite (100% Pass Rate)
Run the automated test suite locally:
```bash
python3 backend/tests/run_all_tests.py
```
**Results**:
- Ran 22 tests in 46.032s — **ALL 22 TESTS PASSED (OK)**.
- Covers: Live Gemini API interactions, OpenAI fallback, multi-turn memory, image and document analysis, tool dispatching, and security health checks.

---

## ⚖️ Legal & Attributions

AETHER X is an independent clean-room engineering project. We gratefully acknowledge the research, architecture, and educational videos by **Tech GPTx (Rehaan)** on the JARVIS assistant series.

All original contributions are formally cited in [THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md) in full accordance with the **Tech Automation & Media Copyright License v1.0 (TAMCL v1.0)**.
