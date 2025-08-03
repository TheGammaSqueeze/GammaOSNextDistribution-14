/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.avf.rkpdapp.e2etest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.hardware.security.keymint.IRemotelyProvisionedComponent;
import android.os.Process;
import android.os.SystemProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import com.android.compatibility.common.util.CddTest;
import com.android.microdroid.test.device.MicrodroidDeviceTestBase;
import com.android.rkpdapp.database.ProvisionedKey;
import com.android.rkpdapp.database.ProvisionedKeyDao;
import com.android.rkpdapp.database.RkpdDatabase;
import com.android.rkpdapp.interfaces.ServerInterface;
import com.android.rkpdapp.interfaces.ServiceManagerInterface;
import com.android.rkpdapp.interfaces.SystemInterface;
import com.android.rkpdapp.provisioner.PeriodicProvisioner;
import com.android.rkpdapp.testutil.SystemInterfaceSelector;
import com.android.rkpdapp.utils.Settings;
import com.android.rkpdapp.utils.X509Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.concurrent.Executors;

/**
 * End-to-end test for the pVM remote attestation (key provisioning/VM attestation).
 *
 * <p>To run this test, you need to:
 *
 * - Have an arm64 device supporting protected VMs.
 * - Have a stable network connection on the device.
 * - Have the RKP server hostname configured in the device. If not, you can set it using:
 * $ adb shell setprop remote_provisioning.hostname remoteprovisioning.googleapis.com
 */
@RunWith(AndroidJUnit4.class)
public class AvfIntegrationTest extends MicrodroidDeviceTestBase {
    private static final String SERVICE_NAME = IRemotelyProvisionedComponent.DESCRIPTOR + "/avf";

    private ProvisionedKeyDao mKeyDao;
    private PeriodicProvisioner mProvisioner;

    @Before
    public void setUp() throws Exception {
        assume().withMessage("AVF key provisioning is not supported on CF.")
                .that(isCuttlefish())
                .isFalse();
        assume().withMessage("The RKP server hostname is not configured -- assume RKP disabled.")
                .that(SystemProperties.get("remote_provisioning.hostname"))
                .isNotEmpty();
        assume().withMessage("RKP Integration tests rely on network availability.")
                .that(ServerInterface.isNetworkConnected(getContext()))
                .isTrue();

        Settings.clearPreferences(getContext());
        mKeyDao = RkpdDatabase.getDatabase(getContext()).provisionedKeyDao();
        mKeyDao.deleteAllKeys();

        mProvisioner =
                TestWorkerBuilder.from(
                                getContext(),
                                PeriodicProvisioner.class,
                                Executors.newSingleThreadExecutor())
                        .build();

        SystemInterface systemInterface =
                SystemInterfaceSelector.getSystemInterfaceForServiceName(SERVICE_NAME);
        ServiceManagerInterface.setInstances(new SystemInterface[] {systemInterface});
    }

    @After
    public void tearDown() throws Exception {
        ServiceManagerInterface.setInstances(null);
        if (mKeyDao != null) {
            mKeyDao.deleteAllKeys();
        }
        Settings.clearPreferences(getContext());
    }

    @Test
    @CddTest(requirements = {"9.17/C-1-1", "9.17/C-2-1"})
    public void provisioningSucceeds() throws Exception {
        assertWithMessage("There should be no keys in the database before provisioning")
                .that(mKeyDao.getTotalKeysForIrpc(SERVICE_NAME))
                .isEqualTo(0);

        // Check provisioning succeeds.
        assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.success());
        int totalUnassignedKeys = mKeyDao.getTotalUnassignedKeysForIrpc(SERVICE_NAME);
        assertWithMessage("There should be unassigned keys in the database after provisioning")
                .that(totalUnassignedKeys)
                .isGreaterThan(0);

        ProvisionedKey attestationKey =
                mKeyDao.getKeyForClientAndIrpc(SERVICE_NAME, Process.SYSTEM_UID, Process.myUid());
        assertThat(attestationKey).isNull();
        // Assign a key to a new client.
        attestationKey =
                mKeyDao.getOrAssignKey(
                        SERVICE_NAME, Instant.now(), Process.SYSTEM_UID, Process.myUid());

        // Assert.
        assertThat(attestationKey).isNotNull();
        assertThat(attestationKey.irpcHal).isEqualTo(SERVICE_NAME);
        assertWithMessage("One key should be assigned")
                .that(mKeyDao.getTotalUnassignedKeysForIrpc(SERVICE_NAME))
                .isEqualTo(totalUnassignedKeys - 1);

        // Parsing the certificate chain successfully indicates that the chain is well-formed,
        // each certificate is signed by the next one, and the root certificate is self-signed.
        X509Certificate[] certs = X509Utils.formatX509Certs(attestationKey.certificateChain);
        assertThat(certs.length).isGreaterThan(1);
        assertThat(certs[0].getSubjectX500Principal().getName()).contains("O=AVF");
    }
}
