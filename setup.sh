#!/bin/bash

echo "========================================"
echo "USB Camera Test 8 - Setup Script"
echo "========================================"
echo

echo "Checking for Android SDK..."
if [ -z "$ANDROID_HOME" ]; then
    echo "WARNING: ANDROID_HOME environment variable not set"
    echo "Please set ANDROID_HOME to your Android SDK path"
    echo "Example: export ANDROID_HOME=/Users/username/Library/Android/sdk"
    echo
fi

echo "Checking for local.properties..."
if [ ! -f "local.properties" ]; then
    echo "Creating local.properties from template..."
    cp "local.properties.template" "local.properties"
    echo
    echo "IMPORTANT: Please edit local.properties and set your SDK and NDK paths"
    echo
    echo "Required paths:"
    echo "- sdk.dir: Path to your Android SDK"
    echo "- ndk.dir: Path to your Android NDK (r27 recommended)"
    echo
    echo "Example:"
    echo "sdk.dir=/Users/username/Library/Android/sdk"
    echo "ndk.dir=/Users/username/Library/Android/sdk/ndk/27.2.11579242"
    echo
    read -p "Press Enter to continue..."
else
    echo "local.properties already exists"
fi

echo
echo "Checking for connected Android devices..."
adb devices
echo

echo "Setup complete!"
echo
echo "Next steps:"
echo "1. Edit local.properties with your SDK and NDK paths"
echo "2. Run: ./gradlew assembleDebug"
echo "3. Run: adb install usbCameraTest8/build/outputs/apk/debug/usbCameraTest8-debug.apk"
echo
echo "Or install the pre-built APK directly:"
echo "adb install usbCameraTest8-debug.apk"
echo 