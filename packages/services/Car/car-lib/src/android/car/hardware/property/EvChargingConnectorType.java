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
import android.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Uses IEC(International Electrotechnical Commission) 62196  and other standards to
 * denote the charging connector type an electric vehicle may use.
 *
 * <p>Applications can use {@link CarPropertyManager#getProperty(int, int)} with
 * {@link android.car.VehiclePropertyIds#INFO_EV_CONNECTOR_TYPE} to query charging connector
 * types of the car.
 */
public final class EvChargingConnectorType {
    /**
     * The vehicle does not know the charging connector type.
     */
    public static final int UNKNOWN = 0;

    /**
     * IEC 62196 Type 1 connector
     *
     * <p>It is colloquially known as the "Yazaki connector" or "J1772 connector".
     */
    public static final int IEC_TYPE_1_AC = 1;

    /**
     * IEC 62196 Type 2 connector
     *
     * <p>It is colloquially known as the "Mennekes connector".
     */
    public static final int IEC_TYPE_2_AC = 2;

    /**
     * IEC 62196 Type 3 connector
     *
     * <p>It is colloquially known as the "Scame connector".
     */
    public static final int IEC_TYPE_3_AC = 3;

    /**
     * IEC 62196 Type AA connector
     *
     * <p>It is colloquially known as the "Chademo connector".
     */
    public static final int IEC_TYPE_4_DC = 4;

    /**
     * IEC 62196 Type EE connector
     *
     * <p>It is colloquially known as the “CCS1 connector” or “Combo1 connector".
     */
    public static final int IEC_TYPE_1_CCS_DC = 5;

    /**
     * IEC 62196 Type EE connector
     *
     * <p>It is colloquially known as the “CCS2 connector” or “Combo2 connector”.
     */
    public static final int IEC_TYPE_2_CCS_DC = 6;

    /** Connector of Tesla Roadster */
    public static final int TESLA_ROADSTER = 7;

    /** High Power Wall Charger of Tesla */
    public static final int TESLA_HPWC = 8;

    /** Supercharger of Tesla */
    public static final int TESLA_SUPERCHARGER = 9;

    /** GBT_AC Fast Charging Standard */
    public static final int GBT_AC = 10;

    /** GBT_DC Fast Charging Standard */
    public static final int GBT_DC = 11;

    /**
     * Connector type to use when no other types apply.
     */
    public static final int OTHER = 101;

    /** @hide */
    @IntDef({
            UNKNOWN,
            IEC_TYPE_1_AC,
            IEC_TYPE_2_AC,
            IEC_TYPE_3_AC,
            IEC_TYPE_4_DC,
            IEC_TYPE_1_CCS_DC,
            IEC_TYPE_2_CCS_DC,
            TESLA_ROADSTER,
            TESLA_HPWC,
            TESLA_SUPERCHARGER,
            GBT_AC,
            GBT_DC,
            OTHER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Enum {}

    private EvChargingConnectorType() {}

    /**
     * Gets a user-friendly representation of a charging connector type.
     */
    @NonNull
    public static String toString(@EvChargingConnectorType.Enum int connectorType) {
        switch (connectorType) {
            case UNKNOWN:
                return "UNKNOWN";
            case IEC_TYPE_1_AC:
                return "IEC_TYPE_1_AC";
            case IEC_TYPE_2_AC:
                return "IEC_TYPE_2_AC";
            case IEC_TYPE_3_AC:
                return "IEC_TYPE_3_AC";
            case IEC_TYPE_4_DC:
                return "IEC_TYPE_4_DC";
            case IEC_TYPE_1_CCS_DC:
                return "IEC_TYPE_1_CCS_DC";
            case IEC_TYPE_2_CCS_DC:
                return "IEC_TYPE_2_CCS_DC";
            case TESLA_ROADSTER:
                return "TESLA_ROADSTER";
            case TESLA_HPWC:
                return "TESLA_HPWC";
            case TESLA_SUPERCHARGER:
                return "TESLA_SUPERCHARGER";
            case GBT_AC:
                return "GBT_AC";
            case GBT_DC:
                return "GBT_DC";
            case OTHER:
                return "OTHER";
            default:
                return "0x" + Integer.toHexString(connectorType);
        }
    }
}
