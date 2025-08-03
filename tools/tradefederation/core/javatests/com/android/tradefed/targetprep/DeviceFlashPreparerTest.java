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

package com.android.tradefed.targetprep;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.IDeviceFlasher.UserDataFlashOption;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Arrays;

/** Unit tests for {@link DeviceFlashPreparer}. */
@RunWith(JUnit4.class)
public class DeviceFlashPreparerTest {

    @Mock IDeviceFlasher mMockFlasher;
    private DeviceFlashPreparer mDeviceFlashPreparer;
    @Mock ITestDevice mMockDevice;
    private IDeviceBuildInfo mMockBuildInfo;
    @Mock IHostOptions mMockHostOptions;
    private File mTmpDir;
    private boolean mFlashingMetricsReported;
    private TestInformation mTestInfo;
    private OptionSetter mSetter;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockDevice.getSerialNumber()).thenReturn("foo");
        when(mMockDevice.getOptions()).thenReturn(new TestDeviceOptions());
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        mMockBuildInfo = new DeviceBuildInfo("0", "");
        mMockBuildInfo.setDeviceImageFile(new File("foo"), "0");
        mMockBuildInfo.setBuildFlavor("flavor");

        mFlashingMetricsReported = false;
        mDeviceFlashPreparer =
                new DeviceFlashPreparer() {
                    @Override
                    protected IDeviceFlasher createFlasher(ITestDevice device) {
                        return mMockFlasher;
                    }

                    @Override
                    int getDeviceBootPollTimeMs() {
                        return 100;
                    }

                    @Override
                    protected IHostOptions getHostOptions() {
                        return mMockHostOptions;
                    }

                    @Override
                    protected void reportFlashMetrics(
                            String branch,
                            String buildFlavor,
                            String buildId,
                            String serial,
                            long queueTime,
                            long flashingTime,
                            CommandStatus flashingStatus) {
                        mFlashingMetricsReported = true;
                    }
                };
        mSetter = new OptionSetter(mDeviceFlashPreparer);
        // Reset default settings
        mSetter.setOptionValue("device-boot-time", "100");
        // expect this call
        mMockFlasher.setUserDataFlashOption(UserDataFlashOption.FLASH);
        mTmpDir = FileUtil.createTempDir("tmp");
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTmpDir);
    }

    /** Simple normal case test for {@link DeviceFlashPreparer#setUp(TestInformation)}. */
    @Test
    public void testSetup() throws Exception {
        doSetupExpectations(mMockBuildInfo.getBuildFlavor());

        // report flashing success in normal case
        when(mMockFlasher.getSystemFlashingStatus()).thenReturn(CommandStatus.SUCCESS);

        mDeviceFlashPreparer.setUp(mTestInfo);

        verify(mMockFlasher).setShouldFlashRamdisk(false);
        assertTrue("should report flashing metrics in normal case", mFlashingMetricsReported);
    }

    @Test
    public void testSetup_trunk() throws Exception {
        mMockBuildInfo.setBuildFlavor("flavor-trunk_food-userdebug");
        doSetupExpectations("flavor-userdebug");

        // report flashing success in normal case
        when(mMockFlasher.getSystemFlashingStatus()).thenReturn(CommandStatus.SUCCESS);

        mDeviceFlashPreparer.setUp(mTestInfo);

        verify(mMockFlasher).setShouldFlashRamdisk(false);
        assertTrue("should report flashing metrics in normal case", mFlashingMetricsReported);
    }

    /** Set EasyMock expectations for a normal setup call */
    private void doSetupExpectations(String flavor)
            throws TargetSetupError, DeviceNotAvailableException {
        mMockDevice.setRecoveryMode(RecoveryMode.ONLINE);
        mMockFlasher.overrideDeviceOptions(mMockDevice);
        mMockFlasher.setForceSystemFlash(false);
        mMockFlasher.setDataWipeSkipList(Arrays.asList(new String[] {}));
        mMockFlasher.flash(mMockDevice, mMockBuildInfo);
        mMockFlasher.setWipeTimeout(Mockito.anyLong());
        mMockDevice.waitForDeviceOnline();
        when(mMockDevice.enableAdbRoot()).thenReturn(Boolean.TRUE);
        mMockDevice.setDate(null);
        when(mMockDevice.getBuildId()).thenReturn(mMockBuildInfo.getBuildId());
        when(mMockDevice.getBuildFlavor()).thenReturn(flavor);
        when(mMockDevice.isEncryptionSupported()).thenReturn(Boolean.TRUE);
        when(mMockDevice.isDeviceEncrypted()).thenReturn(Boolean.FALSE);
        mMockDevice.clearLogcat();
        when(mMockDevice.waitForDeviceAvailableInRecoverPath(Mockito.anyLong())).thenReturn(true);
        mMockDevice.setRecoveryMode(RecoveryMode.AVAILABLE);
        mMockDevice.postBootSetup();
    }

    /**
     * Test {@link DeviceFlashPreparer#setUp(TestInformation)} when a non IDeviceBuildInfo type is
     * provided.
     */
    @Test
    public void testSetUp_nonDevice() throws Exception {
        try {
            mTestInfo.getContext().addDeviceBuildInfo("device", mock(IBuildInfo.class));

            mDeviceFlashPreparer.setUp(mTestInfo);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test {@link DeviceFlashPreparer#setUp(TestInformation)} when ramdisk flashing is required via
     * parameter but not provided in build info
     */
    @Test
    public void testSetUp_noRamdisk() throws Exception {
        mSetter.setOptionValue("flash-ramdisk", "true");
        try {
            mDeviceFlashPreparer.setUp(mTestInfo);
            fail("HarnessRuntimeException not thrown");
        } catch (HarnessRuntimeException e) {
            // expected
        }
    }

    /** Test {@link DeviceFlashPreparer#setUp(TestInformation)} when build does not boot. */
    @Test
    public void testSetup_buildError() throws Exception {

        when(mMockDevice.enableAdbRoot()).thenReturn(Boolean.TRUE);

        when(mMockDevice.getBuildId()).thenReturn(mMockBuildInfo.getBuildId());
        when(mMockDevice.getBuildFlavor()).thenReturn(mMockBuildInfo.getBuildFlavor());
        when(mMockDevice.isEncryptionSupported()).thenReturn(Boolean.TRUE);
        when(mMockDevice.isDeviceEncrypted()).thenReturn(Boolean.FALSE);

        doThrow(new DeviceUnresponsiveException("foo", "fakeserial"))
                .when(mMockDevice)
                .waitForDeviceAvailable(Mockito.anyLong());

        when(mMockDevice.getDeviceDescriptor())
                .thenReturn(
                        new DeviceDescriptor(
                                "SERIAL",
                                false,
                                DeviceAllocationState.Available,
                                "unknown",
                                "unknown",
                                "unknown",
                                "unknown",
                                "unknown"));
        // report SUCCESS since device was flashed successfully (but didn't boot up)
        when(mMockFlasher.getSystemFlashingStatus()).thenReturn(CommandStatus.SUCCESS);

        try {
            mDeviceFlashPreparer.setUp(mTestInfo);
            fail("DeviceFlashPreparerTest not thrown");
        } catch (BuildError e) {
            // expected; use the general version to make absolutely sure that
            // DeviceFailedToBootError properly masquerades as a BuildError.
            assertTrue(e instanceof DeviceFailedToBootError);
        }

        verify(mMockDevice).setRecoveryMode(RecoveryMode.ONLINE);
        verify(mMockFlasher).overrideDeviceOptions(mMockDevice);
        verify(mMockFlasher).setForceSystemFlash(false);
        verify(mMockFlasher).setDataWipeSkipList(Arrays.asList(new String[] {}));
        verify(mMockFlasher).setShouldFlashRamdisk(false);
        verify(mMockFlasher).flash(mMockDevice, mMockBuildInfo);
        verify(mMockFlasher).setWipeTimeout(Mockito.anyLong());
        verify(mMockDevice).waitForDeviceOnline();
        verify(mMockDevice).setDate(null);
        verify(mMockDevice).clearLogcat();
        verify(mMockDevice).setRecoveryMode(RecoveryMode.AVAILABLE);
        assertTrue(
                "should report flashing metrics with device boot failure",
                mFlashingMetricsReported);
    }

    /**
     * Test {@link DeviceFlashPreparer#setUp(TestInformation)} when flashing step hits device
     * failure.
     */
    @Test
    public void testSetup_flashException() throws Exception {

        doThrow(new DeviceNotAvailableException("test", "serial"))
                .when(mMockFlasher)
                .flash(mMockDevice, mMockBuildInfo);

        // report exception
        when(mMockFlasher.getSystemFlashingStatus()).thenReturn(CommandStatus.EXCEPTION);

        try {
            mDeviceFlashPreparer.setUp(mTestInfo);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }

        verify(mMockDevice).setRecoveryMode(RecoveryMode.ONLINE);
        verify(mMockFlasher).overrideDeviceOptions(mMockDevice);
        verify(mMockFlasher).setForceSystemFlash(false);
        verify(mMockFlasher).setDataWipeSkipList(Arrays.asList(new String[] {}));
        verify(mMockFlasher).setShouldFlashRamdisk(false);
        verify(mMockFlasher).setWipeTimeout(Mockito.anyLong());
        assertTrue(
                "should report flashing metrics with device flash failure",
                mFlashingMetricsReported);
    }

    /**
     * Test {@link DeviceFlashPreparer#setUp(TestInformation)} when flashing of system partitions
     * are skipped.
     */
    @Test
    public void testSetup_flashSkipped() throws Exception {
        doSetupExpectations(mMockBuildInfo.getBuildFlavor());

        // report flashing status as null (for not flashing system partitions)
        when(mMockFlasher.getSystemFlashingStatus()).thenReturn(null);

        mDeviceFlashPreparer.setUp(mTestInfo);

        verify(mMockFlasher).setShouldFlashRamdisk(false);
        assertFalse("should not report flashing metrics in normal case", mFlashingMetricsReported);
    }

    /**
     * Verifies that the ramdisk flashing parameter is passed down to the device flasher
     *
     * @throws Exception
     */
    @Test
    public void testSetup_flashRamdisk() throws Exception {
        mSetter.setOptionValue("flash-ramdisk", "true");
        mMockBuildInfo.setRamdiskFile(new File("foo"), "0");
        doSetupExpectations(mMockBuildInfo.getBuildFlavor());
        // report flashing success in normal case
        when(mMockFlasher.getSystemFlashingStatus()).thenReturn(CommandStatus.SUCCESS);

        mDeviceFlashPreparer.setUp(mTestInfo);
        verify(mMockFlasher, times(1)).setRamdiskPartition("boot");
        verify(mMockFlasher).setShouldFlashRamdisk(true);
    }

    /**
     * Verifies that the ramdisk partition parameter is passed down to the device flasher
     *
     * @throws Exception
     */
    @Test
    public void testSetup_flashRamdiskWithRamdiskPartition() throws Exception {
        mSetter.setOptionValue("flash-ramdisk", "true");
        mSetter.setOptionValue("ramdisk-partition", "vendor_boot");
        mMockBuildInfo.setRamdiskFile(new File("foo"), "0");
        doSetupExpectations(mMockBuildInfo.getBuildFlavor());
        // report flashing success in normal case
        when(mMockFlasher.getSystemFlashingStatus()).thenReturn(CommandStatus.SUCCESS);

        mDeviceFlashPreparer.setUp(mTestInfo);
        verify(mMockFlasher, times(1)).setRamdiskPartition("vendor_boot");
        verify(mMockFlasher).setShouldFlashRamdisk(true);
    }
}
