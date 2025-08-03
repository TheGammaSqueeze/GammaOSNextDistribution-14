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

package android.car.apitest;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Collection;

/**
 * A {@link Context} for testing which tracks registering/unregistering of broadcast receivers.
 */
final class ReceiverTrackingContext extends ContextWrapper {

    private static final String TAG =  ReceiverTrackingContext.class.getSimpleName();

    private final ArrayMap<BroadcastReceiver, String> mReceivers = new ArrayMap<>();

    ReceiverTrackingContext(Context baseContext) {
        super(baseContext);
    }

    @Override
    public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter) {
        addReceiver(receiver, "registerReceiver(%s, %s)", receiver, filter);

        return super.registerReceiver(receiver, filter);
    }

    @Override
    public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter,
            int flags) {
        addReceiver(receiver, "registerReceiver(%s, %s, %d)", receiver, filter, flags);

        return super.registerReceiver(receiver, filter, flags);
    }

    @Override
    public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter,
            @Nullable String broadcastPermission, @Nullable Handler scheduler) {
        addReceiver(receiver, "registerReceiver(%s, %s, %s, %s)",
                receiver, filter, broadcastPermission, scheduler);

        return super.registerReceiver(receiver, filter, broadcastPermission,
                scheduler);
    }

    @Override
    public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter,
            @Nullable String broadcastPermission, @Nullable Handler scheduler, int flags) {
        addReceiver(receiver, "registerReceiver(%s, %s, %s, %s, %d)",
                receiver, filter, broadcastPermission, scheduler, flags);

        return super.registerReceiver(receiver, filter, broadcastPermission,
                scheduler, flags);
    }

    @Override
    @Nullable
    public Intent registerReceiverForAllUsers(@Nullable BroadcastReceiver receiver,
            IntentFilter filter, @Nullable String broadcastPermission,
            @Nullable Handler scheduler) {
        addReceiver(receiver, "registerReceiverForAllUsers(%s, %s, %s, %s)",
                receiver, filter, broadcastPermission, scheduler);

        return super.registerReceiverForAllUsers(receiver, filter, broadcastPermission,
                scheduler);
    }

    @Override
    @Nullable
    public Intent registerReceiverForAllUsers(@Nullable BroadcastReceiver receiver,
            IntentFilter filter, @Nullable String broadcastPermission,
            @Nullable Handler scheduler, int flags) {
        addReceiver(receiver, "registerReceiverForAllUsers(%s, %s, %s, %s, %d)",
                receiver, filter, broadcastPermission, scheduler, flags);

        return super.registerReceiverForAllUsers(receiver, filter, broadcastPermission,
                scheduler, flags);
    }

    @Override
    public Intent registerReceiverAsUser(@Nullable BroadcastReceiver receiver, UserHandle user,
            IntentFilter filter, @Nullable String broadcastPermission,
            @Nullable Handler scheduler) {
        addReceiver(receiver, "registerReceiverAsUser(%s, %s, %s, %s, %s)",
                receiver, user, filter, broadcastPermission, scheduler);

        return super.registerReceiverAsUser(receiver, user, filter, broadcastPermission,
                scheduler);
    }

    @Override
    public Intent registerReceiverAsUser(@Nullable BroadcastReceiver receiver, UserHandle user,
            IntentFilter filter, @Nullable String broadcastPermission,
            @Nullable Handler scheduler, int flags) {
        addReceiver(receiver, "registerReceiverAsUser(%s, %s, %s, %s, %s, %d)",
                receiver, user, filter, broadcastPermission, scheduler, flags);

        return super.registerReceiverAsUser(receiver, user, filter, broadcastPermission,
                scheduler, flags);
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        Log.d(TAG, "unregisterReceiver(" + receiver + ") called.");
        mReceivers.remove(receiver);

        super.unregisterReceiver(receiver);
    }

    /**
     * Returns the registration information of currently registered broadcast receivers.
     *
     * <p>At the end of each test, make sure this collection is empty to verify all registered
     * receivers have been unregistered.</p>
     */
    public Collection<String> getReceiversInfo() {
        return mReceivers.values();
    }

    private void addReceiver(BroadcastReceiver receiver, String methodPattern, Object... args) {
        String info = String.format(methodPattern, args)
                + String.format(" called by %s", new Throwable().getStackTrace()[2]);
        Log.d(TAG, info);
        mReceivers.put(receiver, info);
    }
}
