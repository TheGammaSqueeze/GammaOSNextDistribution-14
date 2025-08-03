/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.car.kitchensink.hotword;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.voice.HotwordDetectionService;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.car.test.concurrent.hotword.ConcurrentHotwordDetectionService;

import java.util.Objects;

final class HotwordServiceManager {

    private static final String TAG = "HotwordTestManager";
    private static final int INVALID_UID = -1;

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final PackageManager mPackageManager;
    private final ComponentName mComponentName;

    private Messenger mService;
    private boolean mBound;
    private int mServiceUid = INVALID_UID;

    @Nullable
    private HotwordServiceUpdatedCallback mServiceUpdatedCallback;

    private final Messenger mMessenger;

    private ServiceConnection mServiceConnection = new android.content.ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "Service Connected: " + name);

            mService = new Messenger(service);
            mBound = true;
            if (mServiceUpdatedCallback != null) {
                mServiceUpdatedCallback.onServiceStarted("Service Connected");
            }

            try {
                mServiceUid = mPackageManager.getPackageUid(name.getPackageName(),
                        PackageManager.GET_SERVICES);
                Log.i(TAG, "Service connected[" + mServiceUid + "] " + name);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Service connected failed to get package info: " + name, e);
                stopService();
                return;
            }

            mAudioManager.addAssistantServicesUids(new int[] { mServiceUid });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "Service Disconnected: " + name);

            mService = null;
            mBound = false;
            removeServerUidFromAssistantList();
        }
    };


    HotwordServiceManager(ComponentName componentName, Context context,
            AudioManager audioManager, PackageManager packageManager) {
        mComponentName = Objects.requireNonNull(componentName, "ComponentName can not be null");
        mContext = Objects.requireNonNull(context, "Context can not be null");
        mAudioManager = Objects.requireNonNull(audioManager, "AudioManager can not be null");
        mPackageManager = Objects.requireNonNull(packageManager, "PackageManager can not be null");
        mMessenger = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.i(TAG, "handleMessage " + msg);

                String reply;
                Bundle data = msg.getData();
                if (data == null) {
                    Log.i(TAG, "handleMessage no data replied " + msg);
                    reply = "No reply msg.what " + msg.what;
                } else {
                    reply = data.getString(ConcurrentHotwordDetectionService.MESSAGE_REPLY);
                }

                switch (msg.what) {
                    case ConcurrentHotwordDetectionService.MSG_START_DETECT_REPLY:
                        if (mServiceUpdatedCallback != null) {
                            mServiceUpdatedCallback.onRecordingStarted(reply);
                        }
                        return;
                    case ConcurrentHotwordDetectionService.MSG_STOP_DETECT_REPLY:
                        if (mServiceUpdatedCallback != null) {
                            mServiceUpdatedCallback.onServiceStopped(reply);
                        }
                        return;
                    default:
                        super.handleMessage(msg);
                        Log.i(TAG, "handleMessage error no handler " + msg);
                        return;
                }
            }
        });
    }

    void startService() {
        Log.i(TAG, "startService: " + mComponentName);
        Intent intent = new Intent(HotwordDetectionService.SERVICE_INTERFACE);
        intent.setComponent(mComponentName);

        boolean bounded = mContext.bindServiceAsUser(intent, mServiceConnection,
                Context.BIND_AUTO_CREATE, UserHandle.CURRENT);

        Log.i(TAG, "StartService bounded: " + bounded);
    }

    void stopService() {
        Log.i(TAG, "stopService bounded " + mBound);

        removeServerUidFromAssistantList();

        if (!mBound) {
            return;
        }

        sendMessageToService(ConcurrentHotwordDetectionService.MSG_STOP_SERVICE);


        mContext.unbindService(mServiceConnection);
        mBound = false;
        if (mServiceUpdatedCallback != null) {
            mServiceUpdatedCallback.onServiceStopped("Service Stopped");
        }
    }

    void startRecording() {
        Log.i(TAG, "startRecording bounded " + mBound);

        if (!mBound) {
            return;
        }

        sendMessageToService(ConcurrentHotwordDetectionService.MSG_START_DETECT);
    }

    void stopRecording() {
        Log.i(TAG, "stopRecording bounded " + mBound);
        if (!mBound) {
            return;
        }

        sendMessageToService(ConcurrentHotwordDetectionService.MSG_STOP_DETECT);
    }

    void setServiceUpdatedCallback(HotwordServiceUpdatedCallback hotwordServiceViewHolder) {
        mServiceUpdatedCallback = Objects.requireNonNull(hotwordServiceViewHolder,
                "Hotword service update callback can not be null");
    }

    void release() {
        stopService();
    }

    private void sendMessageToService(int what) {
        Log.i(TAG, "sendMessageToService sending message what " + what);

        Message msg = Message.obtain(/* handler= */ null, what, /* arg1= */ 0, /* arg2= */ 0);
        msg.replyTo = mMessenger;

        try {
            mService.send(msg);
            Log.i(TAG, "sendMessageToService message sent " + msg);
        } catch (RemoteException e) {
            Log.e(TAG, "sendMessageToService error ", e);
        }
    }

    private void removeServerUidFromAssistantList() {
        if (mServiceUid == INVALID_UID) {
            return;
        }

        mAudioManager.removeAssistantServicesUids(new int[] { mServiceUid });
        mServiceUid = INVALID_UID;
    }
}
