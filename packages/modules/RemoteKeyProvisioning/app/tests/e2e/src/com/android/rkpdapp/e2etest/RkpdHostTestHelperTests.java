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

import static com.android.rkpdapp.database.RkpdDatabase.DB_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.hardware.security.keymint.IRemotelyProvisionedComponent;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.security.keystore.KeyGenParameterSpec;

import androidx.room.Room;
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
import com.android.rkpdapp.testutil.SystemInterfaceSelector;
import com.android.rkpdapp.testutil.TestDatabase;
import com.android.rkpdapp.testutil.TestProvisionedKeyDao;
import com.android.rkpdapp.utils.Settings;
import com.android.rkpdapp.utils.StatsProcessor;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

@RunWith(Parameterized.class)
public class RkpdHostTestHelperTests {
    private static final String KEY_ALIAS = "RKPD_HOST_TEST_HELPER_KEY";
    private static Context sContext;
    private final String mInstanceName;
    private final String mServiceName;
    private ProvisionedKeyDao mRealDao;
    private TestProvisionedKeyDao mTestDao;
    private PeriodicProvisioner mProvisioner;

    @Rule
    public final TestName mName = new TestName();

    @Parameterized.Parameters(name = "{index}: instanceName={0}")
    public static String[] parameters() {
        return ServiceManager.getDeclaredInstances(IRemotelyProvisionedComponent.DESCRIPTOR);
    }

    public RkpdHostTestHelperTests(String instanceName) {
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
        mRealDao = RkpdDatabase.getDatabase(sContext).provisionedKeyDao();
        mRealDao.deleteAllKeys();
        mTestDao = Room.databaseBuilder(sContext, TestDatabase.class, DB_NAME).build().dao();

        mProvisioner = TestWorkerBuilder.from(
                sContext,
                PeriodicProvisioner.class,
                Executors.newSingleThreadExecutor()).build();

        SystemInterface systemInterface =
                SystemInterfaceSelector.getSystemInterfaceForServiceName(mServiceName);
        ServiceManagerInterface.setInstances(new SystemInterface[] {systemInterface});
    }

    @After
    public void tearDown() throws Exception {
        Settings.clearPreferences(sContext);

        if (mRealDao != null) {
            mRealDao.deleteAllKeys();
        }

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry(KEY_ALIAS);

        ServiceManagerInterface.setInstances(null);
    }

    @Test
    public void provisionThenUseKeyThenProvision() throws Exception {
        assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.success());

        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM_EC,
                "AndroidKeyStore");
        generator.initialize(
                new KeyGenParameterSpec.Builder(KEY_ALIAS, PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setAttestationChallenge((new byte[64]))
                        .setIsStrongBoxBacked(mInstanceName.equals("strongbox"))
                        .build());
        generator.generateKeyPair();

        assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.success());
    }

    @Test
    public void provisionThenExpireThenProvisionAgain() throws Exception {
        assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.success());

        List<ProvisionedKey> keys = mTestDao.getAllKeys();

        // Expire a key
        keys.get(0).expirationTime = Instant.now().minusSeconds(60);
        mRealDao.updateKey(keys.get(0));

        // Mark two more keys as expiring soon
        for (int i = 1; i < 3; ++i) {
            keys.get(i).expirationTime = Instant.now().plusSeconds(60);
            mRealDao.updateKey(keys.get(i));
        }

        assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.success());
    }

    @Test
    public void testPeriodicProvisionerNoop() {
        // Similar to the PeriodicProvisioner round trip, except first we actually populate the
        // key pool to ensure that the PeriodicProvisioner just noops.
        // This test is purely to test out proper metrics.
        assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.success());
        StatsProcessor.PoolStats pool = StatsProcessor.processPool(mRealDao, mServiceName,
                Settings.getExtraSignedKeysAvailable(sContext),
                Settings.getExpirationTime(sContext));

        // The metrics host test will perform additional validation by ensuring correct metrics
        // are recorded.
        assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.success());
        StatsProcessor.PoolStats updatedPool = StatsProcessor.processPool(mRealDao, mServiceName,
                Settings.getExtraSignedKeysAvailable(sContext),
                Settings.getExpirationTime(sContext));

        assertThat(updatedPool.keysInUse + updatedPool.keysUnassigned)
                .isEqualTo(pool.keysInUse + pool.keysUnassigned);
    }
}
