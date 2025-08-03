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
import android.content.Context;
import android.hardware.wifi.supplicant.AuthAlgMask;
import android.hardware.wifi.supplicant.DppConnectionKeys;
import android.hardware.wifi.supplicant.EapMethod;
import android.hardware.wifi.supplicant.EapPhase2Method;
import android.hardware.wifi.supplicant.GroupCipherMask;
import android.hardware.wifi.supplicant.GroupMgmtCipherMask;
import android.hardware.wifi.supplicant.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.ISupplicantStaNetworkCallback;
import android.hardware.wifi.supplicant.KeyMgmtMask;
import android.hardware.wifi.supplicant.NetworkResponseEapSimGsmAuthParams;
import android.hardware.wifi.supplicant.NetworkResponseEapSimUmtsAuthParams;
import android.hardware.wifi.supplicant.OcspType;
import android.hardware.wifi.supplicant.PairwiseCipherMask;
import android.hardware.wifi.supplicant.ProtoMask;
import android.hardware.wifi.supplicant.SaeH2eMode;
import android.hardware.wifi.supplicant.TlsVersion;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.ArrayUtils;
import com.android.server.wifi.util.HalAidlUtil;
import com.android.server.wifi.util.NativeUtil;
import com.android.wifi.resources.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Wrapper class for ISupplicantStaNetwork HAL calls. Gets and sets supplicant sta network variables
 * and interacts with networks.
 * Public fields should be treated as invalid until their 'get' method is called, which will set the
 * value if it returns true
 * To maintain thread-safety, the locking protocol is that every non-static method (regardless of
 * access level) acquires mLock.
 */
@ThreadSafe
public class SupplicantStaNetworkHalAidlImpl {
    private static final String TAG = "SupplicantStaNetworkHalAidlImpl";
    @VisibleForTesting
    public static final String ID_STRING_KEY_FQDN = "fqdn";
    @VisibleForTesting
    public static final String ID_STRING_KEY_CREATOR_UID = "creatorUid";
    @VisibleForTesting
    public static final String ID_STRING_KEY_CONFIG_KEY = "configKey";

    /**
     * Regex pattern for extracting the GSM sim authentication response params from a string.
     * Matches a strings like the following: "[:<kc_value>:<sres_value>]";
     */
    private static final Pattern GSM_AUTH_RESPONSE_PARAMS_PATTERN =
            Pattern.compile(":([0-9a-fA-F]+):([0-9a-fA-F]+)");
    /**
     * Regex pattern for extracting the UMTS sim authentication response params from a string.
     * Matches a strings like the following: ":<ik_value>:<ck_value>:<res_value>";
     */
    private static final Pattern UMTS_AUTH_RESPONSE_PARAMS_PATTERN =
            Pattern.compile("^:([0-9a-fA-F]+):([0-9a-fA-F]+):([0-9a-fA-F]+)$");
    /**
     * Regex pattern for extracting the UMTS sim auts response params from a string.
     * Matches a strings like the following: ":<auts_value>";
     */
    private static final Pattern UMTS_AUTS_RESPONSE_PARAMS_PATTERN =
            Pattern.compile("^:([0-9a-fA-F]+)$");

    private final Object mLock = new Object();
    private final Context mContext;
    private final String mIfaceName;
    private final WifiMonitor mWifiMonitor;
    private final WifiGlobals mWifiGlobals;
    private ISupplicantStaNetwork mISupplicantStaNetwork;
    private ISupplicantStaNetworkCallback mISupplicantStaNetworkCallback;
    private int mServiceVersion;

    private boolean mVerboseLoggingEnabled = false;
    // Network variables read from wpa_supplicant.
    private int mNetworkId;
    private byte[] mSsid;
    private byte[/* 6 */] mBssid;
    private boolean mScanSsid;
    private int mKeyMgmtMask;
    private int mProtoMask;
    private int mAuthAlgMask;
    private int mGroupCipherMask;
    private int mPairwiseCipherMask;
    private int mGroupMgmtCipherMask;
    private String mPskPassphrase;
    private String mSaePassword;
    private String mSaePasswordId;
    private byte[] mPsk;
    private byte[] mWepKey;
    private int mWepTxKeyIdx;
    private boolean mRequirePmf;
    private String mIdStr;
    private int mEapMethod;
    private int mEapPhase2Method;
    private byte[] mEapIdentity;
    private byte[] mEapAnonymousIdentity;
    private byte[] mEapPassword;
    private String mEapCACert;
    private String mEapCAPath;
    private String mEapClientCert;
    private String mEapPrivateKeyId;
    private String mEapSubjectMatch;
    private String mEapAltSubjectMatch;
    private boolean mEapEngine;
    private String mEapEngineID;
    private String mEapDomainSuffixMatch;
    private @WifiEnterpriseConfig.Ocsp int mOcsp;
    private String mWapiCertSuite;
    private long mAdvanceKeyMgmtFeatures;
    private long mWpaDriverFeatures;

    SupplicantStaNetworkHalAidlImpl(int serviceVersion,
            ISupplicantStaNetwork staNetwork, String ifaceName,
            Context context, WifiMonitor monitor, WifiGlobals wifiGlobals,
            long advanceKeyMgmtFeature, long wpaDriverFeatures) {
        mServiceVersion = serviceVersion;
        mISupplicantStaNetwork = staNetwork;
        mContext = context;
        mIfaceName = ifaceName;
        mWifiMonitor = monitor;
        mWifiGlobals = wifiGlobals;
        mAdvanceKeyMgmtFeatures = advanceKeyMgmtFeature;
        mWpaDriverFeatures = wpaDriverFeatures;
    }

    /**
     * Check that the service is running at least the expected version.
     * Use to avoid the case where the framework is using a newer
     * interface version than the service.
     */
    private boolean isServiceVersionIsAtLeast(int expectedVersion) {
        return expectedVersion <= mServiceVersion;
    }

    /**
     * Enable/Disable verbose logging.
     *
     */
    void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        synchronized (mLock) {
            mVerboseLoggingEnabled = verboseEnabled;
        }
    }

    /**
     * Read network variables from wpa_supplicant into the provided WifiConfiguration object.
     *
     * @param config WifiConfiguration object to be populated.
     * @param networkExtras Map of network extras parsed from wpa_supplicant.
     * @return true if succeeds, false otherwise.
     * @throws IllegalArgumentException on malformed configuration params.
     */
    @VisibleForTesting
    public boolean loadWifiConfiguration(WifiConfiguration config,
            Map<String, String> networkExtras) throws IllegalArgumentException {
        synchronized (mLock) {
            if (config == null) {
                return false;
            }
            /** SSID */
            config.SSID = null;
            if (getSsid() && !ArrayUtils.isEmpty(mSsid)) {
                config.SSID = WifiSsid.fromBytes(mSsid).toString();
            } else {
                Log.e(TAG, "failed to read ssid");
                return false;
            }
            /** Network Id */
            config.networkId = -1;
            if (getId()) {
                config.networkId = mNetworkId;
            } else {
                Log.e(TAG, "getId failed");
                return false;
            }
            /** BSSID */
            config.getNetworkSelectionStatus().setNetworkSelectionBSSID(null);
            if (getBssid() && !ArrayUtils.isEmpty(mBssid)) {
                config.getNetworkSelectionStatus().setNetworkSelectionBSSID(
                        NativeUtil.macAddressFromByteArray(mBssid));
            }
            /** Scan SSID (Is Hidden Network?) */
            config.hiddenSSID = false;
            if (getScanSsid()) {
                config.hiddenSSID = mScanSsid;
            }
            /** Require PMF*/
            config.requirePmf = false;
            if (getRequirePmf()) {
                config.requirePmf = mRequirePmf;
            }
            /** WEP keys **/
            config.wepTxKeyIndex = -1;
            if (getWepTxKeyIdx()) {
                config.wepTxKeyIndex = mWepTxKeyIdx;
            }
            for (int i = 0; i < 4; i++) {
                config.wepKeys[i] = null;
                if (getWepKey(i) && !ArrayUtils.isEmpty(mWepKey)) {
                    config.wepKeys[i] = NativeUtil.bytesToHexOrQuotedString(
                            NativeUtil.byteArrayToArrayList(mWepKey));
                }
            }

            /** allowedKeyManagement */
            if (getKeyMgmt()) {
                BitSet keyMgmtMask = HalAidlUtil.supplicantToWifiConfigurationKeyMgmtMask(
                        mKeyMgmtMask);
                keyMgmtMask = removeFastTransitionFlags(keyMgmtMask);
                keyMgmtMask = removeSha256KeyMgmtFlags(keyMgmtMask);
                keyMgmtMask = removePskSaeUpgradableTypeFlags(keyMgmtMask);
                config.setSecurityParams(keyMgmtMask);
                config.enableFils(
                        keyMgmtMask.get(WifiConfiguration.KeyMgmt.FILS_SHA256),
                        keyMgmtMask.get(WifiConfiguration.KeyMgmt.FILS_SHA384));
            }

            // supplicant only have one valid security type, it won't be a disbled params.
            SecurityParams securityParams = config.getDefaultSecurityParams();

            /** PSK passphrase */
            config.preSharedKey = null;
            if (getPskPassphrase() && !TextUtils.isEmpty(mPskPassphrase)) {
                if (securityParams.isSecurityType(WifiConfiguration.SECURITY_TYPE_WAPI_PSK)) {
                    config.preSharedKey = mPskPassphrase;
                } else {
                    config.preSharedKey = NativeUtil.addEnclosingQuotes(mPskPassphrase);
                }
            } else if (getPsk() && !ArrayUtils.isEmpty(mPsk)) {
                config.preSharedKey = NativeUtil.hexStringFromByteArray(mPsk);
            } /* Do not read SAE password */

            /** metadata: idstr */
            if (getIdStr() && !TextUtils.isEmpty(mIdStr)) {
                Map<String, String> metadata = parseNetworkExtra(mIdStr);
                networkExtras.putAll(metadata);
            } else {
                Log.w(TAG, "getIdStr failed or empty");
            }

            /** WAPI Cert Suite */
            if (securityParams.isSecurityType(WifiConfiguration.SECURITY_TYPE_WAPI_CERT)) {
                if (config.enterpriseConfig == null) {
                    return false;
                }
                config.enterpriseConfig.setEapMethod(
                        WifiEnterpriseConfig.Eap.WAPI_CERT);
                /** WAPI Certificate Suite. */
                if (getWapiCertSuite() && !TextUtils.isEmpty(mWapiCertSuite)) {
                    config.enterpriseConfig.setWapiCertSuite(mWapiCertSuite);
                }
                return true;
            }
            return loadWifiEnterpriseConfig(config.SSID, config.enterpriseConfig);
        }
    }

    /**
     * Read network variables from the provided WifiConfiguration object into wpa_supplicant.
     *
     * @param config WifiConfiguration object to be saved. Note that the SSID will already by the
     *               raw, untranslated SSID to pass to supplicant directly.
     * @return true if succeeds, false otherwise.
     * @throws IllegalArgumentException on malformed configuration params.
     */
    public boolean saveWifiConfiguration(WifiConfiguration config) throws IllegalArgumentException {
        synchronized (mLock) {
            if (config == null) {
                return false;
            }
            // ieee80211be
            if (!config.isWifi7Enabled() && isServiceVersionIsAtLeast(3)) {
                if (!disableEht()) {
                    Log.e(TAG, "failed to disable EHT (Wi-Fi 7)");
                    return false;
                }
            }
            // SSID
            if (config.SSID != null) {
                WifiSsid wifiSsid = WifiSsid.fromString(config.SSID);
                if (!setSsid(wifiSsid.getBytes())) {
                    Log.e(TAG, "failed to set SSID: " + wifiSsid);
                    return false;
                }
            }
            // BSSID
            String bssidStr = config.getNetworkSelectionStatus().getNetworkSelectionBSSID();
            if (bssidStr != null) {
                byte[] bssid = NativeUtil.macAddressToByteArray(bssidStr);
                if (!setBssid(bssid)) {
                    Log.e(TAG, "failed to set BSSID: " + bssidStr);
                    return false;
                }
            }
            // HiddenSSID
            if (!setScanSsid(config.hiddenSSID)) {
                Log.e(TAG, config.SSID + ": failed to set hiddenSSID: " + config.hiddenSSID);
                return false;
            }

            SecurityParams securityParams = config.getNetworkSelectionStatus()
                    .getCandidateSecurityParams();
            if (securityParams == null) {
                Log.wtf(TAG, "No available security params.");
                return false;
            }
            Log.d(TAG, "The target security params: " + securityParams);

            boolean isRequirePmf = NativeUtil.getOptimalPmfSettingForConfig(config,
                    securityParams.isRequirePmf(), mWifiGlobals);
            // RequirePMF
            if (!setRequirePmf(isRequirePmf)) {
                Log.e(TAG, config.SSID + ": failed to set requirePMF: " + config.requirePmf);
                return false;
            }
            // Key Management Scheme
            BitSet allowedKeyManagement = securityParams.getAllowedKeyManagement();
            if (allowedKeyManagement.cardinality() != 0) {
                // Add upgradable type key management flags for PSK/SAE.
                allowedKeyManagement = addPskSaeUpgradableTypeFlagsIfSupported(config,
                        allowedKeyManagement);
                // Add FT flags if supported.
                allowedKeyManagement = addFastTransitionFlags(allowedKeyManagement);
                // Add SHA256 key management flags.
                allowedKeyManagement = addSha256KeyMgmtFlags(allowedKeyManagement);
                if (!setKeyMgmt(wifiConfigurationToSupplicantKeyMgmtMask(allowedKeyManagement))) {
                    Log.e(TAG, "failed to set Key Management");
                    return false;
                }
                // Check and set SuiteB configurations.
                if (allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SUITE_B_192)
                        && !saveSuiteBConfig(config)) {
                    Log.e(TAG, "failed to set Suite-B-192 configuration");
                    return false;
                }
                // Check and set DPP Connection keys
                if (allowedKeyManagement.get(WifiConfiguration.KeyMgmt.DPP)
                        && !saveDppConnectionConfig(config)) {
                    Log.e(TAG, "failed to set DPP connection params");
                    return false;
                }
            }
            // Security Protocol
            BitSet allowedProtocols = securityParams.getAllowedProtocols();
            if (allowedProtocols.cardinality() != 0 && !setProto(
                    wifiConfigurationToSupplicantProtoMask(allowedProtocols, mWifiGlobals,
                            WifiConfigurationUtil.isConfigForEnterpriseNetwork(config)))) {
                Log.e(TAG, "failed to set Security Protocol");
                return false;
            }
            // Auth Algorithm
            BitSet allowedAuthAlgorithms = securityParams.getAllowedAuthAlgorithms();
            if (allowedAuthAlgorithms.cardinality() != 0
                    && !setAuthAlg(wifiConfigurationToSupplicantAuthAlgMask(
                    allowedAuthAlgorithms))) {
                Log.e(TAG, "failed to set AuthAlgorithm");
                return false;
            }
            // Group Cipher
            BitSet allowedGroupCiphers = NativeUtil.getOptimalGroupCiphersForConfig(
                    config, securityParams.getAllowedGroupCiphers(), mWifiGlobals);
            if (allowedGroupCiphers.cardinality() != 0
                    && (!setGroupCipher(wifiConfigurationToSupplicantGroupCipherMask(
                    allowedGroupCiphers)))) {
                Log.e(TAG, "failed to set Group Cipher");
                return false;
            }
            // Pairwise Cipher
            BitSet allowedPairwiseCiphers = NativeUtil.getOptimalPairwiseCiphersForConfig(
                    config, securityParams.getAllowedPairwiseCiphers(), mWifiGlobals);
            if (allowedPairwiseCiphers.cardinality() != 0
                    && !setPairwiseCipher(wifiConfigurationToSupplicantPairwiseCipherMask(
                    allowedPairwiseCiphers))) {
                Log.e(TAG, "failed to set PairwiseCipher");
                return false;
            }
            // Pre Shared Key
            // For PSK, this can either be quoted ASCII passphrase or hex string for raw psk.
            // For SAE, password must be a quoted ASCII string
            if (config.preSharedKey != null) {
                if (securityParams.isSecurityType(WifiConfiguration.SECURITY_TYPE_WAPI_PSK)) {
                    if (!setPskPassphrase(config.preSharedKey)) {
                        Log.e(TAG, "failed to set wapi psk passphrase");
                        return false;
                    }
                } else if (config.preSharedKey.startsWith("\"")) {
                    if (allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SAE)) {
                        // WPA3 case, field is SAE Password
                        if (!setSaePassword(
                                NativeUtil.removeEnclosingQuotes(config.preSharedKey))) {
                            Log.e(TAG, "failed to set sae password");
                            return false;
                        }
                    }
                    if (allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                        if (!setPskPassphrase(
                                NativeUtil.removeEnclosingQuotes(config.preSharedKey))) {
                            Log.e(TAG, "failed to set psk passphrase");
                            return false;
                        }
                    }
                } else {
                    if (!allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)
                            && allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SAE)) {
                        return false;
                    }
                    if (!setPsk(NativeUtil.hexStringToByteArray(config.preSharedKey))) {
                        Log.e(TAG, "failed to set psk");
                        return false;
                    }
                }
            }
            // Wep Keys
            boolean hasSetKey = false;
            if (config.wepKeys != null) {
                for (int i = 0; i < config.wepKeys.length; i++) {
                    if (config.wepKeys[i] != null) {
                        if (!setWepKey(i, NativeUtil.byteArrayFromArrayList(
                                NativeUtil.hexOrQuotedStringToBytes(config.wepKeys[i])))) {
                            Log.e(TAG, "failed to set wep_key " + i);
                            return false;
                        }
                        hasSetKey = true;
                    }
                }
            }
            // Wep Tx Key Idx
            if (hasSetKey) {
                if (!setWepTxKeyIdx(config.wepTxKeyIndex)) {
                    Log.e(TAG, "failed to set wep_tx_keyidx: " + config.wepTxKeyIndex);
                    return false;
                }
            }
            // metadata: FQDN + ConfigKey + CreatorUid
            final Map<String, String> metadata = new HashMap<String, String>();
            if (config.isPasspoint()) {
                metadata.put(ID_STRING_KEY_FQDN, config.FQDN);
                // Selected RCOI
                if (!setSelectedRcoi(config.enterpriseConfig.getSelectedRcoi())) {
                    Log.e(TAG, "failed to set selected RCOI");
                    return false;
                }
            }
            metadata.put(ID_STRING_KEY_CONFIG_KEY, config.getProfileKey());
            metadata.put(ID_STRING_KEY_CREATOR_UID, Integer.toString(config.creatorUid));
            if (!setIdStr(createNetworkExtra(metadata))) {
                Log.e(TAG, "failed to set id string");
                return false;
            }
            // UpdateIdentifier
            if (config.updateIdentifier != null
                    && !setUpdateIdentifier(Integer.parseInt(config.updateIdentifier))) {
                Log.e(TAG, "failed to set update identifier");
                return false;
            }
            // SAE configuration
            if (allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SAE)) {
                /**
                 * Hash-to-Element preference.
                 * For devices that don't support H2E, H2E mode will be permanently disabled.
                 * Devices that support H2E will enable both legacy and H2E mode by default,
                 * and will connect to SAE networks with H2E if possible, unless H2E only
                 * mode is enabled, and then the device will not connect to SAE networks in
                 * legacy mode.
                 */
                if (!mWifiGlobals.isWpa3SaeH2eSupported() && securityParams.isSaeH2eOnlyMode()) {
                    Log.e(TAG, "This device does not support SAE H2E.");
                    return false;
                }
                byte mode = mWifiGlobals.isWpa3SaeH2eSupported()
                        ? SaeH2eMode.H2E_OPTIONAL
                        : SaeH2eMode.DISABLED;
                if (securityParams.isSaeH2eOnlyMode()) {
                    mode = SaeH2eMode.H2E_MANDATORY;
                }
                if (!setSaeH2eMode(mode)) {
                    Log.e(TAG, "failed to set H2E preference.");
                    return false;
                }
            }
            // Vendor data
            if (SdkLevel.isAtLeastV() && isServiceVersionIsAtLeast(3)
                    && config.getVendorData() != null
                    && !config.getVendorData().isEmpty()
                    && !setVendorData(config.getVendorData())) {
                Log.e(TAG, "Failed to set vendor data.");
                return false;
            }
            // Finish here if no EAP config to set
            if (config.enterpriseConfig != null
                    && config.enterpriseConfig.getEapMethod() != WifiEnterpriseConfig.Eap.NONE) {
                if (config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.WAPI_CERT) {
                    // WAPI certificate suite name
                    String param = config.enterpriseConfig
                            .getFieldValue(WifiEnterpriseConfig.WAPI_CERT_SUITE_KEY);
                    if (!TextUtils.isEmpty(param) && !setWapiCertSuite(param)) {
                        Log.e(TAG, config.SSID + ": failed to set WAPI certificate suite: "
                                + param);
                        return false;
                    }
                    return true;
                } else if (!saveWifiEnterpriseConfig(config.SSID, config.enterpriseConfig)) {
                    return false;
                }
            }

            // Now that the network is configured fully, start listening for callback events.
            return registerNewCallback(config.networkId, config.SSID);
        }
    }

    /**
     * Read network variables from wpa_supplicant into the provided WifiEnterpriseConfig object.
     *
     * @param ssid      SSID of the network. (Used for logging purposes only)
     * @param eapConfig WifiEnterpriseConfig object to be populated.
     * @return true if succeeds, false otherwise.
     */
    private boolean loadWifiEnterpriseConfig(String ssid, WifiEnterpriseConfig eapConfig) {
        synchronized (mLock) {
            if (eapConfig == null) {
                return false;
            }
            /** EAP method */
            if (getEapMethod()) {
                eapConfig.setEapMethod(supplicantToWifiConfigurationEapMethod(mEapMethod));
            } else {
                // Invalid eap method could be because it's not an enterprise config.
                Log.e(TAG, "Failed to get eap method. Assuming not an enterprise network");
                return true;
            }
            /** EAP Phase 2 method */
            if (getEapPhase2Method()) {
                eapConfig.setPhase2Method(
                        supplicantToWifiConfigurationEapPhase2Method(mEapPhase2Method));
            } else {
                // We cannot have an invalid eap phase 2 method. Return failure.
                Log.e(TAG, "Failed to get eap phase2 method");
                return false;
            }
            /** EAP Identity */
            if (getEapIdentity() && !ArrayUtils.isEmpty(mEapIdentity)) {
                eapConfig.setFieldValue(
                        WifiEnterpriseConfig.IDENTITY_KEY,
                        NativeUtil.stringFromByteArray(mEapIdentity));
            }
            /** EAP Anonymous Identity */
            if (getEapAnonymousIdentity() && !ArrayUtils.isEmpty(mEapAnonymousIdentity)) {
                eapConfig.setFieldValue(
                        WifiEnterpriseConfig.ANON_IDENTITY_KEY,
                        NativeUtil.stringFromByteArray(mEapAnonymousIdentity));
            }
            /** EAP Password */
            if (getEapPassword() && !ArrayUtils.isEmpty(mEapPassword)) {
                eapConfig.setFieldValue(
                        WifiEnterpriseConfig.PASSWORD_KEY,
                        NativeUtil.stringFromByteArray(mEapPassword));
            }
            /** EAP Client Cert */
            if (getEapClientCert() && !TextUtils.isEmpty(mEapClientCert)) {
                eapConfig.setFieldValue(WifiEnterpriseConfig.CLIENT_CERT_KEY, mEapClientCert);
            }
            /** EAP CA Cert */
            if (getEapCACert() && !TextUtils.isEmpty(mEapCACert)) {
                eapConfig.setFieldValue(WifiEnterpriseConfig.CA_CERT_KEY, mEapCACert);
            }
            /** EAP OCSP type */
            if (getOcsp()) {
                eapConfig.setOcsp(mOcsp);
            }
            /** EAP Subject Match */
            if (getEapSubjectMatch() && !TextUtils.isEmpty(mEapSubjectMatch)) {
                eapConfig.setFieldValue(WifiEnterpriseConfig.SUBJECT_MATCH_KEY, mEapSubjectMatch);
            }
            /** EAP Engine ID */
            if (getEapEngineId() && !TextUtils.isEmpty(mEapEngineID)) {
                eapConfig.setFieldValue(WifiEnterpriseConfig.ENGINE_ID_KEY, mEapEngineID);
            }
            /** EAP Engine. Set this only if the engine id is non null. */
            if (getEapEngine() && !TextUtils.isEmpty(mEapEngineID)) {
                eapConfig.setFieldValue(
                        WifiEnterpriseConfig.ENGINE_KEY,
                        mEapEngine
                                ? WifiEnterpriseConfig.ENGINE_ENABLE
                                : WifiEnterpriseConfig.ENGINE_DISABLE);
            }
            /** EAP Private Key */
            if (getEapPrivateKeyId() && !TextUtils.isEmpty(mEapPrivateKeyId)) {
                eapConfig.setFieldValue(WifiEnterpriseConfig.PRIVATE_KEY_ID_KEY, mEapPrivateKeyId);
            }
            /** EAP Alt Subject Match */
            if (getEapAltSubjectMatch() && !TextUtils.isEmpty(mEapAltSubjectMatch)) {
                eapConfig.setFieldValue(
                        WifiEnterpriseConfig.ALTSUBJECT_MATCH_KEY, mEapAltSubjectMatch);
            }
            /** EAP Domain Suffix Match */
            if (getEapDomainSuffixMatch() && !TextUtils.isEmpty(mEapDomainSuffixMatch)) {
                eapConfig.setFieldValue(
                        WifiEnterpriseConfig.DOM_SUFFIX_MATCH_KEY, mEapDomainSuffixMatch);
            }
            /** EAP CA Path*/
            if (getEapCAPath() && !TextUtils.isEmpty(mEapCAPath)) {
                eapConfig.setFieldValue(WifiEnterpriseConfig.CA_PATH_KEY, mEapCAPath);
            }
            return true;
        }
    }

    /**
     * save network variables from the provided dpp configuration to wpa_supplicant.
     *
     * @param config wificonfiguration object to be saved
     * @return true if succeeds, false otherwise.
     */
    private boolean saveDppConnectionConfig(WifiConfiguration config) {
        synchronized (mLock) {
            final String methodStr = "setDppKeys";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                DppConnectionKeys keys = new DppConnectionKeys();
                keys.connector = config.getDppConnector();
                keys.cSign = config.getDppCSignKey();
                keys.netAccessKey = config.getDppNetAccessKey();
                mISupplicantStaNetwork.setDppKeys(keys);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Save network variables from the provided SuiteB configuration to wpa_supplicant.
     *
     * @param config WifiConfiguration object to be saved
     * @return true if succeeds, false otherwise.
     */
    private boolean saveSuiteBConfig(WifiConfiguration config) {
        synchronized (mLock) {
            SecurityParams securityParams = config.getNetworkSelectionStatus()
                    .getCandidateSecurityParams();
            if (securityParams == null) {
                Log.wtf(TAG, "No available security params.");
                return false;
            }

            /** Group Cipher **/
            BitSet allowedGroupCiphers = securityParams.getAllowedGroupCiphers();
            if (allowedGroupCiphers.cardinality() != 0
                    && !setGroupCipher(wifiConfigurationToSupplicantGroupCipherMask(
                    allowedGroupCiphers))) {
                Log.e(TAG, "Failed to set Group Cipher");
                return false;
            }
            /** Pairwise Cipher*/
            BitSet allowedPairwiseCiphers = securityParams.getAllowedPairwiseCiphers();
            if (allowedPairwiseCiphers.cardinality() != 0
                    && !setPairwiseCipher(wifiConfigurationToSupplicantPairwiseCipherMask(
                    allowedPairwiseCiphers))) {
                Log.e(TAG, "Failed to set PairwiseCipher");
                return false;
            }
            /** GroupMgmt Cipher */
            BitSet allowedGroupManagementCiphers =
                    securityParams.getAllowedGroupManagementCiphers();
            if (allowedGroupManagementCiphers.cardinality() != 0
                    && !setGroupMgmtCipher(wifiConfigurationToSupplicantGroupMgmtCipherMask(
                    allowedGroupManagementCiphers))) {
                Log.e(TAG, "Failed to set GroupMgmtCipher");
                return false;
            }

            BitSet allowedSuiteBCiphers = securityParams.getAllowedSuiteBCiphers();
            if (allowedSuiteBCiphers.get(WifiConfiguration.SuiteBCipher.ECDHE_RSA)) {
                if (!enableTlsSuiteBEapPhase1Param(true)) {
                    Log.e(TAG, "Failed to set TLSSuiteB");
                    return false;
                }
            } else if (allowedSuiteBCiphers.get(WifiConfiguration.SuiteBCipher.ECDHE_ECDSA)) {
                if (!enableSuiteBEapOpenSslCiphers()) {
                    Log.e(TAG, "Failed to set OpensslCipher");
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * Save network variables from the provided WifiEnterpriseConfig object to wpa_supplicant.
     *
     * @param ssid SSID of the network. (Used for logging purposes only)
     * @param eapConfig WifiEnterpriseConfig object to be saved.
     * @return true if succeeds, false otherwise.
     */
    private boolean saveWifiEnterpriseConfig(String ssid, WifiEnterpriseConfig eapConfig) {
        synchronized (mLock) {
            if (eapConfig == null) {
                return false;
            }
            /** EAP method */
            if (!setEapMethod(wifiConfigurationToSupplicantEapMethod(eapConfig.getEapMethod()))) {
                Log.e(TAG, ssid + ": failed to set eap method: " + eapConfig.getEapMethod());
                return false;
            }
            /** EAP Phase 2 method */
            if (!setEapPhase2Method(wifiConfigurationToSupplicantEapPhase2Method(
                    eapConfig.getPhase2Method()))) {
                Log.e(TAG, ssid + ": failed to set eap phase 2 method: "
                        + eapConfig.getPhase2Method());
                return false;
            }
            if (eapConfig.isAuthenticationSimBased()
                    && eapConfig.getEapMethod() != WifiEnterpriseConfig.Eap.PEAP
                    && eapConfig.getStrictConservativePeerMode()) {
                if (!enableStrictConservativePeerMode()) {
                    Log.w(TAG, "failed or not support to set strict conservative peer mode.");
                }
                // don't return false, as the mode is optional.
            }
            String eapParam = null;
            /** EAP Identity */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.IDENTITY_KEY);
            if (!TextUtils.isEmpty(eapParam)
                    && !setEapIdentity(NativeUtil.stringToByteArray(eapParam))) {
                Log.e(TAG, ssid + ": failed to set eap identity: " + eapParam);
                return false;
            }
            /** EAP Anonymous Identity */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.ANON_IDENTITY_KEY);
            if (!TextUtils.isEmpty(eapParam)) {
                String decoratedUsernamePrefix =
                        eapConfig.getFieldValue(WifiEnterpriseConfig.DECORATED_IDENTITY_PREFIX_KEY);
                if (!TextUtils.isEmpty(decoratedUsernamePrefix)) {
                    eapParam = decoratedUsernamePrefix + eapParam;
                }
                if (!setEapAnonymousIdentity(NativeUtil.stringToByteArray(eapParam))) {
                    Log.e(TAG, ssid + ": failed to set eap anonymous identity: " + eapParam);
                    return false;
                }
            }
            /** EAP Password */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.PASSWORD_KEY);
            if (!TextUtils.isEmpty(eapParam)
                    && !setEapPassword(NativeUtil.stringToByteArray(eapParam))) {
                Log.e(TAG, ssid + ": failed to set eap password");
                return false;
            }
            /** EAP Client Cert */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.CLIENT_CERT_KEY);
            if (!TextUtils.isEmpty(eapParam) && !setEapClientCert(eapParam)) {
                Log.e(TAG, ssid + ": failed to set eap client cert: " + eapParam);
                return false;
            }
            /** EAP CA Cert */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.CA_CERT_KEY);
            if (!TextUtils.isEmpty(eapParam) && !setEapCACert(eapParam)) {
                Log.e(TAG, ssid + ": failed to set eap ca cert: " + eapParam);
                return false;
            }
            /** EAP Subject Match */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.SUBJECT_MATCH_KEY);
            if (!TextUtils.isEmpty(eapParam) && !setEapSubjectMatch(eapParam)) {
                Log.e(TAG, ssid + ": failed to set eap subject match: " + eapParam);
                return false;
            }
            /** EAP Engine ID */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.ENGINE_ID_KEY);
            if (!TextUtils.isEmpty(eapParam) && !setEapEngineID(eapParam)) {
                Log.e(TAG, ssid + ": failed to set eap engine id: " + eapParam);
                return false;
            }
            /** EAP Engine */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.ENGINE_KEY);
            if (!TextUtils.isEmpty(eapParam) && !setEapEngine(
                    eapParam.equals(WifiEnterpriseConfig.ENGINE_ENABLE) ? true : false)) {
                Log.e(TAG, ssid + ": failed to set eap engine: " + eapParam);
                return false;
            }
            /** EAP Private Key */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.PRIVATE_KEY_ID_KEY);
            if (!TextUtils.isEmpty(eapParam) && !setEapPrivateKeyId(eapParam)) {
                Log.e(TAG, ssid + ": failed to set eap private key: " + eapParam);
                return false;
            }
            /** EAP Alt Subject Match */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.ALTSUBJECT_MATCH_KEY);
            if (!TextUtils.isEmpty(eapParam) && !setEapAltSubjectMatch(eapParam)) {
                Log.e(TAG, ssid + ": failed to set eap alt subject match: " + eapParam);
                return false;
            }
            /** EAP Domain Suffix Match */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.DOM_SUFFIX_MATCH_KEY);
            if (!TextUtils.isEmpty(eapParam) && !setEapDomainSuffixMatch(eapParam)) {
                Log.e(TAG, ssid + ": failed to set eap domain suffix match: " + eapParam);
                return false;
            }
            /** EAP CA Path*/
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.CA_PATH_KEY);
            if (!TextUtils.isEmpty(eapParam) && !setEapCAPath(eapParam)) {
                Log.e(TAG, ssid + ": failed to set eap ca path: " + eapParam);
                return false;
            }
            /** EAP Proactive Key Caching */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.OPP_KEY_CACHING);
            if (!TextUtils.isEmpty(eapParam)
                    && !setEapProactiveKeyCaching(eapParam.equals("1") ? true : false)) {
                Log.e(TAG, ssid + ": failed to set proactive key caching: " + eapParam);
                return false;
            }
            /** OCSP (Online Certificate Status Protocol) */
            if (!setOcsp(eapConfig.getOcsp())) {
                Log.e(TAG, "failed to set ocsp");
                return false;
            }
            /** EAP ERP */
            eapParam = eapConfig.getFieldValue(WifiEnterpriseConfig.EAP_ERP);
            if (!TextUtils.isEmpty(eapParam) && eapParam.equals("1")) {
                if (!setEapErp(true)) {
                    Log.e(TAG, ssid + ": failed to set eap erp");
                    return false;
                }
            }
            if (isServiceVersionIsAtLeast(2)) {
                if (!setMinimumTlsVersionEapPhase1Param(getOptimalMinimumTlsVersion(eapConfig))) {
                    Log.e(TAG, "Failed to set the minimum TLS version");
                    return false;
                }
            }
            return true;
        }
    }

    private int getOptimalMinimumTlsVersion(WifiEnterpriseConfig enterpriseConfig) {
        int maxTlsVersionSupported = WifiEnterpriseConfig.TLS_V1_2;
        if ((mWpaDriverFeatures & WifiManager.WIFI_FEATURE_TLS_V1_3) != 0) {
            maxTlsVersionSupported = WifiEnterpriseConfig.TLS_V1_3;
        }

        int requiredMinimumTlsVersion = enterpriseConfig.getMinimumTlsVersion();
        if (requiredMinimumTlsVersion > maxTlsVersionSupported) {
            Log.w(TAG, "The required minimum TLS version " + requiredMinimumTlsVersion
                    + " exceeds the maximum supported TLS version " + maxTlsVersionSupported
                    + ", fallback to the maximum supported TLS version.");
            return maxTlsVersionSupported;
        }
        return requiredMinimumTlsVersion;
    }

    /**
     * Maps WifiConfiguration Key Management BitSet to Supplicant AIDL bitmask int
     *
     * @return bitmask int describing the allowed Key Management schemes, readable by the Supplicant
     * AIDL hal
     */
    private static int wifiConfigurationToSupplicantKeyMgmtMask(BitSet keyMgmt) {
        int mask = 0;
        for (int bit = keyMgmt.nextSetBit(0); bit != -1;
                bit = keyMgmt.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiConfiguration.KeyMgmt.NONE:
                    mask |= KeyMgmtMask.NONE;
                    break;
                case WifiConfiguration.KeyMgmt.WPA_PSK:
                    mask |= KeyMgmtMask.WPA_PSK;
                    break;
                case WifiConfiguration.KeyMgmt.WPA_EAP:
                    mask |= KeyMgmtMask.WPA_EAP;
                    break;
                case WifiConfiguration.KeyMgmt.IEEE8021X:
                    mask |= KeyMgmtMask.IEEE8021X;
                    break;
                case WifiConfiguration.KeyMgmt.OSEN:
                    mask |= KeyMgmtMask.OSEN;
                    break;
                case WifiConfiguration.KeyMgmt.FT_PSK:
                    mask |= KeyMgmtMask.FT_PSK;
                    break;
                case WifiConfiguration.KeyMgmt.FT_EAP:
                    mask |= KeyMgmtMask.FT_EAP;
                    break;
                case WifiConfiguration.KeyMgmt.OWE:
                    mask |= KeyMgmtMask.OWE;
                    break;
                case WifiConfiguration.KeyMgmt.SAE:
                    mask |= KeyMgmtMask.SAE;
                    break;
                case WifiConfiguration.KeyMgmt.SUITE_B_192:
                    mask |= KeyMgmtMask.SUITE_B_192;
                    break;
                case WifiConfiguration.KeyMgmt.WPA_PSK_SHA256:
                    mask |= KeyMgmtMask.WPA_PSK_SHA256;
                    break;
                case WifiConfiguration.KeyMgmt.WPA_EAP_SHA256:
                    mask |= KeyMgmtMask.WPA_EAP_SHA256;
                    break;
                case WifiConfiguration.KeyMgmt.WAPI_PSK:
                    mask |= KeyMgmtMask.WAPI_PSK;
                    break;
                case WifiConfiguration.KeyMgmt.WAPI_CERT:
                    mask |= KeyMgmtMask.WAPI_CERT;
                    break;
                case WifiConfiguration.KeyMgmt.FILS_SHA256:
                    mask |= KeyMgmtMask.FILS_SHA256;
                    break;
                case WifiConfiguration.KeyMgmt.FILS_SHA384:
                    mask |= KeyMgmtMask.FILS_SHA384;
                    break;
                case WifiConfiguration.KeyMgmt.DPP:
                    mask |= KeyMgmtMask.DPP;
                    break;
                case WifiConfiguration.KeyMgmt.WPA2_PSK: // This should never happen
                default:
                    throw new IllegalArgumentException(
                            "Invalid protoMask bit in keyMgmt: " + bit);
            }
        }
        return mask;
    }

    private static int wifiConfigurationToSupplicantProtoMask(BitSet protoMask,
            WifiGlobals wifiGlobals, boolean isEnterprise) {
        int mask = 0;
        for (int bit = protoMask.nextSetBit(0); bit != -1;
                bit = protoMask.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiConfiguration.Protocol.WPA:
                    if (isEnterprise || !wifiGlobals.isWpaPersonalDeprecated()) {
                        mask |= ProtoMask.WPA;
                    }
                    break;
                case WifiConfiguration.Protocol.RSN:
                    mask |= ProtoMask.RSN;
                    break;
                case WifiConfiguration.Protocol.OSEN:
                    mask |= ProtoMask.OSEN;
                    break;
                case WifiConfiguration.Protocol.WAPI:
                    mask |= ProtoMask.WAPI;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid protoMask bit in wificonfig: " + bit);
            }
        }
        return mask;
    }

    private static int wifiConfigurationToSupplicantAuthAlgMask(BitSet authAlgMask) {
        int mask = 0;
        for (int bit = authAlgMask.nextSetBit(0); bit != -1;
                bit = authAlgMask.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiConfiguration.AuthAlgorithm.OPEN:
                    mask |= AuthAlgMask.OPEN;
                    break;
                case WifiConfiguration.AuthAlgorithm.SHARED:
                    mask |= AuthAlgMask.SHARED;
                    break;
                case WifiConfiguration.AuthAlgorithm.LEAP:
                    mask |= AuthAlgMask.LEAP;
                    break;
                case WifiConfiguration.AuthAlgorithm.SAE:
                    mask |= AuthAlgMask.SAE;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid authAlgMask bit in wificonfig: " + bit);
            }
        }
        return mask;
    }

    private int wifiConfigurationToSupplicantGroupCipherMask(BitSet groupCipherMask) {
        synchronized (mLock) {
            int mask = 0;
            for (int bit = groupCipherMask.nextSetBit(0); bit != -1; bit =
                    groupCipherMask.nextSetBit(bit + 1)) {
                switch (bit) {
                    case WifiConfiguration.GroupCipher.WEP40:
                        mask |= GroupCipherMask.WEP40;
                        break;
                    case WifiConfiguration.GroupCipher.WEP104:
                        mask |= GroupCipherMask.WEP104;
                        break;
                    case WifiConfiguration.GroupCipher.TKIP:
                        mask |= GroupCipherMask.TKIP;
                        break;
                    case WifiConfiguration.GroupCipher.CCMP:
                        mask |= GroupCipherMask.CCMP;
                        break;
                    case WifiConfiguration.GroupCipher.GTK_NOT_USED:
                        mask |= GroupCipherMask.GTK_NOT_USED;
                        break;
                    case WifiConfiguration.GroupCipher.GCMP_256:
                        if (0 == (mAdvanceKeyMgmtFeatures
                                & WifiManager.WIFI_FEATURE_WPA3_SUITE_B)) {
                            Log.d(TAG, "Ignore unsupported GCMP_256 cipher.");
                            break;
                        }
                        mask |= GroupCipherMask.GCMP_256;
                        break;
                    case WifiConfiguration.GroupCipher.SMS4:
                        mask |= GroupCipherMask.SMS4;
                        break;
                    case WifiConfiguration.GroupCipher.GCMP_128:
                        mask |= GroupCipherMask.GCMP_128;
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Invalid GroupCipherMask bit in wificonfig: " + bit);
                }
            }
            return mask;
        }
    }

    private static int wifiConfigurationToSupplicantGroupMgmtCipherMask(BitSet
            groupMgmtCipherMask) {
        int mask = 0;

        for (int bit = groupMgmtCipherMask.nextSetBit(0); bit != -1; bit =
                groupMgmtCipherMask.nextSetBit(bit + 1)) {
            switch (bit) {
                case WifiConfiguration.GroupMgmtCipher.BIP_CMAC_256:
                    mask |= GroupMgmtCipherMask.BIP_CMAC_256;
                    break;
                case WifiConfiguration.GroupMgmtCipher.BIP_GMAC_128:
                    mask |= GroupMgmtCipherMask.BIP_GMAC_128;
                    break;
                case WifiConfiguration.GroupMgmtCipher.BIP_GMAC_256:
                    mask |= GroupMgmtCipherMask.BIP_GMAC_256;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid GroupMgmtCipherMask bit in wificonfig: " + bit);
            }
        }
        return mask;
    }

    private int wifiConfigurationToSupplicantPairwiseCipherMask(BitSet pairwiseCipherMask) {
        synchronized (mLock) {
            int mask = 0;
            for (int bit = pairwiseCipherMask.nextSetBit(0); bit != -1;
                    bit = pairwiseCipherMask.nextSetBit(bit + 1)) {
                switch (bit) {
                    case WifiConfiguration.PairwiseCipher.NONE:
                        mask |= PairwiseCipherMask.NONE;
                        break;
                    case WifiConfiguration.PairwiseCipher.TKIP:
                        mask |= PairwiseCipherMask.TKIP;
                        break;
                    case WifiConfiguration.PairwiseCipher.CCMP:
                        mask |= PairwiseCipherMask.CCMP;
                        break;
                    case WifiConfiguration.PairwiseCipher.GCMP_256:
                        if (0 == (mAdvanceKeyMgmtFeatures
                                & WifiManager.WIFI_FEATURE_WPA3_SUITE_B)) {
                            Log.d(TAG, "Ignore unsupporting GCMP_256 cipher.");
                            break;
                        }
                        mask |= PairwiseCipherMask.GCMP_256;
                        break;
                    case WifiConfiguration.PairwiseCipher.SMS4:
                        mask |= PairwiseCipherMask.SMS4;
                        break;
                    case WifiConfiguration.PairwiseCipher.GCMP_128:
                        mask |= PairwiseCipherMask.GCMP_128;
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Invalid pairwiseCipherMask bit in wificonfig: " + bit);
                }
            }
            return mask;
        }
    }

    private static int supplicantToWifiConfigurationEapMethod(int value) {
        switch (value) {
            case EapMethod.PEAP:
                return WifiEnterpriseConfig.Eap.PEAP;
            case EapMethod.TLS:
                return WifiEnterpriseConfig.Eap.TLS;
            case EapMethod.TTLS:
                return WifiEnterpriseConfig.Eap.TTLS;
            case EapMethod.PWD:
                return WifiEnterpriseConfig.Eap.PWD;
            case EapMethod.SIM:
                return WifiEnterpriseConfig.Eap.SIM;
            case EapMethod.AKA:
                return WifiEnterpriseConfig.Eap.AKA;
            case EapMethod.AKA_PRIME:
                return WifiEnterpriseConfig.Eap.AKA_PRIME;
            case EapMethod.WFA_UNAUTH_TLS:
                return WifiEnterpriseConfig.Eap.UNAUTH_TLS;
            // WifiEnterpriseConfig.Eap.NONE:
            default:
                Log.e(TAG, "invalid eap method value from supplicant: " + value);
                return -1;
        }
    }

    private static int supplicantToWifiConfigurationEapPhase2Method(int value) {
        switch (value) {
            case EapPhase2Method.NONE:
                return WifiEnterpriseConfig.Phase2.NONE;
            case EapPhase2Method.PAP:
                return WifiEnterpriseConfig.Phase2.PAP;
            case EapPhase2Method.MSPAP:
                return WifiEnterpriseConfig.Phase2.MSCHAP;
            case EapPhase2Method.MSPAPV2:
                return WifiEnterpriseConfig.Phase2.MSCHAPV2;
            case EapPhase2Method.GTC:
                return WifiEnterpriseConfig.Phase2.GTC;
            case EapPhase2Method.SIM:
                return WifiEnterpriseConfig.Phase2.SIM;
            case EapPhase2Method.AKA:
                return WifiEnterpriseConfig.Phase2.AKA;
            case EapPhase2Method.AKA_PRIME:
                return WifiEnterpriseConfig.Phase2.AKA_PRIME;
            default:
                Log.e(TAG, "Invalid eap phase2 method value from supplicant: " + value);
                return -1;
        }
    }

    private static int wifiConfigurationToSupplicantEapMethod(int value) {
        switch (value) {
            case WifiEnterpriseConfig.Eap.PEAP:
                return EapMethod.PEAP;
            case WifiEnterpriseConfig.Eap.TLS:
                return EapMethod.TLS;
            case WifiEnterpriseConfig.Eap.TTLS:
                return EapMethod.TTLS;
            case WifiEnterpriseConfig.Eap.PWD:
                return EapMethod.PWD;
            case WifiEnterpriseConfig.Eap.SIM:
                return EapMethod.SIM;
            case WifiEnterpriseConfig.Eap.AKA:
                return EapMethod.AKA;
            case WifiEnterpriseConfig.Eap.AKA_PRIME:
                return EapMethod.AKA_PRIME;
            case WifiEnterpriseConfig.Eap.UNAUTH_TLS:
                return EapMethod.WFA_UNAUTH_TLS;
            // WifiEnterpriseConfig.Eap.NONE:
            default:
                Log.e(TAG, "Invalid eap method value from WifiConfiguration: " + value);
                return -1;
        }
    }

    private static int wifiConfigurationToSupplicantEapPhase2Method(int value) {
        switch (value) {
            case WifiEnterpriseConfig.Phase2.NONE:
                return EapPhase2Method.NONE;
            case WifiEnterpriseConfig.Phase2.PAP:
                return EapPhase2Method.PAP;
            case WifiEnterpriseConfig.Phase2.MSCHAP:
                return EapPhase2Method.MSPAP;
            case WifiEnterpriseConfig.Phase2.MSCHAPV2:
                return EapPhase2Method.MSPAPV2;
            case WifiEnterpriseConfig.Phase2.GTC:
                return EapPhase2Method.GTC;
            case WifiEnterpriseConfig.Phase2.SIM:
                return EapPhase2Method.SIM;
            case WifiEnterpriseConfig.Phase2.AKA:
                return EapPhase2Method.AKA;
            case WifiEnterpriseConfig.Phase2.AKA_PRIME:
                return EapPhase2Method.AKA_PRIME;
            default:
                Log.e(TAG, "Invalid eap phase2 method value from WifiConfiguration: " + value);
                return -1;
        }
    }

    /**
     * Retrieves the ID allocated to this network by the supplicant.
     * Result is stored in mNetworkId.
     *
     * This is not the |SSID| of the network, but an internal identifier for
     * this network used by the supplicant.
     *
     * @return true if ID was retrieved, false otherwise
     */
    private boolean getId() {
        synchronized (mLock) {
            final String methodStr = "getId";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mNetworkId = mISupplicantStaNetwork.getId();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /** Get current network id */
    public int getNetworkId() {
        synchronized (mLock) {
            if (!getId()) {
                return -1;
            }
            return mNetworkId;
        }
    }

    private boolean registerCallback(ISupplicantStaNetworkCallback callback) {
        synchronized (mLock) {
            final String methodStr = "registerCallback";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.registerCallback(callback);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private boolean registerNewCallback(int networkId, String ssid) {
        synchronized (mLock) {
            ISupplicantStaNetworkCallback callback =
                    new SupplicantStaNetworkCallbackAidlImpl(
                            SupplicantStaNetworkHalAidlImpl.this,
                            networkId, ssid, mIfaceName, mLock, mWifiMonitor);
            if (!registerCallback(callback)) {
                Log.e(TAG, "Failed to register callback.");
                return false;
            }
            mISupplicantStaNetworkCallback = callback;
            return true;
        }
    }

    /**
     * Set SSID for this network.
     *
     * @param ssid Value to set.
     *        Max length of |ParamSizeLimits.SSID_MAX_LEN_IN_BYTES|.
     * @return true if successful, false otherwise
     */
    private boolean setSsid(byte[] ssid) {
        synchronized (mLock) {
            final String methodStr = "setSsid";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setSsid(ssid);
                Log.i(TAG, "Successfully set SSID");
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Disable EHT for this network.
     *
     * @return true if successful, false otherwise
     */
    private boolean disableEht() {
        synchronized (mLock) {
            final String methodStr = "disableEht";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                if (!isServiceVersionIsAtLeast(3)) {
                    return false;
                }
                mISupplicantStaNetwork.disableEht();
                Log.i(TAG, "Successfully disabled EHT");
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set the BSSID for this network.
     *
     * @param bssidStr MAC address in "XX:XX:XX:XX:XX:XX" form or "any" to reset the mac address.
     * @return true if it succeeds, false otherwise.
     */
    public boolean setBssid(String bssidStr) {
        synchronized (mLock) {
            try {
                return setBssid(NativeUtil.macAddressToByteArray(bssidStr));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssidStr, e);
                return false;
            }
        }
    }

    private boolean setBssid(byte[/* 6 */] bssid) {
        synchronized (mLock) {
            final String methodStr = "setBssid";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setBssid(bssid);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set whether to send probe requests for this network (hidden).
     *
     * @param enable true to set, false otherwise.
     * @return true if successful, false otherwise
     */
    private boolean setScanSsid(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setScanSsid";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setScanSsid(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set key management mask for the network.
     *
     * @param keyMgmtMask value to set.
     *        Combination of |KeyMgmtMask| values.
     * @return true if successful, false otherwise
     */
    private boolean setKeyMgmt(int keyMgmtMask) {
        synchronized (mLock) {
            final String methodStr = "setKeyMgmt";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setKeyMgmt(keyMgmtMask);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set proto mask for the network.
     *
     * @param protoMask value to set.
     *        Combination of |ProtoMask| values.
     * @return true if successful, false otherwise
     */
    private boolean setProto(int protoMask) {
        synchronized (mLock) {
            final String methodStr = "setProto";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setProto(protoMask);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set auth alg mask for the network.
     *
     * @param authAlgMask value to set.
     *        Combination of |ProtoMask| values.
     * @return true if successful, false otherwise
     */
    private boolean setAuthAlg(int authAlgMask) {
        synchronized (mLock) {
            final String methodStr = "setAuthAlg";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setAuthAlg(authAlgMask);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set group cipher mask for the network.
     *
     * @param groupCipherMask value to set.
     *        Combination of |ProtoMask| values.
     * @return true if successful, false otherwise
     */
    private boolean setGroupCipher(int groupCipherMask) {
        synchronized (mLock) {
            final String methodStr = "setGroupCipher";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, String.format("setGroupCipher: 0x%x", groupCipherMask));
            }
            try {
                mISupplicantStaNetwork.setGroupCipher(groupCipherMask);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Enable TLS Suite-B in EAP Phase1
     *
     * @param enable Set to true to enable TLS Suite-B in EAP phase1
     * @return true if successful, false otherwise
     */
    private boolean enableTlsSuiteBEapPhase1Param(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setEapPhase1Params";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.enableTlsSuiteBEapPhase1Param(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP OpenSSL Suite-B-192 ciphers for WPA3-Enterprise
     *
     * @return true if successful, false otherwise
     */
    private boolean enableSuiteBEapOpenSslCiphers() {
        synchronized (mLock) {
            final String methodStr = "setEapOpenSslCiphers";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.enableSuiteBEapOpenSslCiphers();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set pairwise cipher mask for the network.
     *
     * @param pairwiseCipherMask value to set.
     *        Combination of |ProtoMask| values.
     * @return true if successful, false otherwise
     */
    private boolean setPairwiseCipher(int pairwiseCipherMask) {
        synchronized (mLock) {
            final String methodStr = "setPairwiseCipher";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, String.format("setPairwiseCipher: 0x%x", pairwiseCipherMask));
            }
            try {
                mISupplicantStaNetwork.setPairwiseCipher(pairwiseCipherMask);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set group management cipher mask for the network.
     *
     * @param groupMgmtCipherMask value to set.
     *        Combination of |GroupMgmtCipherMask| values.
     * @return true if successful, false otherwise
     */
    private boolean setGroupMgmtCipher(int groupMgmtCipherMask) {
        synchronized (mLock) {
            final String methodStr = "setGroupMgmtCipher";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setGroupMgmtCipher(groupMgmtCipherMask);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set passphrase for WPA_PSK network.
     *
     * @param psk value to set.
     *        Length of value must be between
     *        |ParamSizeLimits.PSK_PASSPHRASE_MIN_LEN_IN_BYTES| and
     *        |ParamSizeLimits.PSK_PASSPHRASE_MAX_LEN_IN_BYTES|.
     * @return true if successful, false otherwise
     */
    private boolean setPskPassphrase(String psk) {
        synchronized (mLock) {
            final String methodStr = "setPskPassphrase";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setPskPassphrase(psk);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set raw psk for WPA_PSK network.
     *
     * @param psk value to set as specified in IEEE 802.11i-2004 standard.
     *        This is the calculated using 'wpa_passphrase <ssid> [passphrase]'
     * @return true if successful, false otherwise
     */
    private boolean setPsk(byte[] psk) {
        synchronized (mLock) {
            final String methodStr = "setPsk";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setPsk(psk);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.e(TAG, "ISupplicantStaNetwork." + methodStr + " failed: " + e);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set WEP key for WEP network.
     *
     * @param keyIdx Index of wep key to set.
     *        Max of |ParamSizeLimits.WEP_KEYS_MAX_NUM|.
     * @param wepKey value to set.
     *        Length of each key must be either
     *        |ParamSizeLimits.WEP40_KEY_LEN_IN_BYTES| or
     *        |ParamSizeLimits.WEP104_KEY_LEN_IN_BYTES|.
     * @return true if successful, false otherwise
     */
    private boolean setWepKey(int keyIdx, byte[] wepKey) {
        synchronized (mLock) {
            final String methodStr = "setWepKey";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setWepKey(keyIdx, wepKey);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set default Tx key index for WEP network.
     *
     * @param keyIdx value to set.
     *        Max of |ParamSizeLimits.WEP_KEYS_MAX_NUM|.
     * @return true if successful, false otherwise
     */
    private boolean setWepTxKeyIdx(int keyIdx) {
        synchronized (mLock) {
            final String methodStr = "setWepTxKeyIdx";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setWepTxKeyIdx(keyIdx);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set whether RequirePmf is enabled for this network.
     *
     * @param enable true to set, false otherwise.
     * @return true if successful, false otherwise
     */
    private boolean setRequirePmf(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setRequirePmf";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "setRequirePmf: " + enable);
            }
            try {
                mISupplicantStaNetwork.setRequirePmf(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set PPS MO ID for this network.
     * (Hotspot 2.0 PerProviderSubscription/UpdateIdentifier)
     *
     * @param identifier ID value to set.
     * @return true if successful, false otherwise
     */
    private boolean setUpdateIdentifier(int identifier) {
        synchronized (mLock) {
            final String methodStr = "setUpdateIdentifier";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setUpdateIdentifier(identifier);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set WAPI certificate suite name for this network.
     *
     * @param certSuite value to set.
     * @return true if successful, false otherwise
     */
    private boolean setWapiCertSuite(String certSuite) {
        synchronized (mLock) {
            final String methodStr = "setWapiCertSuite";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setWapiCertSuite(certSuite);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP Method for this network.
     *
     * @param method value to be set.
     *        Must be one of |EapMethod| values.
     * @return true if successful, false otherwise
     */
    private boolean setEapMethod(int method) {
        synchronized (mLock) {
            final String methodStr = "setEapMethod";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapMethod(method);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP Phase2 Method for this network.
     *
     * EAP method needs to be set for this to work.
     *
     * @param method value to set.
     *        Must be one of |EapPhase2Method| values.
     * @return true if successful, false otherwise
     */
    private boolean setEapPhase2Method(int method) {
        synchronized (mLock) {
            final String methodStr = "setEapPhase2Method";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapPhase2Method(method);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP Identity for this network.
     *
     * @param identity value to set.
     * @return true if successful, false otherwise
     */
    private boolean setEapIdentity(byte[] identity) {
        synchronized (mLock) {
            final String methodStr = "setEapIdentity";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapIdentity(identity);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP Anonymous Identity for this network.
     *
     * @param identity value to set.
     * @return true if successful, false otherwise
     */
    public boolean setEapAnonymousIdentity(byte[] identity) {
        synchronized (mLock) {
            final String methodStr = "setEapAnonymousIdentity";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapAnonymousIdentity(identity);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP Password for this network.
     *
     * @param password value to set.
     * @return true if successful, false otherwise
     */
    private boolean setEapPassword(byte[] password) {
        synchronized (mLock) {
            final String methodStr = "setEapPassword";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapPassword(password);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP CA certificate file path for this network.
     *
     * @param path value to set.
     * @return true if successful, false otherwise
     */
    private boolean setEapCACert(String path) {
        synchronized (mLock) {
            final String methodStr = "setEapCACert";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapCACert(path);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP CA certificate directory path for this network.
     *
     * @param path value to set.
     * @return true if successful, false otherwise
     */
    private boolean setEapCAPath(String path) {
        synchronized (mLock) {
            final String methodStr = "setEapCAPath";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapCAPath(path);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP Client certificate file path for this network.
     *
     * @param path value to set.
     * @return true if successful, false otherwise
     */
    private boolean setEapClientCert(String path) {
        synchronized (mLock) {
            final String methodStr = "setEapClientCert";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapClientCert(path);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP private key Id for this network.
     * This is used if private key operations for EAP-TLS are performed
     * using a smartcard.
     *
     * @param id value to set.
     * @return true if successful, false otherwise
     */
    private boolean setEapPrivateKeyId(String id) {
        synchronized (mLock) {
            final String methodStr = "setEapPrivateKeyId";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapPrivateKeyId(id);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP subject match for this network.
     *
     * @param match value to set.
     * @return true if successful, false otherwise
     */
    private boolean setEapSubjectMatch(String match) {
        synchronized (mLock) {
            final String methodStr = "setEapSubjectMatch";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapSubjectMatch(match);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP Alt subject match for this network.
     *
     * @param match value to set.
     * @return true if successful, false otherwise
     */
    private boolean setEapAltSubjectMatch(String match) {
        synchronized (mLock) {
            final String methodStr = "setEapAltSubjectMatch";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapAltSubjectMatch(match);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Enable EAP Open SSL Engine for this network.
     *
     * @param enable true to set, false otherwise.
     * @return true if successful, false otherwise
     */
    private boolean setEapEngine(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setEapEngine";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapEngine(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP Open SSL Engine ID for this network.
     *
     * @param id value to set.
     * @return true if successful, false otherwise
     */
    private boolean setEapEngineID(String id) {
        synchronized (mLock) {
            final String methodStr = "setEapEngineID";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapEngineID(id);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set EAP Domain suffix match for this network.
     *
     * @param match value to set.
     * @return true if successful, false otherwise
     */
    private boolean setEapDomainSuffixMatch(String match) {
        synchronized (mLock) {
            final String methodStr = "setEapDomainSuffixMatch";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapDomainSuffixMatch(match);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * This field can be used to enable proactive key caching which is also
     * known as opportunistic PMKSA caching for WPA2. This is disabled (0)
     * by default unless default value is changed with the global okc=1
     * parameter.
     *
     * Proactive key caching is used to make supplicant assume that the APs
     * are using the same PMK and generate PMKSA cache entries without
     * doing RSN pre-authentication. This requires support from the AP side
     * and is normally used with wireless switches that co-locate the
     * authenticator.
     *
     * @param enable true to set, false otherwise.
     * @return true if successful, false otherwise
     */
    private boolean setEapProactiveKeyCaching(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setEapProactiveKeyCaching";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setProactiveKeyCaching(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set ID string for this network.
     * Network identifier string for external scripts.
     *
     * @param idString ID string value to set.
     * @return true if successful, false otherwise
     */
    private boolean setIdStr(String idString) {
        synchronized (mLock) {
            final String methodStr = "setIdStr";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setIdStr(idString);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set SAE password for WPA3-Personal
     *
     * @param saePassword string with the above option
     * @return true if successful, false otherwise
     */
    private boolean setSaePassword(String saePassword) {
        synchronized (mLock) {
            final String methodStr = "setSaePassword";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setSaePassword(saePassword);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Enable Extensible Authentication (EAP) - Re-authentication Protocol (ERP) for this network.
     *
     * @param enable true to set, false otherwise.
     * @return true if successful, false otherwise
     */
    private boolean setEapErp(boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setEapErp";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setEapErp(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get SSID for this network. Result is stored in mSsid.
     *
     * @return true if successful, false otherwise
     */
    private boolean getSsid() {
        synchronized (mLock) {
            final String methodStr = "getSsid";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mSsid = mISupplicantStaNetwork.getSsid();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get the BSSID set for this network. Result is stored in mBssid.
     *
     * @return true if successful, false otherwise
     */
    private boolean getBssid() {
        synchronized (mLock) {
            final String methodStr = "getBssid";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mBssid = mISupplicantStaNetwork.getBssid();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get whether Probe Requests are being sent for this network (hidden).
     * Result is stored in mScanSsid.
     *
     * @return true if successful, false otherwise
     */
    private boolean getScanSsid() {
        synchronized (mLock) {
            final String methodStr = "getScanSsid";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mScanSsid = mISupplicantStaNetwork.getScanSsid();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get the key mgmt mask set for the network. Result is stored in mKeyMgmtMask.
     *
     * @return true if successful, false otherwise
     */
    private boolean getKeyMgmt() {
        synchronized (mLock) {
            final String methodStr = "getKeyMgmt";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mKeyMgmtMask = mISupplicantStaNetwork.getKeyMgmt();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get the proto mask set for the network. Result is stored in mProtoMask.
     *
     * @return true if successful, false otherwise
     */
    private boolean getProto() {
        synchronized (mLock) {
            final String methodStr = "getProto";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mProtoMask = mISupplicantStaNetwork.getProto();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get the auth alg mask set for the network. Result is stored in mAuthAlgMask.
     *
     * @return true if successful, false otherwise
     */
    private boolean getAuthAlg() {
        synchronized (mLock) {
            final String methodStr = "getAuthAlg";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mAuthAlgMask = mISupplicantStaNetwork.getAuthAlg();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get the group cipher mask set for the network. Result is stored in mGroupCipherMask.
     *
     * @return true if successful, false otherwise
     */
    private boolean getGroupCipher() {
        synchronized (mLock) {
            final String methodStr = "getGroupCipher";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mGroupCipherMask = mISupplicantStaNetwork.getGroupCipher();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get the pairwise cipher mask set for the network. Result is stored in mPairwiseCipherMask.
     *
     * @return true if successful, false otherwise
     */
    private boolean getPairwiseCipher() {
        synchronized (mLock) {
            final String methodStr = "getPairwiseCipher";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mPairwiseCipherMask = mISupplicantStaNetwork.getPairwiseCipher();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }

    }

    /**
     * Get the group management cipher mask set for the network. Result is stored in
     * mGroupMgmtCipherMask.
     *
     * @return true if successful, false otherwise
     */
    private boolean getGroupMgmtCipher() {
        synchronized (mLock) {
            final String methodStr = "getGroupMgmtCipher";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mGroupMgmtCipherMask = mISupplicantStaNetwork.getGroupMgmtCipher();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get passphrase for WPA_PSK network. Result is stored in mPskPassphrase if retrieved.
     * Must return a failure if network has no passphrase set (use |getPsk| if
     * network was configured with raw psk instead).
     *
     * @return true if successful, false otherwise
     */
    private boolean getPskPassphrase() {
        synchronized (mLock) {
            final String methodStr = "getPskPassphrase";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mPskPassphrase = mISupplicantStaNetwork.getPskPassphrase();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get SAE password for WPA3-Personal. Result is stored in mSaePassword.
     *
     * @return true if successful, false otherwise
     */
    private boolean getSaePassword() {
        synchronized (mLock) {
            final String methodStr = "getSaePassword";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mSaePassword = mISupplicantStaNetwork.getSaePassword();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get raw psk for WPA_PSK network. Result is stored in mPsk.
     *
     * @return true if successful, false otherwise
     */
    private boolean getPsk() {
        synchronized (mLock) {
            final String methodStr = "getPsk";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mPsk = mISupplicantStaNetwork.getPsk();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get WEP key for WEP network. Result is stored in mWepKey.
     *
     * @param keyIdx Index of wep key to be fetched.
     *        Max of |WEP_KEYS_MAX_NUM|.
     * @return true if successful, false otherwise
     */
    private boolean getWepKey(int keyIdx) {
        synchronized (mLock) {
            final String methodStr = "keyIdx";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mWepKey = mISupplicantStaNetwork.getWepKey(keyIdx);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get default Tx key index for WEP network. Result is stored in mWepTxKeyIdx.
     *
     * @return true if successful, false otherwise
     */
    private boolean getWepTxKeyIdx() {
        synchronized (mLock) {
            final String methodStr = "getWepTxKeyIdx";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mWepTxKeyIdx = mISupplicantStaNetwork.getWepTxKeyIdx();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get whether RequirePmf is enabled for this network. Result is stored in mWepTxKeyIdx.
     *
     * @return true if successful, false otherwise
     */
    private boolean getRequirePmf() {
        synchronized (mLock) {
            final String methodStr = "getRequirePmf";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mRequirePmf = mISupplicantStaNetwork.getRequirePmf();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get WAPI certificate suite name set for this network. Result is stored in mWapiCertSuite.
     *
     * @return true if successful, false otherwise
     */
    private boolean getWapiCertSuite() {
        synchronized (mLock) {
            final String methodStr = "getWapiCertSuite";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mWapiCertSuite = mISupplicantStaNetwork.getWapiCertSuite();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP Method set for this network. Result is stored in mEapMethod.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapMethod() {
        synchronized (mLock) {
            final String methodStr = "getEapMethod";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapMethod = mISupplicantStaNetwork.getEapMethod();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP Phase2 Method set for this network. Result is stored in mEapPhase2Method.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapPhase2Method() {
        synchronized (mLock) {
            final String methodStr = "getEapPhase2Method";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapPhase2Method = mISupplicantStaNetwork.getEapPhase2Method();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP Identity set for this network. Result is stored in mEapIdentity.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapIdentity() {
        synchronized (mLock) {
            final String methodStr = "getEapIdentity";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapIdentity = mISupplicantStaNetwork.getEapIdentity();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP Anonymous Identity set for this network. Result is stored in mEapAnonymousIdentity.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapAnonymousIdentity() {
        synchronized (mLock) {
            final String methodStr = "getEapAnonymousIdentity";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapAnonymousIdentity = mISupplicantStaNetwork.getEapAnonymousIdentity();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Wrapper method for getEapAnonymousIdentity(). Gets the anonymous identity
     * from supplicant and returns it as a string.
     *
     * @return anonymous identity string if successful, null otherwise.
     */
    public String fetchEapAnonymousIdentity() {
        synchronized (mLock) {
            if (!getEapAnonymousIdentity()) {
                return null;
            }
            return NativeUtil.stringFromByteArray(mEapAnonymousIdentity);
        }
    }

    /**
     * Get EAP Password set for this network. Result is stored in mEapPassword.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapPassword() {
        synchronized (mLock) {
            final String methodStr = "getEapPassword";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapPassword = mISupplicantStaNetwork.getEapPassword();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP CA certificate file path set for this network. Result is stored in mEapCACert.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapCACert() {
        synchronized (mLock) {
            final String methodStr = "getEapCACert";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapCACert = mISupplicantStaNetwork.getEapCACert();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP CA certificate directory path set for this network. Result is stored in mEapCAPath.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapCAPath() {
        synchronized (mLock) {
            final String methodStr = "getEapCAPath";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapCAPath = mISupplicantStaNetwork.getEapCAPath();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP Client certificate file path set for this network.
     * Result is stored in mEapClientCert.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapClientCert() {
        synchronized (mLock) {
            final String methodStr = "getEapClientCert";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapClientCert = mISupplicantStaNetwork.getEapClientCert();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP private key Id set for this network. Result is stored in mEapPrivateKeyId.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapPrivateKeyId() {
        synchronized (mLock) {
            final String methodStr = "getEapPrivateKeyId";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapPrivateKeyId = mISupplicantStaNetwork.getEapPrivateKeyId();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP subject match set for this network. Result is stored in mEapSubjectMatch.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapSubjectMatch() {
        synchronized (mLock) {
            final String methodStr = "getEapSubjectMatch";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapSubjectMatch = mISupplicantStaNetwork.getEapSubjectMatch();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP Alt subject match set for this network. Result is stored in mEapAltSubjectMatch.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapAltSubjectMatch() {
        synchronized (mLock) {
            final String methodStr = "getEapAltSubjectMatch";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapAltSubjectMatch = mISupplicantStaNetwork.getEapAltSubjectMatch();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get whether EAP Open SSL Engine is enabled for this network. Result is stored in mEapEngine.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapEngine() {
        synchronized (mLock) {
            final String methodStr = "getEapEngine";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapEngine = mISupplicantStaNetwork.getEapEngine();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP Open SSL Engine ID set for this network. Result is stored in mEapEngineID.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapEngineId() {
        synchronized (mLock) {
            final String methodStr = "getEapEngineId";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapEngineID = mISupplicantStaNetwork.getEapEngineId();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get EAP Domain suffix match set for this network. Result is stored in mEapDomainSuffixMatch.
     *
     * @return true if successful, false otherwise
     */
    private boolean getEapDomainSuffixMatch() {
        synchronized (mLock) {
            final String methodStr = "getEapDomainSuffixMatch";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mEapDomainSuffixMatch = mISupplicantStaNetwork.getEapDomainSuffixMatch();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get ID string set for this network. Network identifier string for external scripts.
     * Result is stored in mIdStr.
     *
     * @return true if successful, false otherwise
     */
    private boolean getIdStr() {
        synchronized (mLock) {
            final String methodStr = "getIdStr";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mIdStr = mISupplicantStaNetwork.getIdStr();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Enable the network for connection purposes.
     *
     * This must trigger a connection to the network if:
     * a) |noConnect| is false, and
     * b) This is the only network configured, and
     * c) Is visible in the current scan results.
     *
     * @param noConnect Only enable the network, don't trigger a connect.
     * @return true if successful, false otherwise
     */
    public boolean enable(boolean noConnect) {
        synchronized (mLock) {
            final String methodStr = "enable";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.enable(noConnect);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Disable the network for connection purposes.
     * This must trigger a disconnection from the network, if currently
     * connected to this one.
     *
     * @return true if successful, false otherwise
     */
    public boolean disable() {
        synchronized (mLock) {
            final String methodStr = "disable";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.disable();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Trigger a connection to this network.
     *
     * @return true if it succeeds, false otherwise.
     */
    public boolean select() {
        synchronized (mLock) {
            final String methodStr = "select";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.select();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Send GSM auth response.
     *
     * @param paramsStr Response params as a string.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendNetworkEapSimGsmAuthResponse(String paramsStr) {
        synchronized (mLock) {
            try {
                Matcher match = GSM_AUTH_RESPONSE_PARAMS_PATTERN.matcher(paramsStr);
                ArrayList<NetworkResponseEapSimGsmAuthParams> params = new ArrayList<>();
                while (match.find()) {
                    if (match.groupCount() != 2) {
                        Log.e(TAG, "Malformed gsm auth response params: " + paramsStr);
                        return false;
                    }
                    NetworkResponseEapSimGsmAuthParams param =
                            new NetworkResponseEapSimGsmAuthParams();
                    param.kc = new byte[8];
                    param.sres = new byte[4];
                    byte[] kc = NativeUtil.hexStringToByteArray(match.group(1));
                    if (kc == null || kc.length != param.kc.length) {
                        Log.e(TAG, "Invalid kc value: " + match.group(1));
                        return false;
                    }
                    byte[] sres = NativeUtil.hexStringToByteArray(match.group(2));
                    if (sres == null || sres.length != param.sres.length) {
                        Log.e(TAG, "Invalid sres value: " + match.group(2));
                        return false;
                    }
                    System.arraycopy(kc, 0, param.kc, 0, param.kc.length);
                    System.arraycopy(sres, 0, param.sres, 0, param.sres.length);
                    params.add(param);
                }
                // The number of kc/sres pairs can either be 2 or 3 depending on the request.
                if (params.size() > 3 || params.size() < 2) {
                    Log.e(TAG, "Malformed gsm auth response params: " + paramsStr);
                    return false;
                }
                NetworkResponseEapSimGsmAuthParams[] paramsArr =
                        new NetworkResponseEapSimGsmAuthParams[params.size()];
                for (int i = 0; i < params.size(); i++) {
                    paramsArr[i] = params.get(i);
                }
                return sendNetworkEapSimGsmAuthResponse(paramsArr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + paramsStr, e);
                return false;
            }
        }
    }

    private boolean sendNetworkEapSimGsmAuthResponse(
            NetworkResponseEapSimGsmAuthParams[] params) {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapSimGsmAuthResponse";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.sendNetworkEapSimGsmAuthResponse(params);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Send GSM auth failure.
     *
     * @return true if successful, false otherwise
     */
    public boolean sendNetworkEapSimGsmAuthFailure() {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapSimGsmAuthFailure";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.sendNetworkEapSimGsmAuthFailure();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Send UMTS auth response.
     *
     * @param paramsStr Response params as a string.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendNetworkEapSimUmtsAuthResponse(String paramsStr) {
        synchronized (mLock) {
            try {
                Matcher match = UMTS_AUTH_RESPONSE_PARAMS_PATTERN.matcher(paramsStr);
                if (!match.find() || match.groupCount() != 3) {
                    Log.e(TAG, "Malformed umts auth response params: " + paramsStr);
                    return false;
                }
                NetworkResponseEapSimUmtsAuthParams params =
                        new NetworkResponseEapSimUmtsAuthParams();
                params.ik = new byte[16];
                params.ck = new byte[16];
                byte[] ik = NativeUtil.hexStringToByteArray(match.group(1));
                if (ik == null || ik.length != params.ik.length) {
                    Log.e(TAG, "Invalid ik value: " + match.group(1));
                    return false;
                }
                byte[] ck = NativeUtil.hexStringToByteArray(match.group(2));
                if (ck == null || ck.length != params.ck.length) {
                    Log.e(TAG, "Invalid ck value: " + match.group(2));
                    return false;
                }
                byte[] res = NativeUtil.hexStringToByteArray(match.group(3));
                if (res == null || res.length == 0) {
                    Log.e(TAG, "Invalid res value: " + match.group(3));
                    return false;
                }
                params.res = new byte[res.length];
                System.arraycopy(ik, 0, params.ik, 0, params.ik.length);
                System.arraycopy(ck, 0, params.ck, 0, params.ck.length);
                System.arraycopy(res, 0, params.res, 0, params.res.length);
                return sendNetworkEapSimUmtsAuthResponse(params);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + paramsStr, e);
                return false;
            }
        }
    }

    private boolean sendNetworkEapSimUmtsAuthResponse(
            NetworkResponseEapSimUmtsAuthParams params) {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapSimUmtsAuthResponse";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.sendNetworkEapSimUmtsAuthResponse(params);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Send UMTS auts response.
     *
     * @param paramsStr Response params as a string.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendNetworkEapSimUmtsAutsResponse(String paramsStr) {
        synchronized (mLock) {
            try {
                Matcher match = UMTS_AUTS_RESPONSE_PARAMS_PATTERN.matcher(paramsStr);
                if (!match.find() || match.groupCount() != 1) {
                    Log.e(TAG, "Malformed umts auts response params: " + paramsStr);
                    return false;
                }
                byte[] auts = NativeUtil.hexStringToByteArray(match.group(1));
                if (auts == null || auts.length != 14) {
                    Log.e(TAG, "Invalid auts value: " + match.group(1));
                    return false;
                }
                return sendNetworkEapSimUmtsAutsResponse(auts);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + paramsStr, e);
                return false;
            }
        }
    }

    private boolean sendNetworkEapSimUmtsAutsResponse(byte[/* 14 */] auts) {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapSimUmtsAutsResponse";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.sendNetworkEapSimUmtsAutsResponse(auts);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Send UMTS auth failure.
     *
     * @return true if successful, false otherwise
     */
    public boolean sendNetworkEapSimUmtsAuthFailure() {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapSimUmtsAuthFailure";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.sendNetworkEapSimUmtsAuthFailure();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Send eap identity response.
     *
     * @param identityStr identity used for EAP-Identity
     * @param encryptedIdentityStr encrypted identity used for EAP-AKA/EAP-SIM
     * @return true if succeeds, false otherwise.
     */
    public boolean sendNetworkEapIdentityResponse(String identityStr,
            String encryptedIdentityStr) {
        synchronized (mLock) {
            try {
                byte[] unencryptedIdentity = NativeUtil.stringToByteArray(identityStr);
                byte[] encryptedIdentity = new byte[0];
                if (!TextUtils.isEmpty(encryptedIdentityStr)) {
                    encryptedIdentity = NativeUtil.stringToByteArray(encryptedIdentityStr);
                }
                return sendNetworkEapIdentityResponse(unencryptedIdentity, encryptedIdentity);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + identityStr + "," + encryptedIdentityStr, e);
                return false;
            }
        }
    }

    private boolean sendNetworkEapIdentityResponse(byte[] unencryptedIdentity,
            byte[] encryptedIdentity) {
        synchronized (mLock) {
            final String methodStr = "sendNetworkEapIdentityResponse";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.sendNetworkEapIdentityResponse(
                        unencryptedIdentity, encryptedIdentity);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set OCSP (Online Certificate Status Protocol) type for this network.
     *
     * @param ocsp value to set.
     * @return true if successful, false otherwise
     */
    private boolean setOcsp(@WifiEnterpriseConfig.Ocsp int ocsp) {
        synchronized (mLock) {
            final String methodStr = "setOcsp";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }

            int halOcspValue = OcspType.NONE;
            switch (ocsp) {
                case WifiEnterpriseConfig.OCSP_REQUEST_CERT_STATUS:
                    halOcspValue = OcspType.REQUEST_CERT_STATUS;
                    break;
                case WifiEnterpriseConfig.OCSP_REQUIRE_CERT_STATUS:
                    halOcspValue = OcspType.REQUIRE_CERT_STATUS;
                    break;
                case WifiEnterpriseConfig.OCSP_REQUIRE_ALL_NON_TRUSTED_CERTS_STATUS:
                    halOcspValue = OcspType.REQUIRE_ALL_CERTS_STATUS;
                    break;
            }

            try {
                mISupplicantStaNetwork.setOcsp(halOcspValue);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get OCSP (Online Certificate Status Protocol) type for this network. Value stored in mOcsp.
     *
     * @return true if successful, false otherwise
     */
    private boolean getOcsp() {
        synchronized (mLock) {
            final String methodStr = "getOcsp";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }

            try {
                int halOcspValue = mISupplicantStaNetwork.getOcsp();
                mOcsp = WifiEnterpriseConfig.OCSP_NONE;
                switch (halOcspValue) {
                    case OcspType.REQUEST_CERT_STATUS:
                        mOcsp = WifiEnterpriseConfig.OCSP_REQUEST_CERT_STATUS;
                        break;
                    case OcspType.REQUIRE_CERT_STATUS:
                        mOcsp = WifiEnterpriseConfig.OCSP_REQUIRE_CERT_STATUS;
                        break;
                    case OcspType.REQUIRE_ALL_CERTS_STATUS:
                        mOcsp = WifiEnterpriseConfig.OCSP_REQUIRE_ALL_NON_TRUSTED_CERTS_STATUS;
                        break;
                    default:
                        Log.e(TAG, "Invalid HAL OCSP value " + halOcspValue);
                        break;
                }
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Add a pairwise master key (PMK) into supplicant PMK cache.
     *
     * @param serializedEntry is serialized PMK cache entry, the content is
     *              opaque for the framework and depends on the native implementation.
     * @return true if successful, false otherwise
     */
    public boolean setPmkCache(byte[] serializedEntry) {
        synchronized (mLock) {
            final String methodStr = "setPmkCache";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setPmkCache(serializedEntry);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set SAE H2E (Hash-to-Element) mode.
     *
     * @param mode SAE H2E supporting mode.
     * @return true if successful, false otherwise
     */
    private boolean setSaeH2eMode(byte mode) {
        synchronized (mLock) {
            final String methodStr = "setSaeH2eMode";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setSaeH2eMode(mode);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Retrieve the NFC token for this network.
     *
     * @return Hex string corresponding to the NFC token or null for failure.
     */
    public String getWpsNfcConfigurationToken() {
        synchronized (mLock) {
            byte[] token = getWpsNfcConfigurationTokenInternal();
            if (token == null) {
                return null;
            }
            return NativeUtil.hexStringFromByteArray(token);
        }
    }

    private byte[] getWpsNfcConfigurationTokenInternal() {
        synchronized (mLock) {
            final String methodStr = "getWpsNfcConfigurationToken";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return null;
            }
            try {
                return mISupplicantStaNetwork.getWpsNfcConfigurationToken();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    private String getTag() {
        synchronized (mLock) {
            return TAG + "[" + mIfaceName + "]";
        }
    }

    /**
     * Helper function to log callbacks.
     */
    protected void logCallback(final String methodStr) {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "ISupplicantStaNetworkCallback." + methodStr + " received");
            }
        }
    }

    /**
     * Returns false if mISupplicantStaNetwork is null, and logs failure containing methodStr
     */
    private boolean checkStaNetworkAndLogFailure(final String methodStr) {
        synchronized (mLock) {
            if (mISupplicantStaNetwork == null) {
                Log.e(TAG, "Can't call " + methodStr + ", ISupplicantStaNetwork is null");
                return false;
            }
            return true;
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        synchronized (mLock) {
            mISupplicantStaNetwork = null;
            Log.e(TAG,
                    "ISupplicantStaNetwork." + methodStr + " failed with remote exception: ", e);
        }
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        synchronized (mLock) {
            Log.e(TAG, "ISupplicantStaNetwork." + methodStr + " failed with "
                    + "service specific exception: ", e);
        }
    }

    /**
     * Adds FT flags for networks if the device supports it.
     */
    private BitSet addFastTransitionFlags(BitSet keyManagementFlags) {
        synchronized (mLock) {
            if (!mContext.getResources().getBoolean(
                    R.bool.config_wifi_fast_bss_transition_enabled)) {
                return keyManagementFlags;
            }
            BitSet modifiedFlags = (BitSet) keyManagementFlags.clone();
            if (keyManagementFlags.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                modifiedFlags.set(WifiConfiguration.KeyMgmt.FT_PSK);
            }
            if (keyManagementFlags.get(WifiConfiguration.KeyMgmt.WPA_EAP)) {
                modifiedFlags.set(WifiConfiguration.KeyMgmt.FT_EAP);
            }
            return modifiedFlags;
        }
    }

    /**
     * Removes FT flags for networks if the device supports it.
     */
    private BitSet removeFastTransitionFlags(BitSet keyManagementFlags) {
        synchronized (mLock) {
            BitSet modifiedFlags = (BitSet) keyManagementFlags.clone();
            modifiedFlags.clear(WifiConfiguration.KeyMgmt.FT_PSK);
            modifiedFlags.clear(WifiConfiguration.KeyMgmt.FT_EAP);
            return modifiedFlags;
        }
    }

    /**
     * Adds SHA256 key management flags for networks.
     */
    private BitSet addSha256KeyMgmtFlags(BitSet keyManagementFlags) {
        synchronized (mLock) {
            BitSet modifiedFlags = (BitSet) keyManagementFlags.clone();
            if (keyManagementFlags.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                modifiedFlags.set(WifiConfiguration.KeyMgmt.WPA_PSK_SHA256);
            }
            if (keyManagementFlags.get(WifiConfiguration.KeyMgmt.WPA_EAP)) {
                modifiedFlags.set(WifiConfiguration.KeyMgmt.WPA_EAP_SHA256);
            }
            return modifiedFlags;
        }
    }

    /**
     * Removes SHA256 key management flags for networks.
     */
    private BitSet removeSha256KeyMgmtFlags(BitSet keyManagementFlags) {
        synchronized (mLock) {
            BitSet modifiedFlags = (BitSet) keyManagementFlags.clone();
            modifiedFlags.clear(WifiConfiguration.KeyMgmt.WPA_PSK_SHA256);
            modifiedFlags.clear(WifiConfiguration.KeyMgmt.WPA_EAP_SHA256);
            return modifiedFlags;
        }
    }

    /**
     * Adds both PSK and SAE AKM if auto-upgrade offload is supported.
     */
    private BitSet addPskSaeUpgradableTypeFlagsIfSupported(
            WifiConfiguration config, BitSet keyManagementFlags) {
        synchronized (mLock) {
            if (!config.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK)
                    || !config.getSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK).isEnabled()
                    || !mWifiGlobals.isWpa3SaeUpgradeOffloadEnabled()) {
                return keyManagementFlags;
            }

            BitSet modifiedFlags = (BitSet) keyManagementFlags.clone();
            modifiedFlags.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            modifiedFlags.set(WifiConfiguration.KeyMgmt.SAE);
            return modifiedFlags;
        }
    }

    /**
     * Removes SAE AKM when PSK and SAE AKM are both set, it only happens when
     * auto-upgrade offload is supported.
     */
    private BitSet removePskSaeUpgradableTypeFlags(BitSet keyManagementFlags) {
        if (!keyManagementFlags.get(WifiConfiguration.KeyMgmt.WPA_PSK)
                || !keyManagementFlags.get(WifiConfiguration.KeyMgmt.SAE)) {
            return keyManagementFlags;
        }
        BitSet modifiedFlags = (BitSet) keyManagementFlags.clone();
        modifiedFlags.clear(WifiConfiguration.KeyMgmt.SAE);
        return modifiedFlags;
    }

    /**
     * Creates the JSON encoded network extra using the map of string key, value pairs.
     */
    public static String createNetworkExtra(Map<String, String> values) {
        final String encoded;
        try {
            encoded = URLEncoder.encode(new JSONObject(values).toString(), "UTF-8");
        } catch (NullPointerException e) {
            Log.e(TAG, "Unable to serialize networkExtra: " + e.toString());
            return null;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Unable to serialize networkExtra: " + e.toString());
            return null;
        }
        return encoded;
    }

    /**
     * Parse the network extra JSON encoded string to a map of string key, value pairs.
     */
    public static Map<String, String> parseNetworkExtra(String encoded) {
        if (TextUtils.isEmpty(encoded)) {
            return null;
        }
        try {
            // This method reads a JSON dictionary that was written by setNetworkExtra(). However,
            // on devices that upgraded from Marshmallow, it may encounter a legacy value instead -
            // an FQDN stored as a plain string. If such a value is encountered, the JSONObject
            // constructor will thrown a JSONException and the method will return null.
            final JSONObject json = new JSONObject(URLDecoder.decode(encoded, "UTF-8"));
            final Map<String, String> values = new HashMap<>();
            final Iterator<?> it = json.keys();
            while (it.hasNext()) {
                final String key = (String) it.next();
                final Object value = json.get(key);
                if (value instanceof String) {
                    values.put(key, (String) value);
                }
            }
            return values;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Unable to deserialize networkExtra: " + e.toString());
            return null;
        } catch (JSONException e) {
            // This is not necessarily an error. This exception will also occur if we encounter a
            // legacy FQDN stored as a plain string. We want to return null in this case as no JSON
            // dictionary of extras was found.
            return null;
        }
    }

    /**
     * Returns a big-endian representation of {@code rcoi} in an 3 or 5-element byte array.
     */
    private static byte[] rcoiToByteArray(long rcoi) {
        // An RCOI is either 3- or 5-octet array, IEEE Std 802.11, section 9.4.1.31: Organization
        // Identifier field
        int arraySize = 3;
        rcoi &= 0xffffffffffL;
        if ((rcoi & 0xffff000000L) != 0) {
            // This is a 5-octet RCOI
            arraySize = 5;
        }

        byte[] result = new byte[arraySize];
        for (int i = arraySize - 1; i >= 0; i--) {
            result[i] = (byte) (rcoi & 0xffL);
            rcoi >>= 8;
        }
        return result;
    }

    /**
     * Set the selected RCOI for this Passpoint network.
     *
     * @param selectedRcoi value to set.
     * @return true if successful, false otherwise
     */
    private boolean setSelectedRcoi(long selectedRcoi) {
        if (selectedRcoi == 0) {
            // Nothing to set
            return true;
        }
        synchronized (mLock) {
            final String methodStr = "setSelectedRcoi";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }

            try {
                mISupplicantStaNetwork
                        .setRoamingConsortiumSelection(rcoiToByteArray(selectedRcoi));
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private int frameworkToAidlTlsVersion(@WifiEnterpriseConfig.TlsVersion int tlsVersion) {
        switch (tlsVersion) {
            case WifiEnterpriseConfig.TLS_V1_3:
                return TlsVersion.TLS_V1_3;
            case WifiEnterpriseConfig.TLS_V1_2:
                return TlsVersion.TLS_V1_2;
            case WifiEnterpriseConfig.TLS_V1_1:
                return TlsVersion.TLS_V1_1;
            case WifiEnterpriseConfig.TLS_V1_0:
                return TlsVersion.TLS_V1_0;
            default:
                Log.e(TAG, "Invalid TLS version: " + tlsVersion);
                return -1;
        }
    }

    /**
     * Enable TLS V1.3 in EAP Phase1
     *
     * @param tlsVersion the TLS version
     * @return true if successful, false otherwise
     */
    private boolean setMinimumTlsVersionEapPhase1Param(
            @WifiEnterpriseConfig.TlsVersion int tlsVersion) {
        synchronized (mLock) {
            final String methodStr = "setMinimumTlsVersionEapPhase1Param";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            int aidlTlsVersion = frameworkToAidlTlsVersion(tlsVersion);
            if (aidlTlsVersion < 0) {
                return false;
            }
            try {
                mISupplicantStaNetwork.setMinimumTlsVersionEapPhase1Param(aidlTlsVersion);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private boolean enableStrictConservativePeerMode() {
        synchronized (mLock) {
            final String methodStr = "setStrictConservativePeerMode";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            // check AIDL version
            try {
                if (mISupplicantStaNetwork.getInterfaceVersion() < 2) {
                    return false;
                }
                mISupplicantStaNetwork.setStrictConservativePeerMode(true);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    private boolean setVendorData(@NonNull List<OuiKeyedData> vendorData) {
        synchronized (mLock) {
            final String methodStr = "setVendorData";
            if (!checkStaNetworkAndLogFailure(methodStr)) {
                return false;
            }
            try {
                if (!isServiceVersionIsAtLeast(3)) {
                    return false;
                }
                if (vendorData == null || vendorData.isEmpty()) {
                    return false;
                }
                mISupplicantStaNetwork.setVendorData(
                        HalAidlUtil.frameworkToHalOuiKeyedDataList(vendorData));
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }
}
