/*
 * Copyright 2022 The Android Open Source Project
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
package com.android.cts.graphics;

import com.android.tradefed.util.RunUtil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class GpuWorkDumpsysTest extends BaseHostJUnit4Test {

  private static final String DUMPSYS_COMMAND = "dumpsys gpu --gpuwork";
  private static final String TEST_PKG = "com.android.cts.framestatstestapp";
  private static final int PAUSE_MILLIS = 3000;
  private static final String GPU_WORK_PERIOD_TRACEPOINT_FORMAT_PATH =
      "/sys/kernel/tracing/events/power/gpu_work_period/format";

  private CommandResult assertShellCommand(String command) throws DeviceNotAvailableException {
    CommandResult commandResult = getDevice().executeShellV2Command(command);

    // It must succeed.
    assertEquals(
        String.format("Failed shell command: %s", command),
        CommandStatus.SUCCESS,
        commandResult.getStatus());

    return commandResult;
  }

  private long getTestAppUid() throws DeviceNotAvailableException {
    int currentUser = getDevice().getCurrentUser();
    CommandResult commandResult =
        assertShellCommand(
            String.format("cmd package list packages -U --user %d %s", currentUser, TEST_PKG));
    String[] parts = commandResult.getStdout().split(":");
    // Example output:
    // package:com.android.cts.framestatstestapp uid:10183
    assertTrue(
        String.format("Unexpected output getting package uid:\n%s", commandResult.getStdout()),
        parts.length > 2);

    long appUid = Long.parseLong(parts[2].trim());
    assertTrue(String.format("Unexpected app uid: %d", appUid), appUid > 10000);
    return appUid;
  }

  @CddTest(requirement = "6.1/C-6-1")
  @Test
  public void testOutputFormat() throws Exception {
    CommandResult commandResult =
        getDevice()
            .executeShellV2Command(String.format("cat %s", GPU_WORK_PERIOD_TRACEPOINT_FORMAT_PATH));

    // If we fail to cat the tracepoint format then the test ends here. If the tracing file system
    // is inaccessible, then this test won't fail. We rely on VTS tests to ensure that the tracing
    // file system is accessible.
    assumeTrue(
        String.format(
            "Failed to cat the gpu_work_period tracepoint format at %s",
            GPU_WORK_PERIOD_TRACEPOINT_FORMAT_PATH),
        commandResult.getStatus().equals(CommandStatus.SUCCESS));

    // Turn screen on.
    assertShellCommand("input keyevent KEYCODE_WAKEUP");
    RunUtil.getDefault().sleep(PAUSE_MILLIS);

    // Skip lock screen.
    assertShellCommand("wm dismiss-keyguard");
    RunUtil.getDefault().sleep(PAUSE_MILLIS);

    // Start basic app.
    assertShellCommand("am start -W -S " + TEST_PKG);
    RunUtil.getDefault().sleep(PAUSE_MILLIS);

    // Get the UID of the test app.
    long appUid = getTestAppUid();

    // Execute dumpsys command.
    commandResult = assertShellCommand(DUMPSYS_COMMAND);

    assertFalse(
        String.format(
            "The command shows errors.\nCommand: %s\nOutput:\n%s\n",
            DUMPSYS_COMMAND, commandResult.getStdout()),
        commandResult.getStdout().contains("[errors:"));

    String[] lines = commandResult.getStdout().trim().split("\n");
    int i = 0;
    for (; i < lines.length; ++i) {
      if (lines[i].startsWith("gpu_id uid")) {
        break;
      }
    }
    assertTrue(
        String.format(
            "Could not find \"gpu_id uid ...\" header in output:\n%s\n", commandResult.getStdout()),
        i < lines.length);

    Pattern uidInfoPattern = Pattern.compile(String.format("\\d+ (%d) (\\d+) (\\d+)", appUid));
    for (; i < lines.length; ++i) {
      Matcher matcher = uidInfoPattern.matcher(lines[i]);
      if (!matcher.lookingAt()) {
        continue;
      }
      if (appUid == Long.parseLong(matcher.group(1))
          && Long.parseLong(matcher.group(2)) > 0
          && Long.parseLong(matcher.group(3)) > 0) {
        // Success!
        return;
      }
    }
    fail(
        String.format(
            "Could not find UID %d with non-zero active and inactive GPU time in output:\n%s\n",
            appUid, commandResult.getStdout()));
  }
}
