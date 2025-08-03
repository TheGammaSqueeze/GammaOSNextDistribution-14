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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.command.CommandFileParser.CommandLine;
import com.android.tradefed.command.CommandScheduler.CommandTracker;
import com.android.tradefed.command.CommandScheduler.CommandTrackerIdComparator;
import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.config.proxy.ProxyConfiguration;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.MockDeviceManager;
import com.android.tradefed.device.NoDeviceException;
import com.android.tradefed.device.NullDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.TcpDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.ITestInvocation;
import com.android.tradefed.invoker.ITestInvocation.ExitInformation;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.ILogRegistry.EventType;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.service.TradefedFeatureServer;
import com.android.tradefed.service.management.DeviceManagementGrpcServer;
import com.android.tradefed.service.management.TestInvocationManagementServer;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.Pair;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.keystore.DryRunKeyStore;
import com.android.tradefed.util.keystore.IKeyStoreClient;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link CommandScheduler}. */
@RunWith(JUnit4.class)
public class CommandSchedulerTest {

    private CommandScheduler mScheduler;
    @Mock ITestInvocation mMockInvocation;
    @Mock CommandFileWatcher mMockFileWatcher;
    @Mock ILogSaver mMockLogSaver;
    @Mock ILeveledLogOutput mMockLogOutput;
    private MockDeviceManager mFakeDeviceManager;
    @Mock IConfigurationFactory mMockConfigFactory;
    @Mock IConfiguration mMockConfiguration;
    private CommandOptions mCommandOptions;
    private DeviceSelectionOptions mDeviceOptions;
    private CommandFileParser mCommandFileParser;
    private List<IDeviceConfiguration> mDeviceConfigList;
    private ConfigurationDescriptor mConfigDescriptor;
    @Mock IKeyStoreClient mMockKeyStoreClient;
    private IInvocationContext mContext;
    private boolean mIsFirstInvoke = true; // For testRun_rescheduled()
    @Captor private ArgumentCaptor<HashMap<String, ITestDevice>> devicesCapture;

    class TestableCommandScheduler extends CommandScheduler {

        @Override
        ITestInvocation createRunInstance() {
            return mMockInvocation;
        }

        @Override
        protected IDeviceManager getDeviceManager() {
            return mFakeDeviceManager;
        }

        @Override
        protected IConfigurationFactory getConfigFactory() {
            return mMockConfigFactory;
        }

        @Override
        protected TradefedFeatureServer getFeatureServer() {
            return null;
        }

        @Override
        protected TestInvocationManagementServer getTestInvocationManagementServer() {
            return null;
        }

        @Override
        protected DeviceManagementGrpcServer getDeviceManagementServer() {
            return null;
        }

        @Override
        protected IInvocationContext createInvocationContext() {
            return mContext;
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
        void logEvent(EventType event, Map<String, String> args) {
            // ignore
        }

        @Override
        void checkInvocations() {
            // ignore
        }

        @Override
        CommandFileParser createCommandFileParser() {
            return mCommandFileParser;
        }

        @Override
        protected IKeyStoreClient getKeyStoreClient() {
            return mMockKeyStoreClient;
        }

        @Override
        public synchronized CommandFileWatcher getCommandFileWatcher() {
            return mMockFileWatcher;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockInvocation.getExitInfo()).thenReturn(new ExitInformation());

        mFakeDeviceManager = new MockDeviceManager(0);
        when(mMockConfiguration.getTests()).thenReturn(new ArrayList<>());
        when(mMockConfiguration.getConfigurationObject(ProxyConfiguration.PROXY_CONFIG_TYPE_KEY))
                .thenReturn(null);
        mCommandOptions = new CommandOptions();
        // Avoid any issue related to env. variable.
        mDeviceOptions =
                new DeviceSelectionOptions() {
                    @Override
                    public String fetchEnvironmentVariable(String name) {
                        return null;
                    }
                };
        mDeviceConfigList = new ArrayList<IDeviceConfiguration>();
        mConfigDescriptor = new ConfigurationDescriptor();
        mContext = new InvocationContext();

        mScheduler = new TestableCommandScheduler();
        // not starting the CommandScheduler yet because test methods need to setup mocks first
    }

    @After
    public void tearDown() throws Exception {
        if (mScheduler != null) {
            mScheduler.shutdown();
        }
    }

    /** Test {@link CommandScheduler#run()} when no configs have been added */
    @Test
    public void testRun_empty() throws InterruptedException {
        mFakeDeviceManager.setNumDevices(1);

        mScheduler.start();
        while (!mScheduler.isAlive()) {
            Thread.sleep(10);
        }
        mScheduler.shutdown();
        // expect run not to block
        mScheduler.join();

        mFakeDeviceManager.assertDevicesFreed();
    }

    /** Test {@link CommandScheduler#addCommand(String[])} when help mode is specified */
    @Test
    public void testAddConfig_configHelp() throws ConfigurationException {
        String[] args = new String[] {"test"};
        mCommandOptions.setHelpMode(true);
        setCreateConfigExpectations(args);

        mScheduler.start();
        mScheduler.addCommand(args);

        verify(mMockConfigFactory)
                .printHelpForConfig(AdditionalMatchers.aryEq(args), eq(true), eq(System.out));

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
    }

    /** Test {@link CommandScheduler#run()} when one config has been added */
    @Test
    public void testRun_oneConfig() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevices(2);
        setCreateConfigExpectations(args);

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
    }

    /**
     * Test {@link CommandScheduler#removeAllCommands()} for idle case, where command is waiting for
     * device.
     */
    @Test
    public void testRemoveAllCommands() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevices(0);
        setCreateConfigExpectations(args);

        mScheduler.start();
        mScheduler.addCommand(args);
        assertEquals(1, mScheduler.getAllCommandsSize());
        mScheduler.removeAllCommands();
        assertEquals(0, mScheduler.getAllCommandsSize());

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
    }

    /** Test {@link CommandScheduler#run()} when one config has been added in dry-run mode */
    @Test
    public void testRun_dryRun() throws Throwable {
        String[] dryRunArgs = new String[] {"--dry-run"};
        mCommandOptions.setDryRunMode(true);
        mFakeDeviceManager.setNumDevices(2);
        setCreateConfigExpectations(dryRunArgs);

        // add a second command, to verify the first dry-run command did not get added
        String[] args2 = new String[] {"test"};
        setCreateConfigExpectations(args2);

        mScheduler.start();
        Pair<Boolean, Integer> addCommandReturn = mScheduler.addCommand(dryRunArgs);
        assertFalse(addCommandReturn.first);
        assertTrue(addCommandReturn.second < 0);
        // the same config object is being used, so clear its state
        mCommandOptions.setDryRunMode(false);

        addCommandReturn = mScheduler.addCommand(args2);
        assertTrue(addCommandReturn.first);
        assertTrue(addCommandReturn.second >= 0);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());
        verify(mMockConfiguration, times(2)).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(dryRunArgs), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
    }

    /**
     * Test {@link CommandScheduler#run()} when one config has been added in noisy-dry-run or
     * dry-run mode the keystore is properly faked by a {@link DryRunKeyStore}.
     */
    @Test
    public void testRun_dryRun_keystore() throws Throwable {
        mScheduler =
                new TestableCommandScheduler() {
                    @Override
                    protected IConfigurationFactory getConfigFactory() {
                        // Use the real factory for that loading test.
                        return ConfigurationFactory.getInstance();
                    }
                };
        String[] dryRunArgs =
                new String[] {"empty", "--noisy-dry-run", "--min-loop-time", "USE_KEYSTORE@fake"};
        mFakeDeviceManager.setNumDevices(2);

        mScheduler.start();
        assertFalse(mScheduler.addCommand(dryRunArgs).first);
        assertTrue(mScheduler.addCommand(dryRunArgs).second < 0);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();

        mFakeDeviceManager.assertDevicesFreed();
    }

    /**
     * Test simple case for {@link CommandScheduler#execCommand(IScheduledInvocationListener,
     * String[])}
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecCommand() throws Throwable {
        String[] args = new String[] {"foo"};
        setCreateConfigExpectations(args);

        IDevice mockIDevice = mock(IDevice.class);
        ITestDevice mockDevice = mock(ITestDevice.class);
        when(mockDevice.getSerialNumber()).thenReturn("serial");
        when(mockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        mockDevice.setRecoveryMode(eq(RecoveryMode.AVAILABLE));
        when(mockDevice.getIDevice()).thenReturn(mockIDevice);
        IScheduledInvocationListener mockListener = mock(IScheduledInvocationListener.class);
        mockListener.invocationInitiated((IInvocationContext) any());
        mockListener.invocationComplete(
                (IInvocationContext) any(), (Map<ITestDevice, FreeDeviceState>) any());
        when(mockDevice.waitForDeviceShell(anyLong())).thenReturn(true);
        mScheduler =
                new TestableCommandScheduler() {
                    @Override
                    DeviceAllocationResult allocateDevices(
                            IConfiguration config,
                            IDeviceManager manager,
                            ArrayList<String> excludeDevices) {
                        DeviceAllocationResult results = new DeviceAllocationResult();
                        Map<String, ITestDevice> allocated = new HashMap<>();
                        ((MockDeviceManager) manager).addDevice(mockDevice);
                        allocated.put("device", ((MockDeviceManager) manager).allocateDevice());
                        results.addAllocatedDevices(allocated);
                        return results;
                    }
                };

        mScheduler.start();
        mScheduler.execCommand(mockListener, args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join(2 * 1000);
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any(),
                        any());
        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
    }

    /**
     * Test{@link CommandScheduler#run()} when config is matched to a specific device serial number
     *
     * <p>Adds two configs to run, and verify they both run on one device
     */
    @Test
    public void testRun_configSerial() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevices(2);
        setCreateConfigExpectations(args);
        // allocate and free a device to get its serial
        ITestDevice dev = mFakeDeviceManager.allocateDevice();
        mDeviceOptions.addSerial(dev.getSerialNumber());
        mMockConfiguration.validateOptions();

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.addCommand(args);
        mFakeDeviceManager.freeDevice(dev, FreeDeviceState.AVAILABLE);

        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verify(mMockInvocation, times(2))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());
        verify(mMockConfiguration, times(3)).validateOptions();
        verify(mMockConfigFactory, times(2))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
    }

    /**
     * Test{@link CommandScheduler#run()} when config is matched to a exclude specific device serial
     * number.
     *
     * <p>Adds two configs to run, and verify they both run on the other device
     */
    @Test
    public void testRun_configExcludeSerial() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevices(2);
        setCreateConfigExpectations(args);
        // allocate and free a device to get its serial
        ITestDevice dev = mFakeDeviceManager.allocateDevice();
        mDeviceOptions.addExcludeSerial(dev.getSerialNumber());
        ITestDevice expectedDevice = mFakeDeviceManager.allocateDevice();
        mMockConfiguration.validateOptions();

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.addCommand(args);
        mFakeDeviceManager.freeDevice(dev, FreeDeviceState.AVAILABLE);
        mFakeDeviceManager.freeDevice(expectedDevice, FreeDeviceState.AVAILABLE);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verify(mMockInvocation, times(2))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());
        verify(mMockConfiguration, times(3)).validateOptions();
        verify(mMockConfigFactory, times(2))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
    }

    /** Test {@link CommandScheduler#run()} when one config has been rescheduled */
    @Test
    public void testRun_rescheduled() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevices(2);
        setCreateConfigExpectations(args);

        final IConfiguration mockRescheduledConfig = mock(IConfiguration.class);
        when(mockRescheduledConfig.getCommandOptions()).thenReturn(mCommandOptions);
        when(mockRescheduledConfig.getDeviceRequirements()).thenReturn(mDeviceOptions);
        when(mockRescheduledConfig.getDeviceConfig()).thenReturn(mDeviceConfigList);
        when(mockRescheduledConfig.getCommandLine()).thenReturn("");
        when(mockRescheduledConfig.getConfigurationDescription()).thenReturn(mConfigDescriptor);
        when(mockRescheduledConfig.getLogSaver()).thenReturn(mMockLogSaver);
        when(mockRescheduledConfig.getLogOutput()).thenReturn(mMockLogOutput);

        // The first call sets recheduler and throws. The second call is successful.
        doAnswer(
                        invocation -> {
                            if (mIsFirstInvoke) {
                                mIsFirstInvoke = false;

                                IRescheduler rescheduler =
                                        (IRescheduler) invocation.getArguments()[2];
                                rescheduler.scheduleConfig(mockRescheduledConfig);
                                throw new DeviceNotAvailableException("not avail", "fakeserial");
                            } else {
                                return null;
                            }
                        })
                .when(mMockInvocation)
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();

        verify(mMockConfiguration).validateOptions();
        verify(mMockInvocation, times(2))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());
    }

    /**
     * Simple success case test for {@link CommandScheduler#addCommandFile(String, java.util.List)}
     *
     * @throws ConfigurationException
     */
    @Test
    public void testAddCommandFile() throws ConfigurationException {
        // set number of devices to 0 so we can verify command presence
        mFakeDeviceManager.setNumDevices(0);
        List<String> extraArgs = Arrays.asList("--bar");
        setCreateConfigExpectations(new String[] {"foo", "--bar"});
        mMockConfiguration.validateOptions();
        final List<CommandLine> cmdFileContent =
                Arrays.asList(new CommandLine(Arrays.asList("foo"), null, 0));
        mCommandFileParser =
                new CommandFileParser() {
                    @Override
                    public List<CommandLine> parseFile(File cmdFile) {
                        return cmdFileContent;
                    }
                };

        mScheduler.start();
        mScheduler.addCommandFile("mycmd.txt", extraArgs);
        List<CommandTracker> cmds = mScheduler.getCommandTrackers();
        assertEquals(1, cmds.size());
        assertEquals("foo", cmds.get(0).getArgs()[0]);
        assertEquals("--bar", cmds.get(0).getArgs()[1]);
    }

    /**
     * Simple success case test for auto reloading a command file
     *
     * @throws ConfigurationException
     */
    @Test
    public void testAddCommandFile_reload() throws ConfigurationException {
        // set number of devices to 0 so we can verify command presence
        mFakeDeviceManager.setNumDevices(0);
        String[] addCommandArgs = new String[] {"fromcommand"};
        List<String> extraArgs = Arrays.asList("--bar");

        setCreateConfigExpectations(addCommandArgs);
        String[] cmdFile1Args = new String[] {"fromFile1", "--bar"};
        setCreateConfigExpectations(cmdFile1Args);
        String[] cmdFile2Args = new String[] {"fromFile2", "--bar"};
        setCreateConfigExpectations(cmdFile2Args);

        final List<CommandLine> cmdFileContent1 =
                Arrays.asList(new CommandLine(Arrays.asList("fromFile1"), null, 0));
        final List<CommandLine> cmdFileContent2 =
                Arrays.asList(new CommandLine(Arrays.asList("fromFile2"), null, 0));
        mCommandFileParser =
                new CommandFileParser() {
                    boolean firstCall = true;

                    @Override
                    public List<CommandLine> parseFile(File cmdFile) {
                        if (firstCall) {
                            firstCall = false;
                            return cmdFileContent1;
                        }
                        return cmdFileContent2;
                    }
                };

        mScheduler.start();
        mScheduler.setCommandFileReload(true);
        mScheduler.addCommand(addCommandArgs);
        mScheduler.addCommandFile("mycmd.txt", extraArgs);

        List<CommandTracker> cmds = mScheduler.getCommandTrackers();
        assertEquals(2, cmds.size());
        Collections.sort(cmds, new CommandTrackerIdComparator());
        Assert.assertArrayEquals(addCommandArgs, cmds.get(0).getArgs());
        Assert.assertArrayEquals(cmdFile1Args, cmds.get(1).getArgs());

        // now reload the command file
        mScheduler.notifyFileChanged(new File("mycmd.txt"), extraArgs);

        cmds = mScheduler.getCommandTrackers();
        assertEquals(2, cmds.size());
        Collections.sort(cmds, new CommandTrackerIdComparator());
        Assert.assertArrayEquals(addCommandArgs, cmds.get(0).getArgs());
        Assert.assertArrayEquals(cmdFile2Args, cmds.get(1).getArgs());
        verify(mMockConfiguration, times(3)).validateOptions();
    }

    /** Verify attempts to add the same commmand file in reload mode are rejected */
    @Test
    public void testAddCommandFile_twice() throws ConfigurationException {
        // set number of devices to 0 so we can verify command presence
        mFakeDeviceManager.setNumDevices(0);
        String[] cmdFile1Args = new String[] {"fromFile1"};
        setCreateConfigExpectations(cmdFile1Args);
        setCreateConfigExpectations(cmdFile1Args);

        final List<CommandLine> cmdFileContent1 =
                Arrays.asList(new CommandLine(Arrays.asList("fromFile1"), null, 0));
        mCommandFileParser =
                new CommandFileParser() {
                    @Override
                    public List<CommandLine> parseFile(File cmdFile) {
                        return cmdFileContent1;
                    }
                };

        when(mMockFileWatcher.isFileWatched(Mockito.any())).thenReturn(false).thenReturn(true);
        mScheduler.start();
        mScheduler.setCommandFileReload(true);
        mScheduler.addCommandFile("mycmd.txt", Collections.<String>emptyList());

        List<CommandTracker> cmds = mScheduler.getCommandTrackers();
        assertEquals(1, cmds.size());
        Assert.assertArrayEquals(cmdFile1Args, cmds.get(0).getArgs());

        // now attempt to add the same command file
        mScheduler.addCommandFile("mycmd.txt", Collections.<String>emptyList());

        // expect reload
        // ensure same state as before
        cmds = mScheduler.getCommandTrackers();
        assertEquals(1, cmds.size());
        Assert.assertArrayEquals(cmdFile1Args, cmds.get(0).getArgs());
        verify(mMockConfiguration, times(2)).validateOptions();
    }

    /** Test {@link CommandScheduler#shutdown()} when no devices are available. */
    @Test
    public void testShutdown() throws Exception {
        mFakeDeviceManager.setNumDevices(0);
        mScheduler.start();
        while (!mScheduler.isAlive()) {
            Thread.sleep(10);
        }
        // hack - sleep a bit more to ensure allocateDevices is called
        Thread.sleep(50);
        mScheduler.shutdown();
        mScheduler.join();
        // test will hang if not successful
    }

    /** Set EasyMock expectations for a create configuration call. */
    private void setCreateConfigExpectations(String[] args) throws ConfigurationException {
        when(mMockConfigFactory.createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any()))
                .thenReturn(mMockConfiguration);
        when(mMockConfiguration.getCommandOptions()).thenReturn(mCommandOptions);
        when(mMockConfiguration.getDeviceRequirements()).thenReturn(mDeviceOptions);
        when(mMockConfiguration.getDeviceConfig()).thenReturn(mDeviceConfigList);
        when(mMockConfiguration.getCommandLine()).thenReturn("");
        when(mMockConfiguration.getConfigurationDescription()).thenReturn(mConfigDescriptor);
        when(mMockConfiguration.getLogSaver()).thenReturn(mMockLogSaver);
        when(mMockConfiguration.getLogOutput()).thenReturn(mMockLogOutput);

        // Assume all legacy test are single device
        if (mDeviceConfigList.isEmpty()) {
            IDeviceConfiguration mockConfig = new DeviceConfigurationHolder("device");
            mockConfig.addSpecificConfig(mDeviceOptions);
            mDeviceConfigList.add(mockConfig);
        }
    }

    /** Test that Available device at the end of a test are available to be reselected. */
    @Test
    public void testDeviceReleased() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevices(1);
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
        setCreateConfigExpectations(args);

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
    }

    /**
     * Test that if device is released properly and marked as such, the next invocation can run
     * without issues.
     */
    @Test
    public void testDeviceReleasedEarly() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevices(1);
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
        setCreateConfigExpectations(args);

        doAnswer(
                        invocation -> {
                            IInvocationContext context =
                                    (IInvocationContext) invocation.getArguments()[0];
                            IScheduledInvocationListener listener =
                                    (IScheduledInvocationListener) invocation.getArguments()[3];
                            Map<ITestDevice, FreeDeviceState> deviceStates = new HashMap<>();
                            for (ITestDevice device : context.getDevices()) {
                                deviceStates.put(device, FreeDeviceState.AVAILABLE);
                            }
                            context.markReleasedEarly();
                            listener.releaseDevices(context, deviceStates);
                            RunUtil.getDefault().sleep(500);
                            return null;
                        })
                .when(mMockInvocation)
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());

        // Second invocation runs properly
        mScheduler.start();
        mScheduler.addCommand(args);
        RunUtil.getDefault().sleep(100);
        mScheduler.addCommand(args);
        RunUtil.getDefault().sleep(200);
        mScheduler.shutdown();
        mScheduler.join();
        verify(mMockInvocation, times(2))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());
        verify(mMockConfiguration, times(2)).validateOptions();
        verify(mMockConfigFactory, times(2))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
        assertNull(mScheduler.getLastInvocationThrowable());
    }

    /**
     * If for any reasons the device is released early and it's unexpected, we still release it in
     * the next invocation properly.
     */
    @Test
    public void testDeviceReleasedEarly_conflict() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevices(1);
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
        setCreateConfigExpectations(args);
        reset(mMockInvocation);
        when(mMockInvocation.getExitInfo()).thenReturn(null);
        doAnswer(
                        invocation -> {
                            IInvocationContext context =
                                    (IInvocationContext) invocation.getArguments()[0];
                            IScheduledInvocationListener listener =
                                    (IScheduledInvocationListener) invocation.getArguments()[3];
                            Map<ITestDevice, FreeDeviceState> deviceStates = new HashMap<>();
                            for (ITestDevice device : context.getDevices()) {
                                deviceStates.put(device, FreeDeviceState.AVAILABLE);
                            }
                            // Device is released early but this is not marked properly in
                            // context
                            listener.releaseDevices(context, deviceStates);
                            RunUtil.getDefault().sleep(500);
                            return null;
                        })
                .when(mMockInvocation)
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());

        mScheduler.start();
        mScheduler.addCommand(args);
        RunUtil.getDefault().sleep(100);
        mScheduler.addCommand(args);
        RunUtil.getDefault().sleep(200);
        mScheduler.shutdown();
        mScheduler.join();
        verify(mMockConfiguration, times(2)).validateOptions();

        verify(mMockConfigFactory, times(2))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
        assertNotNull(mScheduler.getLastInvocationThrowable());
        assertEquals(
                "Attempting invocation on device serial0 when one is already running",
                mScheduler.getLastInvocationThrowable().getMessage());
    }

    /**
     * Test that NOT_AVAILABLE devices at the end of a test are not returned to the selectable
     * devices.
     */
    @Test
    public void testDeviceReleased_unavailable() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevicesCustom(1, TestDeviceState.NOT_AVAILABLE, IDevice.class);
        assertEquals(1, mFakeDeviceManager.getQueueOfAvailableDeviceSize());
        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());

        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 0);
    }

    /**
     * Test that only the device NOT_AVAILABLE, selected for invocation is not returned at the end.
     */
    @Test
    public void testDeviceReleased_unavailableMulti() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevicesCustom(2, TestDeviceState.NOT_AVAILABLE, IDevice.class);
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 2);
        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());

        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
    }

    /** Test that the TCP device NOT available are NOT released. */
    @Test
    public void testTcpDevice_NotReleased() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevicesStub(
                1, TestDeviceState.NOT_AVAILABLE, new TcpDevice("serial"));
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());
    }

    /** Test that the TCP device NOT available selected for a run is NOT released. */
    @Test
    public void testTcpDevice_NotReleasedMulti() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevicesStub(
                2, TestDeviceState.NOT_AVAILABLE, new TcpDevice("serial"));
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 2);
        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 2);
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());
    }

    /** Test that the Stub device NOT available are NOT released. */
    @Test
    public void testStubDevice_NotReleased() throws Throwable {
        String[] args = new String[] {"test"};
        IDevice stub = new StubDevice("emulator-5554", true);
        mFakeDeviceManager.setNumDevicesStub(1, TestDeviceState.NOT_AVAILABLE, stub);
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        assertTrue(mFakeDeviceManager.getQueueOfAvailableDeviceSize() == 1);
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());
    }

    /** Test that a device recovery state is reset when returned to the available queue. */
    @Test
    public void testDeviceRecoveryState() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevicesCustomRealNoRecovery(1, IDevice.class);
        assertEquals(1, mFakeDeviceManager.getQueueOfAvailableDeviceSize());
        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());

        assertEquals(1, mFakeDeviceManager.getQueueOfAvailableDeviceSize());
        ITestDevice t = mFakeDeviceManager.allocateDevice();
        assertTrue(t.getRecoveryMode().equals(RecoveryMode.AVAILABLE));
    }

    /** Test that a device that is unresponsive at the end of an invocation is made unavailable. */
    @Test
    public void testDevice_unresponsive() throws Throwable {
        String[] args = new String[] {"test"};
        mFakeDeviceManager.setNumDevicesUnresponsive(1);
        assertEquals(1, mFakeDeviceManager.getQueueOfAvailableDeviceSize());
        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join();
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any());

        // Device does not return to the list since it's unavailable.
        assertEquals(0, mFakeDeviceManager.getQueueOfAvailableDeviceSize());
    }

    /**
     * Test that {@link CommandScheduler#displayCommandQueue(PrintWriter)} is properly printing the
     * state of a command.
     */
    @Test
    public void testDisplayCommandQueue() throws Throwable {
        String[] args = new String[] {"empty"};
        setCreateConfigExpectations(args);

        mScheduler.start();
        mScheduler.addCommand(args);
        OutputStream res = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(res);
        mScheduler.displayCommandQueue(pw);

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
        pw.flush();
        assertEquals(
                "Id  Config  Created  Exec time  State            Sleep time  Rescheduled  Loop  "
                    + " \n"
                    + "1   empty   0m:00    0m:00      Wait_for_device  N/A         false       "
                    + " false  \n",
                res.toString());
        mScheduler.shutdown();
    }

    /**
     * Test that {@link CommandScheduler#dumpCommandsXml(PrintWriter, String)} is properly printing
     * the xml of a command.
     */
    @Test
    public void testDumpCommandXml() throws Throwable {
        String[] args = new String[] {"empty"};
        OutputStream res = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(res);
        setCreateConfigExpectations(args);

        mMockConfiguration.dumpXml(any());

        mScheduler.start();
        mScheduler.addCommand(args);
        mScheduler.dumpCommandsXml(pw, null);

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
        pw.flush();
        String filename = res.toString().replace("Saved command dump to ", "").trim();
        File test = new File(filename);
        try {
            assertTrue(test.exists());
            mScheduler.shutdown();
        } finally {
            FileUtil.deleteFile(test);
        }
    }

    /**
     * Test that {@link CommandScheduler#displayCommandsInfo(PrintWriter, String)} is properly
     * printing the command.
     */
    @Test
    public void testDisplayCommandsInfo() throws Throwable {
        String[] args = new String[] {"empty"};
        setCreateConfigExpectations(args);

        mScheduler.start();
        mScheduler.addCommand(args);
        OutputStream res = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(res);
        mScheduler.displayCommandsInfo(pw, null);

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
        pw.flush();
        assertEquals("Command 1: [0m:00] empty\n", res.toString());
        mScheduler.shutdown();
    }

    /**
     * Test that {@link CommandScheduler#getInvocationInfo(int)} is properly returning null if no
     * invocation matching the id.
     */
    @Test
    public void testGetInvocationInfo_null() throws Throwable {
        String[] args = new String[] {"empty", "test"};
        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        mScheduler.addCommand(args);
        assertNull(mScheduler.getInvocationInfo(999));
        mScheduler.shutdown();
    }

    @Test
    public void testAllocateDevices() throws Exception {
        String[] args = new String[] {"foo", "test"};
        mFakeDeviceManager.setNumDevices(1);
        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        DeviceAllocationResult results =
                mScheduler.allocateDevices(mMockConfiguration, mFakeDeviceManager);
        assertTrue(results.wasAllocationSuccessful());
        Map<String, ITestDevice> devices = results.getAllocatedDevices();
        assertEquals(1, devices.size());
        mScheduler.shutdown();
    }

    @Test
    public void testAllocateDevices_replicated() throws Exception {
        String[] args = new String[] {"foo", "test"};
        mFakeDeviceManager.setNumDevices(3);
        setCreateConfigExpectations(args);
        OptionSetter setter = new OptionSetter(mCommandOptions);
        setter.setOptionValue("replicate-parent-setup", "true");
        mCommandOptions.setShardCount(3);
        mMockConfiguration.validateOptions();
        for (int i = 0; i < 2; i++) {
            IConfiguration configReplicat = new Configuration("test", "test");
            configReplicat.setDeviceConfig(new DeviceConfigurationHolder("serial"));
            when(mMockConfiguration.partialDeepClone(
                            Mockito.any(), Mockito.eq(mMockKeyStoreClient)))
                    .thenReturn(configReplicat);
        }
        mMockConfiguration.setDeviceConfigList(any());

        mScheduler.start();
        DeviceAllocationResult results =
                mScheduler.allocateDevices(mMockConfiguration, mFakeDeviceManager);
        assertTrue(results.wasAllocationSuccessful());
        Map<String, ITestDevice> devices = results.getAllocatedDevices();
        // With replicated setup, all devices get allocated.
        assertEquals(3, devices.size());
        mScheduler.shutdown();
    }

    private IDeviceConfiguration createDeviceConfig(String serial) throws Exception {
        IDeviceConfiguration mockConfig = new DeviceConfigurationHolder(serial);
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addSerial(serial);
        mockConfig.addSpecificConfig(options);
        return mockConfig;
    }

    private IDeviceConfiguration createNullDeviceConfig(String serial) throws Exception {
        IDeviceConfiguration mockConfig = new DeviceConfigurationHolder(serial);
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addSerial(serial);
        options.setNullDeviceRequested(true);
        mockConfig.addSpecificConfig(options);
        return mockConfig;
    }

    @Test
    public void testAllocateDevices_multipleDevices() throws Exception {
        String[] args = new String[] {"foo", "test"};

        mFakeDeviceManager.setNumDevices(2);
        mDeviceConfigList.add(createDeviceConfig("serial0"));
        mDeviceConfigList.add(createDeviceConfig("serial1"));

        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        DeviceAllocationResult results =
                mScheduler.allocateDevices(mMockConfiguration, mFakeDeviceManager);
        assertTrue(results.wasAllocationSuccessful());
        Map<String, ITestDevice> devices = results.getAllocatedDevices();
        assertEquals(2, devices.size());
        assertEquals(0, mFakeDeviceManager.getQueueOfAvailableDeviceSize());
        mScheduler.shutdown();
    }

    @Test
    public void testAllocateDevices_multipleDevices_failed() throws Exception {
        String[] args = new String[] {"foo", "test"};

        mFakeDeviceManager.setNumDevices(2);
        mDeviceConfigList.add(createDeviceConfig("serial0"));
        mDeviceConfigList.add(createDeviceConfig("not_exist_serial"));

        setCreateConfigExpectations(args);
        mMockConfiguration.validateOptions();

        mScheduler.start();
        DeviceAllocationResult results =
                mScheduler.allocateDevices(mMockConfiguration, mFakeDeviceManager);
        assertFalse(results.wasAllocationSuccessful());
        Map<String, ITestDevice> devices = results.getAllocatedDevices();
        assertEquals(0, devices.size());
        assertEquals(2, mFakeDeviceManager.getQueueOfAvailableDeviceSize());
        mScheduler.shutdown();
    }

    /**
     * Test case for execCommand with multiple devices. {@link
     * CommandScheduler#execCommand(IScheduledInvocationListener, String[])}
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecCommand_multipleDevices() throws Throwable {
        String[] args = new String[] {"foo"};
        mFakeDeviceManager.setNumDevices(2);
        mDeviceConfigList.add(createDeviceConfig("serial0"));
        mDeviceConfigList.add(createDeviceConfig("serial1"));
        setCreateConfigExpectations(args);

        mMockInvocation.invoke(
                (IInvocationContext) any(),
                (IConfiguration) any(),
                (IRescheduler) any(),
                (ITestInvocationListener) any(),
                // This is FreeDeviceHandler.
                (IScheduledInvocationListener) any());
        IScheduledInvocationListener mockListener = mock(IScheduledInvocationListener.class);
        mockListener.invocationInitiated((IInvocationContext) any());
        mockListener.invocationComplete(
                (IInvocationContext) any(), (Map<ITestDevice, FreeDeviceState>) any());
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any(),
                        // This is FreeDeviceHandler.
                        (IScheduledInvocationListener) any());

        mScheduler.start();
        mScheduler.execCommand(mockListener, args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join(2 * 1000);

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
    }

    /**
     * Test case for execCommand with multiple devices but fail to allocate some device. {@link
     * CommandScheduler#execCommand(IScheduledInvocationListener, String[])}
     */
    @Test
    public void testExecCommand_multipleDevices_noDevice() throws Throwable {
        String[] args = new String[] {"foo"};
        mFakeDeviceManager.setNumDevices(2);
        mDeviceConfigList.add(createDeviceConfig("serial0"));
        mDeviceConfigList.add(createDeviceConfig("not_exist_serial"));
        setCreateConfigExpectations(args);

        IScheduledInvocationListener mockListener = mock(IScheduledInvocationListener.class);

        mScheduler.start();
        try {
            mScheduler.execCommand(mockListener, args);
            fail();
        } catch (NoDeviceException e) {
            // expect NoDeviceException
        }
        mScheduler.shutdownOnEmpty();
        mScheduler.join(2 * 1000);

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();
    }

    /**
     * Test case for execCommand with reserved device. {@link
     * CommandScheduler#execCommand(IScheduledInvocationListener, List<ITestDevice>, String[])}
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecCommand_reservedDevice() throws Throwable {
        String[] args = new String[] {"foo"};
        List<ITestDevice> reservedDevices = Arrays.asList(mock(ITestDevice.class));
        mDeviceConfigList.add(createDeviceConfig("serial0"));
        setCreateConfigExpectations(args);

        mMockInvocation.invoke(
                (IInvocationContext) any(),
                (IConfiguration) any(),
                (IRescheduler) any(),
                (ITestInvocationListener) any(),
                // This is FreeDeviceHandler.
                (IScheduledInvocationListener) any());
        IScheduledInvocationListener mockListener = mock(IScheduledInvocationListener.class);
        mockListener.invocationInitiated((IInvocationContext) any());
        mockListener.invocationComplete(
                (IInvocationContext) any(), (Map<ITestDevice, FreeDeviceState>) any());
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any(),
                        // This is FreeDeviceHandler.
                        (IScheduledInvocationListener) any());

        mScheduler.start();
        mScheduler.execCommand(mockListener, reservedDevices, args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join(2 * 1000);

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
    }

    /**
     * Test case for execCommand with reserved devices and null devices. {@link
     * CommandScheduler#execCommand(IScheduledInvocationListener, List<ITestDevice>, String[])}
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecCommand_reservedAndNullDevices() throws Throwable {
        String[] args = new String[] {"foo"};
        List<ITestDevice> reservedDevices =
                Arrays.asList(mock(ITestDevice.class), mock(ITestDevice.class));
        IInvocationContext spyContext = spy(new InvocationContext());
        mFakeDeviceManager.setNumDevicesCustom(2, TestDeviceState.ONLINE, NullDevice.class);
        mDeviceConfigList.add(createDeviceConfig("reserved0"));
        mDeviceConfigList.add(createNullDeviceConfig("serial0"));
        mDeviceConfigList.add(createDeviceConfig("reserved1"));
        mDeviceConfigList.add(createNullDeviceConfig("serial1"));
        setCreateConfigExpectations(args);

        mMockInvocation.invoke(
                (IInvocationContext) any(),
                (IConfiguration) any(),
                (IRescheduler) any(),
                (ITestInvocationListener) any(),
                // This is FreeDeviceHandler.
                (IScheduledInvocationListener) any());
        IScheduledInvocationListener mockListener = mock(IScheduledInvocationListener.class);
        mockListener.invocationInitiated((IInvocationContext) any());
        mockListener.invocationComplete(
                (IInvocationContext) any(), (Map<ITestDevice, FreeDeviceState>) any());
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any(),
                        // This is FreeDeviceHandler.
                        (IScheduledInvocationListener) any());

        mScheduler.start();
        mScheduler.execCommand(spyContext, mockListener, reservedDevices, args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join(2 * 1000);

        verify(mMockConfiguration).validateOptions();
        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        verify(spyContext, times(1)).addAllocatedDevice(devicesCapture.capture());
        // Verify allocated devices added to the context are in the same order as in config.
        HashMap<String, ITestDevice> devices = devicesCapture.getValue();
        List<String> deviceNames = new ArrayList<String>();
        devices.forEach((deviceName, device) -> deviceNames.add(deviceName));
        assertEquals(Arrays.asList("reserved0", "serial0", "reserved1", "serial1"), deviceNames);
        mFakeDeviceManager.assertDevicesFreed();
    }

    /**
     * Test case for execCommand with too many reserved devices and null devices. {@link
     * CommandScheduler#execCommand(IScheduledInvocationListener, List<ITestDevice>, String[])}
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecCommand_reservedTooManyDevices() throws Throwable {
        String[] args = new String[] {"foo"};
        List<ITestDevice> reservedDevices =
                Arrays.asList(mock(ITestDevice.class), mock(ITestDevice.class));
        mFakeDeviceManager.setNumDevicesCustom(2, TestDeviceState.ONLINE, NullDevice.class);
        mDeviceConfigList.add(createDeviceConfig("reserved0"));
        mDeviceConfigList.add(createNullDeviceConfig("serial0"));
        mDeviceConfigList.add(createNullDeviceConfig("serial1"));
        setCreateConfigExpectations(args);

        mMockInvocation.invoke(
                (IInvocationContext) any(),
                (IConfiguration) any(),
                (IRescheduler) any(),
                (ITestInvocationListener) any(),
                // This is FreeDeviceHandler.
                (IScheduledInvocationListener) any());
        IScheduledInvocationListener mockListener = mock(IScheduledInvocationListener.class);
        mockListener.invocationInitiated((IInvocationContext) any());
        mockListener.invocationComplete(
                (IInvocationContext) any(), (Map<ITestDevice, FreeDeviceState>) any());
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any(),
                        // This is FreeDeviceHandler.
                        (IScheduledInvocationListener) any());

        mScheduler.start();

        try {
            mScheduler.execCommand(mockListener, reservedDevices, args);
            fail("NoDeviceException was not rethrown");
        } catch (NoDeviceException e) {
            // expected
            assertEquals("Reserved devices (2) more than required (1).", e.getMessage());
        }

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
    }

    /**
     * Test case for execCommand with too few reserved devices and null devices. {@link
     * CommandScheduler#execCommand(IScheduledInvocationListener, List<ITestDevice>, String[])}
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecCommand_reservedTooFewDevices() throws Throwable {
        String[] args = new String[] {"foo"};
        List<ITestDevice> reservedDevices = Arrays.asList(mock(ITestDevice.class));
        mFakeDeviceManager.setNumDevicesCustom(2, TestDeviceState.ONLINE, NullDevice.class);
        mDeviceConfigList.add(createDeviceConfig("reserved0"));
        mDeviceConfigList.add(createDeviceConfig("reserved1"));
        mDeviceConfigList.add(createNullDeviceConfig("serial0"));
        mDeviceConfigList.add(createNullDeviceConfig("serial1"));
        setCreateConfigExpectations(args);

        mMockInvocation.invoke(
                (IInvocationContext) any(),
                (IConfiguration) any(),
                (IRescheduler) any(),
                (ITestInvocationListener) any(),
                // This is FreeDeviceHandler.
                (IScheduledInvocationListener) any());
        IScheduledInvocationListener mockListener = mock(IScheduledInvocationListener.class);
        mockListener.invocationInitiated((IInvocationContext) any());
        mockListener.invocationComplete(
                (IInvocationContext) any(), (Map<ITestDevice, FreeDeviceState>) any());
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any(),
                        // This is FreeDeviceHandler.
                        (IScheduledInvocationListener) any());

        mScheduler.start();

        try {
            mScheduler.execCommand(mockListener, reservedDevices, args);
            fail("NoDeviceException was not rethrown");
        } catch (NoDeviceException e) {
            // expected
            assertEquals("Reserved devices (1) fewer than required.", e.getMessage());
        }

        verify(mMockConfiguration).validateOptions();

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
    }

    /**
     * Test that when a command runs in the versioned subprocess with --invocation-data option we do
     * not add the attributes again
     */
    @Test
    public void testExecCommand_versioning() throws Throwable {
        String[] args =
                new String[] {
                    "foo", "--invocation-data", "test",
                };
        setCreateConfigExpectations(args);
        OptionSetter setter = new OptionSetter(mCommandOptions);
        // If invocation-data are added and we are in a versioned invocation, the data should not
        // be added again.
        setter.setOptionValue("invocation-data", "key", "value");
        mConfigDescriptor.setSandboxed(true);

        mMockConfiguration.validateOptions();
        IDevice mockIDevice = mock(IDevice.class);
        ITestDevice mockDevice = mock(ITestDevice.class);
        when(mockDevice.getSerialNumber()).thenReturn("serial");
        when(mockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        mockDevice.setRecoveryMode(eq(RecoveryMode.AVAILABLE));
        when(mockDevice.getIDevice()).thenReturn(mockIDevice);
        IScheduledInvocationListener mockListener = mock(IScheduledInvocationListener.class);
        mockListener.invocationInitiated((InvocationContext) any());
        mockListener.invocationComplete((IInvocationContext) any(), any());
        when(mockDevice.waitForDeviceShell(anyLong())).thenReturn(true);

        mScheduler =
                new TestableCommandScheduler() {
                    @Override
                    DeviceAllocationResult allocateDevices(
                            IConfiguration config,
                            IDeviceManager manager,
                            ArrayList<String> excludeDevices) {
                        DeviceAllocationResult results = new DeviceAllocationResult();
                        Map<String, ITestDevice> allocated = new HashMap<>();
                        ((MockDeviceManager) manager).addDevice(mockDevice);
                        allocated.put("device", ((MockDeviceManager) manager).allocateDevice());
                        results.addAllocatedDevices(allocated);
                        return results;
                    }
                };

        mScheduler.start();
        mScheduler.execCommand(mockListener, args);
        mScheduler.shutdownOnEmpty();
        mScheduler.join(2 * 1000);
        verify(mMockInvocation, times(1))
                .invoke(
                        (IInvocationContext) any(),
                        (IConfiguration) any(),
                        (IRescheduler) any(),
                        (ITestInvocationListener) any(),
                        // This is FreeDeviceHandler.
                        (IScheduledInvocationListener) any());

        verify(mMockConfigFactory, times(1))
                .createConfigurationFromArgs(
                        AdditionalMatchers.aryEq(args), isNull(), (IKeyStoreClient) any());
        mFakeDeviceManager.assertDevicesFreed();

        // only attribute is invocation ID
        assertEquals(1, mContext.getAttributes().size());
        assertNotNull(mContext.getInvocationId());
    }
}
