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

import static android.Manifest.permission.TRIGGER_LOST_MODE;
import static android.app.admin.DevicePolicyManager.ACTION_LOST_MODE_LOCATION_UPDATE;
import static android.app.admin.DevicePolicyManager.EXTRA_LOST_MODE_LOCATION;
import static android.content.Context.RECEIVER_EXPORTED;
import static android.location.LocationManager.FUSED_PROVIDER;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.LostMode;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.location.LocationProvider;
import com.android.bedstead.nene.location.Locations;
import com.android.bedstead.nene.permissions.PermissionContext;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
public final class LostModeLocationTest {

    private static final double TEST_LATITUDE = 51.5;
    private static final double TEST_LONGITUDE = -0.1;
    private static final float TEST_ACCURACY = 14.0f;
    private static final double TEST_LATITUDE_2 = 22.0;
    private static final double TEST_LONGITUDE_2 = -10.5;
    private static final float TEST_ACCURACY_2 = 15.0f;
    private static final int LOCATION_UPDATE_TIMEOUT_SECONDS = 180;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static final IntentFilter sFilter = new IntentFilter(ACTION_LOST_MODE_LOCATION_UPDATE);

    @Before
    public void setUp() {
        TestApis.location().setLocationEnabled(true);
        sDeviceState.dpc().registerReceiver(sFilter, RECEIVER_EXPORTED);
    }

    @After
    public void tearDown() throws Exception {
        sDeviceState.dpc().unregisterReceiver(sFilter);
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LostMode.class)
    @EnsureDoesNotHavePermission(TRIGGER_LOST_MODE)
    public void sendLostModeLocationUpdate_withoutPermission_throwsException() throws Exception {
        assertThrows(SecurityException.class,
                () -> sLocalDevicePolicyManager.sendLostModeLocationUpdate(
                        sContext.getMainExecutor(),
                        new Locations.BlockingLostModeLocationUpdateCallback()));
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = LostMode.class)
    public void sendLostModeLocationUpdate_noLocation_returnFalse() throws Exception {
        try {
            TestApis.location().setLocationEnabled(false);
            sendLostModeLocationUpdate(/* expected= */ false);
        } finally {
            TestApis.location().setLocationEnabled(true);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = LostMode.class)
    public void sendLostModeLocationUpdate_returnTrueAndSendLocationUpdate()
            throws Exception {
        try (LocationProvider provider = TestApis.location().addLocationProvider(FUSED_PROVIDER)) {
            provider.setLocation(TEST_LATITUDE, TEST_LONGITUDE, TEST_ACCURACY);

            sendLostModeLocationUpdate(/* expected= */ true);

            final Intent receivedIntent = sDeviceState.dpc().events().broadcastReceived()
                    .whereIntent().action()
                    .isEqualTo(ACTION_LOST_MODE_LOCATION_UPDATE)
                    .poll().intent();
            assertThat(receivedIntent).isNotNull();

            final Location receivedLocation =
                    receivedIntent.getParcelableExtra(EXTRA_LOST_MODE_LOCATION);
            assertThat(receivedLocation.getLatitude()).isEqualTo(TEST_LATITUDE);
            assertThat(receivedLocation.getLongitude()).isEqualTo(TEST_LONGITUDE);
            assertThat(receivedLocation.getAccuracy()).isEqualTo(TEST_ACCURACY);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = LostMode.class)
    public void sendLostModeLocationUpdate_sendMostRecentLocation() throws Exception {
        try (LocationProvider provider = TestApis.location().addLocationProvider(FUSED_PROVIDER)) {
            provider.setLocation(TEST_LATITUDE, TEST_LONGITUDE, TEST_ACCURACY);
            provider.setLocation(TEST_LATITUDE_2, TEST_LONGITUDE_2, TEST_ACCURACY_2);

            sendLostModeLocationUpdate(/* expected= */ true);

            final Intent receivedIntent = sDeviceState.dpc().events().broadcastReceived()
                    .whereIntent().action()
                    .isEqualTo(ACTION_LOST_MODE_LOCATION_UPDATE)
                    .poll().intent();
            assertThat(receivedIntent).isNotNull();

            final Location receivedLocation =
                    receivedIntent.getParcelableExtra(EXTRA_LOST_MODE_LOCATION);
            assertThat(receivedLocation.getLatitude()).isEqualTo(TEST_LATITUDE_2);
            assertThat(receivedLocation.getLongitude()).isEqualTo(TEST_LONGITUDE_2);
            assertThat(receivedLocation.getAccuracy()).isEqualTo(TEST_ACCURACY_2);
        }
    }

    private void sendLostModeLocationUpdate(boolean expected) throws InterruptedException {
        Locations.BlockingLostModeLocationUpdateCallback callback =
                new Locations.BlockingLostModeLocationUpdateCallback();
        try (PermissionContext p = TestApis.permissions().withPermission(TRIGGER_LOST_MODE)) {
            sLocalDevicePolicyManager.sendLostModeLocationUpdate(sContext.getMainExecutor(),
                    callback);
        }

        assertThat(callback.await(LOCATION_UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(expected);
    }
}
