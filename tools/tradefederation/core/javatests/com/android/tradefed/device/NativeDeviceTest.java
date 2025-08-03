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
package com.android.tradefed.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.FileListingService.FileEntry;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncException.SyncError;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.SyncService.ISyncProgressMonitor;
import com.android.ddmlib.TimeoutException;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.IWifiHelper.WifiConnectionResult;
import com.android.tradefed.device.NativeDevice.RebootMode;
import com.android.tradefed.host.HostOptions;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.ProcessInfo;
import com.android.tradefed.util.StreamUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.AdditionalAnswers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link NativeDevice}. */
@RunWith(JUnit4.class)
public class NativeDeviceTest {

    private static final String MOCK_DEVICE_SERIAL = "serial";
    private static final String FAKE_NETWORK_SSID = "FakeNet";
    private static final String FAKE_NETWORK_PASSWORD = "FakePass";

    @Mock IDevice mMockIDevice;
    private TestableAndroidNativeDevice mTestDevice;
    @Mock IDeviceRecovery mMockRecovery;
    @Mock IDeviceStateMonitor mMockStateMonitor;
    @Mock IRunUtil mMockRunUtil;
    @Mock IWifiHelper mMockWifi;
    @Mock IDeviceMonitor mMockDvcMonitor;
    private IHostOptions mHostOptions;

    /** A {@link TestDevice} that is suitable for running tests against */
    private class TestableAndroidNativeDevice extends NativeDevice {

        public boolean wasCalled = false;

        public TestableAndroidNativeDevice() {
            super(mMockIDevice, mMockStateMonitor, mMockDvcMonitor);
        }

        @Override
        public void postBootSetup() {
            // too annoying to mock out postBootSetup actions everyone, so do nothing
        }

        @Override
        protected IRunUtil getRunUtil() {
            return mMockRunUtil;
        }

        @Override
        IHostOptions getHostOptions() {
            return mHostOptions;
        }

        @Override
        public int getCurrentUser() throws DeviceNotAvailableException {
            return 0;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mHostOptions = new HostOptions();

        when(mMockIDevice.getSerialNumber()).thenReturn(MOCK_DEVICE_SERIAL);
        when(mMockIDevice.supportsFeature(IDevice.Feature.SHELL_V2)).thenReturn(true);

        // A TestDevice with a no-op recoverDevice() implementation
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean recoverDevice() throws DeviceNotAvailableException {
                        // ignore
                        return true;
                    }

                    @Override
                    public IDevice getIDevice() {
                        return mMockIDevice;
                    }

                    @Override
                    IWifiHelper createWifiHelper() {
                        return mMockWifi;
                    }

                    @Override
                    IWifiHelper createWifiHelper(boolean useV2) {
                        return mMockWifi;
                    }
                };
        mTestDevice.setRecovery(mMockRecovery);
        mTestDevice.setCommandTimeout(100);
        mTestDevice.setLogStartDelay(-1);
    }

    /**
     * Test return exception for package installation {@link NativeDevice#installPackage(File,
     * boolean, String...)}.
     */
    @Test
    public void testInstallPackages_exception() throws Exception {
        try {
            mTestDevice.installPackage(new File(""), false);
            fail("installPackage should have thrown an exception");
        } catch (UnsupportedOperationException expected) {
            // Expected
        }
    }

    /**
     * Test return exception for package installation {@link NativeDevice#uninstallPackage(String)}.
     */
    @Test
    public void testUninstallPackages_exception() throws Exception {
        try {
            mTestDevice.uninstallPackage("");
            fail("uninstallPackageForUser should have thrown an exception");
        } catch (UnsupportedOperationException expected) {
            // Expected
        }
    }

    /**
     * Test return exception for package installation {@link NativeDevice#installPackage(File,
     * boolean, boolean, String...)}.
     */
    @Test
    public void testInstallPackagesBool_exception() throws Exception {
        try {
            mTestDevice.installPackage(new File(""), false, false);
            fail("installPackage should have thrown an exception");
        } catch (UnsupportedOperationException expected) {
            // Expected
        }
    }

    /**
     * Test return exception for package installation {@link
     * NativeDevice#installPackageForUser(File, boolean, int, String...)}.
     */
    @Test
    public void testInstallPackagesForUser_exception() throws Exception {
        try {
            mTestDevice.installPackageForUser(new File(""), false, 0);
            fail("installPackageForUser should have thrown an exception");
        } catch (UnsupportedOperationException expected) {
            // Expected
        }
    }

    /**
     * Test return exception for package installation {@link
     * NativeDevice#installPackageForUser(File, boolean, boolean, int, String...)}.
     */
    @Test
    public void testInstallPackagesForUserWithPermission_exception() throws Exception {
        try {
            mTestDevice.installPackageForUser(new File(""), false, false, 0);
            fail("installPackageForUser should have thrown an exception");
        } catch (UnsupportedOperationException expected) {
            // Expected
        }
    }

    /** Unit test for {@link NativeDevice#getInstalledPackageNames()}. */
    @Test
    public void testGetInstalledPackageNames_exception() throws Exception {
        try {
            mTestDevice.getInstalledPackageNames();
            fail("getInstalledPackageNames should have thrown an exception");
        } catch (UnsupportedOperationException expected) {
            // Expected
        }
    }

    /** Unit test for {@link NativeDevice#getActiveApexes()}. */
    @Test
    public void testGetActiveApexes_exception() throws Exception {
        try {
            mTestDevice.getActiveApexes();
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getActiveApexes should have thrown an exception");
    }

    /** Unit test for {@link NativeDevice#getMainlineModuleInfo()}. */
    @Test
    public void testGetMainlineModuleInfo_exception() throws Exception {
        try {
            mTestDevice.getMainlineModuleInfo();
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getMainlineModuleInfo should have thrown an exception");
    }

    /** Unit test for {@link NativeDevice#getScreenshot()}. */
    @Test
    public void testGetScreenshot_exception() throws Exception {
        try {
            mTestDevice.getScreenshot();
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getScreenshot should have thrown an exception");
    }

    /** Unit test for {@link NativeDevice#pushDir(File, String)}. */
    @Test
    public void testPushDir_notADir() throws Exception {
        assertFalse(mTestDevice.pushDir(new File(""), ""));
    }

    /** Unit test for {@link NativeDevice#pushDir(File, String)}. */
    @Test
    public void testPushDir_childFile() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean pushFileInternal(
                            File localFile, String remoteFilePath, boolean skipContentProvider)
                            throws DeviceNotAvailableException {
                        return true;
                    }
                };
        File testDir = FileUtil.createTempDir("pushDirTest");
        FileUtil.createTempFile("test1", ".txt", testDir);
        assertTrue(mTestDevice.pushDir(testDir, ""));
        FileUtil.recursiveDelete(testDir);
    }

    /** Unit test for {@link NativeDevice#pushDir(File, String)}. */
    @Test
    public void testPushDir_childDir() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String cmd)
                            throws DeviceNotAvailableException {
                        return "";
                    }

                    @Override
                    public boolean pushFile(File localFile, String remoteFilePath)
                            throws DeviceNotAvailableException {
                        return false;
                    }
                };
        File testDir = FileUtil.createTempDir("pushDirTest");
        File subDir = FileUtil.createTempDir("testSubDir", testDir);
        FileUtil.createTempDir("test1", subDir);
        assertTrue(mTestDevice.pushDir(testDir, ""));
        FileUtil.recursiveDelete(testDir);
    }

    /** Unit test for {@link NativeDevice#pushDir(File, String, Set)}. */
    @Test
    public void testPushDir_childDir_filtered() throws Exception {
        File testDir = FileUtil.createTempDir("pushDirTest");
        Set<String> filter = new HashSet<>();
        try {
            File subDir = FileUtil.createTempDir("testSubDir", testDir);
            File childDir = FileUtil.createTempDir("test1", subDir);
            filter.add(childDir.getName());
            mTestDevice =
                    new TestableAndroidNativeDevice() {
                        @Override
                        public String executeShellCommand(String cmd)
                                throws DeviceNotAvailableException {
                            // Ensure we never attempt to push the excluded child.
                            assertFalse(cmd.contains(childDir.getName()));
                            return "";
                        }

                        @Override
                        public boolean pushFile(File localFile, String remoteFilePath)
                                throws DeviceNotAvailableException {
                            return false;
                        }
                    };

            assertTrue(mTestDevice.pushDir(testDir, "/", filter));
        } finally {
            FileUtil.recursiveDelete(testDir);
        }
    }

    /** Test {@link NativeDevice#pullDir(String, File)} when the remote directory is empty. */
    @Test
    public void testPullDir_nothingToDo() throws Exception {
        final IFileEntry fakeEntry = mock(IFileEntry.class);
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public IFileEntry getFileEntry(FileEntry path)
                            throws DeviceNotAvailableException {
                        return fakeEntry;
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "drwxr-xr-x root     root    somedirectory";
                    }

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return 0;
                    }
                };
        File dir = FileUtil.createTempDir("tf-test");
        Collection<IFileEntry> childrens = new ArrayList<>();
        when(fakeEntry.getChildren(false)).thenReturn(childrens);
        // Empty list of childen

        try {
            boolean res = mTestDevice.pullDir("/some_device_path/screenshots/", dir);
            assertTrue(res);
            assertTrue(dir.list().length == 0);
        } finally {
            FileUtil.recursiveDelete(dir);
        }
    }

    /**
     * Test {@link NativeDevice#pullDir(String, File)} when the remote directory has a file and a
     * directory.
     */
    @Test
    public void testPullDir() throws Exception {
        final IFileEntry fakeEntry = mock(IFileEntry.class);
        final IFileEntry fakeDir = mock(IFileEntry.class);
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    private boolean mFirstCall = true;

                    @Override
                    public IFileEntry getFileEntry(FileEntry path)
                            throws DeviceNotAvailableException {
                        if (mFirstCall) {
                            mFirstCall = false;
                            return fakeEntry;
                        } else {
                            return fakeDir;
                        }
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "drwxr-xr-x root     root    somedirectory";
                    }

                    @Override
                    protected boolean pullFileInternal(String remoteFilePath, File localFile)
                            throws DeviceNotAvailableException {
                        try {
                            // Just touch the file to make it appear.
                            localFile.createNewFile();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return true;
                    }

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return 0;
                    }
                };
        File dir = FileUtil.createTempDir("tf-test");
        Collection<IFileEntry> children = new ArrayList<>();
        IFileEntry fakeFile = mock(IFileEntry.class);
        children.add(fakeFile);
        when(fakeFile.isDirectory()).thenReturn(false);
        when(fakeFile.getName()).thenReturn("fakeFile");
        when(fakeFile.getFullPath()).thenReturn("/some_device_path/fakeFile");

        children.add(fakeDir);
        when(fakeDir.isDirectory()).thenReturn(true);
        when(fakeDir.getName()).thenReturn("fakeDir");
        when(fakeDir.getFullPath()).thenReturn("/some_device_path/fakeDir");
        // #pullDir is being called on dir fakeDir to pull everything recursively.
        Collection<IFileEntry> fakeDirChildren = new ArrayList<>();
        when(fakeDir.getChildren(false)).thenReturn(fakeDirChildren);
        when(fakeEntry.getChildren(false)).thenReturn(children);

        try {
            boolean res = mTestDevice.pullDir("/some_device_path/", dir);
            assertTrue(res);
            assertEquals(2, dir.list().length);
            assertTrue(Arrays.asList(dir.list()).contains("fakeFile"));
            assertTrue(Arrays.asList(dir.list()).contains("fakeDir"));
        } finally {
            FileUtil.recursiveDelete(dir);
        }
    }

    /** Test pulling a directory when one of the pull fails. */
    @Test
    public void testPullDir_pullFail() throws Exception {
        final IFileEntry fakeEntry = mock(IFileEntry.class);
        final IFileEntry fakeDir = mock(IFileEntry.class);
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    private boolean mFirstCall = true;
                    private boolean mFirstPull = true;

                    @Override
                    public IFileEntry getFileEntry(FileEntry path)
                            throws DeviceNotAvailableException {
                        if (mFirstCall) {
                            mFirstCall = false;
                            return fakeEntry;
                        } else {
                            return fakeDir;
                        }
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "drwxr-xr-x root     root    somedirectory";
                    }

                    @Override
                    protected boolean pullFileInternal(String remoteFilePath, File localFile)
                            throws DeviceNotAvailableException {
                        if (mFirstPull) {
                            mFirstPull = false;
                            try {
                                // Just touch the file to make it appear.
                                localFile.createNewFile();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return true;
                        } else {
                            return false;
                        }
                    }

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return 0;
                    }
                };
        File dir = FileUtil.createTempDir("tf-test");
        Collection<IFileEntry> children = new ArrayList<>();
        IFileEntry fakeFile = mock(IFileEntry.class);
        children.add(fakeFile);
        when(fakeFile.isDirectory()).thenReturn(false);
        when(fakeFile.getName()).thenReturn("fakeFile");
        when(fakeFile.getFullPath()).thenReturn("/some_device_path/fakeFile");

        children.add(fakeDir);
        when(fakeDir.isDirectory()).thenReturn(true);
        when(fakeDir.getName()).thenReturn("fakeDir");
        when(fakeDir.getFullPath()).thenReturn("/some_device_path/fakeDir");
        // #pullDir is being called on dir fakeDir to pull everything recursively.
        Collection<IFileEntry> fakeDirChildren = new ArrayList<>();
        IFileEntry secondLevelChildren = mock(IFileEntry.class);
        fakeDirChildren.add(secondLevelChildren);
        when(fakeDir.getChildren(false)).thenReturn(fakeDirChildren);
        when(fakeEntry.getChildren(false)).thenReturn(children);

        when(secondLevelChildren.isDirectory()).thenReturn(false);
        when(secondLevelChildren.getName()).thenReturn("secondLevelChildren");
        when(secondLevelChildren.getFullPath())
                .thenReturn("/some_device_path/fakeDir/secondLevelChildren");

        try {
            boolean res = mTestDevice.pullDir("/some_device_path/", dir);
            // If one of the pull fails, the full command is considered failed.
            assertFalse(res);
            assertEquals(2, dir.list().length);
            assertTrue(Arrays.asList(dir.list()).contains("fakeFile"));
            // The subdir was created
            assertTrue(Arrays.asList(dir.list()).contains("fakeDir"));
            // The last file failed to pull, so the dir is empty.
            assertEquals(0, new File(dir, "fakeDir").list().length);
        } finally {
            FileUtil.recursiveDelete(dir);
        }
    }

    /**
     * Test that if the requested path is not a directory on the device side, we just fail directly.
     */
    @Test
    public void testPullDir_invalidPath() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "-rwxr-xr-x root     root    somefile";
                    }

                    @Override
                    public int getCurrentUser() throws DeviceNotAvailableException {
                        return 0;
                    }
                };
        File dir = FileUtil.createTempDir("tf-test");
        try {
            assertFalse(mTestDevice.pullDir("somefile", dir));
            assertTrue(dir.list().length == 0);
        } finally {
            FileUtil.recursiveDelete(dir);
        }
    }

    /** Unit test for {@link NativeDevice#getCurrentUser()}. */
    @Test
    public void testGetCurrentUser_exception() throws Exception {
        try {
            mTestDevice.getScreenshot();
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getCurrentUser should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#getUserFlags(int)}. */
    @Test
    public void testGetUserFlags_exception() throws Exception {
        try {
            mTestDevice.getUserFlags(0);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getUserFlags should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#getUserSerialNumber(int)}. */
    @Test
    public void testGetUserSerialNumber_exception() throws Exception {
        try {
            mTestDevice.getUserSerialNumber(0);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getUserSerialNumber should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#switchUser(int)}. */
    @Test
    public void testSwitchUser_exception() throws Exception {
        try {
            mTestDevice.switchUser(10);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("switchUser should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#switchUser(int, long)}. */
    @Test
    public void testSwitchUserTimeout_exception() throws Exception {
        try {
            mTestDevice.switchUser(10, 5 * 1000);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("switchUser should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#stopUser(int)}. */
    @Test
    public void testStopUser_exception() throws Exception {
        try {
            mTestDevice.stopUser(0);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("stopUser should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#stopUser(int, boolean, boolean)}. */
    @Test
    public void testStopUserFlags_exception() throws Exception {
        try {
            mTestDevice.stopUser(0, true, true);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("stopUser should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#startUser(int, boolean)}. */
    @Test
    public void testStartUserFlags_exception() throws Exception {
        try {
            mTestDevice.startUser(0, true);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("startUser should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#isUserRunning(int)}. */
    @Test
    public void testIsUserIdRunning_exception() throws Exception {
        try {
            mTestDevice.isUserRunning(0);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("stopUser should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#hasFeature(String)}. */
    @Test
    public void testHasFeature_exception() throws Exception {
        try {
            mTestDevice.hasFeature("feature:test");
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("hasFeature should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#getSetting(String, String)}. */
    @Test
    public void testGetSettingSystemUser_exception() throws Exception {
        try {
            mTestDevice.getSetting("global", "wifi_on");
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getSettings should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#getSetting(int, String, String)}. */
    @Test
    public void testGetSetting_exception() throws Exception {
        try {
            mTestDevice.getSetting(0, "global", "wifi_on");
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getSettings should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#getAllSettings(String)}. */
    @Test
    public void testGetAllSettingsSystemUser_exception() throws Exception {
        try {
            mTestDevice.getAllSettings("global");
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getAllSettings should have thrown an exception");
    }

    /** Unit test for {@link NativeDevice#setSetting(String, String, String)}. */
    @Test
    public void testSetSettingSystemUser_exception() throws Exception {
        try {
            mTestDevice.setSetting("global", "wifi_on", "0");
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("putSettings should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#setSetting(int, String, String, String)}. */
    @Test
    public void testSetSetting_exception() throws Exception {
        try {
            mTestDevice.setSetting(0, "global", "wifi_on", "0");
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("putSettings should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#getAndroidId(int)}. */
    @Test
    public void testGetAndroidId_exception() throws Exception {
        try {
            mTestDevice.getAndroidId(0);
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getAndroidId should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#getAndroidIds()}. */
    @Test
    public void testGetAndroidIds_exception() throws Exception {
        try {
            mTestDevice.getAndroidIds();
        } catch (UnsupportedOperationException onse) {
            return;
        }
        fail("getAndroidIds should have thrown an exception.");
    }

    /** Unit test for {@link NativeDevice#connectToWifiNetworkIfNeeded(String, String)}. */
    @Test
    public void testConnectToWifiNetworkIfNeeded_alreadyConnected()
            throws DeviceNotAvailableException {
        when(mMockWifi.checkConnectivity(mTestDevice.getOptions().getConnCheckUrl()))
                .thenReturn(true);

        assertTrue(
                mTestDevice.connectToWifiNetworkIfNeeded(FAKE_NETWORK_SSID, FAKE_NETWORK_PASSWORD));
    }

    /** Unit test for {@link NativeDevice#connectToWifiNetwork(String, String)}. */
    @Test
    public void testConnectToWifiNetwork_success() throws DeviceNotAvailableException {
        when(mMockWifi.connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        false,
                        "wpa2"))
                .thenReturn(WifiConnectionResult.SUCCESS);
        Map<String, String> fakeWifiInfo = new HashMap<String, String>();
        fakeWifiInfo.put("bssid", FAKE_NETWORK_SSID);
        when(mMockWifi.getWifiInfo()).thenReturn(fakeWifiInfo);

        assertTrue(mTestDevice.connectToWifiNetwork(FAKE_NETWORK_SSID, FAKE_NETWORK_PASSWORD));
    }

    /** Unit test for {@link NativeDevice#connectToWifiNetwork(Map<String, String>)}. */
    @Test
    public void testConnectToWifiNetworkGivenMap_success() throws DeviceNotAvailableException {
        when(mMockWifi.connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        false,
                        "wpa2"))
                .thenReturn(WifiConnectionResult.SUCCESS);
        Map<String, String> fakeWifiInfo = new HashMap<>();
        fakeWifiInfo.put("bssid", FAKE_NETWORK_SSID);
        when(mMockWifi.getWifiInfo()).thenReturn(fakeWifiInfo);

        LinkedHashMap<String, String> ssidToPsk = new LinkedHashMap<>();
        ssidToPsk.put(FAKE_NETWORK_SSID, FAKE_NETWORK_PASSWORD);
        assertTrue(mTestDevice.connectToWifiNetwork(ssidToPsk));
    }

    /**
     * Unit test for {@link NativeDevice#connectToWifiNetwork(String, String)} for a failure to
     * connect case.
     */
    @Test
    public void testConnectToWifiNetwork_failure() throws DeviceNotAvailableException {
        when(mMockWifi.connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        false,
                        "wpa2"))
                .thenReturn(WifiConnectionResult.FAILED_TO_CONNECT);
        Map<String, String> fakeWifiInfo = new HashMap<String, String>();
        fakeWifiInfo.put("bssid", FAKE_NETWORK_SSID);
        when(mMockWifi.getWifiInfo()).thenReturn(fakeWifiInfo);

        assertFalse(mTestDevice.connectToWifiNetwork(FAKE_NETWORK_SSID, FAKE_NETWORK_PASSWORD));
        verify(mMockRunUtil, times(mTestDevice.getOptions().getWifiAttempts() - 1))
                .sleep(Mockito.anyLong());
        verify(mMockWifi, times(mTestDevice.getOptions().getWifiAttempts()))
                .connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        false,
                        "wpa2");
        verify(mMockWifi, times(mTestDevice.getOptions().getWifiAttempts())).getWifiInfo();
    }

    /**
     * Unit test for {@link NativeDevice#connectToWifiNetwork(Map<String, String>)} for a failure to
     * connect case.
     */
    @Test
    public void testConnectToWifiNetworkGivenMap_failure() throws DeviceNotAvailableException {
        when(mMockWifi.connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        false,
                        "wpa2"))
                .thenReturn(WifiConnectionResult.FAILED_TO_CONNECT);
        Map<String, String> fakeWifiInfo = new HashMap<>();
        fakeWifiInfo.put("bssid", FAKE_NETWORK_SSID);
        when(mMockWifi.getWifiInfo()).thenReturn(fakeWifiInfo);

        LinkedHashMap<String, String> ssidToPsk = new LinkedHashMap<>();
        ssidToPsk.put(FAKE_NETWORK_SSID, FAKE_NETWORK_PASSWORD);
        assertFalse(mTestDevice.connectToWifiNetwork(ssidToPsk));
        verify(mMockRunUtil, times(mTestDevice.getOptions().getWifiAttempts() - 1))
                .sleep(Mockito.anyLong());
        verify(mMockWifi, times(mTestDevice.getOptions().getWifiAttempts()))
                .connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        false,
                        "wpa2");
        verify(mMockWifi, times(mTestDevice.getOptions().getWifiAttempts())).getWifiInfo();
    }

    /**
     * Unit test for {@link NativeDevice#connectToWifiNetwork(String, String)} for limiting the time
     * trying to connect to wifi.
     */
    @Test
    public void testConnectToWifiNetwork_maxConnectTime()
            throws DeviceNotAvailableException, ConfigurationException {
        OptionSetter deviceOptionSetter = new OptionSetter(mTestDevice.getOptions());
        deviceOptionSetter.setOptionValue("max-wifi-connect-time", "10000");
        Clock mockClock = Mockito.mock(Clock.class);
        mTestDevice.setClock(mockClock);
        when(mMockWifi.connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        false,
                        "wpa2"))
                .thenReturn(WifiConnectionResult.FAILED_TO_CONNECT);
        Mockito.when(mockClock.millis())
                .thenReturn(Long.valueOf(0), Long.valueOf(6000), Long.valueOf(12000));
        Map<String, String> fakeWifiInfo = new HashMap<String, String>();
        fakeWifiInfo.put("bssid", FAKE_NETWORK_SSID);
        when(mMockWifi.getWifiInfo()).thenReturn(fakeWifiInfo);

        assertFalse(mTestDevice.connectToWifiNetwork(FAKE_NETWORK_SSID, FAKE_NETWORK_PASSWORD));
        verify(mMockWifi, times(2))
                .connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        false,
                        "wpa2");
        verify(mMockWifi, times(2)).getWifiInfo();
        Mockito.verify(mockClock, Mockito.times(4)).millis();
    }

    /**
     * Unit test for {@link NativeDevice#connectToWifiNetwork(Map<String, String>)} for limiting the
     * time trying to connect to wifi.
     */
    @Test
    public void testConnectToWifiNetworkGivenMap_maxConnectTime()
            throws DeviceNotAvailableException, ConfigurationException {
        OptionSetter deviceOptionSetter = new OptionSetter(mTestDevice.getOptions());
        deviceOptionSetter.setOptionValue("max-wifi-connect-time", "10000");
        Clock mockClock = Mockito.mock(Clock.class);
        mTestDevice.setClock(mockClock);
        when(mMockWifi.connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        false,
                        "wpa2"))
                .thenReturn(WifiConnectionResult.FAILED_TO_CONNECT);
        Mockito.when(mockClock.millis())
                .thenReturn(Long.valueOf(0), Long.valueOf(6000), Long.valueOf(12000));
        Map<String, String> fakeWifiInfo = new HashMap<String, String>();
        fakeWifiInfo.put("bssid", FAKE_NETWORK_SSID);
        when(mMockWifi.getWifiInfo()).thenReturn(fakeWifiInfo);

        LinkedHashMap<String, String> ssidToPsk = new LinkedHashMap<>();
        ssidToPsk.put(FAKE_NETWORK_SSID, FAKE_NETWORK_PASSWORD);
        assertFalse(mTestDevice.connectToWifiNetwork(ssidToPsk));
        verify(mMockWifi, times(2))
                .connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        false,
                        "wpa2");
        verify(mMockWifi, times(2)).getWifiInfo();
        Mockito.verify(mockClock, Mockito.times(4)).millis();
    }

    /** Unit test for {@link NativeDevice#connectToWifiNetwork(String, String, boolean)}. */
    @Test
    public void testConnectToWifiNetwork_scanSsid() throws DeviceNotAvailableException {
        when(mMockWifi.connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        true,
                        "wpa2"))
                .thenReturn(WifiConnectionResult.SUCCESS);
        Map<String, String> fakeWifiInfo = new HashMap<String, String>();
        fakeWifiInfo.put("bssid", FAKE_NETWORK_SSID);
        when(mMockWifi.getWifiInfo()).thenReturn(fakeWifiInfo);

        assertTrue(
                mTestDevice.connectToWifiNetwork(FAKE_NETWORK_SSID, FAKE_NETWORK_PASSWORD, true));
    }

    /** Unit test for {@link NativeDevice#connectToWifiNetwork(Map<String, String>, boolean)}. */
    @Test
    public void testConnectToWifiNetworkGivenMap_scanSsid() throws DeviceNotAvailableException {
        when(mMockWifi.connectToNetwork(
                        FAKE_NETWORK_SSID,
                        FAKE_NETWORK_PASSWORD,
                        mTestDevice.getOptions().getConnCheckUrl(),
                        true,
                        "wpa2"))
                .thenReturn(WifiConnectionResult.SUCCESS);
        Map<String, String> fakeWifiInfo = new HashMap<>();
        fakeWifiInfo.put("bssid", FAKE_NETWORK_SSID);
        when(mMockWifi.getWifiInfo()).thenReturn(fakeWifiInfo);

        LinkedHashMap<String, String> ssidToPsk = new LinkedHashMap<>();
        ssidToPsk.put(FAKE_NETWORK_SSID, FAKE_NETWORK_PASSWORD);
        assertTrue(mTestDevice.connectToWifiNetwork(ssidToPsk, true));
    }

    /** Unit test for {@link NativeDevice#checkWifiConnection(String)}. */
    @Test
    public void testCheckWifiConnection() throws DeviceNotAvailableException {
        when(mMockWifi.isWifiEnabled()).thenReturn(true);
        when(mMockWifi.getSSID()).thenReturn("\"" + FAKE_NETWORK_SSID + "\"");
        when(mMockWifi.hasValidIp()).thenReturn(true);
        when(mMockWifi.checkConnectivity(mTestDevice.getOptions().getConnCheckUrl()))
                .thenReturn(true);

        assertTrue(mTestDevice.checkWifiConnection(FAKE_NETWORK_SSID));
    }

    /** Unit test for {@link NativeDevice#checkWifiConnection(String)} for a failure. */
    @Test
    public void testCheckWifiConnection_failure() throws DeviceNotAvailableException {
        when(mMockWifi.isWifiEnabled()).thenReturn(false);

        assertFalse(mTestDevice.checkWifiConnection(FAKE_NETWORK_SSID));
    }

    /** Unit test for {@link NativeDevice#isWifiEnabled()}. */
    @Test
    public void testIsWifiEnabled() throws DeviceNotAvailableException {
        when(mMockWifi.isWifiEnabled()).thenReturn(true);

        assertTrue(mTestDevice.isWifiEnabled());
    }

    /**
     * Unit test for {@link NativeDevice#isWifiEnabled()} with runtime exception from wifihelper.
     */
    @Test
    public void testIsWifiEnabled_exception() throws DeviceNotAvailableException {
        when(mMockWifi.isWifiEnabled()).thenThrow(new RuntimeException());

        assertFalse(mTestDevice.isWifiEnabled());
    }

    /** Unit test for {@link NativeDevice#disconnectFromWifi()}. */
    @Test
    public void testDisconnectFromWifi() throws DeviceNotAvailableException {
        when(mMockWifi.disconnectFromNetwork()).thenReturn(true);

        assertTrue(mTestDevice.disconnectFromWifi());
    }

    /** Unit test for {@link NativeDevice#enableNetworkMonitor()}. */
    @Test
    public void testEnableNetworkMonitor() throws DeviceNotAvailableException {
        when(mMockWifi.stopMonitor()).thenReturn(null);
        when(mMockWifi.startMonitor(
                        Mockito.anyLong(), Mockito.eq(mTestDevice.getOptions().getConnCheckUrl())))
                .thenReturn(true);

        assertTrue(mTestDevice.enableNetworkMonitor());
    }

    /** Unit test for {@link NativeDevice#enableNetworkMonitor()} in case of failure. */
    @Test
    public void testEnableNetworkMonitor_failure() throws DeviceNotAvailableException {
        when(mMockWifi.stopMonitor()).thenReturn(null);
        when(mMockWifi.startMonitor(
                        Mockito.anyLong(), Mockito.eq(mTestDevice.getOptions().getConnCheckUrl())))
                .thenReturn(false);

        assertFalse(mTestDevice.enableNetworkMonitor());
    }

    /** Unit test for {@link NativeDevice#disableNetworkMonitor()}. */
    @Test
    public void testDisableNetworkMonitor() throws DeviceNotAvailableException {
        List<Long> samples = new ArrayList<Long>();
        samples.add(Long.valueOf(42));
        samples.add(Long.valueOf(256));
        samples.add(Long.valueOf(-1)); // failure to connect
        when(mMockWifi.stopMonitor()).thenReturn(samples);

        assertTrue(mTestDevice.disableNetworkMonitor());
    }

    /** Unit test for {@link NativeDevice#reconnectToWifiNetwork()}. */
    @Test
    public void testReconnectToWifiNetwork() throws DeviceNotAvailableException {
        when(mMockWifi.checkConnectivity(mTestDevice.getOptions().getConnCheckUrl()))
                .thenReturn(false, true);

        try {
            mTestDevice.reconnectToWifiNetwork();
        } finally {
            verify(mMockRunUtil, times(1)).sleep(Mockito.anyLong());
        }
    }

    /** Unit test for {@link NativeDevice#isHeadless()}. */
    @Test
    public void testIsHeadless() throws DeviceNotAvailableException {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "1\n";
                    }
                };
        assertTrue(mTestDevice.isHeadless());
    }

    /** Unit test for {@link NativeDevice#isHeadless()}. */
    @Test
    public void testIsHeadless_notHeadless() throws DeviceNotAvailableException {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return null;
                    }
                };
        assertFalse(mTestDevice.isHeadless());
    }

    /** Unit test for {@link NativeDevice#getDeviceDate()}. */
    @Test
    public void testGetDeviceDate() throws DeviceNotAvailableException {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String name)
                            throws DeviceNotAvailableException {
                        return "21692641\n";
                    }
                };
        assertEquals(21692641000L, mTestDevice.getDeviceDate());
    }

    /** Unit test for {@link NativeDevice#getDeviceDate()}. */
    @Test
    public void testGetDeviceDate_wrongformat() throws DeviceNotAvailableException {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String name)
                            throws DeviceNotAvailableException {
                        return "WRONG\n";
                    }
                };
        assertEquals(0, mTestDevice.getDeviceDate());
    }

    /**
     * Test that we can distinguish a newer file even with Timezone on the device. Seoul is GMT+9.
     */
    @Test
    public void testIsNewer() throws Exception {
        TestableAndroidNativeDevice testDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "Asia/Seoul";
                    }

                    @Override
                    public long getDeviceTimeOffset(Date date) throws DeviceNotAvailableException {
                        return 0;
                    }
                };
        File localFile = FileUtil.createTempFile("timezonetest", ".txt");
        try {
            localFile.setLastModified(1470906000000L); // Thu Aug 11 09:00:00 GMT 2016
            IFileEntry remoteFile = mock(IFileEntry.class);
            when(remoteFile.getDate()).thenReturn("2016-08-11");
            when(remoteFile.getTime()).thenReturn("18:00");

            assertTrue(testDevice.isNewer(localFile, remoteFile));
        } finally {
            FileUtil.deleteFile(localFile);
        }
    }

    /**
     * Test that we can distinguish a newer file even with Timezone on the device. Seoul is GMT+9.
     * Clock on device is inaccurate and in advance of host.
     */
    @Test
    public void testIsNewer_timeOffset() throws Exception {
        TestableAndroidNativeDevice testDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "Asia/Seoul";
                    }

                    @Override
                    public long getDeviceTimeOffset(Date date) throws DeviceNotAvailableException {
                        return -15 * 60 * 1000; // Device in advance of 15m on host.
                    }
                };
        File localFile = FileUtil.createTempFile("timezonetest", ".txt");
        try {
            localFile.setLastModified(1470906000000L); // Thu, 11 Aug 2016 09:00:00 GMT
            IFileEntry remoteFile = mock(IFileEntry.class);
            when(remoteFile.getDate()).thenReturn("2016-08-11");
            when(remoteFile.getTime()).thenReturn("18:15");

            // Should sync because after time offset correction, file is older.
            assertTrue(testDevice.isNewer(localFile, remoteFile));
        } finally {
            FileUtil.deleteFile(localFile);
        }
    }

    /**
     * Test that we can distinguish a newer file even with Timezone on the device. Seoul is GMT+9.
     * Local file is set to 10min earlier than remoteFile.
     */
    @Test
    public void testIsNewer_fails() throws Exception {
        TestableAndroidNativeDevice testDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "Asia/Seoul";
                    }

                    @Override
                    public long getDeviceTimeOffset(Date date) throws DeviceNotAvailableException {
                        return 0;
                    }
                };
        File localFile = FileUtil.createTempFile("timezonetest", ".txt");
        try {
            localFile.setLastModified(1470906000000L); // Thu, 11 Aug 2016 09:00:00 GMT
            IFileEntry remoteFile = mock(IFileEntry.class);
            when(remoteFile.getDate()).thenReturn("2016-08-11");
            when(remoteFile.getTime()).thenReturn("18:10");

            assertFalse(testDevice.isNewer(localFile, remoteFile));
        } finally {
            FileUtil.deleteFile(localFile);
        }
    }

    /** Unit test for {@link NativeDevice#getBuildAlias()}. */
    @Test
    public void testGetBuildAlias() throws DeviceNotAvailableException {
        final String alias = "alias\n";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return alias;
                    }
                };
        assertEquals(alias, mTestDevice.getBuildAlias());
    }

    /** Unit test for {@link NativeDevice#getBuildAlias()}. */
    @Test
    public void testGetBuildAlias_null() throws DeviceNotAvailableException {
        final String alias = null;
        final String buildId = "alias\n";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return alias;
                    }

                    @Override
                    public String getBuildId() throws DeviceNotAvailableException {
                        return buildId;
                    }
                };
        assertEquals(buildId, mTestDevice.getBuildAlias());
    }

    /** Unit test for {@link NativeDevice#getBuildAlias()}. */
    @Test
    public void testGetBuildAlias_empty() throws DeviceNotAvailableException {
        final String alias = "";
        final String buildId = "alias\n";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return alias;
                    }

                    @Override
                    public String getBuildId() throws DeviceNotAvailableException {
                        return buildId;
                    }
                };
        assertEquals(buildId, mTestDevice.getBuildAlias());
    }

    /** Unit test for {@link NativeDevice#getBuildId()}. */
    @Test
    public void testGetBuildId() throws DeviceNotAvailableException {
        final String buildId = "299865";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return buildId;
                    }
                };
        assertEquals(buildId, mTestDevice.getBuildId());
    }

    /** Unit test for {@link NativeDevice#getBuildId()}. */
    @Test
    public void testGetBuildId_null() throws DeviceNotAvailableException {
        final String buildId = null;
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return buildId;
                    }
                };
        assertEquals(IBuildInfo.UNKNOWN_BUILD_ID, mTestDevice.getBuildId());
    }

    /** Unit test for {@link NativeDevice#getBuildFlavor()}. */
    @Test
    public void testGetBuildFlavor() throws DeviceNotAvailableException {
        final String flavor = "ham-user";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return flavor;
                    }
                };
        assertEquals(flavor, mTestDevice.getBuildFlavor());
    }

    /** Unit test for {@link NativeDevice#getBuildFlavor()}. */
    @Test
    public void testGetBuildFlavor_null_flavor() throws DeviceNotAvailableException {
        final String productName = "prod";
        final String buildType = "buildtype";
        String expected = String.format("%s-%s", productName, buildType);
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        if ("ro.build.flavor".equals(name)) {
                            return null;
                        } else if ("ro.product.name".equals(name)) {
                            return productName;
                        } else if ("ro.build.type".equals(name)) {
                            return buildType;
                        } else {
                            return null;
                        }
                    }
                };
        assertEquals(expected, mTestDevice.getBuildFlavor());
    }

    /** Unit test for {@link NativeDevice#getBuildFlavor()}. */
    @Test
    public void testGetBuildFlavor_null() throws DeviceNotAvailableException {
        final String productName = null;
        final String buildType = "buildtype";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        if ("ro.build.flavor".equals(name)) {
                            return "";
                        } else if ("ro.product.name".equals(name)) {
                            return productName;
                        } else if ("ro.build.type".equals(name)) {
                            return buildType;
                        } else {
                            return null;
                        }
                    }
                };
        assertNull(mTestDevice.getBuildFlavor());
    }

    /** Unit test for {@link NativeDevice#doAdbReboot(RebootMode, String)} )}. */
    @Test
    public void testDoAdbReboot_emulator() throws Exception {
        final String into = "bootloader";

        mTestDevice.doAdbReboot(RebootMode.REBOOT_INTO_BOOTLOADER, "");
        verify(mMockIDevice, times(1)).reboot(into);
    }

    /** Unit test for {@link NativeDevice#doReboot(RebootMode, String)} ()}. */
    @Test
    public void testDoReboot() throws Exception {
        NativeDevice testDevice =
                new NativeDevice(mMockIDevice, mMockStateMonitor, mMockDvcMonitor) {
                    @Override
                    public TestDeviceState getDeviceState() {
                        return TestDeviceState.ONLINE;
                    }
                };

        when(mMockStateMonitor.waitForDeviceNotAvailable(Mockito.anyLong())).thenReturn(true);

        testDevice.doReboot(RebootMode.REBOOT_FULL, null);
        verify(mMockIDevice, times(1)).reboot(null);
    }

    /** Unit test for {@link NativeDevice#doReboot(RebootMode, String)} ()}. */
    @Test
    public void testDoReboot_skipped() throws Exception {
        NativeDevice testDevice =
                new NativeDevice(mMockIDevice, mMockStateMonitor, mMockDvcMonitor) {
                    @Override
                    public TestDeviceState getDeviceState() {
                        mOptions =
                                new TestDeviceOptions() {
                                    @Override
                                    public boolean shouldDisableReboot() {
                                        return true;
                                    }
                                };
                        return TestDeviceState.ONLINE;
                    }
                };

        testDevice.doReboot(RebootMode.REBOOT_FULL, null);
    }

    /** Unit test for {@link NativeDevice#doReboot(RebootMode, String)} ()}. */
    @Test
    public void testDoReboot_fastboot() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public TestDeviceState getDeviceState() {
                        return TestDeviceState.FASTBOOT;
                    }

                    @Override
                    public CommandResult executeFastbootCommand(String... cmdArgs)
                            throws DeviceNotAvailableException, UnsupportedOperationException {
                        wasCalled = true;
                        return new CommandResult();
                    }
                };

        mTestDevice.doReboot(RebootMode.REBOOT_FULL, null);
        assertTrue(mTestDevice.wasCalled);
    }

    /** Unit test for {@link NativeDevice#rebootIntoSideload()}}. */
    @Test
    public void testRebootIntoSideload() throws Exception {
        NativeDevice testDevice =
                new NativeDevice(mMockIDevice, mMockStateMonitor, mMockDvcMonitor) {
                    @Override
                    public TestDeviceState getDeviceState() {
                        return TestDeviceState.ONLINE;
                    }
                };
        String into = "sideload";

        when(mMockStateMonitor.waitForDeviceInSideload(Mockito.anyLong())).thenReturn(true);

        testDevice.rebootIntoSideload();
        verify(mMockIDevice, times(1)).reboot(into);
    }

    /** Unit test for {@link NativeDevice#rebootIntoBootloader()}}. */
    @Test
    public void testRebootIntoBootloader() throws Exception {
        NativeDevice testDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public TestDeviceState getDeviceState() {
                        return TestDeviceState.ONLINE;
                    }

                    @Override
                    public String getFastbootSerialNumber() {
                        return MOCK_DEVICE_SERIAL;
                    }

                    @Override
                    protected CommandResult simpleFastbootCommand(
                            long timeout, Map<String, String> envVarMap, String[] fullCmd)
                            throws UnsupportedOperationException {
                        return new CommandResult(CommandStatus.SUCCESS);
                    }
                };
        String into = "bootloader";

        testDevice.rebootIntoBootloader();
        verify(mMockIDevice, times(1)).reboot(into);
        verify(mMockStateMonitor).setFastbootSerialNumber(MOCK_DEVICE_SERIAL);
    }

    /**
     * Unit test for {@link NativeDevice#rebootIntoBootloader()}} when device is already in fastboot
     * mode.
     */
    @Test
    public void testRebootIntoBootloader_forceFastboot() throws Exception {
        TestableAndroidNativeDevice testDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public TestDeviceState getDeviceState() {
                        return TestDeviceState.FASTBOOT;
                    }

                    @Override
                    public CommandResult executeFastbootCommand(String... cmdArgs)
                            throws DeviceNotAvailableException, UnsupportedOperationException {
                        if (cmdArgs[0].equals("reboot-bootloader")) {
                            wasCalled = true;
                        }
                        return new CommandResult();
                    }

                    @Override
                    public String getFastbootSerialNumber() {
                        return MOCK_DEVICE_SERIAL;
                    }

                    @Override
                    protected CommandResult simpleFastbootCommand(
                            long timeout, Map<String, String> envVarMap, String[] fullCmd)
                            throws UnsupportedOperationException {
                        return new CommandResult(CommandStatus.SUCCESS);
                    }
                };

        testDevice.rebootIntoBootloader();
        assertTrue(testDevice.wasCalled);

        verify(mMockStateMonitor).setFastbootSerialNumber(MOCK_DEVICE_SERIAL);
    }

    /** Unit test for {@link NativeDevice#rebootIntoFastbootd()}}. */
    @Test
    public void testRebootIntoFastbootd() throws Exception {
        NativeDevice testDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public TestDeviceState getDeviceState() {
                        return TestDeviceState.ONLINE;
                    }

                    @Override
                    public String getFastbootSerialNumber() {
                        return MOCK_DEVICE_SERIAL;
                    }
                };
        String into = "fastboot";

        when(mMockStateMonitor.waitForDeviceFastbootd(Mockito.any(), Mockito.anyLong()))
                .thenReturn(true);

        testDevice.rebootIntoFastbootd();
        verify(mMockIDevice, times(1)).reboot(into);
        verify(mMockStateMonitor).setFastbootSerialNumber(MOCK_DEVICE_SERIAL);
    }

    /**
     * Unit test for {@link NativeDevice#rebootIntoFastbootd()}} when device is already in fastboot
     * mode.
     */
    @Test
    public void testRebootIntoFastbootd_forceFastboot() throws Exception {
        TestableAndroidNativeDevice testDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public TestDeviceState getDeviceState() {
                        return TestDeviceState.FASTBOOT;
                    }

                    @Override
                    public CommandResult executeFastbootCommand(String... cmdArgs)
                            throws DeviceNotAvailableException, UnsupportedOperationException {
                        if (cmdArgs[0].equals("reboot-fastboot")) {
                            wasCalled = true;
                        }
                        return new CommandResult();
                    }

                    @Override
                    public String getFastbootSerialNumber() {
                        return MOCK_DEVICE_SERIAL;
                    }
                };

        when(mMockStateMonitor.waitForDeviceFastbootd(Mockito.any(), Mockito.anyLong()))
                .thenReturn(true);

        testDevice.rebootIntoFastbootd();
        assertTrue(testDevice.wasCalled);

        verify(mMockStateMonitor).setFastbootSerialNumber(MOCK_DEVICE_SERIAL);
    }

    /** Unit test for {@link NativeDevice#unlockDevice()} already decrypted. */
    @Test
    public void testUnlockDevice_skipping() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
                        return false;
                    }
                };

        assertTrue(mTestDevice.unlockDevice());
    }

    /** Unit test for {@link NativeDevice#unlockDevice()}. */
    @Test
    public void testUnlockDevice() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "block";
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "200 checkpw -1";
                    }
                };

        assertTrue(mTestDevice.unlockDevice());
    }

    /** Unit test for {@link NativeDevice#unlockDevice()}. */
    @Test
    public void testUnlockDevice_garbageOutput() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "block";
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "gdsgdgggsgdg not working";
                    }
                };

        assertFalse(mTestDevice.unlockDevice());
    }

    /** Unit test for {@link NativeDevice#unlockDevice()}. */
    @Test
    public void testUnlockDevice_emptyOutput() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "block";
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "";
                    }
                };

        assertFalse(mTestDevice.unlockDevice());
    }

    /** Unit test for {@link NativeDevice#unlockDevice()}. */
    @Test
    public void testUnlockDevice_goodOutputPasswordEnteredCorrectly() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean isEncryptionSupported() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean isDeviceEncrypted() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public String getProperty(String name) throws DeviceNotAvailableException {
                        return "block";
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "200 encryption 0";
                    }
                };
        when(mMockStateMonitor.waitForDeviceAvailable()).thenReturn(mMockIDevice);

        assertTrue(mTestDevice.unlockDevice());
    }

    /**
     * Test {NativeDevice#parseFreeSpaceFromModernOutput(String)} with a regular df output and mount
     * name of numbers.
     */
    @Test
    public void testParseDfOutput_mountWithNumber() {
        String dfOutput =
                "Filesystem     1K-blocks   Used Available Use% Mounted on\n"
                        + "/dev/fuse       31154688 100576  31054112   1% /storage/3134-3433";
        Long res = mTestDevice.parseFreeSpaceFromModernOutput(dfOutput);
        assertNotNull(res);
        assertEquals(31054112L, res.longValue());
    }

    /**
     * Test {NativeDevice#parseFreeSpaceFromModernOutput(String)} with a regular df output and mount
     * name of mix of letters and numbers.
     */
    @Test
    public void testParseDfOutput() {
        String dfOutput =
                "Filesystem     1K-blocks   Used Available Use% Mounted on\n"
                        + "/dev/fuse       31154688 100576  31054112   1% /storage/sdcard58";
        Long res = mTestDevice.parseFreeSpaceFromModernOutput(dfOutput);
        assertNotNull(res);
        assertEquals(31054112L, res.longValue());
    }

    /**
     * Test {NativeDevice#parseFreeSpaceFromModernOutput(String)} with a regular df output and mount
     * name with incorrect field.
     */
    @Test
    public void testParseDfOutput_wrongMount() {
        String dfOutput =
                "Filesystem     1K-blocks   Used Available Use% Mounted on\n"
                        + "/dev/fuse       31154688 100576  31054112   1% \test\\wrongpath";
        Long res = mTestDevice.parseFreeSpaceFromModernOutput(dfOutput);
        assertNull(res);
    }

    /**
     * Test {NativeDevice#parseFreeSpaceFromModernOutput(String)} with a regular df output and a
     * filesytem name with numbers in it.
     */
    @Test
    public void testParseDfOutput_filesystemWithNumber() {
        String dfOutput =
                "Filesystem     1K-blocks   Used Available Use% Mounted on\n"
                        + "/dev/dm-1       31154688 100576  31054112   1% /";
        Long res = mTestDevice.parseFreeSpaceFromModernOutput(dfOutput);
        assertNotNull(res);
        assertEquals(31054112L, res.longValue());
    }

    /**
     * Test that {@link NativeDevice#getDeviceTimeOffset(Date)} returns the proper offset forward
     */
    @Test
    public void testGetDeviceTimeOffset() throws DeviceNotAvailableException {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public long getDeviceDate() throws DeviceNotAvailableException {
                        return 1476958881000L;
                    }
                };
        Date date = new Date(1476958891000L);
        assertEquals(10000L, mTestDevice.getDeviceTimeOffset(date));
    }

    /**
     * Test that {@link NativeDevice#getDeviceTimeOffset(Date)}} returns the proper offset when
     * there is delay.
     */
    @Test
    public void testGetDeviceTimeOffset_delay() throws DeviceNotAvailableException {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public long getDeviceDate() throws DeviceNotAvailableException {
                        // DeviceDate is in second
                        return 1476958891000L;
                    }
                };
        // Date takes millisecond since Epoch
        Date date = new Date(1476958881000L);
        assertEquals(-10000L, mTestDevice.getDeviceTimeOffset(date));
    }

    /**
     * Test that {@link NativeDevice#setDate(Date)} returns the proper offset when there is delay
     * with api level above 24, posix format is used.
     */
    @Test
    public void testSetDate() throws DeviceNotAvailableException {
        Date date = new Date(1476958881000L);
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 24;
                    }

                    @Override
                    public long getDeviceTimeOffset(Date date) throws DeviceNotAvailableException {
                        // right above set threshold
                        return NativeDevice.MAX_HOST_DEVICE_TIME_OFFSET + 1;
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        CLog.e("%s", command);
                        assertEquals("TZ=UTC date -u 102010212016.21", command);
                        return command;
                    }
                };
        mTestDevice.setDate(date);
    }

    /**
     * Test that {@link NativeDevice#setDate(Date)} returns the proper offset when there is delay
     * with api level below 23, regular second format is used.
     */
    @Test
    public void testSetDate_lowApi() throws DeviceNotAvailableException {
        Date date = new Date(1476958881000L);
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 22;
                    }

                    @Override
                    public long getDeviceTimeOffset(Date date) throws DeviceNotAvailableException {
                        // right above set threshold
                        return NativeDevice.MAX_HOST_DEVICE_TIME_OFFSET + 1;
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        CLog.e("%s", command);
                        assertEquals("TZ=UTC date -u 1476958881", command);
                        return command;
                    }
                };
        mTestDevice.setDate(date);
    }

    /** Test that {@link NativeDevice#setDate(Date)} does not attemp to set if bellow threshold. */
    @Test
    public void testSetDate_NoAction() throws DeviceNotAvailableException {
        Date date = new Date(1476958881000L);
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public long getDeviceTimeOffset(Date date) throws DeviceNotAvailableException {
                        // right below set threshold
                        return NativeDevice.MAX_HOST_DEVICE_TIME_OFFSET - 1;
                    }

                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        fail("Should not be called");
                        return command;
                    }
                };
        mTestDevice.setDate(date);
    }

    /** Test that {@link NativeDevice#getDeviceDescriptor()} returns the proper information. */
    @Test
    public void testGetDeviceDescriptor() {
        final String serial = "Test";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public IDevice getIDevice() {
                        return new StubDevice(serial);
                    }
                };
        DeviceDescriptor desc = mTestDevice.getDeviceDescriptor();
        assertTrue(desc.isStubDevice());
        assertEquals(serial, desc.getSerial());
        assertEquals("StubDevice", desc.getDeviceClass());
    }

    /**
     * Test that {@link NativeDevice#pullFile(String, File)} returns true when the pull is
     * successful.
     */
    @Test
    public void testPullFile() throws Exception {
        final String fakeRemotePath = "/test/";
        SyncService s = Mockito.mock(SyncService.class);
        when(mMockIDevice.getSyncService()).thenReturn(s);

        File tmpFile = FileUtil.createTempFile("pull", ".test");
        try {
            boolean res = mTestDevice.pullFile(fakeRemotePath, tmpFile);

            Mockito.verify(s)
                    .pullFile(
                            Mockito.eq(fakeRemotePath),
                            Mockito.eq(tmpFile.getAbsolutePath()),
                            Mockito.any(ISyncProgressMonitor.class));
            Mockito.verify(s).close();
            assertTrue(res);
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test that {@link NativeDevice#pullFile(String, File)} returns false when it fails to pull the
     * file.
     */
    @Test
    public void testPullFile_fails() throws Exception {
        final String fakeRemotePath = "/test/";
        SyncService s = Mockito.mock(SyncService.class);
        when(mMockIDevice.getSyncService()).thenReturn(s);

        File tmpFile = FileUtil.createTempFile("pull", ".test");
        doThrow(new SyncException(SyncError.CANCELED))
                .when(s)
                .pullFile(
                        Mockito.eq(fakeRemotePath),
                        Mockito.eq(tmpFile.getAbsolutePath()),
                        Mockito.any(ISyncProgressMonitor.class));
        try {
            boolean res = mTestDevice.pullFile(fakeRemotePath, tmpFile);

            Mockito.verify(s)
                    .pullFile(
                            Mockito.eq(fakeRemotePath),
                            Mockito.eq(tmpFile.getAbsolutePath()),
                            Mockito.any(ISyncProgressMonitor.class));
            Mockito.verify(s).close();
            assertFalse(res);
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test that {@link NativeDevice#pullFile(String)} returns a file when succeed pulling the file.
     */
    @Test
    public void testPullFile_returnFileSuccess() throws Exception {
        final String fakeRemotePath = "/test/";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean pullFile(String remoteFilePath, File localFile, int userId)
                            throws DeviceNotAvailableException {
                        return true;
                    }
                };
        File res = mTestDevice.pullFile(fakeRemotePath);
        try {
            assertNotNull(res);
        } finally {
            FileUtil.deleteFile(res);
        }
    }

    /**
     * Test that {@link NativeDevice#pullFile(String)} returns null when failed to pull the file.
     */
    @Test
    public void testPullFile_returnNull() throws Exception {
        final String fakeRemotePath = "/test/";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean pullFile(String remoteFilePath, File localFile, int userId)
                            throws DeviceNotAvailableException {
                        return false;
                    }
                };
        File res = mTestDevice.pullFile(fakeRemotePath);
        try {
            assertNull(res);
        } finally {
            FileUtil.deleteFile(res);
        }
    }

    /**
     * Test that {@link NativeDevice#pullFileContents(String)} returns a string when succeed pulling
     * the file.
     */
    @Test
    public void testPullFileContents_returnFileSuccess() throws Exception {
        final String fakeRemotePath = "/test/";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean pullFile(String remoteFilePath, File localFile, int userId)
                            throws DeviceNotAvailableException {
                        return true;
                    }
                };
        String res = mTestDevice.pullFileContents(fakeRemotePath);
        assertNotNull(res);
    }

    /**
     * Test that {@link NativeDevice#pullFileContents(String)} returns null when failed to pull the
     * file.
     */
    @Test
    public void testPullFileContents_returnNull() throws Exception {
        final String fakeRemotePath = "/test/";
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean pullFile(String remoteFilePath, File localFile, int userId)
                            throws DeviceNotAvailableException {
                        return false;
                    }
                };
        String res = mTestDevice.pullFileContents(fakeRemotePath);
        assertNull(res);
    }

    /**
     * Test that {@link NativeDevice#pushFile(File, String)} returns true when the push is
     * successful.
     */
    @Test
    public void testPushFile() throws Exception {
        final String fakeRemotePath = "/test/";
        SyncService s = Mockito.mock(SyncService.class);
        when(mMockIDevice.getSyncService()).thenReturn(s);

        File tmpFile = FileUtil.createTempFile("push", ".test");
        try {
            boolean res = mTestDevice.pushFile(tmpFile, fakeRemotePath);

            Mockito.verify(s)
                    .pushFile(
                            Mockito.eq(tmpFile.getAbsolutePath()),
                            Mockito.eq(fakeRemotePath),
                            Mockito.any(ISyncProgressMonitor.class));
            Mockito.verify(s).close();
            assertTrue(res);
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test that {@link NativeDevice#pushFile(File, String)} returns false when the push is
     * unsuccessful.
     */
    @Test
    public void testPushFile_fails() throws Exception {
        final String fakeRemotePath = "/test/";
        SyncService s = Mockito.mock(SyncService.class);
        when(mMockIDevice.getSyncService()).thenReturn(s);

        File tmpFile = FileUtil.createTempFile("push", ".test");
        doThrow(new SyncException(SyncError.CANCELED))
                .when(s)
                .pushFile(
                        Mockito.eq(tmpFile.getAbsolutePath()),
                        Mockito.eq(fakeRemotePath),
                        Mockito.any(ISyncProgressMonitor.class));
        try {
            boolean res = mTestDevice.pushFile(tmpFile, fakeRemotePath);

            Mockito.verify(s)
                    .pushFile(
                            Mockito.eq(tmpFile.getAbsolutePath()),
                            Mockito.eq(fakeRemotePath),
                            Mockito.any(ISyncProgressMonitor.class));
            Mockito.verify(s).close();
            assertFalse(res);
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /** Test get Process pid by process name */
    @Test
    public void testGetProcessPid() throws Exception {
        final String fakePid = "914";
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn(fakePid).when(spy).executeShellCommand("pidof system_server");

        assertEquals(fakePid, spy.getProcessPid("system_server"));
    }

    /** Test get Process pid by process name with adb shell return of extra new line */
    @Test
    public void testGetProcessPidWithNewLine() throws Exception {
        final String fakePid = "914";
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn(fakePid + "\n").when(spy).executeShellCommand("pidof system_server");

        assertEquals(fakePid, spy.getProcessPid("system_server"));
    }

    /** Test get Process pid return null with invalid shell command output */
    @Test
    public void testGetProcessPidInvalidOutput() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("invalid output").when(spy).executeShellCommand("pidof system_server");

        assertNull(spy.getProcessPid("system_server"));
    }

    /** Test get Process pid return null with shell command empty output */
    @Test
    public void testGetProcessPidEmptyOutput() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("").when(spy).executeShellCommand("pidof system_server");

        assertNull(spy.getProcessPid("system_server"));
    }

    public void runTestGetProcessByName(int apiLevel) throws Exception {
        final String fakePid = "914";
        final String fakeCreationTime = "1559091922";
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn(fakePid).when(spy).executeShellCommand("pidof system_server");
        doReturn("12:07:32").when(spy).executeShellCommand("ps -p " + fakePid + " -o stime=");

        doReturn(apiLevel).when(spy).getApiLevel();

        if (apiLevel <= 28) {
            doReturn(fakeCreationTime)
                    .when(spy)
                    .executeShellCommand(
                            "date -d \"$(date +%Y:%m:%d):"
                                    + "12:07:32"
                                    + "\" +%s -D \"%Y:%m:%d:%H:%M:%S\"");
        } else {
            doReturn(fakeCreationTime).when(spy).executeShellCommand("date -d\"12:07:32\" +%s");
        }

        doReturn("system").when(spy).executeShellCommand("stat -c%U /proc/" + fakePid);

        ProcessInfo info = spy.getProcessByName("system_server");
        assertEquals(Integer.parseInt(fakePid), spy.getProcessByName("system_server").getPid());
        assertEquals(
                Long.parseLong(fakeCreationTime),
                spy.getProcessByName("system_server").getStartTime());
    }

    /** Test get ProcessInfo by process name */
    @Test
    public void testGetProcessByName() throws Exception {
        runTestGetProcessByName(26);
        runTestGetProcessByName(28);
        runTestGetProcessByName(29);
        runTestGetProcessByName(31);
    }

    /** Test get ProcessInfo by process name return null for invalid process */
    @Test
    public void testGetProcessByNameInvalidProcess() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("").when(spy).executeShellCommand("pidof system_server");

        assertNull(spy.getProcessByName("system_server"));
    }

    /** Test get ProcessInfo by process name return null for invalid process */
    @Test
    public void testGetProcessByNameInvalidStartTime() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("120").when(spy).executeShellCommand("pidof system_server");
        doReturn("").when(spy).executeShellCommand("ps -p 120 -o stime=");

        assertNull(spy.getProcessByName("system_server"));
    }

    @Test
    public void testGetIntProperty() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("123").when(spy).getProperty("ro.test.prop");

        assertEquals(123, spy.getIntProperty("ro.test.prop", -1));
    }

    @Test
    public void testGetIntPropertyNotAnIntegerPropertyReturnsDefaultValue() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("not-an-int").when(spy).getProperty("ro.test.prop");

        assertEquals(-1, spy.getIntProperty("ro.test.prop", -1));
    }

    @Test
    public void testGetIntPropertyUnknownPropertyReturnsDefaultValue() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn(null).when(spy).getProperty("ro.test.prop");

        assertEquals(-1, spy.getIntProperty("ro.test.prop", -1));
    }

    @Test
    public void testGetBooleanPropertyReturnsTrue() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("true").when(spy).getProperty("ro.test.prop");

        assertTrue(spy.getBooleanProperty("ro.test.prop", false));
    }

    @Test
    public void testGetBooleanPropertyReturnsFalse() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("no").when(spy).getProperty("ro.test.prop");

        assertFalse(spy.getBooleanProperty("ro.test.prop", true));
    }

    @Test
    public void testGetBooleanPropertyUnknownPropertyReturnsDefaultValue() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn(null).when(spy).getProperty("ro.test.prop");

        assertTrue(spy.getBooleanProperty("ro.test.prop", true));
    }

    @Test
    public void testGetBooleanPropertyInvalidValueReturnsDefaultValue() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("123").when(spy).getProperty("ro.test.prop");

        assertTrue(spy.getBooleanProperty("ro.test.prop", true));
    }

    @Test
    public void testGetProperty_noOutput() throws Exception {
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        res.setStdout("\n");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        (OutputStream) Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.eq("adb"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("shell"),
                        Mockito.eq("getprop"),
                        Mockito.eq("test")))
                .thenReturn(res);

        assertNull(mTestDevice.getProperty("test"));
    }

    /** Test get boot history */
    @Test
    public void testGetBootHistory() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn(
                        "kernel_panic,1556587278\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);
        Map<Long, String> history = new LinkedHashMap<Long, String>();
        history.put(1556587278L, "kernel_panic");
        history.put(1556238008L, "reboot");
        history.put(1556237796L, "reboot");
        history.put(1556237725L, "reboot");

        assertEquals(history, spy.getBootHistory());
    }

    /** Test get empty boot history */
    @Test
    public void testGetBootHistoryEmpty() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("").when(spy).getProperty(DeviceProperties.BOOT_REASON_HISTORY);

        assertTrue(spy.getBootHistory().isEmpty());
    }

    /** Test get invalid boot history */
    @Test
    public void testGetBootHistoryInvalid() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("invalid output").when(spy).getProperty(DeviceProperties.BOOT_REASON_HISTORY);

        assertTrue(spy.getBootHistory().isEmpty());
    }

    /** Test get boot history since */
    @Test
    public void testGetBootHistorySince() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn(
                        "kernel_panic,1556587278\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);
        Map<Long, String> history = new LinkedHashMap<Long, String>();
        history.put(1556587278L, "kernel_panic");

        assertEquals(history, spy.getBootHistorySince(1556238009L, TimeUnit.SECONDS));
    }

    /** Test {@link NativeDevice#getBootHistorySince(long, TimeUnit)} on an edge condition. */
    @Test
    public void testGetBootHistorySince_limit() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("reboot,1579678463\n" + "        reboot,,1579678339\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);
        Map<Long, String> history = new LinkedHashMap<Long, String>();
        history.put(1579678463L, "reboot");

        // For the same value we should expect it to be part of the reboot.
        assertEquals(history, spy.getBootHistorySince(1579678463, TimeUnit.SECONDS));
    }

    /** Test get boot history since */
    @Test
    public void testGetBootHistorySinceInMillisecond() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn(
                        "kernel_panic,1556587278\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);
        Map<Long, String> history = new LinkedHashMap<Long, String>();
        history.put(1556587278L, "kernel_panic");

        assertEquals(history, spy.getBootHistorySince(1556238009000L, TimeUnit.MILLISECONDS));
    }

    /** Test deviceSoftRestartedSince */
    @Test
    public void testDeviceSoftRestartedSince() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("914").when(spy).executeShellCommand("pidof system_server");
        doReturn("12:07:32").when(spy).executeShellCommand("ps -p 914 -o stime=");
        doReturn(29).when(spy).getApiLevel();
        doReturn("1559091922").when(spy).executeShellCommand("date -d\"12:07:32\" +%s");
        doReturn("system").when(spy).executeShellCommand("stat -c%U /proc/914");
        doReturn(
                        "kernel_panic,1556587278\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);

        assertFalse(spy.deviceSoftRestartedSince(1559091923L, TimeUnit.SECONDS));
        assertFalse(spy.deviceSoftRestartedSince(1559091923000L, TimeUnit.MILLISECONDS));
        assertFalse(spy.deviceSoftRestartedSince(1559091922L, TimeUnit.SECONDS));
        assertFalse(spy.deviceSoftRestartedSince(1559091922000L, TimeUnit.MILLISECONDS));
        assertTrue(spy.deviceSoftRestartedSince(1559091920L, TimeUnit.SECONDS));
        assertTrue(spy.deviceSoftRestartedSince(1559091920000L, TimeUnit.MILLISECONDS));
    }

    /** Test deviceSoftRestartedSince return true with system_server stopped */
    @Test
    public void testDeviceSoftRestartedSinceWithSystemServerStopped() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("").when(spy).executeShellCommand("pidof system_server");
        assertTrue(spy.deviceSoftRestartedSince(1559091922L, TimeUnit.SECONDS));
    }

    /** Test deviceSoftRestartedSince throw RuntimeException with abnormal reboot */
    @Test
    public void testDeviceSoftRestartedSinceWithAbnormalReboot() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("914").when(spy).executeShellCommand("pidof system_server");
        doReturn("12:07:32").when(spy).executeShellCommand("ps -p 914 -o stime=");
        doReturn(29).when(spy).getApiLevel();
        doReturn("1559091999").when(spy).executeShellCommand("date -d\"12:07:32\" +%s");
        doReturn("system").when(spy).executeShellCommand("stat -c%U /proc/914");
        doReturn(
                        "kernel_panic,1559091933\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);

        try {
            spy.deviceSoftRestartedSince(1559091922L, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            // expected
            return;
        }
        fail("RuntimeException is expected");
    }

    /** Test deviceSoftRestartedSince return false with normal reboot */
    @Test
    public void testDeviceSoftRestartedSinceNotAfterNormalReboot() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("914").when(spy).executeShellCommand("pidof system_server");
        doReturn("12:07:32").when(spy).executeShellCommand("ps -p 914 -o stime=");
        doReturn(29).when(spy).getApiLevel();
        doReturn("1559091939").when(spy).executeShellCommand("date -d\"12:07:32\" +%s");
        doReturn("system").when(spy).executeShellCommand("stat -c%U /proc/914");
        doReturn(
                        "reboot,1559091933\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);

        assertFalse(spy.deviceSoftRestartedSince(1559091921L, TimeUnit.SECONDS));
    }

    /** Test deviceSoftRestartedSince return false with normal reboot */
    @Test
    public void testDeviceSoftRestartedSinceAfterNormalReboot() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("914").when(spy).executeShellCommand("pidof system_server");
        doReturn("12:07:32").when(spy).executeShellCommand("ps -p 914 -o stime=");
        doReturn(29).when(spy).getApiLevel();
        doReturn("1559091992").when(spy).executeShellCommand("date -d\"12:07:32\" +%s");
        doReturn("system").when(spy).executeShellCommand("stat -c%U /proc/914");
        doReturn(
                        "reboot,1559091933\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);

        assertTrue(spy.deviceSoftRestartedSince(1559091921L, TimeUnit.SECONDS));
    }

    /** Test deviceSoftRestarted given the previous system_server {@link ProcessInfo} */
    @Test
    public void testDeviceSoftRestarted() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        ProcessInfo prev1 = new ProcessInfo("system", 123, "system_server", 1559000000L);
        ProcessInfo prev2 = new ProcessInfo("system", 914, "system_server", 1559091922L);
        doReturn("914").when(spy).executeShellCommand("pidof system_server");
        doReturn("12:07:32").when(spy).executeShellCommand("ps -p 914 -o stime=");
        doReturn(29).when(spy).getApiLevel();
        doReturn("1559091922").when(spy).executeShellCommand("date -d\"12:07:32\" +%s");
        doReturn("system").when(spy).executeShellCommand("stat -c%U /proc/914");
        doReturn(
                        "kernel_panic,1556587278\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);

        assertTrue(spy.deviceSoftRestarted(prev1));
        assertFalse(spy.deviceSoftRestarted(prev2));
    }

    /** Test deviceSoftRestarted return true with system_server stopped */
    @Test
    public void testDeviceSoftRestartedWithSystemServerStopped() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("").when(spy).executeShellCommand("pidof system_server");
        assertTrue(
                spy.deviceSoftRestarted(
                        new ProcessInfo("system", 123, "system_server", 1559000000L)));
    }

    /** Test deviceSoftRestarted throw RuntimeException with abnormal reboot */
    @Test
    public void testDeviceSoftRestartedWithAbnormalReboot() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("914").when(spy).executeShellCommand("pidof system_server");
        doReturn("12:07:32").when(spy).executeShellCommand("ps -p 914 -o stime=");
        doReturn(29).when(spy).getApiLevel();
        doReturn("1559091999").when(spy).executeShellCommand("date -d\"12:07:32\" +%s");
        doReturn("system").when(spy).executeShellCommand("stat -c%U /proc/914");
        doReturn(
                        "kernel_panic,1559091933\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);

        try {
            spy.deviceSoftRestarted(new ProcessInfo("system", 123, "system_server", 1559000000L));
        } catch (RuntimeException e) {
            // expected
            return;
        }
        fail("Abnormal reboot is detected, RuntimeException is expected");
    }

    /** Test ddeviceSoftRestarted return false with normal reboot */
    @Test
    public void testDeviceSoftRestartedNotAfterNormalReboot() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("914").doReturn("914").when(spy).executeShellCommand("pidof system_server");
        doReturn("12:07:32").when(spy).executeShellCommand("ps -p 914 -o stime=");
        doReturn(29).when(spy).getApiLevel();
        doReturn("1559091935").when(spy).executeShellCommand("date -d\"12:07:32\" +%s");
        doReturn("system").when(spy).executeShellCommand("stat -c%U /proc/914");
        doReturn(
                        "reboot,,1559091933\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);

        assertFalse(
                spy.deviceSoftRestarted(
                        new ProcessInfo("system", 123, "system_server", 1559000000L)));
    }

    /** Test deviceSoftRestarted return true if system_server restarted after normal reboot */
    @Test
    public void testDeviceSoftRestartedAfterNormalReboot() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn("914").doReturn("914").when(spy).executeShellCommand("pidof system_server");
        doReturn("12:07:32").when(spy).executeShellCommand("ps -p 914 -o stime=");
        doReturn(29).when(spy).getApiLevel();
        doReturn("1559091995").when(spy).executeShellCommand("date -d\"12:07:32\" +%s");
        doReturn("system").when(spy).executeShellCommand("stat -c%U /proc/914");
        doReturn(
                        "reboot,,1559091933\n"
                                + "        reboot,,1556238008\n"
                                + "        reboot,,1556237796\n"
                                + "        reboot,,1556237725\n")
                .when(spy)
                .getProperty(DeviceProperties.BOOT_REASON_HISTORY);

        assertTrue(
                spy.deviceSoftRestarted(
                        new ProcessInfo("system", 123, "system_server", 1559000000L)));
    }

    /** Test validating valid MAC addresses */
    @Test
    public void testIsMacAddress() {
        assertTrue(mTestDevice.isMacAddress("00:00:00:00:00:00"));
        assertTrue(mTestDevice.isMacAddress("00:15:E9:2B:99:3C"));
        assertTrue(mTestDevice.isMacAddress("FF:FF:FF:FF:FF:FF"));
        assertTrue(mTestDevice.isMacAddress("58:a2:b5:7d:49:24"));
    }

    /** Test validating invalid MAC addresses */
    @Test
    public void testIsMacAddress_invalidInput() {
        assertFalse(mTestDevice.isMacAddress(""));
        assertFalse(mTestDevice.isMacAddress("00-15-E9-2B-99-3C")); // Invalid delimiter
    }

    /** Test querying a device MAC address */
    @Test
    public void testGetMacAddress() throws Exception {
        String address = "58:a2:b5:7d:49:24";
        IDevice device =
                new StubDevice(MOCK_DEVICE_SERIAL) {
                    @Override
                    public void executeShellCommand(String command, IShellOutputReceiver receiver)
                            throws TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, IOException {
                        receiver.addOutput(address.getBytes(), 0, address.length());
                    }
                };
        mMockIDevice = mock(IDevice.class, AdditionalAnswers.delegatesTo(device));
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    IHostOptions getHostOptions() {
                        return new HostOptions() {
                            @Override
                            public String getNetworkInterface() {
                                return "en0";
                            }
                        };
                    }

                    @Override
                    public boolean isAdbTcp() {
                        return true;
                    }

                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };

        assertEquals(address, mTestDevice.getMacAddress());
    }

    /** Test querying a device MAC address when the device is in fastboot */
    @Test
    public void testGetMacAddress_fastboot() throws Exception {
        mTestDevice.setDeviceState(TestDeviceState.FASTBOOT);
        // Will fail if executeShellCommand is called. Which it should not.
        assertNull(mTestDevice.getMacAddress());
    }

    /** Test querying a device MAC address but failing to do so */
    @Test
    public void testGetMacAddress_failure() throws Exception {
        IDevice device =
                new StubDevice(MOCK_DEVICE_SERIAL) {
                    @Override
                    public void executeShellCommand(String command, IShellOutputReceiver receiver)
                            throws TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, IOException {
                        throw new IOException();
                    }
                };
        mMockIDevice = mock(IDevice.class, AdditionalAnswers.delegatesTo(device));
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    IHostOptions getHostOptions() {
                        return new HostOptions() {
                            @Override
                            public String getNetworkInterface() {
                                return "en0";
                            }
                        };
                    }

                    @Override
                    public boolean isAdbTcp() {
                        return true;
                    }

                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };

        assertNull(mTestDevice.getMacAddress());
    }

    /** Test querying a device MAC address for a stub device */
    @Test
    public void testGetMacAddress_stubDevice() throws Exception {
        String address = "58:a2:b5:7d:49:24";
        IDevice device =
                new StubDevice(MOCK_DEVICE_SERIAL) {
                    @Override
                    public void executeShellCommand(String command, IShellOutputReceiver receiver)
                            throws TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, IOException {
                        receiver.addOutput(address.getBytes(), 0, address.length());
                    }
                };
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean recoverDevice() throws DeviceNotAvailableException {
                        // ignore
                        return true;
                    }

                    @Override
                    public IDevice getIDevice() {
                        return device;
                    }

                    @Override
                    IWifiHelper createWifiHelper() {
                        return mMockWifi;
                    }
                };
        mTestDevice.setIDevice(device);
        assertNull(mTestDevice.getMacAddress());
    }

    /** Test if valid shell output returns correct memory size. */
    @Test
    public void testGetTotalMemory() {
        final long expectSize = 1902936064;
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "MemTotal:        1858336 kB";
                    }
                };
        assertEquals(expectSize, mTestDevice.getTotalMemory());
    }

    /** Test if empty shell output returns -1. */
    @Test
    public void testGetTotalMemory_emptyString() {
        final long expectSize = -1;
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "";
                    }
                };
        assertEquals(expectSize, mTestDevice.getTotalMemory());
    }

    /** Test if unexpected shell output returns -1. */
    @Test
    public void testGetTotalMemory_unexpectedFormat() {
        final long expectSize = -1;
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "1858336 kB";
                    }
                };
        assertEquals(expectSize, mTestDevice.getTotalMemory());
    }

    /** Test if catching exception returns -1. */
    @Test
    public void testGetTotalMemory_exception() {
        final long expectSize = -1;
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        throw new DeviceNotAvailableException("test", "serial");
                    }
                };
        assertEquals(expectSize, mTestDevice.getTotalMemory());
    }

    /**
     * Test that when a {@link NativeDevice#getLogcatSince(long)} is requested a matching logcat
     * command is generated on sdk version 23.
     */
    @Test
    public void testGetLogcatSinceOnSdk23() throws Exception {
        long date = 1512990942000L; // 2017-12-11 11:15:42.000 UTC
        setGetPropertyExpectation("ro.build.version.sdk", "23");

        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
        String dateFormatted = format.format(new Date(date));

        InputStreamSource res = mTestDevice.getLogcatSince(date);
        StreamUtil.close(res);

        verify(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq(String.format(
                            "logcat -b all -v threadtime -t '%s'", dateFormatted)),
                        Mockito.any());
    }

    /**
     * Test that when a {@link NativeDevice#getLogcatSince(long)} is requested a matching logcat
     * command is generated on sdk version 24.
     */
    @Test
    public void testGetLogcatSinceOnSdkOver24() throws Exception {
        long date = 1512990942012L;
        setGetPropertyExpectation("ro.build.version.sdk", "24");

        InputStreamSource res = mTestDevice.getLogcatSince(date);
        StreamUtil.close(res);

        verify(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq("logcat -b all -v threadtime,uid -t '1512990942.012'"),
                        Mockito.any());
    }

    @Test
    public void testGetProductVariant() throws Exception {
        setGetPropertyExpectation(DeviceProperties.VARIANT, "variant");

        assertEquals("variant", mTestDevice.getProductVariant());
    }

    @Test
    public void testGetProductVariant_legacyOmr1() throws Exception {
        TestableAndroidNativeDevice testDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    protected String internalGetProperty(
                            String propName, String fastbootVar, String description)
                            throws DeviceNotAvailableException, UnsupportedOperationException {
                        if (DeviceProperties.VARIANT_LEGACY_O_MR1.equals(propName)) {
                            return "legacy_omr1";
                        }
                        return null;
                    }
                };

        assertEquals("legacy_omr1", testDevice.getProductVariant());
    }

    @Test
    public void testGetProductVariant_legacy() throws Exception {
        TestableAndroidNativeDevice testDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    protected String internalGetProperty(
                            String propName, String fastbootVar, String description)
                            throws DeviceNotAvailableException, UnsupportedOperationException {
                        if (DeviceProperties.VARIANT_LEGACY_LESS_EQUAL_O.equals(propName)) {
                            return "legacy";
                        }
                        return null;
                    }
                };

        assertEquals("legacy", testDevice.getProductVariant());
    }

    /** Test when {@link NativeDevice#executeShellV2Command(String)} returns a success. */
    @Test
    public void testExecuteShellV2Command() throws Exception {
    OutputStream stdout = null;
    OutputStream stderr = null;
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        100, stdout, stderr, "adb", "-s", "serial", "shell", "some", "command"))
                .thenReturn(res);

        assertNotNull(mTestDevice.executeShellV2Command("some command"));
    }

    /**
     * Test when {@link NativeDevice#executeShellV2Command(String, long, TimeUnit, int)} fails and
     * repeat because of a timeout.
     */
    @Test
    public void testExecuteShellV2Command_timeout() throws Exception {
    OutputStream stdout = null;
    OutputStream stderr = null;
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.TIMED_OUT);
        res.setStderr("timed out");
        when(mMockRunUtil.runTimedCmd(
                        200L, stdout, stderr, "adb", "-s", "serial", "shell", "some", "command"))
                .thenReturn(res);

        try {
            mTestDevice.executeShellV2Command("some command", 200L, TimeUnit.MILLISECONDS, 1);
            fail("Should have thrown an exception.");
        } catch (DeviceUnresponsiveException e) {
            // expected
        }
        verify(mMockRunUtil, times(2))
                .runTimedCmd(
                        200L, stdout, stderr, "adb", "-s", "serial", "shell", "some", "command");
    }

    /**
     * Test when {@link NativeDevice#executeShellV2Command(String, long, TimeUnit, int)} fails and
     * output.
     */
    @Test
    public void testExecuteShellV2Command_fail() throws Exception {
    OutputStream stdout = null;
    OutputStream stderr = null;
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.FAILED);
        res.setStderr("timed out");
        when(mMockRunUtil.runTimedCmd(
                        200L, stdout, stderr, "adb", "-s", "serial", "shell", "some", "command"))
                .thenReturn(res);

        CommandResult result =
                mTestDevice.executeShellV2Command("some command", 200L, TimeUnit.MILLISECONDS, 1);
        assertNotNull(result);
        // The final result is what RunUtil returned, so it contains full status information.
        assertSame(res, result);
        verify(mMockRunUtil, times(1))
                .runTimedCmd(
                        200L, stdout, stderr, "adb", "-s", "serial", "shell", "some", "command");
    }

    /**
     * Test when {@link NativeDevice#executeShellV2Command(String, long, TimeUnit, int)} hit offline
     * but successful on retry
     */
    @Test
    public void testExecuteShellV2Command_offline_recovered() throws Exception {
        OutputStream stdout = null, stderr = null;
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.FAILED);
        res.setStderr("adb: device offline");
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean recoverDevice() throws DeviceNotAvailableException {
                        // change shell command result since now we want success on retry
                        res.setStatus(CommandStatus.SUCCESS);
                        res.setStderr("");
                        return true;
                    }
                };
        when(mMockRunUtil.runTimedCmd(
                        200L, stdout, stderr, "adb", "-s", "serial", "shell", "some", "command"))
                .thenReturn(res);

        CommandResult result =
                mTestDevice.executeShellV2Command("some command", 200L, TimeUnit.MILLISECONDS, 1);
        assertNotNull(result);
        // The final result is what RunUtil returned, so it contains full status information.
        assertSame(res, result);
        verify(mMockRunUtil, times(2))
                .runTimedCmd(
                        200L, stdout, stderr, "adb", "-s", "serial", "shell", "some", "command");
    }

    /**
     * Test when {@link NativeDevice#executeShellV2Command(String, long, TimeUnit, int)} hit offline
     */
    @Test
    public void testExecuteShellV2Command_offline_exception() throws Exception {
        OutputStream stdout = null, stderr = null;
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.FAILED);
        res.setStderr("adb: device offline");
        when(mMockRunUtil.runTimedCmd(
                        200L, stdout, stderr, "adb", "-s", "serial", "shell", "some", "command"))
                .thenReturn(res);

        try {
            mTestDevice.executeShellV2Command("some command", 200L, TimeUnit.MILLISECONDS, 1);
            fail("executeShellV2Command should have thrown a DeviceNotAvailableException");
        } catch (DeviceNotAvailableException dnae) {
            // expected
        }
    }

    /**
     * Test {@link NativeDevice#executeShellV2Command(String)} with workaround for devices without
     * shell v2.
     */
    @Test
    public void testExecuteShellV2Command_exit_status_workaround() throws Exception {
        OutputStream stdout = null;
        OutputStream stderr = null;
        when(mMockIDevice.supportsFeature(IDevice.Feature.SHELL_V2)).thenReturn(false);
        OptionSetter deviceOptionSetter = new OptionSetter(mTestDevice.getOptions());
        deviceOptionSetter.setOptionValue("exit-status-workaround", "true");
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.SUCCESS);
        res.setStdout("foox1");
        when(mMockRunUtil.runTimedCmd(
                        100, stdout, stderr, "adb", "-s", "serial", "shell", "some", "command", ";",
                        "echo", "x$?"))
                .thenReturn(res);

        CommandResult result = mTestDevice.executeShellV2Command("some command");
        assertEquals(CommandStatus.FAILED, result.getStatus());
        assertEquals(1, result.getExitCode().intValue());
        assertEquals("foo", result.getStdout());
    }

    /** Unit test for {@link INativeDevice#setProperty(String, String)}. */
    @Test
    public void testSetProperty() throws DeviceNotAvailableException {
        OutputStream stdout = null;
        OutputStream stderr = null;
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        120000,
                        stdout,
                        stderr,
                        "adb",
                        "-s",
                        "serial",
                        "shell",
                        "setprop test 'value'"))
                .thenReturn(res);

        assertTrue(mTestDevice.setProperty("test", "value"));
    }

    /**
     * Verifies that {@link INativeDevice#isExecutable(String)} recognizes regular executable file
     *
     * @throws Exception
     */
    @Test
    public void testIsDeviceFileExecutable_executable_rwx() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "-rwxr-xr-x 1 root shell 42824 2009-01-01 00:00 /system/bin/ping";
                    }
                };
        assertTrue(mTestDevice.isExecutable("/system/bin/ping"));
    }

    /**
     * Verifies that {@link INativeDevice#isExecutable(String)} recognizes symlink'd executable file
     *
     * @throws Exception
     */
    @Test
    public void testIsDeviceFileExecutable_executable_lrwx() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "lrwxr-xr-x 1 root shell 7 2009-01-01 00:00 /system/bin/start ->"
                                + " toolbox";
                    }
                };
        assertTrue(mTestDevice.isExecutable("/system/bin/start"));
    }

    /**
     * Verifies that {@link INativeDevice#isExecutable(String)} recognizes non-executable file
     *
     * @throws Exception
     */
    @Test
    public void testIsDeviceFileExecutable_notExecutable() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "-rw-r--r-- 1 root root 5020 2009-01-01 00:00 /system/build.prop";
                    }
                };
        assertFalse(mTestDevice.isExecutable("/system/build.prop"));
    }

    /**
     * Verifies that {@link INativeDevice#isExecutable(String)} recognizes a directory listing
     *
     * @throws Exception
     */
    @Test
    public void testIsDeviceFileExecutable_directory() throws Exception {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public String executeShellCommand(String command)
                            throws DeviceNotAvailableException {
                        return "total 416\n"
                                + "drwxr-xr-x 74 root root    4096 2009-01-01 00:00 app\n"
                                + "drwxr-xr-x  3 root shell   8192 2009-01-01 00:00 bin\n"
                                + "-rw-r--r--  1 root root    5020 2009-01-01 00:00"
                                + " build.prop\n"
                                + "drwxr-xr-x 15 root root    4096 2009-01-01 00:00 etc\n"
                                + "drwxr-xr-x  2 root root    4096 2009-01-01 00:00 fake-libs\n"
                                + "drwxr-xr-x  2 root root    8192 2009-01-01 00:00 fonts\n"
                                + "drwxr-xr-x  4 root root    4096 2009-01-01 00:00 framework\n"
                                + "drwxr-xr-x  6 root root    8192 2009-01-01 00:00 lib\n"
                                + "drwx------  2 root root    4096 1970-01-01 00:00"
                                + " lost+found\n"
                                + "drwxr-xr-x  3 root root    4096 2009-01-01 00:00 media\n"
                                + "drwxr-xr-x 68 root root    4096 2009-01-01 00:00 priv-app\n"
                                + "-rw-r--r--  1 root root  137093 2009-01-01 00:00"
                                + " recovery-from-boot.p\n"
                                + "drwxr-xr-x  9 root root    4096 2009-01-01 00:00 usr\n"
                                + "drwxr-xr-x  8 root shell   4096 2009-01-01 00:00 vendor\n"
                                + "drwxr-xr-x  2 root shell   4096 2009-01-01 00:00 xbin\n";
                    }
                };
        assertFalse(mTestDevice.isExecutable("/system"));
    }

    /** Test {@link NativeDevice#getTombstones()}. */
    @Test
    public void testGetTombstones_notRoot() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn(false).when(spy).isAdbRoot();

        List<File> result = spy.getTombstones();
        assertEquals(0, result.size());
    }

    /** Test {@link NativeDevice#getTombstones()}. */
    @Test
    public void testGetTombstones() throws Exception {
        TestableAndroidNativeDevice spy = Mockito.spy(mTestDevice);
        doReturn(true).when(spy).isAdbRoot();

        String[] tombs = new String[] {"tomb1", "tomb2"};
        doReturn(tombs).when(spy).getChildren("/data/tombstones/");

        doReturn(new File("tomb1_test")).when(spy).pullFile("/data/tombstones/tomb1");
        doReturn(new File("tomb2_test")).when(spy).pullFile("/data/tombstones/tomb2");

        List<File> result = spy.getTombstones();
        assertEquals(2, result.size());
    }

    /** Test {@link NativeDevice#getLaunchApiLevel()} with ro.product.first_api_level being set. */
    @Test
    public void testGetLaunchApiLevel_w_first_api() throws DeviceNotAvailableException {
        setGetPropertyExpectation(DeviceProperties.FIRST_API_LEVEL, "23");
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 29;
                    }
                };

        assertEquals(23, mTestDevice.getLaunchApiLevel());
    }

    /**
     * Test {@link NativeDevice#getLaunchApiLevel()} without ro.product.first_api_level being set.
     */
    @Test
    public void testGetLaunchApiLevel_wo_first_api() throws DeviceNotAvailableException {
        setGetPropertyExpectation(DeviceProperties.FIRST_API_LEVEL, null);
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 29;
                    }
                };

        assertEquals(29, mTestDevice.getLaunchApiLevel());
    }

    /** Test {@link NativeDevice#getLaunchApiLevel()} with NumberFormatException be asserted. */
    @Test
    public void testGetLaunchApiLevel_w_exception() throws DeviceNotAvailableException {
        setGetPropertyExpectation(DeviceProperties.FIRST_API_LEVEL, "R");
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public int getApiLevel() throws DeviceNotAvailableException {
                        return 29;
                    }
                };

        assertEquals(29, mTestDevice.getLaunchApiLevel());
    }

    private void setGetPropertyExpectation(String property, String value) {
        CommandResult stubResult = new CommandResult(CommandStatus.SUCCESS);
        stubResult.setStdout(value);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        (OutputStream) Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.eq("adb"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("shell"),
                        Mockito.eq("getprop"),
                        Mockito.eq(property)))
                .thenReturn(stubResult);
    }

    /** Test {@link NativeDevice#getFastbootSerialNumber()} with USB adb. */
    @Test
    public void testGetFastbootSerialNumber_usb_adb() {
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    public boolean isAdbTcp() {
                        return false;
                    }
                };

        assertEquals(MOCK_DEVICE_SERIAL, mTestDevice.getFastbootSerialNumber());
    }

    /** Test {@link NativeDevice#getFastbootSerialNumber()} with non-root TCP adb. */
    @Test
    public void testGetFastbootSerialNumber_non_root_tcp_adb() throws Exception {
        String address = "00:30:1b:ba:81:28";
        IDevice device =
                new StubDevice(MOCK_DEVICE_SERIAL) {
                    @Override
                    public void executeShellCommand(String command, IShellOutputReceiver receiver)
                            throws TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, IOException {
                        receiver.addOutput(address.getBytes(), 0, address.length());
                    }
                };
        mMockIDevice = mock(IDevice.class, AdditionalAnswers.delegatesTo(device));
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    IHostOptions getHostOptions() {
                        return new HostOptions() {
                            @Override
                            public String getNetworkInterface() {
                                return "en0";
                            }
                        };
                    }

                    @Override
                    public boolean isAdbTcp() {
                        return true;
                    }

                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return false;
                    }

                    @Override
                    public boolean enableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }

                    @Override
                    public boolean disableAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        assertEquals(
                "tcp:fe80:0:0:0:230:1bff:feba:8128%en0", mTestDevice.getFastbootSerialNumber());
    }

    /** Test {@link NativeDevice#getFastbootSerialNumber()} with root TCP adb. */
    @Test
    public void testGetFastbootSerialNumber_root_tcp_adb() throws Exception {
        String address = "00:30:1b:ba:81:28";
        IDevice device =
                new StubDevice(MOCK_DEVICE_SERIAL) {
                    @Override
                    public void executeShellCommand(String command, IShellOutputReceiver receiver)
                            throws TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, IOException {
                        receiver.addOutput(address.getBytes(), 0, address.length());
                    }
                };
        mMockIDevice = mock(IDevice.class, AdditionalAnswers.delegatesTo(device));
        mTestDevice =
                new TestableAndroidNativeDevice() {
                    @Override
                    IHostOptions getHostOptions() {
                        return new HostOptions() {
                            @Override
                            public String getNetworkInterface() {
                                return "en0";
                            }
                        };
                    }

                    @Override
                    public boolean isAdbTcp() {
                        return true;
                    }

                    @Override
                    public boolean isAdbRoot() throws DeviceNotAvailableException {
                        return true;
                    }
                };
        assertEquals(
                "tcp:fe80:0:0:0:230:1bff:feba:8128%en0", mTestDevice.getFastbootSerialNumber());
    }

    @Test
    public void testBatchPrefetch() throws Exception {
        NativeDevice device =
                new TestableAndroidNativeDevice() {
                    @Override
                    public CommandResult executeShellV2Command(
                            String cmd,
                            final long maxTimeoutForCommand,
                            final TimeUnit timeUnit,
                            int retryAttempts)
                            throws DeviceNotAvailableException {
                        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
                        if (cmd.equals("getprop")) {
                            result.setStdout(
                                    String.join(
                                            "\n",
                                            "[prop1]: [value1]",
                                            "[ro.product.cpu.abi]: [x86_64]",
                                            "[ro.system.product.cpu.abilist]:"
                                                    + " [x86_64,x86,arm64-v8a,armeabi-v7a,armeabi]",
                                            "[open error",
                                            "[open error]: [no val",
                                            "build.ro: foo",
                                            "build.ro.",
                                            "[ro.build.version.sdk]: [99]",
                                            "[ro.build.version.codename]: [CremeBrule]",
                                            "[ro.build.id]: [AOSP.MASTER.hash]"));
                        } else if (cmd.equals("getprop ro.product.cpu.abi")) {
                            result.setStdout("");
                        }
                        return result;
                    }
                };

        // We don't have access to the cache, but we can call getProperty which will read
        // from the cache if the key is there.
        device.batchPrefetchStartupBuildProps();
        assertEquals("AOSP.MASTER.hash", device.getProperty("ro.build.id"));
        // And getProperty will issue more calls that we stub out with "" if we haven't cached.
        assertNull(device.getProperty("ro.product.cpu.abi"));
    }
}
