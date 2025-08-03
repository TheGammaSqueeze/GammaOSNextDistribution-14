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
package com.android.bluetooth.gatt;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProtoEnums;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.os.ParcelUuid;
import android.util.SparseArray;

import androidx.annotation.VisibleForTesting;

import com.android.bluetooth.btservice.MetricsLogger;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ScanStats class helps keep track of information about scans
 * on a per application basis.
 * @hide
 */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class AppAdvertiseStats {
    private static final String TAG = AppAdvertiseStats.class.getSimpleName();

    private static DateTimeFormatter sDateFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    static final String[] PHY_LE_STRINGS = {"LE_1M", "LE_2M", "LE_CODED"};
    static final int UUID_STRING_FILTER_LEN = 8;

    // ContextMap here is needed to grab Apps and Connections
    ContextMap mContextMap;

    // GattService is needed to add scan event protos to be dumped later
    GattService mGattService;

    static class AppAdvertiserData {
        public boolean includeDeviceName = false;
        public boolean includeTxPowerLevel = false;
        public SparseArray<byte[]> manufacturerData;
        public Map<ParcelUuid, byte[]> serviceData;
        public List<ParcelUuid> serviceUuids;
        AppAdvertiserData(boolean includeDeviceName, boolean includeTxPowerLevel,
                SparseArray<byte[]> manufacturerData, Map<ParcelUuid, byte[]> serviceData,
                List<ParcelUuid> serviceUuids) {
            this.includeDeviceName = includeDeviceName;
            this.includeTxPowerLevel = includeTxPowerLevel;
            this.manufacturerData = manufacturerData;
            this.serviceData = serviceData;
            this.serviceUuids = serviceUuids;
        }
    }

    static class AppAdvertiserRecord {
        public Instant startTime = null;
        public Instant stopTime = null;
        public int duration = 0;
        public int maxExtendedAdvertisingEvents = 0;
        AppAdvertiserRecord(Instant startTime) {
            this.startTime = startTime;
        }
    }

    private int mAppUid;
    private String mAppName;
    private int mId;
    private boolean mAdvertisingEnabled = false;
    private boolean mPeriodicAdvertisingEnabled = false;
    private int mPrimaryPhy = BluetoothDevice.PHY_LE_1M;
    private int mSecondaryPhy = BluetoothDevice.PHY_LE_1M;
    private int mInterval = 0;
    private int mTxPowerLevel = 0;
    private boolean mLegacy = false;
    private boolean mAnonymous = false;
    private boolean mConnectable = false;
    private boolean mScannable = false;
    private AppAdvertiserData mAdvertisingData = null;
    private AppAdvertiserData mScanResponseData = null;
    private AppAdvertiserData mPeriodicAdvertisingData = null;
    private boolean mPeriodicIncludeTxPower = false;
    private int mPeriodicInterval = 0;
    public ArrayList<AppAdvertiserRecord> mAdvertiserRecords =
            new ArrayList<AppAdvertiserRecord>();

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public AppAdvertiseStats(int appUid, int id, String name, ContextMap map, GattService service) {
        this.mAppUid = appUid;
        this.mId = id;
        this.mAppName = name;
        this.mContextMap = map;
        this.mGattService = service;
    }

    void recordAdvertiseStart(AdvertisingSetParameters parameters,
            AdvertiseData advertiseData, AdvertiseData scanResponse,
            PeriodicAdvertisingParameters periodicParameters, AdvertiseData periodicData,
            int duration, int maxExtAdvEvents) {
        mAdvertisingEnabled = true;
        AppAdvertiserRecord record = new AppAdvertiserRecord(Instant.now());
        record.duration = duration;
        record.maxExtendedAdvertisingEvents = maxExtAdvEvents;
        mAdvertiserRecords.add(record);
        if (mAdvertiserRecords.size() > 5) {
            mAdvertiserRecords.remove(0);
        }

        if (parameters != null) {
            mPrimaryPhy = parameters.getPrimaryPhy();
            mSecondaryPhy = parameters.getSecondaryPhy();
            mInterval = parameters.getInterval();
            mTxPowerLevel = parameters.getTxPowerLevel();
            mLegacy = parameters.isLegacy();
            mAnonymous = parameters.isAnonymous();
            mConnectable = parameters.isConnectable();
            mScannable = parameters.isScannable();
        }

        if (advertiseData != null) {
            mAdvertisingData = new AppAdvertiserData(advertiseData.getIncludeDeviceName(),
                    advertiseData.getIncludeTxPowerLevel(),
                    advertiseData.getManufacturerSpecificData(),
                    advertiseData.getServiceData(),
                    advertiseData.getServiceUuids());
        }

        if (scanResponse != null) {
            mScanResponseData = new AppAdvertiserData(scanResponse.getIncludeDeviceName(),
                    scanResponse.getIncludeTxPowerLevel(),
                    scanResponse.getManufacturerSpecificData(),
                    scanResponse.getServiceData(),
                    scanResponse.getServiceUuids());
        }

        if (periodicData != null) {
            mPeriodicAdvertisingData = new AppAdvertiserData(
                    periodicData.getIncludeDeviceName(),
                    periodicData.getIncludeTxPowerLevel(),
                    periodicData.getManufacturerSpecificData(),
                    periodicData.getServiceData(),
                    periodicData.getServiceUuids());
        }

        if (periodicParameters != null) {
            mPeriodicAdvertisingEnabled = true;
            mPeriodicIncludeTxPower = periodicParameters.getIncludeTxPower();
            mPeriodicInterval = periodicParameters.getInterval();
        }
        recordAdvertiseEnableCount(true, mConnectable, mPeriodicAdvertisingEnabled);
    }

    void recordAdvertiseStart(int duration, int maxExtAdvEvents) {
        recordAdvertiseStart(null, null, null, null, null, duration, maxExtAdvEvents);
    }

    void recordAdvertiseStop() {
        recordAdvertiseEnableCount(false, mConnectable, mPeriodicAdvertisingEnabled);
        if (!mAdvertiserRecords.isEmpty()) {
            AppAdvertiserRecord record = mAdvertiserRecords.get(mAdvertiserRecords.size() - 1);
            record.stopTime = Instant.now();
            Duration duration = Duration.between(record.startTime, record.stopTime);
            recordAdvertiseDurationCount(duration, mConnectable, mPeriodicAdvertisingEnabled);
        }
        mAdvertisingEnabled = false;
        mPeriodicAdvertisingEnabled = false;
    }

    static void recordAdvertiseInstanceCount(int instanceCount) {
        if (instanceCount < 5) {
            MetricsLogger.getInstance().cacheCount(BluetoothProtoEnums.LE_ADV_INSTANCE_COUNT_5, 1);
        } else if (instanceCount < 10) {
            MetricsLogger.getInstance().cacheCount(BluetoothProtoEnums.LE_ADV_INSTANCE_COUNT_10, 1);
        } else if (instanceCount < 15) {
            MetricsLogger.getInstance().cacheCount(BluetoothProtoEnums.LE_ADV_INSTANCE_COUNT_15, 1);
        } else {
            MetricsLogger.getInstance().cacheCount(
                    BluetoothProtoEnums.LE_ADV_INSTANCE_COUNT_15P, 1);
        }
    }

    static void recordAdvertiseErrorCount(int key) {
        if (key != BluetoothProtoEnums.LE_ADV_ERROR_ON_START_COUNT) {
            return;
        }
        MetricsLogger.getInstance().cacheCount(key, 1);
    }

    void enableAdvertisingSet(boolean enable, int duration, int maxExtAdvEvents) {
        if (enable) {
            //if the advertisingSet have not been disabled, skip enabling.
            if (!mAdvertisingEnabled) {
                recordAdvertiseStart(duration, maxExtAdvEvents);
            }
        } else {
            //if the advertisingSet have not been enabled, skip disabling.
            if (mAdvertisingEnabled) {
                recordAdvertiseStop();
            }
        }
    }

    void setAdvertisingData(AdvertiseData data) {
        if (mAdvertisingData == null) {
            mAdvertisingData = new AppAdvertiserData(data.getIncludeDeviceName(),
                    data.getIncludeTxPowerLevel(),
                    data.getManufacturerSpecificData(),
                    data.getServiceData(),
                    data.getServiceUuids());
        } else if (data != null) {
            mAdvertisingData.includeDeviceName = data.getIncludeDeviceName();
            mAdvertisingData.includeTxPowerLevel = data.getIncludeTxPowerLevel();
            mAdvertisingData.manufacturerData = data.getManufacturerSpecificData();
            mAdvertisingData.serviceData = data.getServiceData();
            mAdvertisingData.serviceUuids = data.getServiceUuids();
        }
    }

    void setScanResponseData(AdvertiseData data) {
        if (mScanResponseData == null) {
            mScanResponseData = new AppAdvertiserData(data.getIncludeDeviceName(),
                    data.getIncludeTxPowerLevel(),
                    data.getManufacturerSpecificData(),
                    data.getServiceData(),
                    data.getServiceUuids());
        } else if (data != null) {
            mScanResponseData.includeDeviceName = data.getIncludeDeviceName();
            mScanResponseData.includeTxPowerLevel = data.getIncludeTxPowerLevel();
            mScanResponseData.manufacturerData = data.getManufacturerSpecificData();
            mScanResponseData.serviceData = data.getServiceData();
            mScanResponseData.serviceUuids = data.getServiceUuids();
        }
    }

    void setAdvertisingParameters(AdvertisingSetParameters parameters) {
        if (parameters != null) {
            mPrimaryPhy = parameters.getPrimaryPhy();
            mSecondaryPhy = parameters.getSecondaryPhy();
            mInterval = parameters.getInterval();
            mTxPowerLevel = parameters.getTxPowerLevel();
            mLegacy = parameters.isLegacy();
            mAnonymous = parameters.isAnonymous();
            mConnectable = parameters.isConnectable();
            mScannable = parameters.isScannable();
        }
    }

    void setPeriodicAdvertisingParameters(PeriodicAdvertisingParameters parameters) {
        if (parameters != null) {
            mPeriodicIncludeTxPower = parameters.getIncludeTxPower();
            mPeriodicInterval = parameters.getInterval();
        }
    }

    void setPeriodicAdvertisingData(AdvertiseData data) {
        if (mPeriodicAdvertisingData == null) {
            mPeriodicAdvertisingData = new AppAdvertiserData(data.getIncludeDeviceName(),
                    data.getIncludeTxPowerLevel(),
                    data.getManufacturerSpecificData(),
                    data.getServiceData(),
                    data.getServiceUuids());
        } else if (data != null) {
            mPeriodicAdvertisingData.includeDeviceName = data.getIncludeDeviceName();
            mPeriodicAdvertisingData.includeTxPowerLevel = data.getIncludeTxPowerLevel();
            mPeriodicAdvertisingData.manufacturerData = data.getManufacturerSpecificData();
            mPeriodicAdvertisingData.serviceData = data.getServiceData();
            mPeriodicAdvertisingData.serviceUuids = data.getServiceUuids();
        }
    }

    void onPeriodicAdvertiseEnabled(boolean enable) {
        mPeriodicAdvertisingEnabled = enable;
    }

    void setId(int id) {
        this.mId = id;
    }

    private static void recordAdvertiseDurationCount(Duration duration, boolean isConnectable,
            boolean inPeriodic) {
        if (duration.compareTo(Duration.ofMinutes(1)) < 0) {
            MetricsLogger.getInstance().cacheCount(
                    BluetoothProtoEnums.LE_ADV_DURATION_COUNT_TOTAL_1M, 1);
            if (isConnectable) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_DURATION_COUNT_CONNECTABLE_1M, 1);
            }
            if (inPeriodic) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_DURATION_COUNT_PERIODIC_1M, 1);
            }
        } else if (duration.compareTo(Duration.ofMinutes(30)) < 0) {
            MetricsLogger.getInstance().cacheCount(
                    BluetoothProtoEnums.LE_ADV_DURATION_COUNT_TOTAL_30M, 1);
            if (isConnectable) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_DURATION_COUNT_CONNECTABLE_30M, 1);
            }
            if (inPeriodic) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_DURATION_COUNT_PERIODIC_30M, 1);
            }
        } else if (duration.compareTo(Duration.ofHours(1)) < 0) {
            MetricsLogger.getInstance().cacheCount(
                    BluetoothProtoEnums.LE_ADV_DURATION_COUNT_TOTAL_1H, 1);
            if (isConnectable) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_DURATION_COUNT_CONNECTABLE_1H, 1);
            }
            if (inPeriodic) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_DURATION_COUNT_PERIODIC_1H, 1);
            }
        } else if (duration.compareTo(Duration.ofHours(3)) < 0) {
            MetricsLogger.getInstance().cacheCount(
                    BluetoothProtoEnums.LE_ADV_DURATION_COUNT_TOTAL_3H, 1);
            if (isConnectable) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_DURATION_COUNT_CONNECTABLE_3H, 1);
            }
            if (inPeriodic) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_DURATION_COUNT_PERIODIC_3H, 1);
            }
        } else {
            MetricsLogger.getInstance().cacheCount(
                    BluetoothProtoEnums.LE_ADV_DURATION_COUNT_TOTAL_3HP, 1);
            if (isConnectable) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_DURATION_COUNT_CONNECTABLE_3HP, 1);
            }
            if (inPeriodic) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_DURATION_COUNT_PERIODIC_3HP, 1);
            }
        }
    }

    private static void recordAdvertiseEnableCount(boolean enable, boolean isConnectable,
            boolean inPeriodic) {
        if (enable) {
            MetricsLogger.getInstance().cacheCount(BluetoothProtoEnums.LE_ADV_COUNT_ENABLE, 1);
            if (isConnectable) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_COUNT_CONNECTABLE_ENABLE, 1);
            }
            if (inPeriodic) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_COUNT_PERIODIC_ENABLE, 1);
            }
        } else {
            MetricsLogger.getInstance().cacheCount(BluetoothProtoEnums.LE_ADV_COUNT_DISABLE, 1);
            if (isConnectable) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_COUNT_CONNECTABLE_DISABLE, 1);
            }
            if (inPeriodic) {
                MetricsLogger.getInstance().cacheCount(
                        BluetoothProtoEnums.LE_ADV_COUNT_PERIODIC_DISABLE, 1);
            }
        }
    }

    private static String printByteArrayInHex(byte[] data) {
        final StringBuilder hex = new StringBuilder();
        for (byte b : data) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static void dumpAppAdvertiserData(StringBuilder sb, AppAdvertiserData advData) {
        sb.append("\n          └Include Device Name                          : "
                + advData.includeDeviceName);
        sb.append("\n          └Include Tx Power Level                       : "
                + advData.includeTxPowerLevel);

        if (advData.manufacturerData.size() > 0) {
            sb.append("\n          └Manufacturer Data (length of data)           : "
                    + advData.manufacturerData.size());
        }

        if (!advData.serviceData.isEmpty()) {
            sb.append("\n          └Service Data(UUID, length of data)           : ");
            for (ParcelUuid uuid : advData.serviceData.keySet()) {
                sb.append("\n            [" + uuid.toString().substring(0, UUID_STRING_FILTER_LEN)
                        + "-xxxx-xxxx-xxxx-xxxxxxxxxxxx, "
                        + advData.serviceData.get(uuid).length + "]");
            }
        }

        if (!advData.serviceUuids.isEmpty()) {
            sb.append("\n          └Service Uuids                                : \n            "
                    + advData.serviceUuids.toString().substring(0, UUID_STRING_FILTER_LEN)
                    + "-xxxx-xxxx-xxxx-xxxxxxxxxxxx");
        }
    }

    private static String dumpPhyString(int phy) {
        if (phy > PHY_LE_STRINGS.length) {
            return Integer.toString(phy);
        } else {
            return PHY_LE_STRINGS[phy - 1];
        }
    }

    private static void dumpAppAdvertiseStats(StringBuilder sb, AppAdvertiseStats stats) {
        sb.append("\n      └Advertising:");
        sb.append("\n        └Interval(0.625ms)                              : "
                + stats.mInterval);
        sb.append("\n        └TX POWER(dbm)                                  : "
                + stats.mTxPowerLevel);
        sb.append("\n        └Primary Phy                                    : "
                + dumpPhyString(stats.mPrimaryPhy));
        sb.append("\n        └Secondary Phy                                  : "
                + dumpPhyString(stats.mSecondaryPhy));
        sb.append("\n        └Legacy                                         : "
                + stats.mLegacy);
        sb.append("\n        └Anonymous                                      : "
                + stats.mAnonymous);
        sb.append("\n        └Connectable                                    : "
                + stats.mConnectable);
        sb.append("\n        └Scannable                                      : "
                + stats.mScannable);

        if (stats.mAdvertisingData != null) {
            sb.append("\n        └Advertise Data:");
            dumpAppAdvertiserData(sb, stats.mAdvertisingData);
        }

        if (stats.mScanResponseData != null) {
            sb.append("\n        └Scan Response:");
            dumpAppAdvertiserData(sb, stats.mScanResponseData);
        }

        if (stats.mPeriodicInterval > 0) {
            sb.append("\n      └Periodic Advertising Enabled                     : "
                    + stats.mPeriodicAdvertisingEnabled);
            sb.append("\n        └Periodic Include TxPower                       : "
                    + stats.mPeriodicIncludeTxPower);
            sb.append("\n        └Periodic Interval(1.25ms)                      : "
                    + stats.mPeriodicInterval);
        }

        if (stats.mPeriodicAdvertisingData != null) {
            sb.append("\n        └Periodic Advertise Data:");
            dumpAppAdvertiserData(sb, stats.mPeriodicAdvertisingData);
        }

        sb.append("\n");
    }

    static void dumpToString(StringBuilder sb, AppAdvertiseStats stats) {
        Instant currentTime = Instant.now();

        sb.append("\n    " + stats.mAppName);
        sb.append("\n     Advertising ID                                     : "
                + stats.mId);
        for (int i = 0; i < stats.mAdvertiserRecords.size(); i++) {
            AppAdvertiserRecord record = stats.mAdvertiserRecords.get(i);

            sb.append("\n      " + (i + 1) + ":");
            sb.append("\n        └Start time                                     : "
                    + sDateFormat.format(record.startTime));
            if (record.stopTime == null) {
                Duration timeElapsed = Duration.between(record.startTime, currentTime);
                sb.append("\n        └Elapsed time                                   : "
                        + timeElapsed.toMillis() + "ms");
            } else {
                sb.append("\n        └Stop time                                      : "
                        + sDateFormat.format(record.stopTime));
            }
            sb.append("\n        └Duration(10ms unit)                            : "
                    + record.duration);
            sb.append("\n        └Maximum number of extended advertising events  : "
                    + record.maxExtendedAdvertisingEvents);
        }

        dumpAppAdvertiseStats(sb, stats);
    }
}
