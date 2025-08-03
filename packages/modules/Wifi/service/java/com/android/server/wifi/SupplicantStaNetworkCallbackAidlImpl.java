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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.hardware.wifi.supplicant.GsmRand;
import android.hardware.wifi.supplicant.ISupplicantStaNetworkCallback;
import android.hardware.wifi.supplicant.NetworkRequestEapSimGsmAuthParams;
import android.hardware.wifi.supplicant.NetworkRequestEapSimUmtsAuthParams;
import android.hardware.wifi.supplicant.TransitionDisableIndication;

import com.android.server.wifi.util.NativeUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

class SupplicantStaNetworkCallbackAidlImpl extends ISupplicantStaNetworkCallback.Stub {
    private static final String TAG = "SupplicantStaNetworkCallbackAidlImpl";
    private final SupplicantStaNetworkHalAidlImpl mNetworkHal;
    /**
     * Current configured network's framework network id.
     */
    private final int mFrameworkNetworkId;
    /**
     * Current configured network's ssid.
     */
    private final String mSsid;
    private final String mIfaceName;
    private final WifiMonitor mWifiMonitor;
    private final Object mLock;

    SupplicantStaNetworkCallbackAidlImpl(
            @NonNull SupplicantStaNetworkHalAidlImpl networkHal,
            int frameworkNetworkId, @NonNull String ssid,
            @NonNull String ifaceName, @NonNull Object lock, @NonNull WifiMonitor wifiMonitor) {
        mNetworkHal = networkHal;
        mFrameworkNetworkId = frameworkNetworkId;
        mSsid = ssid;
        mIfaceName = ifaceName;
        mLock = lock;
        mWifiMonitor = wifiMonitor;
    }

    @Override
    public void onNetworkEapSimGsmAuthRequest(NetworkRequestEapSimGsmAuthParams params) {
        synchronized (mLock) {
            mNetworkHal.logCallback("onNetworkEapSimGsmAuthRequest");
            String[] data = new String[params.rands.length];
            int i = 0;
            for (GsmRand rand : params.rands) {
                data[i++] = NativeUtil.hexStringFromByteArray(rand.data);
            }
            mWifiMonitor.broadcastNetworkGsmAuthRequestEvent(
                    mIfaceName, mFrameworkNetworkId, mSsid, data);
        }
    }

    @Override
    public void onNetworkEapSimUmtsAuthRequest(NetworkRequestEapSimUmtsAuthParams params) {
        synchronized (mLock) {
            mNetworkHal.logCallback("onNetworkEapSimUmtsAuthRequest");
            String randHex = NativeUtil.hexStringFromByteArray(params.rand);
            String autnHex = NativeUtil.hexStringFromByteArray(params.autn);
            String[] data = {randHex, autnHex};
            mWifiMonitor.broadcastNetworkUmtsAuthRequestEvent(
                    mIfaceName, mFrameworkNetworkId, mSsid, data);
        }
    }

    @Override
    public void onNetworkEapIdentityRequest() {
        synchronized (mLock) {
            mNetworkHal.logCallback("onNetworkEapIdentityRequest");
            mWifiMonitor.broadcastNetworkIdentityRequestEvent(
                    mIfaceName, mFrameworkNetworkId, mSsid);
        }
    }

    @Override
    public void onTransitionDisable(int indicationBits) {
        synchronized (mLock) {
            mNetworkHal.logCallback("onTransitionDisable");
            int frameworkBits = 0;
            if ((indicationBits & TransitionDisableIndication.USE_WPA3_PERSONAL) != 0) {
                frameworkBits |= WifiMonitor.TDI_USE_WPA3_PERSONAL;
            }
            if ((indicationBits & TransitionDisableIndication.USE_SAE_PK) != 0) {
                frameworkBits |= WifiMonitor.TDI_USE_SAE_PK;
            }
            if ((indicationBits & TransitionDisableIndication.USE_WPA3_ENTERPRISE) != 0) {
                frameworkBits |= WifiMonitor.TDI_USE_WPA3_ENTERPRISE;
            }
            if ((indicationBits & TransitionDisableIndication.USE_ENHANCED_OPEN) != 0) {
                frameworkBits |= WifiMonitor.TDI_USE_ENHANCED_OPEN;
            }
            if (frameworkBits == 0) {
                return;
            }

            mWifiMonitor.broadcastTransitionDisableEvent(
                    mIfaceName, mFrameworkNetworkId, frameworkBits);
        }
    }

    private String byteArrayToString(byte[] byteArray) {
        // Not a valid bytes for a string
        if (byteArray == null) return null;
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(byteArray));
            return decoded.toString();
        } catch (CharacterCodingException cce) {
        }
        return null;
    }

    @Override
    public void onServerCertificateAvailable(
            int depth,
            byte[] subjectBytes,
            byte[] certHashBytes,
            byte[] certBytes) {
        synchronized (mLock) {
            // OpenSSL default maximum depth is 100.
            if (depth < 0 || depth > 100) {
                mNetworkHal.logCallback("onServerCertificateAvailable: invalid depth " + depth);
                return;
            }
            if (null == subjectBytes) {
                mNetworkHal.logCallback("onServerCertificateAvailable: subject is null.");
                return;
            }
            if (null == certHashBytes) {
                mNetworkHal.logCallback("onServerCertificateAvailable: cert hash is null.");
                return;
            }
            if (null == certBytes) {
                mNetworkHal.logCallback("onServerCertificateAvailable: cert is null.");
                return;
            }

            mNetworkHal.logCallback("onServerCertificateAvailable: "
                    + " depth=" + depth
                    + " subjectBytes size=" + subjectBytes.length
                    + " certHashBytes size=" + certHashBytes.length
                    + " certBytes size=" + certBytes.length);

            if (0 == certHashBytes.length) return;
            if (0 == certBytes.length) return;

            String subject = byteArrayToString(subjectBytes);
            if (null == subject) {
                mNetworkHal.logCallback(
                        "onServerCertificateAvailable: cannot convert subject bytes to string.");
                return;
            }
            String certHash = byteArrayToString(certHashBytes);
            if (null == subject) {
                mNetworkHal.logCallback(
                        "onServerCertificateAvailable: cannot convert cert hash bytes to string.");
                return;
            }
            X509Certificate cert = null;
            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                InputStream in = new ByteArrayInputStream(certBytes);
                cert = (X509Certificate) certFactory.generateCertificate(in);
            } catch (CertificateException e) {
                cert = null;
                mNetworkHal.logCallback(
                        "onServerCertificateAvailable: "
                        + "Failed to get instance for CertificateFactory: " + e);
            } catch (IllegalArgumentException e) {
                cert = null;
                mNetworkHal.logCallback(
                        "onServerCertificateAvailable: Failed to decode the data: " + e);
            }
            if (null == cert) {
                mNetworkHal.logCallback(
                        "onServerCertificateAvailable: Failed to read certificate.");
                return;
            }

            mNetworkHal.logCallback("onServerCertificateAvailable:"
                    + " depth=" + depth
                    + " subject=" + subject
                    + " certHash=" + certHash
                    + " cert=" + cert);
            mWifiMonitor.broadcastCertificationEvent(
                    mIfaceName, mFrameworkNetworkId, mSsid, depth,
                    new CertificateEventInfo(cert, certHash));
        }
    }

    @Override
    public void onPermanentIdReqDenied() {
        mNetworkHal.logCallback("onPermanentIdReqDenied");

        mWifiMonitor.broadcastPermanentIdReqDenied(
                mIfaceName, mFrameworkNetworkId, mSsid);
    }

    @Override
    public String getInterfaceHash() {
        return ISupplicantStaNetworkCallback.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return ISupplicantStaNetworkCallback.VERSION;
    }
}
