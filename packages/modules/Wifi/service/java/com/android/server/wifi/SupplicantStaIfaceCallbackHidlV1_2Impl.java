/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback;
import android.hardware.wifi.supplicant.V1_2.DppAkm;
import android.hardware.wifi.supplicant.V1_2.DppFailureCode;
import android.hardware.wifi.supplicant.V1_2.DppProgressCode;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiSsid;
import android.os.Process;
import android.util.Log;

import com.android.server.wifi.util.NativeUtil;

import java.util.ArrayList;
import java.util.Arrays;

abstract class SupplicantStaIfaceCallbackHidlV1_2Impl extends
        android.hardware.wifi.supplicant.V1_2.ISupplicantStaIfaceCallback.Stub {
    private static final String TAG = SupplicantStaIfaceCallbackHidlV1_2Impl.class.getSimpleName();
    private final SupplicantStaIfaceHalHidlImpl mStaIfaceHal;
    private final String mIfaceName;
    private final Context mContext;
    private final @NonNull SsidTranslator mSsidTranslator;
    private final SupplicantStaIfaceHalHidlImpl.SupplicantStaIfaceHalCallbackV1_1 mCallbackV11;

    SupplicantStaIfaceCallbackHidlV1_2Impl(@NonNull SupplicantStaIfaceHalHidlImpl staIfaceHal,
            @NonNull String ifaceName,
            @NonNull Context context,
            @NonNull SsidTranslator ssidTranslator) {
        mStaIfaceHal = staIfaceHal;
        mIfaceName = ifaceName;
        mContext = context;
        mSsidTranslator = ssidTranslator;
        // Create an older callback for function delegation,
        // and it would cascadingly create older one.
        mCallbackV11 = mStaIfaceHal.new SupplicantStaIfaceHalCallbackV1_1(mIfaceName);
    }

    public SupplicantStaIfaceHalHidlImpl.SupplicantStaIfaceHalCallback getCallbackV10() {
        return mCallbackV11.getCallbackV10();
    }

    @Override
    public void onNetworkAdded(int id) {
        mCallbackV11.onNetworkAdded(id);
    }

    @Override
    public void onNetworkRemoved(int id) {
        mCallbackV11.onNetworkRemoved(id);
    }

    /**
     * Added to plumb the new {@code filsHlpSent} param from the V1.3 callback version.
     */
    public void onStateChanged(int newState, byte[/* 6 */] bssid, int id, ArrayList<Byte> ssid,
            boolean filsHlpSent) {
        mCallbackV11.onStateChanged(newState, bssid, id, ssid, filsHlpSent);
    }

    @Override
    public void onStateChanged(int newState, byte[/* 6 */] bssid, int id,
            ArrayList<Byte> ssid) {
        onStateChanged(newState, bssid, id, ssid, false);
    }

    @Override
    public void onAnqpQueryDone(byte[/* 6 */] bssid,
            ISupplicantStaIfaceCallback.AnqpData data,
            ISupplicantStaIfaceCallback.Hs20AnqpData hs20Data) {
        mCallbackV11.onAnqpQueryDone(bssid, data, hs20Data);
    }

    @Override
    public void onHs20IconQueryDone(byte[/* 6 */] bssid, String fileName,
            ArrayList<Byte> data) {
        mCallbackV11.onHs20IconQueryDone(bssid, fileName, data);
    }

    @Override
    public void onHs20SubscriptionRemediation(byte[/* 6 */] bssid,
            byte osuMethod, String url) {
        mCallbackV11.onHs20SubscriptionRemediation(bssid, osuMethod, url);
    }

    @Override
    public void onHs20DeauthImminentNotice(byte[/* 6 */] bssid, int reasonCode,
            int reAuthDelayInSec, String url) {
        mCallbackV11.onHs20DeauthImminentNotice(bssid, reasonCode, reAuthDelayInSec, url);
    }

    @Override
    public void onDisconnected(byte[/* 6 */] bssid, boolean locallyGenerated,
            int reasonCode) {
        mCallbackV11.onDisconnected(bssid, locallyGenerated, reasonCode);
    }

    @Override
    public void onAssociationRejected(byte[/* 6 */] bssid, int statusCode,
            boolean timedOut) {
        mCallbackV11.onAssociationRejected(bssid, statusCode, timedOut);
    }

    @Override
    public void onAuthenticationTimeout(byte[/* 6 */] bssid) {
        mCallbackV11.onAuthenticationTimeout(bssid);
    }

    @Override
    public void onBssidChanged(byte reason, byte[/* 6 */] bssid) {
        mCallbackV11.onBssidChanged(reason, bssid);
    }

    @Override
    public void onEapFailure() {
        mCallbackV11.onEapFailure();
    }

    @Override
    public void onEapFailure_1_1(int code) {
        mCallbackV11.onEapFailure_1_1(code);
    }

    @Override
    public void onWpsEventSuccess() {
        mCallbackV11.onWpsEventSuccess();
    }

    @Override
    public void onWpsEventFail(byte[/* 6 */] bssid, short configError, short errorInd) {
        mCallbackV11.onWpsEventFail(bssid, configError, errorInd);
    }

    @Override
    public void onWpsEventPbcOverlap() {
        mCallbackV11.onWpsEventPbcOverlap();
    }

    @Override
    public void onExtRadioWorkStart(int id) {
        mCallbackV11.onExtRadioWorkStart(id);
    }

    @Override
    public void onExtRadioWorkTimeout(int id) {
        mCallbackV11.onExtRadioWorkTimeout(id);
    }

    @Override
    public void onDppSuccessConfigReceived(ArrayList<Byte> ssid, String password,
            byte[] psk, int securityAkm) {
        if (mStaIfaceHal.getDppCallback() == null) {
            Log.e(TAG, "onDppSuccessConfigReceived callback is null");
            return;
        }

        WifiConfiguration newWifiConfiguration = new WifiConfiguration();

        // Set up SSID
        WifiSsid wifiSsid = mSsidTranslator.getTranslatedSsid(
                WifiSsid.fromBytes(NativeUtil.byteArrayFromArrayList(ssid)));

        newWifiConfiguration.SSID = wifiSsid.toString();

        // Set up password or PSK
        if (password != null) {
            newWifiConfiguration.preSharedKey = "\"" + password + "\"";
        } else if (psk != null) {
            newWifiConfiguration.preSharedKey = Arrays.toString(psk);
        }

        // Set up key management: SAE or PSK
        if (securityAkm == DppAkm.SAE) {
            newWifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        } else if (securityAkm == DppAkm.PSK_SAE || securityAkm == DppAkm.PSK) {
            newWifiConfiguration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        } else {
            // No other AKMs are currently supported
            onDppFailure(DppFailureCode.NOT_SUPPORTED);
            return;
        }

        // Set up default values
        newWifiConfiguration.creatorName = mContext.getPackageManager()
                .getNameForUid(Process.WIFI_UID);
        newWifiConfiguration.status = WifiConfiguration.Status.ENABLED;

        mStaIfaceHal.getDppCallback().onSuccessConfigReceived(newWifiConfiguration,
                false);
    }

    @Override
    public void onDppSuccessConfigSent() {
        if (mStaIfaceHal.getDppCallback() != null) {
            mStaIfaceHal.getDppCallback().onSuccess(
                    SupplicantStaIfaceHal.DppEventType.CONFIGURATION_SENT);
        } else {
            Log.e(TAG, "onSuccessConfigSent callback is null");
        }
    }

    @Override
    public void onDppFailure(int code) {
        // Convert HIDL to framework DppFailureCode, then continue with callback
        onDppFailureInternal(halToFrameworkDppFailureCode(code));
    }

    protected void onDppFailureInternal(int frameworkCode) {
        if (mStaIfaceHal.getDppCallback() != null) {
            mStaIfaceHal.getDppCallback().onFailure(frameworkCode, null, null, null);
        } else {
            Log.e(TAG, "onDppFailure callback is null");
        }
    }

    @Override
    public void onDppProgress(int code) {
        // Convert HIDL to framework DppProgressCode, then continue with callback
        onDppProgressInternal(halToFrameworkDppProgressCode(code));
    }

    protected void onDppProgressInternal(int frameworkCode) {
        if (mStaIfaceHal.getDppCallback() != null) {
            mStaIfaceHal.getDppCallback().onProgress(frameworkCode);
        } else {
            Log.e(TAG, "onDppProgress callback is null");
        }
    }

    private int halToFrameworkDppProgressCode(int progressCode) {
        switch(progressCode) {
            case DppProgressCode.AUTHENTICATION_SUCCESS:
                return SupplicantStaIfaceHal.DppProgressCode.AUTHENTICATION_SUCCESS;
            case DppProgressCode.RESPONSE_PENDING:
                return SupplicantStaIfaceHal.DppProgressCode.RESPONSE_PENDING;
            default:
                Log.e(TAG, "Invalid DppProgressCode received");
                return -1;
        }
    }

    private int halToFrameworkDppFailureCode(int failureCode) {
        switch(failureCode) {
            case DppFailureCode.INVALID_URI:
                return SupplicantStaIfaceHal.DppFailureCode.INVALID_URI;
            case DppFailureCode.AUTHENTICATION:
                return SupplicantStaIfaceHal.DppFailureCode.AUTHENTICATION;
            case DppFailureCode.NOT_COMPATIBLE:
                return SupplicantStaIfaceHal.DppFailureCode.NOT_COMPATIBLE;
            case DppFailureCode.CONFIGURATION:
                return SupplicantStaIfaceHal.DppFailureCode.CONFIGURATION;
            case DppFailureCode.BUSY:
                return SupplicantStaIfaceHal.DppFailureCode.BUSY;
            case DppFailureCode.TIMEOUT:
                return SupplicantStaIfaceHal.DppFailureCode.TIMEOUT;
            case DppFailureCode.FAILURE:
                return SupplicantStaIfaceHal.DppFailureCode.FAILURE;
            case DppFailureCode.NOT_SUPPORTED:
                return SupplicantStaIfaceHal.DppFailureCode.NOT_SUPPORTED;
            default:
                Log.e(TAG, "Invalid DppFailureCode received");
                return -1;
        }
    }
}
