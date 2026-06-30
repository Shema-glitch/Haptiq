## **HAPTIQ_ANDROID_ARCHITECTURE_BIBLE_V1.1_PATCH** 

## **Audio Focus Requirements** 

Implement Android Audio Focus. 

Scenarios: 

Incoming Call → Pause Playback → Suspend Haptics 

Notification Interruption → Duck Audio 

Focus Regained → Resume Playback → Resume Haptics 

## **Foreground Playback Service** 

Use MediaSessionService. 

Requirements: 

Media Notification 

Lock Screen Controls 

Bluetooth Controls 

Background Playback Support 

Playback must survive screen lock. 

## **Repository Rules** 

ViewModels may never access Room directly. 

Required repositories: 

SongRepository 

RecentSongRepository 

PresetRepository 

1 

CalibrationRepository 

All persistence flows through repositories. 

## **Room Schema** 

RecentSongs 

- songId • lastPlayedAt 

SavedPresets 

- presetId • intensity • sensitivity 

CalibrationProfile 

- deviceModel • multiplier 

- updatedAt 

## **FFT Processing Threading** 

Main Thread: UI only 

Background Thread: FFT Analysis 

Background Thread: Bass Extraction 

Background Thread: Haptic Mapping 

Never perform FFT calculations on the Main Thread. 

## **Audio Pipeline** 

MediaStore 

↓ 

SongRepository 

2 

↓ 

Media3 ExoPlayer 

## ↓ 

Custom AudioProcessor 

## ↓ 

PCM Sample Stream 

## ↓ 

TarsosDSP Dispatcher 

## ↓ 

FFT Analysis 

## ↓ 

Bass Detection 

## ↓ 

Haptic Mapper 

## ↓ 

VibratorManager 

## **Permission Architecture** 

PermissionViewModel 

PermissionUiState 

Fields: 

hasMediaPermission 

permissionDenied 

3 

permissionDeniedPermanently 

All permission logic is centralized. 

## **Calibration Flow** 

Step 1: Play Test Pulse 

Step 2: User confirms perception 

Step 3: User selects strength 

Weak 

Medium 

Strong 

Step 4: Multiplier generated 

Step 5: Profile stored 

## **Haptic Safety Rules** 

Maximum Pulse Duration: 100ms 

Minimum Cooldown: 20ms 

Thermal Protection: Enabled 

Battery Saver: Reduce intensity by 30% 

Continuous vibration loops are forbidden. 

## **StateFlow Rules** 

Every screen exposes: 

UiState 

UiEvent 

4 

UiAction 

Single source of truth: 

StateFlow 

Compose collects state only. 

Business logic never lives inside Composables. 

## **Coding Agent Constraints** 

Compose Only 

No XML 

No Firebase 

No Backend 

No Accounts 

No AI Features 

No TensorFlow 

No Streaming Integrations 

No Placeholder Code 

Every feature must compile before proceeding to the next build phase. 

5 

