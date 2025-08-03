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
package com.android.server.uwb.data;

import android.uwb.RangingMeasurement;

import com.android.server.uwb.util.UwbUtil;

public class UwbTwoWayMeasurement {
    public byte[] mMacAddress;
    public int mStatus;
    public int mNLoS;
    public int mDistance;
    public float mAoaAzimuth;
    public int mAoaAzimuthFom;
    public float mAoaElevation;
    public int mAoaElevationFom;
    public float mAoaDestAzimuth;
    public int mAoaDestAzimuthFom;
    public float mAoaDestElevation;
    public int mAoaDestElevationFom;
    public int mSlotIndex;
    public int mRssi;

    public UwbTwoWayMeasurement(byte[] macAddress, int status, int nLoS, int distance,
            int aoaAzimuth, int aoaAzimuthFom, int aoaElevation,
            int aoaElevationFom, int aoaDestAzimuth, int aoaDestAzimuthFom,
            int aoaDestElevation, int aoaDestElevationFom, int slotIndex, int rssiHalfDbmAbs) {

        this.mMacAddress = macAddress;
        this.mStatus = status;
        this.mNLoS = nLoS;
        // Set distance to negative value if the status code indicates negative distance
        this.mDistance = status == UwbUciConstants.STATUS_CODE_OK_NEGATIVE_DISTANCE_REPORT
                ? -distance : distance;
        this.mAoaAzimuth = toFloatFromQFormat(aoaAzimuth);
        this.mAoaAzimuthFom = aoaAzimuthFom;
        this.mAoaElevation = toFloatFromQFormat(aoaElevation);
        this.mAoaElevationFom = aoaElevationFom;
        this.mAoaDestAzimuth = toFloatFromQFormat(aoaDestAzimuth);
        this.mAoaDestAzimuthFom = aoaDestAzimuthFom;
        this.mAoaDestElevation = toFloatFromQFormat(aoaDestElevation);
        this.mAoaDestElevationFom = aoaDestElevationFom;
        this.mSlotIndex = slotIndex;
        /*
         * According to FiRa UCI Generic Technical Specification v2.0.0,
         * decode the rssi value in dBm format where the abs value was encoded in FP Q7.1 format.
         * Just need to divide this number by two and take the negative value.
         * If the reported RSSI is lower than RSSI_MIN, set it to RSSI_MIN to avoid exceptions.
         */
        this.mRssi = Math.max(-rssiHalfDbmAbs / 2, RangingMeasurement.RSSI_MIN);
    }

    public byte[] getMacAddress() {
        return mMacAddress;
    }

    public int getRangingStatus() {
        return mStatus;
    }

    public int getNLoS() {
        return mNLoS;
    }

    public int getDistance() {
        return mDistance;
    }

    public float getAoaAzimuth() {
        return mAoaAzimuth;
    }

    public int getAoaAzimuthFom() {
        return mAoaAzimuthFom;
    }

    public float getAoaElevation() {
        return mAoaElevation;
    }

    public int getAoaElevationFom() {
        return mAoaElevationFom;
    }

    public float getAoaDestAzimuth() {
        return mAoaDestAzimuth;
    }

    public int getAoaDestAzimuthFom() {
        return mAoaDestAzimuthFom;
    }

    public float getAoaDestElevation() {
        return mAoaDestElevation;
    }

    public int getAoaDestElevationFom() {
        return mAoaDestElevationFom;
    }

    public int getSlotIndex() {
        return mSlotIndex;
    }
    public int getRssi() {
        return mRssi;
    }

    public boolean isStatusCodeOk() {
        return mStatus == UwbUciConstants.STATUS_CODE_OK
                || mStatus == UwbUciConstants.STATUS_CODE_OK_NEGATIVE_DISTANCE_REPORT;
    }

    /**
     * Convert the UCI status code to success, out of range, or unknown error
     */
    public int convertStatusCode() {
        switch (mStatus) {
            case UwbUciConstants.STATUS_CODE_OK:
            case UwbUciConstants.STATUS_CODE_OK_NEGATIVE_DISTANCE_REPORT:
                return RangingMeasurement.RANGING_STATUS_SUCCESS;
            case UwbUciConstants.STATUS_CODE_INVALID_RANGE:
                return RangingMeasurement.RANGING_STATUS_FAILURE_OUT_OF_RANGE;
            default:
                return RangingMeasurement.RANGING_STATUS_FAILURE_UNKNOWN_ERROR;
        }
    }

    private float toFloatFromQFormat(int value) {
        return UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(value, 16),
                9, 7);
    }

    public String toString() {
        return "UwbTwoWayMeasurement { "
                + " MacAddress = " + UwbUtil.toHexString(mMacAddress)
                + ", RangingStatus = " + mStatus
                + ", NLoS = " + mNLoS
                + ", Distance = " + mDistance
                + ", AoaAzimuth = " + mAoaAzimuth
                + ", AoaAzimuthFom = " + mAoaAzimuthFom
                + ", AoaElevation = " + mAoaElevation
                + ", AoaElevationFom = " + mAoaElevationFom
                + ", AoaDestAzimuth = " + mAoaDestAzimuth
                + ", AoaDestAzimuthFom = " + mAoaDestAzimuthFom
                + ", AoaDestElevation = " + mAoaDestElevation
                + ", AoaDestElevationFom = " + mAoaDestElevationFom
                + ", SlotIndex = 0x" + UwbUtil.toHexString(mSlotIndex)
                + ", RSSI = " + mRssi
                + '}';
    }
}
