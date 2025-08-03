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

package android.inputmethodservice.cts.hostside;

import android.inputmethodservice.cts.common.test.DeviceTestConstants;
import android.inputmethodservice.cts.common.test.TestInfo;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test IInputMethodManager#shellComman verifies callers.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ShellCommandFromAppTest extends BaseHostJUnit4Test {

    /**
     * Run device test with disabling hidden API check.
     *
     * @param testInfo test to be executed.
     * @param instant {@code true} when {@code testInfo} needs to be installed as an instant app.
     */
    private void runDeviceTestMethodWithoutHiddenApiCheck(TestInfo testInfo, boolean instant)
            throws Exception {
        if (instant) {
            installPackage(DeviceTestConstants.APK, "-r", "--instant");
        } else {
            installPackage(DeviceTestConstants.APK, "-r");
        }
        runDeviceTests(new DeviceTestRunOptions(testInfo.testPackage)
                .setDevice(getDevice())
                .setDisableHiddenApiCheck(false)
                .setTestClassName(testInfo.testClass)
                .setTestMethodName(testInfo.testMethod));
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{}, null, receiver)}
     * returns {@link SecurityException} for full (non-instant) apps.
     */
    @AppModeFull
    @Test
    public void testShellCommandFull() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND, false);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{}, null, receiver)}
     * returns {@link SecurityException} for instant apps.
     */
    @AppModeInstant
    @Test
    public void testShellCommandInstant() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND, true);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime"}, null, receiver)}
     * returns {@link SecurityException} for full (non-instant) apps.
     */
    @AppModeFull
    @Test
    public void testShellCommandImeFull() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_IME, false);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime"}, null, receiver)}
     * returns {@link SecurityException} for instant apps.
     */
    @AppModeInstant
    @Test
    public void testShellCommandImeInstant() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_IME, true);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime", "list"}, null,
     * receiver)} returns {@link SecurityException} for full (non-instant) apps.
     */
    @AppModeFull
    @Test
    public void testShellCommandImeListFull() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_IME_LIST,
                false);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime", "list"}, null,
     * receiver)} returns {@link SecurityException} for instant apps.
     */
    @AppModeInstant
    @Test
    public void testShellCommandImeListInstant() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_IME_LIST,
                true);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime", "list"}, null,
     * receiver)} returns {@link SecurityException} for full (non-instant) apps.
     */
    @AppModeFull
    @Test
    public void testShellCommandDumpFull() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_DUMP,
                false);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime", "list"}, null,
     * receiver)} returns {@link SecurityException} for instant apps.
     */
    @AppModeInstant
    @Test
    public void testShellCommandDumpInstant() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_DUMP, true);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime", "list"}, null,
     * receiver)} returns {@link SecurityException} for full (non-instant) apps.
     */
    @AppModeFull
    @Test
    public void testShellCommandHelpFull() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_HELP,
                false);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime", "list"}, null,
     * receiver)} returns {@link SecurityException} for instant apps.
     */
    @AppModeInstant
    @Test
    public void testShellCommandHelpInstant() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_HELP, true);
    }
}
