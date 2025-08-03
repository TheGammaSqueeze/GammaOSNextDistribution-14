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

import static org.mockito.Mockito.mock;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.rkpdapp.BootReceiver;
import com.android.rkpdapp.interfaces.ServiceManagerInterface;
import com.android.rkpdapp.interfaces.SystemInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class BootReceiverTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        Configuration config = new Configuration.Builder()
                .setExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext, config);
    }

    @Test
    public void verifyBootWorkers() throws Exception {
        SystemInterface mockHal = mock(SystemInterface.class);
        ServiceManagerInterface.setInstances(new SystemInterface[]{mockHal});

        new BootReceiver().onReceive(mContext, null);

        List<WorkInfo> provisioningJobs = WorkManager.getInstance(
                mContext).getWorkInfosForUniqueWork("ProvisioningJob").get();
        assertThat(provisioningJobs.size()).isEqualTo(1);

        List<WorkInfo> widevineJobs = WorkManager.getInstance(
                mContext).getWorkInfosByTag("WidevineProvisioner").get();
        assertThat(widevineJobs.size()).isEqualTo(1);
    }
}
