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

import java.util.Arrays;
import java.util.Objects;

/**
 * Stores the response of the UCI CORE_GET_DEVICE_INFO CMD.
 */
public class UwbDeviceInfoResponse {
    public int mStatusCode;
    public int mUciVersion;
    public int mMacVersion;
    public int mPhyVersion;
    public int mUciTestVersion;
    public byte[] mVendorSpecInfo;

    public UwbDeviceInfoResponse(
            int statusCode,
            int uciVersion,
            int macVersion,
            int phyVersion,
            int uciTestVersion,
            byte[] vendorSpecInfo) {
        this.mStatusCode = statusCode;
        this.mUciVersion = uciVersion;
        this.mMacVersion = macVersion;
        this.mPhyVersion = phyVersion;
        this.mUciTestVersion = uciTestVersion;
        this.mVendorSpecInfo = vendorSpecInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UwbDeviceInfoResponse)) return false;
        UwbDeviceInfoResponse that = (UwbDeviceInfoResponse) o;
        return mStatusCode == that.mStatusCode
                && mUciVersion == that.mUciVersion
                && mMacVersion == that.mMacVersion
                && mPhyVersion == that.mPhyVersion
                && mUciTestVersion == that.mUciTestVersion
                && Arrays.equals(mVendorSpecInfo, that.mVendorSpecInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStatusCode,
                mUciVersion, mMacVersion, mPhyVersion, mUciTestVersion,
                Arrays.hashCode(mVendorSpecInfo));
    }

    @Override
    public String toString() {
        return "UwbDeviceInfoResponse{"
                + "statusCode=" + mStatusCode
                + ", uciVersion=" + mUciVersion
                + ", macVersion=" + mMacVersion
                + ", phyVersion=" + mPhyVersion
                + ", uciTestVersion=" + mUciTestVersion
                + ", vendorSpecInfo=" + Arrays.toString(mVendorSpecInfo)
                + '}';
    }
}
