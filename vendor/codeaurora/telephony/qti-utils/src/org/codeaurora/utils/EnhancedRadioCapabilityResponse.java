/*
 * Copyright (c) 2021 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 */

/*
 * Copyright (C) 2008 The Android Open Source Project
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

package org.codeaurora.telephony.utils;

import android.util.Log;

public class EnhancedRadioCapabilityResponse {

    private static String LOG_TAG = "EnhancedRadioCapabilityResponse";
    private String mDeviceCapability;

    public static final int NETWORK_TYPE_UNKNOWN = 0;
    public static final int NETWORK_TYPE_GPRS = 1;
    public static final int NETWORK_TYPE_EDGE = 2;
    public static final int NETWORK_TYPE_UMTS = 3;
    public static final int NETWORK_TYPE_CDMA = 4;
    public static final int NETWORK_TYPE_EVDO_0 = 5;
    public static final int NETWORK_TYPE_EVDO_A = 6;
    public static final int NETWORK_TYPE_1xRTT = 7;
    public static final int NETWORK_TYPE_HSDPA = 8;
    public static final int NETWORK_TYPE_HSUPA = 9;
    public static final int NETWORK_TYPE_HSPA = 10;
    public static final int NETWORK_TYPE_IDEN = 11;
    public static final int NETWORK_TYPE_EVDO_B = 12;
    public static final int NETWORK_TYPE_LTE = 13;
    public static final int NETWORK_TYPE_EHRPD = 14;
    public static final int NETWORK_TYPE_HSPAP = 15;
    public static final int NETWORK_TYPE_GSM = 16;
    public static final int NETWORK_TYPE_TD_SCDMA = 17;
    public static final int NETWORK_TYPE_IWLAN = 18;
    public static final int NETWORK_TYPE_LTE_CA = 19;
    public static final int NETWORK_TYPE_NR_NSA = 20;
    public static final int NETWORK_TYPE_NR_SA = 21;

    /**
     * network type bitmask unknown.
     */
    public static final long NETWORK_TYPE_BITMASK_UNKNOWN = 0L;
    /**
     * network type bitmask indicating the support of radio tech GSM.
     */
    public static final long NETWORK_TYPE_BITMASK_GSM = (1 << (NETWORK_TYPE_GSM -1));
    /**
     * network type bitmask indicating the support of radio tech GPRS.
     */
    public static final long NETWORK_TYPE_BITMASK_GPRS = (1 << (NETWORK_TYPE_GPRS -1));
    /**
     * network type bitmask indicating the support of radio tech EDGE.
     */
    public static final long NETWORK_TYPE_BITMASK_EDGE = (1 << (NETWORK_TYPE_EDGE -1));
    /**
     * network type bitmask indicating the support of radio tech CDMA(IS95A/IS95B).
     */
    public static final long NETWORK_TYPE_BITMASK_CDMA = (1 << (NETWORK_TYPE_CDMA -1));
    /**
     * network type bitmask indicating the support of radio tech 1xRTT.
     */
    public static final long NETWORK_TYPE_BITMASK_1xRTT = (1 << (NETWORK_TYPE_1xRTT - 1));
    /**
     * network type bitmask indicating the support of radio tech EVDO 0.
     */
    public static final long NETWORK_TYPE_BITMASK_EVDO_0 = (1 << (NETWORK_TYPE_EVDO_0 -1));
    /**
     * network type bitmask indicating the support of radio tech EVDO A.
     */
    public static final long NETWORK_TYPE_BITMASK_EVDO_A = (1 << (NETWORK_TYPE_EVDO_A - 1));
    /**
     * network type bitmask indicating the support of radio tech EVDO B.
     */
    public static final long NETWORK_TYPE_BITMASK_EVDO_B = (1 << (NETWORK_TYPE_EVDO_B -1));
    /**
     * network type bitmask indicating the support of radio tech EHRPD.
     */
    public static final long NETWORK_TYPE_BITMASK_EHRPD = (1 << (NETWORK_TYPE_EHRPD -1));
    /**
     * network type bitmask indicating the support of radio tech HSUPA.
     */
    public static final long NETWORK_TYPE_BITMASK_HSUPA = (1 << (NETWORK_TYPE_HSUPA -1));
    /**
     * network type bitmask indicating the support of radio tech HSDPA.
     */
    public static final long NETWORK_TYPE_BITMASK_HSDPA = (1 << (NETWORK_TYPE_HSDPA -1));
    /**
     * network type bitmask indicating the support of radio tech HSPA.
     */
    public static final long NETWORK_TYPE_BITMASK_HSPA = (1 << (NETWORK_TYPE_HSPA -1));
    /**
     * network type bitmask indicating the support of radio tech HSPAP.
     */
    public static final long NETWORK_TYPE_BITMASK_HSPAP = (1 << (NETWORK_TYPE_HSPAP -1));
    /**
     * network type bitmask indicating the support of radio tech UMTS.
     */
    public static final long NETWORK_TYPE_BITMASK_UMTS = (1 << (NETWORK_TYPE_UMTS -1));
    /**
     * network type bitmask indicating the support of radio tech TD_SCDMA.
     */
    public static final long NETWORK_TYPE_BITMASK_TD_SCDMA = (1 << (NETWORK_TYPE_TD_SCDMA -1));
    /**
     * network type bitmask indicating the support of radio tech LTE.
     */
    public static final long NETWORK_TYPE_BITMASK_LTE = (1 << (NETWORK_TYPE_LTE -1));
    /**
     * network type bitmask indicating the support of radio tech LTE CA (carrier aggregation).
     */
    public static final long NETWORK_TYPE_BITMASK_LTE_CA = (1 << (NETWORK_TYPE_LTE_CA -1));

    /**
     * network type bitmask indicating the support of radio tech NR(New Radio) 5G NSA.
     */
    public static final long NETWORK_TYPE_BITMASK_NR_NSA = (1 << (NETWORK_TYPE_NR_NSA -1));

    /**
     * network type bitmask indicating the support of radio tech NR(New Radio) 5G SA.
     */
    public static final long NETWORK_TYPE_BITMASK_NR_SA = (1 << (NETWORK_TYPE_NR_SA -1));


    public void updateEnhancedRadioCapability(int raf) {
        Log.d(LOG_TAG, "updateEnhancedRadioCapability: raf:"+ raf);
        if(raf == NETWORK_TYPE_BITMASK_UNKNOWN) {
            return;
        }
        if((raf & NETWORK_TYPE_BITMASK_NR_SA) != 0){
            mDeviceCapability = "SA";
        } else if((raf & NETWORK_TYPE_BITMASK_NR_NSA) != 0) {
            mDeviceCapability = "NSA";
        } else if ((raf & NETWORK_TYPE_BITMASK_LTE) != 0) {
            mDeviceCapability = "LTE";
        } else if((raf & NETWORK_TYPE_BITMASK_TD_SCDMA) != 0) {
            mDeviceCapability = "TD_SCDMA";
        } else if((raf & NETWORK_TYPE_BITMASK_UMTS) != 0){
            mDeviceCapability = "UMTS";
        } else if((raf & NETWORK_TYPE_BITMASK_GSM) != 0){
            mDeviceCapability = "GSM";
        } else if((raf & NETWORK_TYPE_BITMASK_EVDO_A) != 0){
            mDeviceCapability = "HDR";
        } else if((raf & NETWORK_TYPE_BITMASK_1xRTT) != 0){
            mDeviceCapability = "1xRTT";
        }
        Log.d(LOG_TAG, "updateEnhancedRadioCapability:"+ mDeviceCapability);
    }

    public String getEnhancedRadioCapability() {
        Log.d(LOG_TAG, "getEnhancedRadioCapability: "+ mDeviceCapability);
        return mDeviceCapability;
    }
}
