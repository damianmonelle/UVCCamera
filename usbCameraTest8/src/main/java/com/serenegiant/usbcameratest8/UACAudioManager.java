package com.serenegiant.usbcameratest8;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * UAC Audio Manager for handling USB Audio Class audio from UVC devices
 * 
 * This class manages audio capture from USB audio devices that are part of
 * UVC camera systems. It detects USB audio devices and provides audio
 * capture capabilities.
 */
public class UACAudioManager {
    private static final String TAG = "UACAudioManager";
    private static final boolean DEBUG = true;

    // Audio configuration for UAC devices
    private static final int SAMPLE_RATE = 48000; // Most UAC devices support 48kHz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_FACTOR = 2;

    private Context mContext;
    private AudioManager mAudioManager;
    private AudioRecord mAudioRecord;
    private boolean mIsRecording = false;
    private AudioCaptureCallback mCallback;
    private Thread mAudioThread;

    // USB Audio device detection
    private List<AudioDeviceInfo> mUsbAudioDevices = new ArrayList<>();
    private AudioDeviceInfo mSelectedUsbDevice = null;

    public interface AudioCaptureCallback {
        void onAudioData(ByteBuffer audioData, int sampleRate, int channelCount);
        void onAudioError(String error);
        void onAudioDeviceConnected(AudioDeviceInfo device);
        void onAudioDeviceDisconnected(AudioDeviceInfo device);
    }

    public UACAudioManager(Context context) {
        mContext = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        scanForUsbAudioDevices();
    }

    /**
     * Scan for USB audio devices
     */
    public void scanForUsbAudioDevices() {
        mUsbAudioDevices.clear();
        
        if (mAudioManager != null) {
            AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            
            for (AudioDeviceInfo device : devices) {
                if (DEBUG) Log.d(TAG, "Found audio device: " + device.getProductName() + 
                    " type: " + device.getType() + " id: " + device.getId());
                
                // Check if it's a USB audio device
                if (isUsbAudioDevice(device)) {
                    mUsbAudioDevices.add(device);
                    if (DEBUG) Log.i(TAG, "USB Audio device found: " + device.getProductName());
                }
            }
        }
        
        if (DEBUG) Log.i(TAG, "Found " + mUsbAudioDevices.size() + " USB audio devices");
    }

    /**
     * Check if an audio device is a USB audio device
     */
    private boolean isUsbAudioDevice(AudioDeviceInfo device) {
        // USB audio devices typically have these characteristics:
        // - Type is USB_HEADSET, USB_ACCESSORY, or USB_DEVICE
        // - Product name contains "USB" or specific UVC camera names
        // - Device ID indicates USB connection
        
        String productName = device.getProductName().toLowerCase();
        int deviceType = device.getType();
        
        return (deviceType == AudioDeviceInfo.TYPE_USB_HEADSET ||
                deviceType == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                deviceType == AudioDeviceInfo.TYPE_USB_DEVICE ||
                productName.contains("usb") ||
                productName.contains("uvc") ||
                productName.contains("capture") ||
                productName.contains("camera"));
    }

    /**
     * Get list of available USB audio devices
     */
    public List<AudioDeviceInfo> getUsbAudioDevices() {
        return new ArrayList<>(mUsbAudioDevices);
    }

    /**
     * Select a specific USB audio device for recording
     */
    public boolean selectUsbAudioDevice(AudioDeviceInfo device) {
        if (device != null && mUsbAudioDevices.contains(device)) {
            mSelectedUsbDevice = device;
            if (DEBUG) Log.i(TAG, "Selected USB audio device: " + device.getProductName());
            return true;
        }
        return false;
    }

    /**
     * Auto-select the first available USB audio device
     */
    public boolean autoSelectUsbAudioDevice() {
        if (!mUsbAudioDevices.isEmpty()) {
            return selectUsbAudioDevice(mUsbAudioDevices.get(0));
        }
        return false;
    }

    /**
     * Start audio recording from the selected USB audio device
     */
    public boolean startAudioRecording(AudioCaptureCallback callback) {
        if (mIsRecording) {
            if (DEBUG) Log.w(TAG, "Audio recording already in progress");
            return false;
        }

        mCallback = callback;

        // Auto-select device if none selected
        if (mSelectedUsbDevice == null) {
            if (!autoSelectUsbAudioDevice()) {
                if (DEBUG) Log.e(TAG, "No USB audio device available");
                if (mCallback != null) {
                    mCallback.onAudioError("No USB audio device available");
                }
                return false;
            }
        }

        // Calculate buffer size
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            if (DEBUG) Log.e(TAG, "Invalid audio parameters");
            if (mCallback != null) {
                mCallback.onAudioError("Invalid audio parameters");
            }
            return false;
        }

        int bufferSize = minBufferSize * BUFFER_SIZE_FACTOR;

        try {
            // Try to create AudioRecord with USB audio source
            mAudioRecord = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .build();

            if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                if (DEBUG) Log.e(TAG, "Failed to initialize AudioRecord");
                if (mCallback != null) {
                    mCallback.onAudioError("Failed to initialize audio recording");
                }
                return false;
            }

            // Start recording thread
            mIsRecording = true;
            mAudioThread = new Thread(new AudioCaptureRunnable());
            mAudioThread.start();

            if (DEBUG) Log.i(TAG, "Started USB audio recording from: " + mSelectedUsbDevice.getProductName());
            return true;

        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "Error starting audio recording", e);
            if (mCallback != null) {
                mCallback.onAudioError("Error starting audio recording: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Stop audio recording
     */
    public void stopAudioRecording() {
        if (!mIsRecording) {
            return;
        }

        mIsRecording = false;

        if (mAudioThread != null) {
            try {
                mAudioThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                if (DEBUG) Log.w(TAG, "Audio thread interrupted", e);
            }
            mAudioThread = null;
        }

        if (mAudioRecord != null) {
            mAudioRecord.release();
            mAudioRecord = null;
        }

        if (DEBUG) Log.i(TAG, "Stopped USB audio recording");
    }

    /**
     * Audio capture runnable
     */
    private class AudioCaptureRunnable implements Runnable {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            
            if (mAudioRecord == null) {
                return;
            }

            ByteBuffer audioBuffer = ByteBuffer.allocateDirect(4096);
            mAudioRecord.startRecording();

            try {
                while (mIsRecording && mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioBuffer.clear();
                    
                    int bytesRead = mAudioRecord.read(audioBuffer, audioBuffer.capacity());
                    
                    if (bytesRead > 0) {
                        audioBuffer.limit(bytesRead);
                        audioBuffer.position(0);
                        
                        if (mCallback != null) {
                            mCallback.onAudioData(audioBuffer, SAMPLE_RATE, 1);
                        }
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        if (DEBUG) Log.e(TAG, "AudioRecord error: INVALID_OPERATION");
                        break;
                    } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                        if (DEBUG) Log.e(TAG, "AudioRecord error: BAD_VALUE");
                        break;
                    }
                }
            } catch (Exception e) {
                if (DEBUG) Log.e(TAG, "Error in audio capture thread", e);
                if (mCallback != null) {
                    mCallback.onAudioError("Audio capture error: " + e.getMessage());
                }
            } finally {
                if (mAudioRecord != null) {
                    mAudioRecord.stop();
                }
            }
        }
    }

    /**
     * Check if USB audio recording is active
     */
    public boolean isRecording() {
        return mIsRecording;
    }

    /**
     * Get the currently selected USB audio device
     */
    public AudioDeviceInfo getSelectedDevice() {
        return mSelectedUsbDevice;
    }

    /**
     * Release resources
     */
    public void release() {
        stopAudioRecording();
        mUsbAudioDevices.clear();
        mSelectedUsbDevice = null;
        mCallback = null;
    }
} 