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

package com.android.eventlib.premade;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;

import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.broadcastreceivers.BroadcastReceivedEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EventLibBroadcastReceiverTest {

    // We expect that AndroidManifest.xml contains a <receiver> for
    // com.android.eventlib.premade.EventLibBroadcastReceiver which receives the
    // com.android.eventlib.DEFAULT_BROADCAST_RECEIVER action
    private static final String DEFAULT_BROADCAST_RECEIVER_ACTION =
            "com.android.eventlib.DEFAULT_BROADCAST_RECEIVER";

    // This must exist as an <receiver> in AndroidManifest.xml which receives the
    // com.android.eventlib.GENERATED_BROADCAST_RECEIVER action
    private static final String GENERATED_RECEIVER_CLASS_NAME =
            "com.android.generatedEventLibBroadcastReceiver";
    private static final String GENERATED_BROADCAST_RECEIVER_ACTION =
            "com.android.eventlib.GENERATED_BROADCAST_RECEIVER";

    private static final Context sContext = TestApis.context().instrumentedContext();

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void receiveBroadcast_logsBroadcastReceivedEvent() {
        Intent intent = new Intent(DEFAULT_BROADCAST_RECEIVER_ACTION);
        intent.setPackage(sContext.getPackageName());

        sContext.sendBroadcast(intent);

        EventLogs<BroadcastReceivedEvent> eventLogs = BroadcastReceivedEvent
                .queryPackage(sContext.getPackageName())
                .whereBroadcastReceiver().receiverClass().isSameClassAs(EventLibBroadcastReceiver.class);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void receiveBroadcast_withGeneratedBroadcastReceiverClass_logsBroadcastReceivedEventWithCorrectClassName() {
        Intent intent = new Intent(GENERATED_BROADCAST_RECEIVER_ACTION);
        intent.setPackage(sContext.getPackageName());

        sContext.sendBroadcast(intent);

        EventLogs<BroadcastReceivedEvent> eventLogs = BroadcastReceivedEvent
                .queryPackage(sContext.getPackageName())
                .whereBroadcastReceiver().receiverClass().className().isEqualTo(GENERATED_RECEIVER_CLASS_NAME);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void receiveBroadcast_logsIntent() {
        Intent intent = new Intent(GENERATED_BROADCAST_RECEIVER_ACTION);
        intent.setPackage(sContext.getPackageName());

        sContext.sendBroadcast(intent);

        EventLogs<BroadcastReceivedEvent> eventLogs = BroadcastReceivedEvent
                .queryPackage(sContext.getPackageName())
                .whereIntent().action().isEqualTo(GENERATED_BROADCAST_RECEIVER_ACTION);
        assertThat(eventLogs.poll()).isNotNull();
    }
}
