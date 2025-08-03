/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.ui.util;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;

import android.content.Context;
import android.os.RemoteException;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.AdservicesTestHelper;

import org.junit.After;
import org.junit.Before;

/**
 * Base class for all settings UI unit tests.
 *
 * <p>Contains basic device setup and teardown methods.
 */
public abstract class AdServicesUiTestCase extends AdServicesExtendedMockitoTestCase {

    public static final int LAUNCH_TIMEOUT = 5_000;

    protected final UiDevice mDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    protected Context mSpyContext;

    @Before
    public void setUpDevice() throws RemoteException {
        mSpyContext = spy(appContext.get());

        // Start from the home screen
        mDevice.pressHome();
        mDevice.setOrientationNatural();

        // Wait for launcher
        String launcherPackage = mDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        mDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void teardown() throws UiObjectNotFoundException {
        ApkTestUtil.takeScreenshot(mDevice, getClass().getSimpleName() + "_" + getTestName() + "_");

        AdservicesTestHelper.killAdservicesProcess(appContext.get());
    }
}
