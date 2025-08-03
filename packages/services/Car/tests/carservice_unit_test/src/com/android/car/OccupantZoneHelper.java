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

package com.android.car;

import static android.car.CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_FAIL_ALREADY_ASSIGNED;
import static android.car.CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE;
import static android.car.CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.VehicleAreaSeat;
import android.os.UserHandle;
import android.util.SparseIntArray;
import android.view.Display;

import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

/**
 * This provides simple mock for {@link com.android.car.CarOccupantZoneService}.
 *
 * <p>For simplicity, only main display is supported and display id and zone id will be the same.
 */
public final class OccupantZoneHelper {

    public final CarOccupantZoneManager.OccupantZoneInfo zoneDriverLHD = new OccupantZoneInfo(0,
            CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER,
            VehicleAreaSeat.SEAT_ROW_1_LEFT);
    public final OccupantZoneInfo zoneFrontPassengerLHD = new OccupantZoneInfo(1,
            CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER,
            VehicleAreaSeat.SEAT_ROW_1_RIGHT);
    public final OccupantZoneInfo zoneRearLeft = new OccupantZoneInfo(2,
            CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER,
            VehicleAreaSeat.SEAT_ROW_2_LEFT);
    public final OccupantZoneInfo zoneRearRight = new OccupantZoneInfo(3,
            CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER,
            VehicleAreaSeat.SEAT_ROW_2_RIGHT);
    public final OccupantZoneInfo zoneRearCenter = new OccupantZoneInfo(4,
            CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER,
            VehicleAreaSeat.SEAT_ROW_2_CENTER);

    private final List<CarOccupantZoneManager.OccupantZoneInfo> mZones = new ArrayList<>();
    private final SparseIntArray mZoneToUser = new SparseIntArray(); // key: zone, value: user id

    public void setUpOccupantZones(@Mock CarOccupantZoneService service, boolean hasDriver,
            boolean hasFrontPassenger, int numRearPassengers) {
        if (hasDriver) {
            mZones.add(zoneDriverLHD);
        }
        if (hasFrontPassenger) {
            mZones.add(zoneFrontPassengerLHD);
        }
        if (numRearPassengers >= 1) {
            mZones.add(zoneRearLeft);
        }
        if (numRearPassengers == 2) {
            mZones.add(zoneRearRight);
        }
        if (numRearPassengers == 3) {
            mZones.add(zoneRearCenter);
        }
        if (numRearPassengers > 3) {
            throw new IllegalArgumentException("Supports only up to 3 rear passengers");
        }

        // All following mocking are dynamic based on mZones and other params.
        when(service.getAllOccupantZones()).thenAnswer(inv -> mZones);
        when(service.hasDriverZone()).thenReturn(hasDriver);
        when(service.getNumberOfPassengerZones()).thenReturn(
                numRearPassengers + (hasFrontPassenger ? 1 : 0));
        when(service.getOccupantZone(anyInt(), anyInt())).thenAnswer(
                inv -> {
                    int type = (int) inv.getArgument(0);
                    int seat = (int) inv.getArgument(1);
                    if (type == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                        if (hasDriver) {
                            return zoneDriverLHD;
                        } else {
                            return null;
                        }
                    }
                    for (OccupantZoneInfo zone : mZones) {
                        if (zone.occupantType == type && zone.seat == seat) {
                            return zone;
                        }
                    }
                    return null;
                }
        );
        when(service.getDisplayForOccupant(anyInt(), anyInt())).thenAnswer(
                inv -> {
                    int zoneId = (int) inv.getArgument(0);
                    int displayType = (int) inv.getArgument(1);
                    if (displayType != CarOccupantZoneManager.DISPLAY_TYPE_MAIN) {
                        return Display.INVALID_DISPLAY;
                    }
                    for (OccupantZoneInfo zone : mZones) {
                        if (zone.zoneId == zoneId) {
                            return zoneId; // display id and zone id the same
                        }
                    }
                    return Display.INVALID_DISPLAY;
                }
        );
        when(service.assignVisibleUserToOccupantZone(anyInt(), any())).thenAnswer(
                inv -> {
                    int zoneId = (int) inv.getArgument(0);
                    UserHandle user = (UserHandle) inv.getArgument(1);
                    int userId = user.getIdentifier();
                    if (!hasZone(zoneId)) {
                        return USER_ASSIGNMENT_RESULT_FAIL_ALREADY_ASSIGNED;
                    }
                    if (mZones.contains(zoneDriverLHD) && zoneDriverLHD.zoneId == zoneId) {
                        return USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE;
                    }
                    int existingIndex = mZoneToUser.indexOfValue(userId);
                    if (existingIndex >= 0) {
                        int preassignedZone = mZoneToUser.keyAt(existingIndex);
                        if (preassignedZone == zoneId) {
                            return USER_ASSIGNMENT_RESULT_OK;
                        } else {
                            return USER_ASSIGNMENT_RESULT_FAIL_ALREADY_ASSIGNED;
                        }
                    }
                    mZoneToUser.append(zoneId, userId);
                    return USER_ASSIGNMENT_RESULT_OK;
                }
        );
        when(service.unassignOccupantZone(anyInt())).thenAnswer(
                inv -> {
                    int zoneId = (int) inv.getArgument(0);
                    if (!hasZone(zoneId)) {
                        return USER_ASSIGNMENT_RESULT_FAIL_ALREADY_ASSIGNED;
                    }
                    if (mZones.contains(zoneDriverLHD) && zoneDriverLHD.zoneId == zoneId) {
                        return USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE;
                    }
                    int existingIndex = mZoneToUser.indexOfKey(zoneId);
                    if (existingIndex >= 0) {
                        mZoneToUser.delete(zoneId);
                    }
                    return USER_ASSIGNMENT_RESULT_OK;
                }
        );
        when(service.getOccupantZoneIdForUserId(anyInt())).thenAnswer(
                inv -> {
                    int userId = (int) inv.getArgument(0);
                    for (int i = 0; i < mZoneToUser.size(); i++) {
                        int user = mZoneToUser.valueAt(i);
                        int zoneId = mZoneToUser.keyAt(i);
                        if (user == userId) {
                            return zoneId;
                        }
                    }
                    return OccupantZoneInfo.INVALID_ZONE_ID;
                }
        );
        when(service.getUserForOccupant(anyInt())).thenAnswer(
                inv -> {
                    int zoneId = (int) inv.getArgument(0);
                    int existingIndex = mZoneToUser.indexOfKey(zoneId);
                    if (existingIndex >= 0) {
                        return mZoneToUser.valueAt(existingIndex);
                    }
                    return CarOccupantZoneManager.INVALID_USER_ID;
                }
        );
    }

    public int getDisplayId(OccupantZoneInfo zone) {
        return zone.zoneId; // For simplicity, use the same id with zone.
    }

    private boolean hasZone(int zoneId) {
        for (OccupantZoneInfo zone : mZones) {
            if (zoneId == zone.zoneId) {
                return true;
            }
        }
        return false;
    }
}
