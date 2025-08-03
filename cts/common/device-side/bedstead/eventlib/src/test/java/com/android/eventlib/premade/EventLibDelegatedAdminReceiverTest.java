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
import android.net.Uri;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminChoosePrivateKeyAliasEvent;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminSecurityLogsAvailableEvent;
import com.android.eventlib.events.deviceadminreceivers.DelegatedAdminNetworkLogsAvailableEvent;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class EventLibDelegatedAdminReceiverTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final Intent sIntent = new Intent();
    private static final int UID = 1;
    private static final Uri URI = Uri.parse("http://uri");
    private static final String ALIAS = "alias";
    private static final long BATCH_TOKEN = 1;
    private static final int NETWORK_LOGS_COUNT = 1;

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void choosePrivateKeyAlias_logsChoosePrivateKeyAliasEvent() {
        EventLibDelegatedAdminReceiver receiver = new EventLibDelegatedAdminReceiver();

        receiver.onChoosePrivateKeyAlias(sContext, sIntent, UID, URI, ALIAS);

        EventLogs<DelegatedAdminChoosePrivateKeyAliasEvent> eventLogs =
                DelegatedAdminChoosePrivateKeyAliasEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void securityLogsAvailable_logsSecurityLogsAvailableEvent() {
        EventLibDelegatedAdminReceiver receiver = new EventLibDelegatedAdminReceiver();

        receiver.onSecurityLogsAvailable(sContext, sIntent);

        EventLogs<DelegatedAdminSecurityLogsAvailableEvent> eventLogs =
                DelegatedAdminSecurityLogsAvailableEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void networkLogsAvailable_logsNetworksLogsAvailableEvent() {
        EventLibDelegatedAdminReceiver receiver = new EventLibDelegatedAdminReceiver();

        receiver.onNetworkLogsAvailable(sContext, sIntent, BATCH_TOKEN, NETWORK_LOGS_COUNT);

        EventLogs<DelegatedAdminNetworkLogsAvailableEvent> eventLogs =
                DelegatedAdminNetworkLogsAvailableEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

}
