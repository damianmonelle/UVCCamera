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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.DeviceFilter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class DiagnosticService extends Service {
    private static final String TAG = "DiagnosticService";
    private static final String CHANNEL_ID = "DiagnosticServiceChannel";
    private static final int NOTIFICATION_ID = 1001;
    
    private final IBinder binder = new LocalBinder();
    private Handler handler;
    private Runnable diagnosticRunnable;
    private boolean isRunning = false;
    
    public class LocalBinder extends Binder {
        DiagnosticService getService() {
            return DiagnosticService.this;
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "=== DIAGNOSTIC SERVICE CREATED ===");
        
        handler = new Handler();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("USB Camera Diagnostic Service"));
        
        // Start continuous diagnostic monitoring
        startDiagnosticMonitoring();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "=== DIAGNOSTIC SERVICE STARTED ===");
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.i(TAG, "=== DIAGNOSTIC SERVICE DESTROYED ===");
        stopDiagnosticMonitoring();
        super.onDestroy();
    }
    
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "USB Camera Diagnostic",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background diagnostic service for USB camera issues");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification(String content) {
        Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        return builder
            .setContentTitle("USB Camera Diagnostic")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build();
    }
    
    private void startDiagnosticMonitoring() {
        if (isRunning) return;
        
        isRunning = true;
        Log.i(TAG, "Starting continuous diagnostic monitoring");
        
        diagnosticRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    performDiagnosticCheck();
                    handler.postDelayed(this, 5000); // Check every 5 seconds
                }
            }
        };
        
        handler.post(diagnosticRunnable);
    }
    
    private void stopDiagnosticMonitoring() {
        isRunning = false;
        if (handler != null && diagnosticRunnable != null) {
            handler.removeCallbacks(diagnosticRunnable);
        }
        Log.i(TAG, "Stopped diagnostic monitoring");
    }
    
    private void performDiagnosticCheck() {
        try {
            Log.d(TAG, "=== PERFORMING DIAGNOSTIC CHECK ===");
            
            // Check USB devices
            checkUSBDevices();
            
            // Check system state
            checkSystemState();
            
            // Update notification
            updateNotification("Monitoring USB devices...");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during diagnostic check", e);
            writeDiagnosticLog("DIAGNOSTIC_ERROR", null, e);
        }
    }
    
    private void checkUSBDevices() {
        try {
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                Log.w(TAG, "UsbManager is null");
                return;
            }
            
            // Get all USB devices
            List<UsbDevice> allDevices = new java.util.ArrayList<>(usbManager.getDeviceList().values());
            Log.d(TAG, "Total USB devices found: " + allDevices.size());
            
            // Check for UVC devices specifically
            List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
            if (!filters.isEmpty()) {
                DeviceFilter filter = filters.get(0);
                int uvcCount = 0;
                
                for (UsbDevice device : allDevices) {
                    if (filter.matches(device)) {
                        uvcCount++;
                        Log.i(TAG, "UVC Device found: " + device.getDeviceName() + 
                                " VID:" + device.getVendorId() + 
                                " PID:" + device.getProductId());
                        
                        // Check if we have permission
                        boolean hasPermission = usbManager.hasPermission(device);
                        Log.i(TAG, "Has permission: " + hasPermission);
                        
                        writeDiagnosticLog("UVC_DEVICE_CHECK", device, 
                                "hasPermission=" + hasPermission);
                    }
                }
                
                Log.i(TAG, "UVC devices found: " + uvcCount);
                writeDiagnosticLog("UVC_DEVICE_COUNT", null, "count=" + uvcCount);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking USB devices", e);
            writeDiagnosticLog("USB_CHECK_ERROR", null, null, e);
        }
    }
    
    private void checkSystemState() {
        try {
            // Check available memory
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            Log.d(TAG, "Memory - Max: " + maxMemory + ", Used: " + usedMemory + 
                    ", Free: " + freeMemory);
            
            // Check if our app is in foreground
            // This is a simplified check - in a real app you'd use ActivityManager
            
            writeDiagnosticLog("SYSTEM_STATE", null, 
                    "memory_used=" + usedMemory + 
                    ",memory_free=" + freeMemory);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking system state", e);
        }
    }
    
    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(content));
        }
    }
    
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
            File diagnosticLog = new File(getExternalFilesDir(null), "diagnostic_service.log");
            FileWriter writer = new FileWriter(diagnosticLog, true);
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
        } catch (IOException e) {
            Log.e(TAG, "Failed to write diagnostic log", e);
        }
    }
    
    // Public methods for external control
    public void forceDiagnosticCheck() {
        Log.i(TAG, "Force diagnostic check requested");
        performDiagnosticCheck();
    }
    
    public void stopService() {
        Log.i(TAG, "Stop service requested");
        stopSelf();
    }
} 