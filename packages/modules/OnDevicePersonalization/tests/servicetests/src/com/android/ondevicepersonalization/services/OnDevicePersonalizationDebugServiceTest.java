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

package com.android.ondevicepersonalization.services;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ServiceTestRule;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.ondevicepersonalization.services.util.DebugUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoSession;

import java.util.concurrent.TimeoutException;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationDebugServiceTest {
    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationDebugServiceDelegate mService;

    @Before
    public void setup() throws Exception {
        mService = new OnDevicePersonalizationDebugServiceDelegate(mContext);
    }

    @Test
    public void testIsEnabledTrueWhenDeveloperModeOn() throws Exception {
        MockitoSession session = ExtendedMockito.mockitoSession()
                .mockStatic(DebugUtils.class)
                .startMocking();
        try {
            when(DebugUtils.isDeveloperModeEnabled(mContext)).thenReturn(true);
            assertTrue(mService.isEnabled());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testIsEnabledFalseWhenDeveloperModeOff() throws Exception {
        MockitoSession session = ExtendedMockito.mockitoSession()
                .mockStatic(DebugUtils.class)
                .startMocking();
        try {
            when(DebugUtils.isDeveloperModeEnabled(mContext)).thenReturn(false);
            assertFalse(mService.isEnabled());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testWithBoundService() throws TimeoutException {
        Intent serviceIntent = new Intent(mContext,
                OnDevicePersonalizationDebugServiceImpl.class);
        IBinder binder = serviceRule.bindService(serviceIntent);
        assertTrue(binder instanceof OnDevicePersonalizationDebugServiceDelegate);
    }

}
