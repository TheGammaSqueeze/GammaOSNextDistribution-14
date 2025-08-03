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

package com.android.tests.packagemanager.multiuser.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class PackageManagerMultiUserTestBase extends BaseHostJUnit4Test {
    private static final String RUNNER = "androidx.test.runner.AndroidJUnitRunner";
    private static final String TEST_PACKAGE = "com.android.tests.packagemanager.multiuser.app";
    private static final String TEST_CLASS = ".PackageManagerMultiUserTest";
    private static final long DEFAULT_TIMEOUT = 10 * 60 * 1000L;

    private static final String DEV_CREATE_OVERRIDE_PROPERTY = "debug.user.creation_override";

    private List<Integer> mCreatedUsers;
    protected int mUserId;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Device does not support multiple users",
                getDevice().getMaxNumberOfUsersSupported() > 1);
        mUserId = getDevice().getCurrentUser();
        mCreatedUsers = new ArrayList<>();
    }

    /** Remove created users after tests. */
    @After
    public void tearDown() throws Exception {
        if (mCreatedUsers == null) {
            return;
        }
        for (int userId : mCreatedUsers) {
            removeUser(userId);
        }
    }

    protected void runDeviceTestAsUser(
            String pkgName, @Nullable String testClassName,
            @Nullable String testMethodName, int userId,
            Map<String, String> params) throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = pkgName + testClassName;
        }

        runDeviceTests(
                getDevice(),
                RUNNER,
                pkgName,
                testClassName,
                testMethodName,
                userId,
                DEFAULT_TIMEOUT,
                DEFAULT_TIMEOUT,
                0L /* maxInstrumentationTimeoutMs */,
                true /* checkResults */,
                false /* isHiddenApiCheckDisabled */,
                params == null ? Collections.emptyMap() : params);
    }

    protected void runDeviceTestAsUser(String testMethodName, int userId,
            Map<String, String> params)
            throws DeviceNotAvailableException {
        runDeviceTestAsUser(TEST_PACKAGE, TEST_CLASS, testMethodName, userId, params);
    }

    protected int createUser() throws Exception {
        final String oldPropertyValue = getSystemProperty(DEV_CREATE_OVERRIDE_PROPERTY);
        setSystemProperty(DEV_CREATE_OVERRIDE_PROPERTY, "1");
        try {
            String command = "pm create-user TestUser_" + System.currentTimeMillis();
            LogUtil.CLog.d("Starting command " + command);
            String commandOutput = getDevice().executeShellCommand(command);
            LogUtil.CLog.d("Output for command " + command + ": " + commandOutput);

            // Extract the id of the new user.
            String[] tokens = commandOutput.split("\\s+");
            assertTrue(commandOutput, tokens.length > 0);
            assertEquals(commandOutput, "Success:", tokens[0]);
            int userId = Integer.parseInt(tokens[tokens.length - 1]);
            mCreatedUsers.add(userId);

            setupUser(userId);
            return userId;
        } finally {
            setSystemProperty(DEV_CREATE_OVERRIDE_PROPERTY,
                    oldPropertyValue.isEmpty() ? "invalid" : oldPropertyValue);
        }
    }

    protected void setupUser(int userId) throws Exception {
        installExistingPackageForUser(TEST_PACKAGE, userId);
    }

    protected void removeUser(int userId) throws Exception {
        String command = "pm remove-user " + userId;
        LogUtil.CLog.d("Starting command " + command);
        String commandOutput = getDevice().executeShellCommand(command);
        LogUtil.CLog.d("Output for command " + command + ": " + commandOutput);
    }

    protected void installExistingPackageForUser(String pkgName, int userId) throws Exception {
        String command = "pm install-existing --user " + userId + " " + pkgName;
        LogUtil.CLog.d("Starting command " + command);
        String commandOutput = getDevice().executeShellCommand(command);
        LogUtil.CLog.d("Output for command " + command + ": " + commandOutput);
    }

    protected boolean isPackageInstalledForUser(String pkgName, int userId) throws Exception {
        return getDevice().isPackageInstalled(pkgName, String.valueOf(userId));
    }

    private void setSystemProperty(String name, String value) throws Exception {
        assertEquals("", getDevice().executeShellCommand("setprop " + name + " " + value));
    }

    private String getSystemProperty(String prop) throws Exception {
        return getDevice().executeShellCommand("getprop " + prop).replace("\n", "");
    }
}
