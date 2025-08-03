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

import static java.util.Objects.requireNonNull;

import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.util.ArraySet;

import com.android.car.internal.util.PairSparseArray;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Manages a group of property IDs and area IDs registered for the same client at possibly
 * different update rates. The client here might be different callbacks at
 * {@code CarPropertyManager} or might be different managers at {@code CarPropertyService}.
 *
 * This class is used to decide whether a new property update event needs to be filtered out and
 * not passed to the client.
 *
 * This class is thread-safe.
 *
 * @hide
 */
public class CarPropertyEventController {
    // Abbreviating TAG because class name is longer than the 23 character Log tag limit.
    private static final String TAG = "CPEController";
    private final Logger mLogger;
    private final boolean mUseSystemLogger;
    private final Object mLock = new Object();
    // For each property ID and area ID, track the property event information.
    @GuardedBy("mLock")
    private final PairSparseArray<CarPropertyEventTracker> mPropIdToAreaIdToCpeTracker =
            new PairSparseArray<>();

    public CarPropertyEventController(boolean useSystemLogger) {
        mUseSystemLogger = useSystemLogger;
        mLogger = new Logger(useSystemLogger, TAG);
    }

    /** Gets the update rate in Hz for the property ID, area ID. */
    @VisibleForTesting
    public float getUpdateRateHz(int propertyId, int areaId) {
        synchronized (mLock) {
            CarPropertyEventTracker tracker = mPropIdToAreaIdToCpeTracker.get(propertyId, areaId);
            if (tracker == null) {
                return 0f;
            }
            return tracker.getUpdateRateHz();
        }
    }

    /** Tracks the continuous property ID and area IDs at the given update rate. */
    public void addContinuousProperty(int propertyId, int[] areaIds, float updateRateHz,
            boolean enableVur, float resolution) {
        requireNonNull(areaIds);
        synchronized (mLock) {
            for (int areaId : areaIds) {
                if (mLogger.dbg()) {
                    mLogger.logD(String.format(
                            "Add new continuous property event tracker, property: %s, "
                            + "areaId: %d, updateRate: %f Hz, enableVur: %b, resolution: %f",
                            VehiclePropertyIds.toString(propertyId), areaId, updateRateHz,
                            enableVur, resolution));
                }
                mPropIdToAreaIdToCpeTracker.put(propertyId, areaId,
                        new ContCarPropertyEventTracker(mUseSystemLogger, updateRateHz, enableVur,
                                resolution));
            }
        }
    }

    /** Tracks a newly subscribed on-change property ID and area IDs. */
    public void addOnChangeProperty(int propertyId, int[] areaIds) {
        requireNonNull(areaIds);
        synchronized (mLock) {
            for (int areaId : areaIds) {
                if (mLogger.dbg()) {
                    mLogger.logD(String.format(
                            "Add new on-change property event tracker, property: %s, "
                            + "areaId: %d", VehiclePropertyIds.toString(propertyId), areaId));
                }
                mPropIdToAreaIdToCpeTracker.put(propertyId, areaId,
                        new OnChangeCarPropertyEventTracker(mUseSystemLogger));
            }
        }
    }

    /**
     * Returns the areaIds associated with the given propertyId
     */
    public int[] getAreaIds(int propertyId) {
        synchronized (mLock) {
            return setToArray(mPropIdToAreaIdToCpeTracker.getSecondKeysForFirstKey(propertyId));
        }
    }

    /**
     * Stop tracking the given property ID.
     *
     * @return {@code true} if there are no remaining properties being tracked.
     */
    public boolean remove(int propertyId) {
        synchronized (mLock) {
            for (int areaId : mPropIdToAreaIdToCpeTracker.getSecondKeysForFirstKey(propertyId)) {
                mPropIdToAreaIdToCpeTracker.delete(propertyId, areaId);
            }
            return mPropIdToAreaIdToCpeTracker.size() == 0;
        }
    }

    /**
     * Stop tracking the given property IDs.
     *
     * @return {@code true} if there are no remaining properties being tracked.
     */
    public boolean remove(ArraySet<Integer> propertyIds) {
        synchronized (mLock) {
            for (int i = 0; i < propertyIds.size(); i++) {
                int propertyId = propertyIds.valueAt(i);
                for (int areaId :
                        mPropIdToAreaIdToCpeTracker.getSecondKeysForFirstKey(propertyId)) {
                    mPropIdToAreaIdToCpeTracker.delete(propertyId, areaId);
                }
            }
            return mPropIdToAreaIdToCpeTracker.size() == 0;
        }
    }

    /** Returns a list of property IDs being tracked for the client. */
    public int[] getSubscribedProperties() {
        synchronized (mLock) {
            return setToArray(mPropIdToAreaIdToCpeTracker.getFirstKeys());
        }
    }

    /**
     * Returns a new sanitized CarPropertyValue if the client callback should be invoked for the
     * event.
     */
    protected CarPropertyValue<?> getCarPropertyValueIfCallbackRequired(
            CarPropertyEvent carPropertyEvent) {
        CarPropertyValue<?> carPropertyValue = carPropertyEvent.getCarPropertyValue();
        int propertyId = carPropertyValue.getPropertyId();
        int areaId = carPropertyValue.getAreaId();

        synchronized (mLock) {
            CarPropertyEventTracker tracker = mPropIdToAreaIdToCpeTracker.get(propertyId, areaId);
            if (tracker == null) {
                mLogger.logW(
                        "getCarPropertyValueIfCallbackRequired: callback not registered for event: "
                        + carPropertyEvent);
                return null;
            }
            if (carPropertyEvent.getEventType() == CarPropertyEvent.PROPERTY_EVENT_ERROR) {
                return carPropertyValue;
            }
            if (tracker.hasUpdate(carPropertyValue)) {
                return tracker.getCurrentCarPropertyValue();
            }
            return null;
        }
    }

    private static int[] setToArray(ArraySet<Integer> ids) {
        int[] propertyIds = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            propertyIds[i] = ids.valueAt(i);
        }
        return propertyIds;
    }
}
