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

package com.android.eventlib.events.broadcastreceivers;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BroadcastReceivedEventTest {

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final String STRING_VALUE = "Value";
    private static final String DIFFERENT_STRING_VALUE = "Value2";
    private static final Intent INTENT = new Intent();

    private static final String DEFAULT_BROADCAST_RECEIVER_CLASS_NAME =
            TestBroadcastReceiver.class.getName();
    private static final String CUSTOM_BROADCAST_RECEIVER_CLASS_NAME = "customBroadcastReceiver";
    private static final String DIFFERENT_CUSTOM_ACTIVITY_CLASS_NAME = "customBroadcastReceiver2";
    private static final TestBroadcastReceiver BROADCAST_RECEIVER = new TestBroadcastReceiver();

    private static class TestBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        }
    }

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void whereIntent_works() {
        Intent intent = new Intent(STRING_VALUE);
        BroadcastReceivedEvent.logger(BROADCAST_RECEIVER, sContext, intent, 0).log();

        EventLogs<BroadcastReceivedEvent> eventLogs =
                BroadcastReceivedEvent.queryPackage(sContext.getPackageName())
                        .whereIntent().action().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.poll().intent()).isEqualTo(intent);
    }

    @Test
    public void whereIntent_skipsNonMatching() {
        Intent intent = new Intent(STRING_VALUE);
        Intent differentIntent = new Intent();
        differentIntent.setAction(DIFFERENT_STRING_VALUE);
        BroadcastReceivedEvent.logger(BROADCAST_RECEIVER, sContext, differentIntent, 0).log();
        BroadcastReceivedEvent.logger(BROADCAST_RECEIVER, sContext, intent, 0).log();

        EventLogs<BroadcastReceivedEvent> eventLogs =
                BroadcastReceivedEvent.queryPackage(sContext.getPackageName())
                        .whereIntent().action().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.poll().intent()).isEqualTo(intent);
    }

    @Test
    public void whereBroadcastReceiver_customValueOnLogger_works() {
        BroadcastReceivedEvent.logger(BROADCAST_RECEIVER, sContext, INTENT, 0)
                .setBroadcastReceiver(CUSTOM_BROADCAST_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<BroadcastReceivedEvent> eventLogs =
                BroadcastReceivedEvent.queryPackage(sContext.getPackageName())
                        .whereBroadcastReceiver().receiverClass().className().isEqualTo(
                                CUSTOM_BROADCAST_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().broadcastReceiver().className()).isEqualTo(
                CUSTOM_BROADCAST_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereBroadcastReceiver_customValueOnLogger_skipsNonMatching() {
        BroadcastReceivedEvent.logger(BROADCAST_RECEIVER, sContext, INTENT, 0)
                .setBroadcastReceiver(DIFFERENT_CUSTOM_ACTIVITY_CLASS_NAME)
                .log();
        BroadcastReceivedEvent.logger(BROADCAST_RECEIVER, sContext, INTENT, 0)
                .setBroadcastReceiver(CUSTOM_BROADCAST_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<BroadcastReceivedEvent> eventLogs =
                BroadcastReceivedEvent.queryPackage(sContext.getPackageName())
                        .whereBroadcastReceiver().receiverClass().className().isEqualTo(
                        CUSTOM_BROADCAST_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().broadcastReceiver().className()).isEqualTo(
                CUSTOM_BROADCAST_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereBroadcastReceiver_defaultValue_works() {
        BroadcastReceivedEvent.logger(BROADCAST_RECEIVER, sContext, INTENT, 0).log();

        EventLogs<BroadcastReceivedEvent> eventLogs =
                BroadcastReceivedEvent.queryPackage(sContext.getPackageName())
                        .whereBroadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_BROADCAST_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().broadcastReceiver().className())
                .isEqualTo(DEFAULT_BROADCAST_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereBroadcastReceiver_defaultValue_skipsNonMatching() {
        BroadcastReceivedEvent.logger(BROADCAST_RECEIVER, sContext, INTENT, 0)
                .setBroadcastReceiver(CUSTOM_BROADCAST_RECEIVER_CLASS_NAME)
                .log();
        BroadcastReceivedEvent.logger(BROADCAST_RECEIVER, sContext, INTENT, 0)
                .log();

        EventLogs<BroadcastReceivedEvent> eventLogs =
                BroadcastReceivedEvent.queryPackage(sContext.getPackageName())
                        .whereBroadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_BROADCAST_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().broadcastReceiver().className())
                .isEqualTo(DEFAULT_BROADCAST_RECEIVER_CLASS_NAME);
    }

}
