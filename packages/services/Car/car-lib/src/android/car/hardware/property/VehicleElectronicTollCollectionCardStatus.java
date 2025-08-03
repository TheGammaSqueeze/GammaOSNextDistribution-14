/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.car.hardware.property;
import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used by {@link android.car.VehiclePropertyIds#ELECTRONIC_TOLL_COLLECTION_CARD_STATUS} to
 * enumerate ETC(electronic toll collection) card types in the vehicle.
 * Use in {@link android.car.hardware.property.CarPropertyManager#getProperty(int, int)} to
 * get this property value.
 */
public class VehicleElectronicTollCollectionCardStatus {
    /**
     * Status could not be determined
     */
    public static final int UNKNOWN = 0;
    /**
     * A valid electronic toll collection card is present
     */
    public static final int ELECTRONIC_TOLL_COLLECTION_CARD_VALID = 1;
    /**
     * An electronic toll collection card is present, but it is expired or otherwise invalid
     */
    public static final int ELECTRONIC_TOLL_COLLECTION_CARD_INVALID = 2;
    /**
     * No electronic toll collection card is inserted in the reader
     */
    public static final int ELECTRONIC_TOLL_COLLECTION_CARD_NOT_INSERTED = 3;

    /** @hide */
    @IntDef({
            UNKNOWN,
            ELECTRONIC_TOLL_COLLECTION_CARD_VALID,
            ELECTRONIC_TOLL_COLLECTION_CARD_NOT_INSERTED,
            ELECTRONIC_TOLL_COLLECTION_CARD_INVALID
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Enum {}
    private VehicleElectronicTollCollectionCardStatus() {}
}
