/*
 * Copyright (C) 2022 The Android Open Source Project.
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
package com.android.car.cartelemetryapp;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Service;
import android.car.Car;
import android.car.telemetry.CarTelemetryManager;
import android.car.telemetry.CarTelemetryManager.AddMetricsConfigCallback;
import android.car.telemetry.TelemetryProto.MetricsConfig;
import android.car.telemetry.TelemetryProto.TelemetryError;
import android.content.Intent;
import android.os.IBinder;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.RemoteException;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Service to interface with CarTelemetryManager.
 */
public class CarMetricsCollectorService extends Service {
    private static final int HISTORY_SIZE = 10;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final ReportListener mReportListener = new ReportListener();
    private final ReportCallback mReportCallback = new ReportCallback();
    private Car mCar;
    private CarTelemetryManager mCarTelemetryManager;
    private ConfigParser mConfigParser;
    private Map<String, MetricsConfig> mConfigs;
    private Map<String, IConfigData> mConfigData = new HashMap<>();
    private Map<String, Deque<PersistableBundle>> mBundleHistory = new HashMap<>();
    private Map<String, Deque<String>> mErrorHistory = new HashMap<>();
    private IConfigStateListener mConfigStateListener;
    private IResultListener mResultListener;
    private AddMetricsConfigCallback mAddConfigCallback = new AddConfigCallback();
    private Car.CarServiceLifecycleListener mCarLifecycleListener = (car, ready) -> {
        if (ready) {
            mCarTelemetryManager =
                    (CarTelemetryManager) car.getCarManager(Car.CAR_TELEMETRY_SERVICE);
        }
    };

    private final ICarMetricsCollectorService.Stub mBinder =
            new ICarMetricsCollectorService.Stub() {
                @Override
                public List<IConfigData> getConfigData() {
                    return new ArrayList<>(mConfigData.values());
                }

                @Override
                public void addConfig(String configName) {
                    addMetricsConfig(configName);
                }

                @Override
                public void removeConfig(String configName) {
                    removeMetricsConfig(configName);
                }

                @Override
                public void setConfigStateListener(IConfigStateListener listener) {
                    mConfigStateListener = listener;
                }

                @Override
                public void setResultListener(IResultListener listener) {
                    mResultListener = listener;
                    mCarTelemetryManager.setReportReadyListener(getMainExecutor(), mReportListener);
                }

                @Override
                public List<PersistableBundle> getBundleHistory(String configName) {
                    return new ArrayList(mBundleHistory.get(configName));
                }

                @Override
                public List<String> getErrorHistory(String configName) {
                    return new ArrayList(mErrorHistory.get(configName));
                }

                @Override
                public void clearHistory(String configName) {
                    mBundleHistory.get(configName).clear();
                    mErrorHistory.get(configName).clear();
                    IConfigData configData = mConfigData.get(configName);
                    configData.onReadyTimes = 0;
                    configData.sentBytes = 0;
                    configData.errorCount = 0;
                }

                @Override
                public String getLog() {
                    return dumpLogs();
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        mCar = Car.createCar(
                getApplicationContext(),
                /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                mCarLifecycleListener);
        mConfigParser = new ConfigParser(this.getApplicationContext());
        mConfigs = mConfigParser.getConfigs();
        updateConfigData();
        addActiveConfigs();
    }

    @Override
    public void onDestroy() {
        mCar.disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public String dumpLogs() {
        return mConfigParser.dumpLogs();
    }

    private void addMetricsConfig(String configName) {
        if (!mConfigs.containsKey(configName)) {
            throw new IllegalArgumentException(
                    "Failed to add metrics config, name does not exist! " + configName);
        }
        mCarTelemetryManager.addMetricsConfig(
                configName, mConfigs.get(configName).toByteArray(), mExecutor, mAddConfigCallback);
    }

    private void removeMetricsConfig(String configName) {
        mCarTelemetryManager.removeMetricsConfig(configName);
        mConfigData.get(configName).selected = false;
    }

    private void addActiveConfigs() {
        for (String configName : mConfigs.keySet()) {
            if (mConfigData.get(configName).selected) {
                addMetricsConfig(configName);
            }
        }
    }

    /** Updates the config data mapping from the config list.
     * If config list has newer version config, that config is either added or updated in the
     * config data mapping and set selected.
     */
    private void updateConfigData() {
        // Add new or updated config data
        for (MetricsConfig config : mConfigs.values()) {
            if (!mConfigData.containsKey(config.getName())
                    || mConfigData.get(config.getName()).version < config.getVersion()) {
                IConfigData configData = new IConfigData();
                configData.name = config.getName();
                configData.version = config.getVersion();
                configData.selected = true;
                mConfigData.put(config.getName(), configData);
                mBundleHistory.put(config.getName(), new ArrayDeque<>());
                mErrorHistory.put(config.getName(), new ArrayDeque<>());
            }
        }
        // Remove config data for configs not in mConfigs
        Set<String> keys = mConfigData.keySet();
        for (String name : keys) {
            if (!mConfigs.containsKey(name)) {
                mConfigData.remove(name);
                mBundleHistory.remove(name);
                mErrorHistory.remove(name);
            }
        }
    }

    private String errorToString(TelemetryError error) {
        StringBuilder sb = new StringBuilder()
                .append(":\n")
                .append("    Error type: ")
                .append(error.getErrorType().name())
                .append("\n")
                .append("    Message: ")
                .append(error.getMessage());
        return sb.toString();
    }

    private int getPersistableBundleByteSize(@Nullable PersistableBundle bundle) {
        if (bundle == null) {
            return 0;
        }
        Parcel parcel = Parcel.obtain();
        parcel.writePersistableBundle(bundle);
        int size = parcel.dataSize();
        parcel.recycle();
        return size;
    }

    private class ReportListener implements CarTelemetryManager.ReportReadyListener {
        @Override
        public void onReady(@NonNull String metricsConfigName) {
            mCarTelemetryManager.getFinishedReport(
                    metricsConfigName, getMainExecutor(), mReportCallback);
        }
    }

    private class ReportCallback implements CarTelemetryManager.MetricsReportCallback {
        @Override
        public void onResult(
                @NonNull String metricsConfigName,
                @Nullable PersistableBundle report,
                @Nullable byte[] telemetryError,
                int status) {
            IConfigData configData = mConfigData.get(metricsConfigName);
            String errorString = null;
            if (report != null) {
                if (!mBundleHistory.containsKey(metricsConfigName)) {
                    mBundleHistory.put(metricsConfigName, new ArrayDeque<>());
                }
                Deque<PersistableBundle> reportHistory = mBundleHistory.get(metricsConfigName);
                if (reportHistory.size() >= HISTORY_SIZE) {
                    // Remove oldest element
                    reportHistory.pollFirst();
                }
                reportHistory.addLast(report);
                configData.sentBytes += getPersistableBundleByteSize(report);
                configData.onReadyTimes += 1;
            }
            if (telemetryError != null) {
                if (!mErrorHistory.containsKey(metricsConfigName)) {
                    mErrorHistory.put(metricsConfigName, new ArrayDeque<>());
                }
                Deque<String> errorHistory = mErrorHistory.get(metricsConfigName);
                if (errorHistory.size() >= HISTORY_SIZE) {
                    // Remove oldest element
                    errorHistory.pollFirst();
                }
                TelemetryError error;
                try {
                    error = TelemetryError.parseFrom(telemetryError);
                } catch (InvalidProtocolBufferException e) {
                    throw new IllegalStateException(
                            "Failed to get error from bytes, invalid proto buffer.", e);
                }
                errorString = errorToString(error);
                errorHistory.addLast(errorString);
                configData.errorCount += 1;
            }
            try {
                mResultListener.onResult(metricsConfigName, configData, report, errorString);
            } catch (RemoteException e) {
                throw new IllegalStateException(
                        "Failed to call IResultListener.onResult.", e);
            }
        }
    }

    private class AddConfigCallback implements AddMetricsConfigCallback {
        @Override
        public void onAddMetricsConfigStatus(
                @NonNull String metricsConfigName, int statusCode) {
            if (statusCode == CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED
                    || statusCode == CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_ALREADY_EXISTS) {
                mConfigData.get(metricsConfigName).selected = true;
                if (mConfigStateListener != null) {
                    try {
                        mConfigStateListener.onConfigAdded(metricsConfigName);
                    } catch (RemoteException e) {
                        throw new IllegalStateException(
                            "Failed to call IConfigStateListener.onConfigAdded.", e);
                    }
                }
            }
        }
    }
}
