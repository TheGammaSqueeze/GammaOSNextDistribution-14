/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.keychain;

import static android.app.admin.SecurityLog.TAG_CERT_AUTHORITY_INSTALLED;
import static android.app.admin.SecurityLog.TAG_CERT_AUTHORITY_REMOVED;
import static android.security.KeyStore.UID_SELF;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.app.BroadcastOptions;
import android.app.IntentService;
import android.app.admin.SecurityLog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.StringParceledListSlice;
import android.hardware.security.keymint.ErrorCode;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import android.security.AppUriAuthenticationPolicy;
import android.security.CredentialManagementApp;
import android.security.IKeyChainService;
import android.security.KeyChain;
import android.security.KeyStore2;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.ParcelableKeyGenParameterSpec;
import android.security.keystore.StrongBoxUnavailableException;
import android.security.keystore2.AndroidKeyStoreLoadStoreParameter;
import android.system.keystore2.Domain;
import android.system.keystore2.KeyDescriptor;
import android.system.keystore2.KeyPermission;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.keychain.internal.ExistingKeysProvider;
import com.android.keychain.internal.GrantsDatabase;
import com.android.org.conscrypt.TrustedCertificateStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

public class KeyChainService extends IntentService {

    private static final String TAG = "KeyChain";
    private static final String CERT_INSTALLER_PACKAGE = "com.android.certinstaller";
    private final Set<Integer> ALLOWED_UIDS = Collections.unmodifiableSet(
            new HashSet(Arrays.asList(UID_SELF, Process.WIFI_UID)));

    private static final String MSG_NOT_SYSTEM = "Not system package";
    private static final String MSG_NOT_SYSTEM_OR_CERT_INSTALLER =
            "Not system or cert installer package";
    private static final String MSG_NOT_SYSTEM_OR_CRED_MNG_APP =
            "Not system or credential management app package";

    /** created in onCreate(), closed in onDestroy() */
    private GrantsDatabase mGrantsDb;
    private Injector mInjector;
    private KeyStore mKeyStore;
    private KeyChainStateStorage mStateStorage;

    private Object mCredentialManagementAppLock = new Object();
    @Nullable
    @GuardedBy("mCredentialManagementAppLock")
    private CredentialManagementApp mCredentialManagementApp;

    public KeyChainService() {
        super(KeyChainService.class.getSimpleName());
        mInjector = new Injector();
    }

    private KeyStore getKeyStore() {
        try {
            final KeyStore keystore = mInjector.getKeyStoreInstance();
            keystore.load(null);
            return keystore;
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException
                | CertificateException e) {
            Log.e(TAG, "Error opening AndroidKeyStore.", e);
            throw new RuntimeException("Error opening AndroidKeyStore.", e);
        }
    }

    private KeyStore getKeyStore(boolean useWifiNamespace) {
        if (!useWifiNamespace) {
            return mKeyStore;
        }
        try {
            final KeyStore keystore = mInjector.getKeyStoreInstance();
            keystore.load(
                    new AndroidKeyStoreLoadStoreParameter(
                            KeyProperties.NAMESPACE_WIFI));
            return keystore;
        } catch (IOException | CertificateException | KeyStoreException
                | NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to open AndroidKeyStore for WI-FI namespace.", e);
            return null;
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        mKeyStore = getKeyStore();
        mGrantsDb = new GrantsDatabase(this, new KeyStoreAliasesProvider(mKeyStore));
        mStateStorage = new KeyChainStateStorage(getDataDir());

        synchronized (mCredentialManagementAppLock) {
            mCredentialManagementApp = mStateStorage.loadCredentialManagementApp();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mGrantsDb.destroy();
        mGrantsDb = null;
    }

    private static class KeyStoreAliasesProvider implements ExistingKeysProvider {
        private final KeyStore mKeyStore;

        KeyStoreAliasesProvider(KeyStore keyStore) {
            mKeyStore = keyStore;
        }

        @Override
        public List<String> getExistingKeyAliases() {
            final List<String> keyStoreAliases = new ArrayList<>();
            try {
                final Enumeration<String> aliases = mKeyStore.aliases();
                while (aliases.hasMoreElements()) {
                    final String alias = aliases.nextElement();
                    if (mKeyStore.isKeyEntry(alias)) {
                        keyStoreAliases.add(alias);
                    }
                }
            } catch (KeyStoreException e) {
                Log.e(TAG, "Error while loading entries from keystore. "
                        + "List may be empty or incomplete.");
            }

            return keyStoreAliases;
        }
    }

    private KeyDescriptor makeKeyDescriptor(String alias) {
        final KeyDescriptor key = new KeyDescriptor();
        key.domain = Domain.APP;
        key.nspace = KeyProperties.NAMESPACE_APPLICATION;
        key.alias = alias;
        key.blob = null;
        return key;
    }

    private final IKeyChainService.Stub mIKeyChainService = new IKeyChainService.Stub() {
        private final TrustedCertificateStore mTrustedCertificateStore
                = new TrustedCertificateStore();
        private final Context mContext = KeyChainService.this;

        @Override
        public String requestPrivateKey(String alias) {
            final CallerIdentity caller = getCaller();
            if (!hasGrant(alias, caller)) {
                return null;
            }

            final int granteeUid = caller.mUid;

            try {
                final KeyStore2 keyStore2 = KeyStore2.getInstance();
                KeyDescriptor grant = keyStore2.grant(makeKeyDescriptor(alias), granteeUid,
                        KeyPermission.USE | KeyPermission.GET_INFO);
                return KeyChain.getGrantString(grant);
            } catch (android.security.KeyStoreException e) {
                Log.e(TAG, "Failed to grant " + alias + " to uid: " + granteeUid, e);
                return null;
            }
        }

        @Override
        public String getWifiKeyGrantAsUser(String alias) {
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);

            if (!hasGrant(alias, Process.WIFI_UID)) {
                return null;
            }

            KeyStore2 keyStore2 = KeyStore2.getInstance();
            try {
                KeyDescriptor grant = keyStore2.grant(makeKeyDescriptor(alias),
                        Process.WIFI_UID, KeyPermission.USE | KeyPermission.GET_INFO);
                return KeyStore2.makeKeystoreEngineGrantString(grant.nspace);
            } catch (android.security.KeyStoreException e) {
                Log.e(TAG, "Failed to grant " + alias + " to uid: " + Process.WIFI_UID, e);
                return null;
            }
        }

        @Override public byte[] getCertificate(String alias) {
            final CallerIdentity caller = getCaller();
            if (!hasGrant(alias, caller) && !isSystemUid(caller)) {
                return null;
            }
            try {
                if (!mKeyStore.isCertificateEntry(alias)) {
                    final Certificate cert = mKeyStore.getCertificate(alias);
                    if (cert == null) return null;
                    return cert.getEncoded();
                } else {
                    return null;
                }
            } catch (KeyStoreException | CertificateEncodingException e) {
                Log.e(TAG, "Failed to retrieve certificate.", e);
                return null;
            }
        }

        @Override public byte[] getCaCertificates(String alias) {
            final CallerIdentity caller = getCaller();
            if (!hasGrant(alias, caller) && !isSystemUid(caller)) {
                return null;
            }
            try {
                if (mKeyStore.isCertificateEntry(alias)) {
                    return mKeyStore.getCertificate(alias).getEncoded();
                } else {
                    final Certificate[] certs = mKeyStore.getCertificateChain(alias);
                    if (certs == null || certs.length <= 1) return null;
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    for (int i = 1; i < certs.length; ++i) {
                        outputStream.write(certs[i].getEncoded());
                    }
                    return outputStream.toByteArray();
                }
            } catch (KeyStoreException | CertificateEncodingException | IOException e) {
                Log.e(TAG, "Failed to retrieve certificate(s) from AndroidKeyStore.", e);
                return null;
            }
        }

        @Override public boolean isUserSelectable(String alias) {
            validateAlias(alias);
            return mGrantsDb.isUserSelectable(alias);
        }

        @Override public void setUserSelectable(String alias, boolean isUserSelectable) {
            validateAlias(alias);
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            Log.i(TAG, String.format("Marking certificate %s as user-selectable: %b", alias,
                    isUserSelectable));
            mGrantsDb.setIsUserSelectable(alias, isUserSelectable);
        }

        @Override public int generateKeyPair(
                String algorithm, ParcelableKeyGenParameterSpec parcelableSpec) {
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            final KeyGenParameterSpec spec = parcelableSpec.getSpec();
            final String alias = spec.getKeystoreAlias();

            Log.i(TAG, String.format("About to generate key with alias %s, algorithm %s",
                    alias, algorithm));

            if (KeyChain.KEY_ALIAS_SELECTION_DENIED.equals(alias)) {
                throw new IllegalArgumentException("The alias specified for the key denotes "
                        + "a reserved value and cannot be used to name a key");
            }
            // Validate the alias here to avoid relying on KeyGenParameterSpec c'tor preventing
            // the creation of a KeyGenParameterSpec instance with a non-empty alias.
            if (TextUtils.isEmpty(alias) || spec.getUid() != UID_SELF) {
                Log.e(TAG, "Cannot generate key pair with empty alias or specified uid.");
                return KeyChain.KEY_GEN_MISSING_ALIAS;
            }

            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance(
                        algorithm, "AndroidKeyStore");
                // Do not prepend USER_PRIVATE_KEY to the alias because
                // AndroidKeyStoreKeyPairGeneratorSpi will helpfully prepend that in
                // generateKeyPair.
                generator.initialize(spec);
                KeyPair kp = generator.generateKeyPair();
                if (kp == null) {
                    Log.e(TAG, "Key generation failed.");
                    return KeyChain.KEY_GEN_FAILURE;
                }
                return KeyChain.KEY_GEN_SUCCESS;
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "Invalid algorithm requested", e);
                return KeyChain.KEY_GEN_NO_SUCH_ALGORITHM;
            } catch (InvalidAlgorithmParameterException e) {
                Log.e(TAG, "Invalid algorithm params", e);
                return KeyChain.KEY_GEN_INVALID_ALGORITHM_PARAMETERS;
            } catch (NoSuchProviderException e) {
                Log.e(TAG, "Could not find Keystore.", e);
                return KeyChain.KEY_GEN_NO_KEYSTORE_PROVIDER;
            } catch (StrongBoxUnavailableException e) {
                Log.e(TAG, "StrongBox unavailable.", e);
                return KeyChain.KEY_GEN_STRONGBOX_UNAVAILABLE;
            } catch (ProviderException e) {
                Throwable t = e.getCause();
                if (t instanceof android.security.KeyStoreException) {
                    if (((android.security.KeyStoreException) t).getErrorCode()
                            == ErrorCode.CANNOT_ATTEST_IDS) {
                        return KeyChain.KEY_ATTESTATION_CANNOT_ATTEST_IDS;
                    }
                }
                Log.e(TAG, "KeyStore error.", e);
                return KeyChain.KEY_GEN_FAILURE;
            }
        }

        @Override public boolean setKeyPairCertificate(String alias, byte[] userCertificate,
                byte[] userCertificateChain) {
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);

            final PrivateKey privateKey;
            try {
                final Key key = mKeyStore.getKey(alias, null);
                if (! (key instanceof PrivateKey)) {
                    return false;
                }
                privateKey = (PrivateKey) key;
            } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
                Log.e(TAG, "Failed to get private key entry.", e);
                return false;
            }

            final ArrayList<Certificate> certs = new ArrayList<>();
            try {
                if (userCertificate != null) {
                    certs.add(parseCertificate(userCertificate));
                }
                if (userCertificateChain != null) {
                    certs.addAll(parseCertificates(userCertificateChain));
                }
            } catch (CertificateException e) {
                Log.e(TAG, "Failed to parse user certificate.", e);
                return false;
            }

            final Certificate[] certsArray = certs.toArray(new Certificate[0]);

            try {
                // setKeyEntry with a private key loaded from AndroidKeyStore replaces
                // the certificate components without touching the private key if
                // the alias is the same as that of the private key.
                mKeyStore.setKeyEntry(alias, privateKey, null, certsArray);
            } catch (KeyStoreException e) {
                Log.e(TAG, "Failed update key certificates.", e);
                return false;
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("Set certificate for key alias %s : user %s CA chain: %s",
                        alias, emptyOrBase64Encoded(userCertificate),
                        emptyOrBase64Encoded(userCertificateChain)));
            }
            broadcastKeychainChange();
            broadcastLegacyStorageChange();
            return true;
        }

        private void validateAlias(String alias) {
            if (alias == null) {
                throw new NullPointerException("alias == null");
            }
        }

        private boolean hasGrant(String alias, CallerIdentity caller) {
            return hasGrant(alias, caller.mUid);
        }

        private boolean hasGrant(String alias, int targetUid) {
            validateAlias(alias);

            if (!mGrantsDb.hasGrant(targetUid, alias)) {
                Log.w(TAG, String.format(
                        "uid %d doesn't have permission to access the requested alias %s",
                        targetUid, alias));
                return false;
            }

            return true;
        }

        @Override public String installCaCertificate(byte[] caCertificate) {
            final CallerIdentity caller = getCaller();
            Preconditions.checkCallAuthorization(isSystemUid(caller) || isCertInstaller(caller),
                    MSG_NOT_SYSTEM_OR_CERT_INSTALLER);
            final String alias;
            String subject = null;
            final boolean isSecurityLoggingEnabled = mInjector.isSecurityLoggingEnabled();
            final X509Certificate cert;
            try {
                cert = parseCertificate(caCertificate);

                final boolean isDebugLoggable = Log.isLoggable(TAG, Log.DEBUG);
                subject = cert.getSubjectX500Principal().getName(X500Principal.CANONICAL);
                if (isDebugLoggable) {
                    Log.d(TAG, String.format("Installing CA certificate: %s", subject));
                }

                synchronized (mTrustedCertificateStore) {
                    mTrustedCertificateStore.installCertificate(cert);
                    alias = mTrustedCertificateStore.getCertificateAlias(cert);
                }
            } catch (IOException | CertificateException e) {
                Log.w(TAG, "Failed installing CA certificate", e);
                if (isSecurityLoggingEnabled && subject != null) {
                    mInjector.writeSecurityEvent(
                            TAG_CERT_AUTHORITY_INSTALLED, 0 /*result*/, subject,
                            UserHandle.myUserId());
                }
                throw new IllegalStateException(e);
            }
            if (isSecurityLoggingEnabled && subject != null) {
                mInjector.writeSecurityEvent(
                        TAG_CERT_AUTHORITY_INSTALLED, 1 /*result*/, subject,
                        UserHandle.myUserId());
            }

            // If the caller is the cert installer, install the CA certificate into KeyStore.
            // This is a temporary solution to enable CA certificates to be used as VPN trust
            // anchors. Ultimately, the user should explicitly choose to install the VPN trust
            // anchor separately and independently of CA certificates, at which point this code
            // should be removed.
            if (CERT_INSTALLER_PACKAGE.equals(caller.mPackageName)) {
                try {
                    mKeyStore.setCertificateEntry(String.format("%s %s", subject, alias), cert);
                } catch(KeyStoreException e) {
                    Log.e(TAG, String.format(
                            "Attempted installing %s (subject: %s) to KeyStore. Failed", alias,
                            subject), e);
                }
            }

            broadcastLegacyStorageChange();
            broadcastTrustStoreChange();
            return alias;
        }

        /**
         * Install a key pair to the keystore.
         *
         * @param privateKey The private key associated with the client certificate
         * @param userCertificate The client certificate to be installed
         * @param userCertificateChain The rest of the chain for the client certificate
         * @param alias The alias under which the key pair is installed. It is invalid to pass
         *              {@code KeyChain.KEY_ALIAS_SELECTION_DENIED}.
         * @param uid Can be only one of two values: Either
         *            {@code android.security.KeyStore.UID_SELF} to indicate installation into the
         *            current user's system Keystore instance, or {@code Process.WIFI_UID} to
         *            indicate installation into the main user's WiFi Keystore instance. It is only
         *            valid to pass {@code Process.WIFI_UID} to the KeyChain service on user 0.
         * @return Whether the operation succeeded or not.
         */
        @Override public boolean installKeyPair(@Nullable byte[] privateKey,
                @Nullable byte[] userCertificate, @Nullable byte[] userCertificateChain,
                String alias, int uid) {
            final CallerIdentity caller = getCaller();
            Preconditions.checkCallAuthorization(isSystemUid(caller) || isCertInstaller(caller),
                    MSG_NOT_SYSTEM_OR_CERT_INSTALLER);
            if (KeyChain.KEY_ALIAS_SELECTION_DENIED.equals(alias)) {
                throw new IllegalArgumentException("The alias specified for the key denotes "
                        + "a reserved value and cannot be used to name a key");
            }
            if (!ALLOWED_UIDS.contains(uid)) {
                Log.e(TAG,
                        String.format("Installing alias %s as UID %d is now allowed.", alias, uid));
                return false;
            }

            if (privateKey == null && userCertificate == null && userCertificateChain == null) {
                Log.e(TAG, String.format("Nothing to install for alias %s", alias));
                return false;
            }

            if (uid == Process.WIFI_UID && UserHandle.myUserId() != UserHandle.USER_SYSTEM) {
                Log.e(TAG, String.format(
                        "Installation into the WiFi Keystore should be called from the primary "
                                + "user, not user %d",
                        UserHandle.myUserId()));
                return false;
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("Installing certificate and key to alias %s to uid %d: "
                                + "user cert %s CA chain: %s", alias, uid,
                                emptyOrBase64Encoded(userCertificate),
                                emptyOrBase64Encoded(userCertificateChain)));
            }

            final ArrayList<Certificate> certs = new ArrayList<>();
            try {
                if (userCertificate != null) {
                    certs.add(parseCertificate(userCertificate));
                }
                if (userCertificateChain != null) {
                    certs.addAll(parseCertificates(userCertificateChain));
                }
            } catch (CertificateException e) {
                Log.e(TAG, "Failed to parse user certificate.", e);
                return false;
            }

            if (certs.isEmpty()) {
                Log.e(TAG, "Cannot install private key without public certificate.");
                return false;
            }

            final Certificate[] certificates = certs.toArray(new Certificate[0]);

            final PrivateKey privateKey1;
            try {
                if (privateKey != null) {
                    final KeyFactory keyFactory =
                            KeyFactory.getInstance(certificates[0].getPublicKey().getAlgorithm());
                    privateKey1 = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKey));
                } else {
                    privateKey1 = null;
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                Log.e(TAG, "Failed to parse private key.", e);
                return false;
            }

            KeyStore keystore = getKeyStore(uid == Process.WIFI_UID);
            if (keystore == null) {
                return false;
            }

            try {
                if (privateKey != null) {
                    keystore.setKeyEntry(alias, privateKey1, null, certificates);
                } else {
                    if (certificates.length > 1) {
                        Log.e(TAG,
                                "Cannot install key certificate chain without private key.");
                        return false;
                    }
                    keystore.setCertificateEntry(alias, certificates[0]);
                }
            } catch (KeyStoreException e) {
                Log.e(TAG, "Failed to install key pair.", e);
            }

            broadcastKeychainChange();
            broadcastLegacyStorageChange();
            return true;
        }

        @Override public boolean removeKeyPair(String alias) {
            final CallerIdentity caller = getCaller();
            Preconditions.checkCallAuthorization(isSystemUid(caller) || isCertInstaller(caller),
                    MSG_NOT_SYSTEM_OR_CERT_INSTALLER);
            return removeKeyPairInternal(alias);
        }

        private boolean removeKeyPairInternal(String alias) {
            try {
                mKeyStore.deleteEntry(alias);
            } catch (KeyStoreException e) {
                Log.e(TAG, String.format(
                        "Failed not remove keystore entry with alias %s", alias));
                return false;
            }
            Log.w(TAG, String.format(
                    "WARNING: Removing alias %s, existing grants will be revoked.", alias));
            mGrantsDb.removeAliasInformation(alias);
            broadcastKeychainChange();
            broadcastLegacyStorageChange();
            return true;
        }

        @Override public boolean containsKeyPair(String alias) {
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            try {
                final Key key = mKeyStore.getKey(alias, null);
                return key instanceof PrivateKey;
            } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
                Log.w("Error while trying to check for key presence.", e);
                return false;
            }
        }

        private X509Certificate parseCertificate(byte[] bytes) throws CertificateException {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(bytes));
        }
        private Collection<X509Certificate> parseCertificates(byte[] bytes)
                throws CertificateException {
            final CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (Collection<X509Certificate>)
                    cf.generateCertificates(new ByteArrayInputStream(bytes));
        }

        @Override public boolean reset() {
            // only Settings should be able to reset
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            mGrantsDb.removeAllAliasesInformation();
            boolean ok = true;
            synchronized (mTrustedCertificateStore) {
                // delete user-installed CA certs
                for (String alias : mTrustedCertificateStore.aliases()) {
                    if (TrustedCertificateStore.isUser(alias)) {
                        if (!deleteCertificateEntry(alias)) {
                            ok = false;
                        }
                    }
                }
            }
            broadcastTrustStoreChange();
            broadcastKeychainChange();
            broadcastLegacyStorageChange();
            return ok;
        }

        @Override public boolean deleteCaCertificate(String alias) {
            // only Settings should be able to delete
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            boolean ok = true;
            Log.i(TAG, String.format("Deleting CA certificate %s", alias));
            synchronized (mTrustedCertificateStore) {
                ok = deleteCertificateEntry(alias);
            }
            broadcastTrustStoreChange();
            broadcastLegacyStorageChange();
            return ok;
        }

        private boolean deleteCertificateEntry(String alias) {
            String subjectForAudit = null;
            if (mInjector.isSecurityLoggingEnabled()) {
                final Certificate cert = mTrustedCertificateStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    subjectForAudit = ((X509Certificate) cert)
                            .getSubjectX500Principal().getName(X500Principal.CANONICAL);
                }
            }

            try {
                mTrustedCertificateStore.deleteCertificateEntry(alias);
                if (subjectForAudit != null) {
                    mInjector.writeSecurityEvent(
                            TAG_CERT_AUTHORITY_REMOVED, 1 /*result*/, subjectForAudit,
                            UserHandle.myUserId());
                }
                return true;
            } catch (IOException | CertificateException e) {
                Log.w(TAG, "Problem removing CA certificate " + alias, e);
                if (subjectForAudit != null) {
                    mInjector.writeSecurityEvent(
                            TAG_CERT_AUTHORITY_REMOVED, 0 /*result*/, subjectForAudit,
                            UserHandle.myUserId());
                }
                return false;
            }
        }

        private boolean hasManageCredentialManagementAppPermission(CallerIdentity caller) {
            return mContext.checkPermission(Manifest.permission.MANAGE_CREDENTIAL_MANAGEMENT_APP,
                    caller.mPid, caller.mUid) == PackageManager.PERMISSION_GRANTED;
        }

        private boolean isCertInstaller(CallerIdentity caller) {
            return caller.mPackageName != null
                    && CERT_INSTALLER_PACKAGE.equals(caller.mPackageName);
        }

        private boolean isCredentialManagementApp(CallerIdentity caller) {
            synchronized (mCredentialManagementAppLock) {
                return mCredentialManagementApp != null && caller.mPackageName != null
                        && caller.mPackageName.equals(mCredentialManagementApp.getPackageName());
            }
        }

        private boolean isSystemUid(CallerIdentity caller) {
            return UserHandle.isSameApp(caller.mUid, Process.SYSTEM_UID);
        }

        @Override public boolean hasGrant(int uid, String alias) {
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            return mGrantsDb.hasGrant(uid, alias);
        }

        @Override public boolean setGrant(int uid, String alias, boolean granted) {
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            Preconditions.checkArgument(containsKeyPair(alias),
                    "Alias not associated with a key.");
            mGrantsDb.setGrant(uid, alias, granted);
            if (!granted) {
                try {
                    KeyStore2.getInstance().ungrant(makeKeyDescriptor(alias), uid);
                } catch (android.security.KeyStoreException e) {
                    Log.e(TAG, "Failed to ungrant " + alias + " to uid: " + uid, e);
                    return false;
                }
            }
            broadcastPermissionChange(uid, alias, granted);
            broadcastLegacyStorageChange();
            return true;
        }

        @Override public int[] getGrants(String alias) {
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            try {
                if (mKeyStore.isKeyEntry(alias)) {
                    return mGrantsDb.getGrants(alias);
                }
            } catch (KeyStoreException e) {
                Log.w(TAG, "Error while checking if key exists.", e);
            }
            throw new IllegalArgumentException("Alias not found: " + alias);
        }

        @Override
        public StringParceledListSlice getUserCaAliases() {
            synchronized (mTrustedCertificateStore) {
                return new StringParceledListSlice(new ArrayList<String>(
                        mTrustedCertificateStore.userAliases()));
            }
        }

        @Override
        public StringParceledListSlice getSystemCaAliases() {
            synchronized (mTrustedCertificateStore) {
                return new StringParceledListSlice(new ArrayList<String>(
                        mTrustedCertificateStore.allSystemAliases()));
            }
        }

        @Override
        public boolean containsCaAlias(String alias) {
            return mTrustedCertificateStore.containsAlias(alias);
        }

        @Override
        public byte[] getEncodedCaCertificate(String alias, boolean includeDeletedSystem) {
            synchronized (mTrustedCertificateStore) {
                X509Certificate certificate = (X509Certificate) mTrustedCertificateStore
                        .getCertificate(alias, includeDeletedSystem);
                if (certificate == null) {
                    Log.w(TAG, "Could not find CA certificate " + alias);
                    return null;
                }
                try {
                    return certificate.getEncoded();
                } catch (CertificateEncodingException e) {
                    Log.w(TAG, "Error while encoding CA certificate " + alias);
                    return null;
                }
            }
        }

        @Override
        public List<String> getCaCertificateChainAliases(String rootAlias,
                boolean includeDeletedSystem) {
            synchronized (mTrustedCertificateStore) {
                X509Certificate root = (X509Certificate) mTrustedCertificateStore.getCertificate(
                        rootAlias, includeDeletedSystem);
                try {
                    List<X509Certificate> chain = mTrustedCertificateStore.getCertificateChain(
                            root);
                    List<String> aliases = new ArrayList<String>(chain.size());
                    final int n = chain.size();
                    for (int i = 0; i < n; ++i) {
                        String alias = mTrustedCertificateStore.getCertificateAlias(chain.get(i),
                                true);
                        if (alias != null) {
                            aliases.add(alias);
                        }
                    }
                    return aliases;
                } catch (CertificateException e) {
                    Log.w(TAG, "Error retrieving cert chain for root " + rootAlias);
                    return Collections.emptyList();
                }
            }
        }

        @Override
        public void setCredentialManagementApp(@NonNull String packageName,
                @NonNull AppUriAuthenticationPolicy authenticationPolicy) {
            final CallerIdentity caller = getCaller();
            Preconditions.checkCallAuthorization(isSystemUid(caller)
                    || hasManageCredentialManagementAppPermission(caller), MSG_NOT_SYSTEM);
            checkValidAuthenticationPolicy(authenticationPolicy);

            synchronized (mCredentialManagementAppLock) {
                if (mCredentialManagementApp != null) {
                    final String existingPackage = mCredentialManagementApp.getPackageName();
                    if (existingPackage.equals(packageName)) {
                        // Update existing credential management app's policy
                        removeOrphanedKeyPairs(authenticationPolicy);
                    } else {
                        // Replace existing credential management app
                        removeOrphanedKeyPairs(null);
                        setManageCredentialsAppOps(existingPackage, false);
                    }
                }
                setManageCredentialsAppOps(packageName, true);
                mCredentialManagementApp = new CredentialManagementApp(packageName,
                        authenticationPolicy);
                mStateStorage.saveCredentialManagementApp(mCredentialManagementApp);
            }
        }

        private void setManageCredentialsAppOps(String packageName, boolean allowed) {
            try {
                int mode = allowed ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_DEFAULT;
                ApplicationInfo appInfo = getPackageManager().getApplicationInfo(packageName, 0);
                getSystemService(AppOpsManager.class).setMode(AppOpsManager.OP_MANAGE_CREDENTIALS,
                        appInfo.uid, packageName, mode);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Unable to find info for package: " + packageName);
            }
        }

        private void removeOrphanedKeyPairs(
                @Nullable AppUriAuthenticationPolicy newPolicy) {
            Set<String> existingAliases = mCredentialManagementApp.getAuthenticationPolicy()
                    .getAliases();
            Set<String> newAliases = newPolicy != null ? newPolicy.getAliases() : new HashSet<>();

            // Uninstall all certificates that are no longer included in the new
            // authentication policy
            for (String existingAlias : existingAliases) {
                if (!newAliases.contains(existingAlias)) {
                    removeKeyPairInternal(existingAlias);
                }
            }
        }

        private void checkValidAuthenticationPolicy(
                @NonNull AppUriAuthenticationPolicy authenticationPolicy) {
            if (authenticationPolicy == null
                    || authenticationPolicy.getAppAndUriMappings().isEmpty()) {
                throw new IllegalArgumentException("The authentication policy is null or empty");
            }
            // Check whether any of the aliases in the policy already exist
            for (String alias : authenticationPolicy.getAliases()) {
                if (requestPrivateKey(alias) != null) {
                    throw new IllegalArgumentException(String.format("The authentication policy "
                            + "contains an installed alias: %s", alias));
                }
            }
        }

        @Override
        public boolean hasCredentialManagementApp() {
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            synchronized (mCredentialManagementAppLock) {
                return mCredentialManagementApp != null;
            }
        }

        @Nullable
        @Override
        public String getCredentialManagementAppPackageName() {
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            synchronized (mCredentialManagementAppLock) {
                return mCredentialManagementApp != null
                        ? mCredentialManagementApp.getPackageName()
                        : null;
            }
        }

        @Nullable
        @Override
        public AppUriAuthenticationPolicy getCredentialManagementAppPolicy() {
            final CallerIdentity caller = getCaller();
            Preconditions.checkCallAuthorization(isSystemUid(caller)
                            || isCredentialManagementApp(caller), MSG_NOT_SYSTEM_OR_CRED_MNG_APP);
            synchronized (mCredentialManagementAppLock) {
                return mCredentialManagementApp != null
                        ? mCredentialManagementApp.getAuthenticationPolicy()
                        : null;
            }
        }

        @Nullable
        @Override
        public String getPredefinedAliasForPackageAndUri(@NonNull String packageName,
                @Nullable Uri uri) {
            Preconditions.checkCallAuthorization(isSystemUid(getCaller()), MSG_NOT_SYSTEM);
            synchronized (mCredentialManagementAppLock) {
                if (mCredentialManagementApp == null || uri == null) {
                    return null;
                }
                Map<Uri, String> urisToAliases = mCredentialManagementApp.getAuthenticationPolicy()
                        .getAppAndUriMappings().get(packageName);
                return urisToAliases != null ? urisToAliases.get(uri) : null;
            }
        }

        @Override
        public void removeCredentialManagementApp() {
            final CallerIdentity caller = getCaller();
            Preconditions.checkCallAuthorization(isSystemUid(caller)
                            || isCredentialManagementApp(caller)
                            || hasManageCredentialManagementAppPermission(caller),
                    MSG_NOT_SYSTEM_OR_CRED_MNG_APP);
            synchronized (mCredentialManagementAppLock) {
                if (mCredentialManagementApp != null) {
                    // Remove all certificates
                    removeOrphanedKeyPairs(null);
                    setManageCredentialsAppOps(mCredentialManagementApp.getPackageName(), false);
                }
                mCredentialManagementApp = null;
                mStateStorage.saveCredentialManagementApp(mCredentialManagementApp);
            }
        }

        @Override
        public boolean isCredentialManagementApp(@NonNull String packageName) {
            final CallerIdentity caller = getCaller();
            Preconditions.checkCallAuthorization(isSystemUid(caller)
                    || isCredentialManagementApp(caller), MSG_NOT_SYSTEM_OR_CRED_MNG_APP);
            synchronized (mCredentialManagementAppLock) {
                return packageName.equals(mCredentialManagementApp.getPackageName());
            }
        }
    };

    @Override public IBinder onBind(Intent intent) {
        if (IKeyChainService.class.getName().equals(intent.getAction())) {
            return mIKeyChainService;
        }
        return null;
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
            mGrantsDb.purgeOldGrants(getPackageManager());
        }
    }

    private void broadcastLegacyStorageChange() {
        Intent intent = new Intent(KeyChain.ACTION_STORAGE_CHANGED);
        BroadcastOptions opts = BroadcastOptions.makeBasic();
        opts.setMaxManifestReceiverApiLevel(Build.VERSION_CODES.N_MR1);
        sendBroadcastAsUser(intent, UserHandle.of(UserHandle.myUserId()), null, opts.toBundle());
    }

    private void broadcastKeychainChange() {
        Intent intent = new Intent(KeyChain.ACTION_KEYCHAIN_CHANGED);
        sendBroadcastAsUser(intent, UserHandle.of(UserHandle.myUserId()));
    }

    private void broadcastTrustStoreChange() {
        Intent intent = new Intent(KeyChain.ACTION_TRUST_STORE_CHANGED);
        sendBroadcastAsUser(intent, UserHandle.of(UserHandle.myUserId()));
    }

    private void broadcastPermissionChange(int uid, String alias, boolean access) {
        // Since the permission change only impacts one uid only send to that uid's packages.
        final PackageManager packageManager = getPackageManager();
        String[] packages = packageManager.getPackagesForUid(uid);
        if (packages == null) {
            return;
        }
        for (String pckg : packages) {
            Intent intent = new Intent(KeyChain.ACTION_KEY_ACCESS_CHANGED);
            intent.putExtra(KeyChain.EXTRA_KEY_ALIAS, alias);
            intent.putExtra(KeyChain.EXTRA_KEY_ACCESSIBLE, access);
            intent.setPackage(pckg);
            sendBroadcastAsUser(intent, UserHandle.of(UserHandle.myUserId()));
        }
    }

    private static String emptyOrBase64Encoded(byte[] cert) {
        if (cert == null) {
            return "";
        }
        return Base64.encodeToString(cert, Base64.NO_WRAP);
    }

    private final class CallerIdentity {

        final int mUid;
        final int mPid;
        final String mPackageName;

        CallerIdentity() {
            mUid = mInjector.getCallingUid();
            mPid = Binder.getCallingPid();
            mPackageName = getPackageManager().getNameForUid(mUid);
        }
    }

    private CallerIdentity getCaller() {
        return new CallerIdentity();
    }

    @VisibleForTesting
    void setInjector(Injector injector) {
        mInjector = injector;
    }

    /**
     * Injector for mocking out dependencies in tests.
     */
    @VisibleForTesting
    static class Injector {
        public boolean isSecurityLoggingEnabled() {
            return SecurityLog.isLoggingEnabled();
        }

        public void writeSecurityEvent(int tag, Object... payload) {
            SecurityLog.writeEvent(tag, payload);
        }

        public int getCallingUid() {
            return Binder.getCallingUid();
        }

        public KeyStore getKeyStoreInstance() throws KeyStoreException {
            return KeyStore.getInstance("AndroidKeyStore");
        }
    }
}
