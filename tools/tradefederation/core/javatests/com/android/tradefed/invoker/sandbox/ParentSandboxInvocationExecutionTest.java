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
package com.android.tradefed.invoker.sandbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.build.StubBuildProvider;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.sandbox.TradefedSandbox;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link ParentSandboxInvocationExecution}. */
@RunWith(JUnit4.class)
public class ParentSandboxInvocationExecutionTest {

    private ParentSandboxInvocationExecution mParentSandbox;
    private IConfiguration mConfig;
    private TestInformation mTestInfo;
    private IInvocationContext mContext;
    private IConfigurationFactory mMockFactory;
    private ITargetPreparer mMockLabPreparer;
    private ITestDevice mMockDevice;
    private ITestLogger mMockLogger;
    private IRunUtil mMockRunUtil;

    @Before
    public void setUp() throws Exception {
        mMockFactory = Mockito.mock(IConfigurationFactory.class);
        mMockDevice = Mockito.mock(ITestDevice.class);
        mMockLogger = Mockito.mock(ITestLogger.class);
        mMockRunUtil = Mockito.mock(IRunUtil.class);
        mMockLabPreparer = Mockito.mock(ITargetPreparer.class);

        mParentSandbox =
                new ParentSandboxInvocationExecution() {
                    @Override
                    protected IConfigurationFactory getFactory() {
                        return mMockFactory;
                    }

                    @Override
                    protected String getAdbVersion() {
                        return "0";
                    }

                    @Override
                    protected boolean prepareAndRunSandbox(
                            TestInformation info,
                            IConfiguration config,
                            ITestInvocationListener listener)
                            throws Throwable {
                        return false;
                    }

                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected void logHostAdb(IConfiguration config, ITestLogger logger) {
                        // Inop for testing
                    }
                };
        mContext = new InvocationContext();
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mContext.addDeviceBuildInfo(ConfigurationDef.DEFAULT_DEVICE_NAME, new BuildInfo());
        TestDeviceOptions options = mock(TestDeviceOptions.class);
        when(options.shouldSkipTearDown()).thenReturn(false);
        when(mMockDevice.getOptions()).thenReturn(options);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(mContext).build();
        mConfig = new Configuration("test", "test");
        mConfig.setCommandLine(new String[] {"empty"});
        mConfig.setConfigurationObject(Configuration.SANDBOX_TYPE_NAME, new TradefedSandbox());
    }

    @Test
    public void testDefaultSkipSetup_tearDown() throws Throwable {
        mParentSandbox.doSetup(mTestInfo, mConfig, null);
        mParentSandbox.doTeardown(mTestInfo, mConfig, null, null);
        mParentSandbox.doCleanUp(mContext, mConfig, null);

        verify(mMockFactory, times(0)).createConfigurationFromArgs(Mockito.any());
        verify(mMockDevice, times(2)).getIDevice();
    }

    /**
     * If the context already contains BuildInfo we are in sandbox-test-mode and should not download
     * again.
     */
    @Test
    public void testParentSandbox_testMode() throws Throwable {
        IBuildProvider stubProvider = new StubBuildProvider();
        OptionSetter setter = new OptionSetter(stubProvider);
        setter.setOptionValue("throw-build-error", "true");
        mConfig.getDeviceConfig().get(0).addSpecificConfig(stubProvider);
        TestInformation testInfo =
                TestInformation.newBuilder().setInvocationContext(mContext).build();
        assertTrue(mParentSandbox.fetchBuild(testInfo, mConfig, null, null));
    }

    /**
     * Test that in regular sandbox mode, the fetchBuild is called as always in the parent sandbox.
     */
    @Test
    public void testParentSandbox_NotTestMode() throws Throwable {
        IBuildProvider stubProvider = new StubBuildProvider();
        OptionSetter setter = new OptionSetter(stubProvider);
        setter.setOptionValue("throw-build-error", "true");
        mConfig.getDeviceConfig().get(0).addSpecificConfig(stubProvider);

        mContext = new InvocationContext();
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        TestInformation testInfo =
                TestInformation.newBuilder().setInvocationContext(mContext).build();
        try {
            mParentSandbox.fetchBuild(testInfo, mConfig, null, null);
            fail("Should have thrown an exception.");
        } catch (BuildRetrievalError expected) {
            assertEquals("stub failed to get build.", expected.getMessage());
        }
    }

    /** Basic test to ensure lab preparers are run in the sandbox parent process */
    @Test
    public void testParentConfig_labPreparer() throws Throwable {
        mConfig.setLabPreparer(mMockLabPreparer);

        mParentSandbox.doSetup(mTestInfo, mConfig, null);
        mParentSandbox.doTeardown(mTestInfo, mConfig, mMockLogger, null);
        mParentSandbox.doCleanUp(mContext, mConfig, null);

        verify(mMockLabPreparer, times(1)).setUp(Mockito.any());
        verify(mMockLabPreparer, times(1)).tearDown(Mockito.any(), Mockito.any());
        verify(mMockDevice, times(2)).getIDevice();
    }
}
