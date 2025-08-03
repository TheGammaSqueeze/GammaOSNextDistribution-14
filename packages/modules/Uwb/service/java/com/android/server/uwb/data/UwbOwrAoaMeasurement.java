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
package com.android.server.uwb.data;

import com.android.server.uwb.util.UwbUtil;

public class UwbOwrAoaMeasurement {
    public byte[] mMacAddress;
    public int mStatus;
    public int mNLoS;
    public int mFrameSequenceNumber;
    public int blockIndex;
    public float mAoaAzimuth;
    public int mAoaAzimuthFom;
    public float mAoaElevation;
    public int mAoaElevationFom;

    public UwbOwrAoaMeasurement(byte[] macAddress, int status, int nLoS, int frameSeqNumber,
            int blockIndex, int aoaAzimuth, int aoaAzimuthFom,
            int aoaElevation, int aoaElevationFom) {
        this.mMacAddress = macAddress;
        this.mStatus = status;
        this.mNLoS = nLoS;
        this.mFrameSequenceNumber = frameSeqNumber;
        this.blockIndex = blockIndex;
        this.mAoaAzimuth = UwbUtil.convertQFormatToFloat(
                UwbUtil.twos_compliment(aoaAzimuth, 16), 9, 7);
        this.mAoaAzimuthFom = aoaAzimuthFom;
        this.mAoaElevation = UwbUtil.convertQFormatToFloat(
                UwbUtil.twos_compliment(aoaElevation, 16), 9, 7);
        this.mAoaElevationFom = aoaElevationFom;
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

    public int getFrameSequenceNumber() {
        return mFrameSequenceNumber;
    }

    public int getBlockIndex() {
        return blockIndex;
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

    public String toString() {
        return "UwbOwrAoaMeasurement { "
                + " MacAddress = " + UwbUtil.toHexString(mMacAddress)
                + ", Status = " + mStatus
                + ", NLoS = " + mNLoS
                + ", FrameSequenceNumber = " + mFrameSequenceNumber
                + ", BlockIndex = " + blockIndex
                + ", AoaAzimuth = " + mAoaAzimuth
                + ", AoaAzimuthFom = " + mAoaAzimuthFom
                + ", AoaElevation = " + mAoaElevation
                + ", AoaElevationFom = " + mAoaElevationFom
                + '}';
    }
}
