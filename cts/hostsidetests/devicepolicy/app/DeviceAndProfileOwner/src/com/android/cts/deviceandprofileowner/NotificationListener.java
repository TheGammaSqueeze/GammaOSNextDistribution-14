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
 * limitations under the License
 */

package com.android.cts.deviceandprofileowner;

import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A {@link NotificationListenerService} that allows tests to register to respond to notifications.
 */
public class NotificationListener extends NotificationListenerService {

    private static final String TAG = NotificationListener.class.getSimpleName();

    private static final int TIMEOUT_SECONDS = 120;

    private static NotificationListener instance;
    private static CountDownLatch connectedLatch = new CountDownLatch(1);

    public static NotificationListener getInstance() {
        try {
            Log.d(TAG, "getInstance(): waiting " + TIMEOUT_SECONDS + "s for connection");
            if (!connectedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("notification listener didn't connect in "
                        + TIMEOUT_SECONDS + "s");
            }
            Log.v(TAG, "wait no more...");
        } catch (InterruptedException e) {
            throw new RuntimeException("NotificationListener not connected.", e);
        }

        return instance;
    }

    private List<Consumer<StatusBarNotification>> mListeners = new ArrayList<>();

    public void addListener(Consumer<StatusBarNotification> listener) {
        mListeners.add(listener);
    }

    public void clearListeners() {
        mListeners.clear();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "Notification posted: " + sbn);
        for (Consumer<StatusBarNotification> listener : mListeners) {
            listener.accept(sbn);
        }
    }

    @Override
    public void onListenerConnected() {
        Log.d(TAG, "onListenerConnected() on user " + UserHandle.myUserId());
        instance = this;
        connectedLatch.countDown();
    }
}
