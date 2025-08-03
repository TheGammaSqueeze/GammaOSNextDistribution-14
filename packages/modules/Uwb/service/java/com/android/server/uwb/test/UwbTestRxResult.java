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

/* UwbTestRxResult is unused now*/
/*package com.android.server.uwb.test;

import com.android.server.uwb.util.UwbUtil;

public class UwbTestRxResult {
    public int mStatus;
    public long mRxDoneTsInt;
    public int mRxDoneTsFrac;
    public float mAoaAzimuth;
    public float mAoaElevation;
    public int mToaGap;
    public int mPhr;
    public byte[] mPsduData;
    public byte[] mVendorExtnData;

    public UwbTestRxResult(int status, long rxDoneTsInt, int rxDoneTsFrac,
            int aoaAzimuth, int aoaElevation, int toaGap, int phr, byte[] psduData,
            byte[] vendorExtnData) {

        this.mStatus = status;
        this.mRxDoneTsInt = rxDoneTsInt;
        this.mRxDoneTsFrac = rxDoneTsFrac;
        this.mAoaAzimuth =
                UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(aoaAzimuth, 16), 9, 7);
        this.mAoaElevation =
                UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(aoaElevation, 16), 9, 7);
        this.mToaGap = toaGap;
        this.mPhr = phr;
        this.mPsduData = psduData;

        *//* Vendor Specific Data *//*
        this.mVendorExtnData = vendorExtnData;

    }

    public int getStatus() {
        return mStatus;
    }

    public long getRxDoneTsInt() {
        return mRxDoneTsInt;
    }

    public int getRxDoneTsFrac() {
        return mRxDoneTsFrac;
    }

    public float getAoaAzimuth() {
        return mAoaAzimuth;
    }

    public float getAoaElevation() {
        return mAoaElevation;
    }

    public int getToaGap() {
        return mToaGap;
    }

    public int getPhr() {
        return mPhr;
    }

    public byte[] getPsduData() {
        return mPsduData;
    }

    *//* Vendor Specific Data *//*

    public byte[] getVendorExtnData() {
        return mVendorExtnData;
    }

    @Override
    public String toString() {
        return " UwbTestRxResult { "
                + " Status = " + mStatus
                + ", RxDoneTsInt = " + mRxDoneTsInt
                + ", RxDoneTsFrac = " + mRxDoneTsFrac
                + ", AoaAzimuth = " + mAoaAzimuth
                + ", AoaElevation = " + mAoaElevation
                + ", ToaGap = " + mToaGap
                + ", Phr = " + mPhr
                + ", PsduData = " + UwbUtil.toHexString(mPsduData)
                + ", VendorExtnData = " + UwbUtil.toHexString(mVendorExtnData)
                + '}';
    }
}*/
