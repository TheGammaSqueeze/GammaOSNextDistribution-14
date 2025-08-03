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

package com.google.android.utils.chre;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubIntentEvent;
import android.util.Log;

import org.junit.Assert;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A BroadcastReceiver that waits for Intent events from the Context Hub. */
public class ContextHubBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "ContextHubBroadcastReceiver";

    /*
     * The queue to store received ContextHubIntentEvents.
     */
    private static final ArrayBlockingQueue<ContextHubIntentEvent> sQueue =
            new ArrayBlockingQueue<>(1);

    /*
     * Note: This method runs on the main UI thread of this app.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        ContextHubIntentEvent event = null;
        try {
            event = ContextHubIntentEvent.fromIntent(intent);
        } catch (IllegalArgumentException e) {
            Assert.fail("Exception while parsing intent event: " + e.getMessage());
        }
        Log.d(TAG, "Received intent event: " + event);

        Assert.assertEquals("Received too many Intent events", sQueue.size(), 0);
        sQueue.add(event);
    }

    /**
     * Waits for an Intent event to be delivered to an instance of this BroadcastReceiver.
     *
     * @param timeout the timeout to wait
     * @param unit the unit of the timeout
     * @param eventType the type of the event type of the expected Intent event
     * @param contextHubInfo the ContextHubInfo of the expected Intent event
     * @param nanoAppId the ID of the nanoapp of the expected Intent event
     * @return the ContextHubIntentEvent generated from the Intent event
     */
    public static ContextHubIntentEvent pollIntentEvent(
            long timeout,
            TimeUnit unit,
            int eventType,
            ContextHubInfo contextHubInfo,
            long nanoAppId) {
        ContextHubIntentEvent event = null;
        try {
            event = sQueue.poll(timeout, unit);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException while waiting for Intent event");
        }
        if (event == null) {
            Assert.fail("Timed out while waiting for Intent event");
        }

        if (event.getEventType() != eventType) {
            Assert.fail(
                    "Received unexpected event type, expected "
                            + eventType
                            + " received "
                            + event.getEventType());
        }
        if (!event.getContextHubInfo().equals(contextHubInfo)) {
            Assert.fail(
                    "Received unexpected ContextHubInfo, expected "
                            + contextHubInfo
                            + " received "
                            + event.getContextHubInfo());
        }
        if (event.getNanoAppId() != nanoAppId) {
            Assert.fail(
                    "Received unexpected nanoapp ID, expected "
                            + nanoAppId
                            + " received "
                            + event.getNanoAppId());
        }

        return event;
    }

    /**
     * Asserts that no Intent events are delivered to an instance of this BroadcastReceiver.
     *
     * @param timeout the timeout to wait
     * @param unit the unit of the timeout
     */
    public static void assertNoIntentEventReceived(long timeout, TimeUnit unit) {
        ContextHubIntentEvent event = null;
        try {
            event = sQueue.poll(timeout, unit);
        } catch (InterruptedException e) {
            Assert.fail("InterruptedException while waiting for Intent event");
        }

        Assert.assertNull("Received unexpected intent event " + event, event);
    }
}
