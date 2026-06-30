

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 1
Haptiq — Haptic Enhancement Engine for Android
UI/UX Specification
Haptiq — Haptic Enhancement Engine for Android
This document specifies the complete UI/UX design system for Haptiq. It covers screen anatomy,
component specifications, interaction patterns, haptic feedback integration, and design tokens. Use this
alongside the Architecture Bible for implementation.
DocumentVersionStatusPlatform
Haptiq UI/UX Spec1.0In ProgressAndroid 13+ / Compose
Target SDKMin SDKUI FrameworkDesign System
36 (Android 16)33 (Android 13)Jetpack ComposeMaterial 3 Expressive

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 2
Haptiq — Haptic Enhancement Engine for Android
## SECTION 01
## Design Tokens
Colors, typography, spacing, elevation
## 1.1 Color Tokens
Haptiq uses a dark-first palette. The primary accent is amber-gold (#D4A853) reserved exclusively for
haptic-related UI elements — it signals "this is the haptic layer." Dynamic color from Material You adapts to
album artwork.
TokenValueCompose ReferenceUsage
colorBackground
## #0D0D0F
MaterialTheme.colorScheme.back
ground
App root background
colorSurface
## #16161A
MaterialTheme.colorScheme.surf
ace
Cards, sheets, bottom nav
colorSurfaceVariant
## #1E1E24
MaterialTheme.colorScheme.surf
aceVariant
Elevated cards, input fields
colorHapticAccent
## #D4A853
Custom token — hapticAccent
Haptic toggle, intensity, active
preset
colorOnBackground
## #F0EDE8
MaterialTheme.colorScheme.onBa
ckground
Primary text, song titles
colorOnSurface60
## #9A9599
MaterialTheme.colorScheme.onSu
rface.copy(0.6)
Secondary text, artist names
colorDynamic
## Palette-extr
acted
dynamicDarkColorScheme() /
Palette API
Per-track primary color from
artwork
## 1.2 Typography Scale
RoleStyleSize / WeightUsage
displayLargeRoboto / Inter57sp / RegularNot used in MVP
headlineLargeRoboto / Inter32sp / RegularAlbum title on detail screen
headlineMediumRoboto / Inter28sp / RegularSong title on Now Playing
titleLargeRoboto / Inter22sp / RegularScreen titles, section headers
titleMediumRoboto / Inter16sp / MediumList item primary text
bodyLargeRoboto / Inter16sp / RegularBody content, descriptions
bodyMediumRoboto / Inter14sp / RegularArtist names, secondary list text
labelLargeRoboto / Inter14sp / MediumButtons, chips, tab labels
labelSmallRoboto / Inter11sp / MediumTimestamps, metadata labels

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 3
Haptiq — Haptic Enhancement Engine for Android
## 1.3 Spacing & Layout Grid
All spacing uses an 8dp base grid. No magic numbers. Horizontal margins are 16dp. Content well is max
600dp on large screens.
TokenValueUsage
spacing.xs4dpInternal component padding
spacing.sm8dpBetween related elements
spacing.md16dpScreen horizontal margins, between sections
spacing.lg24dpSection separation
spacing.xl32dpLarge gaps, above page titles
spacing.artwork_margin24dpAlbum art margin on Now Playing
corner.card12dpStandard card corner radius
corner.sheet28dpBottom sheet top corners (M3 standard)
corner.chip8dpFilter chips
corner.artwork16dpAlbum artwork corners

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 4
Haptiq — Haptic Enhancement Engine for Android
## SECTION 02
## Screen Specifications
Anatomy, components, states for all 8 MVP screens
## 2.1 Now Playing
SlotComponentSpec
Status barSystem status barTransparent, edge-to-edge, icons white
Album ArtworkAsyncImage + Rounde
dCornerShape(16dp)
Fills max width minus 24dp margins. Aspect ratio 1:1. Shadow
elevation 8dp.
Song TitleText(style=headlineMe
dium)
Max 2 lines. Ellipsize end. 28sp Bold.
Artist NameText(style=bodyLarge,
color=onSurface60)
1 line. Tap navigates to artist. 16sp Regular.
Progress BarSlider(M3) + time
labels
Thumb visible. Active track = hapticAccent. Buffered track =
surface variant.
Transport ControlsRow: Skip Prev ·
Play/Pause · Skip Next
Icon size 48dp. Play/Pause button 64dp filled circle. Ripple on tap.
Haptic Toggle FABFloatingActionButton
## (small)
Bottom-right corner. Amber when active. Vibration icon. Tap opens
## Haptic Sheet.
## Mini Player
## (collapsed)
Animated surface bar
at bottom
Height 72dp. Tap expands. Persistent across navigation.
## 2.2 Haptic Control Sheet
SlotComponentSpec
Drag HandleM3 ModalBottomSheet
default handle
32dp × 4dp. Centered. Color = onSurface / 38%
Sheet TitleText "Haptic Control"titleLarge. Left-aligned. 24dp from edge.
Enable ToggleSwitch (M3)Right-aligned with label. Amber track when enabled.
Preset ChipsFilterChip × 4Deep Bass / Punch / Concert / Soft Pulse. Single-select. Selected
= hapticAccent fill.
Intensity SliderSlider + label
"Intensity"
0–100%. Active track = hapticAccent. Value label above thumb on
drag.
Sheet PeekPartial expand stateShows toggle + current preset. Full expand reveals intensity.
## 2.3 Home Screen

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 5
Haptiq — Haptic Enhancement Engine for Android
SlotComponentSpec
Top App BarSmallTopAppBarTitle "Haptiq". Hamburger or profile icon right.
Search BarSearchBar (M3
docked)
Full width. Placeholder "Search songs, artists...". On focus
expands with recent.
Recently PlayedLazyRow of
MediaCard
Horizontal scroll. Card = 160×200dp. Artwork + 2 lines text below.
SuggestedLazyColumn of
TrackRow
Standard list. 56dp height. Artwork 48dp. Title + artist. Play button
right.
Mini PlayerPersistent bottom barZ-order above nav bar. Height 72dp.
Navigation BarNavigationBar (M3)4 items: Home / Search / Library / Settings. Indicator pill on active
item.

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 6
Haptiq — Haptic Enhancement Engine for Android
## SECTION 03
## Component Library
Reusable Compose components and their specs
3.1 MediaCard
PropertyValue
## Width × Height160dp × 200dp
ArtworkAsyncImage, 160×160dp, RoundedCornerShape(12dp)
TitletitleMedium, 1 line, ellipsize
SubtitlebodyMedium, onSurface60, 1 line
Press stateScale 0.97, ElevatedCard press animation
Long pressContext menu: Play, Add to queue, View album
3.2 TrackRow
PropertyValue
Height56dp (list) / 72dp (queue with drag handle)
Leading artworkAsyncImage 48×48dp, RoundedCornerShape(8dp)
TitletitleMedium, 1 line
SubtitlebodyMedium, onSurface60
TrailingDuration label + overflow icon button
Active stateRow background = hapticAccent / 12%. Title = hapticAccent.
3.3 HapticFAB
PropertyValue
SizeSmallFloatingActionButton (40dp)
IconVibration icon (material symbols)
Enabled colorcontainerColor = hapticAccent, contentColor = onBackground
Disabled colorcontainerColor = surfaceVariant, contentColor = onSurface60
AnimationScale spring 0.85 → 1.0 on toggle. Bounce settle.
BadgeCurrent preset name shown above FAB as tiny label tooltip
3.4 NowPlayingBar (Mini Player)

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 7
Haptiq — Haptic Enhancement Engine for Android
PropertyValue
## Height72dp
BackgroundSurface + elevation 3dp
LeadingArtwork 48×48dp
ContentTitle (titleMedium) + Artist (bodySmall) stacked
TrailingPlay/Pause IconButton (40dp) + Skip Next IconButton (40dp)
ProgressLinear progress indicator at bottom edge, 2dp height
Expand gestureTap anywhere or swipe up → shared element transition to Now Playing

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 8
Haptiq — Haptic Enhancement Engine for Android
## SECTION 04
## Interaction Patterns
Gestures, navigation, state transitions
## 4.1 Navigation Structure
Haptiq uses a flat navigation model with NavigationBar as the primary nav component. The Now Playing
screen sits on its own back stack, accessible from any screen via the persistent mini player.
RouteNavigate FromTransitionBack Behavior
HomeNav bar item 0FadeExit app
SearchNav bar item 1FadePop to Home
LibraryNav bar item 2FadePop to Home
NowPlayingMini player tap / TrackRow
tap
Shared element expandCollapse to mini player
AlbumDetailMediaCard tapScale + fade inPop to previous
SettingsNav bar item 3FadePop to Home
## 4.2 Gesture Reference
GestureTargetResult
TapTrackRowBegin playback, navigate to Now Playing
TapMini PlayerExpand to Now Playing (shared element)
Swipe UpMini PlayerExpand to Now Playing
Swipe DownNow PlayingCollapse to mini player
Swipe UpNow Playing bottomOpen Queue sheet
Long PressTrackRow / MediaCardContext menu (Play, Queue, View Album)
Swipe Left/RightNow Playing artworkSkip prev / skip next (with confirm animation)
DragQueue TrackRow handleReorder queue
TapHaptic FABToggle haptics + open preset sheet
Slider dragIntensity sliderLive haptic feedback preview while dragging
## 4.3 Loading & Empty States
Every screen must handle three data states. No spinners — use skeleton screens.
ScreenLoading StateEmpty StateError State

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 9
Haptiq — Haptic Enhancement Engine for Android
HomeSkeleton cards
(ShimmerEffect)
Illustration + "Add music to get
started"
Retry button + error text
LibrarySkeleton list rows"Your library is empty" + scan
button
Retry with last scan result
SearchNo loading (instant filter)"No results for X" +
suggestions
## N/A
Now PlayingPulse animation on artworkN/A (always has song
context)
Playback error inline toast
QueueSkeleton rows"Queue is empty — add
songs"
## N/A

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 10
Haptiq — Haptic Enhancement Engine for Android
## SECTION 05
Haptic UI Integration
How haptics and UI connect
5.1 Haptic State in UI
The UI must always reflect the current haptic state clearly. The amber accent color (#D4A853) is the
exclusive haptic indicator. Nothing else in the UI should use this color.
UI ElementHaptic OFF StateHaptic ON State
Haptic FABsurfaceVariant bg, onSurface60 iconhapticAccent bg, onBackground icon, glow
shadow
Preset chipsNot visibleFilterChip row visible, selected = hapticAccent
Progress bar trackprimary color (dynamic)hapticAccent color
Mini Player progressonSurface30 colorhapticAccent color
Intensity sliderHiddenVisible. hapticAccent active track.
NotificationStandard media controlsAdds haptic icon indicator in notification
## 5.2 Haptic Preset Visual Identifiers
PresetIconChip Color ActiveDescription Shown
Deep Basswaveform iconhapticAccentLow frequency · Long pulses
Punchbolt iconhapticAccentSharp impact · High energy
Concertequalizer iconhapticAccentBalanced · Full spectrum
Soft Pulseheartbeat iconhapticAccentGentle · Ambient
## 5.3 Intensity Visualization
When the user drags the intensity slider, the UI provides live visual feedback that mirrors the haptic pulse
pattern — so users on low-haptic devices still understand what they're configuring.
→ Slider thumb scales up on drag (1.0 → 1.2×)
→ Artwork border pulses with alpha at bass-detection frequency
→ Intensity value label appears above thumb during drag, disappears on release
→ Do NOT animate background colors — too distracting during music
## SECTION 06
## Accessibility
TalkBack, contrast, touch targets

HAPTIQUI/UX Specification  ·  v1.0  ·  ConfidentialPage 11
Haptiq — Haptic Enhancement Engine for Android
RequirementSpecImplementation
Touch targetsMinimum 48×48dpAll interactive elements use
Modifier.minimumInteractiveComponentSize()
Color contrastWCAG 2.1 AA (4.5:1 text)TEXT_P on BG = 14.2:1 3. TEXT_S on BG = 5.8:1 3
TalkBack labelsAll icons have
contentDescription
Transport controls: "Play", "Pause", "Skip to next track",
etc.
Haptic accessibilityHaptics must be optionalDefault OFF. Toggle clearly labeled. System accessibility
setting respected.
Font scalingSupport up to 200%All text in sp units. Layouts tested at 200% font size.
Dynamic colorsMust meet contrast at all
palettes
Check extracted colors against WCAG on palette
generation
This specification is a living document. Update version number when screens are finalized or component
specs change. All measurements in density-independent pixels (dp) unless noted as sp for text.