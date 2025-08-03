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
package com.android.tradefed.sandbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.config.proxy.AutomatedReporters;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.IRunUtil.EnvPriority;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;

/** Unit tests for {@link com.android.tradefed.sandbox.TradefedSandbox}. */
@RunWith(JUnit4.class)
public class TradefedSandboxTest {
    private static final String TF_JAR_DIR = "TF_JAR_DIR";
    private String mCachedProperty;
    private File mTmpFolder;

    private TradefedSandbox mSandbox;
    private IInvocationContext mMockContext;

    @Mock ITestInvocationListener mMockListener;
    @Mock IConfiguration mMockConfig;
    @Mock IRunUtil mMockRunUtil;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mSandbox =
                new TradefedSandbox() {
                    @Override
                    IRunUtil createRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected String getJava() {
                        return "jdk/java";
                    }
                };

        doReturn(new SandboxOptions())
                .when(mMockConfig)
                .getConfigurationObject(Configuration.SANBOX_OPTIONS_TYPE_NAME);
        doReturn(new ConfigurationDescriptor()).when(mMockConfig).getConfigurationDescription();

        mMockContext = new InvocationContext();
        mMockContext.setConfigurationDescriptor(new ConfigurationDescriptor());

        mTmpFolder = FileUtil.createTempDir("tmp-tf-jar-dir");

        if (System.getProperty(TF_JAR_DIR) != null) {
            mCachedProperty = System.getProperty(TF_JAR_DIR);
        }
        System.setProperty(TF_JAR_DIR, mTmpFolder.getAbsolutePath());
        try {
            GlobalConfiguration.createGlobalConfiguration(new String[] {});
        } catch (IllegalStateException ignore) {
            // ignore the global config re-init
        }
    }

    @After
    public void tearDown() {
        if (mCachedProperty != null) {
            System.setProperty(TF_JAR_DIR, mCachedProperty);
        }
        FileUtil.recursiveDelete(mTmpFolder);
        mSandbox.tearDown();
    }

    /**
     * Test a case where the {@link
     * com.android.tradefed.sandbox.TradefedSandbox#prepareEnvironment(IInvocationContext,
     * IConfiguration, ITestInvocationListener)} succeed and does not have any exception.
     */
    @Test
    public void testPrepareEnvironment() throws Exception {
        stubPrepareConfigurationMethods();
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        doReturn(result)
                .when(mMockRunUtil)
                .runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.endsWith("/java"),
                        Mockito.contains("-Djava.io.tmpdir="),
                        Mockito.eq("-cp"),
                        Mockito.any(),
                        Mockito.eq(SandboxConfigDump.class.getCanonicalName()),
                        Mockito.eq("RUN_CONFIG"),
                        Mockito.any(),
                        Mockito.eq("empty"),
                        Mockito.eq("--arg"),
                        Mockito.eq("1"),
                        Mockito.eq("--use-proto-reporter"));

        Exception res = mSandbox.prepareEnvironment(mMockContext, mMockConfig, mMockListener);

        verify(mMockRunUtil, times(2)).unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE);
        verify(mMockRunUtil, times(2))
                .unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_SERVER_CONFIG_VARIABLE);
        verify(mMockRunUtil, times(2)).unsetEnvVariable(AutomatedReporters.PROTO_REPORTING_PORT);
        verify(mMockRunUtil)
                .setEnvVariable(
                        Mockito.eq(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE), Mockito.any());
        verify(mMockRunUtil, times(2)).setEnvVariablePriority(EnvPriority.SET);
        verify(mMockListener)
                .testLog(
                        Mockito.eq("sandbox-global-config"),
                        Mockito.eq(LogDataType.HARNESS_CONFIG),
                        Mockito.any());
        verifyPrepareConfigurationExpectations();
        assertNull(res);
    }

    /**
     * Test a case where the {@link
     * com.android.tradefed.sandbox.TradefedSandbox#prepareEnvironment(IInvocationContext,
     * IConfiguration, ITestInvocationListener)} fails to dump the configuration, in that case the
     * std err from the dump utility is used for the exception.
     */
    @Test
    public void testPrepareEnvironment_dumpConfigFail() throws Exception {

        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.FAILED);
        result.setStderr("Ouch I failed.");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.endsWith("/java"),
                        Mockito.contains("-Djava.io.tmpdir="),
                        Mockito.eq("-cp"),
                        Mockito.any(),
                        Mockito.eq(SandboxConfigDump.class.getCanonicalName()),
                        Mockito.eq("RUN_CONFIG"),
                        Mockito.any(),
                        Mockito.eq("empty"),
                        Mockito.eq("--arg"),
                        Mockito.eq("1"),
                        Mockito.eq("--use-proto-reporter")))
                .thenReturn(result);
        stubPrepareConfigurationMethods();

        Exception res = mSandbox.prepareEnvironment(mMockContext, mMockConfig, mMockListener);

        verify(mMockRunUtil, times(2)).unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE);
        verify(mMockRunUtil, times(2))
                .unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_SERVER_CONFIG_VARIABLE);
        verify(mMockRunUtil, times(2)).unsetEnvVariable(AutomatedReporters.PROTO_REPORTING_PORT);
        verify(mMockRunUtil)
                .setEnvVariable(
                        Mockito.eq(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE), Mockito.any());
        verify(mMockRunUtil, times(2)).setEnvVariablePriority(EnvPriority.SET);
        verify(mMockListener)
                .testLog(
                        Mockito.eq("sandbox-global-config"),
                        Mockito.eq(LogDataType.HARNESS_CONFIG),
                        Mockito.any());
        verifyPrepareConfigurationExpectations();

        assertNotNull(res);
        assertTrue(res instanceof ConfigurationException);
        assertEquals("Error when dumping the config. stderr: Ouch I failed.", res.getMessage());
    }

    /** Test that the fallback dump config also attempt to parse the config. */
    @Test
    public void testPrepareEnvironment_dumpConfigFail_fallback_fail() throws Exception {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.FAILED);
        result.setStderr("Could not find configuration 'empty'");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.endsWith("/java"),
                        Mockito.contains("-Djava.io.tmpdir="),
                        Mockito.eq("-cp"),
                        Mockito.any(),
                        Mockito.eq(SandboxConfigDump.class.getCanonicalName()),
                        Mockito.eq("RUN_CONFIG"),
                        Mockito.any(),
                        Mockito.eq("empty"),
                        Mockito.eq("--arg"),
                        Mockito.eq("1"),
                        Mockito.eq("--use-proto-reporter")))
                .thenReturn(result);
        stubPrepareConfigurationMethods();

        Exception res = mSandbox.prepareEnvironment(mMockContext, mMockConfig, mMockListener);

        verify(mMockRunUtil, times(2)).unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE);
        verify(mMockRunUtil, times(2))
                .unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_SERVER_CONFIG_VARIABLE);
        verify(mMockRunUtil, times(2)).unsetEnvVariable(AutomatedReporters.PROTO_REPORTING_PORT);
        verify(mMockRunUtil)
                .setEnvVariable(
                        Mockito.eq(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE), Mockito.any());
        verify(mMockRunUtil, times(2)).setEnvVariablePriority(EnvPriority.SET);
        verify(mMockListener)
                .testLog(
                        Mockito.eq("sandbox-global-config"),
                        Mockito.eq(LogDataType.HARNESS_CONFIG),
                        Mockito.any());
        verifyPrepareConfigurationExpectations();

        assertNotNull(res);
        assertTrue(res instanceof ConfigurationException);
        assertEquals(
                "Error when dumping the config. stderr: Could not find configuration 'empty'",
                res.getMessage());
    }

    /**
     * Test a case where the {@link
     * com.android.tradefed.sandbox.TradefedSandbox#prepareEnvironment(IInvocationContext,
     * IConfiguration, ITestInvocationListener)} throws an exception because TF_JAR_DIR was not set.
     */
    @Test
    public void testPrepareEnvironment_noTfDirJar() throws Exception {
        when(mMockConfig.getCommandLine()).thenReturn("empty --arg 1");
        when(mMockConfig.getCommandOptions()).thenReturn(new CommandOptions());
        System.setProperty(TF_JAR_DIR, "");

        Exception res = mSandbox.prepareEnvironment(mMockContext, mMockConfig, mMockListener);

        verify(mMockRunUtil).unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE);
        verify(mMockRunUtil)
                .unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_SERVER_CONFIG_VARIABLE);
        verify(mMockRunUtil).unsetEnvVariable(AutomatedReporters.PROTO_REPORTING_PORT);

        assertNotNull(res);
        assertTrue(res instanceof ConfigurationException);
        assertEquals(
                "Could not read TF_JAR_DIR to get current Tradefed instance.", res.getMessage());
    }

    private void verifyPrepareConfigurationExpectations() throws Exception {
        verify(mMockConfig, times(2)).getCommandLine();
        verify(mMockConfig, Mockito.atLeast(1)).getCommandOptions();
    }

    private void stubPrepareConfigurationMethods() throws Exception {
        when(mMockConfig.getCommandLine()).thenReturn("empty --arg 1");
        when(mMockConfig.getCommandOptions()).thenReturn(new CommandOptions());
    }

    /**
     * Test that when the sandbox option received a TF location, it uses it instead of the current
     * one.
     */
    @Test
    public void testSandboxOptions() throws Exception {
        File tmpDir = FileUtil.createTempDir("tmp-sandbox-dir");
        try {
            mMockConfig = new Configuration("NAME", "DESC");
            mMockConfig.setCommandLine(new String[] {"empty", "--arg", "1"});
            SandboxOptions options = new SandboxOptions();
            OptionSetter setter = new OptionSetter(options);
            setter.setOptionValue("sandbox:tf-location", tmpDir.getAbsolutePath());
            mMockConfig.setConfigurationObject(Configuration.SANBOX_OPTIONS_TYPE_NAME, options);

            File res =
                    mSandbox.getTradefedSandboxEnvironment(
                            mMockContext,
                            mMockConfig,
                            mMockListener,
                            new String[] {"empty", "--arg", "1"});
            assertEquals(tmpDir, res);
        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }

    /**
     * Test that when the sandbox option received a TF location and a build id at the same time that
     * it is rejected, because that combination is not supported.
     */
    @Test
    public void testSandboxOptions_exclusion() throws Exception {
        File tmpDir = FileUtil.createTempDir("tmp-sandbox-dir");
        try {
            mMockConfig = new Configuration("NAME", "DESC");
            mMockConfig.setCommandLine(new String[] {"empty", "--arg", "1"});
            SandboxOptions options = new SandboxOptions();
            OptionSetter setter = new OptionSetter(options);
            setter.setOptionValue("sandbox:tf-location", tmpDir.getAbsolutePath());
            setter.setOptionValue("sandbox:sandbox-build-id", "9999");
            mMockConfig.setConfigurationObject(Configuration.SANBOX_OPTIONS_TYPE_NAME, options);

            try {
                mSandbox.getTradefedSandboxEnvironment(
                        mMockContext,
                        mMockConfig,
                        mMockListener,
                        new String[] {"empty", "--arg", "1"});
                fail("Should have thrown an exception.");
            } catch (ConfigurationException expected) {
                assertEquals(
                        "Sandbox options tf-location and sandbox-build-id cannot be set at "
                                + "the same time",
                        expected.getMessage());
            }
        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }
}
