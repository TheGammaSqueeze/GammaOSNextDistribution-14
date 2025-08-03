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
public final class FinalizationBootCompletedReceiverTest {

    public static final Intent INTENT = new Intent(Intent.ACTION_BOOT_COMPLETED);

    private TestDeviceLockControllerApplication mTestApplication;
    private FinalizationBootCompletedReceiver mReceiver;

    @Before
    public void setUp() {
        mTestApplication = ApplicationProvider.getApplicationContext();
        mReceiver = new FinalizationBootCompletedReceiver();
    }

    @Test
    public void onReceive_initializeFinalizationController() {
        mReceiver.onReceive(mTestApplication, INTENT);

        verify(mTestApplication.getFinalizationController()).enforceInitialState();
    }
}
