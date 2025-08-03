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
package com.android.bedstead.dpmwrapper;

import static com.android.bedstead.dpmwrapper.Utils.MY_USER_ID;
import static com.android.bedstead.dpmwrapper.Utils.VERBOSE;
import static com.android.bedstead.dpmwrapper.Utils.assertCurrentUserOnHeadlessSystemMode;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;

/**
 * {@link BroadcastReceiver} used in the test apps to receive intents that were originally sent to
 * the device owner's {@link android.app.admin.DeviceAdminReceiver}.
 *
 * <p>It must be declared in the manifest:
 * <pre><code>
   <receiver android:name="com.android.bedstead.dpmwrapper.TestAppCallbacksReceiver"
             android:exported="true"/>
</code></pre>
 *
 */
public final class TestAppCallbacksReceiver extends BroadcastReceiver {

    private static final String TAG = TestAppCallbacksReceiver.class.getSimpleName();
    private static final String EXTRA = "relayed_intent";

    private static final Object LOCK = new Object();
    private static HandlerThread sHandlerThread;
    private static Handler sHandler;

    /**
     * Map of receivers per intent action.
     */
    @GuardedBy("LOCK")
    private static final ArrayMap<String, ArrayList<BroadcastReceiver>> sRealReceivers =
            new ArrayMap<>();

    private static void setHandlerThread() {
        if (sHandlerThread != null) return;

        sHandlerThread = new HandlerThread("TestAppCallbacksReceiverThread");
        Log.i(TAG, "Starting thread " + sHandlerThread + " on user " + MY_USER_ID);
        sHandlerThread.start();
        sHandler = new Handler(sHandlerThread.getLooper());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, " received intent on user " + context.getUserId() + ": " + intent);
        assertCurrentUserOnHeadlessSystemMode(context);
        setHandlerThread();

        Intent realIntent = intent.getParcelableExtra(EXTRA);
        if (realIntent == null) {
            Log.e(TAG, "No " + EXTRA + " on intent " + intent);
            return;
        }
        String action = realIntent.getAction();
        ArrayList<BroadcastReceiver> receivers;
        synchronized (LOCK) {
            receivers = sRealReceivers.get(action);
        }
        if (receivers == null || receivers.isEmpty()) {
            Log.e(TAG, "onReceive(): no receiver for " + action + ": " + sRealReceivers);
            return;
        }
        Log.d(TAG, "Will dispatch intent to " + receivers.size() + " on handler thread");
        receivers.forEach((r) -> sHandler.post(() ->
                handleDispatchIntent(r, context, realIntent)));
    }

    private void handleDispatchIntent(BroadcastReceiver receiver, Context context,
            Intent intent) {
        Log.d(TAG, "Dispatching " + intent + " to " + receiver + " on thread "
                + Thread.currentThread());
        receiver.onReceive(context, intent);
    }

    static void registerReceiver(Context context, BroadcastReceiver receiver,
            IntentFilter filter) {
        if (VERBOSE) Log.v(TAG, "registerReceiver(): " + receiver);
        synchronized (LOCK) {
            filter.actionsIterator().forEachRemaining((action) -> {
                Log.d(TAG, "Registering " + receiver + " for " + action);
                ArrayList<BroadcastReceiver> receivers = sRealReceivers.get(action);
                if (receivers == null) {
                    receivers = new ArrayList<>();
                    if (VERBOSE) Log.v(TAG, "Creating list of receivers for " + action);
                    sRealReceivers.put(action, receivers);
                }
                receivers.add(receiver);
            });
        }
    }

    static void unregisterReceiver(Context context, BroadcastReceiver receiver) {
        if (VERBOSE) Log.v(TAG, "unregisterReceiver(): " + receiver);

        synchronized (LOCK) {
            for (int i = 0; i < sRealReceivers.size(); i++) {
                String action = sRealReceivers.keyAt(i);
                ArrayList<BroadcastReceiver> receivers = sRealReceivers.valueAt(i);
                boolean removed = receivers.remove(receiver);
                if (removed) {
                    Log.d(TAG, "Removed " + receiver + " for action " + action);
                }
            }
        }
    }

    static void sendBroadcast(Context context, Intent intent) {
        int currentUserId = ActivityManager.getCurrentUser();
        Intent bridgeIntent = new Intent(context, TestAppCallbacksReceiver.class)
                .putExtra(EXTRA, intent);
        Log.d(TAG, "Relaying " + intent + " from user " + MY_USER_ID + " to user "
                + currentUserId + " using " + bridgeIntent);
        context.sendBroadcastAsUser(bridgeIntent, UserHandle.of(currentUserId));
    }
}
