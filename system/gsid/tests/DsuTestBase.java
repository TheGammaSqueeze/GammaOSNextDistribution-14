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

package com.android.tests.dsu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

abstract class DsuTestBase extends BaseHostJUnit4Test {
    @Option(
            name = "dsu-userdata-size-in-gb",
            description = "Userdata partition size of the DSU installation")
    private long mDsuUserdataSizeInGb;

    protected long getDsuUserdataSize(long defaultValue) {
        return mDsuUserdataSizeInGb > 0 ? mDsuUserdataSizeInGb << 30 : defaultValue;
    }

    public CommandResult assertShellCommand(String command) throws DeviceNotAvailableException {
        CommandResult result = getDevice().executeShellV2Command(command);
        assertEquals(
                String.format("'%s' status", command), CommandStatus.SUCCESS, result.getStatus());
        assertNotNull(String.format("'%s' exit code", command), result.getExitCode());
        assertEquals(String.format("'%s' exit code", command), 0, result.getExitCode().intValue());
        return result;
    }

    private String getDsuStatus() throws DeviceNotAvailableException {
        CommandResult result;
        try {
            result = assertShellCommand("gsi_tool status");
        } catch (AssertionError e) {
            CLog.e(e);
            return null;
        }
        return result.getStdout().split("\n", 2)[0].trim();
    }

    public void assertDsuStatus(String expected) throws DeviceNotAvailableException {
        assertEquals("DSU status", expected, getDsuStatus());
    }

    public boolean isDsuRunning() throws DeviceNotAvailableException {
        return "running".equals(getDsuStatus());
    }

    public void assertDsuRunning() throws DeviceNotAvailableException {
        assertTrue("Expected DSU running", isDsuRunning());
    }

    public void assertDsuNotRunning() throws DeviceNotAvailableException {
        assertFalse("Expected DSU not running", isDsuRunning());
    }

    public void assertAdbRoot() throws DeviceNotAvailableException {
        assertTrue("Failed to 'adb root'", getDevice().enableAdbRoot());
    }

    public void assertDevicePathExist(String path) throws DeviceNotAvailableException {
        assertTrue(String.format("Expected '%s' to exist", path), getDevice().doesFileExist(path));
    }

    public void assertDevicePathNotExist(String path) throws DeviceNotAvailableException {
        assertFalse(
                String.format("Expected '%s' to not exist", path), getDevice().doesFileExist(path));
    }
}
