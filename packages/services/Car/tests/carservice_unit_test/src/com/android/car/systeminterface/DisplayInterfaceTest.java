/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.systeminterface;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.builtin.os.UserManagerHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.os.UserManager;
import android.view.Display;

import com.android.car.power.CarPowerManagementService;
import com.android.car.user.CarUserService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class DisplayInterfaceTest extends AbstractExtendedMockitoTestCase {

    @Mock
    private Context mContext;

    @Mock
    private WakeLockInterface mWakeLockInterface;

    @Mock
    private CarPowerManagementService mCarPowerManagementService;

    @Mock
    private DisplayManager mDisplayManager;

    @Mock
    private ContentResolver mContentResolver;

    @Mock
    private CarUserService mCarUserService;

    @Mock
    private PowerManager mPowerManager;

    @Mock
    private UserManager mUserManager;

    @Mock
    private Display mDisplay;

    private DisplayInterface.DefaultImpl mDisplayInterface;

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(UserManagerHelper.class);
    }

    @Before
    public void setUp() {
        when(mContext.createContextAsUser(any(), anyInt())).thenReturn(mContext);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContext.getSystemService(DisplayManager.class)).thenReturn(mDisplayManager);
        when(mContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY)).thenReturn(mDisplay);
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDisplay});
    }

    @Test
    public void testStartDisplayStateMonitoring_perDisplayBrightnessSupported() {
        when(mDisplayManager.getDisplay(anyInt())).thenReturn(mDisplay);
        when(mDisplay.getState()).thenReturn(Display.STATE_ON);
        createDisplayInterface(/* perDisplayBrightnessSupported= */ true);

        mDisplayInterface.startDisplayStateMonitoring();

        verify(mContentResolver, never()).registerContentObserver(any(), eq(false), any());
        verify(mDisplayManager).registerDisplayListener(any(), isNull(), anyLong());
        verify(mCarUserService).addUserLifecycleListener(any(), any());
    }

    @Test
    public void testStartDisplayStateMonitoring_perDisplayBrightnessNotSupported() {
        when(mDisplayManager.getDisplay(anyInt())).thenReturn(mDisplay);
        when(mDisplay.getState()).thenReturn(Display.STATE_ON);
        createDisplayInterface(/* perDisplayBrightnessSupported= */ false);

        mDisplayInterface.startDisplayStateMonitoring();

        verify(mContentResolver).registerContentObserver(any(), eq(false), any());
        verify(mDisplayManager, never()).registerDisplayListener(any(), isNull(), anyLong());
        verify(mCarUserService).addUserLifecycleListener(any(), any());
    }

    @Test
    public void testStopDisplayStateMonitoring_perDisplayBrightnessSupported() {
        createDisplayInterface(/* perDisplayBrightnessSupported= */ true);
        mDisplayInterface.startDisplayStateMonitoring();
        mDisplayInterface.stopDisplayStateMonitoring();

        verify(mDisplayManager).unregisterDisplayListener(any());
        verify(mContentResolver, never()).unregisterContentObserver(any());
        verify(mCarUserService).removeUserLifecycleListener(any());
    }

    @Test
    public void testStopDisplayStateMonitoring_perDisplayBrightnessNoSupported() {
        createDisplayInterface(/* perDisplayBrightnessSupported= */ false);
        mDisplayInterface.startDisplayStateMonitoring();
        mDisplayInterface.stopDisplayStateMonitoring();

        verify(mDisplayManager, never()).unregisterDisplayListener(any());
        verify(mContentResolver).unregisterContentObserver(any());
        verify(mCarUserService).removeUserLifecycleListener(any());
    }

    private void createDisplayInterface(boolean perDisplayBrightnessSupported) {
        doReturn(perDisplayBrightnessSupported)
                .when(() -> UserManagerHelper.isVisibleBackgroundUsersSupported(any()));

        mDisplayInterface = new DisplayInterface.DefaultImpl(mContext, mWakeLockInterface) {
            @Override
            public void refreshDisplayBrightness() {
            }

            @Override
            public void refreshDisplayBrightness(int displayId) {
            }
        };
        mDisplayInterface.init(mCarPowerManagementService, mCarUserService);
    }
}
