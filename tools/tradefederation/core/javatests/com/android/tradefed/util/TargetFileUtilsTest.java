/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.TargetFileUtils.FilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetFileUtilsTest {
    private @Mock ITestDevice mMockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /** Test {@link TargetFileUtils#hasPermission()}. */
    @Test
    public void testHasPermission() throws DeviceNotAvailableException {
        for (int x = 0; x <= 7; x++) {
            for (int y = 0; y <= 7; y++) {
                for (int z = 0; z <= 7; z++) {
                    String permission = "" + x + y + z;
                    // Determines if the permission bits grant read permission to any group.
                    if (hasPermission(x, FilePermission.READ)
                            || hasPermission(y, FilePermission.READ)
                            || hasPermission(z, FilePermission.READ)) {
                        assertTrue(TargetFileUtils.hasPermission(FilePermission.READ, permission));
                    }
                    // Determines if the permission bits grant write permission to any group.
                    if (hasPermission(x, FilePermission.WRITE)
                            || hasPermission(y, FilePermission.WRITE)
                            || hasPermission(z, FilePermission.WRITE)) {
                        assertTrue(TargetFileUtils.hasPermission(FilePermission.WRITE, permission));
                    }
                    // Determines if the permission bits grant EXECUTE permission to any group.
                    if (hasPermission(x, FilePermission.EXECUTE)
                            || hasPermission(y, FilePermission.EXECUTE)
                            || hasPermission(z, FilePermission.EXECUTE)) {
                        assertTrue(
                                TargetFileUtils.hasPermission(FilePermission.EXECUTE, permission));
                    }
                }
            }
        }
    }

    private boolean hasPermission(int bit, FilePermission permission) {
        return (bit & permission.getPermissionNum()) != 0;
    }

    /** Test {@link TargetFileUtils#findFile()} with NumberFormatException be asserted. */
    @Test
    public void testFindFile() throws DeviceNotAvailableException {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setStdout("path1\npath2\npath3\n");
        commandResult.setExitCode(0);
        doReturn(commandResult)
                .when(mMockDevice)
                .executeShellV2Command("find findPath -name \"namePattern\" option1 option2");
        ArrayList<String> findPaths = new ArrayList<>();
        findPaths.add("path1");
        findPaths.add("path2");
        findPaths.add("path3");
        String[] options = {"option1", "option2"};
        assertEquals(
                findPaths,
                TargetFileUtils.findFile(
                        "findPath", "namePattern", Arrays.asList(options), mMockDevice));
    }

    /** Test {@link TargetFileUtils#findFile()} with shell command failed. */
    @Test
    public void testFindFile_w_cmd_result_fail() throws DeviceNotAvailableException {
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setStdout("path1\npath2\npath3\n");
        commandResult.setExitCode(0);
        doReturn(commandResult)
                .when(mMockDevice)
                .executeShellV2Command("find findPath -name \"namePattern\"");
        ArrayList<String> findPaths = new ArrayList<>();
        assertEquals(
                findPaths, TargetFileUtils.findFile("findPath", "namePattern", null, mMockDevice));
    }

    /** Test {@link TargetFileUtils#findFile()} which have stdout with empty line. */
    @Test
    public void testFindFile_w_empty_line_stdout() throws DeviceNotAvailableException {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setStdout("");
        commandResult.setExitCode(0);
        doReturn(commandResult)
                .when(mMockDevice)
                .executeShellV2Command("find findPath -name \"namePattern\"");
        ArrayList<String> findPaths = new ArrayList<>();
        assertEquals(
                findPaths, TargetFileUtils.findFile("findPath", "namePattern", null, mMockDevice));
    }
}
