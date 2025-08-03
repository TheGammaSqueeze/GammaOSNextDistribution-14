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
package com.android.car.portraitlauncher.common;

import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_REGISTER_CLIENT;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_UNREGISTER_CLIENT;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.caruiportrait.common.service.CarUiPortraitService;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the service connection to {@link CarUiPortraitService}.
 */
public class CarUiPortraitServiceManager {
    private static final boolean DBG = Build.IS_DEBUGGABLE;
    private static final String TAG = CarUiPortraitServiceManager.class.getSimpleName();

    private final Activity mActivity;
    /** Holds any messages fired before service connection is establish. */
    private final List<Message> mMessageCache = new ArrayList<>();
    /** All messages from {@link CarUiPortraitService} are received in this handler. */
    private final Messenger mMessenger;
    /** Messenger for communicating with {@link CarUiPortraitService}. */
    private Messenger mService = null;
    /** Class for interacting with the main interface of the {@link CarUiPortraitService}. */
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Communicating with our service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);

            // Register to the service.
            try {
                Message msg = Message.obtain(null, MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
                Log.w(TAG, "can't connect to CarUiPortraitService: ", e);
            }
            for (Message msg : mMessageCache) {
                notifySystemUI(msg);
            }
            mMessageCache.clear();
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };
    /** Flag indicating whether or not {@link CarUiPortraitService} is bounded. */
    private boolean mIsBound;

    public CarUiPortraitServiceManager(Activity activity, Handler handler) {
        mActivity = activity;
        mMessenger = new Messenger(handler);
        doBindService();
    }

    private static void logIfDebuggable(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }

    /** Sends a message to SystemUI with given {@code key} and {@code value}. */
    public void notifySystemUI(int key, int value) {
        Message msg = Message.obtain(/* handler= */ null, key, value, 0);
        notifySystemUI(msg);
    }

    /** Destroy the {@link CarUiPortraitServiceManager}. */
    public void onDestroy() {
        doUnbindService();
    }

    private void notifySystemUI(Message msg) {
        try {
            if (mService != null) {
                mService.send(msg);
            } else {
                logIfDebuggable("Service is not connected yet! Caching the message:" + msg);
                mMessageCache.add(msg);
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private void doUnbindService() {
        if (!mIsBound) {
            return;
        }
        if (mService != null) {
            try {
                Message msg = Message.obtain(null, MSG_UNREGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                Log.w(TAG, "can't unregister to CarUiPortraitService: ", e);
            }
        }

        // Detach our existing connection.
        mActivity.unbindService(mConnection);
        mIsBound = false;
    }

    private void doBindService() {
        // Establish a connection with {@link CarUiPortraitService}. We use an explicit class
        // name because there is no reason to be able to let other applications replace our
        // component.
        mActivity.bindService(new Intent(mActivity, CarUiPortraitService.class), mConnection,
                Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }
}
