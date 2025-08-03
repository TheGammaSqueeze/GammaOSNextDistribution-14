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

/* UwbTestRxPacketErrorRateResult is unused now*/
/*package com.android.server.uwb.test;

import com.android.server.uwb.util.UwbUtil;

public class UwbTestRxPacketErrorRateResult {
    public int mStatus;
    public long mAttempts;
    public long mAcqDetect;
    public long mAcqReject;
    public long mRxFail;
    public long mSyncCirReady;
    public long mSfdFail;
    public long mSfdFound;
    public long mPhrDecError;
    public long mPhrBitError;
    public long mPsduDecError;
    public long mPsduBitError;
    public long mStsFound;
    public long mEof;
    *//* Vendor Specific Data *//*
    public byte[] mVendorExtnData;

    public UwbTestRxPacketErrorRateResult(int status, long attempts, long acqDetect,
            long acqReject, long rxFail, long syncCirReady, long sfdFail, long sfdFound,
            long phrDecError, long phrBitError, long psduDecError, long psduBitError, long stsFound,
            long eof, byte[] vendorExtnData) {
        this.mStatus = status;
        this.mAttempts = attempts;
        this.mAcqDetect = acqDetect;
        this.mAcqReject = acqReject;
        this.mRxFail = rxFail;
        this.mSyncCirReady = syncCirReady;
        this.mSfdFail = sfdFail;
        this.mSfdFound = sfdFound;
        this.mPhrDecError = phrDecError;
        this.mPhrBitError = phrBitError;
        this.mPsduDecError = psduDecError;
        this.mPsduBitError = psduBitError;
        this.mStsFound = stsFound;
        this.mEof = eof;

        *//* Vendor Specific Data *//*
        this.mVendorExtnData = vendorExtnData;
    }

    public int getStatus() {
        return mStatus;
    }

    public long getAttempts() {
        return mAttempts;
    }

    public long getAcqDetect() {
        return mAcqDetect;
    }

    public long getAcqReject() {
        return mAcqReject;
    }

    public long getRxFail() {
        return mRxFail;
    }

    public long getSyncCirReady() {
        return mSyncCirReady;
    }

    public long getSfdFail() {
        return mSfdFail;
    }

    public long getSfdFound() {
        return mSfdFound;
    }

    public long getPhrDecError() {
        return mPhrDecError;
    }

    public long getPhrBitError() {
        return mPhrBitError;
    }

    public long getPsduDecError() {
        return mPsduDecError;
    }

    public long getPsduBitError() {
        return mPsduBitError;
    }

    public long getStsFound() {
        return mStsFound;
    }

    public long getEof() {
        return mEof;
    }

    *//* Vendor Specific Data *//*

    public byte[] getVendorExtnData() {
        return mVendorExtnData;
    }


    @Override
    public String toString() {
        return " UwbTestRxPacketErrorRateResult { "
                + " Status = " + mStatus
                + ", Attempts = " + mAttempts
                + ", AcqDetect = " + mAcqDetect
                + ", AcqReject = " + mAcqReject
                + ", RxFail = " + mRxFail
                + ", SyncCirReady = " + mSyncCirReady
                + ", SfdFail = " + mSfdFail
                + ", SfdFound = " + mSfdFound
                + ", PhrDecError = " + mPhrDecError
                + ", PhrBitError = " + mPhrBitError
                + ", PsduDecError = " + mPsduDecError
                + ", PsduBitError = " + mPsduBitError
                + ", StsFound = " + mStsFound
                + ", Eof = " + mEof
                + ", VendorExtnData = " + UwbUtil.toHexString(mVendorExtnData)
                + '}';
    }

}*/
