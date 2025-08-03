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
package com.android.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
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
import java.util.ArrayList;
import java.util.Collection;

/** Unit tests for {@link InstallApkSetup} */
@RunWith(JUnit4.class)
public class InstallApkSetupTest {

    private static final String SERIAL = "SERIAL";
    private InstallApkSetup mInstallApkSetup;
    @Mock IDeviceBuildInfo mMockBuildInfo;
    @Mock ITestDevice mMockTestDevice;

    private File testDir = null;
    private File testFile = null;
    private Collection<File> testCollectionFiles = new ArrayList<File>();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mInstallApkSetup = new InstallApkSetup();

        when(mMockTestDevice.getSerialNumber()).thenReturn(SERIAL);
        when(mMockTestDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockTestDevice.isAppEnumerationSupported()).thenReturn(false);

        testDir = FileUtil.createTempDir("TestApkDir");
        testFile = FileUtil.createTempFile("File", ".apk", testDir);
    }

    @After
    public void tearDown() throws Exception {
        testCollectionFiles.clear();
        FileUtil.recursiveDelete(testDir);
    }

    /** Test {@link InstallApkSetupTest#setUp()} by successfully installing 2 Apk files */
    @Test
    public void testSetup() throws DeviceNotAvailableException, BuildError, TargetSetupError {
        testCollectionFiles.add(testFile);
        testCollectionFiles.add(testFile);
        mInstallApkSetup.setApkPaths(testCollectionFiles);
        when(mMockTestDevice.installPackage((File) Mockito.any(), Mockito.eq(true)))
                .thenReturn(null);

        mInstallApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        verify(mMockTestDevice, times(2)).installPackage((File) Mockito.any(), Mockito.eq(true));
    }

    /** Test {@link InstallApkSetupTest#setUp()} by successfully installing 2 Apk files */
    @Test
    public void testSetupForceQueryable()
            throws DeviceNotAvailableException, BuildError, TargetSetupError {
        when(mMockTestDevice.checkApiLevelAgainstNextRelease(Mockito.eq(34))).thenReturn(false);
        when(mMockTestDevice.isAppEnumerationSupported()).thenReturn(true);

        testCollectionFiles.add(testFile);
        testCollectionFiles.add(testFile);
        mInstallApkSetup.setApkPaths(testCollectionFiles);
        when(mMockTestDevice.installPackage(
                        (File) Mockito.any(), Mockito.eq(true), Mockito.eq("--force-queryable")))
                .thenReturn(null);

        mInstallApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        verify(mMockTestDevice, times(2))
                .installPackage(
                        (File) Mockito.any(), Mockito.eq(true), Mockito.eq("--force-queryable"));
    }

    /** Test {@link InstallApkSetupTest#setUp()} by successfully installing 2 Apk files */
    @Test
    public void testSetupDeviceApi34ForceQueryableIsFalse()
            throws DeviceNotAvailableException, BuildError, TargetSetupError {
        when(mMockTestDevice.checkApiLevelAgainstNextRelease(Mockito.eq(34))).thenReturn(true);
        when(mMockTestDevice.isAppEnumerationSupported()).thenReturn(true);

        testCollectionFiles.add(testFile);
        testCollectionFiles.add(testFile);
        mInstallApkSetup.setApkPaths(testCollectionFiles);
        when(mMockTestDevice.installPackage((File) Mockito.any(), Mockito.eq(true)))
                .thenReturn(null);

        mInstallApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        verify(mMockTestDevice, times(2)).installPackage((File) Mockito.any(), Mockito.eq(true));
    }

    /** Test {@link InstallApkSetupTest#setUp()} by installing a non-existing Apk */
    @Test
    public void testNonExistingApk() throws DeviceNotAvailableException, BuildError {
        testCollectionFiles.add(testFile);
        FileUtil.recursiveDelete(testFile);
        mInstallApkSetup.setApkPaths(testCollectionFiles);

        try {
            mInstallApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
            fail("should have failed due to missing APK file");
        } catch (TargetSetupError expected) {
            String refMessage = String.format("%s does not exist", testFile.getAbsolutePath());
            assertEquals(refMessage, expected.getMessage());
        }
    }

    /**
     * Test {@link InstallApkSetupTest#setUp()} by having an installation failure but not throwing
     * any exception
     */
    @Test
    public void testInstallFailureNoThrow()
            throws DeviceNotAvailableException, BuildError, TargetSetupError {
        testCollectionFiles.add(testFile);
        mInstallApkSetup.setApkPaths(testCollectionFiles);

        when(mMockTestDevice.installPackage((File) Mockito.any(), Mockito.eq(true)))
                .thenReturn(String.format("%s (Permission denied)", testFile.getAbsolutePath()));

        mInstallApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
        verify(mMockTestDevice, times(1)).installPackage((File) Mockito.any(), Mockito.eq(true));
    }

    /**
     * Test {@link InstallApkSetupTest#setUp()} by having an installation failure and throwing an
     * exception
     */
    @Test
    public void testInstallFailureThrow() throws DeviceNotAvailableException, BuildError {
        testCollectionFiles.add(testFile);
        mInstallApkSetup.setApkPaths(testCollectionFiles);
        mInstallApkSetup.setThrowIfInstallFail(true);

        when(mMockTestDevice.installPackage((File) Mockito.any(), Mockito.eq(true)))
                .thenReturn(String.format("%s (Permission denied)", testFile.getAbsolutePath()));

        try {
            mInstallApkSetup.setUp(mMockTestDevice, mMockBuildInfo);
            fail("should have failed due to installation failure");
        } catch (TargetSetupError expected) {
            String refMessage =
                    String.format(
                            "Stopping test: failed to install %s on device %s. "
                                    + "Reason: %s (Permission denied)",
                            testFile.getAbsolutePath(), SERIAL, testFile.getAbsolutePath());
            assertEquals(refMessage, expected.getMessage());
        }
        verify(mMockTestDevice, times(1)).installPackage((File) Mockito.any(), Mockito.eq(true));
    }
}
