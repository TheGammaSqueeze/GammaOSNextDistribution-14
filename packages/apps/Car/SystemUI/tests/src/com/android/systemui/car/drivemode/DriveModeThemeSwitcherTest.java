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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.os.UserHandle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.SysuiTestableContext;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DriveModeThemeSwitcherTest extends SysuiTestCase {

    private DriveModeThemeSwitcher mDriveModeThemeSwitcher;
    @Mock
    private DriveModeManager mDriveModeManagerMock;
    @Mock
    private OverlayManager mOverlayManagerMock;

    private OverlayInfo mEcoRRO;
    private OverlayInfo mSportRRO;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        initOverlayMock();

        SysuiTestableContext context = getContext();
        context.addMockSystemService(OverlayManager.class, mOverlayManagerMock);

        mDriveModeThemeSwitcher = new DriveModeThemeSwitcher(context, mDriveModeManagerMock);
    }

    private void initOverlayMock() {
        mEcoRRO = createOverlayInfo("eco", true);
        mSportRRO = createOverlayInfo("sport", false);
        List<OverlayInfo> list = new ArrayList<>();
        list.add(mEcoRRO);
        list.add(mSportRRO);

        when(mOverlayManagerMock.getOverlayInfosForTarget("android",
                UserHandle.CURRENT)).thenReturn(list);

        when(mOverlayManagerMock.getOverlayInfo(mEcoRRO.packageName,
                UserHandle.CURRENT)).thenReturn(mEcoRRO);
        when(mOverlayManagerMock.getOverlayInfo(mSportRRO.packageName,
                UserHandle.CURRENT)).thenReturn(mSportRRO);
    }

    private OverlayInfo createOverlayInfo(String name, boolean enabled) {
        //adding prefix + suffix to create a packageName that can be parsed from the theme switcher
        String packageName = "drivemode.modes." + name + ".rro";
        return new OverlayInfo(
                /* packageName = */ packageName,
                /* targetPackageName = */ "android",
                /* targetOverlayableName = */  "",
                /* category = */ "",
                /* baseCodePath = */ "",
                /* state = */ enabled ? OverlayInfo.STATE_ENABLED : OverlayInfo.STATE_DISABLED,
                /* userId = */ 0,
                /* priority = */  0,
                /* isMutable = */ true);
    }

    @Test
    public void rroAlreadyActive_noRROisActivated() {
        //The Eco RRO is already active in the mock, so setting it again doesn't change the state
        mDriveModeThemeSwitcher.onDriveModeChanged("Eco");

        verify(mOverlayManagerMock, times(0)).setEnabled(any(), eq(true), any());
    }

    @Test
    public void driveModeChangesToComfort_noRROisActivated() {
        //Comfort is the SystemUI default state, so every RRO has to be disabled
        mDriveModeThemeSwitcher.onDriveModeChanged("Comfort");

        //No RRO enabled
        verify(mOverlayManagerMock, times(0)).setEnabled(any(), eq(true), any());
        //Eco RRO disabled
        verify(mOverlayManagerMock, times(1)).setEnabled(
                eq(mEcoRRO.packageName), eq(false), any());
    }

    @Test
    public void driveModeChangesToSport_correctRROisActivated() {
        mDriveModeThemeSwitcher.onDriveModeChanged("Sport");

        verify(mOverlayManagerMock, times(1)).setEnabled(
                eq(mEcoRRO.packageName), eq(false), any());
        verify(mOverlayManagerMock, times(1)).setEnabled(
                eq(mSportRRO.packageName), eq(true), any());
    }
}
