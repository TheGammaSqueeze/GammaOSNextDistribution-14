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

package android.car.cts;

import static android.car.CarOccupantZoneManager.INVALID_USER_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarRemoteDeviceManager;
import android.car.CarRemoteDeviceManager.StateCallback;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Test relies on other server to connect to.")
@EnsureHasPermission(Car.PERMISSION_MANAGE_REMOTE_DEVICE)
public final class CarRemoteDeviceManagerTest extends AbstractCarTestCase {

    private static final String TAG = CarRemoteDeviceManagerTest.class.getSimpleName();
    private static final int WAIT_TIME_MS = 2000;

    private CarRemoteDeviceManager mRemoteDeviceManager;
    private CarOccupantZoneManager mOccupantZoneManager;
    private OccupantZoneInfo mMyZone;
    private List<OccupantZoneInfo> mAllZones;

    @Before
    public void setUp() throws Exception {
        mRemoteDeviceManager = getCar().getCarManager(CarRemoteDeviceManager.class);
        // CarRemoteDeviceManager is available on multi-display builds only.
        // TODO(b/265091454): annotate the test with @RequireMultipleUsersOnMultipleDisplays.
        assumeNotNull(
                "Skip the test because CarRemoteDeviceManager is not available on this build",
                mRemoteDeviceManager);

        mOccupantZoneManager = getCar().getCarManager(CarOccupantZoneManager.class);
        assumeTrue("It should have passenger zones to run the test",
                mOccupantZoneManager.hasPassengerZones());

        mMyZone = mOccupantZoneManager.getMyOccupantZone();
        mAllZones = mOccupantZoneManager.getAllOccupantZones();
    }

    @Test
    @ApiTest(apis = {"android.car.CarRemoteDeviceManager#registerStateCallback",
            "android.car.CarRemoteDeviceManager#unregisterStateCallback"})
    public void testStateCallback() {
        List<OccupantZoneInfo> activePeerZones = getActivePeerZones();
        assumeTrue("Skip the test because there is no passenger zone assigned with a valid user",
                !activePeerZones.isEmpty());

        List<OccupantZoneInfo> allPeerZones = new ArrayList<>(mAllZones);
        allPeerZones.remove(mMyZone);

        // Note: there is no need to guard the lists with a lock because the lists are updated and
        // verified on the same thread (main thread).
        List<OccupantZoneInfo> zoneStateChangedZones = new ArrayList<>();
        List<OccupantZoneInfo> appStateChangedZones = new ArrayList<>();

        mRemoteDeviceManager.registerStateCallback(mContext.getMainExecutor(), new StateCallback() {
            @Override
            public void onOccupantZoneStateChanged(@NonNull OccupantZoneInfo occupantZone,
                    int occupantZoneStates) {
                zoneStateChangedZones.add(occupantZone);
            }

            @Override
            public void onAppStateChanged(@NonNull OccupantZoneInfo occupantZone, int appStates) {
                appStateChangedZones.add(occupantZone);
            }
        });

        // onOccupantZoneStateChanged() should be invoked for all peer zones.
        PollingCheck.waitFor(WAIT_TIME_MS, () -> zoneStateChangedZones.equals(allPeerZones));
        // appStateChangedZones() should be invoked for all active peer zones.
        PollingCheck.waitFor(WAIT_TIME_MS, () -> appStateChangedZones.equals(activePeerZones));

        mRemoteDeviceManager.unregisterStateCallback();
    }

    @Test
    @ApiTest(apis = {"android.car.CarRemoteDeviceManager#getEndpointPackageInfo"})
    public void testGetEndpointPackageInfo() {
        PackageInfo myPackageInfo = mRemoteDeviceManager.getEndpointPackageInfo(mMyZone);

        assertThat(myPackageInfo).isNotNull();

        List<OccupantZoneInfo> allZones = mOccupantZoneManager.getAllOccupantZones();
        for (OccupantZoneInfo zone : allZones) {
            PackageInfo packageInfo = mRemoteDeviceManager.getEndpointPackageInfo(zone);

            // The package info of the peer apps should have the same package name & signing info
            // & long version code since they run on the same Android instance.
            // TODO(b/257118327): update this test for multi-SoC.
            if (packageInfo != null) {
                assertThat(packageInfo.packageName).isEqualTo(myPackageInfo.packageName);
                // SigningInfo doesn't override equals() method, so use a custom equals() method.
                assertThat(signingInfoEquals(packageInfo.signingInfo,
                        myPackageInfo.signingInfo)).isTrue();
                assertThat(packageInfo.getLongVersionCode())
                        .isEqualTo(myPackageInfo.getLongVersionCode());
            }
        }
    }

    @Test
    @ApiTest(apis = {"android.car.CarRemoteDeviceManager#setOccupantZonePower",
            "android.car.CarRemoteDeviceManager#isOccupantZonePowerOn"})
    @EnsureHasPermission({Car.PERMISSION_MANAGE_REMOTE_DEVICE, Car.PERMISSION_CAR_POWER})
    public void testSetOccupantZonePower() {
        for (OccupantZoneInfo zone : mAllZones) {
            if (zone.equals(mMyZone)
                    || zone.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                continue;
            }

            mRemoteDeviceManager.setOccupantZonePower(zone, false);
            PollingCheck.waitFor(WAIT_TIME_MS,
                    () -> !mRemoteDeviceManager.isOccupantZonePowerOn(zone));

            mRemoteDeviceManager.setOccupantZonePower(zone, true);
            PollingCheck.waitFor(WAIT_TIME_MS,
                    () -> mRemoteDeviceManager.isOccupantZonePowerOn(zone));
        }
    }

    /** Returns the peer occupant zones that have a non-system user assigned. */
    private List<OccupantZoneInfo> getActivePeerZones() {
        List<OccupantZoneInfo> activePeerZones = new ArrayList<>();
        for (int i = 0; i < mAllZones.size(); i++) {
            OccupantZoneInfo zone = mAllZones.get(i);
            if (zone.equals(mMyZone)) {
                continue;
            }
            int userId = mOccupantZoneManager.getUserForOccupant(zone);
            if (userId != INVALID_USER_ID && !UserHandle.of(userId).isSystem()) {
                activePeerZones.add(zone);
            }
        }
        return activePeerZones;
    }

    private static boolean signingInfoEquals(SigningInfo info1, SigningInfo info2) {
        if (info1 == info2) {
            return true;
        }
        if (info1 == null || info2 == null) {
            return false;
        }
        Signature[] signatures1 = info1.getSigningCertificateHistory();
        Signature[] signatures2 = info1.getSigningCertificateHistory();
        return Arrays.equals(signatures1, signatures2);
    }
}
