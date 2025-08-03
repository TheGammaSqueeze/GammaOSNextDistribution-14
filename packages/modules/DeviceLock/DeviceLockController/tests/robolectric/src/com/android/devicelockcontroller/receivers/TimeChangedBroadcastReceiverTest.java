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

import static org.mockito.Mockito.verify;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class TimeChangedBroadcastReceiverTest {
    private TestDeviceLockControllerApplication mTestApp;
    private TimeChangedBroadcastReceiver mReceiver;

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
        mReceiver = new TimeChangedBroadcastReceiver();
    }

    @Test
    public void onReceive_shouldNotifySchedulerTimeChanged() {
        // WHEN time changed broadcast received
        mReceiver.onReceive(mTestApp, new Intent(Intent.ACTION_TIME_CHANGED));

        // THEN should should notify scheduler time changed.
        verify(mTestApp.getDeviceLockControllerScheduler()).notifyTimeChanged();
    }
}
