/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.rkpdapp.e2etest;

import static android.security.keystore.KeyProperties.KEY_ALGORITHM_EC;
import static android.security.keystore.KeyProperties.PURPOSE_SIGN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.hardware.security.keymint.IRemotelyProvisionedComponent;
import android.os.Process;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.security.KeyStoreException;
import android.security.keystore.KeyGenParameterSpec;
import android.system.keystore2.ResponseCode;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import com.android.rkpdapp.database.ProvisionedKey;
import com.android.rkpdapp.database.ProvisionedKeyDao;
import com.android.rkpdapp.database.RkpdDatabase;
import com.android.rkpdapp.interfaces.ServerInterface;
import com.android.rkpdapp.interfaces.ServiceManagerInterface;
import com.android.rkpdapp.interfaces.SystemInterface;
import com.android.rkpdapp.provisioner.PeriodicProvisioner;
import com.android.rkpdapp.testutil.FakeRkpServer;
import com.android.rkpdapp.testutil.SystemInterfaceSelector;
import com.android.rkpdapp.testutil.SystemPropertySetter;
import com.android.rkpdapp.utils.Settings;
import com.android.rkpdapp.utils.X509Utils;

import com.google.common.primitives.Bytes;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.ProviderException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.Executors;

@RunWith(Parameterized.class)
public class KeystoreIntegrationTest {
    // This is the SEQUENCE header and AlgorithmIdentifier that prefix the raw public key. This
    // lets us create DER-encoded SubjectPublicKeyInfo by concatenating the prefix with the raw key
    // to produce the following:
    //   SubjectPublicKeyInfo  ::=  SEQUENCE  {
    //       algorithm            AlgorithmIdentifier,
    //       subjectPublicKey     BIT STRING
    //   }
    private static final byte[] SUBJECT_PUBKEY_ASN1_PREFIX = new byte[]{
            48, 89, 48, 19, 6, 7, 42, -122, 72, -50, 61, 2, 1, 6, 8, 42, -122, 72, -50, 61, 3, 1,
            7, 3, 66, 0, 4};

    private static Context sContext;
    private final String mInstanceName;
    private final String mServiceName;
    private ProvisionedKeyDao mKeyDao;

    @Rule
    public final TestName mName = new TestName();
    private KeyStore mKeyStore;

    @Parameterized.Parameters(name = "{index}: instanceName={0}")
    public static String[] parameters() {
        return ServiceManager.getDeclaredInstances(IRemotelyProvisionedComponent.DESCRIPTOR);
    }

    public KeystoreIntegrationTest(String instanceName) {
        mInstanceName = instanceName;
        mServiceName = IRemotelyProvisionedComponent.DESCRIPTOR + "/" + instanceName;
    }

    @BeforeClass
    public static void init() {
        sContext = ApplicationProvider.getApplicationContext();
    }

    @Before
    public void setUp() throws Exception {
        assume()
                .withMessage("The RKP server hostname is not configured -- assume RKP disabled.")
                .that(SystemProperties.get("remote_provisioning.hostname"))
                .isNotEmpty();

        assume()
                .withMessage("RKP Integration tests rely on network availability.")
                .that(ServerInterface.isNetworkConnected(sContext))
                .isTrue();

        Settings.clearPreferences(sContext);

        mKeyDao = RkpdDatabase.getDatabase(sContext).provisionedKeyDao();
        mKeyStore = KeyStore.getInstance("AndroidKeyStore");
        mKeyStore.load(null);
        mKeyDao.deleteAllKeys();

        SystemInterface systemInterface =
                SystemInterfaceSelector.getSystemInterfaceForServiceName(mServiceName);
        ServiceManagerInterface.setInstances(new SystemInterface[] {systemInterface});
    }

    @After
    public void tearDown() throws Exception {
        Settings.clearPreferences(sContext);

        if (mKeyDao != null) {
            mKeyStore.deleteEntry(getTestKeyAlias());
            mKeyDao.deleteAllKeys();
        }

        ServiceManagerInterface.setInstances(null);
    }

    @Test
    public void testKeyCreationUsesRemotelyProvisionedCertificate() throws Exception {
        // Provision keys, then ensure keystore gets a fresh one assigned for the caller.
        provisionFreshKeys();

        // make sure we provisioned keys, but none are yet assigned to this app
        assertThat(mKeyDao.getTotalKeysForIrpc(mServiceName)).isGreaterThan(0);
        assertThat(mKeyDao.getKeyForClientAndIrpc(mServiceName, Process.KEYSTORE_UID,
                Process.myUid())).isNull();

        createKeystoreKeyAndVerifyAttestationKeyAssigned();
    }

    @Test
    public void testKeyCreationWithEmptyKeyPool() throws Exception {
        assertThat(mKeyDao.getTotalKeysForIrpc(mServiceName)).isEqualTo(0);

        createKeystoreKeyAndVerifyAttestationKeyAssigned();
    }

    @Test
    public void testKeyCreationUsesAlreadyAssignedKey() throws Exception {
        // Ensure that keystore uses a key that was previously assigned, assuming it
        // has not yet expired.
        provisionFreshKeys();

        mKeyDao.getOrAssignKey(mServiceName, Instant.now(), Process.KEYSTORE_UID, Process.myUid());

        ProvisionedKey attestationKey = mKeyDao.getKeyForClientAndIrpc(mServiceName,
                Process.KEYSTORE_UID, Process.myUid());

        createKeystoreKeyBackedByRkp();
        verifyCertificateChain(attestationKey);
    }

    @Test
    public void testKeyCreationWorksWhenAllKeysAssigned() throws Exception {
        provisionFreshKeys();

        // Use up all the available keys. Use a while loop so that, in the edge case that something
        // causes provisioning while we're running this test, we still do our best to consume all
        // keys.
        int bogusUidCounter = Process.LAST_APPLICATION_UID;
        while (mKeyDao.getTotalUnassignedKeysForIrpc(mServiceName) > 0) {
            ++bogusUidCounter;
            mKeyDao.getOrAssignKey(mServiceName, Instant.now(), Process.CREDSTORE_UID,
                    bogusUidCounter);
        }

        assertThat(mKeyDao.getKeyForClientAndIrpc(mServiceName, Process.KEYSTORE_UID,
                Process.myUid())).isNull();

        createKeystoreKeyAndVerifyAttestationKeyAssigned();

        // Provisioning should always result in some spare keys left over for future calls.
        assertThat(mKeyDao.getTotalUnassignedKeysForIrpc(mServiceName)).isGreaterThan(0);
    }

    @Test
    public void testKeyCreationWithExpiringAttestationKey() throws Exception {
        // Mark all keys in the pool as expiring soon, create a keystore key, then ensure
        // provisioning ran and a newly provisioned key was used to attest to the keystore key.
        provisionFreshKeys();

        mKeyDao.getOrAssignKey(mServiceName, Instant.now(), Process.KEYSTORE_UID, Process.myUid());

        ProvisionedKey oldAttestationKey = mKeyDao.getKeyForClientAndIrpc(mServiceName,
                Process.KEYSTORE_UID, Process.myUid());
        oldAttestationKey.expirationTime = Instant.now().minusSeconds(60);
        mKeyDao.updateKey(oldAttestationKey);

        createKeystoreKeyAndVerifyAttestationKeyAssigned();

        ProvisionedKey newAttestationKey = mKeyDao.getKeyForClientAndIrpc(mServiceName,
                Process.KEYSTORE_UID, Process.myUid());
        assertThat(newAttestationKey.publicKey).isNotEqualTo(oldAttestationKey.publicKey);
    }

    @Test
    public void testKeyCreationFailsWhenRkpFails() throws Exception {
        // Verify that if the system is set to rkp only, key creation fails when RKP is unable
        // to get keys.

        try (FakeRkpServer server = new FakeRkpServer(FakeRkpServer.Response.INTERNAL_ERROR,
                FakeRkpServer.Response.INTERNAL_ERROR)) {
            Settings.setDeviceConfig(sContext, Settings.EXTRA_SIGNED_KEYS_AVAILABLE_DEFAULT,
                    Duration.ofDays(1), server.getUrl());
            Settings.setMaxRequestTime(sContext, 100);
            createKeystoreKeyBackedByRkp();
            assertWithMessage("Should have gotten a KeyStoreException").fail();
        } catch (ProviderException e) {
            assertThat(e.getCause()).isInstanceOf(KeyStoreException.class);
            assertThat(((KeyStoreException) e.getCause()).getErrorCode())
                    .isEqualTo(ResponseCode.OUT_OF_KEYS_TRANSIENT_ERROR);
        }
    }

    @Test
    public void testKeyCreationWithFallback() throws Exception {
        // Verify that, if RKP doesn't work, we fall back to a factory key.
        assume()
                .withMessage("Fallback is not expected to work on RKP-only devices.")
                .that(SystemProperties.getBoolean(getRkpOnlyProp(), false))
                .isFalse();

        try (FakeRkpServer server = new FakeRkpServer(FakeRkpServer.Response.INTERNAL_ERROR,
                FakeRkpServer.Response.INTERNAL_ERROR)) {
            Settings.setDeviceConfig(sContext, Settings.EXTRA_SIGNED_KEYS_AVAILABLE_DEFAULT,
                    Duration.ofDays(1), server.getUrl());

            createKeystoreKey();

            // Ensure the key has a cert, but it didn't come from rkpd.
            assertThat(mKeyStore.getCertificateChain(getTestKeyAlias())).isNotEmpty();
            assertThat(mKeyDao.getTotalKeysForIrpc(mServiceName)).isEqualTo(0);
        }
    }

    @Test
    public void testDataBudgetEmptyGenerateKey() throws Exception {
        // Check the data budget in order to initialize a rolling window.
        assertThat(Settings.hasErrDataBudget(sContext, null /* curTime */)).isTrue();
        Settings.consumeErrDataBudget(sContext, Settings.FAILURE_DATA_USAGE_MAX);
        try {
            createKeystoreKeyBackedByRkp();
            Assert.fail("Expected a keystore exception");
        } catch (ProviderException e) {
            assertThat(e).hasCauseThat().isInstanceOf(KeyStoreException.class);
            KeyStoreException keyStoreException = (KeyStoreException) e.getCause();
            assertThat(keyStoreException.getErrorCode())
                    .isEqualTo(ResponseCode.OUT_OF_KEYS_TRANSIENT_ERROR);
        }
    }

    @Test
    public void testRetryableRkpError() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(FakeRkpServer.Response.INTERNAL_ERROR,
                FakeRkpServer.Response.INTERNAL_ERROR)) {
            Settings.setDeviceConfig(sContext, 1, Duration.ofDays(1), server.getUrl());
            Settings.setMaxRequestTime(sContext, 100);
            createKeystoreKeyBackedByRkp();
            Assert.fail("Expected a keystore exception");
        } catch (ProviderException e) {
            assertThat(e).hasCauseThat().isInstanceOf(KeyStoreException.class);
            KeyStoreException keyStoreException = (KeyStoreException) e.getCause();
            assertThat(keyStoreException.getErrorCode())
                    .isEqualTo(ResponseCode.OUT_OF_KEYS_TRANSIENT_ERROR);
            assertThat(keyStoreException.isTransientFailure()).isTrue();
            assertThat(keyStoreException.getRetryPolicy())
                    .isEqualTo(KeyStoreException.RETRY_WITH_EXPONENTIAL_BACKOFF);
        }
    }

    @Test
    public void testPeriodicProvisionerProvisioningDisabled() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(FakeRkpServer.Response.FETCH_EEK_RKP_DISABLED,
                     FakeRkpServer.Response.INTERNAL_ERROR)) {
            Settings.setDeviceConfig(sContext, 1, Duration.ofDays(1), server.getUrl());
            createKeystoreKeyBackedByRkp();
            Assert.fail("Expected a keystore exception");
        } catch (ProviderException e) {
            assertThat(e).hasCauseThat().isInstanceOf(KeyStoreException.class);
            KeyStoreException keyStoreException = (KeyStoreException) e.getCause();
            assertThat(keyStoreException.getErrorCode())
                    .isEqualTo(ResponseCode.OUT_OF_KEYS_TRANSIENT_ERROR);
            assertThat(keyStoreException.getRetryPolicy())
                    .isEqualTo(KeyStoreException.RETRY_WITH_EXPONENTIAL_BACKOFF);
            assertThat(keyStoreException.isTransientFailure()).isTrue();
        }
    }

    @Test
    public void testRetryNeverWhenDeviceNotRegistered() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(FakeRkpServer.Response.FETCH_EEK_OK,
                     FakeRkpServer.Response.SIGN_CERTS_DEVICE_UNREGISTERED)) {
            Settings.setDeviceConfig(sContext, 1, Duration.ofDays(1), server.getUrl());
            createKeystoreKeyBackedByRkp();
            Assert.fail("Expected a keystore exception");
        } catch (ProviderException e) {
            assertThat(e).hasCauseThat().isInstanceOf(KeyStoreException.class);
            KeyStoreException keyStoreException = (KeyStoreException) e.getCause();
            assertThat(keyStoreException.getErrorCode())
                    .isEqualTo(ResponseCode.OUT_OF_KEYS_PERMANENT_ERROR);
            assertThat(keyStoreException.getRetryPolicy()).isEqualTo(KeyStoreException.RETRY_NEVER);
            assertThat(keyStoreException.isTransientFailure()).isFalse();
        }
    }

    @Test
    public void testCancelDueToServiceTimeout() throws Exception {
        FakeRkpServer.RequestHandler blocksForOneMinute = (session, bodySize) -> {
            session.getInputStream().readNBytes(bodySize);
            try {
                Thread.sleep(60 * 1000);
            } catch (InterruptedException e) {
                assertWithMessage("sleep failed", e).fail();
            }
            return null;
        };

        try (SystemPropertySetter ignored = SystemPropertySetter.setRkpOnly(mInstanceName);
             FakeRkpServer server = new FakeRkpServer(blocksForOneMinute)) {
            Settings.setDeviceConfig(sContext, 1, Duration.ofDays(1), server.getUrl());

            // keystore will time out well before a minute has passed
            ProviderException e = assertThrows(ProviderException.class, this::createKeystoreKey);

            assertThat(e).hasCauseThat().isInstanceOf(KeyStoreException.class);
            KeyStoreException keyStoreException = (KeyStoreException) e.getCause();
            assertThat(keyStoreException.getErrorCode())
                    .isEqualTo(ResponseCode.OUT_OF_KEYS_TRANSIENT_ERROR);
            assertThat(keyStoreException.getRetryPolicy())
                    .isEqualTo(KeyStoreException.RETRY_WITH_EXPONENTIAL_BACKOFF);
            assertThat(keyStoreException.isTransientFailure()).isTrue();
        }
    }

    private void provisionFreshKeys() {
        PeriodicProvisioner provisioner = TestWorkerBuilder.from(
                sContext,
                PeriodicProvisioner.class,
                Executors.newSingleThreadExecutor()).build();
        assertThat(provisioner.doWork()).isEqualTo(ListenableWorker.Result.success());
    }

    private void createKeystoreKeyBackedByRkp() throws Exception {
        try (SystemPropertySetter ignored = SystemPropertySetter.setRkpOnly(mInstanceName)) {
            createKeystoreKey();
        }
    }

    private void createKeystoreKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM_EC,
                "AndroidKeyStore");
        generator.initialize(
                new KeyGenParameterSpec.Builder(getTestKeyAlias(), PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setAttestationChallenge((new byte[64]))
                        .setIsStrongBoxBacked(isStrongBoxTest())
                        .build());
        generator.generateKeyPair();
    }

    private void createKeystoreKeyAndVerifyAttestationKeyAssigned() throws Exception {
        createKeystoreKeyBackedByRkp();

        ProvisionedKey attestationKey = mKeyDao.getKeyForClientAndIrpc(mServiceName,
                Process.KEYSTORE_UID, Process.myUid());
        assertThat(attestationKey).isNotNull();
        assertThat(attestationKey.irpcHal).isEqualTo(mServiceName);

        verifyCertificateChain(attestationKey);
    }

    private void verifyCertificateChain(ProvisionedKey attestationKey) throws Exception {
        Certificate[] certChain = mKeyStore.getCertificateChain(getTestKeyAlias());
        X509Certificate[] x509Certificates = Arrays.stream(certChain)
                .map(x -> (X509Certificate) x)
                .toList()
                .toArray(new X509Certificate[0]);
        assertThat(X509Utils.isCertChainValid(x509Certificates)).isTrue();
        assertThat(Bytes.concat(SUBJECT_PUBKEY_ASN1_PREFIX, attestationKey.publicKey))
                .isEqualTo(certChain[1].getPublicKey().getEncoded());

        byte[] encodedCerts = new byte[0];
        for (int i = 1; i < certChain.length; ++i) {
            encodedCerts = Bytes.concat(encodedCerts, certChain[i].getEncoded());
        }
        assertThat(attestationKey.certificateChain).isEqualTo(encodedCerts);
    }

    private String getTestKeyAlias() {
        return "testKey_" + mName.getMethodName();
    }

    private String getRkpOnlyProp() {
        if (isStrongBoxTest()) {
            return "remote_provisioning.strongbox.rkp_only";
        }
        return "remote_provisioning.tee.rkp_only";
    }

    private boolean isStrongBoxTest() {
        switch (mInstanceName) {
            case "default":
                return false;
            case "strongbox":
                return true;
            default:
                throw new IllegalArgumentException("Unexpected instance: " + mInstanceName);
        }
    }
}
