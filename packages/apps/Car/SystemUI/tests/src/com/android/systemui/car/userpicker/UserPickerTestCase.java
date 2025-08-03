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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.content.pm.UserInfo;
import android.view.Display;
import android.view.DisplayAdjustments;
import android.view.DisplayInfo;
import android.view.LayoutInflater;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;

import org.junit.Before;

public abstract class UserPickerTestCase extends SysuiTestCase {
    static final int IDLE_TIMEOUT = 1_500;

    static final int USER_ID_DRIVER = 999;
    static final int USER_ID_FRONT = 1000;
    static final int USER_ID_REAR = 1001;
    static final int USER_ID_GUEST = 1010;

    static final String USER_NAME_DRIVER = "Driver";
    static final String USER_NAME_FRONT = "Front";
    static final String USER_NAME_REAR = "Rear";

    static final int MAIN_DISPLAY_ID = 0;
    static final int FRONT_PASSENGER_DISPLAY_ID = 2;
    static final int REAR_PASSENGER_DISPLAY_ID = 3;

    static final Display MAIN_DISPLAY = new Display(null,
            MAIN_DISPLAY_ID, new DisplayInfo(), new DisplayAdjustments());
    static final Display FRONT_PASSENGER_DISPLAY = new Display(null,
            FRONT_PASSENGER_DISPLAY_ID, new DisplayInfo(), new DisplayAdjustments());
    static final Display REAR_PASSENGER_DISPLAY = new Display(null,
            REAR_PASSENGER_DISPLAY_ID, new DisplayInfo(), new DisplayAdjustments());

    static final int ZONE_ID_DRIVER = 0;
    static final int ZONE_ID_FRONT = 1;
    static final int ZONE_ID_REAR = 2;

    final int mRefreshLayoutSize = 1200;

    final LayoutInflater mInflater = LayoutInflater.from(mContext);
    final String mGuestLabel = mContext.getString(R.string.car_guest);
    final String mAddLabel = mContext.getString(R.string.car_add_user);
    final String mLoggedinLabel = mContext.getString(R.string.logged_in_text);

    UserInfo mDriverUserInfo = new UserInfo(USER_ID_DRIVER, USER_NAME_DRIVER,
            UserInfo.FLAG_ADMIN | UserInfo.FLAG_FULL);
    UserInfo mFrontUserInfo = new UserInfo(USER_ID_FRONT, USER_NAME_FRONT, UserInfo.FLAG_FULL);
    UserInfo mRearUserInfo = new UserInfo(USER_ID_REAR, USER_NAME_REAR, UserInfo.FLAG_FULL);

    @Before
    public void setUpUserPickerTest() {
        spyOn(mContext);
        mContext.setTheme(R.style.Theme_UserPicker);
    }

    String getZoneDesc(OccupantZoneInfo zoneInfo) {
        StringBuilder seat = new StringBuilder(15);
        if (zoneInfo.occupantType == OCCUPANT_TYPE_DRIVER) {
            seat.append(mContext.getString(R.string.seat_driver));
        } else {
            if (zoneInfo.occupantType == OCCUPANT_TYPE_FRONT_PASSENGER) {
                seat.append(mContext.getString(R.string.seat_front));
            } else if (zoneInfo.occupantType == OCCUPANT_TYPE_REAR_PASSENGER) {
                seat.append(mContext.getString(R.string.seat_rear));
            }
            seat.append(" ");
            switch (zoneInfo.seat) {
                case SEAT_ROW_1_LEFT:
                case SEAT_ROW_2_LEFT:
                case SEAT_ROW_3_LEFT:
                    seat.append(mContext.getString(R.string.seat_left_side));
                    break;
                case SEAT_ROW_1_CENTER:
                case SEAT_ROW_2_CENTER:
                case SEAT_ROW_3_CENTER:
                    seat.append(mContext.getString(R.string.seat_center_side));
                    break;
                case SEAT_ROW_1_RIGHT:
                case SEAT_ROW_2_RIGHT:
                case SEAT_ROW_3_RIGHT:
                    seat.append(mContext.getString(R.string.seat_right_side));
                    break;
                default:
                    return null;
            }
        }
        return seat.toString();
    }
}
