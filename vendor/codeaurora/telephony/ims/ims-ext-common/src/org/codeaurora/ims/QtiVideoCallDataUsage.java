/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License_Identifier: BSD-3-Clause-Clear
 */

package org.codeaurora.ims;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemProperties;

/**
 * Parcelable object to handle Video Call DataUsage information
 * @hide
 */

public class QtiVideoCallDataUsage implements Parcelable {

    private long[] mDataUsage;
    public static final int DATA_USAGE_WWAN = 0;
    public static final int DATA_USAGE_WLAN = 1;
    public static final int DATA_USAGE_C_IWLAN = 2;
    public static final int DATA_USAGE_INVALID_VALUE = -1;
    private static final String[] TEXT =
            {"WwanDataUsage = "," WlanDataUsage = ", " CIWlanDataUsage = "};

    // Motorola specific
    public static final int DATA_USAGE_LTE_TX = 0;
    public static final int DATA_USAGE_LTE_RX = 1;
    public static final int DATA_USAGE_WLAN_TX = 2;
    public static final int DATA_USAGE_WLAN_RX = 3;
    private static final String[] TEXT_MOTO = {
        "LteTxDataUsage = ", " LteRxDataUsage = ", " WlanTxDataUsage = ", " WlanRxDataUsage = "
    };
    private static final String MOTO_VT_PROP = "ro.qcom.ims.use_moto_vt_ext";
    private final boolean mUseMotoExt;

    public QtiVideoCallDataUsage(long[] dUsage) {
        if (dUsage == null || dUsage.length == 0 ) {
            throw new RuntimeException();
        }
        mDataUsage = dUsage;
        mUseMotoExt = SystemProperties.getBoolean(MOTO_VT_PROP, false);
    }

    public QtiVideoCallDataUsage(Parcel in) {
        readFromParcel(in);
        mUseMotoExt = SystemProperties.getBoolean(MOTO_VT_PROP, false);
    }

    /*
     * These methods return LTE_{TX/RX} Data Usage (Motorola specific)
     */
    public long getLteTxDataUsage() {
        return (mUseMotoExt && mDataUsage.length > DATA_USAGE_LTE_TX)
                ? mDataUsage[DATA_USAGE_LTE_TX]
                : DATA_USAGE_INVALID_VALUE;
    }

    public long getLteRxDataUsage() {
        return (mUseMotoExt && mDataUsage.length > DATA_USAGE_LTE_RX)
                ? mDataUsage[DATA_USAGE_LTE_RX]
                : DATA_USAGE_INVALID_VALUE;
    }

    /*
     * This method returns LTE Data Usage (for backward compatibility)
     */
    public long getLteDataUsage() {
        return getWwanDataUsage();
    }

    /*
     * This method returns WWAN Data Usage
     */
    public long getWwanDataUsage() {
        if (mUseMotoExt) {
            return mDataUsage.length > DATA_USAGE_LTE_RX
                    ? mDataUsage[DATA_USAGE_LTE_TX] + mDataUsage[DATA_USAGE_LTE_RX]
                    : DATA_USAGE_INVALID_VALUE;
        } else {
            return mDataUsage.length > DATA_USAGE_WWAN
                    ? mDataUsage[DATA_USAGE_WWAN]
                    : DATA_USAGE_INVALID_VALUE;
        }
    }

    /*
     * This method returns WLAN Data Usage
     */
    public long getWlanDataUsage() {
        if (mUseMotoExt) {
            return mDataUsage.length > DATA_USAGE_WLAN_RX
                    ? mDataUsage[DATA_USAGE_WLAN_TX] + mDataUsage[DATA_USAGE_WLAN_RX]
                    : DATA_USAGE_INVALID_VALUE;
        } else {
            return mDataUsage.length > DATA_USAGE_WLAN
                    ? mDataUsage[DATA_USAGE_WLAN]
                    : DATA_USAGE_INVALID_VALUE;
        }
    }

    public long getWlanTxDataUsage() {
        return (mUseMotoExt && mDataUsage.length > DATA_USAGE_WLAN_TX)
                ? mDataUsage[DATA_USAGE_WLAN_TX]
                : DATA_USAGE_INVALID_VALUE;
    }

    public long getWlanRxDataUsage() {
        return (mUseMotoExt && mDataUsage.length > DATA_USAGE_WLAN_RX)
                ? mDataUsage[DATA_USAGE_WLAN_RX]
                : DATA_USAGE_INVALID_VALUE;
    }

    /*
     * This method returns C_IWLAN Data Usage
     */
    public long getCiwlanDataUsage() {
        return mDataUsage.length > DATA_USAGE_C_IWLAN ? mDataUsage[DATA_USAGE_C_IWLAN] :
                DATA_USAGE_INVALID_VALUE;
    }

    @Override
    public void writeToParcel(Parcel dest, int flag) {
        dest.writeLongArray(mDataUsage);
    }

    public void readFromParcel(Parcel in) {
        mDataUsage = in.createLongArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<QtiVideoCallDataUsage> CREATOR =
            new Creator<QtiVideoCallDataUsage>() {
        @Override
        public QtiVideoCallDataUsage createFromParcel(Parcel in) {
            return new QtiVideoCallDataUsage(in);
        }

        @Override
        public QtiVideoCallDataUsage[] newArray(int size) {
            return new QtiVideoCallDataUsage[size];
        }
    };

    @Override
    public String toString() {
        if(mDataUsage != null) {
            String msg = "";
            String[] text = mUseMotoExt ? TEXT_MOTO : TEXT;
            for (int i = 0; i < mDataUsage.length; i++) {
                msg += text[i] + mDataUsage[i];
            }
            return msg;
        }
        return null;
    }
}
