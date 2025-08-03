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

package com.google.android.tv.btservices.remote;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import com.google.android.tv.btservices.R;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

public abstract class RemoteProxy {

    private static final String TAG = "Atv.RemoteProxy";
    private static final boolean DEBUG = false;

    public static final int DEFAULT_LOW_BATTERY_LEVEL = 20;
    public static final int DEFAULT_CRITICAL_BATTERY_LEVEL = 0;

    // Battery Info Profile
    public static final UUID UUID_BATTERY_SERVICE =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_BATTERY_LEVEL_CHARACTERISTIC =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    // Version info
    public static final UUID UUID_DEVICE_INFO_SERVICE =
            UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_VERSION_CHARACTERISTIC =
            UUID.fromString("00002A28-0000-1000-8000-00805f9b34fb");

    public static class Result {

        public static final int SUCCESS = 1;
        public static final int UNKNOWN_FAILURE = 2;
        public static final int GATT_DISCONNECTED = 3;
        public static final int SUCCESS_NEEDS_PAIRING = 4;
        public static final int NOT_IMPLEMENTED = 5;
        public static final int DEVICE_BUSY = 6;

        protected static final Result SUCCESS_INST = new Result(SUCCESS);
        protected static final Result UNKNOWN_FAILURE_INST = new Result(UNKNOWN_FAILURE);
        protected static final Result GATT_DISCONNECTED_INST = new Result(GATT_DISCONNECTED);
        protected static final Result SUCCESS_NEEDS_PAIRING_INST =
                new Result(SUCCESS_NEEDS_PAIRING);
        protected static final Result NOT_IMPLEMENTED_INST =
                new Result(NOT_IMPLEMENTED);
        protected static final Result DEVICE_BUSY_INST = new Result(DEVICE_BUSY);

        protected int mCode;

        public Result(int code) {
            mCode = code;
        }

        protected Result(Result res) {
            mCode = res.mCode;
        }

        public int code() {
            return mCode;
        }
    }

    public static class BatteryResult extends Result {

        public static final BatteryResult RESULT_FAILURE =
                new BatteryResult(Result.UNKNOWN_FAILURE_INST);
        public static final BatteryResult RESULT_GATT_DISCONNECTED =
                new BatteryResult(Result.GATT_DISCONNECTED);
        public static final BatteryResult RESULT_NOT_IMPLEMENTED =
                new BatteryResult(Result.NOT_IMPLEMENTED_INST);

        private int mBattery;

        public BatteryResult(int battery) {
            super(Result.SUCCESS);
            mBattery = battery;
        }

        private BatteryResult(Result res) {
            super(res);
        }

        public int battery() {
            return mBattery;
        }
    }

    public static class VersionResult extends Result {

        public static final VersionResult RESULT_FAILURE =
                new VersionResult(Result.UNKNOWN_FAILURE_INST);
        public static final VersionResult RESULT_GATT_DISCONNECTED =
                new VersionResult(Result.GATT_DISCONNECTED_INST);

        private Version mVersion;

        public VersionResult(Version version) {
            super(Result.SUCCESS);
            mVersion = version;
        }

        private VersionResult(Result res) {
            super(res);
        }

        public Version version() {
            return mVersion;
        }
    }

    public static class DfuResult extends Result {

        public static final DfuResult RESULT_FAILURE = new DfuResult(Result.UNKNOWN_FAILURE_INST);
        public static final DfuResult RESULT_GATT_DISCONNECTED =
                new DfuResult(Result.GATT_DISCONNECTED_INST);
        public static final DfuResult RESULT_DEVICE_BUSY = new DfuResult(Result.DEVICE_BUSY_INST);
        public static final DfuResult RESULT_SUCCESS = new DfuResult(Result.SUCCESS_INST);
        public static final DfuResult RESULT_SUCCESS_NEEDS_PAIRING =
                new DfuResult(Result.SUCCESS_NEEDS_PAIRING_INST);

        // Continuing index from fields of Result
        public static final int IN_PROGRESS = 103;

        private final double mProgress;

        public DfuResult(double progress) {
            super(IN_PROGRESS);
            mProgress = progress;
        }

        private DfuResult(Result res) {
            super(res);
            mProgress = 0;
        }

        public double progress() {
            return mProgress;
        }
    }

    protected final BluetoothDevice mDevice;

    protected RemoteProxy(Context context, BluetoothDevice device) {
        mDevice = device;
    }

    public abstract boolean initialize(Context context);

    public abstract CompletableFuture<Boolean> refreshBatteryLevel();

    public abstract BatteryResult getLastKnownBatteryLevel();

    /**
     * @param callback The callback which is called by the underlying implementation whenever there
     *                 is a battery level update.
     * @return A CompleteableFuture of Boolean that indicates whether the callback has been
     *         successfully registered.
     */
    public abstract CompletableFuture<Boolean> registerBatteryLevelCallback(Runnable callback);

    public int lowBatteryLevel() {
        return DEFAULT_LOW_BATTERY_LEVEL;
    }

    public String mapBatteryLevel(Context context, int level) {
        return context.getString(R.string.settings_remote_battery_level_percentage_label, level);
    }

    public abstract CompletableFuture<Boolean> refreshVersion();

    public abstract Version getLastKnownVersion();

    public abstract CompletableFuture<DfuResult> requestDfu(
            DfuBinary dfu, DfuManager.Listener listener, boolean background);

    public abstract DfuResult getDfuState();

    public abstract boolean supportsBackgroundDfu();
}
