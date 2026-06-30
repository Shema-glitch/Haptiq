# HAPTIQ AOT ENGINE: HAPTIC MAP CACHING SYSTEM
**Version:** 1.0  
**Status:** Architecture Specification  

## 1. The Core Concept
Instead of running heavy `TarsosDSP` Fast Fourier Transform (FFT) calculations[cite: 1, 2] on the main playback thread, Haptiq will utilize Android's `WorkManager` API to silently scan newly added audio files in the background. The engine will map the low-frequency energy (bass hits) to specific timestamps, save this map to the local `Room` database[cite: 1], and bypass the FFT engine entirely during actual playback.

## 2. The Room Database Schema Update
The `Room` Schema outlined in the Architecture Bible[cite: 1] must be expanded to store these pre-computed maps.

**New Table: `HapticTrackMap`**
* **`songId`** *(String)*: The unique identifier from the MediaStore.
* **`durationMs`** *(Long)*: Total track length.
* **`isProcessed`** *(Boolean)*: Flag to check if the background worker has finished learning the track.
* **`hapticFrames`** *(ByteArray / BLOB)*: A compressed array of intensity values (0-100) mapped to a fixed time interval (e.g., every 50ms).

## 3. The Background "Auto-Learn" Worker
When the app requests permission to read the user's Music and Audio[cite: 5], a background sync is triggered.
* **The Trigger:** Android `WorkManager` detects new MP3, FLAC, or WAV files[cite: 2] in the MediaStore.
* **The Processing:** A background coroutine loads the audio file into memory, runs the FFT analysis[cite: 1] at high speed, and logs the amplitude of the sub-bass (20-60 Hz) and bass (60-250 Hz) frequencies[cite: 2].
* **The Output:** The worker generates a highly compressed byte array (the `hapticFrames`) and saves it to the local `Room` database.

## 4. The Playback Pipeline (Zero Math)
When the user presses play on a track, the `SongRepository`[cite: 1] checks the database.
* **If `isProcessed == true`:** The app loads the `hapticFrames` array into memory. As ExoPlayer progresses, a lightweight Coroutine reads the array index matching the current playback time and fires the `VibratorManager`[cite: 1, 2]. CPU usage for math processing drops to near 0%.
* **If `isProcessed == false`:** The app falls back to the real-time Custom AudioProcessor[cite: 1] to calculate the haptics live, while seamlessly queuing the track to be permanently learned in the background later.

## 5. Preset Integration
Because the saved `hapticFrames` array stores raw amplitude values (0-100) rather than hardcoded vibrations, the user's selected preset (Deep Bass, Punch, Concert, Soft Pulse)[cite: 2] still works perfectly. The engine dynamically applies the preset's multiplier math to the pre-saved array immediately before firing the haptic motor.