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

package android.appsecurity.cts;

import com.android.compatibility.common.util.NonApiTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that verifies storage APIs for packages without app data storage.
 */
@NonApiTest(
        exemptionReasons = {},
        justification = "Testing behaviour for apps that have defined"
        + "android.content.pm.PROPERTY_NO_APP_DATA_STORAGE in their manifest")
@RunWith(DeviceJUnit4ClassRunner.class)
public class NoAppDataStorageHostTest extends BaseHostJUnit4Test {

    private static final String APK_NO_APP_STORAGE = "CtsNoAppDataStorageApp.apk";
    private static final String CLASS_NO_APP_STORAGE =
            "com.android.cts.noappstorage.NoAppDataStorageTest";
    private static final String PKG_NO_APP_STORAGE = "com.android.cts.noappstorage";


    @Before
    public void setUp() throws Exception {
        installPackage(APK_NO_APP_STORAGE);
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(PKG_NO_APP_STORAGE);
    }

    @Test
    public void testNoInternalAppStorage() throws Exception {
        Utils.runDeviceTests(getDevice(), PKG_NO_APP_STORAGE, CLASS_NO_APP_STORAGE,
                "testNoInternalCeStorage");
        Utils.runDeviceTests(getDevice(), PKG_NO_APP_STORAGE, CLASS_NO_APP_STORAGE,
                "testNoInternalDeStorage");
    }

    @Test
    public void testNoExternalAppStorage() throws Exception {
        Utils.runDeviceTests(getDevice(), PKG_NO_APP_STORAGE, CLASS_NO_APP_STORAGE,
                "testNoExternalStorage");
    }
}
