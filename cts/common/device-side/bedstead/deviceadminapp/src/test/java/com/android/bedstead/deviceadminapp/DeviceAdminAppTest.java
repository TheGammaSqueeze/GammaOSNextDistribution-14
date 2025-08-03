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

package com.android.bedstead.deviceadminapp;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class DeviceAdminAppTest {

    private static final Context sContext = TestApis.context().instrumentedContext();

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // This test assumes that DeviceAdminApp is set as a dependency of the test

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    @RequireRunOnSystemUser
    // TODO(scottjonathan): Add annotations to ensure no accounts and no users
    public void setAsDeviceOwner_isEnabled() throws Exception {
        try (DeviceOwner deviceOwner = TestApis.devicePolicy().setDeviceOwner(
                DeviceAdminApp.deviceAdminComponentName(sContext))) {

            EventLogs<DeviceAdminEnabledEvent> logs =
                    DeviceAdminEnabledEvent.queryPackage(sContext.getPackageName());
            assertThat(logs.poll()).isNotNull();
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasNoWorkProfile
    @Ignore
    public void setAsProfileOwner_isEnabled() {
        try (UserReference profile = TestApis.users().createUser()
                .parent(TestApis.users().instrumented())
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart()) {
            TestApis.packages().find(sContext.getPackageName()).installExisting(profile);

            TestApis.devicePolicy().setProfileOwner(
                    profile, DeviceAdminApp.deviceAdminComponentName(sContext));

            EventLogs<DeviceAdminEnabledEvent> logs =
                    DeviceAdminEnabledEvent.queryPackage(sContext.getPackageName())
                    .onUser(profile);
            assertThat(logs.poll()).isNotNull();
        }
    }
}
