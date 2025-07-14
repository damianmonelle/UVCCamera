# Audio Routing - UAC Audio Implementation

## ✅ **NEW: UAC Audio Support Implemented**

The USB Camera Test 8 application now includes **UAC (USB Audio Class) audio capture** from UVC devices. This means that cameras with built-in microphones can now provide audio alongside video.

## 🎵 **UAC Audio Features**

### **Automatic Audio Detection**
- **USB Audio Device Scanning**: Automatically detects USB audio devices connected to UVC cameras
- **Device Recognition**: Identifies devices with names containing "USB", "UVC", "Capture", or "Camera"
- **Auto-Selection**: Automatically selects the first available USB audio device

### **Audio Capture Capabilities**
- **Sample Rate**: 48kHz (standard for most UAC devices)
- **Format**: 16-bit PCM, Mono channel
- **Real-time Processing**: Audio data is captured in real-time alongside video
- **Error Handling**: Comprehensive error handling and user feedback

### **Device Compatibility**
- **Microsoft LifeCam Show**: ✅ Tested and working
- **MACROSILICON USB3.0 Capture**: ✅ Detected as "USB-Audio - USB3.0 Capture"
- **Other UVC/UAC Devices**: ✅ Should work with any UVC camera that includes UAC audio

## 🔧 **Technical Implementation**

### **UAC Audio Manager**
The new `UACAudioManager` class provides:
- USB audio device detection and enumeration
- Audio device selection and configuration
- Real-time audio capture with callback interface
- Automatic resource management

### **Integration with Camera**
- Audio capture starts automatically when camera connects
- Audio stops automatically when camera disconnects
- Audio data is available for processing or recording

### **Audio Data Callback**
```java
public interface AudioCaptureCallback {
    void onAudioData(ByteBuffer audioData, int sampleRate, int channelCount);
    void onAudioError(String error);
    void onAudioDeviceConnected(AudioDeviceInfo device);
    void onAudioDeviceDisconnected(AudioDeviceInfo device);
}
```

## 📱 **User Experience**

### **Automatic Operation**
- No user intervention required
- Audio capture starts automatically with video
- Toast notifications inform user of audio status

### **Status Indicators**
- "USB Audio: Recording started" - Audio capture active
- "USB Audio: [Device Name]" - Device detected
- "Audio Error: [Message]" - Error notifications

## 🔍 **Device Analysis**

### **UAC Interface Structure**
Your UVC device includes these audio interfaces:

1. **Interface 2**: Audio Control (Class 1, Subclass 1)
   - Input Terminal: Microphone (0x0201)
   - Feature Unit: Mute and Volume controls
   - Output Terminal: USB Streaming (0x0101)

2. **Interface 3**: Audio Streaming (Class 1, Subclass 2)
   - Format: PCM, 16-bit, 1 channel (mono)
   - Sample Rates: 44.1kHz and 48kHz
   - Endpoint: 0x83 (EP 3 IN) - Isochronous transfer

### **System Recognition**
Android automatically detects the device as:
- Name: "USB-Audio - USB3.0 Capture"
- Type: USB Audio Input Device
- Status: `hasInput: true`

## 🚀 **Usage**

### **For End Users**
1. Connect your UVC camera with built-in microphone
2. Launch USB Camera Test 8
3. Grant USB permission when prompted
4. Audio capture starts automatically with video
5. Audio data is available for processing

### **For Developers**
1. Audio data is provided via callback interface
2. Integrate with your preferred audio processing pipeline
3. Audio can be encoded, recorded, or streamed
4. Full control over audio format and processing

## 📋 **Audio Processing Options**

### **Current Implementation**
- Real-time audio capture
- Raw PCM data available
- Callback-based processing

### **Future Enhancements**
- Audio encoding (AAC, MP3)
- Audio recording to file
- Audio streaming capabilities
- Audio effects and processing
- Multi-channel audio support

## 🔧 **Configuration**

### **Audio Parameters**
- **Sample Rate**: 48000 Hz (configurable)
- **Channels**: 1 (Mono)
- **Format**: PCM 16-bit
- **Buffer Size**: Auto-calculated based on device capabilities

### **Device Filtering**
The system automatically detects USB audio devices by:
- Device type (USB_HEADSET, USB_ACCESSORY, USB_DEVICE)
- Product name containing USB-related keywords
- Device ID patterns

## 🐛 **Troubleshooting**

### **Common Issues**
1. **No Audio Devices Found**
   - Check if camera has built-in microphone
   - Verify USB connection is stable
   - Check Android audio permissions

2. **Audio Recording Fails**
   - Ensure RECORD_AUDIO permission is granted
   - Check if another app is using audio
   - Verify device supports UAC audio

3. **Audio Quality Issues**
   - Check USB connection quality
   - Verify device supports 48kHz sample rate
   - Check for interference from other USB devices

### **Debug Information**
- Audio device detection is logged
- Audio capture status is logged
- Error messages provide detailed information
- Diagnostic logs include audio device information

## 📞 **Support**

### **For Audio Issues**
- Check logcat for audio-related messages
- Verify device supports UAC audio
- Test with different UVC cameras
- Check Android audio system status

### **For Development**
- Audio implementation is modular and extensible
- Callback interface allows custom processing
- Audio parameters are configurable
- Integration with existing video pipeline

---

**Note**: UAC audio support provides a complete audio/video solution for UVC cameras with built-in microphones. The implementation is designed to be robust, user-friendly, and extensible for future enhancements. 