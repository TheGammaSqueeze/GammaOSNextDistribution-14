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

package com.android.server.wifi;

import android.annotation.Nullable;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.os.UserHandle;
import android.security.KeyChain;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.util.Preconditions;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.ArrayUtils;

import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * This class provides the methods to access keystore for certificate management.
 *
 * NOTE: This class should only be used from WifiConfigManager!
 */
public class WifiKeyStore {
    private static final String TAG = "WifiKeyStore";

    private boolean mVerboseLoggingEnabled = false;

    @Nullable private final KeyStore mKeyStore;
    private final Context mContext;
    private final FrameworkFacade mFrameworkFacade;

    WifiKeyStore(Context context, @Nullable KeyStore keyStore, FrameworkFacade frameworkFacade) {
        mKeyStore = keyStore;
        if (mKeyStore == null) {
            Log.e(TAG, "Unable to retrieve keystore, all key operations will fail");
        }
        mContext = context;
        mFrameworkFacade = frameworkFacade;
    }

    /**
     * Enable verbose logging.
     */
    void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    // Certificate and private key management for EnterpriseConfig
    private static boolean needsKeyStore(WifiEnterpriseConfig config) {
        return (config.getClientCertificate() != null || config.getCaCertificate() != null
                || config.getCaCertificateAlias() != null
                || config.getClientCertificateAlias() != null);
    }

    private static boolean isHardwareBackedKey(Key key) {
        return KeyChain.isBoundKeyAlgorithm(key.getAlgorithm());
    }

    private static boolean hasHardwareBackedKey(Certificate certificate) {
        return isHardwareBackedKey(certificate.getPublicKey());
    }

    /**
     * Install keys for given enterprise network.
     *
     * @param existingConfig Existing config corresponding to the network already stored in our
     *                       database. This maybe null if it's a new network.
     * @param config         Config corresponding to the network.
     * @param existingAlias  Alias for all the existing key store data stored.
     * @param alias          Alias for all the key store data to store.
     * @return true if successful, false otherwise.
     */
    private boolean installKeys(WifiEnterpriseConfig existingConfig, WifiEnterpriseConfig config,
            String existingAlias, String alias) {
        Preconditions.checkNotNull(mKeyStore);
        Certificate[] clientCertificateChain = config.getClientCertificateChain();
        if (!ArrayUtils.isEmpty(clientCertificateChain)) {
            if (!putUserPrivKeyAndCertsInKeyStore(alias, config.getClientPrivateKey(),
                    clientCertificateChain)) {
                return false;
            }
        }
        X509Certificate[] caCertificates = config.getCaCertificates();
        Set<String> oldCaCertificatesToRemove = new ArraySet<>();

        // Create a list of old Root CA certificate aliases from the existing configuration.
        // Note that when updating from Settings, caCertificates is empty, therefore, all
        // certificates must be kept. This happens because the certificate material is already
        // stored in KeyStore and the only reference left is the alias.
        if (existingConfig != null && existingConfig.getCaCertificateAliases() != null
                && existingConfig.isAppInstalledCaCert() && caCertificates != null) {
            oldCaCertificatesToRemove.addAll(
                    Arrays.asList(existingConfig.getCaCertificateAliases()));
        }
        List<String> caCertificateAliases = null;
        if (caCertificates != null) {
            caCertificateAliases = new ArrayList<>();
            for (int i = 0; i < caCertificates.length; i++) {
                String caAlias = alias + "_" + i;

                oldCaCertificatesToRemove.remove(caAlias);
                if (!putCaCertInKeyStore(caAlias, caCertificates[i])) {
                    // cleanup everything on failure.
                    removeEntryFromKeyStore(alias);
                    for (String addedAlias : caCertificateAliases) {
                        removeEntryFromKeyStore(addedAlias);
                    }
                    return false;
                }
                caCertificateAliases.add(caAlias);
            }
        }
        // If alias changed, remove the old one.
        if (!TextUtils.equals(alias, existingAlias)) {
            if (existingConfig != null && existingConfig.isAppInstalledDeviceKeyAndCert()) {
                // Remove old private keys.
                removeEntryFromKeyStore(existingAlias);
            }
        }
        // Remove any old CA certs.
        for (String oldAlias : oldCaCertificatesToRemove) {
            removeEntryFromKeyStore(oldAlias);
        }
        // Set alias names
        if (config.getClientCertificate() != null) {
            config.setClientCertificateAlias(alias);
            config.resetClientKeyEntry();
        }

        if (caCertificates != null) {
            config.setCaCertificateAliases(
                    caCertificateAliases.toArray(new String[caCertificateAliases.size()]));
            config.resetCaCertificate();
        }
        return true;
    }

    /**
     * Install a CA certificate into the keystore.
     *
     * @param alias The alias name of the CA certificate to be installed
     * @param cert The CA certificate to be installed
     * @return true on success
     */
    public boolean putCaCertInKeyStore(String alias, Certificate cert) {
        try {
            mKeyStore.setCertificateEntry(alias, cert);
            return true;
        } catch (KeyStoreException e) {
            Log.e(TAG, "Failed to put CA certificate in keystore: " + e.getMessage());
            return false;
        }
    }

    /**
     * Install a private key + user certificate into the keystore.
     *
     * @param alias The alias name of the key to be installed
     * @param key The private key to be installed
     * @param certs User Certificate chain.
     * @return true on success
     */
    public boolean putUserPrivKeyAndCertsInKeyStore(String alias, Key key, Certificate[] certs) {
        try {
            mKeyStore.setKeyEntry(alias, key, null, certs);
            return true;
        } catch (KeyStoreException e) {
            Log.e(TAG, "Failed to put private key or certificate in keystore: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove a certificate or key entry specified by the alias name from the keystore.
     *
     * @param alias The alias name of the entry to be removed
     * @return true on success
     */
    public boolean removeEntryFromKeyStore(String alias) {
        Preconditions.checkNotNull(mKeyStore);
        try {
            mKeyStore.deleteEntry(alias);
            return true;
        } catch (KeyStoreException e) {
            return false;
        }
    }

    /**
     * Remove enterprise keys from the network config.
     *
     * @param config Config corresponding to the network.
     * @param forceRemove remove keys regardless of the key installer.
     */
    public void removeKeys(WifiEnterpriseConfig config, boolean forceRemove) {
        Preconditions.checkNotNull(mKeyStore);
        // Do not remove keys that were manually installed by the user
        if (forceRemove || config.isAppInstalledDeviceKeyAndCert()) {
            String client = config.getClientCertificateAlias();
            // a valid client certificate is configured
            if (!TextUtils.isEmpty(client)) {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "removing client private key, user cert and CA cert)");
                }
                // if there is only a single CA certificate, then that is also stored with
                // the same alias, hence will be removed here.
                removeEntryFromKeyStore(client);
            }
        }

        // Do not remove CA certs that were manually installed by the user
        if (forceRemove || config.isAppInstalledCaCert()) {
            String[] aliases = config.getCaCertificateAliases();
            if (aliases == null || aliases.length == 0) {
                return;
            }
            // Remove all CA certificate.
            for (String ca : aliases) {
                if (!TextUtils.isEmpty(ca)) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "removing CA cert: " + ca);
                    }
                    removeEntryFromKeyStore(ca);
                }
            }
        }
    }

    /**
     * Update/Install keys for given enterprise network.
     *
     * @param config         Config corresponding to the network.
     * @param existingConfig Existing config corresponding to the network already stored in our
     *                       database. This maybe null if it's a new network.
     * @return true if successful, false otherwise.
     */
    public boolean updateNetworkKeys(WifiConfiguration config, WifiConfiguration existingConfig) {
        Preconditions.checkNotNull(mKeyStore);
        Preconditions.checkNotNull(config.enterpriseConfig);
        WifiEnterpriseConfig enterpriseConfig = config.enterpriseConfig;
        /* config passed may include only fields being updated.
         * In order to generate the key id, fetch uninitialized
         * fields from the currently tracked configuration
         */
        String keyId = config.getKeyIdForCredentials(existingConfig);
        WifiEnterpriseConfig existingEnterpriseConfig = null;
        String existingKeyId = null;
        if (existingConfig != null) {
            Preconditions.checkNotNull(existingConfig.enterpriseConfig);
            existingEnterpriseConfig = existingConfig.enterpriseConfig;
            existingKeyId = existingConfig.getKeyIdForCredentials(existingConfig);
        }

        if (SdkLevel.isAtLeastS()) {
            // If client key is in KeyChain, convert KeyChain alias into a grant string that can be
            // used by the supplicant like a normal alias.
            final String keyChainAlias = enterpriseConfig.getClientKeyPairAliasInternal();
            if (keyChainAlias != null) {
                final String grantString = mFrameworkFacade.getWifiKeyGrantAsUser(
                        mContext, UserHandle.getUserHandleForUid(config.creatorUid), keyChainAlias);
                if (grantString == null) {
                    // The key is not granted to Wifi uid or the alias is invalid.
                    Log.e(TAG, "Unable to get key grant");
                    return false;
                }
                enterpriseConfig.setClientCertificateAlias(grantString);
            }
        }

        if (!needsKeyStore(enterpriseConfig)) {
            return true;
        }

        try {
            if (!installKeys(existingEnterpriseConfig, enterpriseConfig, existingKeyId, keyId)) {
                Log.e(TAG, config.SSID + ": failed to install keys");
                return false;
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, config.SSID + " invalid config for key installation: " + e.getMessage());
            return false;
        }

        // For WPA3-Enterprise 192-bit networks, set the SuiteBCipher field based on the
        // CA certificate type. Suite-B requires SHA384, reject other certs.
        if (config.isSecurityType(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT)) {
            // Read the CA certificates, and initialize
            String[] caAliases = config.enterpriseConfig.getCaCertificateAliases();
            int caCertType = -1;

            // In TOFU mode, configure the security mode based on the user certificate only.
            if (!config.enterpriseConfig.isTrustOnFirstUseEnabled()) {
                if (caAliases == null || caAliases.length == 0) {
                    Log.e(TAG, "No CA aliases in profile");
                    return false;
                }

                int prevCaCertType = -1;
                for (String caAlias : caAliases) {
                    Certificate caCert = null;
                    try {
                        caCert = mKeyStore.getCertificate(caAlias);
                    } catch (KeyStoreException e) {
                        Log.e(TAG, "Failed to get Suite-B certificate", e);
                    }
                    if (caCert == null || !(caCert instanceof X509Certificate)) {
                        Log.e(TAG, "Failed reading CA certificate for Suite-B");
                        return false;
                    }

                    // Confirm that the CA certificate is compatible with Suite-B requirements
                    caCertType = getSuiteBCipherFromCert((X509Certificate) caCert);
                    if (caCertType < 0) {
                        return false;
                    }
                    if (prevCaCertType != -1) {
                        if (prevCaCertType != caCertType) {
                            Log.e(TAG, "Incompatible CA certificates");
                            return false;
                        }
                    }
                    prevCaCertType = caCertType;
                }
            }

            Certificate clientCert = null;
            try {
                clientCert = mKeyStore.getCertificate(config.enterpriseConfig
                        .getClientCertificateAlias());
            } catch (KeyStoreException e) {
                Log.e(TAG, "Failed to get Suite-B client certificate", e);
            }
            if (clientCert == null || !(clientCert instanceof X509Certificate)) {
                Log.e(TAG, "Failed reading client certificate for Suite-B");
                return false;
            }

            int clientCertType = getSuiteBCipherFromCert((X509Certificate) clientCert);
            if (clientCertType < 0) {
                return false;
            }

            if (clientCertType == caCertType
                    || config.enterpriseConfig.isTrustOnFirstUseEnabled()) {
                config.enableSuiteBCiphers(
                        clientCertType == WifiConfiguration.SuiteBCipher.ECDHE_ECDSA,
                        clientCertType == WifiConfiguration.SuiteBCipher.ECDHE_RSA);
            } else {
                Log.e(TAG, "Client certificate for Suite-B is incompatible with the CA "
                        + "certificate");
                return false;
            }
        }
        return true;
    }

    /**
     * Get the Suite-B cipher from the certificate
     *
     * @param x509Certificate Certificate to process
     * @return WifiConfiguration.SuiteBCipher.ECDHE_RSA if the certificate OID matches the Suite-B
     * requirements for RSA certificates, WifiConfiguration.SuiteBCipher.ECDHE_ECDSA if the
     * certificate OID matches the Suite-B requirements for ECDSA certificates, or -1 otherwise.
     */
    private int getSuiteBCipherFromCert(X509Certificate x509Certificate) {
        String sigAlgOid = x509Certificate.getSigAlgOID();
        if (mVerboseLoggingEnabled) {
            Principal p = x509Certificate.getSubjectX500Principal();
            if (p != null && !TextUtils.isEmpty(p.getName())) {
                Log.d(TAG, "Checking cert " + p.getName());
            }
        }
        int bitLength = 0;

        // Wi-Fi alliance requires the use of both ECDSA secp384r1 and RSA 3072 certificates
        // in WPA3-Enterprise 192-bit security networks, which are also known as Suite-B-192
        // networks, even though NSA Suite-B-192 mandates ECDSA only. The use of the term
        // Suite-B was already coined in the IEEE 802.11-2016 specification for
        // AKM 00-0F-AC but the test plan for WPA3-Enterprise 192-bit for APs mandates
        // support for both RSA and ECDSA, and for STAs it mandates ECDSA and optionally
        // RSA. In order to be compatible with all WPA3-Enterprise 192-bit deployments,
        // we are supporting both types here.
        if (TextUtils.equals(sigAlgOid, "1.2.840.113549.1.1.12")) {
            // sha384WithRSAEncryption
            if (x509Certificate.getPublicKey() instanceof RSAPublicKey) {
                final RSAPublicKey rsaPublicKey = (RSAPublicKey) x509Certificate.getPublicKey();
                if (rsaPublicKey.getModulus() != null) {
                    bitLength = rsaPublicKey.getModulus().bitLength();
                    if (bitLength >= 3072) {
                        if (mVerboseLoggingEnabled) {
                            Log.d(TAG, "Found Suite-B RSA certificate");
                        }
                        return WifiConfiguration.SuiteBCipher.ECDHE_RSA;
                    }
                }
            }
        } else if (TextUtils.equals(sigAlgOid, "1.2.840.10045.4.3.3")) {
            // ecdsa-with-SHA384
            if (x509Certificate.getPublicKey() instanceof ECPublicKey) {
                final ECPublicKey ecPublicKey = (ECPublicKey) x509Certificate.getPublicKey();
                final ECParameterSpec ecParameterSpec = ecPublicKey.getParams();
                if (ecParameterSpec != null && ecParameterSpec.getOrder() != null) {
                    bitLength = ecParameterSpec.getOrder().bitLength();
                    if (bitLength >= 384) {
                        if (mVerboseLoggingEnabled) {
                            Log.d(TAG, "Found Suite-B ECDSA certificate");
                        }
                        return WifiConfiguration.SuiteBCipher.ECDHE_ECDSA;
                    }
                }
            }
        }
        Log.e(TAG, "Invalid certificate type for Suite-B: " + sigAlgOid + " or insufficient"
                + " bit length: " + bitLength);
        return -1;
    }

    /**
     * Requests a grant from KeyChain and populates client certificate alias with it.
     *
     * @return true if no problems encountered.
     */
    public boolean validateKeyChainAlias(String alias, int uid) {
        if (TextUtils.isEmpty(alias)) {
            Log.e(TAG, "Alias cannot be empty");
            return false;
        }

        if (!SdkLevel.isAtLeastS()) {
            Log.w(TAG, "Attempt to use a KeyChain key on pre-S device");
            return false;
        }

        return mFrameworkFacade.hasWifiKeyGrantAsUser(
                mContext, UserHandle.getUserHandleForUid(uid), alias);
    }
}
