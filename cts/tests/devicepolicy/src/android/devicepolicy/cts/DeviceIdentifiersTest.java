/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.devicepolicy.cts;

import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireHandheldDevice;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.roles.RoleContext;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO(b/297181825): add test for SubscriptionManager#getActiveSubscriptionInfo and Build#getSerial
@RunWith(BedsteadJUnit4.class)
public final class DeviceIdentifiersTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String SMS_ROLE = "android.app.role.SMS";
    private static final TestApp sSmsTestApp =
            sDeviceState.testApps().query().whereActivities().contains(
                    activity().where().intentFilters().contains(
                            intentFilter().where().actions().contains("android.intent.action.SEND")
                    )).get();

    @ApiTest(apis = "android.telephony.TelephonyManager#getDeviceId")
    @Postsubmit(reason = "new test")
    @RequireHandheldDevice
    @Test
    public void getDeviceId_smsAppReturnsSameValue() {
        String deviceId = TestApis.telephony().getDeviceId();

        try (TestAppInstance testApp = sSmsTestApp.install();
             RoleContext r = TestApis.packages().find(testApp.packageName()).setAsRoleHolder(
                     SMS_ROLE)) {
            assertThat(testApp.telephonyManager().getDeviceId()).isEqualTo(deviceId);
        }
    }

    @ApiTest(apis = "android.telephony.TelephonyManager#getImei")
    @Postsubmit(reason = "new test")
    @RequireHandheldDevice
    @Test
    public void getImei_withReadPrivilegedPhoneStatePermission_withReadPhoneStatePermission_returnsSameValue() {
        String deviceId = TestApis.telephony().getImei();

        try (TestAppInstance testApp = sSmsTestApp.install();
             RoleContext r = TestApis.packages().find(testApp.packageName()).setAsRoleHolder(
                     SMS_ROLE)) {
            assertThat(testApp.telephonyManager().getImei()).isEqualTo(deviceId);
        }
    }

    @ApiTest(apis = "android.telephony.TelephonyManager#getMeid")
    @Postsubmit(reason = "new test")
    @RequireHandheldDevice
    @Test
    public void getMeid_withReadPrivilegedPhoneStatePermission_withReadPhoneStatePermission_returnsSameValue() {
        String deviceId = TestApis.telephony().getMeid();

        try (TestAppInstance testApp = sSmsTestApp.install();
             RoleContext r = TestApis.packages().find(testApp.packageName()).setAsRoleHolder(
                     SMS_ROLE)) {
            assertThat(testApp.telephonyManager().getMeid()).isEqualTo(deviceId);
        }
    }

    @ApiTest(apis = "android.telephony.TelephonyManager#getSubscriberId")
    @Postsubmit(reason = "new test")
    @RequireHandheldDevice
    @Test
    public void getSubscriberId_withReadPrivilegedPhoneStatePermission_withReadPhoneStatePermission_returnsSameValue() {
        String subscriberId = TestApis.telephony().getSubscriberId();

        try (TestAppInstance testApp = sSmsTestApp.install();
             RoleContext r = TestApis.packages().find(testApp.packageName()).setAsRoleHolder(
                     SMS_ROLE)) {
            assertThat(testApp.telephonyManager().getSubscriberId()).isEqualTo(subscriberId);
        }
    }

    @ApiTest(apis = "android.telephony.TelephonyManager#getSimSerialNumber")
    @Postsubmit(reason = "new test")
    @RequireHandheldDevice
    @Test
    public void getSimSerialNumber_withReadPrivilegedPhoneStatePermission_withReadPhoneStatePermission_returnsSameValue() {
        String simSerialNumber = TestApis.telephony().getSimSerialNumber();

        try (TestAppInstance testApp = sSmsTestApp.install();
             RoleContext r = TestApis.packages().find(testApp.packageName()).setAsRoleHolder(
                     SMS_ROLE)) {
            assertThat(testApp.telephonyManager().getSimSerialNumber()).isEqualTo(simSerialNumber);
        }
    }

    @ApiTest(apis = "android.telephony.TelephonyManager#getNai")
    @Postsubmit(reason = "new test")
    @RequireHandheldDevice
    @Test
    public void getNai_withReadPrivilegedPhoneStatePermission_withReadPhoneStatePermission_returnsSameValue() {
        String nai = TestApis.telephony().getNai();

        try (TestAppInstance testApp = sSmsTestApp.install();
             RoleContext r = TestApis.packages().find(testApp.packageName()).setAsRoleHolder(
                     SMS_ROLE)) {
            assertThat(testApp.telephonyManager().getNai()).isEqualTo(nai);
        }
    }
}
