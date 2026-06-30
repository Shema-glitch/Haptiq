
# HAPTIQ — COMPLETE DEVELOPER HANDOFF PACKAGE
Version 1.0

## Executive Summary

Haptiq is NOT a music player startup.

Haptiq is a real-time haptic enhancement engine for music playback on Android.

Primary Goal:
Convert low-frequency audio information into synchronized tactile feedback.

Primary Validation Question:
Will users prefer listening with Haptiq enabled?

---

# PRODUCT STRATEGY

## Problem

Headphones reproduce sound but not physical bass sensation.

Users hear bass but do not feel impact.

## Core Hypothesis

Synchronized vibration can increase immersion and perceived bass response.

## Success Metric

30%+ of testers continue using haptics after 7 days.

---

# MVP SCOPE

## Must Have

- Local audio playback
- MP3 support
- FLAC support
- WAV support
- FFT analysis
- Bass detection
- Real-time vibration generation
- 4 haptic presets
- Material 3 UI

## Excluded

- AI
- TensorFlow
- Community features
- Accounts
- Backend
- Streaming services
- Social features

---

# TECHNICAL FEASIBILITY

## Android Version

Target SDK 36
Minimum SDK 33

Android 13+

## Haptics APIs

Use:

- VibratorManager
- VibrationEffect

## Audio Engine

Preferred:

- Media3 ExoPlayer

Reason:

- Stable
- Maintained
- Compose friendly

## FFT Processing

Library:

TarsosDSP

Pipeline:

Audio -> FFT -> Bass Energy -> Haptic Mapper

---

# SYSTEM ARCHITECTURE

## Architecture Style

Clean Architecture

### Layers

Presentation
Domain
Data

### Modules

app
core:designsystem
core:audio
core:haptics
feature:player
feature:settings

---

# PROJECT STRUCTURE

app/
core/
designsystem/
audio/
haptics/

features/
player/
settings/

domain/
usecases/

data/
repositories/

---

# USER FLOWS

## First Launch

Splash
→ Permissions
→ Library Scan
→ Player

## Music Playback

Select Song
→ Play
→ FFT Analysis
→ Haptic Mapping
→ Vibrator Output

---

# SCREENS

## Home

Components:

- Album artwork
- Song title
- Artist
- Play/Pause
- Seek bar

## Haptics Sheet

Presets:

- Deep Bass
- Punch
- Concert
- Soft Pulse

Controls:

- Intensity slider
- Enable toggle

## Settings

- Battery saver
- Device calibration
- About

---

# DOMAIN MODELS

Song
- id
- title
- artist
- duration
- artwork

Preset
- id
- name
- sensitivity
- intensity

HapticFrame
- timestamp
- amplitude
- duration

---

# AUDIO PROCESSING

## Frequency Bands

Sub Bass:
20-60 Hz

Bass:
60-250 Hz

Ignore:
Midrange and highs

## FFT Window

1024 samples

Target refresh:

20-30 updates per second

---

# HAPTIC MAPPING

## Deep Bass

Strong amplitude
Long duration

## Punch

Short aggressive pulses

## Concert

Balanced response

## Soft Pulse

Reduced intensity

---

# PERFORMANCE TARGETS

Startup:
<3 seconds

Latency:
<50ms

CPU:
<15%

Battery:
<8% per hour

---

# DEVICE COMPATIBILITY

Tier A

Pixel
Samsung S Series

Tier B

OnePlus
Nothing

Tier C

Budget devices

Calibration screen required.

---

# ANALYTICS EVENTS

app_open

song_played

haptics_enabled

preset_changed

session_duration

---

# TESTING PLAN

## Music Genres

EDM
Hip Hop
Rock
Pop

## Devices

Samsung
Pixel
Xiaomi

## Validation

Check:
- Sync quality
- Battery usage
- Heat generation

---

# RISKS

## Risk 1

Poor vibration hardware.

Mitigation:
Calibration.

## Risk 2

Battery drain.

Mitigation:
Dynamic throttling.

## Risk 3

Users disable feature.

Mitigation:
Improve presets.

---

# FUTURE PHASES

Phase 2

- BPM detection
- Song structure analysis
- Adaptive presets

Phase 3

- Community profile sharing

Phase 4

- External wearable support

---

# CODING AGENT MASTER PROMPT

You are a senior Android engineer.

Build Haptiq using:

- Kotlin
- Jetpack Compose
- Material 3
- Media3 ExoPlayer
- TarsosDSP

Requirements:

1. Use Clean Architecture.
2. Use MVVM.
3. Use Hilt.
4. Use Kotlin Coroutines.
5. Use StateFlow.
6. Avoid XML layouts.
7. Compose only.
8. Target Android 13+.
9. Create production-ready code.
10. Generate complete files.

Work feature-by-feature.

Never generate placeholders.

Every screen must compile.

Every feature must be runnable.
