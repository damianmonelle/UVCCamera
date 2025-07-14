/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
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
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameratest8;

import android.animation.Animator;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.Button;

import com.serenegiant.common.BaseActivity;

import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.utils.ViewAnimationHelper;
import com.serenegiant.widget.CameraViewInterface;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import com.serenegiant.usb.Size;
import android.app.Service;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.view.Window;
import android.view.WindowManager;
import com.serenegiant.usb.DeviceFilter;
import java.util.HashMap;
import android.os.Handler;

public final class MainActivity extends BaseActivity implements CameraDialog.CameraDialogParent {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "MainActivity";
	
	// USB Permission constants
	private static final String ACTION_USB_PERMISSION = "com.serenegiant.usbcameratest8.USB_PERMISSION";

	/**
	 * set true if you want to record movie using MediaSurfaceEncoder
	 * (writing frame data into Surface camera from MediaCodec
	 *  by almost same way as USBCameratest2)
	 * set false if you want to record movie using MediaVideoEncoder
	 */
    private static final boolean USE_SURFACE_ENCODER = false;

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_WIDTH = 640;
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_HEIGHT = 480;
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     * 0:YUYV, other:MJPEG
     */
    private static final int PREVIEW_MODE = 1;

	protected static final int SETTINGS_HIDE_DELAY_MS = 2500;

	/**
	 * for accessing USB
	 */
	private USBMonitor mUSBMonitor;
	/**
	 * Handler to execute camera related methods sequentially on private thread
	 */
	private UVCCameraHandler mCameraHandler;
	/**
	 * for camera preview display
	 */
	private CameraViewInterface mUVCCameraView;
	/**
	 * for open&start / stop&close camera preview
	 */
	private ToggleButton mCameraButton;
	/**
	 * button for start/stop recording
	 */
	private ImageButton mCaptureButton;

	private View mBrightnessButton, mContrastButton, mResolutionButton;
	private View mResetButton;
	private View mToolsLayout, mValueLayout;
	private SeekBar mSettingSeekbar;

	private USBCameraService mUSBCameraService;
	private boolean mBound = false;
	
	// USB Permission handling
	private UsbManager mUsbManager;
	private PendingIntent mPermissionIntent;
	private BroadcastReceiver mUsbReceiver;
	
	private ServiceConnection mConnection = new ServiceConnection() {
	@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			USBCameraService.LocalBinder binder = (USBCameraService.LocalBinder) service;
			mUSBCameraService = binder.getService();
			mBound = true;
			if (DEBUG) Log.d(TAG, "Service connected");
		}
		
		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mBound = false;
			if (DEBUG) Log.d(TAG, "Service disconnected");
		}
	};

	// Timer for permission fallback check
	private Handler mPermissionCheckHandler;
	private Runnable mPermissionCheckRunnable;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Enhanced diagnostic logging
		Log.i(TAG, "=== MAINACTIVITY CREATED ===");
		Log.i(TAG, "Build time: " + new java.util.Date());
		Log.i(TAG, "Device: " + android.os.Build.MODEL);
		Log.i(TAG, "Android: " + android.os.Build.VERSION.RELEASE);
		Log.i(TAG, "SDK: " + android.os.Build.VERSION.SDK_INT);
		
		// Initialize USB Manager and Permission Intent
		initializeUsbPermissionHandling();
		
		// Initialize permission check timer
		mPermissionCheckHandler = new Handler();
		mPermissionCheckRunnable = new Runnable() {
			@Override
			public void run() {
				Log.i(TAG, "=== TIMER: CHECKING PERMISSION STATUS ===");
				checkPermissionAndConnectFallback();
				
				// Schedule next check in 2 seconds
				mPermissionCheckHandler.postDelayed(this, 2000);
			}
		};
		
		// Start diagnostic service
		startService(new Intent(this, DiagnosticService.class));
		
		// Initialize UI components
		mCameraButton = findViewById(R.id.camera_button);
		mCameraButton.setOnClickListener(mOnClickListener);
		mCameraButton.setOnLongClickListener(mOnLongClickListener);
		
		// Add manual permission check button
		Button checkPermissionButton = findViewById(R.id.check_permission_button);
		if (checkPermissionButton != null) {
			checkPermissionButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Log.i(TAG, "=== MANUAL PERMISSION CHECK TRIGGERED ===");
					checkPermissionAndConnectFallback();
				}
			});
		}
		mCaptureButton = (ImageButton)findViewById(R.id.capture_button);
		mCaptureButton.setOnClickListener(mOnClickListener);
		mCaptureButton.setVisibility(View.INVISIBLE);
		mBrightnessButton = findViewById(R.id.brightness_button);
		mBrightnessButton.setOnClickListener(mOnClickListener);
		mContrastButton = findViewById(R.id.contrast_button);
		mContrastButton.setOnClickListener(mOnClickListener);
		mResolutionButton = findViewById(R.id.resolution_button);
		mResolutionButton.setOnClickListener(mOnClickListener);
		mResetButton = findViewById(R.id.reset_button);
		mResetButton.setOnClickListener(mOnClickListener);
		mToolsLayout = findViewById(R.id.tools_layout);
		mValueLayout = findViewById(R.id.value_layout);
		mSettingSeekbar = (SeekBar)findViewById(R.id.setting_seekbar);
		mSettingSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

		// Initialize camera view
		mUVCCameraView = (CameraViewInterface)findViewById(R.id.camera_view);
		mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);
		// Note: CameraViewInterface doesn't have setOnLongClickListener, will handle in layout

		// Initialize USB monitor with enhanced logging
		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
		Log.i(TAG, "USBMonitor initialized");

		// Initialize camera handler
		mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView, USE_SURFACE_ENCODER ? 1 : 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);
		Log.i(TAG, "CameraHandler initialized");

		// Start USB Camera Service
		Intent serviceIntent = new Intent(this, USBCameraService.class);
		startService(serviceIntent);
		bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
		Log.i(TAG, "USBCameraService started and bound");

		// Start Diagnostic Service for continuous monitoring
		Intent diagnosticIntent = new Intent(this, DiagnosticService.class);
		startService(diagnosticIntent);
		Log.i(TAG, "DiagnosticService started");

		// Set up crash handler for better diagnostics
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable ex) {
				Log.e(TAG, "=== CRASH DETECTED ===", ex);
				Log.e(TAG, "Thread: " + thread.getName());
				Log.e(TAG, "Stack trace:", ex);
				
				// Write crash log to file
				try {
					File crashLog = new File(getExternalFilesDir(null), "crash_log.txt");
					java.io.FileWriter writer = new java.io.FileWriter(crashLog, true);
					writer.write("=== CRASH LOG ===\n");
					writer.write("Time: " + new java.util.Date() + "\n");
					writer.write("Thread: " + thread.getName() + "\n");
					writer.write("Exception: " + ex.toString() + "\n");
					java.io.StringWriter sw = new java.io.StringWriter();
					java.io.PrintWriter pw = new java.io.PrintWriter(sw);
					ex.printStackTrace(pw);
					writer.write("Stack trace:\n" + sw.toString() + "\n");
					writer.close();
				} catch (Exception e) {
					Log.e(TAG, "Failed to write crash log", e);
				}
			}
		});

		// Auto-connect functionality - will attempt to connect to any available USB camera
		Log.i(TAG, "Setting up auto-connect functionality");
		startAutoConnectTimer();
	}

	// Initialize USB Permission handling with custom PendingIntent and BroadcastReceiver
	private void initializeUsbPermissionHandling() {
		Log.i(TAG, "=== INITIALIZING USB PERMISSION HANDLING ===");
		
		// Get USB Manager
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		if (mUsbManager == null) {
			Log.e(TAG, "UsbManager is null!");
			return;
		}
		
		// Create PendingIntent for USB permission
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
		Log.i(TAG, "Created PendingIntent for USB permission");
		
		// Create and register BroadcastReceiver for USB permission results
		mUsbReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				Log.i(TAG, "USB BroadcastReceiver received action: " + action);
				
				if (ACTION_USB_PERMISSION.equals(action) || 
					action != null && action.startsWith("com.serenegiant.USB_PERMISSION.")) {
					synchronized (this) {
						UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
						if (device != null) {
							Log.i(TAG, "=== USB PERMISSION RESULT RECEIVED ===");
							Log.i(TAG, "Device: " + device.getDeviceName());
							Log.i(TAG, "Vendor ID: " + device.getVendorId());
							Log.i(TAG, "Product ID: " + device.getProductId());
							Log.i(TAG, "Action: " + action);
							
							if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
								Log.i(TAG, "=== USB PERMISSION GRANTED ===");
								writeDiagnosticLog("USB_PERMISSION_GRANTED", device);
								
								// Stop the permission check timer since we got the response
								stopPermissionCheckTimer();
								
								// Permission granted, now try to connect
								handleUsbDeviceWithPermission(device);
							} else {
								Log.i(TAG, "=== USB PERMISSION DENIED ===");
								writeDiagnosticLog("USB_PERMISSION_DENIED", device);
								
								// Stop the permission check timer since we got the response
								stopPermissionCheckTimer();
								
								Toast.makeText(MainActivity.this, "USB Permission Denied", Toast.LENGTH_LONG).show();
								setCameraButton(false);
							}
						}
					}
				} else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
					Log.i(TAG, "=== USB DEVICE ATTACHED (via BroadcastReceiver) ===");
					UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if (device != null) {
						Log.i(TAG, "Device attached: " + device.getDeviceName());
						writeDiagnosticLog("USB_DEVICE_ATTACHED_BROADCAST", device);
						
						// Check if we have permission for this device
						if (mUsbManager.hasPermission(device)) {
							Log.i(TAG, "Already have permission for attached device");
							handleUsbDeviceWithPermission(device);
						} else {
							Log.i(TAG, "Requesting permission for attached device");
							requestUsbPermission(device);
						}
					}
				} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
					Log.i(TAG, "=== USB DEVICE DETACHED (via BroadcastReceiver) ===");
					UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if (device != null) {
						Log.i(TAG, "Device detached: " + device.getDeviceName());
						writeDiagnosticLog("USB_DEVICE_DETACHED_BROADCAST", device);
						
						// Handle device detachment
						handleUsbDeviceDetachment(device);
					}
				}
			}
		};
		
		// Register the receiver with comprehensive action coverage
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_USB_PERMISSION);
		// Also listen for USBMonitor's permission actions (in case they're still triggered)
		filter.addAction("com.serenegiant.USB_PERMISSION");
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(mUsbReceiver, filter);
		
		Log.i(TAG, "USB BroadcastReceiver registered successfully with comprehensive action coverage");
	}

	// Start permission check timer
	private void startPermissionCheckTimer() {
		Log.i(TAG, "Starting permission check timer");
		mPermissionCheckHandler.postDelayed(mPermissionCheckRunnable, 1000); // Start in 1 second
	}
	
	// Stop permission check timer
	private void stopPermissionCheckTimer() {
		Log.i(TAG, "Stopping permission check timer");
		mPermissionCheckHandler.removeCallbacks(mPermissionCheckRunnable);
	}

	// Request USB permission with fallback timer
	private void requestUsbPermission(UsbDevice device) {
		Log.i(TAG, "=== REQUESTING USB PERMISSION ===");
		Log.i(TAG, "Device: " + device.getDeviceName());
		
		writeDiagnosticLog("REQUESTING_USB_PERMISSION", device);
		
		try {
			// Request permission
			mUsbManager.requestPermission(device, mPermissionIntent);
			Log.i(TAG, "USB permission request sent successfully");
			
			// Start timer to check for permission status as fallback
			startPermissionCheckTimer();
			
		} catch (Exception e) {
			Log.e(TAG, "Error requesting USB permission", e);
			writeDiagnosticLog("USB_PERMISSION_REQUEST_ERROR", device, e);
		}
	}

	// Handle USB device when we have permission
	private void handleUsbDeviceWithPermission(UsbDevice device) {
		Log.i(TAG, "=== HANDLING USB DEVICE WITH PERMISSION ===");
		Log.i(TAG, "Device: " + device.getDeviceName());
		
		writeDiagnosticLog("HANDLING_USB_DEVICE_WITH_PERMISSION", device);
		
		try {
			// Use USBMonitor to process the device connection
			if (mUSBMonitor != null) {
				// Check if device matches our filters
				List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
				boolean deviceMatches = false;
				
				for (DeviceFilter filter : filters) {
					if (filter.matches(device)) {
						deviceMatches = true;
						Log.i(TAG, "Device matches filter: " + filter);
						break;
					}
				}
				
				if (deviceMatches) {
					Log.i(TAG, "Device matches our filters, processing connection with USBMonitor");
					
					// Call USBMonitor's method to process the device with permission
					// This avoids the permission conflict by not calling requestPermission again
					mUSBMonitor.processDeviceWithPermission(device);
				} else {
					Log.w(TAG, "Device does not match our filters");
					Toast.makeText(this, "Device not supported", Toast.LENGTH_SHORT).show();
				}
			} else {
				Log.e(TAG, "USBMonitor is null");
			}
		} catch (Exception e) {
			Log.e(TAG, "Error handling USB device with permission", e);
			writeDiagnosticLog("HANDLING_USB_DEVICE_ERROR", device, e);
		}
	}

	// Fallback method to check permission status and handle device connection
	private void checkPermissionAndConnectFallback() {
		Log.i(TAG, "=== CHECKING PERMISSION FALLBACK ===");
		
		// Get all USB devices
		HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
		if (deviceList.isEmpty()) {
			Log.i(TAG, "No USB devices found in fallback check");
			return;
		}
		
		for (UsbDevice device : deviceList.values()) {
			Log.i(TAG, "Checking device: " + device.getDeviceName());
			
			// Check if we have permission for this device
			if (mUsbManager.hasPermission(device)) {
				Log.i(TAG, "=== FALLBACK: PERMISSION GRANTED FOR DEVICE ===");
				Log.i(TAG, "Device: " + device.getDeviceName());
				writeDiagnosticLog("FALLBACK_PERMISSION_GRANTED", device);
				
				// Handle the device with permission
				handleUsbDeviceWithPermission(device);
				return;
			} else {
				Log.i(TAG, "No permission for device: " + device.getDeviceName());
			}
		}
		
		Log.i(TAG, "No devices with permission found in fallback check");
	}

	// Handle USB device detachment
	private void handleUsbDeviceDetachment(UsbDevice device) {
		Log.i(TAG, "=== HANDLING USB DEVICE DETACHMENT ===");
		Log.i(TAG, "Device: " + device.getDeviceName());
		
		writeDiagnosticLog("HANDLING_USB_DEVICE_DETACHMENT", device);
		
		// Close camera if it's open
		if (mCameraHandler != null && mCameraHandler.isOpened()) {
			Log.i(TAG, "Closing camera due to device detachment");
			mCameraHandler.close();
			setCameraButton(false);
			updateItems();
		}
		
		// Notify service
		if (mBound && mUSBCameraService != null) {
			mUSBCameraService.onUsbDeviceDisconnected();
		}
	}

	// Auto-connect timer to automatically attempt connection
	private void startAutoConnectTimer() {
		Log.i(TAG, "Starting auto-connect timer");
		new android.os.Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				Log.i(TAG, "Auto-connect timer triggered");
				attemptAutoConnect();
			}
		}, 3000); // Wait 3 seconds after app starts
	}

	private void attemptAutoConnect() {
		Log.i(TAG, "=== ATTEMPTING AUTO-CONNECT ===");
		
		// Check if USB manager is ready
		if (mUsbManager == null) {
			Log.e(TAG, "UsbManager is null, cannot auto-connect");
			return;
		}

		// Get list of available devices
		List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
		if (filters.isEmpty()) {
			Log.w(TAG, "No device filters found");
			return;
		}
		
		// Get all USB devices
		List<UsbDevice> allDevices = new java.util.ArrayList<>(mUsbManager.getDeviceList().values());
		Log.i(TAG, "Total USB devices found: " + allDevices.size());
		
		// Filter devices that match our criteria
		List<UsbDevice> matchingDevices = new java.util.ArrayList<>();
		for (UsbDevice device : allDevices) {
			for (DeviceFilter filter : filters) {
				if (filter.matches(device)) {
					matchingDevices.add(device);
					Log.i(TAG, "Found matching device: " + device.getDeviceName() + 
							" VID:" + device.getVendorId() + 
							" PID:" + device.getProductId() + 
							" Class:" + device.getDeviceClass());
					break;
				}
			}
		}

		// If we have matching devices, try to connect to the first one
		if (!matchingDevices.isEmpty()) {
			UsbDevice targetDevice = matchingDevices.get(0);
			Log.i(TAG, "Attempting to auto-connect to: " + targetDevice.getDeviceName());
			
			try {
				// Check if we already have permission
				if (mUsbManager.hasPermission(targetDevice)) {
					Log.i(TAG, "Already have permission, attempting direct connection");
					handleUsbDeviceWithPermission(targetDevice);
				} else {
					Log.i(TAG, "Requesting permission for auto-connect");
					requestUsbPermission(targetDevice);
				}
			} catch (Exception e) {
				Log.e(TAG, "Auto-connect failed", e);
			}
		} else {
			Log.i(TAG, "No matching USB devices found for auto-connect");
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		if (DEBUG) Log.v(TAG, "onStart:");
		if (mUSBMonitor != null) {
		mUSBMonitor.register();
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		if (DEBUG) Log.v(TAG, "onStop:");
		if (mUSBMonitor != null) {
			mUSBMonitor.unregister();
		}
	}

	@Override
	protected void onDestroy() {
		Log.i(TAG, "=== ON DESTROY ===");
		
		// Stop permission check timer
		stopPermissionCheckTimer();
		
		// Unregister USB receiver
		if (mUsbReceiver != null) {
			try {
				unregisterReceiver(mUsbReceiver);
				Log.i(TAG, "USB BroadcastReceiver unregistered");
			} catch (Exception e) {
				Log.e(TAG, "Error unregistering USB receiver", e);
			}
		}
		
		// Stop diagnostic service
		stopService(new Intent(this, DiagnosticService.class));
		
		super.onDestroy();
	}

	/**
	 * event handler when click camera / capture button
	 */
	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			switch (view.getId()) {
			case R.id.capture_button:
				if (mCameraHandler.isOpened()) {
					if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
						if (!mCameraHandler.isRecording()) {
							mCaptureButton.setColorFilter(0xffff0000);	// turn red
							mCameraHandler.startRecording();
						} else {
							mCaptureButton.setColorFilter(0);	// return to default color
							mCameraHandler.stopRecording();
						}
					}
				}
				break;
			case R.id.brightness_button:
				showSettings(UVCCamera.PU_BRIGHTNESS);
				break;
			case R.id.contrast_button:
				showSettings(UVCCamera.PU_CONTRAST);
				break;
			case R.id.resolution_button:
				showResolutionDialog();
				break;
			case R.id.reset_button:
				resetSettings();
				break;
			}
		}
	};

	private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener
		= new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
			switch (compoundButton.getId()) {
			case R.id.camera_button:
				if (isChecked && !mCameraHandler.isOpened()) {
					CameraDialog.showDialog(MainActivity.this);
				} else {
					mCameraHandler.close();
					setCameraButton(false);
				}
				break;
			}
		}
	};

	/**
	 * capture still image when you long click on preview image(not on buttons)
	 */
	private final OnLongClickListener mOnLongClickListener = new OnLongClickListener() {
		@Override
		public boolean onLongClick(final View view) {
			switch (view.getId()) {
			case R.id.camera_view:
				if (mCameraHandler.isOpened()) {
					if (checkPermissionWriteExternalStorage()) {
						mCameraHandler.captureStill();
					}
					return true;
				}
			}
			return false;
		}
	};

	private void setCameraButton(final boolean isOn) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mCameraButton != null) {
					try {
						mCameraButton.setOnCheckedChangeListener(null);
						mCameraButton.setChecked(isOn);
					} finally {
						mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
					}
				}
				if (!isOn && (mCaptureButton != null)) {
					mCaptureButton.setVisibility(View.INVISIBLE);
				}
			}
		}, 0);
		updateItems();
	}

	private void startPreview() {
		Log.d(TAG, "startPreview: Starting preview");
		try {
			final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
			if (st == null) {
				Log.e(TAG, "startPreview: SurfaceTexture is null");
				return;
			}
			Log.d(TAG, "startPreview: Got SurfaceTexture, creating Surface");
			Surface surface = new Surface(st);
			if (!surface.isValid()) {
				Log.e(TAG, "startPreview: Surface is not valid");
				return;
			}
			Log.d(TAG, "startPreview: Created Surface, calling handler.startPreview");
			mCameraHandler.startPreview(surface);
			Log.d(TAG, "startPreview: Handler.startPreview called successfully");
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Log.d(TAG, "startPreview: Showing capture button");
					mCaptureButton.setVisibility(View.VISIBLE);
				}
			});
			Log.d(TAG, "startPreview: Updating items");
			updateItems();
			Log.d(TAG, "startPreview: Preview start completed successfully");
		} catch (Exception e) {
			Log.e(TAG, "startPreview: Exception during preview start", e);
		}
	}

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			Log.i(TAG, "=== USB DEVICE ATTACHED ===");
			Log.i(TAG, "Device: " + device.getDeviceName());
			Log.i(TAG, "Vendor ID: " + device.getVendorId() + " (0x" + Integer.toHexString(device.getVendorId()) + ")");
			Log.i(TAG, "Product ID: " + device.getProductId() + " (0x" + Integer.toHexString(device.getProductId()) + ")");
			Log.i(TAG, "Device Class: " + device.getDeviceClass());
			Log.i(TAG, "Manufacturer: " + device.getManufacturerName());
			Log.i(TAG, "Product: " + device.getProductName());
			
			// Write to diagnostic file
			writeDiagnosticLog("USB_DEVICE_ATTACHED", device);
			
			Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			Log.i(TAG, "=== USB DEVICE CONNECTED ===");
			Log.i(TAG, "Device: " + device.getDeviceName());
			Log.i(TAG, "Control Block: " + ctrlBlock);
			Log.i(TAG, "Create New: " + createNew);
			Log.i(TAG, "Camera Handler: " + mCameraHandler);
			
			// Write to diagnostic file
			writeDiagnosticLog("USB_DEVICE_CONNECTED", device);
			
			try {
				// Notify the service about the USB connection
				if (mBound && mUSBCameraService != null) {
					Log.i(TAG, "Notifying service of USB connection");
					mUSBCameraService.onUsbDeviceConnected(ctrlBlock);
				} else {
					Log.w(TAG, "Service not bound or null, skipping service notification");
				}
				
				// Open the camera with the USB control block
				Log.i(TAG, "Opening camera with control block");
			mCameraHandler.open(ctrlBlock);
				Log.i(TAG, "Camera opened successfully");
				
				Log.i(TAG, "Starting preview");
			startPreview();
				Log.i(TAG, "Preview started successfully");
				
				Log.i(TAG, "Updating UI items");
			updateItems();
				Log.i(TAG, "UI updated successfully");
				
				Log.i(TAG, "=== USB CONNECTION COMPLETED SUCCESSFULLY ===");
				
			} catch (Exception e) {
				Log.e(TAG, "Error during USB connection", e);
				writeDiagnosticLog("USB_CONNECTION_ERROR", device, e);
			}
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			Log.i(TAG, "=== USB DEVICE DISCONNECTED ===");
			Log.i(TAG, "Device: " + device.getDeviceName());
			Log.i(TAG, "Control Block: " + ctrlBlock);
			
			// Write to diagnostic file
			writeDiagnosticLog("USB_DEVICE_DISCONNECTED", device);
			
			try {
				// Notify the service about the USB disconnection
				if (mBound && mUSBCameraService != null) {
					mUSBCameraService.onUsbDeviceDisconnected();
				}
				
			if (mCameraHandler != null) {
				queueEvent(new Runnable() {
					@Override
					public void run() {
							Log.i(TAG, "Closing camera handler");
						mCameraHandler.close();
					}
				}, 0);
				setCameraButton(false);
				updateItems();
			}
			} catch (Exception e) {
				Log.e(TAG, "Error during USB disconnection", e);
		}
		}
		
		@Override
		public void onDettach(final UsbDevice device) {
			Log.i(TAG, "=== USB DEVICE DETACHED ===");
			Log.i(TAG, "Device: " + device.getDeviceName());
			
			// Write to diagnostic file
			writeDiagnosticLog("USB_DEVICE_DETACHED", device);
			
			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCancel(final UsbDevice device) {
			Log.i(TAG, "=== USB PERMISSION CANCELLED ===");
			Log.i(TAG, "Device: " + device.getDeviceName());
			Log.i(TAG, "This indicates the USB permission request failed or was denied");
			
			// Write to diagnostic file
			writeDiagnosticLog("USB_PERMISSION_CANCELLED", device);
			
			setCameraButton(false);
		}
	};

	/**
	 * to access from CameraDialog
	 * @return
	 */
	@Override
	public USBMonitor getUSBMonitor() {
		return mUSBMonitor;
	}

	@Override
	public void onDialogResult(boolean canceled) {
		Log.i(TAG, "=== DIALOG RESULT ===");
		Log.i(TAG, "Canceled: " + canceled);
		
		// Write to diagnostic file
		writeDiagnosticLog("DIALOG_RESULT", null, "canceled=" + canceled);
		
		if (canceled) {
			Log.i(TAG, "Dialog was canceled, setting camera button to false");
			setCameraButton(false);
		} else {
			Log.i(TAG, "Dialog was confirmed, permission should be granted");
		}
	}

	// Diagnostic logging methods
	    private void writeDiagnosticLog(String event, UsbDevice device) {
        writeDiagnosticLog(event, device, null, null);
    }

    private void writeDiagnosticLog(String event, UsbDevice device, String extra) {
        writeDiagnosticLog(event, device, extra, null);
    }

    private void writeDiagnosticLog(String event, UsbDevice device, Exception exception) {
        writeDiagnosticLog(event, device, null, exception);
    }

	private void writeDiagnosticLog(String event, UsbDevice device, String extra, Exception exception) {
		try {
			File diagnosticLog = new File(getExternalFilesDir(null), "usb_diagnostic.log");
			java.io.FileWriter writer = new java.io.FileWriter(diagnosticLog, true);
			writer.write("=== " + event + " ===\n");
			writer.write("Time: " + new java.util.Date() + "\n");
			writer.write("Thread: " + Thread.currentThread().getName() + "\n");
			
			if (device != null) {
				writer.write("Device: " + device.getDeviceName() + "\n");
				writer.write("Vendor ID: " + device.getVendorId() + " (0x" + Integer.toHexString(device.getVendorId()) + ")\n");
				writer.write("Product ID: " + device.getProductId() + " (0x" + Integer.toHexString(device.getProductId()) + ")\n");
				writer.write("Device Class: " + device.getDeviceClass() + "\n");
				writer.write("Manufacturer: " + device.getManufacturerName() + "\n");
				writer.write("Product: " + device.getProductName() + "\n");
			}
			
			if (extra != null) {
				writer.write("Extra: " + extra + "\n");
			}
			
			if (exception != null) {
				writer.write("Exception: " + exception.toString() + "\n");
				java.io.StringWriter sw = new java.io.StringWriter();
				java.io.PrintWriter pw = new java.io.PrintWriter(sw);
				exception.printStackTrace(pw);
				writer.write("Stack trace:\n" + sw.toString() + "\n");
			}
			
			writer.write("\n");
			writer.close();
		} catch (Exception e) {
			Log.e(TAG, "Failed to write diagnostic log", e);
		}
	}

//================================================================================
	private boolean isActive() {
		return mCameraHandler != null && mCameraHandler.isOpened();
	}

	private boolean checkSupportFlag(final int flag) {
		return mCameraHandler != null && mCameraHandler.checkSupportFlag(flag);
	}

	private int getValue(final int flag) {
		return mCameraHandler != null ? mCameraHandler.getValue(flag) : 0;
	}

	private int setValue(final int flag, final int value) {
		return mCameraHandler != null ? mCameraHandler.setValue(flag, value) : 0;
	}

	private int resetValue(final int flag) {
		return mCameraHandler != null ? mCameraHandler.resetValue(flag) : 0;
	}

	private void updateItems() {
		runOnUiThread(mUpdateItemsOnUITask, 100);
	}

	private final Runnable mUpdateItemsOnUITask = new Runnable() {
		@Override
		public void run() {
			if (isFinishing()) return;
			final int visible_active = isActive() ? View.VISIBLE : View.INVISIBLE;
			mToolsLayout.setVisibility(visible_active);
			mBrightnessButton.setVisibility(
		    	checkSupportFlag(UVCCamera.PU_BRIGHTNESS)
		    	? visible_active : View.INVISIBLE);
			mContrastButton.setVisibility(
		    	checkSupportFlag(UVCCamera.PU_CONTRAST)
		    	? visible_active : View.INVISIBLE);
			mResolutionButton.setVisibility(visible_active);
		}
	};

	private int mSettingMode = -1;
	/**
	 * 設定画面を表示
	 * @param mode
	 */
	private final void showSettings(final int mode) {
		if (DEBUG) Log.v(TAG, String.format("showSettings:%08x", mode));
		hideSetting(false);
		if (isActive()) {
			switch (mode) {
			case UVCCamera.PU_BRIGHTNESS:
			case UVCCamera.PU_CONTRAST:
				mSettingMode = mode;
				mSettingSeekbar.setProgress(getValue(mode));
				ViewAnimationHelper.fadeIn(mValueLayout, -1, 0, mViewAnimationListener);
				break;
			}
		}
	}

	private void resetSettings() {
		if (isActive()) {
			switch (mSettingMode) {
			case UVCCamera.PU_BRIGHTNESS:
			case UVCCamera.PU_CONTRAST:
				mSettingSeekbar.setProgress(resetValue(mSettingMode));
				break;
			}
		}
		mSettingMode = -1;
		ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
	}

	/**
	 * Show resolution selection dialog
	 */
	private void showResolutionDialog() {
		if (!isActive()) {
			Toast.makeText(this, "Camera not connected", Toast.LENGTH_SHORT).show();
			return;
		}

		final List<Size> supportedSizes = mCameraHandler.getSupportedResolutions();
		if (supportedSizes == null || supportedSizes.isEmpty()) {
			Toast.makeText(this, "No supported resolutions found", Toast.LENGTH_SHORT).show();
			return;
		}

		// Create resolution strings for dialog
		final String[] resolutionStrings = new String[supportedSizes.size()];
		for (int i = 0; i < supportedSizes.size(); i++) {
			Size size = supportedSizes.get(i);
			resolutionStrings[i] = size.width + "x" + size.height;
		}

		// Get current resolution
		final Size currentSize = mCameraHandler.getCurrentPreviewSize();
		int currentIndex = 0;
		if (currentSize != null) {
			for (int i = 0; i < supportedSizes.size(); i++) {
				Size size = supportedSizes.get(i);
				if (size.width == currentSize.width && size.height == currentSize.height) {
					currentIndex = i;
					break;
				}
			}
		}

		// Show resolution selection dialog
		new android.app.AlertDialog.Builder(this)
			.setTitle("Select Resolution")
			.setSingleChoiceItems(resolutionStrings, currentIndex, new android.content.DialogInterface.OnClickListener() {
				@Override
				public void onClick(android.content.DialogInterface dialog, int which) {
					Size selectedSize = supportedSizes.get(which);
					changeResolution(selectedSize);
					dialog.dismiss();
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	/**
	 * Change camera resolution
	 */
	private void changeResolution(final Size newSize) {
		if (!isActive()) {
			Log.e(TAG, "changeResolution: Camera not active");
			return;
		}

		Log.d(TAG, "changeResolution: Starting resolution change to " + newSize.width + "x" + newSize.height);

		try {
			// Stop preview before changing resolution
			Log.d(TAG, "changeResolution: Stopping preview");
			mCameraHandler.stopPreview();
			
			// Change resolution using handler
			Log.d(TAG, "changeResolution: Changing resolution via handler");
			boolean success = mCameraHandler.changeResolution(newSize.width, newSize.height, PREVIEW_MODE);
			
			if (success) {
				Log.d(TAG, "changeResolution: Resolution change successful");
				
				// Update camera view aspect ratio
				Log.d(TAG, "changeResolution: Updating aspect ratio to " + (newSize.width / (float) newSize.height));
				mUVCCameraView.setAspectRatio(newSize.width / (float) newSize.height);
				
				// Add a small delay to ensure the camera has time to process the resolution change
				Log.d(TAG, "changeResolution: Adding delay before restarting preview");
				new android.os.Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						// Restart preview
						Log.d(TAG, "changeResolution: Restarting preview after delay");
						startPreview();
						
						Toast.makeText(MainActivity.this, "Resolution changed to " + newSize.width + "x" + newSize.height, Toast.LENGTH_SHORT).show();
					}
				}, 200); // 200ms delay
			} else {
				Log.e(TAG, "changeResolution: Resolution change failed");
				Toast.makeText(this, "Failed to change resolution", Toast.LENGTH_SHORT).show();
			}
			
		} catch (Exception e) {
			Log.e(TAG, "changeResolution: Exception during resolution change", e);
			Toast.makeText(this, "Failed to change resolution: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			
			// Try to restore previous resolution
			try {
				Log.d(TAG, "changeResolution: Attempting to restore previous resolution");
				mCameraHandler.changeResolution(PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);
				mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float) PREVIEW_HEIGHT);
				startPreview();
			} catch (Exception ex) {
				Log.e(TAG, "changeResolution: Failed to restore resolution", ex);
			}
		}
	}

	/**
	 * 設定画面を非表示にする
	 * @param fadeOut trueならばフェードアウトさせる, falseなら即座に非表示にする
	 */
	protected final void hideSetting(final boolean fadeOut) {
		removeFromUiThread(mSettingHideTask);
		if (fadeOut) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
				}
			}, 0);
		} else {
			try {
				mValueLayout.setVisibility(View.GONE);
			} catch (final Exception e) {
				// ignore
			}
			mSettingMode = -1;
		}
	}

	protected final Runnable mSettingHideTask = new Runnable() {
		@Override
		public void run() {
			hideSetting(true);
		}
	};

	/**
	 * 設定値変更用のシークバーのコールバックリスナー
	 */
	private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
			// 設定が変更された時はシークバーの非表示までの時間を延長する
			if (fromUser) {
				runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
			}
		}

		@Override
		public void onStartTrackingTouch(final SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(final SeekBar seekBar) {
			// シークバーにタッチして値を変更した時はonProgressChangedへ
			// 行かないみたいなのでここでも非表示までの時間を延長する
			runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
			if (isActive() && checkSupportFlag(mSettingMode)) {
				switch (mSettingMode) {
				case UVCCamera.PU_BRIGHTNESS:
				case UVCCamera.PU_CONTRAST:
					setValue(mSettingMode, seekBar.getProgress());
					break;
				}
			}	// if (active)
		}
	};

	private final ViewAnimationHelper.ViewAnimationListener
		mViewAnimationListener = new ViewAnimationHelper.ViewAnimationListener() {
		@Override
		public void onAnimationStart(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
//			if (DEBUG) Log.v(TAG, "onAnimationStart:");
		}

		@Override
		public void onAnimationEnd(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
			final int id = target.getId();
			switch (animationType) {
			case ViewAnimationHelper.ANIMATION_FADE_IN:
			case ViewAnimationHelper.ANIMATION_FADE_OUT:
			{
				final boolean fadeIn = animationType == ViewAnimationHelper.ANIMATION_FADE_IN;
				if (id == R.id.value_layout) {
					if (fadeIn) {
						runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
					} else {
						mValueLayout.setVisibility(View.GONE);
						mSettingMode = -1;
					}
				} else if (!fadeIn) {
//					target.setVisibility(View.GONE);
				}
				break;
			}
			}
		}

		@Override
		public void onAnimationCancel(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
//			if (DEBUG) Log.v(TAG, "onAnimationStart:");
		}
	};

	/**
	 * Handle USB device attachment safely
	 */
	private void handleUsbDeviceAttachment(UsbDevice device) {
		if (DEBUG) Log.d(TAG, "handleUsbDeviceAttachment: " + device.getDeviceName());
		Log.i(TAG, "Requesting USB permission for device: " + device);
		try {
			// Request permission for the device
			if (mUSBMonitor != null) {
				mUSBMonitor.requestPermission(device);
			} else {
				Log.w(TAG, "USBMonitor is null, cannot request permission");
			}
		} catch (Exception e) {
			Log.e(TAG, "Error handling USB device attachment", e);
		}
	}

}
