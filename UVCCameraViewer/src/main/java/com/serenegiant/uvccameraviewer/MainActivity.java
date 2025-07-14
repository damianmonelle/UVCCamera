/*
 *  UVCCameraViewer
 *  Simplified UVC camera viewer for Android 10
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 * Modified for Android 10 compatibility
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.serenegiant.uvccameraviewer;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;

public class MainActivity extends Activity {
    private static final boolean DEBUG = true;
    private static final String TAG = "UVCCameraViewer";

    // Camera configuration
    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    private static final int PREVIEW_MODE = 1; // MJPEG

    // UI components
    private CameraViewInterface mCameraView;
    private TextView mStatusText;
    private Button mConnectButton;
    private Button mDisconnectButton;

    // Camera components
    private USBMonitor mUSBMonitor;
    private UVCCameraHandler mCameraHandler;
    private boolean mIsConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.v(TAG, "onCreate:");
        
        setContentView(R.layout.activity_main);
        
        // Initialize UI components
        mCameraView = (CameraViewInterface) findViewById(R.id.camera_view);
        mStatusText = (TextView) findViewById(R.id.status_text);
        mConnectButton = (Button) findViewById(R.id.connect_button);
        mDisconnectButton = (Button) findViewById(R.id.disconnect_button);
        
        // Set up camera view
        mCameraView.setAspectRatio(PREVIEW_WIDTH / (float) PREVIEW_HEIGHT);
        
        // Set up button listeners
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectCamera();
            }
        });
        
        mDisconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectCamera();
            }
        });
        
        // Initialize USB monitor and camera handler
        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mCameraHandler = UVCCameraHandler.createHandler(this, mCameraView,
                UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, PREVIEW_MODE);
        
        updateStatus("Ready to connect camera");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (DEBUG) Log.v(TAG, "onStart:");
        mUSBMonitor.register();
        if (mCameraView != null) {
            mCameraView.onResume();
        }
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.v(TAG, "onStop:");
        // Only close camera if we're actually stopping the app, not just losing focus
        if (isFinishing()) {
            if (mCameraHandler != null) {
                mCameraHandler.close();
            }
            if (mCameraView != null) {
                mCameraView.onPause();
            }
            mUSBMonitor.unregister();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) Log.v(TAG, "onDestroy:");
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        super.onDestroy();
    }

    private void connectCamera() {
        Log.d(TAG, "=== CONNECT CAMERA REQUESTED ===");
        if (DEBUG) Log.v(TAG, "connectCamera:");
        
        // Get list of USB devices
        if (mUSBMonitor != null) {
            Log.d(TAG, "Getting USB device list...");
            List<UsbDevice> deviceList = mUSBMonitor.getDeviceList();
            Log.d(TAG, "Found " + deviceList.size() + " USB devices");
            
            for (UsbDevice device : deviceList) {
                Log.d(TAG, "Device: " + device.getDeviceName() + " VID:" + device.getVendorId() + " PID:" + device.getProductId());
                if (DEBUG) Log.v(TAG, "Found device: " + device.getDeviceName());
                // Request permission for the first UVC device found
                Log.d(TAG, "Requesting permission for device...");
                mUSBMonitor.requestPermission(device);
                break;
            }
        } else {
            Log.e(TAG, "USB Monitor is null!");
        }
        
        updateStatus("Searching for UVC camera...");
    }

    private void disconnectCamera() {
        if (DEBUG) Log.v(TAG, "disconnectCamera:");
        
        if (mCameraHandler != null) {
            mCameraHandler.close();
        }
        
        mIsConnected = false;
        updateUI();
        updateStatus("Camera disconnected");
    }

    private void updateStatus(String status) {
        if (DEBUG) Log.v(TAG, "Status: " + status);
        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mStatusText != null) {
                    mStatusText.setText(status);
                }
                Toast.makeText(MainActivity.this, status, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectButton.setEnabled(!mIsConnected);
                mDisconnectButton.setEnabled(mIsConnected);
            }
        });
    }

    private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            Log.d(TAG, "=== CAMERA ATTACH ===");
            Log.d(TAG, "Device: " + device.getDeviceName() + " VID:" + device.getVendorId() + " PID:" + device.getProductId());
            if (DEBUG) Log.v(TAG, "onAttach: " + device.getDeviceName());
            updateStatus("UVC camera detected: " + device.getDeviceName());
        }

        @Override
        public void onConnect(UsbDevice device, UsbControlBlock ctrlBlock, boolean createNew) {
            Log.d(TAG, "=== CAMERA CONNECTION START ===");
            Log.d(TAG, "Device: " + device.getDeviceName() + " VID:" + device.getVendorId() + " PID:" + device.getProductId());
            Log.d(TAG, "Create new: " + createNew);

            boolean useDirectCamera = true; // Set to true to test direct approach
            if (useDirectCamera) {
                // --- Direct UVCCamera approach (like usbCameraTest) ---
                new Thread(() -> {
                    try {
                        Log.d(TAG, "[Direct] Creating UVCCamera instance...");
                        final UVCCamera camera = new UVCCamera();
                        Log.d(TAG, "[Direct] Opening camera...");
                        camera.open(ctrlBlock);
                        Log.d(TAG, "[Direct] Camera opened successfully");
                        Log.d(TAG, "[Direct] Getting supported sizes...");
                        String supportedSizes = camera.getSupportedSize();
                        Log.d(TAG, "[Direct] Supported sizes: " + supportedSizes);
                        camera.setStatusCallback(new IStatusCallback() {
                            @Override
                            public void onStatus(final int statusClass, final int event, final int selector, final int statusAttribute, final java.nio.ByteBuffer data) {
                                Log.d(TAG, "[Direct] Status callback: class=" + statusClass + " event=" + event + " selector=" + selector);
                            }
                        });
                        camera.setButtonCallback(new IButtonCallback() {
                            @Override
                            public void onButton(final int button, final int state) {
                                Log.d(TAG, "[Direct] Button callback: button=" + button + " state=" + state);
                            }
                        });
                        Log.d(TAG, "[Direct] Setting preview size...");
                        try {
                            camera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
                            Log.d(TAG, "[Direct] MJPEG format set successfully");
                        } catch (final IllegalArgumentException e) {
                            Log.w(TAG, "[Direct] MJPEG format failed, trying YUV mode...", e);
                            try {
                                camera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                                Log.d(TAG, "[Direct] YUV mode set successfully");
                            } catch (final IllegalArgumentException e1) {
                                Log.e(TAG, "[Direct] Both MJPEG and YUV modes failed", e1);
                                camera.destroy();
                                return;
                            }
                        }
                        Log.d(TAG, "[Direct] Getting SurfaceTexture from camera view...");
                        SurfaceTexture surfaceTexture = mCameraView.getSurfaceTexture();
                        if (surfaceTexture != null) {
                            Log.d(TAG, "[Direct] SurfaceTexture obtained successfully");
                            Log.d(TAG, "[Direct] Creating Surface from SurfaceTexture...");
                            Surface cameraSurface = new Surface(surfaceTexture);
                            Log.d(TAG, "[Direct] Surface created: " + cameraSurface);
                            Log.d(TAG, "[Direct] Setting preview display...");
                            camera.setPreviewDisplay(cameraSurface);
                            Log.d(TAG, "[Direct] Preview display set successfully");
                            Log.d(TAG, "[Direct] Starting preview...");
                            camera.startPreview();
                            Log.d(TAG, "[Direct] Preview started successfully");
                            runOnUiThread(() -> {
                                mIsConnected = true;
                                updateUI();
                                updateStatus("[Direct] Camera connected successfully");
                            });
                        } else {
                            Log.e(TAG, "[Direct] SurfaceTexture is null! Cannot start preview");
                            camera.destroy();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "[Direct] Error during camera connection", e);
                        runOnUiThread(() -> updateStatus("[Direct] Error: " + e.getMessage()));
                    }
                }).start();
            } else if (mCameraHandler != null) {
                try {
                    Log.d(TAG, "Opening camera handler...");
                    mCameraHandler.open(ctrlBlock);
                    Log.d(TAG, "Camera handler opened successfully");
                    // Add status and button callbacks if possible (not directly supported by handler)
                    Log.d(TAG, "Getting camera view surface...");
                    SurfaceTexture surfaceTexture = mCameraView.getSurfaceTexture();
                    if (surfaceTexture != null) {
                        Log.d(TAG, "SurfaceTexture obtained successfully");
                        Log.d(TAG, "Creating Surface from SurfaceTexture...");
                        Surface cameraSurface = new Surface(surfaceTexture);
                        Log.d(TAG, "Camera surface created: " + cameraSurface);
                        Log.d(TAG, "Starting preview...");
                        mCameraHandler.startPreview(cameraSurface);
                        Log.d(TAG, "Preview started successfully");
                        mIsConnected = true;
                        updateUI();
                        updateStatus("Camera connected successfully");
                        Log.d(TAG, "=== CAMERA CONNECTION SUCCESS ===");
                    } else {
                        Log.e(TAG, "SurfaceTexture is null! Cannot start preview");
                        updateStatus("Error: Camera surface not available");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during camera connection", e);
                    updateStatus("Error: " + e.getMessage());
                }
            } else {
                Log.e(TAG, "Camera handler is null!");
                updateStatus("Error: Camera handler not initialized");
            }
        }

        @Override
        public void onDisconnect(UsbDevice device, UsbControlBlock ctrlBlock) {
            if (DEBUG) Log.v(TAG, "onDisconnect: " + device.getDeviceName());
            
            if (mCameraHandler != null) {
                mCameraHandler.close();
            }
            
            mIsConnected = false;
            updateUI();
            updateStatus("Camera disconnected");
        }

        @Override
        public void onDettach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDettach: " + device.getDeviceName());
            updateStatus("Camera removed");
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCancel: " + device.getDeviceName());
            updateStatus("Permission denied for camera");
        }
    };
} 