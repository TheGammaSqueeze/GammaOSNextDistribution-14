/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.hotspot2;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class carries the payload of a Hotspot 2.0 Wireless Network Management (WNM) frame,
 * described in the Hotspot 2.0 spec, section 3.2.
 */
public class WnmData {
    public static final int ESS = 1;   // HS2.0 spec section 3.2.1.2, table 4
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            HS20_REMEDIATION_EVENT,
            HS20_DEAUTH_IMMINENT_EVENT,
            HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT})
    public @interface WmnEventType {}
    public static final int HS20_REMEDIATION_EVENT = 0;
    public static final int HS20_DEAUTH_IMMINENT_EVENT = 1;
    public static final int HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT = 2;
    public static final int UNDEFINED = -1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            OMA_DM,
            SOAP_XML_SPP})
    public @interface OsuMethod {}
    public static final int OMA_DM = 0;
    public static final int SOAP_XML_SPP = 1;

    private final long mBssid;
    private final String mUrl;
    private final @WmnEventType int mEventType;
    private final int mMethod;
    private final boolean mEss;
    private final int mDelay;

    private WnmData(@WmnEventType int eventType, long bssid, String url, @OsuMethod int method,
            boolean ess, int delay) {
        mBssid = bssid;
        mUrl = url;
        mMethod = method;
        mEss = ess;
        mDelay = delay;
        mEventType = eventType;
    }

    /**
     * Create a Passpoint Remediation WNM-Notification
     *
     * @param bssid BSSID of the source AP
     * @param url URL of the remediation server
     * @param method OSU method. Refer to section 4.8.1.3 of the Passpoint spec
     *
     * @return a WnmData object
     */
    public static WnmData createRemediationEvent(long bssid, String url, @OsuMethod int method) {
        return new WnmData(HS20_REMEDIATION_EVENT, bssid, url, method, false, UNDEFINED);
    }

    /**
     * Create a Passpoint Deauth-Imminent WNM-Notification
     *
     * @param bssid BSSID of the source AP
     * @param url URL of the remediation server
     * @param ess A flag to indicate if the event applies to the ESS or only to the BSS
     *
     * @return a WnmData object
     */
    public static WnmData createDeauthImminentEvent(long bssid, String url, boolean ess,
            int delay) {
        return new WnmData(HS20_DEAUTH_IMMINENT_EVENT, bssid, url, UNDEFINED, ess, delay);
    }

    /**
     * Create a Passpoint Terms & Conditions acceptance required WNM-Notification
     *
     * @param bssid BSSID of the source AP
     * @param url URL of the remediation server
     *
     * @return a WnmData object
     */
    public static WnmData createTermsAndConditionsAccetanceRequiredEvent(long bssid, String url) {
        return new WnmData(HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT, bssid, url,
                UNDEFINED, false, UNDEFINED);
    }

    /**
     * Get the BSSID of the source AP
     *
     * @return The BSSID of the source AP
     */
    public long getBssid() {
        return mBssid;
    }

    /**
     * Get the URL associated to the WNM-Notification
     *
     * @return The URL associated to the WNM-Notification
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * Get event type
     *
     * @return The WNM-Notification event type
     */
    @WmnEventType
    public int getEventType() {
        return mEventType;
    }

    /**
     * Get the OSU method associated to the Remediation WNM-Notification
     *
     * @return The OSU method supported by the server
     */
    @OsuMethod
    public int getMethod() {
        return mMethod;
    }

    /**
     * Get the ESS flag associated to the Deauth-Imminent WNM-Notification
     *
     * @return true if notification is for the entire ESS, false for the BSS only
     */
    public boolean isEss() {
        return mEss;
    }

    /**
     * Get the delay in seconds associated to the Deauth-Imminent WNM-Notification
     *
     * @return The delay in seconds that a mobile device shall wait before attempting reassociation
     */
    public int getDelay() {
        return mDelay;
    }
}
