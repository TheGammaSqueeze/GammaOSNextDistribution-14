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
import com.android.eventlib.events.services.ServiceCreatedEvent;

import org.junit.Before;
import org.junit.Test;

//TODO(b/204770471) Currently unable to create tests for most events without an instrumented
// service.
public class EventLibServiceTest {

    // This must exist as a <service> in AndroidManifest.xml
    private static final String GENERATED_SERVICE_CLASS_NAME =
            "com.android.generatedEventLibService";

    private static final Context sContext = TestApis.context().instrumentedContext();

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void launchEventLibService_logsServiceCreatedEvent() {
        Intent intent = new Intent();
        intent.setPackage(sContext.getPackageName());
        intent.setClassName(sContext.getPackageName(), EventLibService.class.getName());
        sContext.startService(intent);

        EventLogs<ServiceCreatedEvent> eventLogs = ServiceCreatedEvent
                .queryPackage(sContext.getPackageName())
                .whereService().serviceClass().isSameClassAs(EventLibService.class);
        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void launchEventLibService_withGeneratedServiceClass_logsServiceCreatedEventWithCorrectClassName() {
        Intent intent = new Intent();
        intent.setPackage(sContext.getPackageName());
        intent.setClassName(sContext.getPackageName(), GENERATED_SERVICE_CLASS_NAME);
        sContext.startService(intent);

        EventLogs<ServiceCreatedEvent> eventLogs = ServiceCreatedEvent
                .queryPackage(sContext.getPackageName())
                .whereService().serviceClass().className().isEqualTo(GENERATED_SERVICE_CLASS_NAME);
        assertThat(eventLogs.poll()).isNotNull();

        sContext.stopService(intent);
    }
}
