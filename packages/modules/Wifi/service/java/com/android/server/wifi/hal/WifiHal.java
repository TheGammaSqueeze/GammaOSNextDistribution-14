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

package com.android.server.wifi.hal;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.SsidTranslator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wrapper around the root Wifi HAL.
 * Depending on the service available, may initialize using HIDL or AIDL.
 */
public class WifiHal {
    private static final String TAG = "WifiHal";
    private IWifiHal mWifiHal;

    /**
     * Wifi operation status codes.
     */
    public static final int WIFI_STATUS_SUCCESS = 0;
    public static final int WIFI_STATUS_ERROR_WIFI_CHIP_INVALID = 1;
    public static final int WIFI_STATUS_ERROR_WIFI_IFACE_INVALID = 2;
    public static final int WIFI_STATUS_ERROR_WIFI_RTT_CONTROLLER_INVALID = 3;
    public static final int WIFI_STATUS_ERROR_NOT_SUPPORTED = 4;
    public static final int WIFI_STATUS_ERROR_NOT_AVAILABLE = 5;
    public static final int WIFI_STATUS_ERROR_NOT_STARTED = 6;
    public static final int WIFI_STATUS_ERROR_INVALID_ARGS = 7;
    public static final int WIFI_STATUS_ERROR_BUSY = 8;
    public static final int WIFI_STATUS_ERROR_UNKNOWN = 9;
    public static final int WIFI_STATUS_ERROR_REMOTE_EXCEPTION = 10;

    @IntDef(prefix = { "WIFI_STATUS_" }, value = {
            WIFI_STATUS_SUCCESS,
            WIFI_STATUS_ERROR_WIFI_CHIP_INVALID,
            WIFI_STATUS_ERROR_WIFI_IFACE_INVALID,
            WIFI_STATUS_ERROR_WIFI_RTT_CONTROLLER_INVALID,
            WIFI_STATUS_ERROR_NOT_SUPPORTED,
            WIFI_STATUS_ERROR_NOT_AVAILABLE,
            WIFI_STATUS_ERROR_NOT_STARTED,
            WIFI_STATUS_ERROR_INVALID_ARGS,
            WIFI_STATUS_ERROR_BUSY,
            WIFI_STATUS_ERROR_UNKNOWN,
            WIFI_STATUS_ERROR_REMOTE_EXCEPTION,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface WifiStatusCode {}

    /**
     * Interface that can be created by the Wi-Fi HAL.
     */
    public interface WifiInterface {
        /**
         * Get the name of this interface.
         */
        String getName();
    }

    /**
     * Framework callback object. Will get called when the equivalent events are received
     * from the HAL.
     */
    public interface Callback {
        /**
         * Called when the Wi-Fi system failed in a way that caused it be disabled.
         * Calling start again must restart Wi-Fi as if stop then start was called
         * (full state reset). When this event is received, all WifiChip & WifiIface
         * objects retrieved after the last call to start will be considered invalid.
         *
         * @param status Failure reason code.
         */
        void onFailure(@WifiStatusCode int status);

        /**
         * Called in response to a call to start, indicating that the operation
         * completed. After this callback the HAL must be fully operational.
         */
        void onStart();

        /**
         * Called in response to a call to stop, indicating that the operation
         * completed. When this event is received, all WifiChip objects retrieved
         * after the last call to start will be considered invalid.
         */
        void onStop();

        /**
         * Must be called when the Wi-Fi subsystem restart completes.
         * Once this event is received, the framework must fully reset the Wi-Fi stack state.
         *
         * @param status Status code.
         */
        void onSubsystemRestart(@WifiStatusCode int status);
    }

    /**
     * Framework death recipient object. Called if the death recipient registered with the HAL
     * indicates that the service died.
     */
    public interface DeathRecipient {
        /**
         * Called on service death.
         */
        void onDeath();
    }

    public WifiHal(@NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
        mWifiHal = createWifiHalMockable(context, ssidTranslator);
    }

    @VisibleForTesting
    protected IWifiHal createWifiHalMockable(@NonNull Context context,
            @NonNull SsidTranslator ssidTranslator) {
        if (WifiHalHidlImpl.serviceDeclared()) {
            return new WifiHalHidlImpl(context, ssidTranslator);
        } else if (WifiHalAidlImpl.serviceDeclared()) {
            return new WifiHalAidlImpl(context, ssidTranslator);
        } else {
            Log.e(TAG, "No HIDL or AIDL service available for the Wifi Vendor HAL.");
            return null;
        }
    }

    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiHal == null) {
            Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiHal is null");
            return defaultVal;
        }
        return supplier.get();
    }

    /**
     * See comments for {@link IWifiHal#getChip(int)}
     */
    @Nullable
    public WifiChip getChip(int chipId) {
        return validateAndCall("getChip", null,
                () -> mWifiHal.getChip(chipId));
    }

    /**
     * See comments for {@link IWifiHal#getChipIds()}
     */
    @Nullable
    public List<Integer> getChipIds() {
        return validateAndCall("getChipIds", null,
                () -> mWifiHal.getChipIds());
    }

    /**
     * See comments for {@link IWifiHal#registerEventCallback(Callback)}
     */
    public boolean registerEventCallback(Callback callback) {
        return validateAndCall("registerEventCallback", false,
                () -> mWifiHal.registerEventCallback(callback));
    }

    /**
     * See comments for {@link IWifiHal#initialize(DeathRecipient)}
     */
    public void initialize(WifiHal.DeathRecipient deathRecipient) {
        if (mWifiHal != null) {
            mWifiHal.initialize(deathRecipient);
        }
    }

    /**
     * See comments for {@link IWifiHal#isInitializationComplete()}
     */
    public boolean isInitializationComplete() {
        return validateAndCall("isInitializationComplete", false,
                () -> mWifiHal.isInitializationComplete());
    }

    /**
     * See comments for {@link IWifiHal#isSupported()}
     */
    public boolean isSupported() {
        return validateAndCall("isSupported", false,
                () -> mWifiHal.isSupported());
    }

    /**
     * See comments for {@link IWifiHal#start()}
     */
    public @WifiStatusCode int start() {
        return validateAndCall("start", WIFI_STATUS_ERROR_UNKNOWN,
                () -> mWifiHal.start());
    }

    /**
     * See comments for {@link IWifiHal#isStarted()}
     */
    public boolean isStarted() {
        return validateAndCall("isStarted", false,
                () -> mWifiHal.isStarted());
    }

    /**
     * See comments for {@link IWifiHal#stop()}
     */
    public boolean stop() {
        return validateAndCall("stop", false,
                () -> mWifiHal.stop());
    }

    /**
     * See comments for {@link IWifiHal#invalidate()}
     */
    public void invalidate() {
        if (mWifiHal != null) {
            mWifiHal.invalidate();
        }
    }
}
