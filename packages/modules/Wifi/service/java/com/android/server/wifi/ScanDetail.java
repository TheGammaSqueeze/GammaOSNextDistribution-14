/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.wifi;

import android.net.wifi.AnqpInformationElement;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiSsid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.hotspot2.anqp.HSFriendlyNameElement;
import com.android.server.wifi.hotspot2.anqp.RawByteElement;
import com.android.server.wifi.hotspot2.anqp.VenueNameElement;

import java.util.List;
import java.util.Map;

/**
 * Wifi scan result details.
 */
public class ScanDetail {
    private final ScanResult mScanResult;
    private volatile NetworkDetail mNetworkDetail;
    private long mSeen = 0;
    private byte[] mInformationElementRawData;
    private static final ScanResult.Builder sBuilder = new ScanResult.Builder();

    /**
     * Main constructor used when converting from NativeScanResult
     */
    public ScanDetail(@Nullable NetworkDetail networkDetail, @Nullable WifiSsid wifiSsid,
            @Nullable String bssid, @Nullable String caps, int level, int frequency, long tsf,
            @Nullable ScanResult.InformationElement[] informationElements,
            @Nullable List<String> anqpLines, @Nullable byte[] informationElementRawData) {
        mNetworkDetail = networkDetail;
        long hessid = 0L;
        int anqpDomainId = ScanResult.UNSPECIFIED;
        byte[] osuProviders = null;
        int channelWidth = ScanResult.UNSPECIFIED;
        int centerFreq0 = ScanResult.UNSPECIFIED;
        int centerFreq1 = ScanResult.UNSPECIFIED;
        boolean isPasspoint = false;
        boolean is80211McResponder = false;
        boolean isTwtResponder = false;
        if (networkDetail != null) {
            hessid = networkDetail.getHESSID();
            anqpDomainId = networkDetail.getAnqpDomainID();
            osuProviders = networkDetail.getOsuProviders();
            channelWidth = networkDetail.getChannelWidth();
            centerFreq0 = networkDetail.getCenterfreq0();
            centerFreq1 = networkDetail.getCenterfreq1();
            isPasspoint =
                    caps.contains("EAP")
                            && !caps.contains("SUITE_B_192")
                            && networkDetail.isInterworking()
                            && networkDetail.getHSRelease() != null;
            is80211McResponder = networkDetail.is80211McResponderSupport();
            isTwtResponder = networkDetail.isIndividualTwtSupported();
        }
        sBuilder.clear();
        mScanResult = sBuilder
                .setWifiSsid(wifiSsid)
                .setBssid(bssid)
                .setHessid(hessid)
                .setAnqpDomainId(anqpDomainId)
                .setOsuProviders(osuProviders)
                .setCaps(caps)
                .setRssi(level)
                .setFrequency(frequency)
                .setTsf(tsf)
                .setIsTwtResponder(isTwtResponder)
                .build();
        mSeen = System.currentTimeMillis();
        mScanResult.seen = mSeen;
        mScanResult.channelWidth = channelWidth;
        mScanResult.centerFreq0 = centerFreq0;
        mScanResult.centerFreq1 = centerFreq1;
        mScanResult.informationElements = informationElements;
        mScanResult.anqpLines = anqpLines;
        if (is80211McResponder) {
            mScanResult.setFlag(ScanResult.FLAG_80211mc_RESPONDER);
        }
        if (isPasspoint) {
            mScanResult.setFlag(ScanResult.FLAG_PASSPOINT_NETWORK);
        }
        mInformationElementRawData = informationElementRawData;
    }

    /**
     * Creates a ScanDetail without NetworkDetail for unit testing
     */
    @VisibleForTesting
    public ScanDetail(@Nullable WifiSsid wifiSsid, @Nullable String bssid, String caps, int level,
            int frequency, long tsf, long seen) {
        this(null, wifiSsid, bssid, caps, level, frequency, tsf, null, null, null);
        mSeen = seen;
        mScanResult.seen = seen;
    }

    /**
     * Create a ScanDetail from a ScanResult
     */
    public ScanDetail(@NonNull ScanResult scanResult) {
        mScanResult = scanResult;
        mNetworkDetail = new NetworkDetail(
                scanResult.BSSID,
                scanResult.informationElements,
                scanResult.anqpLines,
                scanResult.frequency);
        // Only inherit |mScanResult.seen| if it was previously set. This ensures that |mSeen|
        // will always contain a valid timestamp.
        mSeen = (mScanResult.seen == 0) ? System.currentTimeMillis() : mScanResult.seen;
    }

    /**
     * Copy constructor
     */
    public ScanDetail(@NonNull ScanDetail scanDetail) {
        mScanResult = new ScanResult(scanDetail.mScanResult);
        mNetworkDetail = new NetworkDetail(scanDetail.mNetworkDetail);
        mSeen = scanDetail.mSeen;
        mInformationElementRawData = scanDetail.mInformationElementRawData;
    }

    /**
     * Store ANQ element information
     *
     * @param anqpElements Map<Constants.ANQPElementType, ANQPElement>
     */
    public void propagateANQPInfo(Map<Constants.ANQPElementType, ANQPElement> anqpElements) {
        if (anqpElements.isEmpty()) {
            return;
        }
        mNetworkDetail = mNetworkDetail.complete(anqpElements);
        HSFriendlyNameElement fne = (HSFriendlyNameElement) anqpElements.get(
                Constants.ANQPElementType.HSFriendlyName);
        // !!! Match with language
        if (fne != null && !fne.getNames().isEmpty()) {
            mScanResult.venueName = fne.getNames().get(0).getText();
        } else {
            VenueNameElement vne =
                    (((VenueNameElement) anqpElements.get(
                            Constants.ANQPElementType.ANQPVenueName)));
            if (vne != null && !vne.getNames().isEmpty()) {
                mScanResult.venueName = vne.getNames().get(0).getText();
            }
        }
        RawByteElement osuProviders = (RawByteElement) anqpElements
                .get(Constants.ANQPElementType.HSOSUProviders);
        if (osuProviders != null) {
            mScanResult.anqpElements = new AnqpInformationElement[1];
            mScanResult.anqpElements[0] =
                    new AnqpInformationElement(AnqpInformationElement.HOTSPOT20_VENDOR_ID,
                            AnqpInformationElement.HS_OSU_PROVIDERS, osuProviders.getPayload());
        }
    }

    public ScanResult getScanResult() {
        return mScanResult;
    }

    public NetworkDetail getNetworkDetail() {
        return mNetworkDetail;
    }

    public String getSSID() {
        return mNetworkDetail == null ? mScanResult.SSID : mNetworkDetail.getSSID();
    }

    public String getBSSIDString() {
        return  mNetworkDetail == null ? mScanResult.BSSID : mNetworkDetail.getBSSIDString();
    }

    /**
     *  Return the network detail key string.
     */
    public String toKeyString() {
        NetworkDetail networkDetail = mNetworkDetail;
        if (networkDetail != null) {
            return networkDetail.toKeyString();
        } else {
            return "'" + mScanResult.SSID + "':" + mScanResult.BSSID;
        }
    }

    /**
     * Return the time this network was last seen.
     */
    public long getSeen() {
        return mSeen;
    }

    /**
     * Update the time this network was last seen to the current system time.
     */
    public long setSeen() {
        mSeen = System.currentTimeMillis();
        mScanResult.seen = mSeen;
        return mSeen;
    }

    /**
     * Return the network information element raw data.
     */
    public byte[] getInformationElementRawData() {
        return mInformationElementRawData;
    }

    @Override
    public String toString() {
        return "'" + mScanResult.SSID + "'/" + mScanResult.BSSID;
    }
}
