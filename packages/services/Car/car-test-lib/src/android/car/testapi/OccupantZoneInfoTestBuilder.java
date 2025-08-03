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

package android.car.testapi;

import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;

import android.car.CarOccupantZoneManager.OccupantTypeEnum;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.VehicleAreaSeat;

/**
 * Factory class used to create an instance of
 * {@link android.car.CarOccupantZoneManager.OccupantZoneInfo} for testing purposes
 * since the constructor is hidden.
 */
public final class OccupantZoneInfoTestBuilder {
    private int mZoneId;
    private @OccupantTypeEnum int mOccupantType = OCCUPANT_TYPE_DRIVER;
    private @VehicleAreaSeat.Enum int mSeat = VehicleAreaSeat.SEAT_ROW_1_LEFT;

    /**
     * Builder used to creates an {@link android.car.CarOccupantZoneManager.OccupantZoneInfo}
     * object of the desired state, setting {@code zoneId}, {@code occupantType}, and {@code seat}.
     */
    public OccupantZoneInfoTestBuilder() {
    }

    /**
     * Sets the occupant {@code zoneId}
     * of the {@link android.car.CarOccupantZoneManager.OccupantZoneInfo} object
     */
    public OccupantZoneInfoTestBuilder setZoneId(int zoneId) {
        mZoneId = zoneId;
        return this;
    }
    /**
     * Sets the {@code occupantType}
     * of the {@link android.car.CarOccupantZoneManager.OccupantZoneInfo} object
     */
    public OccupantZoneInfoTestBuilder setOccupantType(@OccupantTypeEnum int occupantType) {
        mOccupantType = occupantType;
        return this;
    }
    /**
     * Sets the {@code seat}
     * of the {@link android.car.CarOccupantZoneManager.OccupantZoneInfo} object
     */
    public OccupantZoneInfoTestBuilder setSeat(@VehicleAreaSeat.Enum int seat) {
        mSeat = seat;
        return this;
    }

    /**
     * Builds an {@link android.car.CarOccupantZoneManager.OccupantZoneInfo}
     * object of the desired state, setting {@code zoneId}, {@code occupantType}, and {@code seat}.
     */
    public OccupantZoneInfo build() {
        return new OccupantZoneInfo(mZoneId, mOccupantType, mSeat);
    }
}
