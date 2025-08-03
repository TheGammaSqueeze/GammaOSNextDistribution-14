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

import static android.car.CarOccupantZoneManager.OccupantZoneInfo;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.CarOccupantZoneManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * This class contains security permission tests for {@link CarOccupantZoneManager}.
 */
@RunWith(AndroidJUnit4.class)
public class CarOccupantZoneManagerPermissionTest extends AbstractCarManagerPermissionTest {

    private static final int ANY_ZONE_ID = 0;
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private OccupantZoneInfo mAnyOccupantZone;

    @Before
    public void setUp() throws Exception {
        super.connectCar();
        mCarOccupantZoneManager = (CarOccupantZoneManager) mCar.getCarManager(
                Car.CAR_OCCUPANT_ZONE_SERVICE);

        List<OccupantZoneInfo> occupantZones = mCarOccupantZoneManager.getAllOccupantZones();
        assertThat(occupantZones).isNotEmpty();
        mAnyOccupantZone = occupantZones.get(0);
    }

    @Test
    public void testDisplayIdForDriver() {
        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarOccupantZoneManager.getDisplayIdForDriver(
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN));
        assertThat(thrown.getMessage()).isEqualTo(
                "requires permission " + Car.ACCESS_PRIVATE_DISPLAY_ID);
    }

    @Test
    public void testGetAudioZoneIdForOccupant() {
        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarOccupantZoneManager.getAudioZoneIdForOccupant(mAnyOccupantZone));
        assertThat(thrown.getMessage()).isEqualTo(
                "requires permission " + Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
    }

    @Test
    public void testGetOccupantForAudioZoneId() {
        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarOccupantZoneManager.getOccupantForAudioZoneId(/* audioZoneId = */
                        ANY_ZONE_ID));
        assertThat(thrown.getMessage()).isEqualTo(
                "requires permission " + Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
    }

    @Test
    public void testAssignProfileUserToOccupantZone() {
        SecurityException thrown = assertThrows(SecurityException.class,
                () -> mCarOccupantZoneManager.assignProfileUserToOccupantZone(
                        mAnyOccupantZone, /* audioZoneId = */ ANY_ZONE_ID));
        assertThat(thrown.getMessage()).isEqualTo(getPermissionFailureMsg());
    }

    private String getPermissionFailureMsg() {
        return "requires any of [android.permission.MANAGE_USERS, android.car.permission"
                + ".MANAGE_OCCUPANT_ZONE]";
    }
}
