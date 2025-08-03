/*
 * Copyright (C) 2011 The Android Open Source Project
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.FileListingService;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.device.MockitoFileUtil;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@RunWith(JUnit4.class)
public class DefaultTestsZipInstallerTest {
    private static final String SKIP_THIS = "skipThis";

    private static final String TEST_STRING = "foo";

    private static final File SOME_PATH_1 = new File("/some/path/1");

    private static final File SOME_PATH_2 = new File("/some/path/2");

    @Mock ITestDevice mMockDevice;
    private IDeviceBuildInfo mDeviceBuild;
    private DefaultTestsZipInstaller mZipInstaller;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        ArrayList<String> skipThis = new ArrayList<String>(Arrays.asList(SKIP_THIS));
        mZipInstaller =
                new DefaultTestsZipInstaller(skipThis) {
                    @Override
                    File[] getTestsZipDataFiles(File hostDir, ITestDevice device) {
                        return new File[] {new File(TEST_STRING)};
                    }

                    @Override
                    Set<File> findDirs(File hostDir, File deviceRootPath) {
                        Set<File> files = new HashSet<File>(2);
                        files.add(SOME_PATH_1);
                        files.add(SOME_PATH_2);
                        return files;
                    }

                    @Override
                    IRunUtil getRunUtil() {
                        return mock(IRunUtil.class);
                    }
                };

        when(mMockDevice.getSerialNumber()).thenReturn(TEST_STRING);
        when(mMockDevice.getProductType()).thenReturn(TEST_STRING);
        when(mMockDevice.getBuildId()).thenReturn("1");

        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        mDeviceBuild = new DeviceBuildInfo("1", TEST_STRING);
    }

    @Test
    public void testSkipWipeFileSetup() throws Exception {
        DefaultTestsZipInstaller testZipInstaller = new DefaultTestsZipInstaller();
        Set<String> skipList = testZipInstaller.getDataWipeSkipList();
        assertNull("Invalid wipe list set.", skipList);
        testZipInstaller.setDataWipeSkipList("foo");
        skipList = testZipInstaller.getDataWipeSkipList();
        assertTrue("Invalid wipe list set. Missing value set.", skipList.contains("foo"));
        Collection<String> skipArrayList = new ArrayList<String>();
        skipArrayList.add("bar");
        skipArrayList.add("foobar");
        testZipInstaller.setDataWipeSkipList(skipArrayList);
        skipList = testZipInstaller.getDataWipeSkipList();
        assertFalse(
                "Invalid wipe list set, should not contain old value.", skipList.contains("foo"));
        assertTrue("Invalid wipe list set. Missing value set.", skipList.contains("bar"));
        assertTrue("Invalid wipe list set. Missing value set.", skipList.contains("foobar"));
    }

    @Test
    public void testCantTouchFilesystem() throws Exception {
        // expect initial android stop
        when(mMockDevice.getSerialNumber()).thenReturn("serial_number_stub");
        when(mMockDevice.getRecoveryMode()).thenReturn(RecoveryMode.AVAILABLE);
        when(mMockDevice.executeShellCommand("stop")).thenReturn("");
        when(mMockDevice.executeShellCommand("stop installd")).thenReturn("");

        // turtle!  (return false, for "write failed")
        when(mMockDevice.pushString((String) Mockito.any(), (String) Mockito.any()))
                .thenReturn(false);

        try {
            mZipInstaller.deleteData(mMockDevice);
            fail("Didn't throw TargetSetupError on failed write test");
        } catch (TargetSetupError e) {
            // expected
        }

        verify(mMockDevice).setRecoveryMode(RecoveryMode.ONLINE);
    }

    /** Exercise the core logic on a successful scenario. */
    @Test
    public void testPushTestsZipOntoData() throws Exception {
        // mock a filesystem with these contents:
        // /data/app
        // /data/$SKIP_THIS
        MockitoFileUtil.setMockDirContents(
                mMockDevice, FileListingService.DIRECTORY_DATA, "app", SKIP_THIS);

        // expect initial android stop
        when(mMockDevice.getSerialNumber()).thenReturn("serial_number_stub");
        when(mMockDevice.getRecoveryMode()).thenReturn(RecoveryMode.AVAILABLE);

        when(mMockDevice.executeShellCommand("stop")).thenReturn("");
        when(mMockDevice.executeShellCommand("stop installd")).thenReturn("");

        // turtle!  (to make sure filesystem is writable)
        when(mMockDevice.pushString((String) Mockito.any(), (String) Mockito.any()))
                .thenReturn(true);

        // expect 'rm app' but not 'rm $SKIP_THIS'
        when(mMockDevice.doesFileExist("data/app")).thenReturn(false);

        when(mMockDevice.syncFiles(
                        (File) Mockito.any(), Mockito.contains(FileListingService.DIRECTORY_DATA)))
                .thenReturn(Boolean.TRUE);

        when(mMockDevice.executeShellCommand(
                        Mockito.startsWith("chown system.system " + SOME_PATH_1.getPath())))
                .thenReturn("");
        when(mMockDevice.executeShellCommand(
                        Mockito.startsWith("chown system.system " + SOME_PATH_2.getPath())))
                .thenReturn("");

        mZipInstaller.pushTestsZipOntoData(mMockDevice, mDeviceBuild);

        verify(mMockDevice).setRecoveryMode(RecoveryMode.ONLINE);
        verify(mMockDevice).deleteFile("data/app");
        verify(mMockDevice).setRecoveryMode(RecoveryMode.AVAILABLE);
    }

    /** Test repeats to delete a dir are aborted */
    @Test
    public void testPushTestsZipOntoData_retry() throws Exception {
        // mock a filesystem with these contents:
        // /data/app
        // /data/$SKIP_THIS
        MockitoFileUtil.setMockDirContents(
                mMockDevice, FileListingService.DIRECTORY_DATA, "app", SKIP_THIS);

        // expect initial android stop
        when(mMockDevice.getSerialNumber()).thenReturn("serial_number_stub");
        when(mMockDevice.getRecoveryMode()).thenReturn(RecoveryMode.AVAILABLE);

        when(mMockDevice.executeShellCommand("stop")).thenReturn("");
        when(mMockDevice.executeShellCommand("stop installd")).thenReturn("");

        // turtle!  (to make sure filesystem is writable)
        when(mMockDevice.pushString((String) Mockito.any(), (String) Mockito.any()))
                .thenReturn(true);

        // expect 'rm app' but not 'rm $SKIP_THIS'
        when(mMockDevice.doesFileExist("data/app")).thenReturn(true);

        try {
            mZipInstaller.pushTestsZipOntoData(mMockDevice, mDeviceBuild);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
        verify(mMockDevice, times(3)).deleteFile("data/app");
        verify(mMockDevice).setRecoveryMode(RecoveryMode.ONLINE);
    }
}
