# Qibla Compass

A beautiful, high-performance, and offline-capable Qibla Compass application for Android. 

Developed by **Abrar M**.

---

## Key Features

- **Advanced Sensor Fusion:** Uses Android's native `Sensor.TYPE_ROTATION_VECTOR` for tilt-compensated, high-precision heading updates.
- **Display Orientation Support:** Remaps coordinates automatically to support Landscape, Portrait, and reverse orientations.
- **Geomagnetic Correction:** Integrates the NOAA World Magnetic Model to compute declination and translate magnetic readings to True North.
- **Power Efficiency ("Stop-on-Fix"):** Automatically suspends GPS tracking once an accurate coordinate fix is resolved, saving battery power.
- **State-Hoisted Architecture:** Standard Jetpack Compose design pattern with a fully previewable layout compiler in Android Studio.
- **Compass Calibration HUD:** Displays status alerts and instructions on performing figure-8 calibration motions when local magnetic interference is high.

---

## Running Locally

### Prerequisites
- [Android Studio](https://developer.android.com/studio) (Ladybug or newer recommended)
- An Android device running Android 7.0 (API 24) or higher, with Developer Options and USB Debugging enabled.

### Steps to Run
1. Clone or download the repository.
2. Open Android Studio, select **Open**, and select this project directory.
3. Wait for Android Studio to import the gradle configurations.
4. Connect your Android phone via USB.
5. Enable **Install via USB** in your device's **Developer Options** (if using Xiaomi, Redmi, or Oppo/Realme devices).
6. Press the green **Run** button in Android Studio's top toolbar.
