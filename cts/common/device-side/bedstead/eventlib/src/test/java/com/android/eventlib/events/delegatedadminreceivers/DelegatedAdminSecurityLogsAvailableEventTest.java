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

package com.android.eventlib.events.delegatedadminreceivers;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DelegatedAdminReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DelegatedAdminSecurityLogsAvailableEventTest {

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final String STRING_VALUE = "Value";
    private static final String DIFFERENT_STRING_VALUE = "Value2";
    private static final Intent INTENT = new Intent();

    private static final String DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            TestDelegatedAdminReceiver.class.getName();
    private static final String CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            "customDelegatedAdminReceiver";
    private static final String DIFFERENT_CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            "customDelegatedAdminReceiver2";
    private static final DelegatedAdminReceiver DEVICE_ADMIN_RECEIVER = new TestDelegatedAdminReceiver();

    private static class TestDelegatedAdminReceiver extends DelegatedAdminReceiver {
    }

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void whereIntent_works() {
        Intent intent = new Intent(STRING_VALUE);
        DelegatedAdminSecurityLogsAvailableEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, intent).log();

        EventLogs<DelegatedAdminSecurityLogsAvailableEvent> eventLogs =
                DelegatedAdminSecurityLogsAvailableEvent.queryPackage(sContext.getPackageName())
                        .whereIntent().action().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.poll().intent()).isEqualTo(intent);
    }

    @Test
    public void whereIntent_skipsNonMatching() {
        Intent intent = new Intent(STRING_VALUE);
        Intent differentIntent = new Intent();
        differentIntent.setAction(DIFFERENT_STRING_VALUE);
        DelegatedAdminSecurityLogsAvailableEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, differentIntent).log();
        DelegatedAdminSecurityLogsAvailableEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, intent).log();

        EventLogs<DelegatedAdminSecurityLogsAvailableEvent> eventLogs =
                DelegatedAdminSecurityLogsAvailableEvent.queryPackage(sContext.getPackageName())
                        .whereIntent().action().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.poll().intent()).isEqualTo(intent);
    }

    @Test
    public void whereDelegatedAdminReceiver_customValueOnLogger_works() {
        DelegatedAdminSecurityLogsAvailableEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setDelegatedAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<DelegatedAdminSecurityLogsAvailableEvent> eventLogs =
                DelegatedAdminSecurityLogsAvailableEvent.queryPackage(sContext.getPackageName())
                        .whereDelegatedAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().delegatedAdminReceiver().className()).isEqualTo(
                CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDelegatedAdminReceiver_customValueOnLogger_skipsNonMatching() {
        DelegatedAdminSecurityLogsAvailableEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setDelegatedAdminReceiver(DIFFERENT_CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();
        DelegatedAdminSecurityLogsAvailableEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setDelegatedAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<DelegatedAdminSecurityLogsAvailableEvent> eventLogs =
                DelegatedAdminSecurityLogsAvailableEvent.queryPackage(sContext.getPackageName())
                        .whereDelegatedAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().delegatedAdminReceiver().className()).isEqualTo(
                CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDelegatedAdminReceiver_defaultValue_works() {
        DelegatedAdminSecurityLogsAvailableEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT).log();

        EventLogs<DelegatedAdminSecurityLogsAvailableEvent> eventLogs =
                DelegatedAdminSecurityLogsAvailableEvent.queryPackage(sContext.getPackageName())
                        .whereDelegatedAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().delegatedAdminReceiver().className())
                .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDelegatedAdminReceiver_defaultValue_skipsNonMatching() {
        DelegatedAdminSecurityLogsAvailableEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setDelegatedAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();
        DelegatedAdminSecurityLogsAvailableEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .log();

        EventLogs<DelegatedAdminSecurityLogsAvailableEvent> eventLogs =
                DelegatedAdminSecurityLogsAvailableEvent.queryPackage(sContext.getPackageName())
                        .whereDelegatedAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().delegatedAdminReceiver().className())
                .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }
}
