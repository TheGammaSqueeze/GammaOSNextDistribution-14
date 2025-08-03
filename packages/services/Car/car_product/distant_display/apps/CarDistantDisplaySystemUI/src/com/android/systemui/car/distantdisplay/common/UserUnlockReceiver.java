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

package com.android.systemui.car.distantdisplay.common;

import static android.content.Intent.ACTION_USER_UNLOCKED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

/**
 * Broadcast receiver which observes {@link ACTION_USER_UNLOCKED}.
 */
public class UserUnlockReceiver extends BroadcastReceiver {
    public static final String TAG = UserUnlockReceiver.class.getSimpleName();
    public Callback mCallback;

    /**
     * Registers a broadcastReceiver to listen to {@link ACTION_USER_UNLOCKED} with given
     * {@code callback} to react on this action.
     */
    public void register(Context context, Callback callback) {
        Log.i(TAG, "register()");
        mCallback = callback;
        IntentFilter intentFilter = new IntentFilter(ACTION_USER_UNLOCKED);
        context.registerReceiver(this, intentFilter);
    }

    /** Unregisters this observer. */
    public void unregister(Context context) {
        Log.i(TAG, "unregister()");
        context.unregisterReceiver(this);
        mCallback = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mCallback == null || !ACTION_USER_UNLOCKED.equals(intent.getAction())) {
            return;
        }
        mCallback.onUserUnlocked();
    }

    /** Callback interface for {@link UserUnlockReceiver}. */
    public interface Callback {

        /** Callback triggered when {@link ACTION_USER_UNLOCKED} is received. */
        void onUserUnlocked();
    }
}
