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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.activities.ActivityCreatedEvent;
import com.android.eventlib.events.broadcastreceivers.BroadcastReceivedEvent;
import com.android.eventlib.events.services.ServiceCreatedEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This test assumes that EventLibAppComponentFactory is in the AndroidManifest.xml of the
 * instrumented test process.
 */
@RunWith(JUnit4.class)
public class EventLibAppComponentFactoryTest {

    // This must exist as an <activity> in AndroidManifest.xml
    private static final String DECLARED_ACTIVITY_WITH_NO_CLASS
            = "com.android.generatedEventLibActivity";

    // This must exist as an <receiver> in AndroidManifest.xml which receives the
    // com.android.eventlib.GENERATED_BROADCAST_RECEIVER action
    private static final String GENERATED_RECEIVER_CLASS_NAME =
            "com.android.generatedEventLibBroadcastReceiver";
    private static final String GENERATED_BROADCAST_RECEIVER_ACTION =
            "com.android.eventlib.GENERATED_BROADCAST_RECEIVER";

    // This must exist as a <service> in AndroidManifest.xml
    private static final String GENERATED_SERVICE_CLASS_NAME =
            "com.android.generatedEventLibService";

    private static final Context sContext =
            TestApis.context().instrumentedContext();

    @Test
    public void startActivity_activityDoesNotExist_startsLoggingActivity() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sContext.getPackageName(),
                DECLARED_ACTIVITY_WITH_NO_CLASS));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sContext.startActivity(intent);

        EventLogs<ActivityCreatedEvent> eventLogs =
                ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                .whereActivity().activityClass().className().isEqualTo(DECLARED_ACTIVITY_WITH_NO_CLASS);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void sendBroadcast_receiverDoesNotExist_launchesBroadcastReceiver() {
        Intent intent = new Intent(GENERATED_BROADCAST_RECEIVER_ACTION);
        intent.setPackage(sContext.getPackageName());

        sContext.sendBroadcast(intent);

        EventLogs<BroadcastReceivedEvent> eventLogs = BroadcastReceivedEvent
                .queryPackage(sContext.getPackageName())
                .whereBroadcastReceiver().receiverClass().className().isEqualTo(GENERATED_RECEIVER_CLASS_NAME);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void startService_serviceDoesNotExist_startsLoggingService() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sContext.getPackageName(),
                GENERATED_SERVICE_CLASS_NAME));

        sContext.startService(intent);

        EventLogs<ServiceCreatedEvent> eventLogs =
                ServiceCreatedEvent.queryPackage(sContext.getPackageName())
                        .whereService().serviceClass().className()
                            .isEqualTo(GENERATED_SERVICE_CLASS_NAME);
        assertThat(eventLogs.poll()).isNotNull();

        sContext.stopService(intent);
    }

}
