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

package com.android.car.hal;

import static android.car.evs.CarEvsManager.SERVICE_TYPE_REARVIEW;
import static android.car.evs.CarEvsManager.SERVICE_TYPE_SURROUNDVIEW;
import static android.hardware.automotive.vehicle.VehicleProperty.EVS_SERVICE_REQUEST;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.builtin.util.Slogf;
import android.car.evs.CarEvsManager.CarEvsServiceType;
import android.hardware.automotive.vehicle.EvsServiceRequestIndex;
import android.hardware.automotive.vehicle.EvsServiceState;
import android.hardware.automotive.vehicle.EvsServiceType;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

/*
 * Translates HAL events, CarEvsService is interested in, into the higher-level semantic
 * information.
 */
public class EvsHalService extends HalServiceBase {

    private static final String TAG = EvsHalService.class.getSimpleName();
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final int[] SUPPORTED_PROPERTIES = new int[] {
        EVS_SERVICE_REQUEST,
    };

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<HalPropConfig> mProperties = new SparseArray();

    private final VehicleHal mHal;

    @GuardedBy("mLock")
    private EvsHalEventListener mListener;

    private boolean mIsEvsServiceRequestSupported;

    public EvsHalService(VehicleHal hal) {
        mHal = hal;
    }

    /**
     * Interface to be implemented by any client that wants to get notified upon a new EVS service
     * request.
     */
    public interface EvsHalEventListener {
        /** Called when a value of monitoring VHAL property gets changed */
        void onEvent(@CarEvsServiceType int id, boolean on);
    }

    /**
     * Sets the event listener to receive Vehicle's EVS-related events.
     *
     * @param listener {@link EvsHalEventListener}
     * @throws {@link IllegalStateException} if none of required VHAL properties are not supported
     *         on this device.
     */
    public void setListener(EvsHalEventListener listener) {
        if (!mIsEvsServiceRequestSupported) {
            throw new IllegalStateException(
                    "Any of required VHAL properties are not supported.");
        }

        synchronized (mLock) {
            mListener = listener;
        }

        mHal.subscribePropertySafe(this, EVS_SERVICE_REQUEST);
    }

    /** Returns whether {@code EVS_SERVICE_REQUEST} is supported */
    public boolean isEvsServiceRequestSupported() {
        return mIsEvsServiceRequestSupported;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            for (int i = 0; i < mProperties.size(); i++) {
                HalPropConfig config = mProperties.valueAt(i);
                if (VehicleHal.isPropertySubscribable(config)) {
                    mHal.subscribePropertySafe(this, config.getPropId());
                }
            }
        }
    }

    @Override
    public void release() {
        synchronized (mLock) {
            mListener = null;
            mProperties.clear();
        }
    }

    @Override
    public int[] getAllSupportedProperties() {
        return SUPPORTED_PROPERTIES;
    }

    @Override
    public void takeProperties(Collection<HalPropConfig> configs) {
        if (configs.isEmpty()) {
            return;
        }

        synchronized (mLock) {
            for (HalPropConfig config : configs) {
                mProperties.put(config.getPropId(), config);
            }

            mIsEvsServiceRequestSupported = mProperties.contains(EVS_SERVICE_REQUEST);
        }
    }

    @Override
    public void onHalEvents(List<HalPropValue> values) {
        EvsHalEventListener listener;
        synchronized (mLock) {
            listener = mListener;
        }

        if (listener == null) {
            Slogf.w(TAG, "EVS Hal event occurs while the listener is null.");
            return;
        }

        dispatchHalEvents(values, listener);
    }

    private void dispatchHalEvents(List<HalPropValue> values, EvsHalEventListener listener) {
        for (int i = 0; i < values.size(); ++i) {
            HalPropValue v = values.get(i);
            boolean on = false;
            @CarEvsServiceType int type;
            switch (v.getPropId()) {
                case EVS_SERVICE_REQUEST:
                    // Check
                    // android.hardware.automotive.vehicle.VehicleProperty.EVS_SERVICE_REQUEST
                    try {
                        int rawServiceType = v.getInt32Value(EvsServiceRequestIndex.TYPE);
                        type = rawServiceType == EvsServiceType.REARVIEW
                                ? SERVICE_TYPE_REARVIEW : SERVICE_TYPE_SURROUNDVIEW;
                        on = v.getInt32Value(EvsServiceRequestIndex.STATE) == EvsServiceState.ON;
                        if (DBG) {
                            Slogf.d(TAG,
                                    "Received EVS_SERVICE_REQUEST: type = " + type + " on = " + on);
                        }
                    } catch (IndexOutOfBoundsException e) {
                        Slogf.e(TAG, "Received invalid EVS_SERVICE_REQUEST, missing type or state,"
                                + " int32Values: " + v.dumpInt32Values());
                        break;
                    }
                    listener.onEvent(type, on);
                    break;

                default:
                    if (DBG) {
                        Slogf.d(TAG, "Received unknown property change: " + v);
                    }
                    break;
            }
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(PrintWriter writer) {
        writer.println("*EVSHALSERVICE*");
        writer.printf("Use EVS_SERVICE_REQUEST: %b\n", isEvsServiceRequestSupported());
    }
}
