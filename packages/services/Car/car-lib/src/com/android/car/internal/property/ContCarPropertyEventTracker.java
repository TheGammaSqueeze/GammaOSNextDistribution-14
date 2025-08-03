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

package com.android.car.internal.property;

import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarPropertyValue.PropertyStatus;

import com.android.internal.util.Preconditions;

import java.time.Duration;
import java.util.Objects;

/**
 * A {@link CarPropertyEventTracker} implementation for continuous property
 *
 * @hide
 */
public final class ContCarPropertyEventTracker implements CarPropertyEventTracker{
    private static final String TAG = "ContCarPropertyEventTracker";
    private static final float NANOSECONDS_PER_SECOND = Duration.ofSeconds(1).toNanos();
    // Add a margin so that if an event timestamp is within
    // 5% of the next timestamp, it will not be dropped.
    private static final float UPDATE_PERIOD_OFFSET = 0.95f;

    private final Logger mLogger;
    private final boolean mEnableVur;
    private final float mUpdateRateHz;
    private final float mResolution;
    private final long mUpdatePeriodNanos;
    private long mNextUpdateTimeNanos;
    private CarPropertyValue<?> mCurrentCarPropertyValue;
    private @PropertyStatus int mCurrentStatus;

    public ContCarPropertyEventTracker(boolean useSystemLogger, float updateRateHz,
            boolean enableVur, float resolution) {
        mLogger = new Logger(useSystemLogger, TAG);
        if (mLogger.dbg()) {
            mLogger.logD(String.format(
                    "new continuous car property event tracker, updateRateHz: %f, "
                    + ", enableVur: %b, resolution: %f", updateRateHz, enableVur, resolution));
        }
        // updateRateHz should be sanitized before.
        Preconditions.checkArgument(updateRateHz > 0, "updateRateHz must be a positive number");
        mUpdateRateHz = updateRateHz;
        mUpdatePeriodNanos =
                (long) ((1.0 / mUpdateRateHz) * NANOSECONDS_PER_SECOND * UPDATE_PERIOD_OFFSET);
        mEnableVur = enableVur;
        mResolution = resolution;
    }

    @Override
    public float getUpdateRateHz() {
        return mUpdateRateHz;
    }

    @Override
    public CarPropertyValue<?> getCurrentCarPropertyValue() {
        return mCurrentCarPropertyValue;
    }

    private int sanitizeValueByResolutionInt(Object value) {
        return (int) (Math.round((Integer) value / mResolution) * mResolution);
    }

    private long sanitizeValueByResolutionLong(Object value) {
        return (long) (Math.round((Long) value / mResolution) * mResolution);
    }

    private float sanitizeValueByResolutionFloat(Object value) {
        return Math.round((Float) value / mResolution) * mResolution;
    }

    private CarPropertyValue<?> sanitizeCarPropertyValueByResolution(
            CarPropertyValue<?> carPropertyValue) {
        if (mResolution == 0.0f) {
            return carPropertyValue;
        }

        Object value = carPropertyValue.getValue();
        if (value instanceof Integer) {
            value = sanitizeValueByResolutionInt(value);
        } else if (value instanceof Integer[]) {
            Integer[] array = (Integer[]) value;
            for (int i = 0; i < array.length; i++) {
                array[i] = sanitizeValueByResolutionInt(array[i]);
            }
            value = array;
        } else if (value instanceof Long) {
            value = sanitizeValueByResolutionLong(value);
        } else if (value instanceof Long[]) {
            Long[] array = (Long[]) value;
            for (int i = 0; i < array.length; i++) {
                array[i] = sanitizeValueByResolutionLong(array[i]);
            }
            value = array;
        } else if (value instanceof Float) {
            value = sanitizeValueByResolutionFloat(value);
        } else if (value instanceof Float[]) {
            Float[] array = (Float[]) value;
            for (int i = 0; i < array.length; i++) {
                array[i] = sanitizeValueByResolutionFloat(array[i]);
            }
            value = array;
        }
        return new CarPropertyValue<>(carPropertyValue.getPropertyId(),
                carPropertyValue.getAreaId(),
                carPropertyValue.getStatus(),
                carPropertyValue.getTimestamp(),
                value);
    }

    /** Returns true if the client needs to be updated for this event. */
    @Override
    public boolean hasUpdate(CarPropertyValue<?> carPropertyValue) {
        if (carPropertyValue.getTimestamp() < mNextUpdateTimeNanos) {
            if (mLogger.dbg()) {
                mLogger.logD(String.format("hasUpdate: Dropping carPropertyValue: %s, "
                        + "because getTimestamp()=%d < nextUpdateTimeNanos=%d",
                        carPropertyValue, carPropertyValue.getTimestamp(), mNextUpdateTimeNanos));
            }
            return false;
        }
        mNextUpdateTimeNanos = carPropertyValue.getTimestamp() + mUpdatePeriodNanos;
        CarPropertyValue<?> sanitizedCarPropertyValue =
                sanitizeCarPropertyValueByResolution(carPropertyValue);
        int status = sanitizedCarPropertyValue.getStatus();
        Object value = sanitizedCarPropertyValue.getValue();
        if (mEnableVur && status == mCurrentStatus && mCurrentCarPropertyValue != null
                    && Objects.deepEquals(value, mCurrentCarPropertyValue.getValue())) {
            if (mLogger.dbg()) {
                mLogger.logD(String.format("hasUpdate: Dropping carPropertyValue: %s, "
                                + "because VUR is enabled and value is the same",
                        sanitizedCarPropertyValue));
            }
            return false;
        }
        mCurrentStatus = status;
        mCurrentCarPropertyValue = sanitizedCarPropertyValue;
        return true;
    }
}
