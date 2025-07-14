# Changelog

All notable changes to the USB Camera Test 8 project will be documented in this file.

## [1.0.0] - 2025-07-13

### 🎉 **WORKING VERSION - OSSURET S8 Android 10**

This version is fully functional and tested on OSSURET S8 Android 10 headunit devices.

### ✅ **Fixed**

#### **Critical Permission System Fixes**
- **Target SDK Version Fix**: Changed `targetSdkVersion` from 29 to 27 to resolve USB permission dialog issues
  - **Problem**: USB permission dialogs fail with `targetSdkVersion > 27` on Android 10+
  - **Solution**: Set `targetSdkVersion = 27` in `build.gradle`
  - **Impact**: Permission dialogs now appear and work correctly

- **Custom Permission System**: Implemented robust permission handling with fallback mechanisms
  - **Problem**: Library's internal permission system conflicts with custom handling
  - **Solution**: Custom permission request with comprehensive BroadcastReceiver
  - **Impact**: Reliable permission handling with automatic recovery

- **Permission Fallback Timer**: Added periodic permission status checking
  - **Problem**: BroadcastReceiver sometimes misses permission responses
  - **Solution**: Handler timer that checks permission status every 2 seconds
  - **Impact**: App recovers even if permission response is missed

#### **Device Compatibility Fixes**
- **MACROSILICON Device Support**: Added specific device filter for USB3.0 Capture
  - **Problem**: MACROSILICON USB3.0 Capture (VID:13407, PID:8496) not detected
  - **Solution**: Added device entry to `device_filter.xml`
  - **Impact**: All supported UVC devices are properly detected

- **Comprehensive Device Filtering**: Enhanced device detection
  - **Problem**: Generic UVC devices not properly filtered
  - **Solution**: Added class-based filters for UVC devices (class 239, 255)
  - **Impact**: Broader device compatibility

#### **UI and User Experience Improvements**
- **Manual Permission Check Button**: Added UI button for manual permission verification
  - **Problem**: No way to manually trigger permission checks
  - **Solution**: Added "Check Permission" button in tools layout
  - **Impact**: Users can manually verify permission status

- **Enhanced Diagnostic Logging**: Comprehensive logging for troubleshooting
  - **Problem**: Limited visibility into app behavior
  - **Solution**: Added detailed logging throughout permission and connection flow
  - **Impact**: Better debugging and troubleshooting capabilities

#### **Code Architecture Improvements**
- **USBMonitor Integration**: Fixed conflicts between custom and library permission systems
  - **Problem**: Two conflicting permission systems running simultaneously
  - **Solution**: Modified USBMonitor to skip internal permission requests when custom system is active
  - **Impact**: Eliminated permission conflicts and improved reliability

- **Service Integration**: Added DiagnosticService for background monitoring
  - **Problem**: No background monitoring of USB devices
  - **Solution**: Implemented DiagnosticService with periodic USB device scanning
  - **Impact**: Better device detection and status monitoring

### 🔧 **Technical Changes**

#### **Build Configuration**
- Updated `build.gradle` with target SDK 27
- Enhanced Gradle configuration for better compatibility
- Added proper dependency management

#### **Manifest Updates**
- Added comprehensive permissions for USB, camera, and system access
- Enhanced intent filters for USB device detection
- Added foreground service support

#### **Source Code Enhancements**
- Implemented custom BroadcastReceiver for USB permission handling
- Added fallback permission checking mechanism
- Enhanced error handling and logging
- Improved device connection flow

### 📱 **Device Testing**

#### **Tested and Working**
- **OSSURET S8** - Android 10 headunit device
- **MACROSILICON USB3.0 Capture** - VID:13407, PID:8496
- **Generic UVC cameras** - Class 239, 255 devices

#### **Performance**
- **Permission Grant**: ~1-2 seconds after user approval
- **Camera Initialization**: ~2-3 seconds after permission grant
- **Video Preview**: Immediate display after initialization
- **Memory Usage**: Optimized for headunit devices

### ⚠️ **Known Limitations**

- **Audio Routing**: Not implemented - manual audio routing required
- **Target SDK**: Limited to 27 for USB permission compatibility
- **Android Version**: Primarily tested on Android 10 (OSSURET S8)

### 🚀 **Installation**

#### **Quick Install**
```bash
adb install usbCameraTest8-debug.apk
```

#### **Build from Source**
```bash
./gradlew assembleDebug
adb install usbCameraTest8/build/outputs/apk/debug/usbCameraTest8-debug.apk
```

### 📋 **Usage Instructions**

1. Connect UVC camera to Android device
2. Launch usbCameraTest8 app
3. Grant USB permissions when prompted
4. View camera feed in app interface
5. Use "Check Permission" button if needed

### 🔍 **Troubleshooting**

- **Permission Issues**: Use "Check Permission" button or restart app
- **No Video**: Check USB connection and camera compatibility
- **Build Errors**: Ensure NDK and SDK are properly configured

---

**Note**: This version represents a complete working solution for UVC camera support on OSSURET S8 Android 10 devices. All critical permission and compatibility issues have been resolved. 