/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.testtype;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.config.proxy.AutomatedReporters;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.IRunUtil.EnvPriority;
import com.android.tradefed.util.SystemUtil.EnvVariable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;

/** Unit tests for {@link TfTestLauncher} */
@RunWith(JUnit4.class)
public class TfTestLauncherTest {

    private static final String CONFIG_NAME = "FAKE_CONFIG";
    private static final String TEST_TAG = "FAKE_TAG";
    private static final String BUILD_BRANCH = "FAKE_BRANCH";
    private static final String BUILD_ID = "FAKE_BUILD_ID";
    private static final String BUILD_FLAVOR = "FAKE_FLAVOR";
    private static final String SUB_GLOBAL_CONFIG = "FAKE_GLOBAL_CONFIG";

    private TfTestLauncher mTfTestLauncher;
    @Mock ITestInvocationListener mMockListener;
    @Mock IRunUtil mMockRunUtil;
    @Mock IFolderBuildInfo mMockBuildInfo;
    @Mock IConfiguration mMockConfig;
    private TestInformation mTestInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTfTestLauncher =
                new TfTestLauncher() {
                    @Override
                    protected String getJava() {
                        return "java";
                    }
                };
        mTfTestLauncher.setRunUtil(mMockRunUtil);
        mTfTestLauncher.setBuild(mMockBuildInfo);
        mTfTestLauncher.setEventStreaming(false);
        mTfTestLauncher.setConfiguration(mMockConfig);
        mTfTestLauncher.setProtoReporting(false);

        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();

        when(mMockConfig.getCommandOptions()).thenReturn(new CommandOptions());

        OptionSetter setter = new OptionSetter(mTfTestLauncher);
        setter.setOptionValue("config-name", CONFIG_NAME);
        setter.setOptionValue("sub-global-config", SUB_GLOBAL_CONFIG);
    }

    /** Test {@link TfTestLauncher#run(TestInformation, ITestInvocationListener)} */
    @Test
    public void testRun() throws DeviceNotAvailableException {
        CommandResult cr = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        (FileOutputStream) Mockito.any(),
                        (FileOutputStream) Mockito.any(),
                        Mockito.contains("java"),
                        (String) Mockito.any(),
                        Mockito.eq("--add-opens=java.base/java.nio=ALL-UNNAMED"),
                        Mockito.eq("--add-opens=java.base/sun.reflect.annotation=ALL-UNNAMED"),
                        Mockito.eq("--add-opens=java.base/java.io=ALL-UNNAMED"),
                        Mockito.eq("-cp"),
                        (String) Mockito.any(),
                        Mockito.eq("com.android.tradefed.command.CommandRunner"),
                        Mockito.eq(CONFIG_NAME),
                        Mockito.eq("-n"),
                        Mockito.eq("--test-tag"),
                        Mockito.eq(TEST_TAG),
                        Mockito.eq("--build-id"),
                        Mockito.eq(BUILD_ID),
                        Mockito.eq("--branch"),
                        Mockito.eq(BUILD_BRANCH),
                        Mockito.eq("--build-flavor"),
                        Mockito.eq(BUILD_FLAVOR),
                        Mockito.eq("--" + CommandOptions.INVOCATION_DATA),
                        Mockito.eq(SubprocessTfLauncher.SUBPROCESS_TAG_NAME),
                        Mockito.eq("true"),
                        Mockito.eq("--subprocess-report-file"),
                        (String) Mockito.any()))
                .thenReturn(cr);

        when(mMockBuildInfo.getTestTag()).thenReturn(TEST_TAG);
        when(mMockBuildInfo.getBuildBranch()).thenReturn(BUILD_BRANCH);
        when(mMockBuildInfo.getBuildFlavor()).thenReturn(BUILD_FLAVOR);

        when(mMockBuildInfo.getRootDir()).thenReturn(new File(""));
        when(mMockBuildInfo.getBuildId()).thenReturn(BUILD_ID);

        mTfTestLauncher.run(mTestInfo, mMockListener);
        verify(mMockListener, times(4))
                .testLog(
                        (String) Mockito.any(),
                        (LogDataType) Mockito.any(),
                        (FileInputStreamSource) Mockito.any());
        verify(mMockBuildInfo, times(3)).getBuildBranch();
        verify(mMockBuildInfo, times(2)).getBuildFlavor();
        verify(mMockBuildInfo, times(3)).getBuildId();
        verify(mMockRunUtil).unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE);
        verify(mMockRunUtil)
                .unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_SERVER_CONFIG_VARIABLE);
        verify(mMockRunUtil).unsetEnvVariable(SubprocessTfLauncher.ANDROID_SERIAL_VAR);
        for (String variable : AutomatedReporters.REPORTER_MAPPING) {
            verify(mMockRunUtil).unsetEnvVariable(variable);
        }
        verify(mMockRunUtil).unsetEnvVariable(EnvVariable.ANDROID_HOST_OUT_TESTCASES.name());
        verify(mMockRunUtil).unsetEnvVariable(EnvVariable.ANDROID_TARGET_OUT_TESTCASES.name());
        verify(mMockRunUtil).setEnvVariablePriority(EnvPriority.SET);
        verify(mMockRunUtil)
                .setEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE, SUB_GLOBAL_CONFIG);
        verify(mMockBuildInfo).addBuildAttribute(SubprocessTfLauncher.PARENT_PROC_TAG_NAME, "true");
        verify(mMockListener).testRunStarted("temporaryFiles", 1);
        verify(mMockListener).testRunStarted("StdErr", 1);
        verify(mMockListener, times(3)).testStarted((TestDescription) Mockito.any());
        verify(mMockListener, times(2))
                .testEnded(
                        (TestDescription) Mockito.any(), Mockito.eq(new HashMap<String, Metric>()));
        verify(mMockListener, times(2)).testRunEnded(0, new HashMap<String, Metric>());
        verify(mMockListener).testRunStarted("elapsed-time", 1);
        verify(mMockListener, times(3))
                .testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener, times(3))
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Test {@link TfTestLauncher#testTmpDirClean(File, ITestInvocationListener)} */
    @Test
    public void testTestTmpDirClean_success() {

        File tmpDir = Mockito.mock(File.class);
        Mockito.when(tmpDir.list())
                .thenReturn(
                        new String[] {
                            "inv_123",
                            "tradefed_global_log_123",
                            "lc_cache",
                            "stage-android-build-api"
                        });

        mTfTestLauncher.testTmpDirClean(tmpDir, mMockListener);

        verify(mMockListener).testRunStarted("temporaryFiles", 1);
        verify(mMockListener).testStarted((TestDescription) Mockito.any());
        verify(mMockListener)
                .testEnded(
                        (TestDescription) Mockito.any(), Mockito.eq(new HashMap<String, Metric>()));
        verify(mMockListener).testRunEnded(0, new HashMap<String, Metric>());
    }

    /**
     * Test {@link TfTestLauncher#testTmpDirClean(File, ITestInvocationListener)}
     *
     * <p>Test should fail if there are extra files do not match expected pattern.
     */
    @Test
    public void testTestTmpDirClean_failExtraFile() {
        mTfTestLauncher.setBuild(mMockBuildInfo);
        when(mMockBuildInfo.getBuildBranch()).thenReturn(BUILD_BRANCH);

        File tmpDir = Mockito.mock(File.class);
        Mockito.when(tmpDir.list()).thenReturn(new String[] {"extra_file"});

        mTfTestLauncher.testTmpDirClean(tmpDir, mMockListener);
        verify(mMockBuildInfo, times(1)).getBuildBranch();
        verify(mMockListener).testRunStarted("temporaryFiles", 1);
        verify(mMockListener).testStarted((TestDescription) Mockito.any());
        verify(mMockListener).testFailed((TestDescription) Mockito.any(),
                (FailureDescription) Mockito.any());
        verify(mMockListener)
                .testEnded(
                        (TestDescription) Mockito.any(), Mockito.eq(new HashMap<String, Metric>()));
        verify(mMockListener).testRunEnded(0, new HashMap<String, Metric>());
    }

    /**
     * Test {@link TfTestLauncher#testTmpDirClean(File, ITestInvocationListener)}
     *
     * <p>Test should fail if there are multiple files matching an expected pattern.
     */
    @Test
    public void testTestTmpDirClean_failMultipleFiles() {
        mTfTestLauncher.setBuild(mMockBuildInfo);
        when(mMockBuildInfo.getBuildBranch()).thenReturn(BUILD_BRANCH);

        File tmpDir = Mockito.mock(File.class);
        Mockito.when(tmpDir.list()).thenReturn(new String[] {"inv_1", "inv_2"});

        mTfTestLauncher.testTmpDirClean(tmpDir, mMockListener);
        verify(mMockBuildInfo, times(1)).getBuildBranch();
        verify(mMockListener).testRunStarted("temporaryFiles", 1);
        verify(mMockListener).testStarted((TestDescription) Mockito.any());
        verify(mMockListener).testFailed((TestDescription) Mockito.any(),
                (FailureDescription) Mockito.any());
        verify(mMockListener)
                .testEnded(
                        (TestDescription) Mockito.any(), Mockito.eq(new HashMap<String, Metric>()));
        verify(mMockListener).testRunEnded(0, new HashMap<String, Metric>());
    }
}
