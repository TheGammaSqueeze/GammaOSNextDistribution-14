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

package com.android.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;

/** Unit tests for {@link InstallAllTestZipAppsSetupTest} */
@RunWith(JUnit4.class)
public class InstallAllTestZipAppsSetupTest {

    private static final String SERIAL = "SERIAL";
    private InstallAllTestZipAppsSetup mPrep;
    @Mock IBuildInfo mMockBuildInfo;
    @Mock ITestDevice mMockTestDevice;
    private File mMockUnzipDir;
    private boolean mFailUnzip;
    private boolean mFailAapt;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mPrep =
                new InstallAllTestZipAppsSetup() {
                    @Override
                    File extractZip(File testsZip) throws IOException {
                        if (mFailUnzip) {
                            throw new IOException();
                        }
                        return mMockUnzipDir;
                    }

                    @Override
                    String getAppPackageName(File appFile) {
                        if (mFailAapt) {
                            return null;
                        }
                        return "";
                    }
                };
        mFailAapt = false;
        mFailUnzip = false;
        mMockUnzipDir = null;

        when(mMockTestDevice.getSerialNumber()).thenReturn(SERIAL);
        when(mMockTestDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockTestDevice.isAppEnumerationSupported()).thenReturn(false);
    }

    @After
    public void tearDown() throws Exception {
        if (mMockUnzipDir != null) {
            FileUtil.recursiveDelete(mMockUnzipDir);
        }
    }

    private void setMockUnzipDir() throws IOException {
        File testDir = FileUtil.createTempDir("TestAppSetupTest");
        // fake hierarchy of directory and files
        FileUtil.createTempFile("fakeApk", ".apk", testDir);
        FileUtil.createTempFile("fakeApk2", ".apk", testDir);
        FileUtil.createTempFile("notAnApk", ".txt", testDir);
        File subTestDir = FileUtil.createTempDir("SubTestAppSetupTest", testDir);
        FileUtil.createTempFile("subfakeApk", ".apk", subTestDir);
        mMockUnzipDir = testDir;
    }

    @Test
    public void testGetZipFile() throws TargetSetupError {
        String zip = "zip";
        mPrep.setTestZipName(zip);
        File file = new File(zip);
        when(mMockBuildInfo.getFile(zip)).thenReturn(file);

        File ret = mPrep.getZipFile(mMockTestDevice, mMockBuildInfo);
        assertEquals(file, ret);
    }

    @Test
    public void testGetZipFileDoesntExist() throws TargetSetupError {
        String zip = "zip";
        mPrep.setTestZipName(zip);
        when(mMockBuildInfo.getFile(zip)).thenReturn(null);

        File ret = mPrep.getZipFile(mMockTestDevice, mMockBuildInfo);
        assertNull(ret);
    }

    @Test
    public void testNullTestZipName() throws DeviceNotAvailableException {

        try {
            mPrep.setUp(mMockTestDevice, mMockBuildInfo);
            fail("Should have thrown a TargetSetupError");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    @Test
    public void testSuccess() throws Exception {
        mPrep.setTestZipName("zip");

        when(mMockBuildInfo.getFile((String) any())).thenReturn(new File("zip"));

        setMockUnzipDir();

        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
        mPrep.tearDown(mMockTestDevice, mMockBuildInfo, null);

        verify(mMockTestDevice, times(3)).installPackage((File) any(), anyBoolean());
        verify(mMockTestDevice, times(3)).uninstallPackage((String) any());
    }

    @Test
    public void testForceQueryableSuccess() throws Exception {
        when(mMockTestDevice.checkApiLevelAgainstNextRelease(eq(34))).thenReturn(false);
        when(mMockTestDevice.isAppEnumerationSupported()).thenReturn(true);
        mPrep.setTestZipName("zip");

        when(mMockBuildInfo.getFile((String) any())).thenReturn(new File("zip"));

        setMockUnzipDir();

        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
        mPrep.tearDown(mMockTestDevice, mMockBuildInfo, null);

        verify(mMockTestDevice, times(3))
                .installPackage(any(), anyBoolean(), eq("--force-queryable"));
        verify(mMockTestDevice, times(3)).uninstallPackage((String) any());
    }

    @Test
    public void testDeviceApi34ForceQueryableIsFalse() throws Exception {
        when(mMockTestDevice.checkApiLevelAgainstNextRelease(eq(34))).thenReturn(true);
        when(mMockTestDevice.isAppEnumerationSupported()).thenReturn(true);
        mPrep.setTestZipName("zip");

        when(mMockBuildInfo.getFile((String) any())).thenReturn(new File("zip"));

        setMockUnzipDir();

        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
        mPrep.tearDown(mMockTestDevice, mMockBuildInfo, null);

        verify(mMockTestDevice, times(3)).installPackage(any(), anyBoolean());
        verify(mMockTestDevice, times(3)).uninstallPackage((String) any());
    }

    @Test
    public void testSuccessNoTearDown() throws Exception {
        mPrep.setTestZipName("zip");
        mPrep.setCleanup(false);

        when(mMockBuildInfo.getFile((String) any())).thenReturn(new File("zip"));

        setMockUnzipDir();

        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
        mPrep.tearDown(mMockTestDevice, mMockBuildInfo, null);

        verify(mMockTestDevice, times(3)).installPackage((File) any(), anyBoolean());
    }

    @Test
    public void testInstallFailure() throws DeviceNotAvailableException {
        final String failure = "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED";
        final String file = "TEST";
        when(mMockTestDevice.installPackage((File) any(), eq(true))).thenReturn(failure);

        try {
            mPrep.installApk(new File(file), mMockTestDevice);
            fail("Should have thrown an exception");
        } catch (TargetSetupError e) {
            String expected =
                    String.format(
                            "Failed to install %s on %s. Reason: '%s'", file, SERIAL, failure);
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void testInstallFailureNoStop() throws DeviceNotAvailableException, TargetSetupError {
        final String failure = "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED";
        final String file = "TEST";
        mPrep.setStopInstallOnFailure(false);
        when(mMockTestDevice.installPackage((File) any(), eq(true))).thenReturn(failure);

        // should not throw exception
        mPrep.installApk(new File(file), mMockTestDevice);
    }

    @Test
    public void testDisable() throws Exception {
        OptionSetter setter = new OptionSetter(mPrep);
        setter.setOptionValue("disable", "true");

        mPrep.setUp(mMockTestDevice, mMockBuildInfo);
        mPrep.tearDown(mMockTestDevice, mMockBuildInfo, null);
    }

    @Test
    public void testUnzipFail() throws Exception {
        mFailUnzip = true;
        mPrep.setTestZipName("zip");

        when(mMockBuildInfo.getFile((String) any())).thenReturn(new File("zip"));

        try {
            mPrep.setUp(mMockTestDevice, mMockBuildInfo);
            fail("Should have thrown an exception");
        } catch (TargetSetupError e) {
            TargetSetupError error =
                    new TargetSetupError(
                            "Failed to extract test zip.",
                            e,
                            mMockTestDevice.getDeviceDescriptor());
            assertEquals(error.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testAaptFail() throws Exception {
        mFailAapt = true;
        mPrep.setTestZipName("zip");
        setMockUnzipDir();

        when(mMockBuildInfo.getFile((String) any())).thenReturn(new File("zip"));

        try {
            mPrep.setUp(mMockTestDevice, mMockBuildInfo);
            fail("Should have thrown an exception");
        } catch (TargetSetupError e) {
            TargetSetupError error =
                    new TargetSetupError(
                            "apk installed but AaptParser failed",
                            e,
                            mMockTestDevice.getDeviceDescriptor());
            assertEquals(error.getMessage(), e.getMessage());
        }
        verify(mMockTestDevice, times(1)).installPackage((File) any(), anyBoolean());
    }
}
