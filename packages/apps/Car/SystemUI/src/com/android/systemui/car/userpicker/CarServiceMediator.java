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
import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER;
import static android.car.CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_CENTER;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_LEFT;
import static android.car.VehicleAreaSeat.SEAT_ROW_1_RIGHT;
import static android.car.VehicleAreaSeat.SEAT_ROW_2_CENTER;
import static android.car.VehicleAreaSeat.SEAT_ROW_2_LEFT;
import static android.car.VehicleAreaSeat.SEAT_ROW_2_RIGHT;
import static android.car.VehicleAreaSeat.SEAT_ROW_3_CENTER;
import static android.car.VehicleAreaSeat.SEAT_ROW_3_LEFT;
import static android.car.VehicleAreaSeat.SEAT_ROW_3_RIGHT;
import static android.view.Display.INVALID_DISPLAY;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.hardware.power.CarPowerManager;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.util.Pair;
import android.util.Slog;
import android.view.Display;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;

@UserPickerScope
final class CarServiceMediator {
    private static final String TAG = CarServiceMediator.class.getSimpleName();

    // These are needed when getting the string of the seat for users.
    private String mSeatDriver;
    private String mSeatFront;
    private String mSeatRear;
    private String mSeatLeftSide;
    private String mSeatCenterSide;
    private String mSeatRightSide;

    private CarOccupantZoneManager mCarOccupantZoneManager;
    private CarUserManager mCarUserManager;
    private CarPowerManager mCarPowerManager;

    private final Context mContext;
    private final CarServiceProvider mCarServiceProvider;
    private final Map<UserLifecycleListener, Pair<Executor, UserLifecycleEventFilter>>
            mUserLifecycleListeners = new HashMap<>();

    private final CarServiceProvider.CarServiceOnConnectedListener mServiceOnConnectedListener =
            new CarServiceProvider.CarServiceOnConnectedListener() {
                @Override
                public void onConnected(Car car) {
                    onConnect(car);
                }
            };

    @Inject
    CarServiceMediator(Context context, CarServiceProvider carServiceProvider) {
        mContext = context.getApplicationContext();
        mCarServiceProvider = carServiceProvider;
        mCarServiceProvider.addListener(mServiceOnConnectedListener);

        updateTexts();
    }

    @VisibleForTesting
    void onConnect(Car car) {
        mCarOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);
        mCarUserManager = car.getCarManager(CarUserManager.class);
        mCarPowerManager = car.getCarManager(CarPowerManager.class);
        //re-register listeners in case of CarService crash and recreation
        if (mCarUserManager != null) {
            for (UserLifecycleListener listener : mUserLifecycleListeners.keySet()) {
                mCarUserManager.addListener(mUserLifecycleListeners.get(listener).first,
                        mUserLifecycleListeners.get(listener).second, listener);
            }
        }
    }

    void updateTexts() {
        mSeatDriver = mContext.getString(R.string.seat_driver);
        mSeatFront = mContext.getString(R.string.seat_front);
        mSeatRear = mContext.getString(R.string.seat_rear);
        mSeatLeftSide = mContext.getString(R.string.seat_left_side);
        mSeatCenterSide = mContext.getString(R.string.seat_center_side);
        mSeatRightSide = mContext.getString(R.string.seat_right_side);
    }

    void registerUserChangeEventsListener(Executor receiver, UserLifecycleEventFilter filter,
            UserLifecycleListener listener) {
        mUserLifecycleListeners.put(listener, new Pair<>(receiver, filter));
        if (mCarUserManager != null) {
            mCarServiceProvider.addListener(car -> {
                mCarUserManager.addListener(receiver, filter, listener);
            });
        }
    }

    void onDestroy() {
        for (UserLifecycleListener listener : mUserLifecycleListeners.keySet()) {
            mCarUserManager.removeListener(listener);
        }
        mUserLifecycleListeners.clear();
        mCarServiceProvider.removeListener(mServiceOnConnectedListener);
    }

    @Nullable
    CarUserManager getCarUserManager() {
        return mCarUserManager;
    }

    @Nullable
    OccupantZoneInfo getOccupantZoneForDisplayId(int displayId) {
        if (mCarOccupantZoneManager == null || displayId == INVALID_DISPLAY) {
            Slog.w(TAG, "Cannot get occupant zone manager or has been requested for invalid"
                    + " display.");
            return null;
        }

        List<OccupantZoneInfo> occupantZoneInfos = mCarOccupantZoneManager.getAllOccupantZones();
        for (int index = 0; index < occupantZoneInfos.size(); index++) {
            CarOccupantZoneManager.OccupantZoneInfo occupantZoneInfo = occupantZoneInfos.get(index);
            List<Display> displays = mCarOccupantZoneManager.getAllDisplaysForOccupant(
                    occupantZoneInfo);
            for (int displayIndex = 0; displayIndex < displays.size(); displayIndex++) {
                if (displays.get(displayIndex).getDisplayId() == displayId) {
                    return occupantZoneInfo;
                }
            }
        }
        return null;
    }

    int getDisplayIdForUser(@UserIdInt int userId) {
        if (mCarOccupantZoneManager == null) {
            Slog.w(TAG, "Cannot get occupant zone manager.");
            return INVALID_DISPLAY;
        }

        List<OccupantZoneInfo> occupantZoneInfos = mCarOccupantZoneManager.getAllOccupantZones();
        for (int i = 0; i < occupantZoneInfos.size(); i++) {
            OccupantZoneInfo zoneInfo = occupantZoneInfos.get(i);
            int zoneUserId = mCarOccupantZoneManager.getUserForOccupant(zoneInfo);
            if (zoneUserId == userId) {
                Display d = mCarOccupantZoneManager.getDisplayForOccupant(zoneInfo,
                        DISPLAY_TYPE_MAIN);
                return d != null ? d.getDisplayId() : INVALID_DISPLAY;
            }
        }
        return INVALID_DISPLAY;
    }

    int getUserForDisplay(int displayId) {
        if (mCarOccupantZoneManager == null) {
            Slog.w(TAG, "Cannot get occupant zone manager.");
            return INVALID_USER_ID;
        }

        return mCarOccupantZoneManager.getUserForDisplayId(displayId);
    }

    int unassignOccupantZoneForDisplay(int displayId) {
        OccupantZoneInfo zoneInfo = getOccupantZoneForDisplayId(displayId);
        if (zoneInfo == null) {
            // Return any error code for this situation.
            Slog.w(TAG, "Cannot find occupant zone info associated with display " + displayId);
            return USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE;
        }
        return mCarOccupantZoneManager.unassignOccupantZone(zoneInfo);
    }

    String getSeatString(int displayId) {
        String seatString = "";
        OccupantZoneInfo zoneInfo = getOccupantZoneForDisplayId(displayId);
        if (zoneInfo == null) {
            Slog.w(TAG, "Cannot find occupant zone info associated with display " + displayId);
            return seatString;
        }

        switch (zoneInfo.occupantType) {
            case OCCUPANT_TYPE_FRONT_PASSENGER:
                return mSeatFront + " " + getSeatSideString(zoneInfo.seat);
            case OCCUPANT_TYPE_REAR_PASSENGER:
                return mSeatRear + " " + getSeatSideString(zoneInfo.seat);
            case OCCUPANT_TYPE_DRIVER:
                return mSeatDriver;
        }
        return seatString;
    }

    private String getSeatSideString(int seat) {
        switch (seat) {
            case SEAT_ROW_1_LEFT:
            case SEAT_ROW_2_LEFT:
            case SEAT_ROW_3_LEFT:
                return mSeatLeftSide;
            case SEAT_ROW_1_CENTER:
            case SEAT_ROW_2_CENTER:
            case SEAT_ROW_3_CENTER:
                return mSeatCenterSide;
            case SEAT_ROW_1_RIGHT:
            case SEAT_ROW_2_RIGHT:
            case SEAT_ROW_3_RIGHT:
                return mSeatRightSide;
            default:
                return "";
        }
    }

    void screenOffDisplay(int displayId) {
        if (mCarPowerManager == null) {
            Slog.e(TAG, "Cannot get power manager.");
            return;
        }
        mCarPowerManager.setDisplayPowerState(displayId, /* enable= */ false);
    }
}
