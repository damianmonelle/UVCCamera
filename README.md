# USB Camera Test 8 - Working Version for OSSURET S8 Android 10

A fully functional Android UVC (USB Video Class) camera application specifically tested and working on OSSURET S8 Android 10 headunit devices.

## 🎯 **Project Status: WORKING**

✅ **Fully functional** - Camera preview, USB permission handling, and video display working  
✅ **Tested on OSSURET S8** - Android 10 headunit device  
✅ **Permission system resolved** - Target SDK 27 fix implemented  
⚠️ **Audio routing** - Not implemented (manual audio routing required)

## 📋 **Features**

- **UVC Camera Support**: Connect to any USB camera supporting UVC standard
- **Android 10+ Compatible**: Specifically tested on OSSURET S8 Android 10
- **Robust Permission Handling**: Custom permission system with fallback mechanisms
- **Real-time Preview**: Live video feed from connected cameras
- **Auto-connect**: Automatic USB device detection and connection
- **Manual Permission Check**: UI button for manual permission verification
- **Diagnostic Logging**: Comprehensive logging for troubleshooting

## 🔧 **Technical Specifications**

- **Target SDK**: 27 (Android 8.1) - Critical for USB permission compatibility
- **Minimum SDK**: 18 (Android 4.3)
- **Architecture**: arm64-v8a, armeabi-v7a
- **Permissions**: Camera, USB Host, Foreground Service, System Alert Window
- **Dependencies**: libuvccamera, usbCameraCommon, Android Support Libraries

## 📱 **Device Compatibility**

### ✅ **Tested and Working**
- **OSSURET S8** - Android 10 headunit
- **MACROSILICON USB3.0 Capture** - VID:13407, PID:8496
- **Other UVC-compatible cameras** (theoretically supported)

### 🔍 **Requirements**
- Android device with USB host support
- UVC-compatible USB camera
- USB OTG cable (if device doesn't have direct USB port)

## 🚀 **Quick Start**

### **Option 1: Install Pre-built APK**
```bash
# Install the working APK directly
adb install usbCameraTest8-debug.apk
```

### **Option 2: Build from Source**
```bash
# Clone and build
git clone <repository-url>
cd usbCameraTest8_Working
./gradlew assembleDebug
adb install usbCameraTest8/build/outputs/apk/debug/usbCameraTest8-debug.apk
```

## 🔧 **Build Requirements**

- **Android Studio** (latest version recommended)
- **Android SDK** with API level 27+
- **NDK** (r25 or r27 recommended)
- **Java Development Kit** (JDK 17 recommended)

### **local.properties Setup**
Create `local.properties` in the root directory:
```properties
sdk.dir=C:\\Android\\Sdk
ndk.dir=C:\\android-ndk-r27c
```

## 📖 **Usage Instructions**

1. **Connect your UVC camera** to the Android device via USB
2. **Launch the usbCameraTest8 app**
3. **Grant USB permissions** when prompted
4. **View the camera feed** in the app interface
5. **Use "Check Permission" button** if needed for manual permission verification

## 🔍 **Key Technical Fixes Implemented**

### **1. Target SDK Version Fix**
- **Problem**: USB permission dialogs fail with `targetSdkVersion > 27`
- **Solution**: Set `targetSdkVersion = 27` in `build.gradle`
- **Result**: Permission dialogs now appear and work correctly

### **2. Custom Permission System**
- **Problem**: Library's internal permission system conflicts with custom handling
- **Solution**: Implemented custom permission request with fallback timer
- **Result**: Robust permission handling with automatic recovery

### **3. Permission Fallback Mechanism**
- **Problem**: BroadcastReceiver sometimes misses permission responses
- **Solution**: Added periodic permission status checking with Handler timer
- **Result**: App recovers even if permission response is missed

### **4. Comprehensive Device Filtering**
- **Problem**: Specific UVC devices not detected
- **Solution**: Added MACROSILICON device to `device_filter.xml`
- **Result**: All supported UVC devices are properly detected

## 📁 **Project Structure**

```
usbCameraTest8_Working/
├── usbCameraTest8/          # Main application module
│   ├── src/main/
│   │   ├── java/           # Java source code
│   │   ├── res/            # Resources (layouts, values, etc.)
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── libuvccamera/           # Core UVC camera library
│   ├── src/main/
│   │   ├── java/          # Java bindings
│   │   ├── jni/           # Native C/C++ code
│   │   └── res/           # Library resources
│   └── build.gradle
├── usbCameraCommon/        # Shared utilities
│   ├── src/main/
│   │   ├── java/          # Common classes
│   │   └── res/           # Shared resources
│   └── build.gradle
├── build.gradle           # Root build configuration
├── settings.gradle        # Project settings
├── gradle.properties      # Gradle properties
├── usbCameraTest8-debug.apk  # Pre-built working APK
└── README.md              # This file
```

## 🔧 **Configuration Files**

### **Device Filter (usbCameraTest8/src/main/res/xml/device_filter.xml)**
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- MACROSILICON USB3.0 Capture -->
    <usb-device vendor-id="13407" product-id="8496" />
    <!-- Generic UVC devices -->
    <usb-device class="239" />
    <usb-device class="255" />
</resources>
```

### **Permissions (usbCameraTest8/src/main/AndroidManifest.xml)**
- `android.permission.CAMERA`
- `android.permission.RECORD_AUDIO`
- `android.permission.WRITE_EXTERNAL_STORAGE`
- `android.permission.READ_EXTERNAL_STORAGE`
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.SYSTEM_ALERT_WINDOW`
- `android.hardware.usb.host` (feature)

## 🐛 **Troubleshooting**

### **Common Issues**

1. **Camera not detected:**
   - Ensure camera supports UVC standard
   - Check USB connection and cable
   - Verify device has USB host support

2. **Permission denied:**
   - Grant USB permissions when prompted
   - Use "Check Permission" button if needed
   - Check device USB settings

3. **No video display:**
   - Ensure camera is properly connected
   - Check camera compatibility
   - Verify USB permissions are granted

4. **Build errors:**
   - Update Android Studio and SDK tools
   - Ensure NDK is properly configured
   - Check Java version compatibility

### **Debug Logs**
Enable debug logging by setting `DEBUG = true` in MainActivity.java:
```java
private static final boolean DEBUG = true;
```

## 📝 **Known Limitations**

- **Audio routing not implemented** - Manual audio routing required
- **Target SDK 27** - Required for USB permission compatibility
- **Android 10+ specific** - Tested primarily on OSSURET S8

## 🤝 **Contributing**

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 **License**

This project is based on the libuvccamera library and is licensed under the Apache License, Version 2.0.

## 🙏 **Acknowledgments**

- Original libuvccamera library developers (saki4510t)
- UVC standard contributors
- Android USB host API developers
- OSSURET S8 testing and validation

## 📞 **Support**

For issues and questions:
- Create an issue on GitHub
- Check the troubleshooting section
- Review the original libuvccamera documentation

---

**Note**: This is a working version specifically tested on OSSURET S8 Android 10. For other devices, compatibility may vary.
