---
name: Haptiq Expressive
colors:
  surface: '#16161A'
  surface-dim: '#16130d'
  surface-bright: '#3d3831'
  surface-container-lowest: '#110e08'
  surface-container-low: '#1f1b15'
  surface-container: '#231f19'
  surface-container-high: '#2e2923'
  surface-container-highest: '#39342d'
  on-surface: '#eae1d7'
  on-surface-variant: '#d2c5b2'
  inverse-surface: '#eae1d7'
  inverse-on-surface: '#343029'
  outline: '#9b8f7e'
  outline-variant: '#4e4637'
  surface-tint: '#eec068'
  primary: '#f2c36b'
  on-primary: '#412d00'
  primary-container: '#d4a853'
  on-primary-container: '#573d00'
  inverse-primary: '#7b5804'
  secondary: '#c9c6c1'
  on-secondary: '#31302d'
  secondary-container: '#474743'
  on-secondary-container: '#b7b5b0'
  tertiary: '#afcbff'
  on-tertiary: '#023061'
  tertiary-container: '#8fb0e9'
  on-tertiary-container: '#1d4274'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdea6'
  primary-fixed-dim: '#eec068'
  on-primary-fixed: '#271900'
  on-primary-fixed-variant: '#5d4200'
  secondary-fixed: '#e5e2dd'
  secondary-fixed-dim: '#c9c6c1'
  on-secondary-fixed: '#1c1c19'
  on-secondary-fixed-variant: '#474743'
  tertiary-fixed: '#d6e3ff'
  tertiary-fixed-dim: '#a8c8ff'
  on-tertiary-fixed: '#001b3d'
  on-tertiary-fixed-variant: '#234779'
  background: '#0D0D0F'
  on-background: '#eae1d7'
  surface-variant: '#1E1E24'
  on-surface-60: '#9A9599'
  haptic-accent: '#D4A853'
typography:
  headline-lg:
    fontFamily: Roboto
    fontSize: 32px
    fontWeight: '400'
    lineHeight: 40px
  headline-md:
    fontFamily: Roboto
    fontSize: 28px
    fontWeight: '400'
    lineHeight: 36px
  title-lg:
    fontFamily: Roboto
    fontSize: 22px
    fontWeight: '400'
    lineHeight: 28px
  title-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  artwork-margin: 24px
---

## Brand & Style

The design system embodies a **Premium Native Android** aesthetic, specifically utilizing the **Material 3 Expressive** framework. It targets audiophiles and power users who seek a deeper, tactile connection to their media. The personality is disciplined and sophisticated—drawing from the functional restraint of Nothing OS and the visual hierarchy of Apple Music—while maintaining the technical precision of a haptic engine.

The design style is **Corporate / Modern** with a focus on **Tonal Hierarchy**. It rejects "gamer" tropes in favor of a mature, dark-first interface where the UI recedes to let album artwork and a singular "Exclusive Haptic Accent" take center stage. The emotional response should be one of "invisible power": a clean, effortless container for a high-performance haptic experience.

**Key Stylistic Pillars:**
- **Artwork Supremacy:** All layout decisions prioritize the visual impact of media assets.
- **Chromatic Restraint:** Color is used strictly for functional signaling, not decoration.
- **Tactile feedback:** Visual elements (sliders, toggles) should feel physically linked to the haptic motor's state.

## Colors

This design system utilizes a **dark-first palette** to ensure the display hardware remains efficient and the content (artwork) feels luminous. 

- **The Haptic Accent (#D4A853):** This amber-gold is the most critical token in the system. It is reserved *exclusively* for haptic-related UI. If an element is amber, it directly controls or represents the "haptic layer."
- **Dynamic Adaptivity:** While the UI is predominantly dark, the `primary` container and specific playback highlights should adapt via the Material You Palette API, extracting hues from current album artwork to create a cohesive "mood" for the Now Playing state.
- **Surface Tiers:** Depth is established through three dark neutral tiers: Background (#0D0D0F) for the root, Surface (#16161A) for standard cards/sheets, and Surface Variant (#1E1E24) for interactive inputs or high-elevation cards.

## Typography

The typography strategy leverages the ubiquity and readability of **Roboto** for large-scale expressive headlines and **Inter** for utilitarian body and label text. 

- **Song Titles:** Use `headline-md` (28sp) to ensure high visibility and a premium feel.
- **Hierarchy:** Headers use `title-lg` (22sp) for clear sectioning. Secondary metadata (artist names) utilizes `body-md` in `on-surface-60` to create a clear visual step-down from the primary track title.
- **Weighting:** Use Medium weights (500) for labels and interactive titles to distinguish them from standard body copy.

## Layout & Spacing

This design system is built on an **8dp base grid**. All layout decisions must align with this rhythm to maintain a disciplined, structural feel.

- **Grid Model:** A **Fluid Grid** is used for mobile devices, with a fixed horizontal margin of 16dp. For tablet and desktop environments, the content well is capped at a maximum width of 600dp to preserve readability and prevent line-length fatigue.
- **Now Playing Spacing:** The artwork requires significant "breathing room," defined by a 24dp margin. This separates the primary visual asset from the system status bars and the playback controls below.
- **Sectioning:** Vertical gaps between distinct sections (e.g., Recently Played vs. Suggested) should use `spacing-lg` (24dp) to provide clear visual grouping without the need for heavy dividers.

## Elevation & Depth

Elevation in this design system is primarily conveyed through **Tonal Layers** and subtle **Ambient Shadows**. 

- **Tonal Layering:** Instead of relying on shadows alone, depth is established by shifting from `Background` to `Surface` to `Surface Variant`. 
- **Shadows:** Use extra-diffused, low-opacity shadows (8dp elevation) specifically for album artwork on the Now Playing screen. This "lifts" the artwork above the UI, emphasizing its importance.
- **Backdrop Blurs:** Modal Bottom Sheets use a standard Material 3 scrim. However, the Mini Player uses a persistent elevation of 3dp with a solid `Surface` background to ensure it remains legible over scrolling list content.

## Shapes

The shape language is sophisticated and variable, following Material 3 Expressive guidelines. It uses soft, intentional radii to balance the "technical" nature of the app with an approachable feel.

- **Standard Cards:** 12dp radius (`corner.card`).
- **Media/Artwork:** 16dp radius (`corner.artwork`) to emphasize the focus on visual content.
- **Interactive Small Elements:** Chips use an 8dp radius to remain distinct from larger card containers.
- **Full-Bleed Surfaces:** Bottom sheets use a generous 28dp radius for top corners, signaling their status as major structural overlays.

## Components

### Buttons & FABs
- **Primary FAB:** The Haptic FAB is a 40dp Small Floating Action Button. When haptics are active, it uses the `haptic-accent` fill.
- **Transport Controls:** Play/Pause is a 64dp filled circle. Skip buttons are 48dp with transparent backgrounds and standard ripples.

### Input Fields & Sliders
- **Haptic Slider:** Uses the `haptic-accent` for the active track. On drag, the thumb scales to 1.2x and a floating value label appears.
- **Progress Bar:** In standard state, it uses the primary dynamic color. When the haptic engine is processing the current track, the track color switches to `haptic-accent`.

### Cards & Lists
- **MediaCard:** (160x200dp) Features 12dp rounded corners. On press, the card scales to 0.97x to provide tactile feedback.
- **TrackRow:** (56dp height) Leading artwork is 48dp with 8dp corners. The active track in a list should have a background tint of `haptic-accent` at 12% opacity.

### Navigation
- **NavigationBar:** Standard M3 Navigation Bar with 4 items. The active state is indicated by a pill-shaped background behind the icon.
- **Mini Player:** 72dp height, persistent at the bottom of the screen. Features a 2dp linear progress indicator at the very bottom edge.

### Haptic Feedback Visuals
- **Pulse Animation:** When the intensity slider is moved, the artwork border pulses in sync with the haptic motor frequency to provide a visual preview of the physical sensation.