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
package com.android.tradefed.targetprep.adb;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;

/** Unit tests for {@link AdbStopServerPreparer}. */
@RunWith(JUnit4.class)
public class AdbStopServerPreparerTest {

    private AdbStopServerPreparer mPreparer;
    @Mock IRunUtil mMockRunUtil;
    @Mock IDeviceManager mMockManager;

    private TestInformation mTestInfo;
    @Mock ITestDevice mMockDevice;
    private IBuildInfo mMockBuild;
    private File mFakeAdbFile;
    private String mEnvironment;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mEnvironment = null;
        mPreparer =
                new AdbStopServerPreparer() {
                    @Override
                    IRunUtil createRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    IDeviceManager getDeviceManager() {
                        return mMockManager;
                    }

                    @Override
                    String getEnvironment(String key) {
                        return mEnvironment;
                    }

                    @Override
                    protected File findAdbLog() {
                        return null;
                    }
                };
        mMockBuild = new BuildInfo();
        mFakeAdbFile = FileUtil.createTempFile("adb", "");
        mMockBuild.setFile("adb", mFakeAdbFile, "v1");

        mMockRunUtil.sleep(2000);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        InvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuild);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mFakeAdbFile);
    }

    /** Test that during setup the adb server is stopped then restart with a new found version. */
    @Test
    public void testSetup_tearDown() throws Exception {

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        // setUp
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("kill-server")))
                .thenReturn(result);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(), Mockito.contains("adb"), Mockito.eq("start-server")))
                .thenReturn(result);
        // tear down
        mockTearDown();

        mPreparer.setUp(mTestInfo);
        mPreparer.tearDown(mTestInfo, null);

        verify(mMockManager).stopAdbBridge();
    }

    /** Test when restarting the adb server fails. */
    @Test
    public void testSetup_fail_tearDown() throws Exception {

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        // setUp
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("kill-server")))
                .thenReturn(result);

        CommandResult failedResult = new CommandResult(CommandStatus.FAILED);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(), Mockito.contains("adb"), Mockito.eq("start-server")))
                .thenReturn(failedResult);

        // tear down
        mockTearDown();

        try {
            mPreparer.setUp(mTestInfo);
            fail("Should have thrown an exception.");
        } catch (TargetSetupError expected) {
            // Expected
        }
        mPreparer.tearDown(mTestInfo, null);

        verify(mMockManager).stopAdbBridge();
    }

    /** Test that we fail the preparation when there is no adb to test. */
    @Test
    public void testNoAdb() throws Exception {

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        // setUp
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("kill-server")))
                .thenReturn(result);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("start-server")))
                .thenReturn(result);

        try {
            InvocationContext context = new InvocationContext();
            context.addAllocatedDevice("device", mMockDevice);
            context.addDeviceBuildInfo("device", new BuildInfo());
            mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
            mPreparer.setUp(mTestInfo);
            fail("Should have thrown an exception.");
        } catch (TargetSetupError expected) {
            // Expected
            assertTrue(
                    expected.getMessage()
                            .contains("Could not find a new version of adb to tests."));
        }

        verify(mMockManager).stopAdbBridge();
    }

    /** Test getting the adb binary from the local path. */
    @Test
    public void testAdbFromEnv() throws Exception {
        File tmpDir = FileUtil.createTempDir("adb-preparer-tests");
        try {
            FileUtil.mkdirsRWX(new File(tmpDir, "bin"));
            File fakeAdb = new File(tmpDir, "bin/adb");
            fakeAdb.createNewFile();
            mEnvironment = tmpDir.getAbsolutePath();

            CommandResult result = new CommandResult(CommandStatus.SUCCESS);
            // setUp
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("kill-server")))
                    .thenReturn(result);
            when(mMockRunUtil.runTimedCmd(
                            Mockito.anyLong(), Mockito.contains("adb"), Mockito.eq("start-server")))
                    .thenReturn(result);
            // tear down
            mockTearDown();

            mPreparer.setUp(mTestInfo);
            mPreparer.tearDown(mTestInfo, null);

            verify(mMockManager).stopAdbBridge();
        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }

    private void mockTearDown() {
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("kill-server")))
                .thenReturn(result);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("start-server")))
                .thenReturn(result);
        mMockManager.restartAdbBridge();
    }
}
