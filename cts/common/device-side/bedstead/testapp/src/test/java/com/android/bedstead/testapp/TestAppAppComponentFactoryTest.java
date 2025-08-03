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

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.eventlib.truth.EventLogsSubject.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.eventlib.events.activities.ActivityCreatedEvent;
import com.android.eventlib.events.broadcastreceivers.BroadcastReceivedEvent;
import com.android.eventlib.events.services.ServiceCreatedEvent;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test assumes that TestAppAppComponentFactory is in the AndroidManifest.xml of the
 * instrumented test process.
 */
@RunWith(BedsteadJUnit4.class)
public final class TestAppAppComponentFactoryTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // This must exist as an <activity> in AndroidManifest.xml
    private static final String DECLARED_ACTIVITY_WITH_NO_CLASS =
            "com.android.GeneratedTestAppActivity";

    // This must exist as an <receiver> in AndroidManifest.xml which receives the
    // com.android.testapp.GENERATED_BROADCAST_RECEIVER action
    private static final String GENERATED_RECEIVER_CLASS_NAME =
            "com.android.GeneratedTestAppBroadcastReceiver";
    private static final String GENERATED_BROADCAST_RECEIVER_ACTION =
            "com.android.testapp.GENERATED_BROADCAST_RECEIVER";

    // This must exist as a <service> in AndroidManifest.xml
    private static final String GENERATED_SERVICE_CLASS_NAME =
            "com.android.GeneratedTestAppService";

    private static final Context sContext =
            TestApis.context().instrumentedContext();

    @Test
    public void startActivity_activityDoesNotExist_startsLoggingActivity() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sContext.getPackageName(),
                DECLARED_ACTIVITY_WITH_NO_CLASS));
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        sContext.startActivity(intent);

        assertThat(ActivityCreatedEvent.queryPackage(sContext.getPackageName())
                        .whereActivity().activityClass().className()
                            .isEqualTo(DECLARED_ACTIVITY_WITH_NO_CLASS)).eventOccurred();
    }

    @Test
    public void sendBroadcast_receiverDoesNotExist_launchesBroadcastReceiver() {
        Intent intent = new Intent(GENERATED_BROADCAST_RECEIVER_ACTION);
        intent.setPackage(sContext.getPackageName());

        sContext.sendBroadcast(intent);

        assertThat(BroadcastReceivedEvent
                .queryPackage(sContext.getPackageName())
                .whereBroadcastReceiver().receiverClass().className()
                .isEqualTo(GENERATED_RECEIVER_CLASS_NAME)).eventOccurred();
    }

    @Test
    public void startService_serviceDoesNotExist_startsLoggingService() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sContext.getPackageName(),
                GENERATED_SERVICE_CLASS_NAME));

        sContext.startService(intent);

        assertThat(ServiceCreatedEvent.queryPackage(sContext.getPackageName())
                        .whereService().serviceClass().className()
                            .isEqualTo(GENERATED_SERVICE_CLASS_NAME)).eventOccurred();
    }
}
