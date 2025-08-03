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

package android.appsecurity.cts;


import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

/**
 * Test that:
 * 1) all the public fields annotated with @Readable in Settings.Secure, Settings.System,
 * Settings.Global classes are readable.
 * 2) hidden fields added before S are also readable, via their raw Settings key String values.
 * 3) public fields without the @Readable annotation will not be readable.
 *
 * Run with:
 * atest android.appsecurity.cts.ReadableSettingsFieldsTest
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ReadableSettingsFieldsTest extends BaseAppSecurityTest {
    private static final String TEST_PACKAGE = "com.android.cts.readsettingsfieldsapp";
    private static final String TEST_CLASS = TEST_PACKAGE + ".ReadSettingsFieldsTest";
    private static final String TEST_APK = "CtsReadSettingsFieldsApp.apk";
    private static final String TEST_APK_TEST_ONLY = "CtsReadSettingsFieldsAppTestOnly.apk";
    private static final String TEST_APK_TARGET_Q = "CtsReadSettingsFieldsAppTargetQ.apk";
    private static final String TEST_APK_TARGET_R = "CtsReadSettingsFieldsAppTargetR.apk";
    private static final String TEST_APK_TARGET_S = "CtsReadSettingsFieldsAppTargetS.apk";

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
    public void testSecureNonHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testSecureNonHiddenSettingsKeysAreReadable");
    }

    @Test
    public void testSystemNonHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testSystemNonHiddenSettingsKeysAreReadable");
    }

    @Test
    public void testGlobalNonHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testGlobalNonHiddenSettingsKeysAreReadable");
    }

    @Test
    public void testSecureSomeHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testSecureSomeHiddenSettingsKeysAreReadable");
    }

    @Test
    public void testSystemSomeHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testSystemSomeHiddenSettingsKeysAreReadable");
    }

    @Test
    public void testGlobalSomeHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testGlobalSomeHiddenSettingsKeysAreReadable");
    }

    @Test
    public void testGlobalHiddenSettingsKeyNotReadableWithoutPermissions() throws
            DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testGlobalHiddenSettingsKeyNotReadableWithoutPermissions");
    }

    @Test
    public void testSecureHiddenSettingsKeysNotReadableWithoutAnnotation()
            throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testSecureHiddenSettingsKeysNotReadableWithoutAnnotation");
    }

    @Test
    public void testSystemHiddenSettingsKeysNotReadableWithoutAnnotation()
            throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testSystemHiddenSettingsKeysNotReadableWithoutAnnotation");
    }

    @Test
    public void testGlobalHiddenSettingsKeysNotReadableWithoutAnnotation()
            throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testGlobalHiddenSettingsKeysNotReadableWithoutAnnotation");
    }

    @Test
    public void testSecureHiddenSettingsKeysReadableWhenTestOnly()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple().addFile(TEST_APK_TEST_ONLY).addArg("-t").run();
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testSecureHiddenSettingsKeysReadableWithoutAnnotation");
    }

    @Test
    public void testSystemHiddenSettingsKeysReadableWhenTestOnly()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple().addFile(TEST_APK_TEST_ONLY).addArg("-t").run();
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testSystemHiddenSettingsKeysReadableWithoutAnnotation");
    }

    @Test
    public void testGlobalHiddenSettingsKeysReadableWhenTestOnly()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple().addFile(TEST_APK_TEST_ONLY).addArg("-t").run();
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testGlobalHiddenSettingsKeysReadableWithoutAnnotation");
    }

    @Test
    public void testSettingsKeysNotReadableForAfterR()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple().addFile(TEST_APK_TARGET_S).run();
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testSettingsKeysNotReadableForAfterR");
    }

    @Test
    public void testSettingsKeysReadableForRMinus()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple().addFile(TEST_APK_TARGET_R).run();
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testSettingsKeysReadableForRMinus");
        new InstallMultiple().addFile(TEST_APK_TARGET_Q).run();
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testSettingsKeysReadableForRMinus");
    }

    @Test
    public void testQueryGlobalSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testQueryGlobalSettingsNoHiddenKeysWithoutAnnotation");
    }

    @Test
    public void testQuerySystemSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testQuerySystemSettingsNoHiddenKeysWithoutAnnotation");
    }

    @Test
    public void testQuerySecureSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testQuerySecureSettingsNoHiddenKeysWithoutAnnotation");
    }

    @Test
    public void testListGlobalSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testListGlobalSettingsNoHiddenKeysWithoutAnnotation");
    }

    @Test
    public void testListSystemSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testListSystemSettingsNoHiddenKeysWithoutAnnotation");
    }

    @Test
    public void testListSecureSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        runDeviceTests(TEST_PACKAGE, TEST_CLASS,
                "testListSecureSettingsNoHiddenKeysWithoutAnnotation");
    }
}
