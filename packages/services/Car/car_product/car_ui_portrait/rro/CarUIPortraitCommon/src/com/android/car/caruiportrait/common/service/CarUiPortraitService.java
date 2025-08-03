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

package com.android.car.caruiportrait.common.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;

/**
 * This application service uses the {@link Messenger} class for communicating with clients
 * {@link CarUiPortraitLauncher}.  This allows for remote interaction with a service, without
 * needing to define an AIDL interface.
 */
public class CarUiPortraitService extends Service {
    public static final String TAG = "CarUiPortraitService";

    // action name for the intent when requested from system UI
    public static final String REQUEST_FROM_SYSTEM_UI = "REQUEST_FROM_SYSTEM_UI";

    // key name for the intent's extra that tells the root task view's visibility status
    public static final String INTENT_EXTRA_IS_IMMERSIVE_MODE_REQUESTED =
            "INTENT_EXTRA_IS_IMMERSIVE_MODE_REQUESTED";

    // key name for the intent's extra that tells the component that request the view's
    // visibility change.
    public static final String INTENT_EXTRA_IMMERSIVE_MODE_REQUESTED_SOURCE =
            "INTENT_EXTRA_IMMERSIVE_MODE_REQUESTED_SOURCE";

    public static final String INTENT_EXTRA_IS_IMMERSIVE_MODE_STATE =
            "INTENT_EXTRA_IS_IMMERSIVE_MODE_STATE";

    // action name for the intent when requested from CarUiPortraitLauncher
    public static final String REQUEST_FROM_LAUNCHER = "REQUEST_FROM_LAUNCHER";

    // key name for the intent's extra that tells the system bars visibility status
    public static final String INTENT_EXTRA_HIDE_SYSTEM_BAR_FOR_IMMERSIVE_MODE =
            "INTENT_EXTRA_HIDE_SYSTEM_BAR_FOR_IMMERSIVE_MODE";

    // key name for the intent's extra that tells the app grid's visibility status
    public static final String INTENT_EXTRA_APP_GRID_VISIBILITY_CHANGE =
            "INTENT_EXTRA_APP_GRID_VISIBILITY_CHANGE";

    // key name for the intent's extra that tells the notification's visibility status
    public static final String INTENT_EXTRA_NOTIFICATION_VISIBILITY_CHANGE =
            "INTENT_EXTRA_NOTIFICATION_VISIBILITY_CHANGE";

    // key name for the intent's extra that tells the Recents' visibility status
    public static final String INTENT_EXTRA_RECENTS_VISIBILITY_CHANGE =
            "INTENT_EXTRA_RECENTS_VISIBILITY_CHANGE";

    // key name for the intent's extra that tells if suw is in progress
    public static final String INTENT_EXTRA_SUW_IN_PROGRESS =
            "INTENT_EXTRA_SUW_IN_PROGRESS";

    // key name for the intent's extra that tells if task views are ready
    public static final String INTENT_EXTRA_FG_TASK_VIEW_READY =
            "INTENT_EXTRA_TASK_VIEW_READY";

    // key name for the intent's extra that tells if launcher is ready
    public static final String INTENT_EXTRA_LAUNCHER_READY =
            "INTENT_EXTRA_LAUNCHER_READY";

    // key name for the intent's extra that tells if notification panel should be collapsed.
    public static final String INTENT_EXTRA_COLLAPSE_NOTIFICATION_PANEL =
            "INTENT_EXTRA_COLLAPSE_NOTIFICATION_PANEL";

    // key name for the intent's extra that tells if recents panel should be collapsed.
    public static final String INTENT_EXTRA_COLLAPSE_RECENTS_PANEL =
            "INTENT_EXTRA_COLLAPSE_RECENTS_PANEL";

    // Keeps track of all current registered clients.
    private final ArrayList<Messenger> mClients = new ArrayList<Messenger>();

    private final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * Command to the service to register a client, receiving callbacks
     * from the service. The Message's replyTo field must be a Messenger of
     * the client where callbacks should be sent.
     */
    public static final int MSG_REGISTER_CLIENT = 1;

    /**
     * Command to the service to unregister a client, it stop receiving callbacks
     * from the service. The Message's replyTo field must be a Messenger of
     * the client as previously given with MSG_REGISTER_CLIENT.
     */
    public static final int MSG_UNREGISTER_CLIENT = 2;

    /**
     * Command to service to set a new value for app grid visibility.
     */
    public static final int MSG_APP_GRID_VISIBILITY_CHANGE = 3;

    /**
     * Command to service to set a new value when immersive mode is requested or exited.
     */
    public static final int MSG_IMMERSIVE_MODE_REQUESTED = 4;

    /**
     * Command to service to set a new value when SUW mode is entered or exited.
     */
    public static final int MSG_SUW_IN_PROGRESS = 5;

    /**
     * Command to service to set a new value when launcher request to hide the systembars.
     */
    public static final int MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE = 6;

    /**
     * Command to service to notify when task views are ready.
     */
    public static final int MSG_FG_TASK_VIEW_READY = 7;

    /**
     * Command to service to notify when immersive mode changes
     */
    public static final int MSG_IMMERSIVE_MODE_CHANGE = 8;

    /**
     * Command to service to notify when SysUI is ready and started.
     */
    public static final int MSG_SYSUI_STARTED = 9;

    /**
     * Command to service to set a new value for notifications visibility.
     */
    public static final int MSG_NOTIFICATIONS_VISIBILITY_CHANGE = 10;

    /**
     * Command to service to set a new value for Recents visibility.
     */
    public static final int MSG_RECENTS_VISIBILITY_CHANGE = 11;

    /**
     * Command to service to collapse notification panel if open.
     */
    public static final int MSG_COLLAPSE_NOTIFICATION = 12;

    /**
     * Command to service to collapse recents panel.
     */
    public static final int MSG_COLLAPSE_RECENTS = 13;

    private boolean mIsSystemInImmersiveMode;
    private String mImmersiveModeSource;
    private boolean mIsSuwInProgress;
    private BroadcastReceiver mSysUiRequestsReceiver;

    /**
     * Handler of incoming messages from CarUiPortraitLauncher.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "Received message: " + msg.what);
            switch (msg.what) {
                case MSG_SYSUI_STARTED:
                    // value is passed as 0 because launcher just needs a event and no need for val
                    notifyClients(MSG_SYSUI_STARTED, 0);
                    break;
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_APP_GRID_VISIBILITY_CHANGE:
                    Intent intent = new Intent(REQUEST_FROM_LAUNCHER);
                    intent.putExtra(INTENT_EXTRA_APP_GRID_VISIBILITY_CHANGE,
                            intToBoolean(msg.arg1));
                    CarUiPortraitService.this.sendBroadcast(intent);
                    break;
                case MSG_NOTIFICATIONS_VISIBILITY_CHANGE:
                    Intent notificationIntent = new Intent(REQUEST_FROM_LAUNCHER);
                    notificationIntent.putExtra(INTENT_EXTRA_NOTIFICATION_VISIBILITY_CHANGE,
                            intToBoolean(msg.arg1));
                    CarUiPortraitService.this.sendBroadcast(notificationIntent);
                    break;
                case MSG_RECENTS_VISIBILITY_CHANGE:
                    Intent recentsIntent = new Intent(REQUEST_FROM_LAUNCHER);
                    recentsIntent.putExtra(INTENT_EXTRA_RECENTS_VISIBILITY_CHANGE,
                            intToBoolean(msg.arg1));
                    CarUiPortraitService.this.sendBroadcast(recentsIntent);
                    break;
                case MSG_HIDE_SYSTEM_BAR_FOR_IMMERSIVE:
                    int val = msg.arg1;
                    Intent hideSysBarIntent = new Intent(REQUEST_FROM_LAUNCHER);
                    Log.d(TAG, "hideSysBarIntent: val = " + val);

                    hideSysBarIntent.putExtra(INTENT_EXTRA_HIDE_SYSTEM_BAR_FOR_IMMERSIVE_MODE, val);
                    CarUiPortraitService.this.sendBroadcast(hideSysBarIntent);
                    break;
                case MSG_FG_TASK_VIEW_READY:
                    Intent taskViewReadyIntent = new Intent(REQUEST_FROM_LAUNCHER);
                    taskViewReadyIntent.putExtra(INTENT_EXTRA_FG_TASK_VIEW_READY,
                            intToBoolean(msg.arg1));
                    taskViewReadyIntent.putExtra(INTENT_EXTRA_LAUNCHER_READY,
                            intToBoolean(msg.arg1));
                    CarUiPortraitService.this.sendBroadcast(taskViewReadyIntent);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    public void onCreate() {
        mSysUiRequestsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean isImmersive = intent.getBooleanExtra(
                        INTENT_EXTRA_IS_IMMERSIVE_MODE_REQUESTED, false);
                String source = intent.getStringExtra(
                        INTENT_EXTRA_IMMERSIVE_MODE_REQUESTED_SOURCE);
                Log.d(TAG, "Immersive request: source = " + source + ", request=" + isImmersive);
                if (intent.hasExtra(INTENT_EXTRA_IS_IMMERSIVE_MODE_REQUESTED)
                        && isImmersive != mIsSystemInImmersiveMode
                        && source != null
                        && !source.equals(mImmersiveModeSource)) {
                    mIsSystemInImmersiveMode = isImmersive;
                    mImmersiveModeSource = source;
                    Bundle bundle = new Bundle();
                    bundle.putString(INTENT_EXTRA_IMMERSIVE_MODE_REQUESTED_SOURCE, source);
                    notifyClients(MSG_IMMERSIVE_MODE_REQUESTED, boolToInt(isImmersive), bundle);
                }

                boolean isImmersiveState = intent.getBooleanExtra(
                        INTENT_EXTRA_IS_IMMERSIVE_MODE_STATE, false);
                if (intent.hasExtra(INTENT_EXTRA_IS_IMMERSIVE_MODE_STATE)) {
                    notifyClients(MSG_IMMERSIVE_MODE_CHANGE, boolToInt(isImmersiveState));
                }

                boolean isSuwInProgress = intent.getBooleanExtra(
                        INTENT_EXTRA_SUW_IN_PROGRESS, false);
                if (intent.hasExtra(INTENT_EXTRA_SUW_IN_PROGRESS)
                        && isSuwInProgress != mIsSuwInProgress) {
                    mIsSuwInProgress = isSuwInProgress;
                    notifyClients(MSG_SUW_IN_PROGRESS, boolToInt(isSuwInProgress));
                }

                if (intent.hasExtra(INTENT_EXTRA_COLLAPSE_NOTIFICATION_PANEL)) {
                    notifyClients(MSG_COLLAPSE_NOTIFICATION, 1);
                }

                if (intent.hasExtra(INTENT_EXTRA_COLLAPSE_RECENTS_PANEL)) {
                    notifyClients(MSG_COLLAPSE_RECENTS, 1);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(REQUEST_FROM_SYSTEM_UI);
        registerReceiver(mSysUiRequestsReceiver, filter);
        Log.d(TAG, "Portrait service is created");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mSysUiRequestsReceiver);
    }

    private void notifyClients(int key, int value) {
        for (int i = mClients.size() - 1; i >= 0; i--) {
            try {
                mClients.get(i).send(Message.obtain(null, key, value, 0));
            } catch (RemoteException e) {
                // The client is dead. Remove it from the list.
                mClients.remove(i);
                Log.d(TAG, "A client is removed from the list");
            }
        }
    }

    private void notifyClients(int key, int value, Bundle bundle) {
        for (int i = mClients.size() - 1; i >= 0; i--) {
            try {
                Message msg = Message.obtain(null, key, value, 0);
                msg.setData(bundle);
                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                // The client is dead. Remove it from the list.
                mClients.remove(i);
                Log.d(TAG, "A client is removed from the list");
            }
        }
    }

    private boolean intToBoolean(int val) {
        return val == 1;
    }

    private static int boolToInt(Boolean b) {
        return b ? 1 : 0;
    }
}
