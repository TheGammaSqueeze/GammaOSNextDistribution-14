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

package com.android.rkpdapp.unittest;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.rkpdapp.BootReceiver;
import com.android.rkpdapp.database.ProvisionedKey;
import com.android.rkpdapp.database.ProvisionedKeyDao;
import com.android.rkpdapp.database.RkpKey;
import com.android.rkpdapp.database.RkpdDatabase;
import com.android.rkpdapp.interfaces.ServiceManagerInterface;
import com.android.rkpdapp.interfaces.SystemInterface;
import com.android.rkpdapp.provisioner.PeriodicProvisioner;
import com.android.rkpdapp.service.RegistrationBinder;
import com.android.rkpdapp.testutil.FakeRkpServer;
import com.android.rkpdapp.testutil.SystemPropertySetter;
import com.android.rkpdapp.utils.Settings;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;

@RunWith(AndroidJUnit4.class)
public class PeriodicProvisionerTests {
    private static final RkpKey FAKE_RKP_KEY = new RkpKey(new byte[1], new byte[2], new Array(),
            "fake-hal", new byte[3]);

    private PeriodicProvisioner mProvisioner;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();

        Assume.assumeFalse(Settings.getDefaultUrl().isEmpty());

        RkpdDatabase.getDatabase(mContext).provisionedKeyDao().deleteAllKeys();
        mProvisioner = TestWorkerBuilder.from(
                mContext,
                PeriodicProvisioner.class,
                Executors.newSingleThreadExecutor()).build();

        Configuration config = new Configuration.Builder()
                .setExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext, config);
        Settings.clearPreferences(mContext);
    }

    @After
    public void tearDown() {
        RkpdDatabase.getDatabase(mContext).provisionedKeyDao().deleteAllKeys();
        ServiceManagerInterface.setInstances(null);
        Settings.clearPreferences(mContext);
    }

    private WorkInfo getProvisionerWorkInfo() throws ExecutionException, InterruptedException {
        WorkManager workManager = WorkManager.getInstance(mContext);
        List<WorkInfo> infos = workManager.getWorkInfosForUniqueWork(
                PeriodicProvisioner.UNIQUE_WORK_NAME).get();
        assertThat(infos.size()).isEqualTo(1);
        return infos.get(0);
    }

    @Test
    public void provisionWithNoHals() throws Exception {
        // setup work with boot receiver
        new BootReceiver().onReceive(mContext, null);

        WorkInfo worker = getProvisionerWorkInfo();
        assertThat(worker.getState()).isEqualTo(WorkInfo.State.ENQUEUED);
        assertThat(worker.getRunAttemptCount()).isEqualTo(0);

        ServiceManagerInterface.setInstances(new SystemInterface[0]);
        WorkManagerTestInitHelper.getTestDriver(mContext).setAllConstraintsMet(worker.getId());

        // the worker should uninstall itself once it realizes it's not needed on this system
        worker = getProvisionerWorkInfo();
        assertThat(worker.getState()).isEqualTo(WorkInfo.State.CANCELLED);
        assertThat(worker.getRunAttemptCount()).isEqualTo(1);

        // verify the worker doesn't run again
        WorkManagerTestInitHelper.getTestDriver(mContext).setAllConstraintsMet(worker.getId());
        worker = getProvisionerWorkInfo();
        assertThat(worker.getState()).isEqualTo(WorkInfo.State.CANCELLED);
        assertThat(worker.getRunAttemptCount()).isEqualTo(1);
    }

    @Test
    public void provisionWithNoHostName() throws Exception {
        // setup work with boot receiver
        new BootReceiver().onReceive(mContext, null);

        try (SystemPropertySetter ignored = SystemPropertySetter.setHostname("")) {
            SystemInterface mockHal = mock(SystemInterface.class);
            ServiceManagerInterface.setInstances(new SystemInterface[]{mockHal});

            WorkInfo worker = getProvisionerWorkInfo();
            WorkManagerTestInitHelper.getTestDriver(mContext).setAllConstraintsMet(worker.getId());
        }

        WorkInfo worker = getProvisionerWorkInfo();
        assertThat(worker.getState()).isEqualTo(WorkInfo.State.CANCELLED);
        assertThat(worker.getRunAttemptCount()).isEqualTo(1);
    }

    @Test
    public void provisionSuccess() throws Exception {
        try (FakeRkpServer fakeRkpServer = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            saveUrlInSettings(fakeRkpServer);
            SystemInterface mockHal = mock(SystemInterface.class);
            doReturn("test-irpc").when(mockHal).getServiceName();
            doReturn(new byte[1]).when(mockHal).generateCsr(any(), any(), any());
            doReturn(5).when(mockHal).getBatchSize();

            ServiceManagerInterface.setInstances(new SystemInterface[]{mockHal});
            assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.success());
        }
    }

    @Test
    public void fetchEekFails() throws Exception {
        try (FakeRkpServer fakeRkpServer = new FakeRkpServer(
                FakeRkpServer.Response.INTERNAL_ERROR,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            saveUrlInSettings(fakeRkpServer);
            Settings.setMaxRequestTime(mContext, 100);
            SystemInterface mockHal = mock(SystemInterface.class);
            ServiceManagerInterface.setInstances(new SystemInterface[]{mockHal});
            assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.failure());

            // we should have failed before trying to generate any keys
            verify(mockHal, never()).generateKey(any());
        }
    }

    @Test
    public void fetchEekDisablesRkp() throws Exception {
        ProvisionedKeyDao dao = RkpdDatabase.getDatabase(mContext).provisionedKeyDao();
        ProvisionedKey fakeKey = new ProvisionedKey(new byte[42], "fake-irpc", new byte[3],
                new byte[2], Instant.now().plusSeconds(120));
        dao.insertKeys(List.of(fakeKey));
        assertThat(dao.getTotalKeysForIrpc("fake-irpc")).isEqualTo(1);

        try (FakeRkpServer fakeRkpServer = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_RKP_DISABLED,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            saveUrlInSettings(fakeRkpServer);
            SystemInterface mockHal = mock(SystemInterface.class);
            ServiceManagerInterface.setInstances(new SystemInterface[]{mockHal});
            assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.success());

            // since RKP is disabled, there should be no keys generated
            verify(mockHal, never()).generateKey(any());
        }

        // when RKP is detected as disabled, the provisioner is supposed to delete all keys
        assertThat(dao.getTotalKeysForIrpc("fake-irpc")).isEqualTo(0);
    }

    @Test
    public void provisioningExpiresOldKeys() throws Exception {
        ProvisionedKeyDao dao = RkpdDatabase.getDatabase(mContext).provisionedKeyDao();
        ProvisionedKey oldKey = new ProvisionedKey(new byte[1], "fake-irpc", new byte[2],
                new byte[3],
                Instant.now().minus(RegistrationBinder.MIN_KEY_LIFETIME.multipliedBy(2)));
        // Add 2 hours so that this key does not get deleted in case getKeyWorker comes alive.
        ProvisionedKey freshKey = new ProvisionedKey(new byte[11], "fake-irpc", new byte[12],
                new byte[13],
                Instant.now().plus(RegistrationBinder.MIN_KEY_LIFETIME.multipliedBy(2)));
        dao.insertKeys(List.of(oldKey, freshKey));
        assertThat(dao.getTotalKeysForIrpc("fake-irpc")).isEqualTo(2);

        try (FakeRkpServer fakeRkpServer = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            saveUrlInSettings(fakeRkpServer);
            SystemInterface mockHal = mock(SystemInterface.class);
            doReturn("test-irpc").when(mockHal).getServiceName();
            doReturn(new byte[1]).when(mockHal).generateCsr(any(), any(), any());
            doReturn(20).when(mockHal).getBatchSize();
            ServiceManagerInterface.setInstances(new SystemInterface[]{mockHal});
            assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.success());
        }

        // old key should be gone, fresh key hangs around
        assertThat(dao.getTotalKeysForIrpc("fake-irpc")).isEqualTo(1);
    }

    @Test
    public void provisionTwoHalsBothFail() throws Exception {
        try (FakeRkpServer fakeRkpServer = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            saveUrlInSettings(fakeRkpServer);
            SystemInterface firstHal = mock(SystemInterface.class);
            doReturn("first").when(firstHal).getServiceName();
            doReturn(20).when(firstHal).getBatchSize();
            doThrow(new CborException("first hal failed")).when(firstHal).generateKey(any());

            SystemInterface secondHal = mock(SystemInterface.class);
            doReturn("second").when(secondHal).getServiceName();
            doReturn(20).when(secondHal).getBatchSize();
            doThrow(new CborException("second hal failed")).when(secondHal).generateKey(any());

            ServiceManagerInterface.setInstances(new SystemInterface[]{firstHal, secondHal});
            assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.failure());

            verify(firstHal, never()).generateCsr(any(), any(), any());
            verify(secondHal, never()).generateCsr(any(), any(), any());
        }
    }

    @Test
    public void provisionTwoHalsFirstFails() throws Exception {
        try (FakeRkpServer fakeRkpServer = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            saveUrlInSettings(fakeRkpServer);
            SystemInterface firstHal = mock(SystemInterface.class);
            doReturn("first").when(firstHal).getServiceName();
            doReturn(20).when(firstHal).getBatchSize();
            doThrow(new CborException("first hal failed")).when(firstHal).generateKey(any());

            SystemInterface secondHal = mock(SystemInterface.class);
            doReturn("second").when(secondHal).getServiceName();
            doReturn(20).when(secondHal).getBatchSize();
            doReturn(FAKE_RKP_KEY).when(secondHal).generateKey(any());
            doReturn(new byte[3]).when(secondHal).generateCsr(any(), any(), any());

            ServiceManagerInterface.setInstances(new SystemInterface[]{firstHal, secondHal});
            assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.failure());

            verify(firstHal, never()).generateCsr(any(), any(), any());
            verify(secondHal).generateCsr(any(), any(), any());
        }
    }

    @Test
    public void provisionTwoHalsSecondFails() throws Exception {
        try (FakeRkpServer fakeRkpServer = new FakeRkpServer(
                FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            saveUrlInSettings(fakeRkpServer);
            SystemInterface firstHal = mock(SystemInterface.class);
            doReturn("first").when(firstHal).getServiceName();
            doReturn(20).when(firstHal).getBatchSize();
            doReturn(FAKE_RKP_KEY).when(firstHal).generateKey(any());
            doReturn(new byte[42]).when(firstHal).generateCsr(any(), any(), any());

            SystemInterface secondHal = mock(SystemInterface.class);
            doReturn("second").when(secondHal).getServiceName();
            doReturn(20).when(secondHal).getBatchSize();
            doThrow(new CborException("second hal failed")).when(secondHal).generateKey(any());

            ServiceManagerInterface.setInstances(new SystemInterface[]{firstHal, secondHal});
            assertThat(mProvisioner.doWork()).isEqualTo(ListenableWorker.Result.failure());

            verify(firstHal).generateCsr(any(), any(), any());
            verify(secondHal, never()).generateCsr(any(), any(), any());
        }
    }

    private void saveUrlInSettings(FakeRkpServer server) {
        Settings.setDeviceConfig(mContext, 1, Duration.ofSeconds(10), server.getUrl());
    }
}
