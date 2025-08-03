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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.AaptParser;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Unit Tests for {@link AppSetup}. */
@RunWith(JUnit4.class)
public class AppSetupTest {

    private static final String SERIAL = "serial";
    private AppSetup mAppSetup;
    @Mock ITestDevice mMockDevice;
    @Mock IBuildInfo mMockBuildInfo;
    private AaptParser mMockAaptParser;
    private List<VersionedFile> mApps;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mAppSetup = new AppSetup();

        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.isAppEnumerationSupported()).thenReturn(false);

        mMockAaptParser = Mockito.mock(AaptParser.class);
        mApps = new ArrayList<>();
        File tmpFile = FileUtil.createTempFile("versioned", ".test");
        mApps.add(new VersionedFile(tmpFile, "1"));
    }

    @After
    public void tearDown() {
        for (VersionedFile f : mApps) {
            FileUtil.deleteFile(f.getFile());
        }
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when the IBuildInfo doesn't contain
     * any apps.
     */
    @Test
    public void testSetup_notApps() throws Exception {
        // Inop setup
        mAppSetup.setUp(mMockDevice, new BuildInfo());
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when the apk installation fails with
     * some error.
     */
    @Test
    public void testSetup_failToInstall() throws Exception {
        List<VersionedFile> files = new ArrayList<>();
        File tmpFile = FileUtil.createTempFile("versioned", ".test");
        try {
            files.add(new VersionedFile(tmpFile, "1"));
            when(mMockBuildInfo.getAppPackageFiles()).thenReturn(files);
            when(mMockDevice.installPackage(eq(tmpFile), eq(true))).thenReturn("Error");

            mAppSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception.");
        } catch (BuildError expected) {
            assertEquals(
                    String.format(
                            "Failed to install %s on %s. Reason: Error null",
                            tmpFile.getName(), SERIAL),
                    expected.getMessage());
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} on the device that is api 34, no
     * force-queryable option in the installation command.
     */
    @Test
    public void testSetup_deviceApi34_forceQueryableIsFalse() throws Exception {
        mAppSetup =
                new AppSetup() {
                    @Override
                    AaptParser doAaptParse(File apkFile) {
                        return mMockAaptParser;
                    }
                };
        List<VersionedFile> files = new ArrayList<>();
        File tmpFile = FileUtil.createTempFile("versioned", ".test");
        try {
            files.add(new VersionedFile(tmpFile, "1"));
            when(mMockDevice.checkApiLevelAgainstNextRelease(Mockito.eq(34))).thenReturn(true);
            when(mMockDevice.isAppEnumerationSupported()).thenReturn(true);
            when(mMockBuildInfo.getAppPackageFiles()).thenReturn(files);
            when(mMockDevice.installPackage(eq(tmpFile), eq(true))).thenReturn(null);
            doReturn("com.fake.package").when(mMockAaptParser).getPackageName();

            mAppSetup.setUp(mMockDevice, mMockBuildInfo);
            verify(mMockDevice, atLeastOnce()).installPackage(eq(tmpFile), Mockito.eq(true));
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when installation succeed but we
     * cannot get any aapt info from the apk.
     */
    @Test
    public void testSetup_aaptCannotParse() throws Exception {
        List<VersionedFile> files = new ArrayList<>();
        File tmpFile = FileUtil.createTempFile("versioned", ".test");
        try {
            files.add(new VersionedFile(tmpFile, "1"));
            when(mMockBuildInfo.getAppPackageFiles()).thenReturn(files);
            when(mMockDevice.installPackage(eq(tmpFile), eq(true))).thenReturn(null);

            mAppSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception.");
        } catch (TargetSetupError expected) {
            assertEquals(
                    String.format(
                            "Failed to extract info from '%s' using aapt",
                            tmpFile.getAbsolutePath()),
                    expected.getMessage());
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when the install succeed but we
     * cannot find any package name on the apk.
     */
    @Test
    public void testSetup_noPackageName() throws Exception {
        mAppSetup =
                new AppSetup() {
                    @Override
                    AaptParser doAaptParse(File apkFile) {
                        return mMockAaptParser;
                    }
                };
        List<VersionedFile> files = new ArrayList<>();
        File tmpFile = FileUtil.createTempFile("versioned", ".test");
        try {
            files.add(new VersionedFile(tmpFile, "1"));
            when(mMockBuildInfo.getAppPackageFiles()).thenReturn(files);
            when(mMockDevice.installPackage(eq(tmpFile), eq(true))).thenReturn(null);
            doReturn(null).when(mMockAaptParser).getPackageName();

            mAppSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception.");
        } catch (TargetSetupError expected) {
            assertEquals(
                    String.format(
                            "Failed to find package name for '%s' using aapt",
                            tmpFile.getAbsolutePath()),
                    expected.getMessage());
        } finally {
            FileUtil.deleteFile(tmpFile);

            Mockito.verify(mMockAaptParser, times(1)).getPackageName();
        }
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when checking min sdk is enabled and
     * we fail to parse the file.
     */
    @Test
    public void testSetup_checkMinSdk_failParsing() throws Exception {
        mAppSetup =
                new AppSetup() {
                    @Override
                    AaptParser doAaptParse(File apkFile) {
                        return null;
                    }
                };
        OptionSetter setter = new OptionSetter(mAppSetup);
        setter.setOptionValue("check-min-sdk", "true");
        List<VersionedFile> files = new ArrayList<>();
        File tmpFile = FileUtil.createTempFile("versioned", ".test");
        try {
            files.add(new VersionedFile(tmpFile, "1"));
            when(mMockBuildInfo.getAppPackageFiles()).thenReturn(files);

            mAppSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception.");
        } catch (TargetSetupError expected) {
            assertEquals(
                    String.format("Failed to extract info from '%s' using aapt", tmpFile.getName()),
                    expected.getMessage());
        } finally {
            FileUtil.deleteFile(tmpFile);
        }
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when we check the min sdk level and
     * the device API level is too low. Install should be skipped.
     */
    @Test
    public void testSetup_checkMinSdk_apiLow() throws Exception {
        mAppSetup =
                new AppSetup() {
                    @Override
                    AaptParser doAaptParse(File apkFile) {
                        return mMockAaptParser;
                    }
                };
        OptionSetter setter = new OptionSetter(mAppSetup);
        setter.setOptionValue("check-min-sdk", "true");
        List<VersionedFile> files = new ArrayList<>();
        File tmpFile = FileUtil.createTempFile("versioned", ".test");
        try {
            files.add(new VersionedFile(tmpFile, "1"));
            when(mMockBuildInfo.getAppPackageFiles()).thenReturn(files);
            when(mMockDevice.getApiLevel()).thenReturn(21);
            doReturn(22).when(mMockAaptParser).getSdkVersion();

            mAppSetup.setUp(mMockDevice, mMockBuildInfo);
        } finally {
            FileUtil.deleteFile(tmpFile);
            verify(mMockDevice, times(2)).getApiLevel();

            Mockito.verify(mMockAaptParser, times(2)).getSdkVersion();
        }
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when api level of device is high
     * enough to install the apk.
     */
    @Test
    public void testSetup_checkMinSdk_apiOk() throws Exception {
        mAppSetup =
                new AppSetup() {
                    @Override
                    AaptParser doAaptParse(File apkFile) {
                        return mMockAaptParser;
                    }
                };
        OptionSetter setter = new OptionSetter(mAppSetup);
        setter.setOptionValue("check-min-sdk", "true");
        List<VersionedFile> files = new ArrayList<>();
        File tmpFile = FileUtil.createTempFile("versioned", ".test");
        try {
            files.add(new VersionedFile(tmpFile, "1"));
            when(mMockBuildInfo.getAppPackageFiles()).thenReturn(files);
            when(mMockDevice.getApiLevel()).thenReturn(23);
            doReturn(22).when(mMockAaptParser).getSdkVersion();
            when(mMockDevice.installPackage(eq(tmpFile), eq(true))).thenReturn(null);
            doReturn("com.fake.package").when(mMockAaptParser).getPackageName();

            mAppSetup.setUp(mMockDevice, mMockBuildInfo);
        } finally {
            FileUtil.deleteFile(tmpFile);

            Mockito.verify(mMockAaptParser, times(2)).getPackageName();
            Mockito.verify(mMockAaptParser, times(1)).getSdkVersion();
        }
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when running a post install command.
     */
    @Test
    public void testSetup_executePostInstall() throws Exception {
        when(mMockBuildInfo.getAppPackageFiles()).thenReturn(mApps);
        final String fakeCmd = "fake command";
        OptionSetter setter = new OptionSetter(mAppSetup);
        setter.setOptionValue("install", "false");
        setter.setOptionValue("post-install-cmd", fakeCmd);

        mAppSetup.setUp(mMockDevice, mMockBuildInfo);
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        eq(fakeCmd), any(), anyLong(), eq(TimeUnit.MILLISECONDS), eq(1));
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when uninstall all package is
     * requested but there is no package to uninstall.
     */
    @Test
    public void testSetup_uninstallAll_noPackage() throws Exception {
        when(mMockBuildInfo.getAppPackageFiles()).thenReturn(mApps);
        OptionSetter setter = new OptionSetter(mAppSetup);
        setter.setOptionValue("install", "false");
        setter.setOptionValue("uninstall-all", "true");
        Set<String> res = new HashSet<>();
        when(mMockDevice.getUninstallablePackageNames()).thenReturn(res);

        mAppSetup.setUp(mMockDevice, mMockBuildInfo);
    }

    /**
     * Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when uninstall is working and all
     * package to be uninstalled are uninstalled.
     */
    @Test
    public void testSetup_uninstallAll() throws Exception {
        when(mMockBuildInfo.getAppPackageFiles()).thenReturn(mApps);
        OptionSetter setter = new OptionSetter(mAppSetup);
        setter.setOptionValue("install", "false");
        setter.setOptionValue("uninstall-all", "true");
        Set<String> res = new HashSet<>();
        res.add("com.fake1");
        // Retunrs res for the first two times, and an empty hashset for the 3rd time.
        when(mMockDevice.getUninstallablePackageNames()).thenReturn(res, res, new HashSet<>());
        when(mMockDevice.uninstallPackage("com.fake1")).thenReturn(null);

        mAppSetup.setUp(mMockDevice, mMockBuildInfo);
        verify(mMockDevice, times(3)).getUninstallablePackageNames();
        verify(mMockDevice, times(2)).uninstallPackage("com.fake1");
    }

    /** Test for {@link AppSetup#setUp(ITestDevice, IBuildInfo)} when uninstall is failing. */
    @Test
    public void testSetup_uninstallAll_fails() throws Exception {
        when(mMockBuildInfo.getAppPackageFiles()).thenReturn(mApps);
        OptionSetter setter = new OptionSetter(mAppSetup);
        setter.setOptionValue("install", "false");
        setter.setOptionValue("uninstall-all", "true");
        Set<String> res = new HashSet<>();
        res.add("com.fake1");
        when(mMockDevice.getUninstallablePackageNames()).thenReturn(res);
        when(mMockDevice.uninstallPackage("com.fake1")).thenReturn("error");

        try {
            mAppSetup.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            assertEquals("Failed to uninstall apps on " + SERIAL, expected.getMessage());
        }
        verify(mMockDevice, times(4)).getUninstallablePackageNames();
        verify(mMockDevice, times(3)).uninstallPackage("com.fake1");
    }

    /**
     * Test for {@link AppSetup#tearDown(ITestDevice, IBuildInfo, Throwable)} when throwable is a
     * DNAE instance, it should just return.
     */
    @Test
    public void testTearDown_DNAE() throws Exception {

        mAppSetup.tearDown(
                mMockDevice, mMockBuildInfo, new DeviceNotAvailableException("test", "serial"));
    }

    /** Test for {@link AppSetup#tearDown(ITestDevice, IBuildInfo, Throwable)}. */
    @Test
    public void testTearDown() throws Exception {

        mAppSetup.tearDown(mMockDevice, mMockBuildInfo, null);
        verify(mMockDevice, times(1)).reboot();
    }
}
