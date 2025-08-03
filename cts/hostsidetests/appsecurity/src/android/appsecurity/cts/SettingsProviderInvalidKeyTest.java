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

package android.appsecurity.cts;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SettingsProviderInvalidKeyTest extends BaseAppSecurityTest {
    private static final String TEST_APK = "CtsSettingsProviderInvalidKeyTestApp.apk";
    private static final String TEST_PACKAGE = "com.android.cts.settingsproviderinvalidkeytestapp";
    private static final String TEST_CLASS = TEST_PACKAGE + ".SettingsProviderInvalidKeyTest";

    @Before
    public void setUp() throws Exception {
        new InstallMultiple().addFile(TEST_APK).run();
        assertTrue(getDevice().isPackageInstalled(TEST_PACKAGE));
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_PACKAGE);
    }

    @Test
    public void testLongKeysAreRejected() throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testLongKeysAreRejected");
    }
}
