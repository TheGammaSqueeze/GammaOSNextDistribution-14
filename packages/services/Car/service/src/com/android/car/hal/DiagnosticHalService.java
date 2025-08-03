/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import static java.lang.Integer.toHexString;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.hardware.CarSensorManager;
import android.hardware.automotive.vehicle.DiagnosticFloatSensorIndex;
import android.hardware.automotive.vehicle.DiagnosticIntegerSensorIndex;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.os.ServiceSpecificException;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Diagnostic HAL service supporting gathering diagnostic info from VHAL and translating it into
 * higher-level semantic information
 */
public class DiagnosticHalService extends HalServiceBase {
    static final int OBD2_SELECTIVE_FRAME_CLEAR = 1;
    static final boolean DEBUG = false;

    private static final int[] SUPPORTED_PROPERTIES = new int[]{
            VehicleProperty.OBD2_LIVE_FRAME,
            VehicleProperty.OBD2_FREEZE_FRAME,
            VehicleProperty.OBD2_FREEZE_FRAME_INFO,
            VehicleProperty.OBD2_FREEZE_FRAME_CLEAR
    };

    private final Object mLock = new Object();
    private final VehicleHal mVehicleHal;
    private final HalPropValueBuilder mPropValueBuilder;

    @GuardedBy("mLock")
    private boolean mIsReady = false;

    /**
     * Nested class used as a place holder for vehicle HAL's diagnosed properties.
     */
    public static final class DiagnosticCapabilities {
        private final CopyOnWriteArraySet<Integer> mProperties = new CopyOnWriteArraySet<>();

        void setSupported(int propertyId) {
            mProperties.add(propertyId);
        }

        boolean isSupported(int propertyId) {
            return mProperties.contains(propertyId);
        }

        public boolean isLiveFrameSupported() {
            return isSupported(VehicleProperty.OBD2_LIVE_FRAME);
        }

        public boolean isFreezeFrameSupported() {
            return isSupported(VehicleProperty.OBD2_FREEZE_FRAME);
        }

        public boolean isFreezeFrameInfoSupported() {
            return isSupported(VehicleProperty.OBD2_FREEZE_FRAME_INFO);
        }

        public boolean isFreezeFrameClearSupported() {
            return isSupported(VehicleProperty.OBD2_FREEZE_FRAME_CLEAR);
        }

        public boolean isSelectiveClearFreezeFramesSupported() {
            return isSupported(OBD2_SELECTIVE_FRAME_CLEAR);
        }

        void clear() {
            mProperties.clear();
        }
    }

    @GuardedBy("mLock")
    private final DiagnosticCapabilities mDiagnosticCapabilities = new DiagnosticCapabilities();

    @GuardedBy("mLock")
    private DiagnosticListener mDiagnosticListener;

    @GuardedBy("mLock")
    protected final SparseArray<HalPropConfig> mVehiclePropertyToConfig = new SparseArray<>();

    @GuardedBy("mLock")
    protected final SparseArray<HalPropConfig> mSensorTypeToConfig = new SparseArray<>();

    public DiagnosticHalService(VehicleHal hal) {
        mVehicleHal = hal;
        mPropValueBuilder = mVehicleHal.getHalPropValueBuilder();
    }

    @Override
    public int[] getAllSupportedProperties() {
        return SUPPORTED_PROPERTIES;
    }

    @Override
    public void takeProperties(Collection<HalPropConfig> properties) {
        if (DEBUG) {
            Slogf.d(CarLog.TAG_DIAGNOSTIC, "takeSupportedProperties");
        }
        for (HalPropConfig vp : properties) {
            int sensorType = getTokenForProperty(vp);
            if (sensorType == NOT_SUPPORTED_PROPERTY) {
                if (DEBUG) {
                    Slogf.d(CarLog.TAG_DIAGNOSTIC, new StringBuilder()
                                .append("0x")
                                .append(toHexString(vp.getPropId()))
                                .append(" ignored")
                                .toString());
                }
            } else {
                synchronized (mLock) {
                    mSensorTypeToConfig.append(sensorType, vp);
                }
            }
        }
    }

    /**
     * Returns a unique token to be used to map this property to a higher-level sensor.
     *
     * This token will be stored in {@link DiagnosticHalService#mSensorTypeToConfig} to allow
     * callers to go from unique sensor identifiers to HalPropConfig objects.
     *
     * <p>Possible returned tokens are:
     * <ul>
     *   <li>{@link HalServiceBase.NOT_SUPPORTED_PROPERTY}
     *   <li>{@link CarDiagnosticManager.FRAME_TYPE_LIVE}
     *   <li>{@link CarDiagnosticManager.FRAME_TYPE_FREEZE}
     *
     * @param propConfig The property config
     * @return A unique token.
     */
    protected int getTokenForProperty(HalPropConfig propConfig) {
        int propId = propConfig.getPropId();
        synchronized (mLock) {
            switch (propId) {
                case VehicleProperty.OBD2_LIVE_FRAME:
                    mDiagnosticCapabilities.setSupported(propId);
                    mVehiclePropertyToConfig.put(propId, propConfig);
                    Slogf.i(CarLog.TAG_DIAGNOSTIC, "configArray for OBD2_LIVE_FRAME is "
                            + Arrays.toString(propConfig.getConfigArray()));
                    return CarDiagnosticManager.FRAME_TYPE_LIVE;
                case VehicleProperty.OBD2_FREEZE_FRAME:
                    mDiagnosticCapabilities.setSupported(propId);
                    mVehiclePropertyToConfig.put(propId, propConfig);
                    Slogf.i(CarLog.TAG_DIAGNOSTIC, "configArray for OBD2_FREEZE_FRAME is "
                            + Arrays.toString(propConfig.getConfigArray()));
                    return CarDiagnosticManager.FRAME_TYPE_FREEZE;
                case VehicleProperty.OBD2_FREEZE_FRAME_INFO:
                    mDiagnosticCapabilities.setSupported(propId);
                    // We should not directly expose this to the client. This property is used
                    // only by {@link DiagnosticHalService} internally. Caller should instead use
                    // function like {@link DiagnosticHalService#getFreezeFrameTimestamps} to access
                    // frame info.
                    return NOT_SUPPORTED_PROPERTY;
                case VehicleProperty.OBD2_FREEZE_FRAME_CLEAR:
                    mDiagnosticCapabilities.setSupported(propId);
                    int[] configArray = propConfig.getConfigArray();
                    Slogf.i(CarLog.TAG_DIAGNOSTIC, "configArray for OBD2_FREEZE_FRAME_CLEAR is "
                            + Arrays.toString(configArray));
                    if (configArray.length < 1) {
                        Slogf.e(CarLog.TAG_DIAGNOSTIC, "property 0x%x does not specify whether it "
                                + "supports selective clearing of freeze frames. assuming it does "
                                + "not.", propId);
                    } else {
                        if (configArray[0] == 1) {
                            mDiagnosticCapabilities.setSupported(OBD2_SELECTIVE_FRAME_CLEAR);
                        }
                    }
                    // We should not directly expose this to the client.
                    return NOT_SUPPORTED_PROPERTY;
                default:
                    return NOT_SUPPORTED_PROPERTY;
            }
        }
    }

    @Override
    public void init() {
        if (DEBUG) {
            Slogf.d(CarLog.TAG_DIAGNOSTIC, "init()");
        }
        synchronized (mLock) {
            mIsReady = true;
        }
    }

    @Override
    public void release() {
        synchronized (mLock) {
            mDiagnosticCapabilities.clear();
            mIsReady = false;
        }
    }

    /**
     * Returns the status of Diagnostic HAL.
     * @return true if Diagnostic HAL is ready after init call.
     */
    public boolean isReady() {
        synchronized (mLock) {
            return mIsReady;
        }
    }

    /**
     * Returns an array of diagnostic property Ids implemented by this vehicle.
     *
     * @return Array of diagnostic property Ids implemented by this vehicle. Empty array if
     * no property available.
     */
    public int[] getSupportedDiagnosticProperties() {
        int[] supportedDiagnosticProperties;
        synchronized (mLock) {
            supportedDiagnosticProperties = new int[mSensorTypeToConfig.size()];
            for (int i = 0; i < supportedDiagnosticProperties.length; i++) {
                supportedDiagnosticProperties[i] = mSensorTypeToConfig.keyAt(i);
            }
        }
        return supportedDiagnosticProperties;
    }

    /**
     * Start to request diagnostic information.
     *
     * <p>The supported sensorTypes are one of:
     * <ul>
     *   <li>{@link CarDiagnosticManager.FRAME_TYPE_LIVE}
     *   <li>{@link CarDiagnosticManager.FRAME_TYPE_FREEZE}
     *
     * <p>The supported rate are one of:
     * <ul>
     *   <li>{@link CarSensorManager.SENSOR_RATE_ONCHANGE}
     *   <li>{@link CarSensorManager.SENSOR_RATE_NORMAL}
     *   <li>{@link CarSensorManager.SENSOR_RATE_FASTEST}
     *   <li>{@link CarSensorManager.SENSOR_RATE_FAST}
     *   <li>{@link CarSensorManager.SENSOR_RATE_UI}
     *
     * @param sensorType One of the supported sensor types.
     * @param rate One of the supported rate.
     *
     * @return true if request successfully. otherwise return false
     */
    public boolean requestDiagnosticStart(int sensorType, int rate) {
        HalPropConfig propConfig;
        synchronized (mLock) {
            propConfig = mSensorTypeToConfig.get(sensorType);
        }
        if (propConfig == null) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, new StringBuilder()
                    .append("Unsupported sensor type: 0x")
                    .append(toHexString(sensorType))
                    .toString());
            return false;
        }
        int propId = propConfig.getPropId();
        if (DEBUG) {
            Slogf.d(CarLog.TAG_DIAGNOSTIC, new StringBuilder()
                    .append("requestDiagnosticStart, propertyId: 0x")
                    .append(toHexString(propId))
                    .append(", rate: ")
                    .append(rate)
                    .toString());
        }
        mVehicleHal.subscribePropertySafe(this, propId,
                fixSamplingRateForProperty(propConfig, rate));
        return true;
    }

    /**
     * Stop requesting diagnostic information.
     * @param sensorType
     */
    public void requestDiagnosticStop(int sensorType) {
        HalPropConfig propConfig;
        synchronized (mLock) {
            propConfig = mSensorTypeToConfig.get(sensorType);
        }
        if (propConfig == null) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, new StringBuilder()
                    .append("Unsupported sensor type: 0x")
                    .append(toHexString(sensorType))
                    .toString());
            return;
        }
        int propId = propConfig.getPropId();
        if (DEBUG) {
            Slogf.d(CarLog.TAG_DIAGNOSTIC, new StringBuilder()
                    .append("requestDiagnosticStop, propertyId: 0x")
                    .append(toHexString(propId))
                    .toString());
        }
        mVehicleHal.unsubscribePropertySafe(this, propId);

    }

    /**
     * Query current diagnostic value
     * @param sensorType
     * @return The property value.
     */
    @Nullable
    public HalPropValue getCurrentDiagnosticValue(int sensorType) {
        HalPropConfig propConfig;
        synchronized (mLock) {
            propConfig = mSensorTypeToConfig.get(sensorType);
        }
        if (propConfig == null) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, new StringBuilder()
                    .append("property not available, sensor type: 0x")
                    .append(toHexString(sensorType))
                    .toString());
            return null;
        }
        int propId = propConfig.getPropId();
        try {
            return mVehicleHal.get(propId);
        } catch (ServiceSpecificException e) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "property not ready 0x" + toHexString(propId),
                    e);
            return null;
        } catch (IllegalArgumentException e) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "illegal argument trying to read property: 0x"
                    + toHexString(propId), e);
            return null;
        }

    }

    private HalPropConfig getPropConfig(int halPropId) {
        HalPropConfig config;
        synchronized (mLock) {
            config = mVehiclePropertyToConfig.get(halPropId, null);
        }
        return config;
    }

    private int[] getPropConfigArray(int halPropId) {
        HalPropConfig propConfig = getPropConfig(halPropId);
        return propConfig.getConfigArray();
    }

    private static int getLastIndex(Class<?> clazz) {
        int lastIndex = 0;
        for (Field field : clazz.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            try {
                if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                        && Modifier.isPublic(modifiers) && field.getType().equals(int.class)) {
                    int value = field.getInt(/* object= */ null);
                    if (value > lastIndex) {
                        lastIndex = value;
                    }
                }
            } catch (IllegalAccessException ignored) {
                // Ignore the exception.
            }
        }
        return lastIndex;
    }

    private int getNumIntegerSensors(int halPropId) {
        int count = getLastIndex(DiagnosticIntegerSensorIndex.class) + 1;
        int[] configArray = getPropConfigArray(halPropId);
        if (configArray.length < 2) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "property 0x%x does not specify the number of "
                    + "vendor-specific properties. Assuming 0.", halPropId);
        } else {
            count += configArray[0];
        }
        return count;
    }

    private int getNumFloatSensors(int halPropId) {
        int count = getLastIndex(DiagnosticFloatSensorIndex.class) + 1;
        int[] configArray = getPropConfigArray(halPropId);
        if (configArray.length < 2) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "property 0x%x does not specify the number of "
                    + "vendor-specific properties. Assuming 0.", halPropId);
        } else {
            count += configArray[1];
        }
        return count;
    }

    private CarDiagnosticEvent createCarDiagnosticEvent(HalPropValue value) {
        if (value == null) {
            return null;
        }
        int propId = value.getPropId();
        final boolean isFreezeFrame = propId == VehicleProperty.OBD2_FREEZE_FRAME;
        CarDiagnosticEvent.Builder builder =
                (isFreezeFrame
                                ? CarDiagnosticEvent.Builder.newFreezeFrameBuilder()
                                : CarDiagnosticEvent.Builder.newLiveFrameBuilder())
                        .atTimestamp(value.getTimestamp());

        BitSet bitset = BitSet.valueOf(value.getByteArray());

        int numIntegerProperties = getNumIntegerSensors(propId);
        int numFloatProperties = getNumFloatSensors(propId);

        for (int i = 0; i < numIntegerProperties; ++i) {
            if (bitset.get(i)) {
                builder.withIntValue(i, value.getInt32Value(i));
            }
        }

        for (int i = 0; i < numFloatProperties; ++i) {
            if (bitset.get(numIntegerProperties + i)) {
                builder.withFloatValue(i, value.getFloatValue(i));
            }
        }

        builder.withDtc(value.getStringValue());

        return builder.build();
    }

    /** Listener for monitoring diagnostic event. */
    public interface DiagnosticListener {
        /**
         * Diagnostic events are available.
         *
         * @param events
         */
        void onDiagnosticEvents(List<CarDiagnosticEvent> events);
    }

    // Should be used only inside handleHalEvents method.
    private final LinkedList<CarDiagnosticEvent> mEventsToDispatch = new LinkedList<>();

    @Override
    public void onHalEvents(List<HalPropValue> values) {
        for (HalPropValue value : values) {
            CarDiagnosticEvent event = createCarDiagnosticEvent(value);
            if (event != null) {
                mEventsToDispatch.add(event);
            }
        }

        DiagnosticListener listener = null;
        synchronized (mLock) {
            listener = mDiagnosticListener;
        }
        if (listener != null) {
            listener.onDiagnosticEvents(mEventsToDispatch);
        }
        mEventsToDispatch.clear();
    }

    /**
     * Set DiagnosticListener.
     * @param listener
     */
    public void setDiagnosticListener(DiagnosticListener listener) {
        synchronized (mLock) {
            mDiagnosticListener = listener;
        }
    }

    public DiagnosticListener getDiagnosticListener() {
        synchronized (mLock) {
            return mDiagnosticListener;
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(PrintWriter writer) {
        writer.println("*Diagnostic HAL*");
    }

    protected float fixSamplingRateForProperty(HalPropConfig prop, int carSensorManagerRate) {
        switch (prop.getChangeMode()) {
            case VehiclePropertyChangeMode.ON_CHANGE:
                return 0;
        }
        float rate = 1.0f;
        switch (carSensorManagerRate) {
            case CarSensorManager.SENSOR_RATE_FASTEST:
            case CarSensorManager.SENSOR_RATE_FAST:
                rate = 10f;
                break;
            case CarSensorManager.SENSOR_RATE_UI:
                rate = 5f;
                break;
            default: // fall back to default.
                break;
        }
        if (rate > prop.getMaxSampleRate()) {
            rate = prop.getMaxSampleRate();
        }
        if (rate < prop.getMinSampleRate()) {
            rate = prop.getMinSampleRate();
        }
        return rate;
    }

    public DiagnosticCapabilities getDiagnosticCapabilities() {
        synchronized (mLock) {
            return mDiagnosticCapabilities;
        }
    }

    /**
     * Returns the {@link CarDiagnosticEvent} for the current Vehicle HAL's live frame.
     */
    @Nullable
    public CarDiagnosticEvent getCurrentLiveFrame() {
        try {
            HalPropValue value = mVehicleHal.get(VehicleProperty.OBD2_LIVE_FRAME);
            return createCarDiagnosticEvent(value);
        } catch (ServiceSpecificException e) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "Failed to read OBD2_LIVE_FRAME.", e);
            return null;
        } catch (IllegalArgumentException e) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "illegal argument trying to read OBD2_LIVE_FRAME", e);
            return null;
        }
    }

    /**
     * Returns all timestamps for the Vehicle HAL's Freeze Frame data.
     */
    @Nullable
    public long[] getFreezeFrameTimestamps() {
        try {
            HalPropValue value = mVehicleHal.get(VehicleProperty.OBD2_FREEZE_FRAME_INFO);
            long[] timestamps = new long[value.getInt64ValuesSize()];
            for (int i = 0; i < timestamps.length; ++i) {
                timestamps[i] = value.getInt64Value(i);
            }
            return timestamps;
        } catch (ServiceSpecificException e) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "Failed to read OBD2_FREEZE_FRAME_INFO.", e);
            return null;
        } catch (IllegalArgumentException e) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "illegal argument trying to read OBD2_FREEZE_FRAME_INFO",
                    e);
            return null;
        }
    }

    /**
     * Returns the {@link CarDiagnosticEvent} representing a Freeze Frame data for the timestamp
     * passed as parameter.
     */
    @Nullable
    public CarDiagnosticEvent getFreezeFrame(long timestamp) {
        HalPropValue getValue = mPropValueBuilder.build(
                VehicleProperty.OBD2_FREEZE_FRAME, /*areaId=*/0, /*value=*/timestamp);
        try {
            HalPropValue value = mVehicleHal.get(getValue);
            return createCarDiagnosticEvent(value);
        } catch (ServiceSpecificException e) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "Failed to read OBD2_FREEZE_FRAME.", e);
            return null;
        } catch (IllegalArgumentException e) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "illegal argument trying to read OBD2_FREEZE_FRAME", e);
            return null;
        }
    }

    /**
     * Clears all Vehicle HAL's Freeze Frame data for the timestamps passed as parameter.
     */
    public void clearFreezeFrames(long... timestamps) {
        HalPropValue value = mPropValueBuilder.build(
                VehicleProperty.OBD2_FREEZE_FRAME_CLEAR, /*areaId=*/0, /*values=*/timestamps);
        try {
            mVehicleHal.set(value);
        } catch (ServiceSpecificException e) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "Failed to write OBD2_FREEZE_FRAME_CLEAR.", e);
        } catch (IllegalArgumentException e) {
            Slogf.e(CarLog.TAG_DIAGNOSTIC, "illegal argument trying to write "
                    + "OBD2_FREEZE_FRAME_CLEAR", e);
        }
    }
}
