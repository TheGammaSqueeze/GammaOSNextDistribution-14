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
package com.android.tradefed.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.StubBuildProvider;
import com.android.tradefed.config.proxy.AutomatedReporters;
import com.android.tradefed.sandbox.ISandbox;
import com.android.tradefed.sandbox.SandboxConfigDump;
import com.android.tradefed.sandbox.SandboxConfigDump.DumpCmd;
import com.android.tradefed.sandbox.SandboxConfigurationException;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.IRunUtil.EnvPriority;
import com.android.tradefed.util.keystore.StubKeyStoreClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/** Unit tests for {@link SandboxConfigurationFactory}. */
@RunWith(JUnit4.class)
public class SandboxConfigurationFactoryTest {

    private SandboxConfigurationFactory mFactory;
    private File mConfig;
    @Mock ISandbox mFakeSandbox;
    @Mock IRunUtil mMockRunUtil;

    @Before
    public void setUp() throws IOException, ConfigurationException {
        MockitoAnnotations.initMocks(this);

        mFactory = SandboxConfigurationFactory.getInstance();
        mConfig = FileUtil.createTempFile("sandbox-config-test", ".xml");

        try {
            GlobalConfiguration.createGlobalConfiguration(new String[] {});
        } catch (IllegalStateException ignore) {
            // ignore the global config re-init
        }
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mConfig);
    }

    private void expectDumpCmd(CommandResult res) {
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.endsWith("/java"),
                        Mockito.contains("-Djava.io.tmpdir="),
                        Mockito.eq("-cp"),
                        Mockito.any(),
                        Mockito.eq(SandboxConfigDump.class.getCanonicalName()),
                        Mockito.eq(DumpCmd.NON_VERSIONED_CONFIG.toString()),
                        Mockito.any(),
                        Mockito.eq(mConfig.getAbsolutePath())))
                .thenAnswer(
                        invocation -> {
                            String resFile = (String) invocation.getArguments()[7];
                            FileUtil.writeToFile(
                                    "<configuration><test class=\"com.android.tradefed.test"
                                            + "type.StubTest\" /></configuration>",
                                    new File(resFile));
                            return res;
                        });
    }

    /**
     * Test that creating a configuration using a sandbox properly create a {@link IConfiguration}.
     */
    @Test
    public void testCreateConfigurationFromArgs() throws ConfigurationException {
        String[] args = new String[] {mConfig.getAbsolutePath()};

        CommandResult results = new CommandResult();
        results.setStatus(CommandStatus.SUCCESS);
        expectDumpCmd(results);

        IConfiguration config =
                mFactory.createConfigurationFromArgs(
                        args, new StubKeyStoreClient(), mFakeSandbox, mMockRunUtil);

        verify(mMockRunUtil, times(2)).unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE);
        verify(mMockRunUtil)
                .unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_SERVER_CONFIG_VARIABLE);
        verify(mMockRunUtil).unsetEnvVariable(AutomatedReporters.PROTO_REPORTING_PORT);
        verify(mMockRunUtil)
                .setEnvVariable(
                        Mockito.eq(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE), Mockito.any());
        verify(mMockRunUtil).setEnvVariablePriority(EnvPriority.SET);
        assertNotNull(config.getConfigurationObject(Configuration.SANDBOX_TYPE_NAME));
        assertEquals(mFakeSandbox, config.getConfigurationObject(Configuration.SANDBOX_TYPE_NAME));
    }

    /** Test that when the dump config failed, we throw a SandboxConfigurationException. */
    @Test
    public void testCreateConfigurationFromArgs_fail() throws Exception {
        String[] args = new String[] {mConfig.getAbsolutePath()};

        CommandResult results = new CommandResult();
        results.setStatus(CommandStatus.FAILED);
        results.setStderr("I failed");
        expectDumpCmd(results);
        // Thin launcher is attempted, and in this case fails, so original exception is thrown.
        when(mFakeSandbox.createThinLauncherConfig(
                        Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any()))
                .thenReturn(null);

        try {
            mFactory.createConfigurationFromArgs(
                    args, new StubKeyStoreClient(), mFakeSandbox, mMockRunUtil);
            fail("Should have thrown an exception.");
        } catch (SandboxConfigurationException expected) {
            // expected
        }

        verify(mMockRunUtil, times(2)).unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE);
        verify(mMockRunUtil)
                .unsetEnvVariable(GlobalConfiguration.GLOBAL_CONFIG_SERVER_CONFIG_VARIABLE);
        verify(mMockRunUtil).unsetEnvVariable(AutomatedReporters.PROTO_REPORTING_PORT);
        verify(mMockRunUtil)
                .setEnvVariable(
                        Mockito.eq(GlobalConfiguration.GLOBAL_CONFIG_VARIABLE), Mockito.any());
        verify(mMockRunUtil).setEnvVariablePriority(EnvPriority.SET);
        // in case of failure, tearDown is called right away for cleaning up
        verify(mFakeSandbox).tearDown();
    }

    @Test
    public void testCreateConfiguration_runConfig() throws Exception {
        IConfiguration originalConfig = new Configuration("name", "description");
        StubBuildProvider provider = new StubBuildProvider();
        OptionSetter providerSetter = new OptionSetter(provider);
        providerSetter.setOptionValue("branch", "test-branch");
        originalConfig.setBuildProvider(provider);

        try (PrintWriter pw = new PrintWriter(mConfig)) {
            originalConfig.dumpXml(pw);
        }

        String[] args = new String[] {mConfig.getAbsolutePath()};
        IConfiguration config = mFactory.createConfigurationFromArgs(args, DumpCmd.RUN_CONFIG);
        // Test that object not part of the versioning still receive their options
        assertEquals("test-branch", config.getBuildProvider().getBuild().getBuildBranch());
    }
}
