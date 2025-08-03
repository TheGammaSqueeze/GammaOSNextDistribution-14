/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.command;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.MockDeviceManager;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.error.ErrorIdentifier;
import com.android.tradefed.util.RunInterruptedException;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.keystore.IKeyStoreClient;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/** Longer running test for {@link CommandScheduler} */
@RunWith(JUnit4.class)
public class CommandSchedulerFuncTest {

    private static final long WAIT_TIMEOUT_MS = 30 * 1000;
    /** the {@link CommandScheduler} under test, with all dependencies mocked out */
    private CommandScheduler mCommandScheduler;

    private MeasuredInvocation mMockTestInvoker;
    private MockDeviceManager mMockDeviceManager;
    private List<IDeviceConfiguration> mMockDeviceConfig;
    @Mock IConfiguration mSlowConfig;
    @Mock IConfiguration mFastConfig;
    @Mock IConfigurationFactory mMockConfigFactory;
    @Mock ILeveledLogOutput mMockLogLevel;
    @Mock ILogSaver mMockLogSaver;
    private CommandOptions mCommandOptions;
    private DeviceSelectionOptions mDeviceOptions;
    private boolean mInterruptible = false;
    private IDeviceConfiguration mMockConfig;

    @BeforeClass
    public static void setUpClass() throws ConfigurationException {
        try {
            GlobalConfiguration.createGlobalConfiguration(new String[] {"empty"});
        } catch (IllegalStateException e) {
            // ignore
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDeviceOptions = new DeviceSelectionOptions();
        mMockDeviceConfig = new ArrayList<IDeviceConfiguration>();
        mMockConfig = new DeviceConfigurationHolder("device");
        mMockConfig.addSpecificConfig(mDeviceOptions);
        mMockConfig.addSpecificConfig(new TestDeviceOptions());
        mMockDeviceConfig.add(mMockConfig);

        mInterruptible = false;

        mMockDeviceManager = new MockDeviceManager(1);
        mMockTestInvoker = new MeasuredInvocation();

        mCommandOptions = new CommandOptions();
        mCommandOptions.setLoopMode(true);
        mCommandOptions.setMinLoopTime(0);
        when(mSlowConfig.getCommandOptions()).thenReturn(mCommandOptions);
        when(mSlowConfig.getTestInvocationListeners())
                .thenReturn(new ArrayList<ITestInvocationListener>());
        when(mFastConfig.getCommandOptions()).thenReturn(mCommandOptions);
        when(mFastConfig.getTestInvocationListeners())
                .thenReturn(new ArrayList<ITestInvocationListener>());
        when(mSlowConfig.getDeviceRequirements()).thenReturn(new DeviceSelectionOptions());
        when(mFastConfig.getDeviceRequirements()).thenReturn(new DeviceSelectionOptions());
        when(mSlowConfig.getDeviceConfig()).thenReturn(mMockDeviceConfig);
        when(mSlowConfig.getDeviceConfigByName(Mockito.eq("device"))).thenReturn(mMockConfig);
        when(mSlowConfig.getCommandLine()).thenReturn("");
        when(mFastConfig.getDeviceConfigByName(Mockito.eq("device"))).thenReturn(mMockConfig);
        when(mFastConfig.getDeviceConfig()).thenReturn(mMockDeviceConfig);
        when(mFastConfig.getCommandLine()).thenReturn("");
        when(mSlowConfig.getConfigurationDescription()).thenReturn(new ConfigurationDescriptor());
        when(mFastConfig.getConfigurationDescription()).thenReturn(new ConfigurationDescriptor());
        when(mFastConfig.getTests()).thenReturn(new ArrayList<>());
        when(mSlowConfig.getTests()).thenReturn(new ArrayList<>());
        when(mFastConfig.getLogSaver()).thenReturn(mMockLogSaver);
        when(mSlowConfig.getLogSaver()).thenReturn(mMockLogSaver);
        when(mFastConfig.getLogOutput()).thenReturn(mMockLogLevel);
        when(mSlowConfig.getLogOutput()).thenReturn(mMockLogLevel);

        mCommandScheduler =
                new CommandScheduler() {
                    @Override
                    ITestInvocation createRunInstance() {
                        return mMockTestInvoker;
                    }

                    @Override
                    protected IDeviceManager getDeviceManager() {
                        return mMockDeviceManager;
                    }

                    @Override
                    protected IConfigurationFactory getConfigFactory() {
                        if (mInterruptible) {
                            // simulate the invocation becoming interruptible
                            RunUtil.getDefault().allowInterrupt(true);
                        }
                        return mMockConfigFactory;
                    }

                    @Override
                    protected void initLogging() {
                        // ignore
                    }

                    @Override
                    protected void cleanUp() {
                        // ignore
                    }
                };
    }

    @After
    public void tearDown() throws Exception {
        if (mCommandScheduler != null) {
            mCommandScheduler.shutdownOnEmpty();
        }
    }

    /**
     * Test config priority scheduling. Verifies that configs are prioritized according to their
     * total run time.
     *
     * <p>This test continually executes two configs in loop mode. One config executes quickly (ie
     * "fast config"). The other config (ie "slow config") takes ~ 2 * fast config time to execute.
     *
     * <p>The run is stopped after the slow config is executed 20 times. At the end of the test, it
     * is expected that "fast config" has executed roughly twice as much as the "slow config".
     */
    @Test
    public void testRun_scheduling() throws Exception {
        String[] fastConfigArgs = new String[] {"fastConfig"};
        String[] slowConfigArgs = new String[] {"slowConfig"};
        List<String> nullArg = null;
        when(mMockConfigFactory.createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(fastConfigArgs),
                        Mockito.eq(nullArg),
                        (IKeyStoreClient) Mockito.any()))
                .thenReturn(mFastConfig);
        when(mMockConfigFactory.createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(slowConfigArgs),
                        Mockito.eq(nullArg),
                        (IKeyStoreClient) Mockito.any()))
                .thenReturn(mSlowConfig);

        mCommandScheduler.start();
        mCommandScheduler.addCommand(fastConfigArgs);
        mCommandScheduler.addCommand(slowConfigArgs);

        synchronized (mMockTestInvoker) {
            mMockTestInvoker.wait(WAIT_TIMEOUT_MS);
        }
        mCommandScheduler.shutdown();
        mCommandScheduler.join(WAIT_TIMEOUT_MS);

        CLog.i(
                "fast times %d slow times %d",
                mMockTestInvoker.mFastCount, mMockTestInvoker.mSlowCount);
        Truth.assertThat(mMockTestInvoker.mFastCount).isGreaterThan(mMockTestInvoker.mSlowCount);
        assertFalse(mMockTestInvoker.runInterrupted);
    }

    private class MeasuredInvocation implements ITestInvocation {
        private final Object mSlowCountLock = new Object();
        int mSlowCount = 0;
        private final Object mFastCountLock = new Object();
        int mFastCount = 0;
        int mSlowCountLimit = 40;
        public boolean runInterrupted = false;
        public boolean printedStop = false;
        public long mSleepTimMs = 200L;

        @Override
        public void invoke(
                IInvocationContext metadata,
                IConfiguration config,
                IRescheduler rescheduler,
                ITestInvocationListener... listeners)
                throws DeviceNotAvailableException {
            try {
                if (mInterruptible) {
                    // simulate the invocation becoming interruptible
                    RunUtil.getDefault().allowInterrupt(true);
                }
                if (config.equals(mSlowConfig)) {
                    // sleep for 2 * fast config time
                    RunUtil.getDefault().sleep(mSleepTimMs);
                    synchronized (mSlowCountLock) {
                        mSlowCount++;
                    }
                    if (mSlowCount >= mSlowCountLimit) {
                        synchronized (this) {
                            notify();
                        }
                    }
                } else if (config.equals(mFastConfig)) {
                    RunUtil.getDefault().sleep(100);
                    synchronized (mFastCountLock) {
                        mFastCount++;
                    }
                } else {
                    throw new IllegalArgumentException("unknown config");
                }
            } catch (RunInterruptedException e) {
                CLog.e(e);
                // Yield right away if an exception occur due to an interrupt.
                runInterrupted = true;
                synchronized (this) {
                    notify();
                }
            }
        }

        @Override
        public void notifyInvocationForceStopped(String message, ErrorIdentifier errorId) {
            runInterrupted = true;
            printedStop = true;
            CLog.d("#notifyInvocationForceStopped");
        }

        @Override
        public void notifyInvocationStopped(String message) {
            CLog.d("#notifyInvocationStopped");
        }
    }

    /** Test that the Invocation is not interruptible even when Battery is low. */
    @Test
    public void testBatteryLowLevel() throws Throwable {
        ITestDevice mockDevice = mock(ITestDevice.class);
        when(mockDevice.getSerialNumber()).thenReturn("serial");
        IDevice mockIDevice = new StubDevice("serial");
        when(mockDevice.getIDevice()).thenReturn(mockIDevice);
        when(mockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);

        TestDeviceOptions testDeviceOptions = new TestDeviceOptions();
        testDeviceOptions.setCutoffBattery(20);
        mMockConfig.addSpecificConfig(testDeviceOptions);
        assertTrue(testDeviceOptions.getCutoffBattery() == 20);
        when(mSlowConfig.getDeviceOptions()).thenReturn(testDeviceOptions);

        mMockDeviceManager.clearAllDevices();
        mMockDeviceManager.addDevice(mockDevice);

        String[] slowConfigArgs = new String[] {"slowConfig"};
        List<String> nullArg = null;
        when(mMockConfigFactory.createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(slowConfigArgs),
                        Mockito.eq(nullArg),
                        (IKeyStoreClient) Mockito.any()))
                .thenReturn(mSlowConfig);

        mCommandScheduler.start();
        mCommandScheduler.addCommand(slowConfigArgs);

        synchronized (mMockTestInvoker) {
            mMockTestInvoker.wait(WAIT_TIMEOUT_MS);
        }

        mCommandScheduler.shutdown();
        mCommandScheduler.join(WAIT_TIMEOUT_MS);
        assertFalse(mMockTestInvoker.runInterrupted);
        // Notify was not sent to the invocation because it was not forced shutdown.
        assertFalse(mMockTestInvoker.printedStop);
    }

    /** Test that the Invocation is interruptible when Battery is low. */
    @Test
    public void testBatteryLowLevel_interruptible() throws Throwable {
        ITestDevice mockDevice = mock(ITestDevice.class);
        when(mockDevice.getSerialNumber()).thenReturn("serial");
        IDevice mockIDevice = new StubDevice("serial");
        when(mockDevice.getBattery()).thenReturn(10);
        when(mockDevice.getIDevice()).thenReturn(mockIDevice);
        when(mockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);

        TestDeviceOptions testDeviceOptions = new TestDeviceOptions();
        testDeviceOptions.setCutoffBattery(20);
        mMockConfig.addSpecificConfig(testDeviceOptions);
        when(mSlowConfig.getDeviceOptions()).thenReturn(testDeviceOptions);

        mMockDeviceManager.clearAllDevices();
        mMockDeviceManager.addDevice(mockDevice);

        String[] slowConfigArgs = new String[] {"slowConfig"};
        List<String> nullArg = null;
        when(mMockConfigFactory.createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(slowConfigArgs),
                        Mockito.eq(nullArg),
                        (IKeyStoreClient) Mockito.any()))
                .thenReturn(mSlowConfig);

        mCommandScheduler.start();
        mInterruptible = true;
        mCommandScheduler.addCommand(slowConfigArgs);

        synchronized (mMockTestInvoker) {
            mMockTestInvoker.wait(WAIT_TIMEOUT_MS);
        }

        mCommandScheduler.shutdown();
        mCommandScheduler.join(WAIT_TIMEOUT_MS);
        assertTrue(mMockTestInvoker.runInterrupted);
    }

    /**
     * Test that the Invocation is interrupted by the shutdownHard and finishes with an
     * interruption. {@link CommandScheduler#shutdownHard()}
     */
    @Test
    public void testShutdown_interruptible() throws Throwable {
        String[] slowConfigArgs = new String[] {"slowConfig"};
        mMockTestInvoker.mSleepTimMs = 10000L; // Sleep much longer than expected interrupt
        List<String> nullArg = null;
        when(mMockConfigFactory.createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(slowConfigArgs),
                        Mockito.eq(nullArg),
                        (IKeyStoreClient) Mockito.any()))
                .thenReturn(mSlowConfig);

        mCommandScheduler.start();
        mInterruptible = true;
        mCommandScheduler.addCommand(slowConfigArgs);

        Thread test =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                RunUtil.getDefault().sleep(500);
                                mCommandScheduler.shutdownHard();
                            }
                        });
        test.setName("CommandSchedulerFuncTest#testShutdown_interruptible");
        test.start();
        synchronized (mMockTestInvoker) {
            mMockTestInvoker.wait(WAIT_TIMEOUT_MS);
        }
        test.join();
        mCommandScheduler.join(WAIT_TIMEOUT_MS);
        // Was interrupted during execution.
        assertTrue(mMockTestInvoker.runInterrupted);
        // Notify was sent to the invocation
        assertTrue(mMockTestInvoker.printedStop);
    }

    /**
     * Test that the Invocation is not interrupted by shutdownHard. Invocation terminate then
     * scheduler finishes. {@link CommandScheduler#shutdownHard()}
     */
    @Test
    public void testShutdown_notInterruptible() throws Throwable {
        final LongInvocation li = new LongInvocation(5);
        mCommandOptions.setLoopMode(false);
        mCommandScheduler =
                new CommandScheduler() {
                    @Override
                    ITestInvocation createRunInstance() {
                        return li;
                    }

                    @Override
                    protected IDeviceManager getDeviceManager() {
                        return mMockDeviceManager;
                    }

                    @Override
                    protected IConfigurationFactory getConfigFactory() {
                        if (mInterruptible) {
                            // simulate the invocation becoming interruptible
                            RunUtil.getDefault().allowInterrupt(true);
                        }
                        return mMockConfigFactory;
                    }

                    @Override
                    protected void initLogging() {
                        // ignore
                    }

                    @Override
                    protected void cleanUp() {
                        // ignore
                    }

                    @Override
                    public long getShutdownTimeout() {
                        return 30000;
                    }
                };
        String[] slowConfigArgs = new String[] {"slowConfig"};
        List<String> nullArg = null;
        when(mMockConfigFactory.createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(slowConfigArgs),
                        Mockito.eq(nullArg),
                        (IKeyStoreClient) Mockito.any()))
                .thenReturn(mSlowConfig);

        mCommandScheduler.start();
        mInterruptible = false;
        mCommandScheduler.addCommand(slowConfigArgs);

        Thread shutdownThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                RunUtil.getDefault().sleep(1000);
                                mCommandScheduler.shutdownHard();
                            }
                        });
        shutdownThread.setName("CommandSchedulerFuncTest#testShutdown_notInterruptible");
        shutdownThread.start();
        synchronized (li) {
            // Invocation will finish first because shorter than shutdownHard final timeout
            li.wait(WAIT_TIMEOUT_MS);
        }
        shutdownThread.join();
        mCommandScheduler.join(WAIT_TIMEOUT_MS);
        // Stop but was not interrupted
        assertFalse(mMockTestInvoker.runInterrupted);
        // Notify was not sent to the invocation because it was not interrupted.
        assertFalse(mMockTestInvoker.printedStop);
    }

    private class LongInvocation implements ITestInvocation {
        public boolean runInterrupted = false;
        private int mIteration = 15;

        public LongInvocation(int iteration) {
            mIteration = iteration;
        }

        @Override
        public void invoke(
                IInvocationContext metadata,
                IConfiguration config,
                IRescheduler rescheduler,
                ITestInvocationListener... listeners)
                throws DeviceNotAvailableException {
            try {
                if (mInterruptible) {
                    // simulate the invocation becoming interruptible
                    RunUtil.getDefault().allowInterrupt(true);
                }
                for (int i = 0; i < mIteration; i++) {
                    RunUtil.getDefault().sleep(2000);
                }
                synchronized (this) {
                    notify();
                }
            } catch (RunInterruptedException e) {
                CLog.e(e);
                // Yield right away if an exception occur due to an interrupt.
                runInterrupted = true;
                synchronized (this) {
                    notify();
                }
            }
        }

        @Override
        public void notifyInvocationForceStopped(String message, ErrorIdentifier errorId) {
            runInterrupted = true;
            CLog.d("#notifyInvocationForceStopped");
        }

        @Override
        public void notifyInvocationStopped(String message) {
            CLog.d("#notifyInvocationStopped");
        }
    }

    /**
     * Test that the Invocation is interrupted by {@link CommandScheduler#shutdownHard()} but only
     * after the shutdown timeout is expired because the invocation was uninterruptible so we only
     * allow for so much time before shutting down.
     */
    @Test
    public void testShutdown_notInterruptible_timeout() throws Throwable {
        final LongInvocation li = new LongInvocation(15);
        mCommandOptions.setLoopMode(false);
        mCommandScheduler =
                new CommandScheduler() {
                    @Override
                    ITestInvocation createRunInstance() {
                        return li;
                    }

                    @Override
                    protected IDeviceManager getDeviceManager() {
                        return mMockDeviceManager;
                    }

                    @Override
                    protected IConfigurationFactory getConfigFactory() {
                        if (mInterruptible) {
                            // simulate the invocation becoming interruptible
                            RunUtil.getDefault().allowInterrupt(true);
                        }
                        return mMockConfigFactory;
                    }

                    @Override
                    protected void initLogging() {
                        // ignore
                    }

                    @Override
                    protected void cleanUp() {
                        // ignore
                    }

                    @Override
                    public long getShutdownTimeout() {
                        return 5000;
                    }
                };
        String[] slowConfigArgs = new String[] {"slowConfig"};
        List<String> nullArg = null;
        when(mMockConfigFactory.createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(slowConfigArgs),
                        Mockito.eq(nullArg),
                        (IKeyStoreClient) Mockito.any()))
                .thenReturn(mSlowConfig);

        mCommandScheduler.start();
        mInterruptible = false;
        mCommandScheduler.addCommand(slowConfigArgs);

        Thread shutdownThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                RunUtil.getDefault().sleep(1000);
                                mCommandScheduler.shutdownHard();
                            }
                        });
        shutdownThread.setName("CommandSchedulerFuncTest#testShutdown_notInterruptible_timeout");
        shutdownThread.start();
        synchronized (li) {
            // Setting a timeout longer than the shutdown timeout.
            li.wait(WAIT_TIMEOUT_MS);
        }
        shutdownThread.join();
        mCommandScheduler.join(WAIT_TIMEOUT_MS);
        // Stop and was interrupted by timeout of shutdownHard()
        assertTrue(li.runInterrupted);
    }

    /** Test that if the invocation run time goes over the timeout, it will be forced stopped. */
    @Test
    public void testShutdown_invocation_timeout() throws Throwable {
        final LongInvocation li = new LongInvocation(2);
        mCommandOptions.setLoopMode(false);
        mCommandOptions.setInvocationTimeout(1000L);
        mCommandScheduler =
                new CommandScheduler() {
                    @Override
                    ITestInvocation createRunInstance() {
                        return li;
                    }

                    @Override
                    protected IDeviceManager getDeviceManager() {
                        return mMockDeviceManager;
                    }

                    @Override
                    protected IConfigurationFactory getConfigFactory() {
                        return mMockConfigFactory;
                    }

                    @Override
                    protected void initLogging() {
                        // ignore
                    }

                    @Override
                    protected void cleanUp() {
                        // ignore
                    }
                };
        String[] slowConfigArgs = new String[] {"slowConfig"};
        List<String> nullArg = null;
        when(mMockConfigFactory.createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(slowConfigArgs),
                        Mockito.eq(nullArg),
                        (IKeyStoreClient) Mockito.any()))
                .thenReturn(mSlowConfig);

        mCommandScheduler.start();
        mInterruptible = true;
        mCommandScheduler.addCommand(slowConfigArgs);
        mCommandScheduler.join(mCommandOptions.getInvocationTimeout() * 3);
        // Stop and was interrupted by timeout
        assertTrue(li.runInterrupted);
    }
}
