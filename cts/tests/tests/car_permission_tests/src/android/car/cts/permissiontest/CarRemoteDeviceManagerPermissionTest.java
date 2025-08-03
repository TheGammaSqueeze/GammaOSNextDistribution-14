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

package android.car.cts.permissiontest;

import static android.car.Car.PERMISSION_CAR_POWER;
import static android.car.Car.PERMISSION_MANAGE_REMOTE_DEVICE;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_LEFT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarRemoteDeviceManager;
import android.car.CarRemoteDeviceManager.StateCallback;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * This class contains security permission tests for {@link CarRemoteDeviceManager}' system
 * APIs.
 */
@RunWith(AndroidJUnit4.class)
public final class CarRemoteDeviceManagerPermissionTest extends AbstractCarManagerPermissionTest {
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private CarRemoteDeviceManager mCarRemoteDeviceManager;
    private OccupantZoneInfo mReceiverZone;
    private StateCallback mStateCallback;

    @Before
    public void setUp() {
        super.connectCar();
        mCarRemoteDeviceManager = mCar.getCarManager(CarRemoteDeviceManager.class);
        // CarRemoteDeviceManager is available on multi-display builds only.
        // TODO(b/265091454): annotate the test with @RequireMultipleUsersOnMultipleDisplays.
        assumeNotNull(
                "Skip the test because CarRemoteDeviceManager is not available on this build",
                mCarRemoteDeviceManager);

        mReceiverZone = new OccupantZoneInfo(/* zoneId= */ 0, OCCUPANT_TYPE_DRIVER,
                SEAT_ROW_1_LEFT);
        mStateCallback = new StateCallback() {
            @Override
            public void onOccupantZoneStateChanged(@NonNull OccupantZoneInfo occupantZone,
                    int occupantZoneStates) {
            }

            @Override
            public void onAppStateChanged(@NonNull OccupantZoneInfo occupantZone, int appStates) {
            }
        };
    }

    @Test
    public void testRegisterOccupantZoneStateCallback() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarRemoteDeviceManager.registerStateCallback(Runnable::run, mStateCallback));

        assertThat(e).hasMessageThat().contains(PERMISSION_MANAGE_REMOTE_DEVICE);
    }

    @Test
    public void testUnregisterStateCallback() {
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_REMOTE_DEVICE);
        mCarRemoteDeviceManager.registerStateCallback(Runnable::run, mStateCallback);
        mUiAutomation.dropShellPermissionIdentity();

        Exception e = assertThrows(SecurityException.class,
                () -> mCarRemoteDeviceManager.unregisterStateCallback());

        assertThat(e).hasMessageThat().contains(PERMISSION_MANAGE_REMOTE_DEVICE);
    }

    @Test
    public void testGetEndpointPackageInfo() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarRemoteDeviceManager.getEndpointPackageInfo(mReceiverZone));

        assertThat(e).hasMessageThat().contains(PERMISSION_MANAGE_REMOTE_DEVICE);
    }

    @Test
    public void testSetOccupantZonePower_withoutManageRemoteDevicePermission() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarRemoteDeviceManager.setOccupantZonePower(mReceiverZone,
                        /* powerOn= */ true));

        assertThat(e).hasMessageThat().contains(PERMISSION_MANAGE_REMOTE_DEVICE);
    }

    @Test
    public void testSetOccupantZonePower_withoutCarPowerPermission() {
        List<OccupantZoneInfo> otherPassengerZones = getOtherPassengerZones();
        assumeTrue("No passenger zones", !otherPassengerZones.isEmpty());

        // Grant PERMISSION_MANAGE_REMOTE_DEVICE to the test.
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_REMOTE_DEVICE);
        Exception e = assertThrows(SecurityException.class,
                () -> mCarRemoteDeviceManager.setOccupantZonePower(otherPassengerZones.get(0),
                        /* powerOn= */ true));
        mUiAutomation.dropShellPermissionIdentity();

        // Verify that it needs another permission PERMISSION_CAR_POWER.
        assertThat(e).hasMessageThat().contains(PERMISSION_CAR_POWER);
    }

    @Test
    public void testIsOccupantZonePowerOn() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarRemoteDeviceManager.isOccupantZonePowerOn(mReceiverZone));

        assertThat(e).hasMessageThat().contains(PERMISSION_MANAGE_REMOTE_DEVICE);
    }

    private List<OccupantZoneInfo> getOtherPassengerZones() {
        CarOccupantZoneManager occupantZoneManager =
                mCar.getCarManager(CarOccupantZoneManager.class);
        OccupantZoneInfo myZone = occupantZoneManager.getMyOccupantZone();
        List<OccupantZoneInfo> otherPassengerZones = occupantZoneManager.getAllOccupantZones();
        for (int i = otherPassengerZones.size() - 1; i >= 0; i--) {
            OccupantZoneInfo zone = otherPassengerZones.get(i);
            if (zone.equals(myZone) || zone.occupantType == OCCUPANT_TYPE_DRIVER) {
                otherPassengerZones.remove(i);
            }
        }
        return otherPassengerZones;
    }
}
