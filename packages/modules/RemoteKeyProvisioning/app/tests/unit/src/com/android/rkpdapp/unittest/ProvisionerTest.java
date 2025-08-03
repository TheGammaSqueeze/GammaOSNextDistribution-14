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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.GeekResponse;
import com.android.rkpdapp.RkpdException;
import com.android.rkpdapp.database.ProvisionedKeyDao;
import com.android.rkpdapp.database.RkpKey;
import com.android.rkpdapp.database.RkpdDatabase;
import com.android.rkpdapp.interfaces.SystemInterface;
import com.android.rkpdapp.metrics.ProvisioningAttempt;
import com.android.rkpdapp.provisioner.Provisioner;
import com.android.rkpdapp.testutil.FakeRkpServer;
import com.android.rkpdapp.utils.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

import co.nstant.in.cbor.model.Array;

@RunWith(AndroidJUnit4.class)
public class ProvisionerTest {
    private static final RkpKey FAKE_RKP_KEY = new RkpKey(new byte[1], new byte[2], new Array(),
            "hal", new byte[3]);

    private static Context sContext;
    private Provisioner mProvisioner;

    @BeforeClass
    public static void init() {
        sContext = ApplicationProvider.getApplicationContext();
    }

    @Before
    public void setUp() {
        Settings.clearPreferences(sContext);

        ProvisionedKeyDao keyDao = RkpdDatabase.getDatabase(sContext).provisionedKeyDao();
        keyDao.deleteAllKeys();

        mProvisioner = new Provisioner(sContext, keyDao, false);
    }

    @After
    public void tearDown() {
        Settings.clearPreferences(sContext);
    }

    @Test
    public void testProvisionerUsesCorrectBatchSize() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            Settings.setDeviceConfig(sContext, 20, Duration.ofDays(1), server.getUrl());
            final int batchSize = 13;
            ProvisioningAttempt atom = ProvisioningAttempt.createScheduledAttemptMetrics(sContext);
            SystemInterface mockSystem = mock(SystemInterface.class);
            doReturn(batchSize).when(mockSystem).getBatchSize();
            doReturn(FAKE_RKP_KEY).when(mockSystem).generateKey(eq(atom));
            doReturn(new byte[1]).when(mockSystem).generateCsr(eq(atom), notNull(), notNull());

            GeekResponse geekResponse = new GeekResponse();
            geekResponse.setChallenge(new byte[1]);
            mProvisioner.provisionKeys(atom, mockSystem, geekResponse);

            verify(mockSystem).generateCsr(any(), any(),
                    argThat(keysGenerated -> keysGenerated.size() == 13));
            verify(mockSystem).generateCsr(any(), any(),
                    argThat(keysGenerated -> keysGenerated.size() == 7));
        }
    }

    @Test
    public void testProvisionerHandlesExceptionOnGetBatchSize() throws Exception {
        try (FakeRkpServer server = new FakeRkpServer(FakeRkpServer.Response.FETCH_EEK_OK,
                FakeRkpServer.Response.SIGN_CERTS_OK_VALID_CBOR)) {
            Settings.setDeviceConfig(sContext, 20, Duration.ofDays(1), server.getUrl());
            ProvisioningAttempt atom = ProvisioningAttempt.createScheduledAttemptMetrics(sContext);
            SystemInterface mockSystem = mock(SystemInterface.class);
            doThrow(new RemoteException()).when(mockSystem).getBatchSize();
            doReturn(FAKE_RKP_KEY).when(mockSystem).generateKey(eq(atom));

            GeekResponse geekResponse = new GeekResponse();
            geekResponse.setChallenge(new byte[1]);

            assertThrows(RkpdException.class, () ->
                    mProvisioner.provisionKeys(atom, mockSystem, geekResponse));
        }
    }
}
