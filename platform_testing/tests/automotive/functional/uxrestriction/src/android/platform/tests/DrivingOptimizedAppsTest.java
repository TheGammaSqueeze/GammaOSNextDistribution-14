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

import android.platform.helpers.AutomotiveConfigConstants;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoAppGridHelper;
import android.platform.helpers.IAutoDialContactDetailsHelper;
import android.platform.helpers.IAutoFacetBarHelper;
import android.platform.helpers.IAutoNotificationHelper;
import android.platform.helpers.IAutoVehicleHardKeysHelper;
import android.platform.helpers.IAutoVehicleHardKeysHelper.DrivingState;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DrivingOptimizedAppsTest {
    private HelperAccessor<IAutoDialContactDetailsHelper> mContactHelper;
    private HelperAccessor<IAutoAppGridHelper> mAppGridHelper;
    private HelperAccessor<IAutoVehicleHardKeysHelper> mHardKeysHelper;
    private HelperAccessor<IAutoFacetBarHelper> mFacetBarHelper;
    private HelperAccessor<IAutoNotificationHelper> mNotificationHelper;

    private static final String NOTIFICATION_TITLE = "Check recent permissions";
    private static final int SPEED_TWENTY = 20;

    public DrivingOptimizedAppsTest() throws Exception {
        mAppGridHelper = new HelperAccessor<>(IAutoAppGridHelper.class);
        mContactHelper = new HelperAccessor<>(IAutoDialContactDetailsHelper.class);
        mHardKeysHelper = new HelperAccessor<>(IAutoVehicleHardKeysHelper.class);
        mFacetBarHelper = new HelperAccessor<>(IAutoFacetBarHelper.class);
        mNotificationHelper = new HelperAccessor<>(IAutoNotificationHelper.class);
    }

    @Before
    public void enableDrivingMode() {
        mHardKeysHelper.get().setDrivingState(DrivingState.MOVING);
        mHardKeysHelper.get().setSpeed(SPEED_TWENTY);
    }

    @After
    public void disableDrivingMode() {
        mAppGridHelper.get().goToHomePage();
        mHardKeysHelper.get().setDrivingState(DrivingState.PARKED);
    }

    @Test
    public void testOpenSettings() {
        mAppGridHelper.get().open();
        mAppGridHelper.get().openApp("Settings");
        assertTrue(
                "Settings app is not open",
                mAppGridHelper
                        .get()
                        .checkPackageInForeground(AutomotiveConfigConstants.SETTINGS_PACKAGE));
    }

    @Test
    public void testOpenRadio() {
        mAppGridHelper.get().open();
        mAppGridHelper.get().openApp("Radio");
        assertTrue(
                "Radio app is not open",
                mAppGridHelper
                        .get()
                        .checkPackageInForeground(AutomotiveConfigConstants.RADIO_PACKAGE));
    }

    @Test
    public void testOpenPhone() {
        mAppGridHelper.get().open();
        mAppGridHelper.get().openApp("Phone");
        assertTrue(
                "Phone is not open",
                mAppGridHelper
                        .get()
                        .checkPackageInForeground(AutomotiveConfigConstants.DIAL_PACKAGE));
    }

    /**
     * The test below verifies. 1. Contacts can be openned while on Drive mode 2. After the park
     * mode, notifications are sent when a certain permission is granted while driving (Silent
     * post-drive notification)
     */
    @Test
    public void testOpenContactsAndVerifyPostDriveNotification() {
        mAppGridHelper.get().open();
        mAppGridHelper.get().openApp("Contacts");
        mContactHelper.get().dismissInitialDialogs();
        assertTrue(
                "Contacts is not open",
                mAppGridHelper
                        .get()
                        .checkPackageInForeground(AutomotiveConfigConstants.CONTACTS_PACKAGE));
        disableDrivingMode();
        mFacetBarHelper.get().clickOnFacetIcon(IAutoFacetBarHelper.FACET_BAR.NOTIFICATION);
        assertTrue(
                "Recent Permission is not Notified",
                mNotificationHelper.get().checkNotificationExists(NOTIFICATION_TITLE));
    }
}
