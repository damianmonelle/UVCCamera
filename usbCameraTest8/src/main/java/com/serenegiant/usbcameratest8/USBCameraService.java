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
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;

public class USBCameraService extends Service {
    private static final String TAG = "USBCameraService";
    private static final boolean DEBUG = true;
    
    private static final String CHANNEL_ID = "usb_camera_default_id";
    private static final int NOTIFICATION_ID = 1;
    
    private final IBinder mBinder = new LocalBinder();
    private boolean mIsConnected = false;
    private UsbControlBlock mUsbControlBlock;
    
    public class LocalBinder extends Binder {
        USBCameraService getService() {
            return USBCameraService.this;
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate");
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "USB Camera Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("USB Camera Service Channel");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        return builder
            .setContentTitle("USB Camera Service")
            .setContentText("Service is running")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build();
    }
    
    private void updateNotification(String status) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        Notification notification = builder
            .setContentTitle("USB Camera Service")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build();
        
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    // Method to be called from MainActivity when USB device is connected
    public void onUsbDeviceConnected(UsbControlBlock ctrlBlock) {
        if (DEBUG) Log.i(TAG, "onUsbDeviceConnected");
        mUsbControlBlock = ctrlBlock;
        mIsConnected = true;
        updateNotification("USB Camera Connected");
    }
    
    // Method to be called from MainActivity when USB device is disconnected
    public void onUsbDeviceDisconnected() {
        if (DEBUG) Log.i(TAG, "onUsbDeviceDisconnected");
        mUsbControlBlock = null;
        mIsConnected = false;
        updateNotification("USB Camera Disconnected");
    }
    
    public boolean isConnected() {
        return mIsConnected;
    }
    
    public UsbControlBlock getUsbControlBlock() {
        return mUsbControlBlock;
    }
} 