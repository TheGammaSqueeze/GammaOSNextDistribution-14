/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.drivemode;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class InMemoryDriveModeManagerTest extends SysuiTestCase {

    private InMemoryDriveModeManager mDriveModeManager;
    @Mock
    private DriveModeManager.Callback mCallbackMock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mDriveModeManager = new InMemoryDriveModeManager(getContext());
    }

    @Test
    public void addingCallback_receivesLastState() {
        //When adding a callback the latest state is received
        mDriveModeManager.addCallback(mCallbackMock);

        verify(mCallbackMock, times(1)).onDriveModeChanged("Comfort");

        mDriveModeManager.removeCallback(mCallbackMock);
    }

    @Test
    public void updatingState_notifiesCallbacks() {
        //When updating the state the callbacks are notified
        mDriveModeManager.addCallback(mCallbackMock);
        mDriveModeManager.setDriveMode("Eco");

        assertEquals(mDriveModeManager.getDriveMode(), "Eco");
        verify(mCallbackMock, times(1)).onDriveModeChanged("Eco");

        mDriveModeManager.removeCallback(mCallbackMock);
    }
}
