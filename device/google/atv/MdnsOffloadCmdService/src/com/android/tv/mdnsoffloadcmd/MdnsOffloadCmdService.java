/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.mdnsoffloadcmd;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.HexFormat;

import device.google.atv.mdns_offload.IMdnsOffloadManager;

public class MdnsOffloadCmdService extends Service {
    private static final String TAG = MdnsOffloadCmdService.class.getSimpleName();

    private static final String ACTION_OFFLOAD_COMMAND =
            "com.android.tv.mdnsoffloadcmd.OFFLOAD_COMMAND";

    private static final String CHANNEL_ID = "MdnsOffloadCmdService";

    private IMdnsOffloadManager mMdnsOffloadManagerService;
    private IBinder mBinder;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        //We don't want any app to bind to this service.
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBinder = new Binder();
        setupCommandBroadcastReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setSmallIcon(R.drawable.notification_template_icon_low_bg)
                .build();

        bindMdnsOffloadManager();

        startForeground(1, notification);
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    private void registerProtocolResponse(String rawHexPacket, String iface) {
        Log.d(TAG, "Registering on iface{" + iface + "} :" + rawHexPacket);
        IMdnsOffloadManager.OffloadServiceInfo info =
                new IMdnsOffloadManager.OffloadServiceInfo();
        info.rawOffloadPacket = HexFormat.of().parseHex(rawHexPacket);
        try {
            if (mMdnsOffloadManagerService == null) {
                Log.e(TAG, "Offload Manager not connected");
                return;
            }
            int recordKey = mMdnsOffloadManagerService.addProtocolResponses(iface, info, mBinder);
            Log.d(TAG, "Packet offloaded with recordKey=" + recordKey);
        } catch (RemoteException e) {
            Log.e(TAG, "Error while registering debug packet", e);
        }
    }

    private void registerPassthrough(String qname, String iface) {
        Log.d(TAG, "Registering on iface{" + iface + "} passthrough:" + qname);
        try {
            if (mMdnsOffloadManagerService == null) {
                Log.e(TAG, "Offload Manager not connected");
                return;
            }
            mMdnsOffloadManagerService.addToPassthroughList(iface, qname, mBinder);
        } catch (RemoteException e) {
            Log.e(TAG, "Error while adding passthrough qname", e);
        }
    }

    private void removeProtocolResponse(int recordKey) {
        IMdnsOffloadManager.OffloadServiceInfo info =
                new IMdnsOffloadManager.OffloadServiceInfo();
        try {
            if (mMdnsOffloadManagerService == null) {
                Log.e(TAG, "Offload Manager not connected");
                return;
            }
            mMdnsOffloadManagerService.removeProtocolResponses(recordKey, mBinder);
            Log.d(TAG, "Removed record " + recordKey);
        } catch (RemoteException e) {
            Log.e(TAG, "Error while registering debug packet", e);
        }
    }

    private void removePassthrough(String qname, String iface) {
        Log.d(TAG, "Removing passthrough:" + qname + " on iface{" + iface + "}");
        try {
            if (mMdnsOffloadManagerService == null) {
                Log.e(TAG, "Offload Manager not connected");
                return;
            }
            mMdnsOffloadManagerService.removeFromPassthroughList(iface, qname, mBinder);
        } catch (RemoteException e) {
            Log.e(TAG, "Error while removing passthrough qname", e);
        }
    }

    private void setupCommandBroadcastReceiver() {
        BroadcastReceiver receiver = new CommandBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_OFFLOAD_COMMAND);
        registerReceiver(receiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    private class CommandBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra("action");
            switch (action) {
                case "ADD_OFFLOAD": {
                    String iface = intent.getStringExtra("iface");
                    String rawHexPacket = intent.getStringExtra("raw_hex_packet");
                    if (rawHexPacket != null && iface != null) {
                        registerProtocolResponse(rawHexPacket, iface);
                    } else {
                        Log.d(TAG, "Bad parameters for ADD_OFFLOAD command");
                    }
                    break;
                }
                case "REMOVE_OFFLOAD": {
                    int recordKey = intent.getIntExtra("recordKey", -1);
                    if (recordKey >= 0) {
                        removeProtocolResponse(recordKey);
                    } else {
                        Log.d(TAG, "Bad parameters for REMOVE_OFFLOAD command");
                    }
                    break;
                }
                case "ADD_PASSTHROUGH": {
                    String iface = intent.getStringExtra("iface");
                    String qname = intent.getStringExtra("qname");
                    if (iface != null && qname != null) {
                        registerPassthrough(qname, iface);
                    } else {
                        Log.d(TAG, "Bad parameters for ADD_PASSTHROUGH command");
                    }
                    break;
                }
                case "REMOVE_PASSTHROUGH": {
                    String iface = intent.getStringExtra("iface");
                    String qname = intent.getStringExtra("qname");
                    if (iface != null && qname != null) {
                        removePassthrough(qname, iface);
                    } else {
                        Log.d(TAG, "Bad parameters for REMOVE_PASSTHROUGH command");
                    }
                    break;
                }
            }
        }
    }


    private void bindMdnsOffloadManager() {
        ComponentName componentName = ComponentName.unflattenFromString(
                "com.android.tv.mdnsoffloadmanager/.MdnsOffloadManagerService");
        Intent explicitIntent = new Intent();
        explicitIntent.setComponent(componentName);
        boolean bindingSuccessful = bindService(explicitIntent,
                mMdnsOffloadManagerServiceConnection, Context.BIND_AUTO_CREATE);
        if (!bindingSuccessful) {
            String msg = "Failed to bind MdnsOffloadManager.";
            Log.e(TAG, msg);
            throw new IllegalStateException(msg);
        }
    }


    private final ServiceConnection mMdnsOffloadManagerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "IMdnsOffloadManager service bound successfully.");
            mMdnsOffloadManagerService = IMdnsOffloadManager.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e(TAG, "IMdnsOffloadManager service has unexpectedly disconnected.");
            mMdnsOffloadManagerService = null;
        }
    };

}
