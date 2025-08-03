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

package android.platform.tests;

import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoAppGridHelper;
import android.platform.helpers.IAutoFacetBarHelper;
import android.platform.helpers.IAutoVehicleHardKeysHelper;
import android.platform.helpers.IAutoVehicleHardKeysHelper.DrivingState;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UxRestrictionFacetBarTest {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

    private HelperAccessor<IAutoAppGridHelper> mAppGridHelper;
    private HelperAccessor<IAutoFacetBarHelper> mFacetBarHelper;
    private HelperAccessor<IAutoVehicleHardKeysHelper> mHardKeysHelper;

    private static final int SPEED_TWENTY = 20;
    private static final int SPEED_ZERO = 0;

    public UxRestrictionFacetBarTest() throws Exception {
        mAppGridHelper = new HelperAccessor<>(IAutoAppGridHelper.class);
        mFacetBarHelper = new HelperAccessor<>(IAutoFacetBarHelper.class);
        mHardKeysHelper = new HelperAccessor<>(IAutoVehicleHardKeysHelper.class);
    }


    @Before
    public void enableDrivingMode() {
        mFacetBarHelper.get().goToHomeScreen();
        mHardKeysHelper.get().setDrivingState(DrivingState.MOVING);
        mHardKeysHelper.get().setSpeed(SPEED_TWENTY);
    }

    @After
    public void disableDrivingMode() {
        mFacetBarHelper.get().goToHomeScreen();
        mHardKeysHelper.get().setSpeed(SPEED_ZERO);
        mHardKeysHelper.get().setDrivingState(DrivingState.PARKED);
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testRestrictedHomeFacetBar() {
        mAppGridHelper.get().open();
        mFacetBarHelper.get().clickOnFacetIcon(IAutoFacetBarHelper.FACET_BAR.HOME);
        assertTrue(
                "Home screen did not open",
                mFacetBarHelper.get().isAppInForeground(IAutoFacetBarHelper.VERIFY_OPEN_APP.HOME));
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testRestrictedPhoneFacetBar() {
        mFacetBarHelper.get().clickOnFacetIcon(IAutoFacetBarHelper.FACET_BAR.PHONE);
        assertTrue(
                "Phone app did not open",
                mFacetBarHelper.get().isAppInForeground(IAutoFacetBarHelper.VERIFY_OPEN_APP.PHONE));
    }

    @Test
    public void testRestrictedHvacFacetBar() {
        mFacetBarHelper.get().clickOnFacetIcon(IAutoFacetBarHelper.FACET_BAR.HVAC);
        assertTrue(
                "Hvac did not open",
                mFacetBarHelper.get().isAppInForeground(IAutoFacetBarHelper.VERIFY_OPEN_APP.HVAC));
    }

    @Test
    public void testRestrictedAppGridFacetBar() {
        mFacetBarHelper.get().clickOnFacetIcon(IAutoFacetBarHelper.FACET_BAR.APP_GRID);
        assertTrue(
                "App grid did not open",
                mFacetBarHelper
                        .get()
                        .isAppInForeground(IAutoFacetBarHelper.VERIFY_OPEN_APP.APP_GRID));
    }

    @Test
    public void testRestrictedNotificationFacetBar() {
        mFacetBarHelper.get().clickOnFacetIcon(IAutoFacetBarHelper.FACET_BAR.NOTIFICATION);
        assertTrue(
                "Notification did not open.",
                mFacetBarHelper
                        .get()
                        .isAppInForeground(IAutoFacetBarHelper.VERIFY_OPEN_APP.NOTIFICATION));
    }
}
