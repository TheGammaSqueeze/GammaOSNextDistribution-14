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

import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.os.UserManager.DISALLOW_CONFIG_PRIVATE_DNS;
import static android.os.UserManager.DISALLOW_NETWORK_RESET;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivitySettingsManager;
import android.os.UserManager;
import android.provider.Settings;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.DisallowNetworkReset;
import com.android.bedstead.harrier.policies.DisallowPrivateDnsConfig;
import com.android.bedstead.nene.TestApis;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

// TODO(b/189280629): Move this test to to net test folder to live with other network reset tests.
@RunWith(BedsteadJUnit4.class)
public final class NetworkResetTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final UserManager sUserManager = sContext.getSystemService(UserManager.class);
    private static final ConnectivityManager sConnectivityManager =
            sContext.getSystemService(ConnectivityManager.class);
    private boolean mOriginalAirplaneMode;
    private int mOriginalPrivateDnsMode;
    private int mOriginalAvoidBadWifi;

    @Before
    public void setUp() throws Exception {
        mOriginalAirplaneMode = getAirplaneMode();
        mOriginalPrivateDnsMode = getPrivateDnsMode();
        mOriginalAvoidBadWifi = getNetworkAvoidBadWifi();
    }

    @After
    public void tearDown() throws Exception {
        restoreSettings(mOriginalAirplaneMode, mOriginalPrivateDnsMode, mOriginalAvoidBadWifi);
    }

    // TODO: Add @PolicyDoesNotApplyTest

    @PolicyAppliesTest(policy = DisallowNetworkReset.class)
    @EnsureHasPermission({NETWORK_SETTINGS, WRITE_SECURE_SETTINGS})
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void factoryReset_disallowedByNetworkResetPolicy_doesNotFactoryReset() throws Exception {
        final boolean originalUserRestriction =
                sUserManager.hasUserRestriction(DISALLOW_NETWORK_RESET);
        try {
            sConnectivityManager.setAirplaneMode(true);
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_NETWORK_RESET);

            sConnectivityManager.factoryReset();

            // As the factory reset should have been rejected, it's expected that the airplane mode
            // should not be changed.
            assertThat(getAirplaneMode()).isTrue();
        } finally {
            restoreUserRestriction(originalUserRestriction, DISALLOW_NETWORK_RESET);
        }
    }

    @PolicyAppliesTest(policy = DisallowPrivateDnsConfig.class)
    @EnsureHasPermission({NETWORK_SETTINGS, WRITE_SECURE_SETTINGS})
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void factoryReset_disallowedByConfigPrivateDnsPolicy_doesPartialFactoryReset() {
        final boolean originalUserRestriction =
                sUserManager.hasUserRestriction(DISALLOW_CONFIG_PRIVATE_DNS);
        try {
            ConnectivitySettingsManager.setPrivateDnsMode(sContext,
                    ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF);
            sDeviceState.dpc().devicePolicyManager()
                    .addUserRestriction(
                            sDeviceState.dpc().componentName(), DISALLOW_CONFIG_PRIVATE_DNS);

            sConnectivityManager.factoryReset();

            // As setting private dns should be rejected, it's expected that private dns mode
            // should not be changed.
            assertThat(getPrivateDnsMode()).isEqualTo(
                    ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF);
        } finally {
            restoreUserRestriction(originalUserRestriction, DISALLOW_CONFIG_PRIVATE_DNS);
        }
    }

    //TODO(b/264642433): Restore to PolicyAppliesTest once Nene can set/unset user restrictions.
    @EnsureHasDeviceOwner
    @EnsureHasPermission({NETWORK_SETTINGS, WRITE_SECURE_SETTINGS})
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void factoryReset_noPolicyRestrictions_resetsToDefault() throws Exception {
        final boolean originalPrivateDnsUserRestriction =
                sUserManager.hasUserRestriction(DISALLOW_CONFIG_PRIVATE_DNS);
        final boolean originalNetworkResetUserRestriction =
                sUserManager.hasUserRestriction(DISALLOW_NETWORK_RESET);
        try {
            sConnectivityManager.setAirplaneMode(true);
            ConnectivitySettingsManager.setPrivateDnsMode(sContext,
                    ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF);
            // Ensure no policy set.
            //TODO(b/264642433): Use Nene API once available.
            sDeviceState.deviceOwner().devicePolicyManager()
                    .clearUserRestriction(
                            sDeviceState.dpc().componentName(), DISALLOW_CONFIG_PRIVATE_DNS);
            sDeviceState.deviceOwner().devicePolicyManager()
                    .clearUserRestriction(
                            sDeviceState.dpc().componentName(), DISALLOW_NETWORK_RESET);

            sConnectivityManager.factoryReset();

            // Verify settings reset to default setting.
            assertThat(getPrivateDnsMode()).isEqualTo(
                    ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC);
            assertThat(getAirplaneMode()).isFalse();
        } finally {
            restoreUserRestriction(originalPrivateDnsUserRestriction, DISALLOW_CONFIG_PRIVATE_DNS);
            restoreUserRestriction(originalNetworkResetUserRestriction, DISALLOW_NETWORK_RESET);
        }
    }

    private boolean getAirplaneMode() throws Exception {
        return Settings.Global.getInt(
                sContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON) != 0;
    }

    private int getPrivateDnsMode() {
        return ConnectivitySettingsManager.getPrivateDnsMode(sContext);
    }

    private int getNetworkAvoidBadWifi() {
        return ConnectivitySettingsManager.getNetworkAvoidBadWifi(sContext);
    }

    private void restoreSettings(boolean airplaneMode, int privateDnsMode, int avoidBadWifi) {
        sConnectivityManager.setAirplaneMode(airplaneMode);
        ConnectivitySettingsManager.setPrivateDnsMode(sContext, privateDnsMode);
        ConnectivitySettingsManager.setNetworkAvoidBadWifi(sContext, avoidBadWifi);
    }

    private void restoreUserRestriction(boolean originalUserRestriction, String policy) {
        if (originalUserRestriction) {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), policy);
        } else {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), policy);
        }
    }
}
