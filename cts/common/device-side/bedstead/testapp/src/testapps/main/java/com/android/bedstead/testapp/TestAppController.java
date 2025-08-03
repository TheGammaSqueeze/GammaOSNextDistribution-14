/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.testapp;

import android.content.Context;
import android.content.IntentFilter;

import com.google.android.enterprise.connectedapps.annotations.CrossUser;

import java.util.HashMap;
import java.util.Map;

/**
 * Class used to issue overall control over a remote test app.
 */
public final class TestAppController {

    private static final Map<Long, BaseTestAppBroadcastReceiver> sBroadcastReceivers =
            new HashMap<>();

    /**
     * Register a broadcast receiver.
     *
     * <p>Received broadcasts can be queried using EventLib.
     */
    @CrossUser
    public void registerReceiver(Context context, long receiverId, IntentFilter intentFilter) {
        registerReceiver(context, receiverId, intentFilter, 0);
    }

    /**
     * See {@link registerReceiver(Context, long, IntentFilter)}.
     */
    @CrossUser
    public void registerReceiver(Context context, long receiverId, IntentFilter intentFilter,
            int flags) {
        BaseTestAppBroadcastReceiver broadcastReceiver = new BaseTestAppBroadcastReceiver();
        sBroadcastReceivers.put(receiverId, broadcastReceiver);

        context.registerReceiver(broadcastReceiver, intentFilter, flags);
    }

    /**
     * Unregister a previously registered broadcast receiver.
     */
    @CrossUser
    public void unregisterReceiver(Context context, long receiverId) {
        BaseTestAppBroadcastReceiver receiver = sBroadcastReceivers.remove(receiverId);
        if (receiver != null) {
            context.unregisterReceiver(receiver);
        }
    }
}
