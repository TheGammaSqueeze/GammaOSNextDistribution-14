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

package android.localemanager.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to check that {@link android.app.LocaleManager} APIs persist across device restarts.
 *
 * <p>When this test builds, it also builds {@link android.localemanager.app.MainActivity} into an
 * APK which is then installed at runtime. The Activity does not do anything interesting, but we
 * need it to be installed so that we can set an app-specific locale for it's package.
 *
 * <p><b>Note:</b> A more comprehensive set of CTS cases are included in the instrumentation suite
 * at {@link android.localemanager.cts.LocaleManagerTests}.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class LocaleManagerHostTest implements IDeviceTest {

    private static final String TAG = LocaleManagerHostTest.class.getSimpleName();

    private static final String INSTALLED_PACKAGE_NAME = "android.localemanager.app";

    private static final String DEFAULT_LANGUAGE_TAGS = "hi-IN,de-DE";
    private static final String EMPTY_LANGUAGE_TAGS = "";

    private static final String GET_APP_LOCALES_SHELL_OUTPUT_FORMAT =
            "Locales for %s for user 0 are [%s]\n";
    private static final String DEFAULT_LANGUAGE_TAGS_GET_APP_LOCALES_SHELL_OUTPUT =
            String.format(GET_APP_LOCALES_SHELL_OUTPUT_FORMAT,
                    INSTALLED_PACKAGE_NAME, DEFAULT_LANGUAGE_TAGS);
    private static final String EMPTY_LANGUAGE_TAGS_GET_APP_LOCALES_SHELL_OUTPUT =
            String.format(GET_APP_LOCALES_SHELL_OUTPUT_FORMAT,
                    INSTALLED_PACKAGE_NAME, EMPTY_LANGUAGE_TAGS);

    private ITestDevice mDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Before
    public void setUp() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);
        resetAppLocales();
    }

    @Test
    public void testSetApplicationLocale_nonEmptyLocales_persistsAcrossReboots() throws Exception {
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME, DEFAULT_LANGUAGE_TAGS);
        String appLocalesBeforeRestart =
                executeGetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME);
        assertEquals(DEFAULT_LANGUAGE_TAGS_GET_APP_LOCALES_SHELL_OUTPUT, appLocalesBeforeRestart);

        restartDeviceAndWaitUntilReady();

        // Verify locales still equal after restart
        String appLocalesAfterRestart = executeGetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME);
        assertEquals(DEFAULT_LANGUAGE_TAGS_GET_APP_LOCALES_SHELL_OUTPUT, appLocalesAfterRestart);
    }

    @Test
    public void testSetApplicationLocale_emptyLocales_persistsAcrossReboots() throws Exception {
        // set some application locales to start with
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME, DEFAULT_LANGUAGE_TAGS);
        String appLocalesBase = executeGetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME);
        assertEquals(DEFAULT_LANGUAGE_TAGS_GET_APP_LOCALES_SHELL_OUTPUT, appLocalesBase);

        // reset the application locales to empty
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME, EMPTY_LANGUAGE_TAGS);
        String appLocalesBeforeRestart =
                executeGetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME);
        assertEquals(EMPTY_LANGUAGE_TAGS_GET_APP_LOCALES_SHELL_OUTPUT, appLocalesBeforeRestart);

        restartDeviceAndWaitUntilReady();
        // Verify new empty locales persisted after restart
        String appLocalesAfterRestart = executeGetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME);
        assertEquals(EMPTY_LANGUAGE_TAGS_GET_APP_LOCALES_SHELL_OUTPUT, appLocalesAfterRestart);
    }

    private void restartDeviceAndWaitUntilReady() throws Exception {
        // Flush pending writes before rebooting device
        getDevice().executeShellCommand("am write");
        getDevice().reboot();
    }


    private void resetAppLocales() throws Exception {
        executeSetApplicationLocalesCommand(INSTALLED_PACKAGE_NAME, EMPTY_LANGUAGE_TAGS);
    }


    private void executeSetApplicationLocalesCommand(String packageName, String languageTags)
            throws Exception {
        getDevice().executeShellCommand(
                String.format(
                        "cmd locale set-app-locales %s --user 0 --locales %s",
                        packageName,
                        languageTags
                )
        );
    }

    private String executeGetApplicationLocalesCommand(String packageName) throws Exception {
        return getDevice().executeShellCommand(
                String.format(
                        "cmd locale get-app-locales %s --user 0",
                        packageName
                )
        );
    }

}
