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
package com.android.keychain.tests;

import static android.os.Process.WIFI_UID;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.security.Credentials;
import android.security.IKeyChainService;
import android.security.KeyChain;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.ParcelableKeyGenParameterSpec;
import android.util.Base64;
import android.util.Log;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import com.android.keychain.tests.support.IKeyChainServiceTestSupport;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import libcore.java.security.TestKeyStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BasicKeyChainServiceTest {
    private static final String TAG = "BasicKeyChainServiceTest";
    private static final String ALIAS_1 = "client";
    private static final String ALIAS_IMPORTED = "imported";
    private static final String ALIAS_GENERATED = "generated";
    public static final byte[] DUMMY_CHALLENGE = {'a', 'b', 'c'};
    private static final String ALIAS_NON_EXISTING = "nonexisting";

    private Context mContext;

    private final ConditionVariable mSupportServiceAvailable = new ConditionVariable(false);
    private IKeyChainServiceTestSupport mTestSupportService;
    private boolean mIsSupportServiceBound;

    private ServiceConnection mSupportConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.d(TAG, "test support service connected!");
                    mTestSupportService = IKeyChainServiceTestSupport.Stub.asInterface(service);
                    mSupportServiceAvailable.open();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mSupportServiceAvailable.close();
                    mTestSupportService = null;
                }
            };

    private final ConditionVariable mKeyChainAvailable = new ConditionVariable(false);
    private IKeyChainService mKeyChainService;
    private boolean mIsKeyChainServiceBound;

    private ServiceConnection mServiceConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.d(TAG, "KeyChain service connected!");
                    mKeyChainService = IKeyChainService.Stub.asInterface(service);
                    mKeyChainAvailable.open();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mKeyChainAvailable.close();
                    mKeyChainService = null;
                }
            };

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        bindTestSupportService();
        assertThat(mIsSupportServiceBound).isTrue();
        bindKeyChainService();
        assertThat(mIsKeyChainServiceBound).isTrue();

        waitForSupportService();
        waitForKeyChainService();
    }

    @After
    public void tearDown() {
        // Clean up keys that might have been left over
        try {
            removeKeyPair(ALIAS_IMPORTED);
            removeKeyPair(ALIAS_GENERATED);
            removeKeyPair(ALIAS_1);
        } catch (RemoteException e) {
            // Nothing to do here but warn that clean-up was not successful.
            Log.w(TAG, "Failed cleaning up installed keys", e);
        }
        unbindTestSupportService();
        assertThat(mIsSupportServiceBound).isFalse();
        unbindKeyChainService();
        assertThat(mIsKeyChainServiceBound).isFalse();
    }

    @Test
    public void testCanAccessKeyAfterGettingGrant()
            throws RemoteException, IOException, CertificateException {
        Log.d(TAG, "Testing access to imported key after getting grant.");
        removeKeyPair(ALIAS_1);
        generateRsaKey(ALIAS_1);
        assertThat(mKeyChainService.requestPrivateKey(ALIAS_1)).isNull();
        mTestSupportService.grantAppPermission(Process.myUid(), ALIAS_1);
        assertThat(mKeyChainService.requestPrivateKey(ALIAS_1)).isNotNull();
    }

    @Test
    public void testInstallAndRemoveKeyPair()
            throws RemoteException, IOException, CertificateException {
        Log.d(TAG, "Testing importing key.");
        removeKeyPair(ALIAS_IMPORTED);
        // No key installed, all should fail.
        assertThat(mKeyChainService.requestPrivateKey(ALIAS_IMPORTED)).isNull();
        assertThat(mKeyChainService.getCertificate(ALIAS_IMPORTED)).isNull();
        assertThat(mKeyChainService.getCaCertificates(ALIAS_IMPORTED)).isNull();

        PrivateKeyEntry privateKeyEntry =
                TestKeyStore.getClientCertificate().getPrivateKey("RSA", "RSA");
        assertThat(mTestSupportService.installKeyPair(privateKeyEntry.getPrivateKey().getEncoded(),
                    privateKeyEntry.getCertificate().getEncoded(),
                    Credentials.convertToPem(privateKeyEntry.getCertificateChain()),
                    ALIAS_IMPORTED)).isTrue();

        // No grant, Private key access should still fail.
        assertThat(mKeyChainService.requestPrivateKey(ALIAS_IMPORTED)).isNull();
        // Certificate access succeeds because this test runs as AID_SYSTEM.
        assertThat(mKeyChainService.getCertificate(ALIAS_IMPORTED)).isNotNull();
        assertThat(mKeyChainService.getCaCertificates(ALIAS_IMPORTED)).isNotNull();
        // Grant access
        mTestSupportService.grantAppPermission(Process.myUid(), ALIAS_IMPORTED);
        // Has grant, all should succeed.
        assertThat(mKeyChainService.requestPrivateKey(ALIAS_IMPORTED)).isNotNull();
        assertThat(mKeyChainService.getCertificate(ALIAS_IMPORTED)).isNotNull();
        assertThat(mKeyChainService.getCaCertificates(ALIAS_IMPORTED)).isNotNull();
        // Finally, test removal.
        assertThat(mTestSupportService.removeKeyPair(ALIAS_IMPORTED)).isTrue();
    }

    @Test
    public void testUserSelectability() throws RemoteException, IOException, CertificateException {
        Log.d(TAG, "Testing user-selectability of a key.");
        removeKeyPair(ALIAS_IMPORTED);
        PrivateKeyEntry privateKeyEntry =
                TestKeyStore.getClientCertificate().getPrivateKey("RSA", "RSA");
        assertThat(mTestSupportService.installKeyPair(privateKeyEntry.getPrivateKey().getEncoded(),
                privateKeyEntry.getCertificate().getEncoded(),
                Credentials.convertToPem(privateKeyEntry.getCertificateChain()),
                ALIAS_IMPORTED)).isTrue();

        assertThat(mKeyChainService.isUserSelectable(ALIAS_IMPORTED)).isFalse();
        mTestSupportService.setUserSelectable(ALIAS_IMPORTED, true);
        assertThat(mKeyChainService.isUserSelectable(ALIAS_IMPORTED)).isTrue();
        mTestSupportService.setUserSelectable(ALIAS_IMPORTED, false);
        assertThat(mKeyChainService.isUserSelectable(ALIAS_IMPORTED)).isFalse();

        // Remove key
        assertThat(mTestSupportService.removeKeyPair(ALIAS_IMPORTED)).isTrue();
    }

    @Test
    public void testGenerateKeyPairErrorsOnBadUid() throws RemoteException {
        KeyGenParameterSpec specBadUid =
                new KeyGenParameterSpec.Builder(buildRsaKeySpec(ALIAS_GENERATED))
                .setUid(WIFI_UID)
                .build();
        ParcelableKeyGenParameterSpec parcelableSpec =
                new ParcelableKeyGenParameterSpec(specBadUid);
        assertThat(mTestSupportService.generateKeyPair("RSA", parcelableSpec)).isEqualTo(
                KeyChain.KEY_GEN_MISSING_ALIAS);
    }

    @Test
    public void testGenerateKeyPairErrorsOnInvalidAlgorithm() throws RemoteException {
        ParcelableKeyGenParameterSpec parcelableSpec = new ParcelableKeyGenParameterSpec(
                buildRsaKeySpec(ALIAS_GENERATED));
        assertThat(mTestSupportService.generateKeyPair("BADBAD", parcelableSpec)).isEqualTo(
                KeyChain.KEY_GEN_NO_SUCH_ALGORITHM);
    }

    @Test
    public void testGenerateKeyPairErrorsOnInvalidAlgorithmParameters() throws RemoteException {
        ParcelableKeyGenParameterSpec parcelableSpec = new ParcelableKeyGenParameterSpec(
                buildRsaKeySpec(ALIAS_GENERATED));
        // RSA key parameters do not make sense for Elliptic Curve
        assertThat(mTestSupportService.generateKeyPair("EC", parcelableSpec)).isEqualTo(
                KeyChain.KEY_GEN_INVALID_ALGORITHM_PARAMETERS);
    }

    @Test
    public void testGenerateKeyPairSucceeds() throws RemoteException {
        generateRsaKey(ALIAS_GENERATED);
        // Test that there are no grants by default
        assertThat(mKeyChainService.requestPrivateKey(ALIAS_GENERATED)).isNull();
        // And is not user-selectable by default
        assertThat(mKeyChainService.isUserSelectable(ALIAS_GENERATED)).isFalse();
        // But after granting access, it can be used.
        mTestSupportService.grantAppPermission(Process.myUid(), ALIAS_GENERATED);
        assertThat(mKeyChainService.requestPrivateKey(ALIAS_GENERATED)).isNotNull();
    }

    private static final String USER_CERT1 =
            "MIIC+DCCAeACAQEwDQYJKoZIhvcNAQELBQAwRTELMAkGA1UEBhMCVVMxEzARBgNV" +
            "BAgMClNvbWUtU3RhdGUxITAfBgNVBAoMGEludGVybmV0IFdpZGdpdHMgUHR5IEx0" +
            "ZDAeFw0yMTAyMTAwMDEyMDNaFw00ODA2MjgwMDEyMDNaMD8xCzAJBgNVBAYTAlVT" +
            "MQswCQYDVQQIDAJDQTEUMBIGA1UEBwwLTWVkaW9jcmV0b24xDTALBgNVBAoMBENv" +
            "Q28wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC2LieaIDILjZPtNvZI" +
            "I6VSvKIPIdY5STadoWjmt9TqjyvszJ+oEceBPqwevOdtXpJCiHhP91B314XBT33z" +
            "8re1b0A5k31YKcy0PU+3OMh4XG6O3/Z5/9GfsekfQZK3jagbn3uqJ2emyj0JK+HY" +
            "ipD6iwyO21DerUYavPpC0uo8PKAxc9l6XjILg9qoi68yCi8P3tkLLAFWCsc7GUkA" +
            "v97zUjGq8iz/gIrwmzJBB3O//7e1nuHO5AswgJiwWa9aY6uHKWm97xP0Kw4pShFI" +
            "eEsdNKj82FURpWIvrWsztYPaEM/+nG8cNiJ1XRT3DfnlKzXVVQi7pE82HkUpFNLQ" +
            "4wCbAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAJwq05ykEDK5etwC5jDYsX7hyiYI" +
            "TAephAfhMqT7CtwScVlurSWt4ivv3IBx2huibQqdwq9zyCG9UzQKACMrEAaINerr" +
            "VPwq+MpfgOUJRlZOCchr1KaN+quoOstStsGqemHZSTyUow6OrG2a+DIGQZy84yza" +
            "y4+NZYtTjbVKFAmQkji344cVd6qFjCG5Dgo+1u4+YOWV4QTVWwbjgjCGiDNyZKeO" +
            "Y8n8pdsv4ygat4VCotch22bqiUJLgY/abiprqKz3jfkko9urHkWsrdqzc3mS29AC" +
            "tWVhomOQq/51A0wfQRbSw2MY+j5LaF22Sz5rnPk0u6Vcm2eLUI5gSJ/HZOU=";

    private static final String USER_CERT2 =
            "MIIDETCCAfkCFDEBBiFSwkBDAZMyhMjrr7P4wHklMA0GCSqGSIb3DQEBCwUAMEUx" +
            "CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRl" +
            "cm5ldCBXaWRnaXRzIFB0eSBMdGQwHhcNMjEwMjIyMTgwNzE2WhcNNDgwNzEwMTgw" +
            "NzE2WjBFMQswCQYDVQQGEwJVUzETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UE" +
            "CgwYSW50ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOC" +
            "AQ8AMIIBCgKCAQEA8XLlpvOvG22fJ4ZNSEdc/QIzkbjmtP5rmqX8dgfZdZ7xdUNO" +
            "24Coyv9f+GiXJdDlL2+KIK3ZTpcuVdd5oO+GdrxB1Dnvhqt2YgOb/+VyeMOdsEre" +
            "Bqw4Y+v8A92zpWlYlh9xyjVE53ismdQg2RT8kIxQt2ydMyZRx8KzNmZRsXouiVSi" +
            "ngWlXdtkpXNiVt5CkSBwscfVNMkF4EfcKqYGLJzFTXcSsRVZoknGNtgJZIsHaOJh" +
            "etmrfxATcbvNYdDhm9xs2ud/WwaRDqbMme9KVOrk9g5NBZIn7SpWDUyBk36W2CgQ" +
            "U/3OPnDOUS6oW+YKE1xvJ3i2FhLD2ufJyNh4WwIDAQABMA0GCSqGSIb3DQEBCwUA" +
            "A4IBAQDkl+8ZlYgKwSHjqxHjJsoBXBlwWUw0FQDaFz0tlPv/f5w80NsTXImVgRcP" +
            "MBWxLrVa/JKCm7GPZgIotvYsRKL0/DRZDUuva86C2hi9C/E7OtgFkDO0d/t9QIQM" +
            "gSNgdEgda/+lixc7XrsjJKAufFlYhzk/q5uyiD3SbqFzdlADumWtY9Xu6fqU2JB5" +
            "TFOTxqpU5c2b+sXL6uc+dA2pSP94wL+7g+uKVhdJGsimbXIq1jl9r+C2ykrUjuz+" +
            "b7uBJ5Qzq81tdmNCZ4pLtqmatTAMj2LYsKiXUe9Fh0nd/2aZek6I1YHaIBGg7jns" +
            "ZYEWPj4Rd0KiE3L/ymeQ4VQx6SW2";

    private static final String CA_CERT =
            "MIIDazCCAlOgAwIBAgIUVA2nyBJMc/OcO0C/yPt/E1TwREIwDQYJKoZIhvcNAQEL" +
            "BQAwRTELMAkGA1UEBhMCVVMxEzARBgNVBAgMClNvbWUtU3RhdGUxITAfBgNVBAoM" +
            "GEludGVybmV0IFdpZGdpdHMgUHR5IEx0ZDAeFw0yMTAyMDkyMzAzMzdaFw00ODA2" +
            "MjcyMzAzMzdaMEUxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApTb21lLVN0YXRlMSEw" +
            "HwYDVQQKDBhJbnRlcm5ldCBXaWRnaXRzIFB0eSBMdGQwggEiMA0GCSqGSIb3DQEB" +
            "AQUAA4IBDwAwggEKAoIBAQDxcuWm868bbZ8nhk1IR1z9AjORuOa0/muapfx2B9l1" +
            "nvF1Q07bgKjK/1/4aJcl0OUvb4ogrdlOly5V13mg74Z2vEHUOe+Gq3ZiA5v/5XJ4" +
            "w52wSt4GrDhj6/wD3bOlaViWH3HKNUTneKyZ1CDZFPyQjFC3bJ0zJlHHwrM2ZlGx" +
            "ei6JVKKeBaVd22Slc2JW3kKRIHCxx9U0yQXgR9wqpgYsnMVNdxKxFVmiScY22Alk" +
            "iwdo4mF62at/EBNxu81h0OGb3Gza539bBpEOpsyZ70pU6uT2Dk0FkiftKlYNTIGT" +
            "fpbYKBBT/c4+cM5RLqhb5goTXG8neLYWEsPa58nI2HhbAgMBAAGjUzBRMB0GA1Ud" +
            "DgQWBBSsg7KE2+ypUJrrk9pBIVYpgjZM3TAfBgNVHSMEGDAWgBSsg7KE2+ypUJrr" +
            "k9pBIVYpgjZM3TAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQBx" +
            "pUWwTL8vsrq3hNEDOui1Y6kYPiaAs2Fd//tBjPhgc9tlKS8KLkGU0yW8kEyPovvK" +
            "M6/IUDZhrHzli8Y9iJxiZ43o/fni1DOLHLU+CCOaKGrmBDBhgDlz1m2nXsCOb8uF" +
            "ohz4Yp1SLMy1YpQPmltiaILXNTw33B8xFa0d9ChZbBudyiiNs0vijdFMMYetqAGM" +
            "aXCjPXdM6hGjHxc6vf2ECJOrfVg3D22VOJ0WzrrJDM9pRxJgJ/IFG0eirKvE7G8s" +
            "zk1Og79JAFr3qMKEnUS7nuym7J69HSQlFHc7JEMzeS78YR7EHOlOlK/bEX/8cHF9" +
            "ZlGCOS1Ds0rmVe3CoQIp";

    @Test
    public void testSetKeyPairCertificate() throws RemoteException {
        generateRsaKey(ALIAS_GENERATED);
        final byte[] userCert = Base64.decode(USER_CERT1, Base64.DEFAULT);
        final byte[] certChain = Base64.decode(CA_CERT, Base64.DEFAULT);

        assertThat(mTestSupportService.setKeyPairCertificate(ALIAS_GENERATED, userCert,
                certChain)).isTrue();
        mTestSupportService.grantAppPermission(Process.myUid(), ALIAS_GENERATED);

        assertThat(mKeyChainService.getCertificate(ALIAS_GENERATED)).isEqualTo(userCert);
        assertThat(mKeyChainService.getCaCertificates(ALIAS_GENERATED)).isEqualTo(certChain);

        final byte[] newUserCert = Base64.decode(USER_CERT2, Base64.DEFAULT);

        assertThat(mTestSupportService.setKeyPairCertificate(ALIAS_GENERATED, newUserCert,
                null)).isTrue();
        assertThat(mKeyChainService.getCertificate(ALIAS_GENERATED)).isEqualTo(newUserCert);
        assertThat(mKeyChainService.getCaCertificates(ALIAS_GENERATED)).isNull();
    }

    @Test
    public void testInstallKeyPairErrorOnAliasSelectionDeniedKey() throws RemoteException,
            IOException, CertificateException {
        PrivateKeyEntry privateKeyEntry =
                TestKeyStore.getClientCertificate().getPrivateKey("RSA", "RSA");
        assertThrows(IllegalArgumentException.class, () -> {
                mTestSupportService.installKeyPair(
                        privateKeyEntry.getPrivateKey().getEncoded(),
                        privateKeyEntry.getCertificate().getEncoded(),
                        Credentials.convertToPem(privateKeyEntry.getCertificateChain()),
                        KeyChain.KEY_ALIAS_SELECTION_DENIED);
        });
    }

    @Test
    public void testGenerateKeyPairErrorOnAliasSelectionDeniedKey() throws RemoteException {
        ParcelableKeyGenParameterSpec parcelableSpec =
                new ParcelableKeyGenParameterSpec(buildRsaKeySpec(
                        KeyChain.KEY_ALIAS_SELECTION_DENIED));
        assertThrows(IllegalArgumentException.class, () -> {
                mTestSupportService.generateKeyPair("RSA", parcelableSpec);
        });
    }

    void generateRsaKey(String alias) throws RemoteException {
        ParcelableKeyGenParameterSpec parcelableSpec = new ParcelableKeyGenParameterSpec(
                buildRsaKeySpec(alias));
        assertThat(mTestSupportService.generateKeyPair("RSA", parcelableSpec)).isEqualTo(
                KeyChain.KEY_GEN_SUCCESS);
    }

    void removeKeyPair(String alias) throws RemoteException {
        assertThat(mTestSupportService.removeKeyPair(alias)).isTrue();
    }

    void bindTestSupportService() {
        Intent serviceIntent = new Intent(mContext, IKeyChainServiceTestSupport.class);
        serviceIntent.setComponent(
                new ComponentName(
                        "com.android.keychain.tests.support",
                        "com.android.keychain.tests.support.KeyChainServiceTestSupport"));
        Log.d(TAG, String.format("Binding intent: %s", serviceIntent));
        mIsSupportServiceBound =
                mContext.bindService(serviceIntent, mSupportConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, String.format("Support service binding result: %b", mIsSupportServiceBound));
    }

    void unbindTestSupportService() {
        if (mIsSupportServiceBound) {
            mContext.unbindService(mSupportConnection);
            mIsSupportServiceBound = false;
        }
    }

    void bindKeyChainService() {
        Context appContext = mContext.getApplicationContext();
        Intent intent = new Intent(IKeyChainService.class.getName());
        ComponentName comp = intent.resolveSystemService(appContext.getPackageManager(), 0);
        intent.setComponent(comp);

        Log.d(TAG, String.format("Binding to KeyChain: %s", intent));
        mIsKeyChainServiceBound =
                appContext.bindServiceAsUser(
                        intent,
                        mServiceConnection,
                        Context.BIND_AUTO_CREATE,
                        Process.myUserHandle());
        Log.d(TAG, String.format("KeyChain service binding result: %b", mIsKeyChainServiceBound));
    }

    void unbindKeyChainService() {
        if (mIsKeyChainServiceBound) {
            mContext.getApplicationContext().unbindService(mServiceConnection);
            mIsKeyChainServiceBound = false;
        }
    }

    @Test
    public void testContainsKeyPair_NonExisting() throws RemoteException {
        assertThat(mKeyChainService.containsKeyPair(ALIAS_NON_EXISTING)).isFalse();
    }

    @Test
    public void testContainsKeyPair_ImportedKey() throws Exception {
        installTestRsaKey();
        assertThat(mKeyChainService.containsKeyPair(ALIAS_IMPORTED)).isTrue();
    }

    @Test
    public void testContainsKeyPair_RemovedKey() throws Exception {
        installTestRsaKey();
        mKeyChainService.removeKeyPair(ALIAS_IMPORTED);
        assertThat(mKeyChainService.containsKeyPair(ALIAS_IMPORTED)).isFalse();
    }

    @Test
    public void testGetGrants_NonExisting() {
        assertThrows(IllegalArgumentException.class,
                () -> mKeyChainService.getGrants(ALIAS_NON_EXISTING));
    }

    @Test
    public void testGetGrants_Empty() throws Exception {
        installTestRsaKey();

        assertThat(mKeyChainService.getGrants(ALIAS_IMPORTED)).isEmpty();
    }

    @Test
    public void testGetGrants_NonEmpty() throws Exception {
        final int uid = Process.myUid();

        installTestRsaKey();
        mTestSupportService.grantAppPermission(uid, ALIAS_IMPORTED);

        assertThat(mKeyChainService.getGrants(ALIAS_IMPORTED)).isEqualTo(new int[] {uid});
    }

    private void installTestRsaKey() throws Exception {
        final PrivateKeyEntry privateKeyEntry =
                TestKeyStore.getClientCertificate().getPrivateKey("RSA", "RSA");
        mTestSupportService.installKeyPair(privateKeyEntry.getPrivateKey().getEncoded(),
                privateKeyEntry.getCertificate().getEncoded(),
                Credentials.convertToPem(privateKeyEntry.getCertificateChain()),
                ALIAS_IMPORTED);
    }

    void waitForSupportService() {
        Log.d(TAG, "Waiting for support service.");
        assertThat(mSupportServiceAvailable.block(10000)).isTrue();;
        assertThat(mTestSupportService).isNotNull();
    }

    void waitForKeyChainService() {
        Log.d(TAG, "Waiting for keychain service.");
        assertThat(mKeyChainAvailable.block(10000)).isTrue();;
        assertThat(mKeyChainService).isNotNull();
    }

    private KeyGenParameterSpec buildRsaKeySpec(String alias) {
        return new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setIsStrongBoxBacked(false)
                .build();
    }
}
