/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.app.admin.RemoteDevicePolicyManager;
import android.app.admin.WifiSsidPolicy;
import android.net.wifi.WifiSsid;
import android.util.ArraySet;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.WifiSsidRestriction;
import com.android.bedstead.remotedpc.RemotePolicyManager;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.testng.Assert;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public class WifiSsidRestrictionTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private RemoteDevicePolicyManager mDevicePolicyManager;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = sDeviceState.dpc();
        mDevicePolicyManager = dpc.devicePolicyManager();
    }

    @CanSetPolicyTest(policy = WifiSsidRestriction.class)
    @Postsubmit(reason = "new test")
    public void setWifiSsidPolicy_validAllowlist_works() {
        try {
            final Set<WifiSsid> ssids = new ArraySet<>(
                    Arrays.asList(WifiSsid.fromBytes("ssid1".getBytes(StandardCharsets.UTF_8)),
                            WifiSsid.fromBytes("ssid2".getBytes(StandardCharsets.UTF_8)),
                            WifiSsid.fromBytes("ssid3".getBytes(StandardCharsets.UTF_8))));
            WifiSsidPolicy policy = new WifiSsidPolicy(
                    WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST, ssids);

            mDevicePolicyManager.setWifiSsidPolicy(policy);

            assertThat(mDevicePolicyManager.getWifiSsidPolicy().getSsids()).isEqualTo(ssids);
            assertThat(mDevicePolicyManager.getWifiSsidPolicy().getPolicyType()).isEqualTo(
                    WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST);
        } finally {
            mDevicePolicyManager.setWifiSsidPolicy(null);
        }

    }

    @CanSetPolicyTest(policy = WifiSsidRestriction.class)
    @Postsubmit(reason = "new test")
    public void setWifiSsidPolicy_validDenylist_works() {
        try {
            final Set<WifiSsid> ssids = new ArraySet<>(
                    Arrays.asList(WifiSsid.fromBytes("ssid1".getBytes(StandardCharsets.UTF_8)),
                            WifiSsid.fromBytes("ssid2".getBytes(StandardCharsets.UTF_8)),
                            WifiSsid.fromBytes("ssid3".getBytes(StandardCharsets.UTF_8))));
            WifiSsidPolicy policy = new WifiSsidPolicy(
                    WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST, ssids);

            mDevicePolicyManager.setWifiSsidPolicy(policy);

            assertThat(mDevicePolicyManager.getWifiSsidPolicy().getSsids()).isEqualTo(ssids);
            assertThat(mDevicePolicyManager.getWifiSsidPolicy().getPolicyType()).isEqualTo(
                    WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST);
        } finally {
            mDevicePolicyManager.setWifiSsidPolicy(null);
        }
    }

    @CanSetPolicyTest(policy = WifiSsidRestriction.class)
    @Postsubmit(reason = "new test")
    public void setWifiSsidPolicy_validRemoveRestriction_works() {
        try {
            final Set<WifiSsid> ssids = new ArraySet<>(
                    Arrays.asList(WifiSsid.fromBytes("ssid1".getBytes(StandardCharsets.UTF_8))));
            WifiSsidPolicy policy = new WifiSsidPolicy(
                    WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST, ssids);

            mDevicePolicyManager.setWifiSsidPolicy(policy);

            assertThat(mDevicePolicyManager.getWifiSsidPolicy().getSsids()).isEqualTo(ssids);
            assertThat(mDevicePolicyManager.getWifiSsidPolicy().getPolicyType()).isEqualTo(
                    WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST);

            mDevicePolicyManager.setWifiSsidPolicy(null);

            assertNull(mDevicePolicyManager.getWifiSsidPolicy());
        } finally {
            mDevicePolicyManager.setWifiSsidPolicy(null);
        }
    }

    @CanSetPolicyTest(policy = WifiSsidRestriction.class)
    @Postsubmit(reason = "new test")
    public void setWifiSsidPolicy_invalidPolicy_fails() {
        final Set<WifiSsid> ssids = new ArraySet<>();
        Assert.assertThrows(IllegalArgumentException.class,
                () -> new WifiSsidPolicy(WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST, ssids));
    }

    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = WifiSsidRestriction.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setWifiSsidPolicy_invalidAdmin_fails() {
        final Set<WifiSsid> ssids = new ArraySet<>(
                Arrays.asList(WifiSsid.fromBytes("ssid1".getBytes(StandardCharsets.UTF_8))));
        WifiSsidPolicy policy = new WifiSsidPolicy(
                WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST, ssids);

        assertThrows(SecurityException.class, () -> mDevicePolicyManager.setWifiSsidPolicy(policy));
    }
}
