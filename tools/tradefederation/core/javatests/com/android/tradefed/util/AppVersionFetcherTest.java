/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tradefed.util;

import static org.mockito.Mockito.when;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.AppVersionFetcher.AppVersionInfo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link AppVersionFetcher} */
@RunWith(JUnit4.class)
public class AppVersionFetcherTest {

    private static final String PACKAGE_NAME = "mypackage";
    private static final String VERSION_CODE_CMD = "dumpsys package mypackage | grep versionCode=";
    private static final String VERSION_NAME_CMD = "dumpsys package mypackage | grep versionName=";

    @Mock ITestDevice mMockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFetchVersionCode_valid() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellCommand(VERSION_CODE_CMD))
                .thenReturn("   versionCode=780616927 minSdk=25 targetSdk=25");

        Assert.assertEquals(
                "780616927",
                AppVersionFetcher.fetch(mMockDevice, PACKAGE_NAME, AppVersionInfo.VERSION_CODE));
    }

    @Test
    public void testFetchVersionName_valid() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellCommand(VERSION_NAME_CMD))
                .thenReturn("    versionName=2.7.0.179109344");

        Assert.assertEquals(
                "2.7.0.179109344",
                AppVersionFetcher.fetch(mMockDevice, PACKAGE_NAME, AppVersionInfo.VERSION_NAME));
    }

    @Test
    public void testFetchVersionCode_validMultipleLines() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellCommand(VERSION_CODE_CMD))
                .thenReturn(
                        "   versionCode=456 minSdk=25 targetSdk=25"
                                + "\nversionCode=123 minSdk=25 targetSdk=25");

        Assert.assertEquals(
                "456",
                AppVersionFetcher.fetch(mMockDevice, PACKAGE_NAME, AppVersionInfo.VERSION_CODE));
    }

    @Test
    public void testFetchVersionName_validMultipleLines() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellCommand(VERSION_NAME_CMD))
                .thenReturn("    versionName=2.7.0.1\n    versionName=2.7.0.0");

        Assert.assertEquals(
                "2.7.0.1",
                AppVersionFetcher.fetch(mMockDevice, PACKAGE_NAME, AppVersionInfo.VERSION_NAME));
    }

    @Test
    public void testFetchVersionCode_invalidResponse() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellCommand(VERSION_CODE_CMD)).thenReturn("invalid response");

        try {
            AppVersionFetcher.fetch(mMockDevice, PACKAGE_NAME, AppVersionInfo.VERSION_CODE);
            Assert.fail();
        } catch (IllegalStateException expectedException) {
        }
    }

    @Test
    public void testFetchVersionName_invalidResponse() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellCommand(VERSION_NAME_CMD)).thenReturn("invalid response");

        try {
            AppVersionFetcher.fetch(mMockDevice, PACKAGE_NAME, AppVersionInfo.VERSION_NAME);
            Assert.fail();
        } catch (IllegalStateException expectedException) {
        }
    }
}
