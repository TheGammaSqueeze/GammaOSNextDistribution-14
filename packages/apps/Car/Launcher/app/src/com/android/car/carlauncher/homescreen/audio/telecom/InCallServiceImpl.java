/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.carlauncher.homescreen.audio.telecom;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.util.Log;

import com.android.car.carlauncher.homescreen.audio.InCallModel;
import com.android.car.carlauncher.homescreen.audio.InCallServiceManagerProvider;

import java.util.ArrayList;

/**
 * Implementation of {@link InCallService}, an {@link android.telecom} service which must be
 * implemented by an app that wishes to provide functionality for managing phone calls. This service
 * is bound by android telecom and {@link InCallModel}.
 */
public class InCallServiceImpl extends InCallService {
    private static final String TAG = "Home.InCallServiceImpl";
    private static final boolean DEBUG = false;

    /**
     * An action which indicates a bind is from local component. Local components must use this
     * action to be able to bind the service.
     */
    public static final String ACTION_LOCAL_BIND = "local_bind";

    private ArrayList<InCallListener> mInCallListeners = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        InCallServiceManagerProvider.get().setInCallService(this);
    }

    @Override
    public void onDestroy() {
        InCallServiceManagerProvider.get().setInCallService(null);
        super.onDestroy();
    }

    @Override
    public void onCallAdded(Call call) {
        if (DEBUG) Log.d(TAG, "onCallAdded: " + call);
        for (InCallListener listener : mInCallListeners) {
            listener.onCallAdded(call);
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        if (DEBUG) Log.d(TAG, "onCallRemoved: " + call);
        for (InCallListener listener : mInCallListeners) {
            listener.onCallRemoved(call);
        }
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        if (DEBUG) Log.d(TAG, "onCallAudioStateChanged: " + audioState);
        for (InCallListener listener : mInCallListeners) {
            listener.onCallAudioStateChanged(audioState);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DEBUG) Log.d(TAG, "onBind, intent: " + intent);
        return ACTION_LOCAL_BIND.equals(intent.getAction())
                ? new LocalBinder()
                : super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (DEBUG) Log.d(TAG, "onUnbind, intent: " + intent);
        if (ACTION_LOCAL_BIND.equals(intent.getAction())) {
            return false;
        }
        return super.onUnbind(intent);
    }

    /**
     * Adds a listener for {@link InCallService} events
     */
    public void addListener(InCallListener listener) {
        mInCallListeners.add(listener);
    }

    /**
     * Removes a listener for {@link InCallService} events
     */
    public void removeListener(InCallListener listener) {
        if (!mInCallListeners.isEmpty()) mInCallListeners.remove(listener);
    }

    /**
     * Class used for the client Binder to access the service.
     */
    public class LocalBinder extends Binder {

        /**
         * Returns this instance of {@link InCallServiceImpl} if running in the Home App process,
         * otherwise null
         */
        public InCallServiceImpl getService() {
            if (getCallingPid() == Process.myPid()) {
                return InCallServiceImpl.this;
            }
            return null;
        }
    }

    /**
     * Listens for {@link #onCallAdded(Call)} and {@link #onCallRemoved(Call)} events
     */
    public interface InCallListener {
        /**
         * Called when a {@link Call} has been added to this in-call session, generally indicating
         * that the call has been received.
         */
        void onCallAdded(Call call);

        /**
         * Called when a {@link Call} has been removed from this in-call session, generally
         * indicating that the call has ended.
         */
        void onCallRemoved(Call call);

        /**
         * Called when {@link CallAudioState} changes.
         */
        void onCallAudioStateChanged(CallAudioState audioState);
    }
}
