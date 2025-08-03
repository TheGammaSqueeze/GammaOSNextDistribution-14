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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_192;
import static android.app.admin.DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_EAP;
import static android.app.admin.DevicePolicyManager.WIFI_SECURITY_OPEN;
import static android.app.admin.DevicePolicyManager.WIFI_SECURITY_PERSONAL;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.RemoteDevicePolicyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.WifiMinimumSecurity;
import com.android.bedstead.remotedpc.RemotePolicyManager;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@RunWith(BedsteadJUnit4.class)
public class WifiMinimumSecurityTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private RemoteDevicePolicyManager mDevicePolicyManager;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = sDeviceState.dpc();
        mDevicePolicyManager = dpc.devicePolicyManager();
    }

    @IntTestParameter({
            WIFI_SECURITY_ENTERPRISE_192,
            WIFI_SECURITY_ENTERPRISE_EAP,
            WIFI_SECURITY_OPEN,
            WIFI_SECURITY_PERSONAL})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface SettableWifiSecurityLevelTestParameter {
    }

    @PolicyAppliesTest(policy = WifiMinimumSecurity.class)
    @Postsubmit(reason = "new test")
    public void setWifiMinimumSecurity_validLevel_works(
            @SettableWifiSecurityLevelTestParameter int flag) {
        try {
            mDevicePolicyManager.setMinimumRequiredWifiSecurityLevel(flag);
            assertThat(mDevicePolicyManager.getMinimumRequiredWifiSecurityLevel())
                    .isEqualTo(flag);
        } finally {
            mDevicePolicyManager.setMinimumRequiredWifiSecurityLevel(WIFI_SECURITY_PERSONAL);
        }
    }

    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = WifiMinimumSecurity.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setWifiMinimumSecurity_invalidAdmin_fails() {
        assertThrows(SecurityException.class, () ->
                mDevicePolicyManager.setMinimumRequiredWifiSecurityLevel(
                        WIFI_SECURITY_ENTERPRISE_EAP));
    }
}
