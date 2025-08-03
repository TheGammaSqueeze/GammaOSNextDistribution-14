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
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.os.Handler;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.util.HexDump;
import com.android.server.wifi.util.CertificateSubjectInfo;
import com.android.wifi.resources.R;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Set;
import java.util.StringJoiner;

/** This class is used to handle insecure EAP networks. */
public class InsecureEapNetworkHandler {
    private static final String TAG = "InsecureEapNetworkHandler";

    @VisibleForTesting
    static final String ACTION_CERT_NOTIF_TAP =
            "com.android.server.wifi.ClientModeImpl.ACTION_CERT_NOTIF_TAP";
    @VisibleForTesting
    static final String ACTION_CERT_NOTIF_ACCEPT =
            "com.android.server.wifi.ClientModeImpl.ACTION_CERT_NOTIF_ACCEPT";
    @VisibleForTesting
    static final String ACTION_CERT_NOTIF_REJECT =
            "com.android.server.wifi.ClientModeImpl.ACTION_CERT_NOTIF_REJECT";
    @VisibleForTesting
    static final String EXTRA_PENDING_CERT_SSID =
            "com.android.server.wifi.ClientModeImpl.EXTRA_PENDING_CERT_SSID";

    static final String TOFU_ANONYMOUS_IDENTITY = "anonymous";
    private final String mCaCertHelpLink;
    private final WifiContext mContext;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiNative mWifiNative;
    private final FrameworkFacade mFacade;
    private final WifiNotificationManager mNotificationManager;
    private final WifiDialogManager mWifiDialogManager;
    private final boolean mIsTrustOnFirstUseSupported;
    private final boolean mIsInsecureEnterpriseConfigurationAllowed;
    private final InsecureEapNetworkHandlerCallbacks mCallbacks;
    private final String mInterfaceName;
    private final Handler mHandler;
    private final OnNetworkUpdateListener mOnNetworkUpdateListener;

    // The latest connecting configuration from the caller, it is updated on calling
    // prepareConnection() always. This is used to ensure that current TOFU config is aligned
    // with the caller connecting config.
    @NonNull
    private WifiConfiguration mConnectingConfig = null;
    // The connecting configuration which is a valid TOFU configuration, it is updated
    // only when the connecting configuration is a valid TOFU configuration and used
    // by later TOFU procedure.
    @NonNull
    private WifiConfiguration mCurrentTofuConfig = null;
    private int mPendingRootCaCertDepth = -1;
    @Nullable
    private X509Certificate mPendingRootCaCert = null;
    @Nullable
    private X509Certificate mPendingServerCert = null;
    // This is updated on setting a pending server cert.
    private CertificateSubjectInfo mPendingServerCertSubjectInfo = null;
    // This is updated on setting a pending server cert.
    private CertificateSubjectInfo mPendingServerCertIssuerInfo = null;
    // Record the whole server cert chain from Root CA to the server cert.
    // The order of the certificates in the chain required by the validation method is in the
    // reverse order to the order we receive them from the lower layers. Therefore, we are using a
    // LinkedList data type here, so that we could add certificates to the head, rather than
    // using an ArrayList and then having to reverse it.
    // Using SuppressLint here to avoid linter errors related to LinkedList usage.
    @SuppressLint("JdkObsolete")
    private LinkedList<X509Certificate> mServerCertChain = new LinkedList<>();
    private WifiDialogManager.DialogHandle mTofuAlertDialog = null;
    private boolean mIsCertNotificationReceiverRegistered = false;
    private String mServerCertHash = null;
    private boolean mUseTrustStore;

    BroadcastReceiver mCertNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String ssid = intent.getStringExtra(EXTRA_PENDING_CERT_SSID);
            // This is an onGoing notification, dismiss it once an action is sent.
            dismissDialogAndNotification();
            Log.d(TAG, "Received CertNotification: ssid=" + ssid + ", action=" + action);
            if (TextUtils.equals(action, ACTION_CERT_NOTIF_TAP)) {
                askForUserApprovalForCaCertificate();
            } else if (TextUtils.equals(action, ACTION_CERT_NOTIF_ACCEPT)) {
                handleAccept(ssid);
            } else if (TextUtils.equals(action, ACTION_CERT_NOTIF_REJECT)) {
                handleReject(ssid);
            }
        }
    };

    public InsecureEapNetworkHandler(@NonNull WifiContext context,
            @NonNull WifiConfigManager wifiConfigManager,
            @NonNull WifiNative wifiNative,
            @NonNull FrameworkFacade facade,
            @NonNull WifiNotificationManager notificationManager,
            @NonNull WifiDialogManager wifiDialogManager,
            boolean isTrustOnFirstUseSupported,
            boolean isInsecureEnterpriseConfigurationAllowed,
            @NonNull InsecureEapNetworkHandlerCallbacks callbacks,
            @NonNull String interfaceName,
            @NonNull Handler handler) {
        mContext = context;
        mWifiConfigManager = wifiConfigManager;
        mWifiNative = wifiNative;
        mFacade = facade;
        mNotificationManager = notificationManager;
        mWifiDialogManager = wifiDialogManager;
        mIsTrustOnFirstUseSupported = isTrustOnFirstUseSupported;
        mIsInsecureEnterpriseConfigurationAllowed = isInsecureEnterpriseConfigurationAllowed;
        mCallbacks = callbacks;
        mInterfaceName = interfaceName;
        mHandler = handler;

        mOnNetworkUpdateListener = new OnNetworkUpdateListener();
        mWifiConfigManager.addOnNetworkUpdateListener(mOnNetworkUpdateListener);

        mCaCertHelpLink = mContext.getString(R.string.config_wifiCertInstallationHelpLink);
    }

    /**
     * Prepare TOFU data for a new connection.
     *
     * Prepare TOFU data if this is an Enterprise configuration, which
     * uses Server Cert, without a valid Root CA certificate or user approval.
     * If TOFU is supported and enabled, this method will also clear the user credentials in the
     * initial connection to the server.
     *
     * @param config the running wifi configuration.
     */
    public void prepareConnection(@NonNull WifiConfiguration config) {
        if (null == config) return;
        mConnectingConfig = config;

        if (!config.isEnterprise()) return;
        WifiEnterpriseConfig entConfig = config.enterpriseConfig;
        if (!entConfig.isEapMethodServerCertUsed()) return;
        if (entConfig.hasCaCertificate()) return;

        Log.d(TAG, "prepareConnection: isTofuSupported=" + mIsTrustOnFirstUseSupported
                + ", isInsecureEapNetworkAllowed=" + mIsInsecureEnterpriseConfigurationAllowed
                + ", isTofuEnabled=" + entConfig.isTrustOnFirstUseEnabled()
                + ", isUserApprovedNoCaCert=" + entConfig.isUserApproveNoCaCert());
        // If TOFU is not supported or insecure EAP network is allowed without TOFU enabled,
        // skip the entire TOFU logic if this network was approved earlier by the user.
        if (entConfig.isUserApproveNoCaCert()) {
            if (!mIsTrustOnFirstUseSupported) return;
            if (mIsInsecureEnterpriseConfigurationAllowed
                    && !entConfig.isTrustOnFirstUseEnabled()) {
                return;
            }
        }

        if (mIsTrustOnFirstUseSupported && (entConfig.isTrustOnFirstUseEnabled()
                || !mIsInsecureEnterpriseConfigurationAllowed)) {
            /**
             * Clear the user credentials from this copy of the configuration object.
             * Supplicant will start the phase-1 TLS session to acquire the server certificate chain
             * which will be provided to the framework. Then since the callbacks for identity and
             * password requests are not populated, it will fail the connection and disconnect.
             * This will allow the user to review the certificates at their own pace, and a
             * reconnection would automatically take place with full verification of the chain once
             * they approve.
             */
            if (config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.TTLS
                    || config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.PEAP) {
                config.enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.NONE);
                config.enterpriseConfig.setIdentity(null);
                if (TextUtils.isEmpty(config.enterpriseConfig.getAnonymousIdentity())) {
                    /**
                     * If anonymous identity was not provided, use "anonymous" to prevent any
                     * untrusted server from tracking real user identities.
                     */
                    config.enterpriseConfig.setAnonymousIdentity(TOFU_ANONYMOUS_IDENTITY);
                }
                config.enterpriseConfig.setPassword(null);
            }
            if (mWifiNative.isSupplicantAidlServiceVersionAtLeast(2)) {
                // For AIDL v2+, we can start with the default trust store
                config.enterpriseConfig.setCaPath(WifiConfigurationUtil.getSystemTrustStorePath());
            }
        }
        mCurrentTofuConfig = config;
        mServerCertChain.clear();
        dismissDialogAndNotification();
        registerCertificateNotificationReceiver();

        if (useTrustOnFirstUse()) {
            // Remove cached PMK in the framework and supplicant to avoid skipping the EAP flow
            // only when TOFU is in use.
            clearNativeData();
            Log.d(TAG, "Remove native cached data and networks for TOFU.");
        }
    }

    /**
     * Do necessary clean up on stopping client mode.
     */
    public void cleanup() {
        dismissDialogAndNotification();
        unregisterCertificateNotificationReceiver();
        clearInternalData();
        mWifiConfigManager.removeOnNetworkUpdateListener(mOnNetworkUpdateListener);
    }

    /**
     * Stores a received certificate for later use.
     *
     * @param networkId networkId of the target network.
     * @param depth the depth of this cert. The Root CA should be 0 or
     *        a positive number, and the server cert is 0.
     * @param certInfo a certificate info object from the server.
     * @return true if the cert is cached; otherwise, false.
     */
    public boolean addPendingCertificate(int networkId, int depth,
            @NonNull CertificateEventInfo certInfo) {
        String configProfileKey = mCurrentTofuConfig != null
                ? mCurrentTofuConfig.getProfileKey() : "null";
        if (networkId == WifiConfiguration.INVALID_NETWORK_ID) {
            return false;
        }
        if (null == mCurrentTofuConfig) return false;
        if (mCurrentTofuConfig.networkId != networkId) {
            return false;
        }
        if (null == certInfo) return false;
        if (depth < 0) return false;

        // If TOFU is not supported return immediately, although this should not happen since
        // the caller code flow is only active when TOFU is supported.
        if (!mIsTrustOnFirstUseSupported) return false;

        // If insecure configurations are allowed and this configuration is configured with
        // "Do not validate" (i.e. TOFU is disabled), skip loading the certificates (no need for
        // them anyway) and don't disconnect the network.
        if (mIsInsecureEnterpriseConfigurationAllowed
                && !mCurrentTofuConfig.enterpriseConfig.isTrustOnFirstUseEnabled()) {
            Log.d(TAG, "Certificates are not required for this connection");
            return false;
        }

        if (depth == 0) {
            // Disable network selection upon receiving the server certificate
            putNetworkOnHold();
        }

        if (!mServerCertChain.contains(certInfo.getCert())) {
            mServerCertChain.addFirst(certInfo.getCert());
            Log.d(TAG, "addPendingCertificate: " + "SSID=" + mCurrentTofuConfig.SSID
                    + " depth=" + depth + " certHash=" + certInfo.getCertHash()
                    + " current config=" + configProfileKey
                    + "\ncertificate content:\n" + certInfo.getCert());
        }

        // 0 is the tail, i.e. the server cert.
        if (depth == 0 && null == mPendingServerCert) {
            mPendingServerCert = certInfo.getCert();
            mPendingServerCertSubjectInfo = CertificateSubjectInfo.parse(
                    certInfo.getCert().getSubjectX500Principal().getName());
            if (null == mPendingServerCertSubjectInfo) {
                Log.e(TAG, "Cert has no valid subject.");
                return false;
            }
            mPendingServerCertIssuerInfo = CertificateSubjectInfo.parse(
                    certInfo.getCert().getIssuerX500Principal().getName());
            if (null == mPendingServerCertIssuerInfo) {
                Log.e(TAG, "Cert has no valid issuer.");
                return false;
            }
            mServerCertHash = certInfo.getCertHash();
        }

        // Root or intermediate cert.
        if (depth < mPendingRootCaCertDepth) {
            return true;
        }
        mPendingRootCaCertDepth = depth;
        mPendingRootCaCert = certInfo.getCert();

        return true;
    }

    /**
     * Ask for the user approval if necessary.
     *
     * For TOFU is supported and an EAP network without a CA certificate.
     * - if insecure EAP networks are not allowed
     *    - if TOFU is not enabled, disconnect it.
     *    - if no pending CA cert, disconnect it.
     *    - if no server cert, disconnect it.
     * - if insecure EAP networks are allowed and TOFU is not enabled
     *    - follow no TOFU support flow.
     * - if TOFU is enabled, CA cert is pending, and server cert is pending
     *     - gate the connecitvity event here
     *     - if this request is from a user, launch a dialog to get the user approval.
     *     - if this request is from auto-connect, launch a notification.
     * If TOFU is not supported, the confirmation flow is similar. Instead of installing CA
     * cert from the server, just mark this network is approved by the user.
     *
     * @param isUserSelected indicates that this connection is triggered by a user.
     * @return true if user approval dialog is displayed and the network is pending.
     */
    public boolean startUserApprovalIfNecessary(boolean isUserSelected) {
        if (null == mConnectingConfig || null == mCurrentTofuConfig) return false;
        if (mConnectingConfig.networkId != mCurrentTofuConfig.networkId) return false;

        // If Trust On First Use is supported and insecure enterprise configuration
        // is not allowed, TOFU must be used for an Enterprise network without certs. This should
        // not happen because the TOFU flag will be set during boot if these conditions are met.
        if (mIsTrustOnFirstUseSupported && !mIsInsecureEnterpriseConfigurationAllowed
                && !mCurrentTofuConfig.enterpriseConfig.isTrustOnFirstUseEnabled()) {
            Log.e(TAG, "Upgrade insecure connection to TOFU.");
            mCurrentTofuConfig.enterpriseConfig.enableTrustOnFirstUse(true);
        }

        if (useTrustOnFirstUse()) {
            if (null == mPendingRootCaCert) {
                Log.e(TAG, "No valid CA cert for TLS-based connection.");
                handleError(mCurrentTofuConfig.SSID);
                return false;
            }
            if (null == mPendingServerCert) {
                Log.e(TAG, "No valid Server cert for TLS-based connection.");
                handleError(mCurrentTofuConfig.SSID);
                return false;
            }

            Log.d(TAG, "TOFU certificate chain:");
            for (X509Certificate cert : mServerCertChain) {
                Log.d(TAG, cert.getSubjectX500Principal().getName());
            }

            if (!configureServerValidationMethod()) {
                Log.e(TAG, "Server cert chain is invalid.");
                String ssid = mCurrentTofuConfig.SSID;
                handleError(ssid);
                createCertificateErrorNotification(isUserSelected, ssid);
                return false;
            }
        } else if (mIsInsecureEnterpriseConfigurationAllowed) {
            Log.i(TAG, "Insecure networks without a Root CA cert are allowed.");
            return false;
        }

        if (isUserSelected) {
            askForUserApprovalForCaCertificate();
        } else {
            notifyUserForCaCertificate();
        }
        return true;
    }

    /**
     * Create a notification or a dialog when a server certificate is invalid
     */
    private void createCertificateErrorNotification(boolean isUserSelected, String ssid) {
        String title = mContext.getString(R.string.wifi_tofu_invalid_cert_chain_title, ssid);
        String message = mContext.getString(R.string.wifi_tofu_invalid_cert_chain_message);
        String okButtonText = mContext.getString(
                R.string.wifi_tofu_invalid_cert_chain_ok_text);

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(message)) return;

        if (isUserSelected) {
            mTofuAlertDialog = mWifiDialogManager.createLegacySimpleDialog(
                    title,
                    message,
                    null /* positiveButtonText */,
                    null /* negativeButtonText */,
                    okButtonText,
                    new WifiDialogManager.SimpleDialogCallback() {
                        @Override
                        public void onPositiveButtonClicked() {
                            // Not used.
                        }

                        @Override
                        public void onNegativeButtonClicked() {
                            // Not used.
                        }

                        @Override
                        public void onNeutralButtonClicked() {
                            // Not used.
                        }

                        @Override
                        public void onCancelled() {
                            // Not used.
                        }
                    },
                    new WifiThreadRunner(mHandler));
            mTofuAlertDialog.launchDialog();
        } else {
            Notification.Builder builder = mFacade.makeNotificationBuilder(mContext,
                            WifiService.NOTIFICATION_NETWORK_ALERTS)
                    .setSmallIcon(
                            Icon.createWithResource(mContext.getWifiOverlayApkPkgName(),
                                    com.android.wifi.resources.R
                                            .drawable.stat_notify_wifi_in_range))
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setColor(mContext.getResources().getColor(
                            android.R.color.system_notification_accent_color));
            mNotificationManager.notify(SystemMessage.NOTE_SERVER_CA_CERTIFICATE,
                    builder.build());
        }
    }

    /**
     * Disable network selection, disconnect if necessary, and clear PMK cache
     */
    private void putNetworkOnHold() {
        // Disable network selection upon receiving the server certificate
        mWifiConfigManager.updateNetworkSelectionStatus(mCurrentTofuConfig.networkId,
                WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER);

        // Force disconnect and clear PMK cache to avoid supplicant reconnection
        mWifiNative.disconnect(mInterfaceName);
        clearNativeData();
    }

    /**
     * Check whether certificate pinning should be used.
     *
     * @param verbose whether to print logs during the check.
     * @return true if certificate pinning should be used, false otherwise.
     */
    private boolean useCertificatePinning(boolean verbose) {
        if (mServerCertChain.size() == 1) {
            if (verbose) {
                Log.i(TAG, "Only one certificate provided, use server certificate pinning");
            }
            return true;
        }
        if (mPendingRootCaCert.getSubjectX500Principal().getName()
                .equals(mPendingRootCaCert.getIssuerX500Principal().getName())) {
            if (mPendingRootCaCert.getVersion() >= 2
                    && mPendingRootCaCert.getBasicConstraints() < 0) {
                if (verbose) {
                    Log.i(TAG, "Root CA with no CA bit set in basic constraints, "
                            + "use server certificate pinning");
                }
                return true;
            }
        } else {
            if (verbose) {
                Log.i(TAG, "Root CA is not self-signed, use server certificate pinning");
            }
            return true;
        }
        return false;
    }

    /**
     * Configure the server validation method based on the incoming server certificate chain.
     * If a valid method is found, the method returns true, and the caller can continue the TOFU
     * process.
     *
     * A valid method could be one of the following:
     * 1. If only the leaf or a partial chain is provided, use server certificate pinning.
     * 2. If a full chain is provided, use the provided Root CA, but only if we are able to
     *    cryptographically validate it.
     *
     * If no certificates were received, or the certificates are invalid, or chain verification
     * fails, the method returns false and the caller should abort the TOFU process.
     */
    private boolean configureServerValidationMethod() {
        if (mServerCertChain.size() == 0) {
            Log.e(TAG, "No certificate chain provided by the server.");
            return false;
        }
        if (useCertificatePinning(true)) {
            return true;
        }

        CertPath certPath;
        try {
            certPath = CertificateFactory.getInstance("X.509").generateCertPath(mServerCertChain);
        } catch (CertificateException e) {
            Log.e(TAG, "Certificate chain is invalid.");
            return false;
        } catch (IllegalStateException e) {
            Log.wtf(TAG, "Fail: " + e);
            return false;
        }
        CertPathValidator certPathValidator;
        try {
            certPathValidator = CertPathValidator.getInstance("PKIX");
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "PKIX algorithm not supported.");
            return false;
        }
        try {
            Set<TrustAnchor> anchorSet = Set.of(new TrustAnchor(mPendingRootCaCert, null));
            PKIXParameters params = new PKIXParameters(anchorSet);
            params.setRevocationEnabled(false);
            certPathValidator.validate(certPath, params);
        } catch (InvalidAlgorithmParameterException e) {
            Log.wtf(TAG, "Invalid algorithm exception.");
            return false;
        } catch (CertPathValidatorException e) {
            Log.e(TAG, "Server certificate chain validation failed: " + e);
            return false;
        }

        // Validation succeeded, no need for the server cert hash
        mServerCertHash = null;

        // Check if the Root CA certificate is in the trust store so that we could configure the
        // connection to use the system store instead of an explicit Root CA.
        mUseTrustStore = false;
        if (mWifiNative.isSupplicantAidlServiceVersionAtLeast(2)) {
            if (isCertInTrustStore(mPendingRootCaCert)) {
                mUseTrustStore = true;
            }
        }
        Log.i(TAG, "Server certificate chain validation succeeded, use "
                + (mUseTrustStore ? "trust store" : "Root CA"));
        return true;
    }

    private boolean useTrustOnFirstUse() {
        return mIsTrustOnFirstUseSupported
                && mCurrentTofuConfig.enterpriseConfig.isTrustOnFirstUseEnabled();
    }

    private void registerCertificateNotificationReceiver() {
        unregisterCertificateNotificationReceiver();

        IntentFilter filter = new IntentFilter();
        if (useTrustOnFirstUse()) {
            filter.addAction(ACTION_CERT_NOTIF_TAP);
        } else {
            filter.addAction(ACTION_CERT_NOTIF_ACCEPT);
            filter.addAction(ACTION_CERT_NOTIF_REJECT);
        }
        mContext.registerReceiver(mCertNotificationReceiver, filter, null, mHandler);
        mIsCertNotificationReceiverRegistered = true;
    }

    private void unregisterCertificateNotificationReceiver() {
        if (!mIsCertNotificationReceiverRegistered) return;

        mContext.unregisterReceiver(mCertNotificationReceiver);
        mIsCertNotificationReceiverRegistered = false;
    }

    @VisibleForTesting
    void handleAccept(@NonNull String ssid) {
        if (!isConnectionValid(ssid)) return;

        if (!useTrustOnFirstUse()) {
            mWifiConfigManager.setUserApproveNoCaCert(mCurrentTofuConfig.networkId, true);
        } else {
            if (null == mPendingRootCaCert || null == mPendingServerCert) {
                handleError(ssid);
                return;
            }
            if (!mWifiConfigManager.updateCaCertificate(
                    mCurrentTofuConfig.networkId, mPendingRootCaCert, mPendingServerCert,
                    mServerCertHash, mUseTrustStore)) {
                // The user approved this network,
                // keep the connection regardless of the result.
                Log.e(TAG, "Cannot update CA cert to network " + mCurrentTofuConfig.getProfileKey()
                        + ", CA cert = " + mPendingRootCaCert);
            }
            int postConnectionMethod = useCertificatePinning(false)
                    ? WifiEnterpriseConfig.TOFU_STATE_CERT_PINNING
                    : WifiEnterpriseConfig.TOFU_STATE_CONFIGURE_ROOT_CA;
            mWifiConfigManager.setTofuPostConnectionState(
                    mCurrentTofuConfig.networkId, postConnectionMethod);
        }
        int networkId = mCurrentTofuConfig.networkId;
        mWifiConfigManager.setTofuDialogApproved(networkId, true);
        mWifiConfigManager.updateNetworkSelectionStatus(networkId,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE);
        dismissDialogAndNotification();
        clearInternalData();

        if (null != mCallbacks) mCallbacks.onAccept(ssid, networkId);
    }

    @VisibleForTesting
    void handleReject(@NonNull String ssid) {
        if (!isConnectionValid(ssid)) return;
        boolean disconnectRequired = !useTrustOnFirstUse();

        mWifiConfigManager.setTofuDialogApproved(mCurrentTofuConfig.networkId, false);
        mWifiConfigManager.updateNetworkSelectionStatus(mCurrentTofuConfig.networkId,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WIFI_MANAGER);
        dismissDialogAndNotification();
        clearInternalData();
        if (disconnectRequired) clearNativeData();
        if (null != mCallbacks) mCallbacks.onReject(ssid, disconnectRequired);
    }

    private void handleError(@Nullable String ssid) {
        if (mCurrentTofuConfig != null) {
            mWifiConfigManager.updateNetworkSelectionStatus(mCurrentTofuConfig.networkId,
                    WifiConfiguration.NetworkSelectionStatus
                    .DISABLED_BY_WIFI_MANAGER);
        }
        dismissDialogAndNotification();
        clearInternalData();
        clearNativeData();

        if (null != mCallbacks) mCallbacks.onError(ssid);
    }

    private void askForUserApprovalForCaCertificate() {
        if (mCurrentTofuConfig == null || TextUtils.isEmpty(mCurrentTofuConfig.SSID)) return;
        if (useTrustOnFirstUse()) {
            if (null == mPendingRootCaCert || null == mPendingServerCert) {
                Log.e(TAG, "Cannot launch a dialog for TOFU without "
                        + "a valid pending CA certificate.");
                return;
            }
        }
        dismissDialogAndNotification();

        String title = useTrustOnFirstUse()
                ? mContext.getString(R.string.wifi_ca_cert_dialog_title)
                : mContext.getString(R.string.wifi_ca_cert_dialog_preT_title);
        String positiveButtonText = useTrustOnFirstUse()
                ? mContext.getString(R.string.wifi_ca_cert_dialog_continue_text)
                : mContext.getString(R.string.wifi_ca_cert_dialog_preT_continue_text);
        String negativeButtonText = useTrustOnFirstUse()
                ? mContext.getString(R.string.wifi_ca_cert_dialog_abort_text)
                : mContext.getString(R.string.wifi_ca_cert_dialog_preT_abort_text);

        String message;
        String messageUrl = null;
        int messageUrlStart = 0;
        int messageUrlEnd = 0;
        if (useTrustOnFirstUse()) {
            StringBuilder contentBuilder = new StringBuilder()
                    .append(mContext.getString(R.string.wifi_ca_cert_dialog_message_hint))
                    .append(mContext.getString(
                            R.string.wifi_ca_cert_dialog_message_server_name_text,
                            mPendingServerCertSubjectInfo.commonName))
                    .append(mContext.getString(
                            R.string.wifi_ca_cert_dialog_message_issuer_name_text,
                            mPendingServerCertIssuerInfo.commonName));
            if (!TextUtils.isEmpty(mPendingServerCertSubjectInfo.organization)) {
                contentBuilder.append(mContext.getString(
                        R.string.wifi_ca_cert_dialog_message_organization_text,
                        mPendingServerCertSubjectInfo.organization));
            }
            final Date expiration = mPendingServerCert.getNotAfter();
            if (expiration != null) {
                contentBuilder.append(mContext.getString(
                        R.string.wifi_ca_cert_dialog_message_expiration_text,
                        DateFormat.getMediumDateFormat(mContext).format(expiration)));
            }
            final String fingerprint = getDigest(mPendingServerCert, "SHA256");
            if (!TextUtils.isEmpty(fingerprint)) {
                contentBuilder.append(mContext.getString(
                        R.string.wifi_ca_cert_dialog_message_signature_name_text, fingerprint));
            }
            message = contentBuilder.toString();
        } else {
            String hint = mContext.getString(
                    R.string.wifi_ca_cert_dialog_preT_message_hint, mCurrentTofuConfig.SSID);
            String linkText = mContext.getString(
                    R.string.wifi_ca_cert_dialog_preT_message_link);
            message = hint + " " + linkText;
            messageUrl = mCaCertHelpLink;
            messageUrlStart = hint.length() + 1;
            messageUrlEnd = message.length();
        }
        mTofuAlertDialog = mWifiDialogManager.createLegacySimpleDialogWithUrl(
                title,
                message,
                messageUrl,
                messageUrlStart,
                messageUrlEnd,
                positiveButtonText,
                negativeButtonText,
                null /* neutralButtonText */,
                new WifiDialogManager.SimpleDialogCallback() {
                    @Override
                    public void onPositiveButtonClicked() {
                        if (mCurrentTofuConfig == null) {
                            return;
                        }
                        Log.d(TAG, "User accepted the server certificate");
                        handleAccept(mCurrentTofuConfig.SSID);
                    }

                    @Override
                    public void onNegativeButtonClicked() {
                        if (mCurrentTofuConfig == null) {
                            return;
                        }
                        Log.d(TAG, "User rejected the server certificate");
                        handleReject(mCurrentTofuConfig.SSID);
                    }

                    @Override
                    public void onNeutralButtonClicked() {
                        // Not used.
                        if (mCurrentTofuConfig == null) {
                            return;
                        }
                        Log.d(TAG, "User input neutral");
                        handleReject(mCurrentTofuConfig.SSID);
                    }

                    @Override
                    public void onCancelled() {
                        if (mCurrentTofuConfig == null) {
                            return;
                        }
                        Log.d(TAG, "User input canceled");
                        handleReject(mCurrentTofuConfig.SSID);
                    }
                },
                new WifiThreadRunner(mHandler));
        mTofuAlertDialog.launchDialog();
    }

    private PendingIntent genCaCertNotifIntent(
            @NonNull String action, @NonNull String ssid) {
        Intent intent = new Intent(action)
                .setPackage(mContext.getServiceWifiPackageName())
                .putExtra(EXTRA_PENDING_CERT_SSID, ssid);
        return mFacade.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void notifyUserForCaCertificate() {
        if (mCurrentTofuConfig == null) return;
        if (useTrustOnFirstUse()) {
            if (null == mPendingRootCaCert) return;
            if (null == mPendingServerCert) return;
        }
        dismissDialogAndNotification();

        PendingIntent tapPendingIntent;
        if (useTrustOnFirstUse()) {
            tapPendingIntent = genCaCertNotifIntent(ACTION_CERT_NOTIF_TAP, mCurrentTofuConfig.SSID);
        } else {
            Intent openLinkIntent = new Intent(Intent.ACTION_VIEW)
                    .setData(Uri.parse(mCaCertHelpLink))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            tapPendingIntent = mFacade.getActivity(mContext, 0, openLinkIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        String title = useTrustOnFirstUse()
                ? mContext.getString(R.string.wifi_ca_cert_notification_title)
                : mContext.getString(R.string.wifi_ca_cert_notification_preT_title);
        String content = useTrustOnFirstUse()
                ? mContext.getString(R.string.wifi_ca_cert_notification_message,
                        mCurrentTofuConfig.SSID)
                : mContext.getString(R.string.wifi_ca_cert_notification_preT_message,
                        mCurrentTofuConfig.SSID);
        Notification.Builder builder = mFacade.makeNotificationBuilder(mContext,
                WifiService.NOTIFICATION_NETWORK_ALERTS)
                .setSmallIcon(Icon.createWithResource(mContext.getWifiOverlayApkPkgName(),
                            com.android.wifi.resources.R.drawable.stat_notify_wifi_in_range))
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setContentIntent(tapPendingIntent)
                .setOngoing(true)
                .setColor(mContext.getResources().getColor(
                            android.R.color.system_notification_accent_color));
        // On a device which does not support Trust On First Use,
        // a user can accept or reject this network via the notification.
        if (!useTrustOnFirstUse()) {
            Notification.Action acceptAction = new Notification.Action.Builder(
                    null /* icon */,
                    mContext.getString(R.string.wifi_ca_cert_dialog_preT_continue_text),
                    genCaCertNotifIntent(ACTION_CERT_NOTIF_ACCEPT, mCurrentTofuConfig.SSID))
                    .build();
            Notification.Action rejectAction = new Notification.Action.Builder(
                    null /* icon */,
                    mContext.getString(R.string.wifi_ca_cert_dialog_preT_abort_text),
                    genCaCertNotifIntent(ACTION_CERT_NOTIF_REJECT, mCurrentTofuConfig.SSID))
                    .build();
            builder.addAction(rejectAction).addAction(acceptAction);
        }
        mNotificationManager.notify(SystemMessage.NOTE_SERVER_CA_CERTIFICATE, builder.build());
    }

    private void dismissDialogAndNotification() {
        mNotificationManager.cancel(SystemMessage.NOTE_SERVER_CA_CERTIFICATE);
        if (mTofuAlertDialog != null) {
            mTofuAlertDialog.dismissDialog();
            mTofuAlertDialog = null;
        }
    }

    private void clearInternalData() {
        mPendingRootCaCertDepth = -1;
        mPendingRootCaCert = null;
        mPendingServerCert = null;
        mPendingServerCertSubjectInfo = null;
        mPendingServerCertIssuerInfo = null;
        mCurrentTofuConfig = null;
        mServerCertHash = null;
        mUseTrustStore = false;
    }

    private void clearNativeData() {
        // PMK should be cleared or it would skip EAP flow next time.
        if (null != mCurrentTofuConfig) {
            mWifiNative.removeNetworkCachedData(mCurrentTofuConfig.networkId);
        }
        // remove network so that supplicant's PMKSA cache is cleared
        mWifiNative.removeAllNetworks(mInterfaceName);
    }

    // There might be two possible conditions that there is no
    // valid information to handle this response:
    // 1. A new network request is fired just before getting the response.
    //    As a result, this response is invalid and should be ignored.
    // 2. There is something wrong, and it stops at an abnormal state.
    //    For this case, we should go back DisconnectedState to
    //    recover the state machine.
    // Unfortunatually, we cannot identify the condition without valid information.
    // If condition #1 occurs, and we found that the target SSID is changed,
    // it should transit to L3Connected soon normally, just ignore this message.
    // If condition #2 occurs, clear existing data and notify the client mode
    // via onError callback.
    private boolean isConnectionValid(@Nullable String ssid) {
        if (TextUtils.isEmpty(ssid) || null == mCurrentTofuConfig) {
            handleError(null);
            return false;
        }

        if (!TextUtils.equals(ssid, mCurrentTofuConfig.SSID)) {
            Log.w(TAG, "Target SSID " + mCurrentTofuConfig.SSID
                    + " is different from TOFU returned SSID" + ssid);
            return false;
        }
        return true;
    }

    @VisibleForTesting
    static String getDigest(X509Certificate x509Certificate, String algorithm) {
        if (x509Certificate == null) {
            return "";
        }
        try {
            byte[] bytes = x509Certificate.getEncoded();
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(bytes);
            return fingerprint(digest);
        } catch (CertificateEncodingException ignored) {
            return "";
        } catch (NoSuchAlgorithmException ignored) {
            return "";
        }
    }

    private static String fingerprint(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringJoiner sj = new StringJoiner(":");
        for (byte b : bytes) {
            sj.add(HexDump.toHexString(b));
        }
        return sj.toString();
    }

    /** The callbacks object to notify the consumer. */
    public static class InsecureEapNetworkHandlerCallbacks {
        /**
         * When a certificate is accepted, this callback is called.
         *
         * @param ssid SSID of the network.
         * @param networkId  network ID
         */
        public void onAccept(@NonNull String ssid, int networkId) {}
        /**
         * When a certificate is rejected, this callback is called.
         *
         * @param ssid SSID of the network.
         * @param disconnectRequired Set to true if the network is currently connected
         */
        public void onReject(@NonNull String ssid, boolean disconnectRequired) {}
        /**
         * When there are no valid data to handle this insecure EAP network,
         * this callback is called.
         *
         * @param ssid SSID of the network, it might be null.
         */
        public void onError(@Nullable String ssid) {}
    }

    /**
     * Listener for config manager network config related events.
     */
    private class OnNetworkUpdateListener implements
            WifiConfigManager.OnNetworkUpdateListener {
        @Override
        public void onNetworkRemoved(WifiConfiguration config) {
            // Dismiss TOFU dialog if the network of the current Tofu config is removed.
            if (config == null || mCurrentTofuConfig == null
                    || mTofuAlertDialog == null
                    || config.networkId != mCurrentTofuConfig.networkId) return;

            dismissDialogAndNotification();
        }
    }

    /**
     * Check if a given Root CA certificate exists in the Android trust store
     *
     * @param rootCaCert the Root CA certificate to check
     * @return true if the Root CA certificate is found in the trust store, false otherwise
     */
    private boolean isCertInTrustStore(X509Certificate rootCaCert) {
        try {
            // Get the Android trust store.
            KeyStore keystore = KeyStore.getInstance("AndroidCAStore");
            keystore.load(null);

            Enumeration<String> aliases = keystore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                X509Certificate trusted = (X509Certificate) keystore.getCertificate(alias);
                if (trusted.getSubjectDN().equals(rootCaCert.getSubjectDN())) {
                    // Check that the supplied cert was actually signed by the key we trust.
                    rootCaCert.verify(trusted.getPublicKey());
                    return true;
                }
            }
        } catch (Exception e) {
            // Fall through
            Log.e(TAG, e.getMessage(), e);
        }
        // The certificate is not in the trust store.
        return false;
    }
}
