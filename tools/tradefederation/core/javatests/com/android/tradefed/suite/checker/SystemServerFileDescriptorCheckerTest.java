/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.suite.checker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.suite.checker.StatusCheckerResult.CheckStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link SystemServerFileDescriptorChecker} */
@RunWith(JUnit4.class)
public class SystemServerFileDescriptorCheckerTest {

    private SystemServerFileDescriptorChecker mChecker;
    @Mock ITestDevice mMockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mChecker = new SystemServerFileDescriptorChecker();
    }

    @Test
    public void testFailToGetPid() throws Exception {
        when(mMockDevice.getProperty(Mockito.eq("ro.build.type"))).thenReturn("userdebug");
        when(mMockDevice.executeShellCommand(Mockito.eq("pidof system_server")))
                .thenReturn("not found\n");

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
    }

    @Test
    public void testFailToGetFdCount() throws Exception {
        when(mMockDevice.getProperty(Mockito.eq("ro.build.type"))).thenReturn("userdebug");
        when(mMockDevice.executeShellCommand(Mockito.eq("pidof system_server")))
                .thenReturn("1024\n");
        when(mMockDevice.executeShellCommand(Mockito.eq("su root ls /proc/1024/fd | wc -w")))
                .thenReturn("not found\n");

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
    }

    @Test
    public void testAcceptableFdCount() throws Exception {
        when(mMockDevice.getProperty(Mockito.eq("ro.build.type"))).thenReturn("userdebug");
        when(mMockDevice.executeShellCommand(Mockito.eq("pidof system_server")))
                .thenReturn("914\n");
        when(mMockDevice.executeShellCommand(Mockito.eq("su root ls /proc/914/fd | wc -w")))
                .thenReturn("382  \n");

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());
    }

    @Test
    public void testUnacceptableFdCount() throws Exception {
        when(mMockDevice.getProperty(Mockito.eq("ro.build.type"))).thenReturn("userdebug");
        when(mMockDevice.executeShellCommand(Mockito.eq("pidof system_server")))
                .thenReturn("914\n");
        when(mMockDevice.executeShellCommand(Mockito.eq("su root ls /proc/914/fd | wc -w")))
                .thenReturn("1002  \n");

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        StatusCheckerResult postResult = mChecker.postExecutionCheck(mMockDevice);
        assertEquals(CheckStatus.FAILED, postResult.getStatus());
        assertNotNull(postResult.getErrorMessage());
    }

    @Test
    public void testUserBuild() throws Exception {
        when(mMockDevice.getProperty(Mockito.eq("ro.build.type"))).thenReturn("user");

        assertEquals(CheckStatus.SUCCESS, mChecker.preExecutionCheck(mMockDevice).getStatus());
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mMockDevice).getStatus());

        verify(mMockDevice).getProperty(Mockito.eq("ro.build.type"));
        // no further calls should happen after above
        verifyNoMoreInteractions(mMockDevice);
    }
}
