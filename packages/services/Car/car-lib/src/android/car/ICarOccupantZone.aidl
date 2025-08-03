/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car;

import android.car.CarOccupantZoneManager;
import android.car.ICarOccupantZoneCallback;
import android.os.UserHandle;

/** @hide */
interface ICarOccupantZone {
    List<CarOccupantZoneManager.OccupantZoneInfo> getAllOccupantZones();
    int getAudioZoneIdForOccupant(in int occupantZoneId);
    int[] getAllDisplaysForOccupantZone(in int occupantZoneId);
    int getDisplayForOccupant(in int occupantZoneId, in int displayType);
    CarOccupantZoneManager.OccupantZoneInfo getOccupantForAudioZoneId(in int audioZoneId);
    int getDisplayType(in int displayId);
    int getUserForOccupant(in int occupantZoneId);

    // Returns the preferred zone id for the user passed as parameter. Users may be associated
    // with more than one zone id. In that case, this method will return the first associated zone
    // (which will be the preferred zone).
    int getOccupantZoneIdForUserId(in int userId);

    void registerCallback(in ICarOccupantZoneCallback callback);
    void unregisterCallback(in ICarOccupantZoneCallback callback);
    boolean assignProfileUserToOccupantZone(in int occupantZoneId, in int userId);
    int getDisplayIdForDriver(in int displayType);

    int assignVisibleUserToOccupantZone(in int occupantZoneId, in UserHandle user);
    int unassignOccupantZone(in int occupantZoneId);
    CarOccupantZoneManager.OccupantZoneInfo getOccupantZoneForUser(in UserHandle user);
    CarOccupantZoneManager.OccupantZoneInfo getMyOccupantZone();
    CarOccupantZoneManager.OccupantZoneInfo getOccupantZone(in int type, in int seat);
    boolean hasDriverZone();
    boolean hasPassengerZones();
    int getUserForDisplayId(int displayId);
    int[] getSupportedInputTypes(in int occupantZoneId, in int inputType);
    CarOccupantZoneManager.OccupantZoneInfo getOccupantZoneForDisplayId(int displayId);
}
