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

package com.android.devicelockcontroller.receivers;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_MANDATORY_PROVISION;

import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.SetupParametersClient;

import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;

@RunWith(RobolectricTestRunner.class)
public class ResetDeviceReceiverTest {
    private SetupParametersClient mSetupParameters;
    private Intent mIntent;
    private TestDeviceLockControllerApplication mTestApp;
    private ResetDeviceReceiver mReceiver;
    private StatsLogger mStatsLogger;

    @Before
    public void setUp() throws Exception {
        mTestApp = ApplicationProvider.getApplicationContext();
        mSetupParameters = SetupParametersClient.getInstance();
        mIntent = new Intent(mTestApp, ResetDeviceReceiver.class);
        mReceiver = new ResetDeviceReceiver(TestingExecutors.sameThreadScheduledExecutor());
        StatsLoggerProvider loggerProvider =
                (StatsLoggerProvider) mTestApp.getApplicationContext();
        mStatsLogger = loggerProvider.getStatsLogger();
    }

    @Test
    public void onReceive_shouldLogToStatsLoggerWhenCalled_whenProvisionIsMandatory()
            throws ExecutionException, InterruptedException {
        final Bundle bundle = new Bundle();
        bundle.putBoolean(EXTRA_MANDATORY_PROVISION, true);
        mSetupParameters.createPrefs(bundle).get();

        mReceiver.onReceive(mTestApp, mIntent);

        verify(mStatsLogger).logDeviceReset(/* isProvisionMandatory= */true);
    }

    @Test
    public void onReceive_shouldLogToStatsLoggerWhenCalled_whenProvisionIsNotMandatory()
            throws ExecutionException, InterruptedException {
        final Bundle bundle = new Bundle();
        bundle.putBoolean(EXTRA_MANDATORY_PROVISION, false);
        mSetupParameters.createPrefs(bundle).get();

        mReceiver.onReceive(mTestApp, mIntent);

        verify(mStatsLogger).logDeviceReset(/* isProvisionMandatory= */false);
    }
}
