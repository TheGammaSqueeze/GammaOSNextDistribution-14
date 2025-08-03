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

import static android.car.VehiclePropertyIds.CLUSTER_DISPLAY_STATE;
import static android.car.VehiclePropertyIds.CLUSTER_HEARTBEAT;
import static android.car.VehiclePropertyIds.CLUSTER_NAVIGATION_STATE;
import static android.car.VehiclePropertyIds.CLUSTER_REPORT_STATE;
import static android.car.VehiclePropertyIds.CLUSTER_REQUEST_DISPLAY;
import static android.car.VehiclePropertyIds.CLUSTER_SWITCH_UI;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.common.CommonConstants.EMPTY_FLOAT_ARRAY;
import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;
import static com.android.car.internal.common.CommonConstants.EMPTY_LONG_ARRAY;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.os.ServiceSpecificException;
import android.os.SystemClock;

import com.android.car.R;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IntArray;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

/**
 * Translates HAL input events to higher-level semantic information.
 */
public final class ClusterHalService extends HalServiceBase {
    private static final String TAG = ClusterHalService.class.getSimpleName();

    // The value of config_clusterHomeServiceOnMode that are currently supported:
    // 0: FULL mode. ClusterHomeService is enabled only when all CORE_PROPERTIES are available.
    // 1: LIGHT mode. No properties need to be available. In this mode, all service methods that
    //    rely on core properties will throw IllegalStateException, regardless of whether the
    //    property is actually available or not.
    private static final int CONFIG_CLUSTER_HOME_SERVICE_FULL_MODE = 0;
    private static final int CONFIG_CLUSTER_HOME_SERVICE_LIGHT_MODE = 1;

    public static final int DISPLAY_OFF = 0;
    public static final int DISPLAY_ON = 1;
    public static final int DONT_CARE = -1;

    /**
     * Interface to receive incoming Cluster HAL events.
     */
    public interface ClusterHalEventCallback {
        /**
         * Called when CLUSTER_SWITCH_UI message is received.
         *
         * @param uiType uiType ClusterOS wants to switch to
         */
        void onSwitchUi(int uiType);

        /**
         * Called when CLUSTER_DISPLAY_STATE message is received.
         *
         * @param onOff 0 - off, 1 - on
         * @param bounds the area to render the cluster Activity in pixel
         * @param insets Insets of the cluster display
         */
        void onDisplayState(int onOff, Rect bounds, Insets insets);
    };

    private static final int[] SUPPORTED_PROPERTIES = new int[]{
            CLUSTER_SWITCH_UI,
            CLUSTER_DISPLAY_STATE,
            CLUSTER_REPORT_STATE,
            CLUSTER_REQUEST_DISPLAY,
            CLUSTER_NAVIGATION_STATE,
            CLUSTER_HEARTBEAT,
    };

    private static final int[] CORE_PROPERTIES = new int[]{
            CLUSTER_SWITCH_UI,
            CLUSTER_REPORT_STATE,
            CLUSTER_DISPLAY_STATE,
            CLUSTER_REQUEST_DISPLAY,
    };

    private static final int[] SUBSCRIBABLE_PROPERTIES = new int[]{
            CLUSTER_SWITCH_UI,
            CLUSTER_DISPLAY_STATE,
    };

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private ClusterHalEventCallback mCallback;

    private final VehicleHal mHal;

    // The value of config_clusterHomeServiceOnMode
    private final int mServiceMode;
    // Whether all CORE_PROPERTIES are available.
    private volatile boolean mIsCoreSupported;
    private volatile boolean mIsNavigationStateSupported;
    private volatile boolean mIsHeartbeatSupported;

    private final HalPropValueBuilder mPropValueBuilder;

    public ClusterHalService(Context context, VehicleHal hal) {
        mHal = hal;
        mPropValueBuilder = hal.getHalPropValueBuilder();
        mServiceMode = context.getResources().getInteger(R.integer.config_clusterHomeServiceMode);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Note that {@link #takeProperties} must be called before this method, so that available
     * properties are correctly initialized.</p>
     */
    @Override
    public void init() {
        Slogf.d(TAG, "initClusterHalService");
        // Do not subscribe if the config is not FULL mode, or any core property is not available.
        if (!isFullModeEnabled()) return;

        for (int property : SUBSCRIBABLE_PROPERTIES) {
            mHal.subscribePropertySafe(this, property);
        }
    }

    @Override
    public void release() {
        Slogf.d(TAG, "releaseClusterHalService");
        synchronized (mLock) {
            mCallback = null;
        }
    }

    /**
     * Sets the event callback to receive Cluster HAL events.
     */
    public void setCallback(ClusterHalEventCallback callback) {
        synchronized (mLock) {
            mCallback = callback;
        }
    }

    @NonNull
    @Override
    public int[] getAllSupportedProperties() {
        return SUPPORTED_PROPERTIES;
    }

    @Override
    public void takeProperties(@NonNull Collection<HalPropConfig> properties) {
        IntArray supportedProperties = new IntArray(properties.size());
        for (HalPropConfig property : properties) {
            supportedProperties.add(property.getPropId());
        }
        mIsCoreSupported = true;
        for (int coreProperty : CORE_PROPERTIES) {
            if (supportedProperties.indexOf(coreProperty) < 0) {
                mIsCoreSupported = false;
                break;
            }
        }
        mIsNavigationStateSupported = supportedProperties.indexOf(CLUSTER_NAVIGATION_STATE) >= 0;
        mIsHeartbeatSupported = supportedProperties.indexOf(CLUSTER_HEARTBEAT) >= 0;
        Slogf.d(TAG, "takeProperties: coreSupported=%s, navigationStateSupported=%s, "
                + "heartbeatSupported=%s",
                mIsCoreSupported, mIsNavigationStateSupported, mIsHeartbeatSupported);
    }

    @VisibleForTesting
    boolean isFullModeEnabled() {
        // In FULL mode, all core properties need to be available.
        return mIsCoreSupported && (mServiceMode == CONFIG_CLUSTER_HOME_SERVICE_FULL_MODE);
    }

    public boolean isLightMode() {
        return mServiceMode == CONFIG_CLUSTER_HOME_SERVICE_LIGHT_MODE;
    }

    public boolean isServiceEnabled() {
        return isFullModeEnabled() || isLightMode();
    }

    public boolean isNavigationStateSupported() {
        return mIsNavigationStateSupported;
    }

    public boolean isHeartbeatSupported() {
        return mIsHeartbeatSupported;
    }

    @Override
    public void onHalEvents(List<HalPropValue> values) {
        Slogf.d(TAG, "handleHalEvents(): %s", values);
        ClusterHalEventCallback callback;
        synchronized (mLock) {
            callback = mCallback;
        }
        if (callback == null || !isFullModeEnabled()) {
            return;
        }

        for (HalPropValue value : values) {
            switch (value.getPropId()) {
                case CLUSTER_SWITCH_UI:
                    if (value.getInt32ValuesSize() < 1) {
                        Slogf.e(TAG, "received invalid CLUSTER_SWITCH_UI property from HAL, "
                                + "expect at least 1 int value.");
                        break;
                    }
                    int uiType = value.getInt32Value(0);
                    callback.onSwitchUi(uiType);
                    break;
                case CLUSTER_DISPLAY_STATE:
                    if (value.getInt32ValuesSize() < 9) {
                        Slogf.e(TAG, "received invalid CLUSTER_DISPLAY_STATE property from HAL, "
                                + "expect at least 9 int value.");
                        break;
                    }
                    int onOff = value.getInt32Value(0);
                    Rect bounds = null;
                    if (hasNoDontCare(value, /* start= */ 1, /* length= */ 4, "bounds")) {
                        bounds =
                                new Rect(
                                        value.getInt32Value(1), value.getInt32Value(2),
                                        value.getInt32Value(3), value.getInt32Value(4));
                    }
                    Insets insets = null;
                    if (hasNoDontCare(value, /* start= */ 5, /* length= */ 4, "insets")) {
                        insets =
                                Insets.of(
                                        value.getInt32Value(5), value.getInt32Value(6),
                                        value.getInt32Value(7), value.getInt32Value(8));
                    }
                    callback.onDisplayState(onOff, bounds, insets);
                    break;
                default:
                    Slogf.w(TAG, "received unsupported event from HAL: %s", value);
            }
        }
    }

    private static boolean hasNoDontCare(HalPropValue value, int start, int length,
                                         String fieldName) {
        int count = 0;
        for (int i = start; i < start + length; ++i) {
            if (value.getInt32Value(i) == DONT_CARE) {
                ++count;
            }
        }
        if (count == 0) {
            return true;
        }
        if (count != length) {
            Slogf.w(TAG, "Don't care should be set in the whole %s.", fieldName);
        }
        return false;
    }

    /**
     * Reports the current display state and ClusterUI state.
     *
     * @param onOff 0 - off, 1 - on
     * @param bounds the area to render the cluster Activity in pixel
     * @param insets Insets of the cluster display
     * @param uiTypeMain uiType that ClusterHome tries to show in main area
     * @param uiTypeSub uiType that ClusterHome tries to show in sub area
     * @param uiAvailability the byte array to represent the availability of ClusterUI.
     */
    public void reportState(int onOff, Rect bounds, Insets insets,
            int uiTypeMain, int uiTypeSub, byte[] uiAvailability) {
        if (!isFullModeEnabled()) {
            throw new IllegalStateException(
                    "reportState: one or more core property is not supported on this device, "
                            + "or the service is not in FULL mode");
        }
        int[] intValues = new int[]{
            onOff,
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            insets.left,
            insets.top,
            insets.right,
            insets.bottom,
            uiTypeMain,
            uiTypeSub
        };
        HalPropValue request = mPropValueBuilder.build(CLUSTER_REPORT_STATE,
                /* areaId= */ 0, SystemClock.elapsedRealtime(), VehiclePropertyStatus.AVAILABLE,
                /* int32Values= */ intValues, /* floatValues= */ EMPTY_FLOAT_ARRAY,
                /* int64Values= */ EMPTY_LONG_ARRAY, /* stringValue= */ "",
                /* byteValues= */ uiAvailability);
        send(request);
    }

    /**
     * Requests to turn the cluster display on to show some ClusterUI.
     *
     * @param uiType uiType that ClusterHome tries to show in main area
     */
    public void requestDisplay(int uiType) {
        if (!isFullModeEnabled()) {
            throw new IllegalStateException("requestDisplay: one or more core property is "
                    + "not supported on this device, or the service is not in FULL mode");
        }
        HalPropValue request = mPropValueBuilder.build(CLUSTER_REQUEST_DISPLAY,
                /* areaId= */ 0, SystemClock.elapsedRealtime(), VehiclePropertyStatus.AVAILABLE,
                /* value= */ uiType);
        send(request);
    }


    /**
     * Informs the current navigation state.
     *
     * @param navigateState the serialized message of {@code NavigationStateProto}
     */
    public void sendNavigationState(byte[] navigateState) {
        if (!isNavigationStateSupported()) {
            return;
        }
        HalPropValue request = mPropValueBuilder.build(CLUSTER_NAVIGATION_STATE,
                /* areaId= */ 0, SystemClock.elapsedRealtime(), VehiclePropertyStatus.AVAILABLE,
                /* value= */ navigateState);
        send(request);
    }

    /**
     * Sends a heartbeat to ClusterOS
     * @param epochTimeNs the current time
     * @param visibility 0 means invisible and 1 means visible.
     * @param appMetadata the application specific metadata which will be delivered with
     *                    the heartbeat.
     */
    public void sendHeartbeat(long epochTimeNs, long visibility, byte[] appMetadata) {
        long[] longValues = new long[]{
                epochTimeNs,
                visibility
        };
        HalPropValue request = mPropValueBuilder.build(CLUSTER_HEARTBEAT,
                /* areaId= */ 0, SystemClock.elapsedRealtime(), VehiclePropertyStatus.AVAILABLE,
                /* int32Values= */ EMPTY_INT_ARRAY, /* floatValues= */ EMPTY_FLOAT_ARRAY,
                /* int64Values= */ longValues, /* stringValue= */ "",
                /* byteValues= */ appMetadata);
        send(request);
    }

    private void send(HalPropValue request) {
        try {
            mHal.set(request);
        } catch (ServiceSpecificException | IllegalArgumentException e) {
            Slogf.e(TAG, "Failed to send request: " + request, e);
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(PrintWriter writer) {
        writer.println("*Cluster HAL*");
        writer.println("mServiceMode: " + mServiceMode);
        writer.println("mIsCoreSupported: " + mIsCoreSupported);
        writer.println("mIsNavigationStateSupported: " + mIsNavigationStateSupported);
        writer.println("mIsHeartbeatSupported: " + mIsHeartbeatSupported);
    }
}
