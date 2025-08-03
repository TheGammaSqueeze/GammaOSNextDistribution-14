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
package com.android.systemui.car.userpicker;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_CENTER;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_LEFT;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_RIGHT;
import static android.car.VehicleAreaSeat.SEAT_ROW_2_CENTER;
import static android.car.VehicleAreaSeat.SEAT_ROW_2_LEFT;
import static android.car.VehicleAreaSeat.SEAT_ROW_2_RIGHT;
import static android.car.VehicleAreaSeat.SEAT_ROW_3_CENTER;
import static android.car.VehicleAreaSeat.SEAT_ROW_3_LEFT;
import static android.car.VehicleAreaSeat.SEAT_ROW_3_RIGHT;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.hardware.power.CarPowerManager;
import android.car.user.CarUserManager;
import android.content.pm.UserInfo;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Display;
import android.view.DisplayAdjustments;
import android.view.DisplayInfo;

import androidx.test.filters.SmallTest;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarServiceMediatorTest extends UserPickerTestCase {
    private CarServiceMediator mCarServiceMediator;

    @Mock
    private Car mMockCar;
    @Mock
    private CarOccupantZoneManager mMockCarOccupantZoneManager;
    @Mock
    private CarUserManager mMockCarUserManager;
    @Mock
    private CarPowerManager mMockCarPowerManager;
    @Mock
    private CarServiceProvider mMockCarServiceProvider;

    private List<OccupantZoneInfo> mOccupantZoneInfos = new ArrayList<OccupantZoneInfo>();
    private OccupantZoneInfo mDriverZoneInfo = new OccupantZoneInfo(/* zoneId= */ ZONE_ID_DRIVER,
            /* occupantType= */ OCCUPANT_TYPE_DRIVER, /* seat= */ SEAT_ROW_1_LEFT);
    private OccupantZoneInfo mFrontPassengerZoneInfo = new OccupantZoneInfo(
            /* zoneId= */ ZONE_ID_FRONT, /* occupantType= */ OCCUPANT_TYPE_FRONT_PASSENGER,
            /* seat= */ SEAT_ROW_1_RIGHT);
    private OccupantZoneInfo mRearPassengerZoneInfo = new OccupantZoneInfo(
            /* zoneId= */ ZONE_ID_REAR, /* occupantType= */ OCCUPANT_TYPE_REAR_PASSENGER,
            /* seat= */ SEAT_ROW_2_LEFT);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        doReturn(mMockCarOccupantZoneManager).when(mMockCar)
                .getCarManager(CarOccupantZoneManager.class);
        doReturn(mMockCarUserManager).when(mMockCar).getCarManager(CarUserManager.class);
        doReturn(mMockCarPowerManager).when(mMockCar).getCarManager(CarPowerManager.class);
        doReturn(mOccupantZoneInfos).when(mMockCarOccupantZoneManager).getAllOccupantZones();

        mCarServiceMediator = new CarServiceMediator(mContext, mMockCarServiceProvider);
        mCarServiceMediator.onConnect(mMockCar);
    }

    @Test
    public void checkUserSeatDescription_byZoneInfo_validSeatDescription() {
        int[] seatDatas = { SEAT_ROW_1_LEFT, SEAT_ROW_1_CENTER, SEAT_ROW_1_RIGHT,
                            SEAT_ROW_2_LEFT, SEAT_ROW_2_CENTER, SEAT_ROW_2_RIGHT,
                            SEAT_ROW_3_LEFT, SEAT_ROW_3_CENTER, SEAT_ROW_3_RIGHT };
        OccupantZoneInfo[] zones = new OccupantZoneInfo[seatDatas.length - 1];
        mOccupantZoneInfos.clear();
        mOccupantZoneInfos.add(mDriverZoneInfo);
        int startId = USER_ID_DRIVER + 1;
        for (int i = 0; i < (seatDatas.length - 1); i++) {
            zones[i] = new OccupantZoneInfo(/* zoneId= */ i + 1,
                    /* occupantType= */((i < 2)
                    ? OCCUPANT_TYPE_FRONT_PASSENGER : OCCUPANT_TYPE_REAR_PASSENGER),
                    /* seat= */ seatDatas[i + 1]);
            mOccupantZoneInfos.add(zones[i]);
            UserInfo userInfo = new UserInfo(startId + i, " ", UserInfo.FLAG_FULL);
            Display display =
                    new Display(null, (i + 1), new DisplayInfo(), new DisplayAdjustments());
            doReturn(List.of(display)).when(mMockCarOccupantZoneManager)
                    .getAllDisplaysForOccupant(zones[i]);
        }

        for (int i = 0; i < seatDatas.length - 1; i++) {
            assertThat(mCarServiceMediator.getSeatString(i + 1))
                    .isEqualTo(getZoneDesc(zones[i]));
        }
    }

    @Test
    public void checkunassignZone_requestWithDisplayId_requestUnassignOccupantZone() {
        mOccupantZoneInfos.clear();
        mOccupantZoneInfos.add(mFrontPassengerZoneInfo);
        doReturn(List.of(FRONT_PASSENGER_DISPLAY)).when(mMockCarOccupantZoneManager)
                    .getAllDisplaysForOccupant(mFrontPassengerZoneInfo);

        mCarServiceMediator.unassignOccupantZoneForDisplay(FRONT_PASSENGER_DISPLAY_ID);

        verify(mMockCarOccupantZoneManager).unassignOccupantZone(eq(mFrontPassengerZoneInfo));
    }

    @Test
    public void checkDisplayIdForUser_requestWithUserId_getDisplayId() {
        mOccupantZoneInfos.clear();
        mOccupantZoneInfos.add(mRearPassengerZoneInfo);
        doReturn(USER_ID_REAR).when(mMockCarOccupantZoneManager)
                .getUserForOccupant(mRearPassengerZoneInfo);
        doReturn(REAR_PASSENGER_DISPLAY).when(mMockCarOccupantZoneManager)
                .getDisplayForOccupant(mRearPassengerZoneInfo, DISPLAY_TYPE_MAIN);

        int result = mCarServiceMediator.getDisplayIdForUser(USER_ID_REAR);

        assertThat(result).isEqualTo(REAR_PASSENGER_DISPLAY_ID);
    }

    @Test
    public void checkDisplayIdForUser_requestWithUserId_getUserId() {
        mOccupantZoneInfos.clear();
        mOccupantZoneInfos.add(mRearPassengerZoneInfo);
        doReturn(USER_ID_REAR).when(mMockCarOccupantZoneManager)
                .getUserForDisplayId(REAR_PASSENGER_DISPLAY_ID);

        int result = mCarServiceMediator.getUserForDisplay(REAR_PASSENGER_DISPLAY_ID);

        assertThat(result).isEqualTo(USER_ID_REAR);
    }
}
