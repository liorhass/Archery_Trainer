# Project Plan

Video Trainer: An app that displays real-time processed video from the device's camera.
Main Screen:
- Real-time video display using AndroidExternalSurface.
- Control bar with: Play, Pause, Switch camera, P-Factor selector, Settings.
- Responsive layout: Bottom bar in portrait, right bar in landscape.
Settings Screen: Dummy settings.
Architecture: Single module, ViewModel-driven, Jetpack Compose, Navigation 3.

## Project Brief

# Project Brief: Video Trainer

A high-performance Android application designed for real-time video processing and display, featuring a responsive Material Design 3 interface that adapts seamlessly to device orientation.

## Features
- **Real-time Video Processing**: Low-latency camera feed rendering using `AndroidExternalSurface` to ensure smooth visualization of processed frames.
- **Adaptive Control Bar**: A responsive UI panel that intelligently shifts between a bottom bar in portrait mode and a sidebar in landscape mode for optimal ergonomics.
- **Dynamic Camera Controls**: Integrated playback management (Play/Pause) and effortless toggling between front and rear-facing cameras.
- **P-Factor Selector**: A dedicated UI component for real-time adjustment of processing parameters.
- **Modern Navigation**: Implementation of a settings hub using the latest Navigation 3 architecture for streamlined screen transitions.

## High-Level Technical Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Camera Engine**: CameraX (Camera2 integration)
- **Concurrency**: Kotlin Coroutines & Flow
- **Architecture**: ViewModel-driven state management with Navigation 3
- **Code Generation**: KSP (Kotlin Symbol Processing)
- **Image Loading**: Coil (for UI assets)

## Implementation Steps
**Total Duration:** 10h 20m 52s

### Task_1_Setup_Core_and_Navigation: Configure Material 3 theme with vibrant colors, enable Edge-to-Edge, and implement Navigation between Main and Settings screens using ViewModel for state.
- **Status:** COMPLETED
- **Updates:** Vibrant Material 3 theme implemented with custom color palette and Dynamic Color support.
- **Acceptance Criteria:**
  - App starts with a vibrant M3 theme
  - Navigation between Main and Settings screens works
  - Edge-to-Edge is enabled
- **Duration:** 2h 41m 55s

### Task_2_Camera_Rendering_Engine: Integrate CameraX to capture video frames and implement a custom Composable using AndroidExternalSurface to display the real-time feed.
- **Status:** COMPLETED
- **Updates:** CameraX integrated with Camera-Compose and AndroidExternalSurface (via CameraXViewfinder).
- **Acceptance Criteria:**
  - Camera permission is handled
  - Real-time video feed is visible on the Main screen
  - AndroidExternalSurface is used for rendering
- **Duration:** 2h 32m 41s

### Task_3_Adaptive_UI_Controls: Build the Main screen control bar with Play, Pause, Switch Camera, and P-Factor selector. Ensure the layout is responsive (bottom bar in portrait, right bar in landscape).
- **Status:** COMPLETED
- **Updates:** Re-implemented Navigation 3 setup in MainActivity with MainViewModel managed backstack.
- **Acceptance Criteria:**
  - Control bar adapts to orientation (Bottom in Portrait, Right in Landscape)
  - Play/Pause and Switch Camera buttons are functional
  - P-Factor selector is implemented
- **Duration:** 2h 33m

### Task_4_Run_and_Verify: Perform final build and verification. Ensure app stability, responsiveness, and Material 3 aesthetic.
- **Status:** COMPLETED
- **Updates:** Final build and verification completed.
App is stable, launches correctly, and handles camera permissions.
Real-time video rendering using AndroidExternalSurface (via CameraXViewfinder) verified.
Adaptive UI (Bottom bar in Portrait, Right bar in Landscape) confirmed functional.
Play/Pause, Switch Camera, and P-Factor selector controls are fully operational.
Navigation 3 setup between Main and Settings screens tested and working.
Vibrant Material 3 theme and Edge-to-Edge display implemented and verified.
Critic agent confirmed all features are implemented and the app is stable.
- **Acceptance Criteria:**
  - Project builds successfully
  - App does not crash
  - Existing tests pass
  - UI aligns with requirements (Responsive layout, M3)
- **Duration:** 2h 33m 16s

