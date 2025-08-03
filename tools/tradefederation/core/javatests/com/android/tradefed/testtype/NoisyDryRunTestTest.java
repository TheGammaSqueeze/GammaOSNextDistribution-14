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
package com.android.tradefed.testtype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.config.proxy.AutomatedReporters;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.sandbox.SandboxConfigDump;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.IRunUtil.EnvPriority;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/** Unit test for {@link NoisyDryRunTest}. */
@RunWith(JUnit4.class)
public class NoisyDryRunTestTest {

    private File mFile;
    @Mock ITestInvocationListener mMockListener;
    @Mock IRunUtil mMockRunUtil;
    private TestInformation mTestInfo;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Initialize the global config if it was not (when running inside eclipse for example).
        try {
            GlobalConfiguration.createGlobalConfiguration(new String[] {"empty "});
        } catch (IllegalStateException e) {
            // Avoid double init.
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFile = FileUtil.createTempFile("NoisyDryRunTestTest", "tmpFile");

        mTestInfo = TestInformation.newBuilder().build();
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mFile);
    }

    @Test
    public void testRun() throws Exception {
        FileUtil.writeToFile("tf/fake\n" + "tf/fake", mFile);
        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseFile", 1);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        mMockListener.testRunStarted(
                "com.android.tradefed.testtype.NoisyDryRunTest_parseCommands", 2);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        NoisyDryRunTest noisyDryRunTest = new NoisyDryRunTest();
        OptionSetter setter = new OptionSetter(noisyDryRunTest);
        setter.setOptionValue("cmdfile", mFile.getAbsolutePath());
        noisyDryRunTest.run(mTestInfo, mMockListener);
        verifyMocks();
    }

    /**
     * Test loading a configuration with a USE_KEYSTORE option specified. It should still load and
     * we fake the keystore to simply validate the overall structure.
     */
    @Test
    public void testRun_withKeystore() throws Exception {
        FileUtil.writeToFile("tf/fake --fail-invocation-with-cause USE_KEYSTORE@fake\n", mFile);
        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseFile", 1);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        mMockListener.testRunStarted(
                "com.android.tradefed.testtype.NoisyDryRunTest_parseCommands", 1);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());

        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        NoisyDryRunTest noisyDryRunTest = new NoisyDryRunTest();
        OptionSetter setter = new OptionSetter(noisyDryRunTest);
        setter.setOptionValue("cmdfile", mFile.getAbsolutePath());
        noisyDryRunTest.run(mTestInfo, mMockListener);
        verifyMocks();
    }

    @Test
    public void testRun_invalidCmdFile() throws Exception {
        FileUtil.deleteFile(mFile);
        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseFile", 1);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testFailed(Mockito.any(), (String) Mockito.any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        NoisyDryRunTest noisyDryRunTest = new NoisyDryRunTest();
        OptionSetter setter = new OptionSetter(noisyDryRunTest);
        setter.setOptionValue("cmdfile", mFile.getAbsolutePath());
        noisyDryRunTest.run(mTestInfo, mMockListener);
        verifyMocks();
    }

    @Test
    public void testRun_invalidCmdLine() throws Exception {
        FileUtil.writeToFile("tf/fake\n" + "invalid --option value2", mFile);
        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseFile", 1);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        mMockListener.testRunStarted(
                "com.android.tradefed.testtype.NoisyDryRunTest_parseCommands", 2);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testStarted(Mockito.any());
        mMockListener.testFailed(Mockito.any(), (String) Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        NoisyDryRunTest noisyDryRunTest = new NoisyDryRunTest();
        OptionSetter setter = new OptionSetter(noisyDryRunTest);
        setter.setOptionValue("cmdfile", mFile.getAbsolutePath());
        noisyDryRunTest.run(mTestInfo, mMockListener);
        verifyMocks();
    }

    @Test
    public void testCheckFileWithTimeout() throws Exception {
        NoisyDryRunTest noisyDryRunTest =
                new NoisyDryRunTest() {
                    long mCurrentTime = 0;

                    @Override
                    void sleep() {}

                    @Override
                    long currentTimeMillis() {
                        mCurrentTime += 5 * 1000;
                        return mCurrentTime;
                    }
                };
        OptionSetter setter = new OptionSetter(noisyDryRunTest);
        setter.setOptionValue("timeout", "7000");
        noisyDryRunTest.checkFileWithTimeout(mFile);
    }

    @Test
    public void testCheckFileWithTimeout_missingFile() throws Exception {
        NoisyDryRunTest noisyDryRunTest =
                new NoisyDryRunTest() {
                    long mCurrentTime = 0;

                    @Override
                    void sleep() {}

                    @Override
                    long currentTimeMillis() {
                        mCurrentTime += 5 * 1000;
                        return mCurrentTime;
                    }
                };
        OptionSetter setter = new OptionSetter(noisyDryRunTest);
        setter.setOptionValue("timeout", "100");
        File missingFile = new File("missing_file");
        try {
            noisyDryRunTest.checkFileWithTimeout(missingFile);
            fail("Should have thrown IOException");
        } catch (IOException e) {
            assertEquals("Can not read " + missingFile.getAbsoluteFile() + ".", e.getMessage());
            assertTrue(true);
        }
    }

    @Test
    public void testCheckFileWithTimeout_delayFile() throws Exception {
        FileUtil.deleteFile(mFile);
        NoisyDryRunTest noisyDryRunTest =
                new NoisyDryRunTest() {
                    long mCurrentTime = 0;

                    @Override
                    void sleep() {}

                    @Override
                    long currentTimeMillis() {
                        mCurrentTime += 5 * 1000;
                        if (mCurrentTime > 10 * 1000) {
                            try {
                                mFile.createNewFile();
                            } catch (IOException e) {
                            }
                        }
                        return mCurrentTime;
                    }
                };
        OptionSetter setter = new OptionSetter(noisyDryRunTest);
        setter.setOptionValue("timeout", "100000");
        noisyDryRunTest.checkFileWithTimeout(mFile);
    }

    @Test
    public void testLoading_sandboxed() throws Exception {
        FileUtil.writeToFile("tf/fake\n" + "tf/fake --use-sandbox", mFile);

        mMockRunUtil.unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_SERVER_CONFIG_VARIABLE);
        mMockRunUtil.unsetEnvVariable(AutomatedReporters.PROTO_REPORTING_PORT);
        mMockRunUtil.setEnvVariable(
                Mockito.eq(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE), Mockito.any());
        mMockRunUtil.setEnvVariablePriority(EnvPriority.SET);
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.endsWith("/java"),
                        Mockito.contains("-Djava.io.tmpdir="),
                        Mockito.eq("-cp"),
                        Mockito.any(),
                        Mockito.eq(SandboxConfigDump.class.getCanonicalName()),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq("tf/fake"),
                        Mockito.eq("--use-sandbox")))
                .thenAnswer(
                        invocation -> {
                            // Fake the command dump to avoid possible timeouts
                            String path = (String) invocation.getArguments()[7];
                            FileUtil.writeToFile(
                                    "<configuration></configuration>", new File(path), false);
                            return result;
                        });

        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseFile", 1);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        mMockListener.testRunStarted(
                "com.android.tradefed.testtype.NoisyDryRunTest_parseCommands", 2);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        NoisyDryRunTest noisyDryRunTest =
                new NoisyDryRunTest() {
                    @Override
                    IRunUtil createRunUtil() {
                        return mMockRunUtil;
                    }
                };
        OptionSetter setter = new OptionSetter(noisyDryRunTest);
        setter.setOptionValue("cmdfile", mFile.getAbsolutePath());
        noisyDryRunTest.run(mTestInfo, mMockListener);
        verifyMocks();
        verify(mMockRunUtil, times(2)).unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE);
    }

    /** Test that we fail the dry run if we fail the sandbox non_versioned loading. */
    @Test
    public void testLoading_sandboxed_failed() throws Exception {
        FileUtil.writeToFile("tf/fake\n" + "tf/fake --use-sandbox", mFile);

        mMockRunUtil.unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_SERVER_CONFIG_VARIABLE);
        mMockRunUtil.unsetEnvVariable(AutomatedReporters.PROTO_REPORTING_PORT);
        mMockRunUtil.setEnvVariable(
                Mockito.eq(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE), Mockito.any());
        mMockRunUtil.setEnvVariablePriority(EnvPriority.SET);
        CommandResult result = new CommandResult(CommandStatus.FAILED);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.endsWith("/java"),
                        Mockito.contains("-Djava.io.tmpdir="),
                        Mockito.eq("-cp"),
                        Mockito.any(),
                        Mockito.eq(SandboxConfigDump.class.getCanonicalName()),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq("tf/fake"),
                        Mockito.eq("--use-sandbox")))
                .thenReturn(result);
        result.setStderr("Failed to dump.");

        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseFile", 1);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        mMockListener.testRunStarted(
                "com.android.tradefed.testtype.NoisyDryRunTest_parseCommands", 2);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testStarted(Mockito.any());
        mMockListener.testFailed(
                Mockito.any(),
                Mockito.contains("Failed to parse command line: tf/fake --use-sandbox"));
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        NoisyDryRunTest noisyDryRunTest =
                new NoisyDryRunTest() {
                    @Override
                    IRunUtil createRunUtil() {
                        return mMockRunUtil;
                    }
                };
        OptionSetter setter = new OptionSetter(noisyDryRunTest);
        setter.setOptionValue("cmdfile", mFile.getAbsolutePath());
        noisyDryRunTest.run(mTestInfo, mMockListener);
        verifyMocks();
        verify(mMockRunUtil, times(2)).unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE);
    }

    @Test
    public void testRun_withDelegation() throws Exception {
        FileUtil.writeToFile("tf/fake --delegated-tf .\n" + "tf/fake", mFile);
        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseFile", 1);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        mMockListener.testRunStarted(
                "com.android.tradefed.testtype.NoisyDryRunTest_parseCommands", 2);
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testStarted(Mockito.any());
        mMockListener.testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        mMockListener.testRunEnded(Mockito.eq(0L), Mockito.<HashMap<String, Metric>>any());

        NoisyDryRunTest noisyDryRunTest = new NoisyDryRunTest();
        OptionSetter setter = new OptionSetter(noisyDryRunTest);
        setter.setOptionValue("cmdfile", mFile.getAbsolutePath());
        noisyDryRunTest.run(mTestInfo, mMockListener);
        verifyMocks();
    }

    private void verifyMocks() {}
}
