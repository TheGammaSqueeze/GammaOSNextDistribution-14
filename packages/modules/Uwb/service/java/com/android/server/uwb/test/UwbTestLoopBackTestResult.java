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

/* UwbTestLoopBackTestResult is unused now*/
/*package com.android.server.uwb.test;

import com.android.server.uwb.util.UwbUtil;

public class UwbTestLoopBackTestResult {
    public int mStatus;
    public  long mTxtsInt;
    public  int mTxtsFrac;
    public  long mRxtsInt;
    public  int mRxtsFrac;
    public  float mAoaAzimuth;
    public  float mAoaElevation;
    public  int mPhr;
    public  byte[] mPsduData;

    *//* Vendor Specific Data *//*
    public byte[] mVendorExtnData;

    public UwbTestLoopBackTestResult(int status, long txtsInt, int txtsFrac, long rxtsInt,
            int rxtsFrac, int aoaAzimuth, int aoaElevation,  int phr, byte[] psduData,
            byte[] vendorExtnData) {
        *//* Vendor Specific data  *//*
        this.mStatus = status;
        this.mTxtsInt = txtsInt;
        this.mTxtsFrac = txtsFrac;
        this.mRxtsInt = rxtsInt;
        this.mRxtsFrac = rxtsFrac;
        this.mAoaAzimuth =
                UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(aoaAzimuth, 16), 9, 7);
        this.mAoaElevation =
                UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(aoaElevation, 16), 9, 7);
        this.mPhr = phr;
        this.mPsduData = psduData;

        *//* Vendor Specific Data *//*
        this.mVendorExtnData = vendorExtnData;

    }

    public int getStatus() {
        return mStatus;
    }


    public long getTxTsInt() {
        return mTxtsInt;
    }

    public int getTxTsFrac() {
        return mTxtsFrac;
    }

    public long getRxTsInt() {
        return mRxtsInt;
    }

    public int getRxTsFrac() {
        return mRxtsFrac;
    }

    public float getAoaAzimuth() {
        return mAoaAzimuth;
    }

    public float getAoaElevation() {
        return mAoaElevation;
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
        return " UwbTestLoopBackTestResult { "
                + " Status = " + mStatus
                + ", TxtsInt = " + mTxtsInt
                + ", TxtsFrac = " + mTxtsFrac
                + ", RxtsInt = " + mRxtsInt
                + ", RxtsFrac = " + mRxtsFrac
                + ", AoaAzimuth = " + mAoaAzimuth
                + ", AoaElevation = " + mAoaElevation
                + ", Phr = " + mPhr
                + ", PsduData = " + UwbUtil.toHexString(mPsduData)
                + *//* Vendor Specific Data *//*
                ", VendorExtnData = " + UwbUtil.toHexString(mVendorExtnData)
                + '}';
    }
}*/
