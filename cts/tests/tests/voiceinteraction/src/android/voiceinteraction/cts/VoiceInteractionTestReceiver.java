/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.voiceinteraction.cts;

import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.voiceinteraction.service.MainInteractionSession;

import androidx.annotation.Nullable;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class VoiceInteractionTestReceiver extends BroadcastReceiver {

    private static final String TAG = VoiceInteractionTestReceiver.class.getSimpleName();

    private static LinkedBlockingQueue<Intent> sServiceStartedQueue = new LinkedBlockingQueue<>();
    private static LinkedBlockingQueue<Intent> sScreenshotReceivedQueue =
            new LinkedBlockingQueue<>();
    private static LinkedBlockingQueue<Intent> sAssistDataReceivedQueue =
            new LinkedBlockingQueue<>();
    private static LinkedBlockingQueue<Intent> sOnShowReceivedQueue = new LinkedBlockingQueue<>();

    public static void waitSessionStarted(long timeout, TimeUnit unit) throws InterruptedException {
        Intent intent = sServiceStartedQueue.poll(timeout, unit);
        if (intent == null) {
            fail("Timed out waiting for session to start");
        }
        String error = intent.getStringExtra("error");
        if (error != null) {
            fail(error);
        }
    }

    /** Waits for onHandleScreenshot being called. */
    public static boolean waitScreenshotReceived(long timeout, TimeUnit unit)
            throws InterruptedException {
        Intent intent = sScreenshotReceivedQueue.poll(timeout, unit);
        if (intent == null) {
            // Timeout. Assume this means no screenshot is sent to VoiceInteractionService.
            return false;
        }
        if (!intent.hasExtra(MainInteractionSession.EXTRA_RECEIVED)) {
            throw new IllegalStateException("Expect EXTRA_RECEIVED");
        }
        return intent.getBooleanExtra(MainInteractionSession.EXTRA_RECEIVED, false);
    }

    /** Waits for onHandleAssist being called. */
    public static boolean waitAssistDataReceived(long timeout, TimeUnit unit)
            throws InterruptedException {
        Intent intent = sAssistDataReceivedQueue.poll(timeout, unit);
        if (intent == null) {
            // Timeout. Assume this means no assist data is sent to VoiceInteractionService.
            return false;
        }
        if (!intent.hasExtra(MainInteractionSession.EXTRA_RECEIVED)) {
            throw new IllegalStateException("Expect EXTRA_RECEIVED");
        }
        return intent.getBooleanExtra(MainInteractionSession.EXTRA_RECEIVED, false);
    }

    /** Waits for onShow being called. */
    @Nullable
    public static Bundle waitOnShowReceived(long timeout, TimeUnit unit)
            throws InterruptedException {
        Intent intent = sOnShowReceivedQueue.poll(timeout, unit);
        if (intent == null) {
            // Timeout. Assume this means no assist data is sent to VoiceInteractionService.
            return null;
        }
        return intent.getExtras();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Got broadcast: " + intent);
        switch (intent.getAction()) {
            case MainInteractionSession.ACTION_SESSION_STARTED -> sServiceStartedQueue.add(intent);
            case MainInteractionSession.ACTION_SCREENSHOT_RECEIVED -> sScreenshotReceivedQueue.add(
                    intent);
            case MainInteractionSession.ACTION_ASSIST_DATA_RECEIVED -> sAssistDataReceivedQueue.add(
                    intent);
            case MainInteractionSession.ACTION_ON_SHOW_RECEIVED -> sOnShowReceivedQueue.add(intent);
        }
    }

    /** Resets the states. */
    public static void reset() {
        sServiceStartedQueue = new LinkedBlockingQueue<>();
        sScreenshotReceivedQueue = new LinkedBlockingQueue<>();
        sAssistDataReceivedQueue = new LinkedBlockingQueue<>();
        sOnShowReceivedQueue = new LinkedBlockingQueue<>();

    }
}