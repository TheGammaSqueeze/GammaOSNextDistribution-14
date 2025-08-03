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
 * Used by {@link android.car.VehiclePropertyIds#ELECTRONIC_TOLL_COLLECTION_CARD_TYPE} to
 * enumerate ETC(electronic toll collection) card types in the vehicle.
 * Use in {@link android.car.hardware.property.CarPropertyManager#getProperty(int, int)} to
 * get this property value.
 */
public final class VehicleElectronicTollCollectionCardType {
    /**
     * Type is unknown or not in the list below.
     */
    public static final int UNKNOWN = 0;
    /**
     * A Japanese electronic toll collection card that does not support ETC 2.0.
     */
    public static final int JP_ELECTRONIC_TOLL_COLLECTION_CARD = 1;
    /**
     * A Japanese electronic toll collection 2.0 card.
     */
    public static final int JP_ELECTRONIC_TOLL_COLLECTION_CARD_V2 = 2;

    /** @hide */
    @IntDef({
            UNKNOWN,
            JP_ELECTRONIC_TOLL_COLLECTION_CARD,
            JP_ELECTRONIC_TOLL_COLLECTION_CARD_V2
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Enum {}
    private VehicleElectronicTollCollectionCardType() {}
}
