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

package com.google.uwb.support.multichip;

import android.os.PersistableBundle;

/**
 * Defines parameters from return value for  {@link android.uwb.UwbManager#getChipInfos()}.
 */
public final class ChipInfoParams {
    private static final String KEY_CHIP_ID = "KEY_CHIP_ID";
    private static final String UNKNOWN_CHIP_ID = "UNKNOWN_CHIP_ID";

    private static final String KEY_POSITION_X = "KEY_POSITION_X";
    private static final String KEY_POSITION_Y = "KEY_POSITION_Y";
    private static final String KEY_POSITION_Z = "KEY_POSITION_Z";

    private final String mChipId;
    private final double mPositionX;
    private final double mPositionY;
    private final double mPositionZ;

    private ChipInfoParams(String chipId, double positionX, double positionY, double positionZ) {
        mChipId = chipId;
        mPositionX = positionX;
        mPositionY = positionY;
        mPositionZ = positionZ;
    }

    /** Returns a String identifier of the chip. */
    public String getChipId() {
        return mChipId;
    }

    /** Returns the x position of the chip as a double in meters. */
    public double getPositionX() {
        return mPositionX;
    }

    /** Returns the y position of the chip as a double in meters. */
    public double getPositionY() {
        return mPositionY;
    }

    /** Returns the z position of the chip as a double in meters. */
    public double getPositionZ() {
        return mPositionZ;
    }

    /** Returns a {@link PersistableBundle} representation of the object. */
    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(KEY_CHIP_ID, mChipId);
        bundle.putDouble(KEY_POSITION_X, mPositionX);
        bundle.putDouble(KEY_POSITION_Y, mPositionY);
        bundle.putDouble(KEY_POSITION_Z, mPositionZ);
        return bundle;
    }

    /** Creates a new {@link ChipInfoParams} from a {@link PersistableBundle}. */
    public static ChipInfoParams fromBundle(PersistableBundle bundle) {
        String chipId = bundle.getString(KEY_CHIP_ID, UNKNOWN_CHIP_ID);
        double positionX = bundle.getDouble(KEY_POSITION_X, 0.0);
        double positionY = bundle.getDouble(KEY_POSITION_Y, 0.0);
        double positionZ = bundle.getDouble(KEY_POSITION_Z, 0.0);
        return new ChipInfoParams(chipId, positionX, positionY, positionZ);
    }

    /** Creates and returns a {@link Builder}. */
    public static Builder createBuilder() {
        return new Builder();
    }

    /**
     * A Class for building an object representing the return type of
     * {@link android.uwb.UwbManager#getChipInfos()}.
     */
    public static class Builder {
        String mChipId = UNKNOWN_CHIP_ID;
        double mPositionX = 0.0;
        double mPositionY = 0.0;
        double mPositionZ = 0.0;

        /** Sets String identifier of chip */
        public Builder setChipId(String chipId) {
            mChipId = chipId;
            return this;
        }

        /** Sets the x position of the chip measured in meters. */
        public Builder setPositionX(double positionX) {
            mPositionX = positionX;
            return this;
        }

        /** Sets the y position of the chip measured in meters. */
        public Builder setPositionY(double positionY) {
            mPositionY = positionY;
            return this;
        }

        /** Sets the z position of the chip measured in meters. */
        public Builder setPositionZ(double positionZ) {
            mPositionZ = positionZ;
            return this;
        }

        /**
         * Builds an object representing the return type of
         * {@link android.uwb.UwbManager#getChipInfos()}.
         */
        public ChipInfoParams build()  {
            return new ChipInfoParams(mChipId, mPositionX, mPositionY, mPositionZ);
        }
    }
}
