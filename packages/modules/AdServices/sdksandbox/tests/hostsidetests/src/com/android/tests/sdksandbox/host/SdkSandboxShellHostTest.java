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

package com.android.tests.sdksandbox.host;


import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;

@Ignore("b/242829515,b/242829475,b/242830221,b/242829965")
@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxShellHostTest extends BaseHostJUnit4Test {

    private static final String DEBUGGABLE_APP_PACKAGE = "com.android.sdksandbox.debuggable";
    private static final String DEBUGGABLE_APP_ACTIVITY = "SdkSandboxTestDebuggableActivity";

    private static final String APP_PACKAGE = "com.android.sdksandbox.app";
    private static final String APP_ACTIVITY = "SdkSandboxTestActivity";

    private static final String DEBUGGABLE_APP_SANDBOX_NAME = DEBUGGABLE_APP_PACKAGE
      + "_sdk_sandbox";
    private static final String APP_SANDBOX_NAME = APP_PACKAGE + "_sdk_sandbox";

    private HashSet<Integer> mOriginalUsers;

    @Before
    public void setUp() throws Exception {
        assertThat(getBuild()).isNotNull();
        assertThat(getDevice()).isNotNull();

        // Ensure neither app is currently running
        for (String pkg : new String[]{APP_PACKAGE, DEBUGGABLE_APP_PACKAGE}) {
            clearProcess(pkg);
        }

        mOriginalUsers = new HashSet<>(getDevice().listUsers());

        assertThat(getDevice().enableAdbRoot()).isTrue();
    }

    @After
    public void tearDown() throws Exception {
        for (Integer userId : getDevice().listUsers()) {
            if (!mOriginalUsers.contains(userId)) {
                getDevice().removeUser(userId);
            }
        }
        getDevice().disableAdbRoot();
    }

    @Test
    public void testStartAndStopSdkSandboxSucceedsForDebuggableApp() throws Exception {
        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox start %s", DEBUGGABLE_APP_PACKAGE));
        assertThat(output.getStderr()).isEmpty();
        assertThat(output.getStatus()).isEqualTo(CommandStatus.SUCCESS);

        String processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).contains(DEBUGGABLE_APP_SANDBOX_NAME);

        output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox stop %s", DEBUGGABLE_APP_PACKAGE));
        assertThat(output.getStderr()).isEmpty();
        assertThat(output.getStatus()).isEqualTo(CommandStatus.SUCCESS);

        processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).doesNotContain(DEBUGGABLE_APP_SANDBOX_NAME);
    }

    @Test
    public void testStartSdkSandboxFailsForNonDebuggableApp() throws Exception {
        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox start %s", APP_PACKAGE));
        assertThat(output.getStatus()).isEqualTo(CommandStatus.FAILED);

        String processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).doesNotContain(APP_SANDBOX_NAME);
    }

    @Test
    public void testStartSdkSandboxFailsForIncorrectUser() throws Exception {
        int otherUserId = getDevice().createUser("TestUser_" + System.currentTimeMillis());
        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox start --user %s %s",
                        otherUserId, DEBUGGABLE_APP_PACKAGE));
        assertThat(output.getStatus()).isEqualTo(CommandStatus.FAILED);

        String processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).doesNotContain(DEBUGGABLE_APP_SANDBOX_NAME);
    }

    @Test
    public void testStopSdkSandboxSucceedsForRunningDebuggableApp() throws Exception {
        startActivity(DEBUGGABLE_APP_PACKAGE, DEBUGGABLE_APP_ACTIVITY);

        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox stop %s", DEBUGGABLE_APP_PACKAGE));
        assertThat(output.getStderr()).isEmpty();
        assertThat(output.getStatus()).isEqualTo(CommandStatus.SUCCESS);

        String processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).doesNotContain(DEBUGGABLE_APP_SANDBOX_NAME);
    }

    @Test
    public void testStartSdkSandboxFailsForInvalidPackage() throws Exception {
        String invalidPackage = "com.android.sdksandbox.nonexistent";
        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox start %s", invalidPackage));
        assertThat(output.getStatus()).isEqualTo(CommandStatus.FAILED);
    }

    @Test
    public void testStopSdkSandboxFailsForNonDebuggableApp() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        CommandResult output = getDevice().executeShellV2Command(
                String.format("cmd sdk_sandbox stop %s", APP_PACKAGE));
        assertThat(output.getStatus()).isEqualTo(CommandStatus.FAILED);

        String processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).contains(APP_SANDBOX_NAME);
    }

    @Test
    public void testStopSdkSandboxFailsForIncorrectUser() throws Exception {
        startActivity(DEBUGGABLE_APP_PACKAGE, DEBUGGABLE_APP_ACTIVITY);

        int otherUserId = getDevice().createUser("TestUser_" + System.currentTimeMillis());
        CommandResult output = getDevice().executeShellV2Command(String.format(
                "cmd sdk_sandbox stop --user %s %s", otherUserId, DEBUGGABLE_APP_PACKAGE));
        assertThat(output.getStatus()).isEqualTo(CommandStatus.FAILED);

        String processDump = getDevice().executeShellCommand("ps -A");
        assertThat(processDump).contains(DEBUGGABLE_APP_SANDBOX_NAME);
    }

    private void clearProcess(String pkg) throws Exception {
        getDevice().executeShellCommand(String.format("pm clear %s", pkg));
    }

    private void startActivity(String pkg, String activity) throws Exception {
        getDevice().executeShellCommand(String.format("am start -W -n %s/.%s", pkg, activity));
    }
}
