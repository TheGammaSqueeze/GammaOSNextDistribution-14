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
package com.android.server.uwb.advertisement;

import static com.android.server.uwb.util.DataTypeConversionUtil.macAddressByteArrayToLong;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.uwb.DeviceConfigFacade;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.data.UwbOwrAoaMeasurement;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class UwbAdvertiseManager {
    private static final String TAG = "UwbAdvertiseManager";

    private final ConcurrentHashMap<Long, UwbAdvertiseTarget> mAdvertiseTargetMap =
            new ConcurrentHashMap<>();

    private final UwbInjector mUwbInjector;
    private final DeviceConfigFacade mDeviceConfigFacade;

    public UwbAdvertiseManager(UwbInjector uwbInjector, DeviceConfigFacade deviceConfigFacade) {
        this.mUwbInjector = uwbInjector;
        this.mDeviceConfigFacade = deviceConfigFacade;
    }

    /**
     * Check if the current device is pointing at the remote device, from which we have received
     * One-way Ranging AoA Measurement(s).
     */
    public boolean isPointedTarget(byte[] macAddressBytes) {
        UwbAdvertiseTarget uwbAdvertiseTarget = getAdvertiseTarget(
                macAddressByteArrayToLong(macAddressBytes));
        if (uwbAdvertiseTarget == null) {
            return false;
        }

        if (!uwbAdvertiseTarget.isWithinCriterionAngle()) {
            return false;
        }
        if (!isWithinCriterionVariance(uwbAdvertiseTarget)) {
            return false;
        }

        if (!isWithinTimeThreshold(uwbAdvertiseTarget)) {
            return false;
        }
        return true;
    }

    /**
     * Store a One-way Ranging AoA Measurement from the remote device in a UWB ranging session.
     */
    public void updateAdvertiseTarget(UwbOwrAoaMeasurement uwbOwrAoaMeasurement) {
        // First check if there exists a stale UwbAdvertiseTarget for the device, and remove it.
        checkAndRemoveStaleAdvertiseTarget(uwbOwrAoaMeasurement.mMacAddress);

        // Now store the new measurement for the device.
        updateAdvertiseTargetInfo(uwbOwrAoaMeasurement);
    }

    /**
     * Remove all the stored AdvertiseTarget data for the given device.
     */
    public void removeAdvertiseTarget(long macAddress) {
        mAdvertiseTargetMap.remove(macAddress);
    }

    private boolean isWithinCriterionVariance(UwbAdvertiseTarget uwbAdvertiseTarget) {
        if (!uwbAdvertiseTarget.isVarianceCalculated()) {
            return false;
        }

        int trustedValueOfVariance = mDeviceConfigFacade.getAdvertiseTrustedVarianceValue();
        if (uwbAdvertiseTarget.getVarianceOfAzimuth() > trustedValueOfVariance) {
            return false;
        }

        if (uwbAdvertiseTarget.getVarianceOfElevation() > trustedValueOfVariance) {
            return false;
        }
        return true;
    }

    private void checkAndRemoveStaleAdvertiseTarget(byte[] macAddressBytes) {
        long macAddress = macAddressByteArrayToLong(macAddressBytes);
        UwbAdvertiseTarget uwbAdvertiseTarget = getAdvertiseTarget(macAddress);
        if (uwbAdvertiseTarget == null) {
            return;
        }

        if (!isWithinTimeThreshold(uwbAdvertiseTarget)) {
            removeAdvertiseTarget(macAddress);
        }
    }

    private boolean isWithinTimeThreshold(UwbAdvertiseTarget uwbAdvertiseTarget) {
        long currentTime = mUwbInjector.getElapsedSinceBootMillis();
        if (currentTime - uwbAdvertiseTarget.getLastUpdatedTime()
                > mDeviceConfigFacade.getAdvertiseTimeThresholdMillis()) {
            return false;
        }
        return true;
    }

    private UwbAdvertiseTarget updateAdvertiseTargetInfo(
            UwbOwrAoaMeasurement uwbOwrAoaMeasurement) {
        long currentTime = mUwbInjector.getElapsedSinceBootMillis();
        long macAddress = macAddressByteArrayToLong(uwbOwrAoaMeasurement.getMacAddress());

        UwbAdvertiseTarget advertiseTarget = getOrAddAdvertiseTarget(macAddress);
        advertiseTarget.calculateAoaVariance(uwbOwrAoaMeasurement);
        advertiseTarget.updateLastMeasuredTime(currentTime);

        return advertiseTarget;
    }

    @VisibleForTesting
    @Nullable
    public UwbAdvertiseTarget getAdvertiseTarget(long macAddress) {
        return isAdvertiseTargetExist(macAddress) ? mAdvertiseTargetMap.get(macAddress) : null;
    }

    private UwbAdvertiseTarget getOrAddAdvertiseTarget(long macAddress) {
        UwbAdvertiseTarget uwbAdvertiseTarget;
        if (isAdvertiseTargetExist(macAddress)) {
            uwbAdvertiseTarget = mAdvertiseTargetMap.get(macAddress);
        } else {
            uwbAdvertiseTarget = addAdvertiseTarget(macAddress);
        }
        return uwbAdvertiseTarget;
    }

    private boolean isAdvertiseTargetExist(long macAddress) {
        return mAdvertiseTargetMap.containsKey(macAddress);
    }

    private UwbAdvertiseTarget addAdvertiseTarget(long macAddress) {
        UwbAdvertiseTarget advertiseTarget = new UwbAdvertiseTarget(macAddress);
        mAdvertiseTargetMap.put(macAddress, advertiseTarget);
        return advertiseTarget;
    }

    /**
     * Stored Owr Aoa Measurements for the remote devices. The data should be cleared when the
     * UWB session is closed.
     */
    @VisibleForTesting
    public class UwbAdvertiseTarget {
        private final long mMacAddress;
        private final ArrayList<Double> mRecentAoaAzimuth = new ArrayList<>();
        private final ArrayList<Double> mRecentAoaElevation = new ArrayList<>();
        private double mVarianceOfAzimuth;
        private double mVarianceOfElevation;
        private long mLastMeasuredTime;
        private boolean mIsVarianceCalculated;

        private UwbAdvertiseTarget(long macAddress) {
            mMacAddress = macAddress;
            mIsVarianceCalculated = false;
        }

        private void calculateAoaVariance(UwbOwrAoaMeasurement owrAoaMeasurement) {
            double aoaAzimuth = owrAoaMeasurement.getAoaAzimuth();
            double aoaElevation = owrAoaMeasurement.getAoaElevation();

            mRecentAoaAzimuth.add(aoaAzimuth);
            mRecentAoaElevation.add(aoaElevation);

            int arraySizeToCheck = mDeviceConfigFacade.getAdvertiseArraySizeToCheck();
            int arrayStartIndex = mDeviceConfigFacade.getAdvertiseArrayStartIndexToCalVariance();
            int arrayEndIndex = mDeviceConfigFacade.getAdvertiseArrayEndIndexToCalVariance();

            if (mRecentAoaAzimuth.size() > arraySizeToCheck) {
                mRecentAoaAzimuth.remove(0);
                mRecentAoaElevation.remove(0);
            }

            if (mRecentAoaAzimuth.size() == arraySizeToCheck) {
                double[] azimuthArr =
                        mRecentAoaAzimuth
                                .subList(arrayStartIndex, arrayEndIndex)
                                .stream()
                                .mapToDouble(Double::doubleValue)
                                .toArray();
                mVarianceOfAzimuth = getVariance(azimuthArr);
                double[] elevationArr =
                        mRecentAoaElevation
                                .subList(arrayStartIndex, arrayEndIndex)
                                .stream()
                                .mapToDouble(Double::doubleValue)
                                .toArray();
                mVarianceOfElevation = getVariance(elevationArr);
                mIsVarianceCalculated = true;
            } else {
                mIsVarianceCalculated = false;
            }
        }

        private boolean isWithinCriterionAngle(double aoa) {
            return Math.abs(aoa) <= mDeviceConfigFacade.getAdvertiseAoaCriteriaAngle();
        }

        private boolean isWithinCriterionAngle() {
            Optional<Double> outsideCriterionAngle;

            // Check if any stored AoaAzimuth value is outside the criterion angle range.
            outsideCriterionAngle = mRecentAoaAzimuth.stream()
                    .filter(x -> !isWithinCriterionAngle(x))
                    .findFirst();
            if (outsideCriterionAngle.isPresent()) return false;

            // Check if any stored AoaElevation value is outside the criterion angle range.
            outsideCriterionAngle = mRecentAoaElevation.stream()
                    .filter(x -> !isWithinCriterionAngle(x))
                    .findFirst();
            return !outsideCriterionAngle.isPresent();
        }

        // TODO(b/246678053): Can we receive measurements that are out of order (in terms of the
        // timestamp) ? In that case we should store the largest timestamp, not the latest, as this
        // stored value is used later for validating the measurements are within the time threshold.
        private void updateLastMeasuredTime(long time) {
            mLastMeasuredTime = time;
        }

        private double getVarianceOfAzimuth() {
            return mVarianceOfAzimuth;
        }

        private double getVarianceOfElevation() {
            return mVarianceOfElevation;
        }

        private long getLastUpdatedTime() {
            return mLastMeasuredTime;
        }

        @VisibleForTesting
        public boolean isVarianceCalculated() {
            return mIsVarianceCalculated;
        }

        private double getAverage(double[] array) {
            double sum = 0.0;

            for (int i = 0; i < array.length; i++) sum += array[i];

            return sum / array.length;
        }

        private double getVariance(double[] array) {
            if (array.length < 2) return Double.NaN;

            double sum = 0.0;
            double ret;
            double avg = getAverage(array);

            for (int i = 0; i < array.length; i++) {
                sum += Math.pow(array[i] - avg, 2);
            }
            ret = (double) sum / array.length;

            return ret;
        }

        @Override
        public String toString() {
            return " mMacAddress : "
                    + mMacAddress
                    + ", mVarOfAzimuth : "
                    + mVarianceOfAzimuth
                    + ", mVarOfElevation : "
                    + mVarianceOfElevation
                    + ", mRecentAoaAzimuth : "
                    + mRecentAoaAzimuth
                    + ", mRecentAoaElevation : "
                    + mRecentAoaElevation;
        }
    }
}
