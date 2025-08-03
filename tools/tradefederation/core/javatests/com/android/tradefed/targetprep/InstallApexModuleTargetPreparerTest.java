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
package com.android.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.BundletoolUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Unit test for {@link InstallApexModuleTargetPreparer} */
@RunWith(JUnit4.class)
public class InstallApexModuleTargetPreparerTest {

    private static final String SERIAL = "serial";
    private InstallApexModuleTargetPreparer mInstallApexModuleTargetPreparer;
    @Mock
    IBuildInfo mMockBuildInfo;
    @Mock
    ITestDevice mMockDevice;
    private TestInformation mTestInfo;
    private BundletoolUtil mMockBundletoolUtil;
    private File mFakeApex;
    private File mFakeApex2;
    private File mFakeApex3;
    private File mFakeApk;
    private File mFakeApk2;
    private File mFakeApkZip;
    private File mFakeApkApks;
    private File mFakeApexApks;
    private File mBundletoolJar;
    private OptionSetter mSetter;
    private static final String APEX_PACKAGE_NAME = "com.android.FAKE_APEX_PACKAGE_NAME";
    private static final String APEX2_PACKAGE_NAME = "com.android.FAKE_APEX2_PACKAGE_NAME";
    private static final String APEX3_PACKAGE_NAME = "com.android.FAKE_APEX3_PACKAGE_NAME";
    private static final String APK_PACKAGE_NAME = "com.android.FAKE_APK_PACKAGE_NAME";
    private static final String APK2_PACKAGE_NAME = "com.android.FAKE_APK2_PACKAGE_NAME";
    private static final String SPLIT_APEX_PACKAGE_NAME =
            "com.android.SPLIT_FAKE_APEX_PACKAGE_NAME";
    private static final String SPLIT_APK_PACKAGE_NAME = "com.android.SPLIT_FAKE_APK_PACKAGE_NAME";
    private static final String APEX_PACKAGE_KEYWORD = "FAKE_APEX_PACKAGE_NAME";
    private static final long APEX_VERSION = 1;
    private static final String APEX_NAME = "fakeApex.apex";
    private static final String APEX2_NAME = "fakeApex_2.apex";
    private static final String APK_NAME = "fakeApk.apk";
    private static final String APK2_NAME = "fakeSecondApk.apk";
    private static final String SPLIT_APEX_APKS_NAME = "fakeApex.apks";
    private static final String SPLIT_APK__APKS_NAME = "fakeApk.apks";
    private static final String BUNDLETOOL_JAR_NAME = "bundletool.jar";
    private static final String APEX_DATA_DIR = "/data/apex/active/";
    private static final String STAGING_DATA_DIR = "/data/app-staging/";
    private static final String SESSION_DATA_DIR = "/data/apex/sessions/";
    private static final String APEX_STAGING_WAIT_TIME = "10";
    private static final String MODULE_PUSH_REMOTE_PATH = "/data/local/tmp/";
    protected static final String PARENT_SESSION_CREATION_CMD =
      "pm install-create --multi-package --staged --enable-rollback | egrep -o -e '[0-9]+'";
    protected static final String CHILD_SESSION_CREATION_CMD_APEX =
      "pm install-create --apex --staged --enable-rollback | egrep -o -e '[0-9]+'";
    protected static final String CHILD_SESSION_CREATION_CMD_APK =
      "pm install-create --staged --enable-rollback | egrep -o -e '[0-9]+'";
    protected static final String PARENT_SESSION_CREATION_ROLLBACK_NO_ENABLE_CMD =
      "pm install-create --multi-package --staged | egrep -o -e '[0-9]+'";
    protected static final String CHILD_SESSION_CREATION_ROLLBACK_NO_ENABLE_CMD_APEX =
      "pm install-create --apex --staged | egrep -o -e '[0-9]+'";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFakeApex = FileUtil.createTempFile("fakeApex", ".apex");
        mFakeApex2 = FileUtil.createTempFile("fakeApex_2", ".apex");
        mFakeApex3 = FileUtil.createTempFile("fakeApex_3", ".apex");
        mFakeApk = FileUtil.createTempFile("fakeApk", ".apk");
        mFakeApk2 = FileUtil.createTempFile("fakeSecondApk", ".apk");
        mFakeApkZip = FileUtil.createTempFile("fakeApkZip", ".zip");

        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.checkApiLevelAgainstNextRelease(30)).thenReturn(true);
        when(mMockDevice.getApiLevel()).thenReturn(100);
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();

        mInstallApexModuleTargetPreparer =
                new InstallApexModuleTargetPreparer() {
                    @Override
                    protected String getModuleKeywordFromApexPackageName(String packageName) {
                        return APEX_PACKAGE_KEYWORD;
                    }

                    @Override
                    protected String getBundletoolFileName() {
                        return BUNDLETOOL_JAR_NAME;
                    }

                    @Override
                    protected BundletoolUtil getBundletoolUtil() {
                        return mMockBundletoolUtil;
                    }

                    @Override
                    protected File getLocalPathForFilename(
                            TestInformation testInfo, String appFileName) throws TargetSetupError {
                        if (appFileName.endsWith(".apex")) {
                            if (appFileName.contains("fakeApex_2")) {
                                return mFakeApex2;
                            } else if (appFileName.contains("fakeApex_3")) {
                                return mFakeApex3;
                            }
                            return mFakeApex;
                        }
                        if (appFileName.endsWith(".apk")) {
                            if (appFileName.contains("Second")) {
                                return mFakeApk2;
                            } else {
                                return mFakeApk;
                            }
                        }
                        if (SPLIT_APEX_APKS_NAME.equals(appFileName)) {
                            return mFakeApexApks;
                        }
                        if (SPLIT_APK__APKS_NAME.equals(appFileName)) {
                            return mFakeApkApks;
                        }
                        if (appFileName.endsWith(".jar")) {
                            return mBundletoolJar;
                        }
                        if (appFileName.endsWith(".zip")) {
                            return mFakeApkZip;
                        }
                        return null;
                    }

                    @Override
                    protected String parsePackageName(File testAppFile) {
                        if (testAppFile.getName().endsWith(".apex")) {
                            if (testAppFile.getName().contains("fakeApex_2")) {
                                return APEX2_PACKAGE_NAME;
                            } else if (testAppFile.getName().contains("fakeApex_3")) {
                                return APEX3_PACKAGE_NAME;
                            } else if (testAppFile.getName().contains("Split")) {
                                return SPLIT_APEX_PACKAGE_NAME;
                            }
                            return APEX_PACKAGE_NAME;
                        }
                        if (testAppFile.getName().endsWith(".apk")
                                && !testAppFile.getName().contains("Split")) {
                            if (testAppFile.getName().contains("Second")) {
                                return APK2_PACKAGE_NAME;
                            } else {
                                return APK_PACKAGE_NAME;
                            }
                        }
                        if (testAppFile.getName().endsWith(".apk")
                                && testAppFile.getName().contains("Split")) {
                            return SPLIT_APK_PACKAGE_NAME;
                        }
                        if (testAppFile.getName().endsWith(".apks")
                                && testAppFile.getName().contains("fakeApk")) {
                            return SPLIT_APK_PACKAGE_NAME;
                        }
                        return null;
                    }

                    @Override
                    protected ApexInfo retrieveApexInfo(
                            File apex, DeviceDescriptor deviceDescriptor) {
                        ApexInfo apexInfo;
                        if (apex.getName().contains("Split")) {
                            apexInfo = new ApexInfo(SPLIT_APEX_PACKAGE_NAME, APEX_VERSION);
                        } else if (apex.getName().contains("fakeApex_2")) {
                            apexInfo = new ApexInfo(APEX2_PACKAGE_NAME, APEX_VERSION);
                        } else {
                            apexInfo = new ApexInfo(APEX_PACKAGE_NAME, APEX_VERSION);
                        }
                        return apexInfo;
                    }
                };

        mSetter = new OptionSetter(mInstallApexModuleTargetPreparer);
        mSetter.setOptionValue("cleanup-apks", "true");
        mSetter.setOptionValue("apex-staging-wait-time", APEX_STAGING_WAIT_TIME);
        mSetter.setOptionValue("apex-rollback-wait-time", APEX_STAGING_WAIT_TIME);
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mFakeApex);
        FileUtil.deleteFile(mFakeApex2);
        FileUtil.deleteFile(mFakeApex3);
        FileUtil.deleteFile(mFakeApk);
        FileUtil.deleteFile(mFakeApk2);
        FileUtil.deleteFile(mFakeApkZip);
        mMockBundletoolUtil = null;
    }

    /**
     * Test that it gets the correct apk files that are already installed on the /data directory.
     */
    @Test
    public void testGetApkModuleInData() throws Exception {
        Set<String> expected = new HashSet<>();
        Set<String> result = new HashSet<>();

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexData2 =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");

        Set<ApexInfo> apexes = new HashSet<>(Arrays.asList(fakeApexData, fakeApexData2));

        final String fakeName = "com.google.apk";
        final String fakeName2 = "com.google.apk2";
        final String fakeName3 = "com.google.apk3";
        final Set<String> mainlineModuleInfo =
                new HashSet<>(
                        Arrays.asList(
                                fakeName,
                                fakeName2,
                                fakeName3,
                                APEX_PACKAGE_NAME,
                                APEX2_PACKAGE_NAME));

        when(mMockDevice.getMainlineModuleInfo()).thenReturn(mainlineModuleInfo);
        when(mMockDevice.executeShellCommand(String.format("pm path %s", fakeName)))
                .thenReturn("package:/system/app/fakeApk/fakeApk.apk");
        when(mMockDevice.executeShellCommand(String.format("pm path %s", fakeName2)))
                .thenReturn("package:/data/app/fakeApk2/fakeApk2.apk");
        when(mMockDevice.executeShellCommand(String.format("pm path %s", fakeName3)))
                .thenReturn("package:/data/app/fakeApk3/fakeApk3.apk");

        expected = new HashSet<>(Arrays.asList(fakeName2, fakeName3));
        result = mInstallApexModuleTargetPreparer.getApkModuleInData(apexes, mMockDevice);
        assertEquals(2, result.size());
        assertEquals(expected, result);
    }

    /**
     * Test that it gets the correct apk files that the apex modules are excluded.
     */
    @Test
    public void testGetApkModules() throws Exception {
        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexData2 =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");

        Set<String> modules =
                new HashSet<>(
                        Arrays.asList(
                                APK_PACKAGE_NAME,
                                APK2_PACKAGE_NAME,
                                APEX_PACKAGE_NAME,
                                APEX2_PACKAGE_NAME));
        Set<ApexInfo> apexes = new HashSet<>(Arrays.asList(fakeApexData, fakeApexData2));
        Set<String> expected = new HashSet<>(Arrays.asList(APK_PACKAGE_NAME, APK2_PACKAGE_NAME));
        assertEquals(expected, mInstallApexModuleTargetPreparer.getApkModules(modules, apexes));
    }

    /**
     * Test that it gets the correct apex files that are already installed on the /data directory.
     */
    @Test
    public void testGetApexInData() throws Exception {
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        Set<String> expectedApex = new HashSet<>();

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexData2 =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexSystem =
                new ApexInfo(
                        "com.android.FAKE_APEX3_PACKAGE_NAME",
                        1,
                        "/system/apex/com.android.FAKE_APEX3_PACKAGE_NAME@1.apex");

        activatedApex = new HashSet<>(Arrays.asList(fakeApexData, fakeApexData2, fakeApexSystem));
        expectedApex = new HashSet<>(Arrays.asList(fakeApexData.name, fakeApexData2.name));
        assertEquals(2, mInstallApexModuleTargetPreparer.getApexInData(activatedApex).size());
        assertEquals(expectedApex, mInstallApexModuleTargetPreparer.getApexInData(activatedApex));

        activatedApex = new HashSet<>(Arrays.asList(fakeApexSystem));
        assertEquals(0, mInstallApexModuleTargetPreparer.getApexInData(activatedApex).size());
    }

    /**
     * Test that it returns the correct files to be installed and uninstalled.
     */
    @Test
    public void testGetModulesToUninstall_NoneUninstallAndInstallFiles() throws Exception {
        Set<String> apexInData = new HashSet<>();
        List<File> testFiles = new ArrayList<>();
        testFiles.add(mFakeApex);
        testFiles.add(mFakeApex2);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexData2 =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");

        apexInData.add(fakeApexData.name);
        apexInData.add(fakeApexData2.name);

        Set<String> results =
                mInstallApexModuleTargetPreparer.getModulesToUninstall(
                        apexInData, testFiles, mMockDevice);

        assertEquals(0, testFiles.size());
        assertEquals(0, results.size());
    }

    /**
     * Test that it returns the correct files to be installed and uninstalled.
     */
    @Test
    public void testGetModulesToUninstall_UninstallAndInstallFiles() throws Exception {
        Set<String> apexInData = new HashSet<>();
        List<File> testFiles = new ArrayList<>();
        testFiles.add(mFakeApex3);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexData2 =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");

        apexInData.add(fakeApexData.name);
        apexInData.add(fakeApexData2.name);

        Set<String> results =
                mInstallApexModuleTargetPreparer.getModulesToUninstall(
                        apexInData, testFiles, mMockDevice);
        assertEquals(1, testFiles.size());
        assertEquals(mFakeApex3, testFiles.get(0));
        assertEquals(2, results.size());
        assertTrue(results.containsAll(apexInData));
    }

    /**
     * Test that it returns the correct files to be installed and uninstalled.
     */
    @Test
    public void testGetModulesToUninstall_UninstallAndInstallFiles2() throws Exception {
        Set<String> apexInData = new HashSet<>();
        List<File> testFiles = new ArrayList<>();
        testFiles.add(mFakeApex2);
        testFiles.add(mFakeApex3);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexData2 =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");

        apexInData.add(fakeApexData.name);
        apexInData.add(fakeApexData2.name);

        Set<String> results =
                mInstallApexModuleTargetPreparer.getModulesToUninstall(
                        apexInData, testFiles, mMockDevice);
        assertEquals(1, testFiles.size());
        assertEquals(mFakeApex3, testFiles.get(0));
        assertEquals(1, results.size());
        assertTrue(results.contains(fakeApexData.name));
    }

    /**
     * Test the method behaves the same process when the files to be installed contain apk or apks.
     */
    @Test
    public void testSetupAndTearDown_Optimize_APEXANDAPK_NoReboot() throws Exception {
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");
        doReturn(new HashSet<>())
                .doReturn(new HashSet<>())
                .doReturn(new HashSet<>(Arrays.asList(fakeApexData)))
                .when(mMockDevice)
                .getActiveApexes();
        when(mMockDevice.getMainlineModuleInfo()).thenReturn(new HashSet<>());
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex, mFakeApk));
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APK_PACKAGE_NAME);
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verifySuccessfulInstallMultiPackages();
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, times(1)).getMainlineModuleInfo();
    }

    /**
     * Test the method will not install and reboot device as all apk/apex are installed already.
     */
    @Test
    public void testSetupAndTearDown_Optimize_APEXANDAPK_NoInstallAndReboot() throws Exception {
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");
        when(mMockDevice.getActiveApexes()).thenReturn(new HashSet<>(Arrays.asList(fakeApexData)));
        when(mMockDevice.getMainlineModuleInfo())
                .thenReturn(new HashSet<>(Arrays.asList(APK_PACKAGE_NAME)));
        when(mMockDevice.executeShellCommand(String.format("pm path %s", APK_PACKAGE_NAME)))
                .thenReturn("package:/data/app/fakeApk/fakeApk.apk");
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APK_PACKAGE_NAME);
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, atLeastOnce()).getMainlineModuleInfo();
    }

    /**
     * Test the method will install and reboot device when installing an apk.
     */
    @Test
    public void testSetupAndTearDown_Optimize_APEXANDAPK_InstallAndReboot() throws Exception {
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");
        when(mMockDevice.getActiveApexes()).thenReturn(new HashSet<>(Arrays.asList(fakeApexData)));
        when(mMockDevice.getMainlineModuleInfo())
                .thenReturn(new HashSet<>(Arrays.asList(APK_PACKAGE_NAME)));
        when(mMockDevice.executeShellCommand(String.format("pm path %s", APK_PACKAGE_NAME)))
                .thenReturn("package:/system/app/fakeApk/fakeApk.apk");
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApk));
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APK_PACKAGE_NAME);
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verifySuccessfulInstallPackages(Arrays.asList(mFakeApk));
        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, atLeastOnce()).getMainlineModuleInfo();
    }

    /**
     * Test the method will uninstall and reboot device as uninstalling apk modules.
     */
    @Test
    public void testSetupAndTearDown_Optimize_APEXANDAPK_UnInstallAPKAndReboot() throws Exception {
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");
        when(mMockDevice.getActiveApexes()).thenReturn(new HashSet<>(Arrays.asList(fakeApexData)));
        when(mMockDevice.getMainlineModuleInfo())
                .thenReturn(new HashSet<>(Arrays.asList(APK_PACKAGE_NAME, APK2_PACKAGE_NAME)));
        when(mMockDevice.executeShellCommand(String.format("pm path %s", APK_PACKAGE_NAME)))
                .thenReturn("package:/data/app/fakeApk/fakeApk.apk");
        when(mMockDevice.executeShellCommand(String.format("pm path %s", APK2_PACKAGE_NAME)))
                .thenReturn("package:/data/app/fakeSecondApk/fakeSecondApk.apk");
        Set<String> installableModules = new HashSet<>();
        when(mMockDevice.uninstallPackage(Mockito.any())).thenReturn(null);

        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, atLeastOnce()).getMainlineModuleInfo();
        verify(mMockDevice, times(2)).uninstallPackage(Mockito.any());
        verify(mMockDevice).reboot();
    }

    /**
     * Test the method will uninstall and reboot device as uninstalling apex modules.
     */
    @Test
    public void testSetupAndTearDown_Optimize_APEXANDAPK_UnInstallAPEXANDReboot() throws Exception {
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APK2_NAME);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");
        when(mMockDevice.getActiveApexes()).thenReturn(new HashSet<>(Arrays.asList(fakeApexData)));
        when(mMockDevice.getMainlineModuleInfo())
                .thenReturn(new HashSet<>(Arrays.asList(APK_PACKAGE_NAME, APK2_PACKAGE_NAME)));
        when(mMockDevice.executeShellCommand(String.format("pm path %s", APK_PACKAGE_NAME)))
                .thenReturn("package:/data/app/fakeApk/fakeApk.apk");
        when(mMockDevice.executeShellCommand(String.format("pm path %s", APK2_PACKAGE_NAME)))
                .thenReturn("package:/data/app/fakeSecondApk/fakeSecondApk.apk");
        Set<String> installableModules = new HashSet<>();
        when(mMockDevice.uninstallPackage(Mockito.any())).thenReturn(null);

        installableModules.add(APK2_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, atLeastOnce()).getMainlineModuleInfo();
        verify(mMockDevice, times(2)).uninstallPackage(Mockito.any());
        verify(mMockDevice).reboot();
    }

    /**
     * Test the method will optimize the process and it will not reboot because the files to be
     * installed are already installed on the device.
     */
    @Test
    public void testSetupAndTearDown_Optimize_MultipleAPEX_NoReboot() throws Exception {
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APEX2_NAME);

        Set<ApexInfo> apexInData = new HashSet<>();
        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexData2 =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");

        apexInData.add(fakeApexData);
        apexInData.add(fakeApexData2);
        when(mMockDevice.getActiveApexes()).thenReturn(apexInData);
        when(mMockDevice.getMainlineModuleInfo()).thenReturn(new HashSet<>());
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        installableModules.add(APEX2_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verify(mMockDevice, times(2)).getActiveApexes();
        verify(mMockDevice, times(1)).getMainlineModuleInfo();
    }

    /**
     * Test the method will uninstall the unused files and install the required files for the
     * current test, and finally reboot the device.
     */
    @Test
    public void testSetupAndTearDown_Optimize_MultipleAPEX_UninstallThenInstallAndReboot()
            throws Exception {
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX2_NAME);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexData2 =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");

        doReturn(new HashSet<>(Arrays.asList(fakeApexData)))
                .doReturn(new HashSet<>(Arrays.asList(fakeApexData)))
                .doReturn(new HashSet<>(Arrays.asList(fakeApexData2)))
                .when(mMockDevice)
                .getActiveApexes();
        when(mMockDevice.getMainlineModuleInfo()).thenReturn(new HashSet<>());
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX2_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        when(mMockDevice.uninstallPackage(Mockito.any())).thenReturn(null);
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex2));

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifySuccessfulInstallPackages(Arrays.asList(mFakeApex2));
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, times(1)).getMainlineModuleInfo();
        verify(mMockDevice, times(1)).uninstallPackage(Mockito.any());
    }

    /**
     * Test the method will uninstall the unused files for the current test, and finally reboot the
     * device.
     */
    @Test
    public void testSetupAndTearDown_Optimize_MultipleAPEX_UninstallAndReboot() throws Exception {
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX2_NAME);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexData2 =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");

        when(mMockDevice.getActiveApexes())
                .thenReturn(new HashSet<>(Arrays.asList(fakeApexData, fakeApexData2)));
        when(mMockDevice.getMainlineModuleInfo()).thenReturn(new HashSet<>());
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX2_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        when(mMockDevice.uninstallPackage(Mockito.any())).thenReturn(null);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).reboot();
        verify(mMockDevice, times(2)).getActiveApexes();
        verify(mMockDevice, times(1)).getMainlineModuleInfo();
        verify(mMockDevice, times(1)).uninstallPackage(Mockito.any());
    }

    /**
     * Test the method will install the required files for the current test, and finally reboot the
     * device.
     */
    @Test
    public void testSetupAndTearDown_Optimize_MultipleAPEX_Reboot() throws Exception {
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APEX2_NAME);

        Set<ApexInfo> apexInData = new HashSet<>();
        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");

        ApexInfo fakeApexData2 =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");

        apexInData.add(fakeApexData);
        apexInData.add(fakeApexData2);
        when(mMockDevice.getMainlineModuleInfo()).thenReturn(new HashSet<>());
        doReturn(new HashSet<>(Arrays.asList(fakeApexData)))
                .doReturn(apexInData)
                .when(mMockDevice)
                .getActiveApexes();
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        installableModules.add(APEX2_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex2));

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifySuccessfulInstallPackages(Arrays.asList(mFakeApex2));
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, times(1)).getMainlineModuleInfo();
    }

    @Test
    public void testSetupSuccess_removeExistingStagedApexSuccess() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));
        setActivatedApex();
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verifySuccessfulInstallPackages(Arrays.asList(mFakeApex));
        verifyCleanInstalledApexPackages(1);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(3)).getActiveApexes();
    }

    @Test
    public void testSetupSuccess_noDataUnderApexDataDirs() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        CommandResult res = new CommandResult();
        res.setStdout("");
        when(mMockDevice.executeShellV2Command("ls " + APEX_DATA_DIR)).thenReturn(res);
        when(mMockDevice.executeShellV2Command("ls " + SESSION_DATA_DIR)).thenReturn(res);
        when(mMockDevice.executeShellV2Command("ls " + STAGING_DATA_DIR)).thenReturn(res);
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));
        setActivatedApex();
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verifySuccessfulInstallPackages(Arrays.asList(mFakeApex));
        verify(mMockDevice, times(3)).getActiveApexes();
    }

    @Test
    public void testSetupSuccess_getActivatedPackageSuccess() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));
        setActivatedApex();
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verifySuccessfulInstallPackages(Arrays.asList(mFakeApex));
        verifyCleanInstalledApexPackages(1);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(3)).getActiveApexes();
    }

    @Test
    public void testSetupSuccess_withAbsoluteTestFileName() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFile(mFakeApex);

        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));
        setActivatedApex();
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verifySuccessfulInstallPackages(Arrays.asList(mFakeApex));
        verifyCleanInstalledApexPackages(1);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(3)).getActiveApexes();
    }

    @Test(expected = TargetSetupError.class)
    public void testSetupFail_getActivatedPackageSuccessThrowModuleNotPreloaded() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);

        mockCleanInstalledApexPackages();
        setActivatedApex();
        when(mMockDevice.getInstalledPackageNames()).thenReturn(new HashSet<>());

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verifyCleanInstalledApexPackages(1);
        verify(mMockDevice, times(1)).reboot();
        verify(mMockDevice, times(2)).getActiveApexes();
    }

    @Test
    public void testSetupFail_getActivatedPackageFail() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);

        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));
        when(mMockDevice.getActiveApexes()).thenReturn(new HashSet<ApexInfo>());
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        try {
            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            assertTrue(
                    expected.getMessage()
                            .contains("Failed to retrieve activated apex on device serial."));
        } finally {
            verifySuccessfulInstallPackages(Arrays.asList(mFakeApex));
            verify(mMockDevice, times(1)).deleteFile(APEX_DATA_DIR + "*");
            verify(mMockDevice, times(1)).deleteFile(SESSION_DATA_DIR + "*");
            verify(mMockDevice, times(1)).deleteFile(STAGING_DATA_DIR + "*");
            verify(mMockDevice, times(2)).reboot();
            verify(mMockDevice, times(3)).getActiveApexes();
        }
    }

    @Test
    public void testSetupFail_apexActivationFailPackageNameWrong() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);

        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(
                new ApexInfo(
                        "com.android.FAKE_APEX_PACKAGE_NAME_TO_FAIL",
                        1,
                        "/system/apex/com.android.FAKE_APEX_PACKAGE_NAME_TO_FAIL.apex"));
        when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        try {
            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            String failureMsg =
                    String.format(
                            "packageName: %s, versionCode: %d", APEX_PACKAGE_NAME, APEX_VERSION);
            assertTrue(expected.getMessage().contains(failureMsg));
        } finally {
            verifySuccessfulInstallPackages(Arrays.asList(mFakeApex));
            verify(mMockDevice, times(1)).deleteFile(APEX_DATA_DIR + "*");
            verify(mMockDevice, times(1)).deleteFile(SESSION_DATA_DIR + "*");
            verify(mMockDevice, times(1)).deleteFile(STAGING_DATA_DIR + "*");
            verify(mMockDevice, times(2)).reboot();
            verify(mMockDevice, times(3)).getActiveApexes();
        }
    }

    @Test
    public void testSetupFail_apexActivationFailVersionWrong() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);

        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(
                new ApexInfo(
                        "com.android.FAKE_APEX_PACKAGE_NAME",
                        0,
                        "/system/apex/com.android.FAKE_APEX_PACKAGE_NAME.apex"));
        when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        try {
            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            String failureMsg =
                    String.format(
                            "packageName: %s, versionCode: %d", APEX_PACKAGE_NAME, APEX_VERSION);
            assertTrue(expected.getMessage().contains(failureMsg));
        } finally {
            verifySuccessfulInstallPackages(Arrays.asList(mFakeApex));
            verify(mMockDevice, times(1)).deleteFile(APEX_DATA_DIR + "*");
            verify(mMockDevice, times(1)).deleteFile(SESSION_DATA_DIR + "*");
            verify(mMockDevice, times(1)).deleteFile(STAGING_DATA_DIR + "*");
            verify(mMockDevice, times(2)).reboot();
            verify(mMockDevice, times(3)).getActiveApexes();
        }
    }

    @Test
    public void testSetupFail_apexActivationFailSourceDirWrong() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);

        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(
                new ApexInfo(
                        "com.android.FAKE_APEX_PACKAGE_NAME",
                        1,
                        "/system/apex/com.android.FAKE_APEX_PACKAGE_NAME.apex"));
        when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        try {
            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            String failureMsg =
                    String.format(
                            "packageName: %s, versionCode: %d", APEX_PACKAGE_NAME, APEX_VERSION);
            assertTrue(expected.getMessage().contains(failureMsg));
        } finally {
            verifySuccessfulInstallPackages(Arrays.asList(mFakeApex));
            verify(mMockDevice, times(1)).deleteFile(APEX_DATA_DIR + "*");
            verify(mMockDevice, times(1)).deleteFile(SESSION_DATA_DIR + "*");
            verify(mMockDevice, times(1)).deleteFile(STAGING_DATA_DIR + "*");
            verify(mMockDevice, times(2)).reboot();
            verify(mMockDevice, times(3)).getActiveApexes();
        }
    }

    @Test
    public void testSetupSuccess_activatedSuccessOnQ() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);

        mockCleanInstalledApexPackages();
        when(mMockDevice.checkApiLevelAgainstNextRelease(Mockito.anyInt())).thenReturn(false);
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(new ApexInfo("com.android.FAKE_APEX_PACKAGE_NAME", 1, ""));
        when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verifySuccessfulInstallPackages(Arrays.asList(mFakeApex));
        verifyCleanInstalledApexPackages(1);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(3)).getActiveApexes();
    }

    @Test
    public void testSetupAndTearDown_SingleApk() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);

        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApk));
        when(mMockDevice.installPackage(
                (File) Mockito.any(),
                Mockito.eq(true),
                Mockito.eq("--enable-rollback"),
                Mockito.eq("--staged")))
                .thenReturn(null);
        when(mMockDevice.uninstallPackage(APK_PACKAGE_NAME)).thenReturn(null);
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APK_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        doReturn(new HashSet<ApexInfo>())
                .doReturn(ImmutableSet.of())
                .when(mMockDevice)
                .getActiveApexes();

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(1);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(1)).uninstallPackage(APK_PACKAGE_NAME);
        verify(mMockDevice, times(2)).getActiveApexes();
    }

    @Test
    public void testSetupAndTearDown_InstallMultipleApk() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APK2_NAME);

        mockCleanInstalledApexPackages();
        List<File> apks = new ArrayList<>();
        apks.add(mFakeApk);
        apks.add(mFakeApk2);
        mockSuccessfulInstallMultiPackages(apks);
        when(mMockDevice.uninstallPackage(APK_PACKAGE_NAME)).thenReturn(null);
        when(mMockDevice.uninstallPackage(APK2_PACKAGE_NAME)).thenReturn(null);
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APK_PACKAGE_NAME);
        installableModules.add(APK2_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        doReturn(new HashSet<ApexInfo>())
                .doReturn(ImmutableSet.of())
                .when(mMockDevice)
                .getActiveApexes();

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(1);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(1)).uninstallPackage(APK_PACKAGE_NAME);
        verify(mMockDevice, times(1)).uninstallPackage(APK2_PACKAGE_NAME);
        verify(mMockDevice, times(2)).getActiveApexes();
    }

    @Test
    public void testSetupAndTearDown_ApkAndApks() throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APK__APKS_NAME);
        mFakeApkApks = File.createTempFile("fakeApk", ".apks");

        File fakeSplitApkApks = File.createTempFile("ApkSplits", "");
        fakeSplitApkApks.delete();
        fakeSplitApkApks.mkdir();
        File splitApk1 = File.createTempFile("fakeSplitApk1", ".apk", fakeSplitApkApks);
        mBundletoolJar = File.createTempFile("bundletool", ".jar");
        File splitApk2 = File.createTempFile("fakeSplitApk2", ".apk", fakeSplitApkApks);
        try {
            mockCleanInstalledApexPackages();
            when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                    .thenReturn("serial.json");

            assertTrue(fakeSplitApkApks != null);
            assertTrue(mFakeApkApks != null);
            assertEquals(2, fakeSplitApkApks.listFiles().length);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    Mockito.eq(mFakeApkApks),
                    anyString(),
                    Mockito.any(ITestDevice.class),
                    Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApkApks);

            List<String> trainInstallCmd = new ArrayList<>();
            trainInstallCmd.add("install-multi-package");
            trainInstallCmd.add("--enable-rollback");
            trainInstallCmd.add(mFakeApk.getAbsolutePath());
            String cmd = "";
            for (File f : fakeSplitApkApks.listFiles()) {
                if (!cmd.isEmpty()) {
                    cmd += ":" + f.getParentFile().getAbsolutePath() + "/" + f.getName();
                } else {
                    cmd += f.getParentFile().getAbsolutePath() + "/" + f.getName();
                }
            }
            trainInstallCmd.add(cmd);
            when(mMockDevice.executeAdbCommand(trainInstallCmd.toArray(new String[0])))
                    .thenReturn("Success");

            when(mMockDevice.uninstallPackage(APK_PACKAGE_NAME)).thenReturn(null);
            when(mMockDevice.uninstallPackage(SPLIT_APK_PACKAGE_NAME)).thenReturn(null);
            Set<String> installableModules = new HashSet<>();
            installableModules.add(APK_PACKAGE_NAME);
            installableModules.add(SPLIT_APK_PACKAGE_NAME);
            when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
            doReturn(new HashSet<ApexInfo>())
                    .doReturn(ImmutableSet.of())
                    .when(mMockDevice)
                    .getActiveApexes();

            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
            Mockito.verify(mMockBundletoolUtil, times(1))
                    .generateDeviceSpecFile(Mockito.any(ITestDevice.class));
            // Extract splits 1 time to get the package name for the module, and again during
            // installation.
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApkApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            verify(mMockDevice, times(1)).deleteFile(APEX_DATA_DIR + "*");
            verify(mMockDevice, times(1)).deleteFile(SESSION_DATA_DIR + "*");
            verify(mMockDevice, times(1)).deleteFile(STAGING_DATA_DIR + "*");
            verify(mMockDevice, times(2)).reboot();
            verify(mMockDevice, times(1)).executeAdbCommand(trainInstallCmd.toArray(new String[0]));
            verify(mMockDevice, times(1)).uninstallPackage(APK_PACKAGE_NAME);
            verify(mMockDevice, times(1)).uninstallPackage(SPLIT_APK_PACKAGE_NAME);
            verify(mMockDevice, times(2)).getActiveApexes();
            verify(mMockDevice).waitForDeviceAvailable();
            assertTrue(!mInstallApexModuleTargetPreparer.getApkInstalled().isEmpty());
        } finally {
            FileUtil.deleteFile(mFakeApexApks);
            FileUtil.deleteFile(mFakeApkApks);
            FileUtil.recursiveDelete(fakeSplitApkApks);
            FileUtil.deleteFile(fakeSplitApkApks);
            FileUtil.deleteFile(mBundletoolJar);
        }
    }

    @Test
    public void testSetupAndTearDown() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));
        setActivatedApex();

        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        when(mMockDevice.executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME))
                .thenReturn("Success");

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifySuccessfulInstallPackages(Arrays.asList(mFakeApex));
        verifyCleanInstalledApexPackages(2);
        verify(mMockDevice, times(3)).reboot();
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
    }

    @Test
    public void testGetModuleKeyword() {
        mInstallApexModuleTargetPreparer = new InstallApexModuleTargetPreparer();
        final String testApex1PackageName = "com.android.foo";
        final String testApex2PackageName = "com.android.bar_test";
        assertEquals(
                "foo",
                mInstallApexModuleTargetPreparer.getModuleKeywordFromApexPackageName(
                        testApex1PackageName));
        assertEquals(
                "bar_test",
                mInstallApexModuleTargetPreparer.getModuleKeywordFromApexPackageName(
                        testApex2PackageName));
    }

    @Test
    public void testSetupAndTearDown_InstallApkAndApex() throws Exception {
        mockCleanInstalledApexPackages();
        Set<String> installableModules = setupInstallableModulesSingleApexSingleApk();
        setActivatedApex();

        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex, mFakeApk));

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(2);
        verifySuccessfulInstallMultiPackages();
        verify(mMockDevice, times(3)).reboot();
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
    }

    @Test
    public void testSetupAndTearDown_InstallApkAndApexOnQ() throws Exception {
        when(mMockDevice.getApiLevel()).thenReturn(29);
        mockCleanInstalledApexPackages();
        Set<String> installableModules = setupInstallableModulesSingleApexSingleApk();
        setActivatedApex();

        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        List<String> trainInstallCmd = new ArrayList<>();
        trainInstallCmd.add("install-multi-package");
        trainInstallCmd.add("--staged");
        trainInstallCmd.add("--enable-rollback");
        trainInstallCmd.add(mFakeApex.getAbsolutePath());
        trainInstallCmd.add(mFakeApk.getAbsolutePath());
        when(mMockDevice.executeAdbCommand(trainInstallCmd.toArray(new String[0])))
            .thenReturn("Success");


        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(2);
        verify(mMockDevice, times(3)).reboot();
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
        verify(mMockDevice, times(1)).executeAdbCommand(trainInstallCmd.toArray(new String[0]));
    }

    @Test
    public void testSetupAndTearDown_FilePushFail() throws Exception {
        mockCleanInstalledApexPackages();
        Set<String> installableModules = setupInstallableModulesSingleApexSingleApk();

        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        for (File f : Arrays.asList(mFakeApk, mFakeApex)) {
            when(mMockDevice.pushFile(f, MODULE_PUSH_REMOTE_PATH + f.getName()))
                    .thenReturn(Boolean.FALSE);
        }
        try {
            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            assertTrue(
                    expected.getMessage()
                            .contains("Failed to push local"));
        }
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(2);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(2)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
    }

    @Test
    public void testSetupAndTearDown_ParentSessionCreationFail() throws Exception {
        mockCleanInstalledApexPackages();
        Set<String> installableModules = setupInstallableModulesSingleApexSingleApk();

        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        for (File f : Arrays.asList(mFakeApk, mFakeApex)) {
            when(mMockDevice.pushFile(f, MODULE_PUSH_REMOTE_PATH + f.getName()))
                    .thenReturn(Boolean.TRUE);
        }
        CommandResult parent_session_creation_res = new CommandResult();
        parent_session_creation_res.setStatus(CommandStatus.FAILED);
        parent_session_creation_res.setStderr("I am an error!");
        parent_session_creation_res.setStdout("I am the output");
        when(mMockDevice.executeShellV2Command(PARENT_SESSION_CREATION_CMD))
          .thenReturn(parent_session_creation_res);
        try {
            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            assertTrue(
                    expected.getMessage()
                            .equals(
                             String.format("Failed to create parent session. Error: %s, Stdout: %s",
                                            parent_session_creation_res.getStderr(),
                                            parent_session_creation_res.getStdout())));
        }
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(2);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(2)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
    }

    @Test
    public void testSetupAndTearDown_ChildSessionCreationFail() throws Exception {
        mockCleanInstalledApexPackages();
        Set<String> installableModules = setupInstallableModulesSingleApexSingleApk();

        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        for (File f : Arrays.asList(mFakeApk, mFakeApex)) {
            when(mMockDevice.pushFile(f, MODULE_PUSH_REMOTE_PATH + f.getName()))
                    .thenReturn(Boolean.TRUE);
        }
        CommandResult parent_session_creation_res = new CommandResult();
        parent_session_creation_res.setStatus(CommandStatus.SUCCESS);
        when(mMockDevice.executeShellV2Command(PARENT_SESSION_CREATION_CMD))
          .thenReturn(parent_session_creation_res);
        CommandResult child_session_creation_res = new CommandResult();
        child_session_creation_res.setStatus(CommandStatus.FAILED);
        when(mMockDevice.executeShellV2Command(CHILD_SESSION_CREATION_CMD_APEX))
          .thenReturn(child_session_creation_res);
        when(mMockDevice.executeShellV2Command(CHILD_SESSION_CREATION_CMD_APK))
          .thenReturn(child_session_creation_res);
        try {
            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            assertTrue(
                    expected.getMessage()
                            .contains("Failed to create child session for"));
        }
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(2);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(2)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
    }

    @Test
    public void testSetupAndTearDown_FileWrittenToSessionFail() throws Exception {
        mockCleanInstalledApexPackages();
        Set<String> installableModules = setupInstallableModulesSingleApexSingleApk();

        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        for (File f : Arrays.asList(mFakeApk, mFakeApex)) {
            when(mMockDevice.pushFile(f, MODULE_PUSH_REMOTE_PATH + f.getName()))
                    .thenReturn(Boolean.TRUE);
        }
        CommandResult session_creation_res = new CommandResult();
        session_creation_res.setStatus(CommandStatus.SUCCESS);
        session_creation_res.setStdout("1");
        when(mMockDevice.executeShellV2Command(PARENT_SESSION_CREATION_CMD))
          .thenReturn(session_creation_res);;
        when(mMockDevice.executeShellV2Command(CHILD_SESSION_CREATION_CMD_APEX))
          .thenReturn(session_creation_res);
        when(mMockDevice.executeShellV2Command(CHILD_SESSION_CREATION_CMD_APK))
          .thenReturn(session_creation_res);
        CommandResult write_to_session_res = new CommandResult();
        write_to_session_res.setStatus(CommandStatus.FAILED);
        write_to_session_res.setStderr("I am an error!");
        write_to_session_res.setStdout("I am the output");
        when(mMockDevice.executeShellV2Command(
                        String.format(
                                "pm install-write -S %d %s %s %s",
                                mFakeApex.length(),
                                "1",
                                mInstallApexModuleTargetPreparer.parsePackageName(mFakeApex),
                                MODULE_PUSH_REMOTE_PATH + mFakeApex.getName())))
                .thenReturn(write_to_session_res);
        try {
            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            assertTrue(
                    expected.getMessage()
                            .equals(
                             String.format("Failed to write %s to session 1. Error: %s, Stdout: %s",
                                            mFakeApex.getName(), write_to_session_res.getStderr(),
                                            write_to_session_res.getStdout())));
        }
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(2);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(2)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
    }

    @Test
    public void testSetupAndTearDown_AddChildSessionToParentSessionFail() throws Exception {
        mockCleanInstalledApexPackages();
        Set<String> installableModules = setupInstallableModulesSingleApexSingleApk();

        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        for (File f : Arrays.asList(mFakeApk, mFakeApex)) {
            when(mMockDevice.pushFile(f, MODULE_PUSH_REMOTE_PATH + f.getName()))
                    .thenReturn(Boolean.TRUE);
        }
        CommandResult parent_session_creation_res = new CommandResult();
        parent_session_creation_res.setStatus(CommandStatus.SUCCESS);
        parent_session_creation_res.setStdout("123");
        CommandResult child_session_creation_res = new CommandResult();
        child_session_creation_res.setStatus(CommandStatus.SUCCESS);
        child_session_creation_res.setStdout("1");
        when(mMockDevice.executeShellV2Command(PARENT_SESSION_CREATION_CMD))
          .thenReturn(parent_session_creation_res);;
        when(mMockDevice.executeShellV2Command(CHILD_SESSION_CREATION_CMD_APEX))
          .thenReturn(child_session_creation_res);
        when(mMockDevice.executeShellV2Command(CHILD_SESSION_CREATION_CMD_APK))
          .thenReturn(child_session_creation_res);
        CommandResult write_to_session_res = new CommandResult();
        write_to_session_res.setStatus(CommandStatus.SUCCESS);
        CommandResult add_to_session_res = new CommandResult();
        add_to_session_res.setStatus(CommandStatus.FAILED);
        add_to_session_res.setStderr("I am an error!");
        add_to_session_res.setStdout("I am the output");
        for (File f : Arrays.asList(mFakeApex, mFakeApk)) {
            when(mMockDevice.executeShellV2Command(
                            String.format(
                                    "pm install-write -S %d %s %s %s",
                                    f.length(),
                                    "1",
                                    mInstallApexModuleTargetPreparer.parsePackageName(f),
                                    MODULE_PUSH_REMOTE_PATH + f.getName())))
                    .thenReturn(write_to_session_res);
            when(mMockDevice.executeShellV2Command(
                    String.format(
                            "pm install-add-session " + parent_session_creation_res.getStdout()
                      + " 1"))).thenReturn(add_to_session_res);
        }
        try {
            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            assertTrue(
                    expected.getMessage()
                            .equals(
                              String.format(
                       "Failed to add child session 1 to parent session 123. Error: %s, Stdout: %s",
                        add_to_session_res.getStderr(), add_to_session_res.getStdout())));
        }
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(2);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(2)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
    }

    @Test
    public void testSetupAndTearDown_CommitParentSessionFail() throws Exception {
        mockCleanInstalledApexPackages();
        Set<String> installableModules = setupInstallableModulesSingleApexSingleApk();

        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        for (File f : Arrays.asList(mFakeApk, mFakeApex)) {
            when(mMockDevice.pushFile(f, MODULE_PUSH_REMOTE_PATH + f.getName()))
                    .thenReturn(Boolean.TRUE);
        }
        CommandResult parent_session_creation_res = new CommandResult();
        parent_session_creation_res.setStatus(CommandStatus.SUCCESS);
        parent_session_creation_res.setStdout("123");
        CommandResult child_session_creation_res = new CommandResult();
        child_session_creation_res.setStatus(CommandStatus.SUCCESS);
        child_session_creation_res.setStdout("1");
        when(mMockDevice.executeShellV2Command(PARENT_SESSION_CREATION_CMD))
          .thenReturn(parent_session_creation_res);;
        when(mMockDevice.executeShellV2Command(CHILD_SESSION_CREATION_CMD_APEX))
          .thenReturn(child_session_creation_res);
        when(mMockDevice.executeShellV2Command(CHILD_SESSION_CREATION_CMD_APK))
          .thenReturn(child_session_creation_res);
        CommandResult cmd_res = new CommandResult();
        cmd_res.setStatus(CommandStatus.SUCCESS);
        for (File f : Arrays.asList(mFakeApex, mFakeApk)) {
            when(mMockDevice.executeShellV2Command(
                            String.format(
                                    "pm install-write -S %d %s %s %s",
                                    f.length(),
                                    "1",
                                    mInstallApexModuleTargetPreparer.parsePackageName(f),
                                    MODULE_PUSH_REMOTE_PATH + f.getName())))
                    .thenReturn(cmd_res);
            when(mMockDevice.executeShellV2Command(
                    String.format(
                            "pm install-add-session "
                      + parent_session_creation_res.getStdout() + " 1"))).thenReturn(cmd_res);
        }
        CommandResult commit_session_res = new CommandResult();
        commit_session_res.setStatus(CommandStatus.FAILED);
        commit_session_res.setStderr("I am an error!");
        commit_session_res.setStdout("I am the output");
        when(mMockDevice.executeShellV2Command("pm install-commit "
                         + parent_session_creation_res.getStdout())).thenReturn(commit_session_res);
        try {
            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            fail("Should have thrown a TargetSetupError.");
        } catch (TargetSetupError expected) {
            assertTrue(
                    expected.getMessage()
                            .contains(
                              String.format("Failed to commit 123 on %s. Error: %s, Output: %s",
                                mMockDevice.getSerialNumber(), commit_session_res.getStderr(),
                                                    commit_session_res.getStdout())));
        }
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(2);
        verify(mMockDevice, times(2)).reboot();
        verify(mMockDevice, times(2)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
    }

    @Test(expected = RuntimeException.class)
    public void testSetupAndTearDown_MultiInstallRollbackFail() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);
        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex, mFakeApk));
        setActivatedApex();
        when(mMockDevice.executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME))
                .thenReturn("No available rollback");
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        installableModules.add(APK_PACKAGE_NAME);

        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(1);
        verifySuccessfulInstallMultiPackages();
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
    }

    @Test
    public void testInstallUsingBundletool() throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APEX_APKS_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APK__APKS_NAME);
        mFakeApexApks = File.createTempFile("fakeApex", ".apks");
        mFakeApkApks = File.createTempFile("fakeApk", ".apks");

        File fakeSplitApexApks = File.createTempFile("ApexSplits", "");
        fakeSplitApexApks.delete();
        fakeSplitApexApks.mkdir();
        File splitApex = File.createTempFile("fakeSplitApex", ".apex", fakeSplitApexApks);

        File fakeSplitApkApks = File.createTempFile("ApkSplits", "");
        fakeSplitApkApks.delete();
        fakeSplitApkApks.mkdir();
        File splitApk1 = File.createTempFile("fakeSplitApk1", ".apk", fakeSplitApkApks);
        mBundletoolJar = File.createTempFile("bundletool", ".jar");
        File splitApk2 = File.createTempFile("fakeSplitApk2", ".apk", fakeSplitApkApks);
        try {
            mockCleanInstalledApexPackages();
            when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                    .thenReturn("serial.json");

            assertTrue(fakeSplitApexApks != null);
            assertTrue(fakeSplitApkApks != null);
            assertTrue(mFakeApexApks != null);
            assertTrue(mFakeApkApks != null);
            assertEquals(1, fakeSplitApexApks.listFiles().length);
            assertEquals(2, fakeSplitApkApks.listFiles().length);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    Mockito.eq(mFakeApexApks),
                    anyString(),
                    Mockito.any(ITestDevice.class),
                    Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApexApks);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    Mockito.eq(mFakeApkApks),
                    anyString(),
                    Mockito.any(ITestDevice.class),
                    Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApkApks);

            List<String> trainInstallCmd = new ArrayList<>();
            trainInstallCmd.add("install-multi-package");
            trainInstallCmd.add("--enable-rollback");
            trainInstallCmd.add(splitApex.getAbsolutePath());
            String cmd = "";
            for (File f : fakeSplitApkApks.listFiles()) {
                if (!cmd.isEmpty()) {
                    cmd += ":" + f.getParentFile().getAbsolutePath() + "/" + f.getName();
                } else {
                    cmd += f.getParentFile().getAbsolutePath() + "/" + f.getName();
                }
            }
            trainInstallCmd.add(cmd);
            when(mMockDevice.executeAdbCommand(trainInstallCmd.toArray(new String[0])))
                    .thenReturn("Success");

            Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
            activatedApex.add(
                    new ApexInfo(
                            SPLIT_APEX_PACKAGE_NAME,
                            1,
                            "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex"));
            when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
            when(mMockDevice.executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME))
                    .thenReturn("Success");

            Set<String> installableModules = new HashSet<>();
            installableModules.add(APEX_PACKAGE_NAME);
            installableModules.add(SPLIT_APK_PACKAGE_NAME);
            when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
            verifyCleanInstalledApexPackages(2);
            Mockito.verify(mMockBundletoolUtil, times(1))
                    .generateDeviceSpecFile(Mockito.any(ITestDevice.class));
            // Extract splits 1 time to get the package name for the module, and again during
            // installation.
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApexApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApkApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            verify(mMockDevice, times(3)).reboot();
            verify(mMockDevice, times(1)).executeAdbCommand(trainInstallCmd.toArray(new String[0]));
            verify(mMockDevice, times(1))
                    .executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME);
            verify(mMockDevice, times(3)).getActiveApexes();
            verify(mMockDevice, times(1)).waitForDeviceAvailable();
        } finally {
            FileUtil.deleteFile(mFakeApexApks);
            FileUtil.deleteFile(mFakeApkApks);
            FileUtil.recursiveDelete(fakeSplitApexApks);
            FileUtil.deleteFile(fakeSplitApexApks);
            FileUtil.recursiveDelete(fakeSplitApkApks);
            FileUtil.deleteFile(fakeSplitApkApks);
            FileUtil.deleteFile(mBundletoolJar);
        }
    }

    @Test
    public void testInstallUsingBundletool_AbsolutePath() throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APEX_APKS_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APK__APKS_NAME);
        mFakeApexApks = File.createTempFile("fakeApex", ".apks");
        mFakeApkApks = File.createTempFile("fakeApk", ".apks");

        File fakeSplitApexApks = File.createTempFile("ApexSplits", "");
        fakeSplitApexApks.delete();
        fakeSplitApexApks.mkdir();
        File splitApex = File.createTempFile("fakeSplitApex", ".apex", fakeSplitApexApks);

        File fakeSplitApkApks = File.createTempFile("ApkSplits", "");
        fakeSplitApkApks.delete();
        fakeSplitApkApks.mkdir();
        File splitApk1 = File.createTempFile("fakeSplitApk1", ".apk", fakeSplitApkApks);
        mBundletoolJar = File.createTempFile("/fake/absolute/path/bundletool", ".jar");
        File splitApk2 = File.createTempFile("fakeSplitApk2", ".apk", fakeSplitApkApks);
        try {
            mockCleanInstalledApexPackages();
            when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                    .thenReturn("serial.json");

            assertTrue(fakeSplitApexApks != null);
            assertTrue(fakeSplitApkApks != null);
            assertTrue(mFakeApexApks != null);
            assertTrue(mFakeApkApks != null);
            assertEquals(1, fakeSplitApexApks.listFiles().length);
            assertEquals(2, fakeSplitApkApks.listFiles().length);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    Mockito.eq(mFakeApexApks),
                    anyString(),
                    Mockito.any(ITestDevice.class),
                    Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApexApks);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    Mockito.eq(mFakeApkApks),
                    anyString(),
                    Mockito.any(ITestDevice.class),
                    Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApkApks);

            List<String> trainInstallCmd = new ArrayList<>();
            trainInstallCmd.add("install-multi-package");
            trainInstallCmd.add("--enable-rollback");
            trainInstallCmd.add(splitApex.getAbsolutePath());
            String cmd = "";
            for (File f : fakeSplitApkApks.listFiles()) {
                if (!cmd.isEmpty()) {
                    cmd += ":" + f.getParentFile().getAbsolutePath() + "/" + f.getName();
                } else {
                    cmd += f.getParentFile().getAbsolutePath() + "/" + f.getName();
                }
            }
            trainInstallCmd.add(cmd);
            when(mMockDevice.executeAdbCommand(trainInstallCmd.toArray(new String[0])))
                    .thenReturn("Success");

            Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
            activatedApex.add(
                    new ApexInfo(
                            SPLIT_APEX_PACKAGE_NAME,
                            1,
                            "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex"));
            when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
            when(mMockDevice.executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME))
                    .thenReturn("Success");

            Set<String> installableModules = new HashSet<>();
            installableModules.add(APEX_PACKAGE_NAME);
            installableModules.add(SPLIT_APK_PACKAGE_NAME);
            when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
            verifyCleanInstalledApexPackages(2);
            Mockito.verify(mMockBundletoolUtil, times(1))
                    .generateDeviceSpecFile(Mockito.any(ITestDevice.class));
            // Extract splits 1 time to get the package name for the module, and again during
            // installation.
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApexApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApkApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            verify(mMockDevice, times(3)).reboot();
            verify(mMockDevice, times(1)).executeAdbCommand(trainInstallCmd.toArray(new String[0]));
            verify(mMockDevice, times(3)).getActiveApexes();
            verify(mMockDevice, times(1))
                    .executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME);
            verify(mMockDevice, times(1)).waitForDeviceAvailable();
        } finally {
            FileUtil.deleteFile(mFakeApexApks);
            FileUtil.deleteFile(mFakeApkApks);
            FileUtil.recursiveDelete(fakeSplitApexApks);
            FileUtil.deleteFile(fakeSplitApexApks);
            FileUtil.recursiveDelete(fakeSplitApkApks);
            FileUtil.deleteFile(fakeSplitApkApks);
            FileUtil.deleteFile(mBundletoolJar);
        }
    }

    @Test
    public void testInstallUsingBundletool_setStagedReadyTimeout() throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);
        mSetter.setOptionValue("staged-ready-timeout-ms", "120000");
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APEX_APKS_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APK__APKS_NAME);
        mFakeApexApks = File.createTempFile("fakeApex", ".apks");
        mFakeApkApks = File.createTempFile("fakeApk", ".apks");

        File fakeSplitApexApks = File.createTempFile("ApexSplits", "");
        fakeSplitApexApks.delete();
        fakeSplitApexApks.mkdir();
        File splitApex = File.createTempFile("fakeSplitApex", ".apex", fakeSplitApexApks);

        File fakeSplitApkApks = File.createTempFile("ApkSplits", "");
        fakeSplitApkApks.delete();
        fakeSplitApkApks.mkdir();
        File splitApk1 = File.createTempFile("fakeSplitApk1", ".apk", fakeSplitApkApks);
        mBundletoolJar = File.createTempFile("bundletool", ".jar");
        File splitApk2 = File.createTempFile("fakeSplitApk2", ".apk", fakeSplitApkApks);
        try {
            mockCleanInstalledApexPackages();
            when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                    .thenReturn("serial.json");

            assertTrue(fakeSplitApexApks != null);
            assertTrue(fakeSplitApkApks != null);
            assertTrue(mFakeApexApks != null);
            assertTrue(mFakeApkApks != null);
            assertEquals(1, fakeSplitApexApks.listFiles().length);
            assertEquals(2, fakeSplitApkApks.listFiles().length);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                            Mockito.eq(mFakeApexApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApexApks);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                            Mockito.eq(mFakeApkApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApkApks);

            List<String> trainInstallCmd = new ArrayList<>();
            trainInstallCmd.add("install-multi-package");
            trainInstallCmd.add("--enable-rollback");
            trainInstallCmd.add("--staged-ready-timeout");
            trainInstallCmd.add("120000");
            trainInstallCmd.add(splitApex.getAbsolutePath());
            String cmd = "";
            for (File f : fakeSplitApkApks.listFiles()) {
                if (!cmd.isEmpty()) {
                    cmd += ":" + f.getParentFile().getAbsolutePath() + "/" + f.getName();
                } else {
                    cmd += f.getParentFile().getAbsolutePath() + "/" + f.getName();
                }
            }
            trainInstallCmd.add(cmd);
            when(mMockDevice.executeAdbCommand(trainInstallCmd.toArray(new String[0])))
                    .thenReturn("Success");

            Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
            activatedApex.add(
                    new ApexInfo(
                            SPLIT_APEX_PACKAGE_NAME,
                            1,
                            "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex"));
            when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
            when(mMockDevice.executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME))
                    .thenReturn("Success");

            Set<String> installableModules = new HashSet<>();
            installableModules.add(APEX_PACKAGE_NAME);
            installableModules.add(SPLIT_APK_PACKAGE_NAME);
            when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
            verifyCleanInstalledApexPackages(2);
            Mockito.verify(mMockBundletoolUtil, times(1))
                    .generateDeviceSpecFile(Mockito.any(ITestDevice.class));
            // Extract splits 1 time to get the package name for the module, and again during
            // installation.
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApexApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApkApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            verify(mMockDevice, times(3)).reboot();
            verify(mMockDevice, times(1)).executeAdbCommand(trainInstallCmd.toArray(new String[0]));
            verify(mMockDevice, times(1))
                    .executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME);
            verify(mMockDevice, times(3)).getActiveApexes();
            verify(mMockDevice, times(1)).waitForDeviceAvailable();
        } finally {
            FileUtil.deleteFile(mFakeApexApks);
            FileUtil.deleteFile(mFakeApkApks);
            FileUtil.recursiveDelete(fakeSplitApexApks);
            FileUtil.deleteFile(fakeSplitApexApks);
            FileUtil.recursiveDelete(fakeSplitApkApks);
            FileUtil.deleteFile(fakeSplitApkApks);
            FileUtil.deleteFile(mBundletoolJar);
        }
    }

    @Test
    public void testInstallUsingBundletool_TrainFolder() throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);
        File trainFolder = File.createTempFile("tmpTrain", "");
        trainFolder.delete();
        trainFolder.mkdir();
        mSetter.setOptionValue("train-path", trainFolder.getAbsolutePath());
        mFakeApexApks = File.createTempFile("fakeApex", ".apks", trainFolder);
        mFakeApkApks = File.createTempFile("fakeApk", ".apks", trainFolder);

        File fakeSplitApexApks = File.createTempFile("ApexSplits", "");
        fakeSplitApexApks.delete();
        fakeSplitApexApks.mkdir();
        File splitApex = File.createTempFile("fakeSplitApex", ".apex", fakeSplitApexApks);

        File fakeSplitApkApks = File.createTempFile("ApkSplits", "");
        fakeSplitApkApks.delete();
        fakeSplitApkApks.mkdir();
        File splitApk1 = File.createTempFile("fakeSplitApk1", ".apk", fakeSplitApkApks);
        mBundletoolJar = File.createTempFile("bundletool", ".jar");
        File splitApk2 = File.createTempFile("fakeSplitApk2", ".apk", fakeSplitApkApks);
        try {
            mockCleanInstalledApexPackages();
            when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                    .thenReturn("serial.json");

            assertTrue(fakeSplitApexApks != null);
            assertTrue(fakeSplitApkApks != null);
            assertTrue(mFakeApexApks != null);
            assertTrue(mFakeApkApks != null);
            assertEquals(1, fakeSplitApexApks.listFiles().length);
            assertEquals(2, fakeSplitApkApks.listFiles().length);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    Mockito.eq(mFakeApexApks),
                    anyString(),
                    Mockito.any(ITestDevice.class),
                    Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApexApks);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    Mockito.eq(mFakeApkApks),
                    anyString(),
                    Mockito.any(ITestDevice.class),
                    Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApkApks);

            List<String> trainInstallCmd = new ArrayList<>();
            trainInstallCmd.add("install-multi-package");
            trainInstallCmd.add("--enable-rollback");
            trainInstallCmd.add(splitApex.getAbsolutePath());
            String cmd = "";
            for (File f : fakeSplitApkApks.listFiles()) {
                if (!cmd.isEmpty()) {
                    cmd += ":" + f.getParentFile().getAbsolutePath() + "/" + f.getName();
                } else {
                    cmd += f.getParentFile().getAbsolutePath() + "/" + f.getName();
                }
            }
            trainInstallCmd.add(cmd);
            when(mMockDevice.executeAdbCommand(trainInstallCmd.toArray(new String[0])))
                    .thenReturn("Success");

            Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
            activatedApex.add(
                    new ApexInfo(
                            SPLIT_APEX_PACKAGE_NAME,
                            1,
                            "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex"));
            when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
            when(mMockDevice.executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME))
                    .thenReturn("Success");

            Set<String> installableModules = new HashSet<>();
            installableModules.add(APEX_PACKAGE_NAME);
            installableModules.add(SPLIT_APK_PACKAGE_NAME);
            when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
            verifyCleanInstalledApexPackages(2);
            Mockito.verify(mMockBundletoolUtil, times(1))
                    .generateDeviceSpecFile(Mockito.any(ITestDevice.class));
            // Extract splits 1 time to get the package name for the module, and again during
            // installation.
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApexApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApkApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            verify(mMockDevice, times(3)).reboot();
            verify(mMockDevice, times(1)).executeAdbCommand(trainInstallCmd.toArray(new String[0]));
            verify(mMockDevice, times(3)).getActiveApexes();
            verify(mMockDevice, times(1))
                    .executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME);
            verify(mMockDevice, times(1)).waitForDeviceAvailable();
        } finally {
            FileUtil.recursiveDelete(trainFolder);
            FileUtil.deleteFile(trainFolder);
            FileUtil.deleteFile(mFakeApexApks);
            FileUtil.deleteFile(mFakeApkApks);
            FileUtil.recursiveDelete(fakeSplitApexApks);
            FileUtil.deleteFile(fakeSplitApexApks);
            FileUtil.recursiveDelete(fakeSplitApkApks);
            FileUtil.deleteFile(fakeSplitApkApks);
            FileUtil.deleteFile(mBundletoolJar);
        }
    }

    @Test
    public void testInstallUsingBundletool_AllFilesHaveAbsolutePath() throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);
        mFakeApexApks = File.createTempFile("fakeApex", ".apks");
        mFakeApkApks = File.createTempFile("fakeApk", ".apks");
        mInstallApexModuleTargetPreparer.addTestFile(mFakeApexApks);
        mInstallApexModuleTargetPreparer.addTestFile(mFakeApkApks);

        File fakeSplitApexApks = File.createTempFile("ApexSplits", "");
        fakeSplitApexApks.delete();
        fakeSplitApexApks.mkdir();
        File splitApex = File.createTempFile("fakeSplitApex", ".apex", fakeSplitApexApks);

        File fakeSplitApkApks = File.createTempFile("ApkSplits", "");
        fakeSplitApkApks.delete();
        fakeSplitApkApks.mkdir();
        File splitApk1 = File.createTempFile("fakeSplitApk1", ".apk", fakeSplitApkApks);
        mBundletoolJar = File.createTempFile("/fake/absolute/path/bundletool", ".jar");
        File splitApk2 = File.createTempFile("fakeSplitApk2", ".apk", fakeSplitApkApks);
        try {
            mockCleanInstalledApexPackages();
            when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                    .thenReturn("serial.json");

            assertTrue(fakeSplitApexApks != null);
            assertTrue(fakeSplitApkApks != null);
            assertTrue(mFakeApexApks != null);
            assertTrue(mFakeApkApks != null);
            assertEquals(1, fakeSplitApexApks.listFiles().length);
            assertEquals(2, fakeSplitApkApks.listFiles().length);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    Mockito.eq(mFakeApexApks),
                    anyString(),
                    Mockito.any(ITestDevice.class),
                    Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApexApks);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    Mockito.eq(mFakeApkApks),
                    anyString(),
                    Mockito.any(ITestDevice.class),
                    Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApkApks);

            List<String> trainInstallCmd = new ArrayList<>();
            trainInstallCmd.add("install-multi-package");
            trainInstallCmd.add("--enable-rollback");
            trainInstallCmd.add(splitApex.getAbsolutePath());
            String cmd = "";
            for (File f : fakeSplitApkApks.listFiles()) {
                if (!cmd.isEmpty()) {
                    cmd += ":" + f.getParentFile().getAbsolutePath() + "/" + f.getName();
                } else {
                    cmd += f.getParentFile().getAbsolutePath() + "/" + f.getName();
                }
            }
            trainInstallCmd.add(cmd);
            when(mMockDevice.executeAdbCommand(trainInstallCmd.toArray(new String[0])))
                    .thenReturn("Success");

            Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
            activatedApex.add(
                    new ApexInfo(
                            SPLIT_APEX_PACKAGE_NAME,
                            1,
                            "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex"));
            when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
            when(mMockDevice.executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME))
                    .thenReturn("Success");

            Set<String> installableModules = new HashSet<>();
            installableModules.add(APEX_PACKAGE_NAME);
            installableModules.add(SPLIT_APK_PACKAGE_NAME);
            when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
            verifyCleanInstalledApexPackages(2);
            Mockito.verify(mMockBundletoolUtil, times(1))
                    .generateDeviceSpecFile(Mockito.any(ITestDevice.class));
            // Extract splits 1 time to get the package name for the module, and again during
            // installation.
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApexApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            Mockito.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApkApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            verify(mMockDevice, times(3)).reboot();
            verify(mMockDevice, times(1)).executeAdbCommand(trainInstallCmd.toArray(new String[0]));
            verify(mMockDevice, times(3)).getActiveApexes();
            verify(mMockDevice, times(1))
                    .executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME);
            verify(mMockDevice, times(1)).waitForDeviceAvailable();
        } finally {
            FileUtil.deleteFile(mFakeApexApks);
            FileUtil.deleteFile(mFakeApkApks);
            FileUtil.recursiveDelete(fakeSplitApexApks);
            FileUtil.deleteFile(fakeSplitApexApks);
            FileUtil.recursiveDelete(fakeSplitApkApks);
            FileUtil.deleteFile(fakeSplitApkApks);
            FileUtil.deleteFile(mBundletoolJar);
        }
    }

    @Test
    public void testInstallUsingBundletool_skipModuleNotPreloaded() throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);
        mSetter.setOptionValue("ignore-if-module-not-preloaded", "true");
        // Will skip this apex module
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APEX_APKS_NAME);
        // Will install this apk module
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APK__APKS_NAME);
        mFakeApexApks = File.createTempFile("fakeApex", ".apks");
        mFakeApkApks = File.createTempFile("fakeApk", ".apks");
        InOrder order = inOrder(mMockDevice, mMockBundletoolUtil);

        File fakeSplitApexApks = File.createTempFile("ApexSplits", "");
        fakeSplitApexApks.delete();
        fakeSplitApexApks.mkdir();
        File splitApex = File.createTempFile("fakeSplitApex", ".apex", fakeSplitApexApks);

        File fakeSplitApkApks = File.createTempFile("ApkSplits", "");
        fakeSplitApkApks.delete();
        fakeSplitApkApks.mkdir();
        File splitApk1 = File.createTempFile("fakeSplitApk1", ".apk", fakeSplitApkApks);
        mBundletoolJar = File.createTempFile("bundletool", ".jar");
        File splitApk2 = File.createTempFile("fakeSplitApk2", ".apk", fakeSplitApkApks);
        try {
            mockCleanInstalledApexPackages();
            when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                    .thenReturn("serial.json");

            assertNotNull(fakeSplitApexApks);
            assertNotNull(fakeSplitApkApks);
            assertNotNull(mFakeApexApks);
            assertNotNull(mFakeApkApks);
            assertEquals(1, fakeSplitApexApks.listFiles().length);
            assertEquals(2, fakeSplitApkApks.listFiles().length);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    eq(mFakeApexApks),
                    anyString(),
                    any(ITestDevice.class),
                    any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApexApks);

            when(mMockBundletoolUtil.extractSplitsFromApks(
                    eq(mFakeApkApks),
                    anyString(),
                    any(ITestDevice.class),
                    any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApkApks);
            // Split apex package is not preloaded in the device so should not be activated.
            when(mMockDevice.getActiveApexes()).thenReturn(new HashSet<>());
            when(mMockDevice.uninstallPackage(SPLIT_APK_PACKAGE_NAME)).thenReturn(null);
            Set<String> installableModules = new HashSet<>();
            installableModules.add(SPLIT_APK_PACKAGE_NAME);

            when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);

            order.verify(mMockDevice, times(1)).deleteFile(APEX_DATA_DIR + "*");
            order.verify(mMockDevice, times(1)).deleteFile(STAGING_DATA_DIR + "*");
            order.verify(mMockDevice, times(1)).deleteFile(SESSION_DATA_DIR + "*");
            order.verify(mMockDevice, times(1)).reboot();
            order.verify(mMockDevice, times(2)).getActiveApexes();
            order.verify(mMockBundletoolUtil, times(1))
                    .generateDeviceSpecFile(Mockito.any(ITestDevice.class));
            // Extract splits 1 time to get the package name for the module, does not attempt to
            // install.
            order.verify(mMockBundletoolUtil, times(1))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApexApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            // The 1st time to exact the package name, the 2nd time to check if it is apex before
            // installing.
            order.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApkApks),
                            anyString(),
                            Mockito.any(ITestDevice.class),
                            Mockito.any(IBuildInfo.class));
            order.verify(mMockBundletoolUtil, times(1))
                    .installApks(eq(mFakeApkApks), eq(mMockDevice), eq(new ArrayList<String>()));
            order.verify(mMockDevice, times(1)).uninstallPackage(SPLIT_APK_PACKAGE_NAME);
        } finally {
            FileUtil.deleteFile(mFakeApexApks);
            FileUtil.deleteFile(mFakeApkApks);
            FileUtil.recursiveDelete(fakeSplitApexApks);
            FileUtil.deleteFile(fakeSplitApexApks);
            FileUtil.recursiveDelete(fakeSplitApkApks);
            FileUtil.deleteFile(fakeSplitApkApks);
            FileUtil.deleteFile(mBundletoolJar);
        }
    }

    @Test
    public void testInstallUsingBundletool_rebootAfterInstallSingleSplitApexApks()
            throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);
        InOrder order = inOrder(mMockDevice, mMockBundletoolUtil);
        mSetter.setOptionValue("ignore-if-module-not-preloaded", "true");
        mBundletoolJar = File.createTempFile("bundletool", ".jar");
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APEX_APKS_NAME);
        mFakeApexApks = File.createTempFile("fakeApex", ".apks");
        File fakeSplitApexApks = File.createTempFile("ApexSplits", "");
        fakeSplitApexApks.delete();
        fakeSplitApexApks.mkdir();
        File splitApex = File.createTempFile("fakeSplitApex", ".apex", fakeSplitApexApks);

        try {
            mockCleanInstalledApexPackages();
            when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                    .thenReturn("serial.json");
            assertNotNull(fakeSplitApexApks);
            assertNotNull(mFakeApexApks);
            assertEquals(1, fakeSplitApexApks.listFiles().length);
            when(mMockBundletoolUtil.extractSplitsFromApks(
                    Mockito.eq(mFakeApexApks),
                    anyString(),
                    Mockito.any(ITestDevice.class),
                    Mockito.any(IBuildInfo.class)))
                    .thenReturn(fakeSplitApexApks);
            Set<ApexInfo> activatedApex = new HashSet<>();
            activatedApex.add(
                    new ApexInfo(
                            SPLIT_APEX_PACKAGE_NAME,
                            1,
                            "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex"));
            when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);

            mInstallApexModuleTargetPreparer.setUp(mTestInfo);

            order.verify(mMockDevice, times(1)).deleteFile(APEX_DATA_DIR + "*");
            order.verify(mMockDevice, times(1)).deleteFile(STAGING_DATA_DIR + "*");
            order.verify(mMockDevice, times(1)).deleteFile(SESSION_DATA_DIR + "*");
            order.verify(mMockDevice, times(1)).reboot();
            order.verify(mMockDevice, times(1)).getActiveApexes();
            order.verify(mMockDevice, times(1)).getActiveApexes();
            order.verify(mMockBundletoolUtil, times(1))
                    .generateDeviceSpecFile(any(ITestDevice.class));
            // The 1st time to exact the package name, the 2nd time to extract apex for
            // installation.
            order.verify(mMockBundletoolUtil, times(2))
                    .extractSplitsFromApks(
                            Mockito.eq(mFakeApexApks),
                            anyString(),
                            any(ITestDevice.class),
                            any(IBuildInfo.class));
            order.verify(mMockDevice, times(1)).installPackage(eq(splitApex), anyBoolean(), any());
            order.verify(mMockDevice, times(1)).reboot();
            order.verify(mMockDevice, times(1)).getActiveApexes();
        } finally {
            FileUtil.deleteFile(mFakeApexApks);
            FileUtil.deleteFile(splitApex);
            FileUtil.recursiveDelete(fakeSplitApexApks);
            FileUtil.deleteFile(fakeSplitApexApks);
            FileUtil.deleteFile(mBundletoolJar);
        }
    }

    @Test
    public void testSetupAndTearDown_SingleInstall_NoEnableRollback() throws Exception {
        mSetter.setOptionValue("enable-rollback", "false");
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");
        doReturn(new HashSet<>())
                .doReturn(new HashSet<>())
                .doReturn(new HashSet<>(Arrays.asList(fakeApexData)))
                .when(mMockDevice)
                .getActiveApexes();
        when(mMockDevice.getMainlineModuleInfo()).thenReturn(new HashSet<>());
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex));

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, times(1)).getMainlineModuleInfo();
        verifySuccessfulInstallPackageNoEnableRollback();
    }

    @Test
    public void testSetupAndTearDown_MultiInstall_NoEnableRollback() throws Exception {
        mSetter.setOptionValue("enable-rollback", "false");
        mSetter.setOptionValue("skip-apex-teardown", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APEX2_NAME);

        ApexInfo fakeApexData =
                new ApexInfo(
                        APEX_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex");
        ApexInfo fakeApex2Data =
                new ApexInfo(
                        APEX2_PACKAGE_NAME,
                        1,
                        "/data/apex/active/com.android.FAKE_APEX2_PACKAGE_NAME@1.apex");
        doReturn(new HashSet<>())
                .doReturn(new HashSet<>())
                .doReturn(new HashSet<>(Arrays.asList(fakeApexData, fakeApex2Data)))
                .when(mMockDevice)
                .getActiveApexes();
        when(mMockDevice.getMainlineModuleInfo()).thenReturn(new HashSet<>());

        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        installableModules.add(APEX2_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        when(mMockDevice.executeAdbCommand((String[]) Mockito.any())).thenReturn("Success");
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApex, mFakeApex2));

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, atLeastOnce()).getActiveApexes();
        verify(mMockDevice, times(1)).getMainlineModuleInfo();
        verifySuccessfulInstallMultiPackagesNoEnableRollback();
    }

    /**
     * Test that teardown without setup does not cause a NPE.
     */
    @Test
    public void testTearDown() throws Exception {

        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
    }

    @Test
    public void testSetupAndTearDown_noModulesPreloaded() throws Exception {
        mSetter.setOptionValue("ignore-if-module-not-preloaded", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);

        when(mMockDevice.getInstalledPackageNames()).thenReturn(new HashSet<String>());
        mockCleanInstalledApexPackages();
        doReturn(ImmutableSet.of())
                .doReturn(new HashSet<ApexInfo>())
                .when(mMockDevice)
                .getActiveApexes();

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(1);
        verify(mMockDevice, times(1)).reboot();
        verify(mMockDevice, times(2)).getActiveApexes();
    }

    @Test
    public void testSetupAndTearDown_skipModulesNotPreloaded() throws Exception {
        mSetter.setOptionValue("ignore-if-module-not-preloaded", "true");
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);
        // Module not preloaded.
        mInstallApexModuleTargetPreparer.addTestFileName(APK2_NAME);
        mockCleanInstalledApexPackages();
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApk, mFakeApex));
        setActivatedApex();
        when(mMockDevice.executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME))
                .thenReturn("Success");

        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        installableModules.add(APK_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifySuccessfulInstallMultiPackages();
        verify(mMockDevice, times(3)).reboot();
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, times(1)).executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
    }

    @Test(expected = TargetSetupError.class)
    public void testSetupAndTearDown_throwExceptionModulesNotPreloaded() throws Exception {
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APK2_NAME);
        mockCleanInstalledApexPackages();
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(
                new ApexInfo(
                        "com.android.FAKE_APEX_PACKAGE_NAME",
                        1,
                        "/system/apex/com.android.FAKE_APEX_PACKAGE_NAME.apex"));
        when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
        when(mMockDevice.uninstallPackage(APK_PACKAGE_NAME)).thenReturn(null);

        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        installableModules.add(APK_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(1)).reboot();
        verify(mMockDevice, times(3)).getActiveApexes();
        verify(mMockDevice, times(1)).uninstallPackage(APK_PACKAGE_NAME);
        verify(mMockDevice, times(1)).getInstalledPackageNames();
    }

    @Test
    public void testSetupAndTearDown_skipModulesThatFailToExtract() throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(SPLIT_APK__APKS_NAME);
        mFakeApkApks = File.createTempFile("fakeApk", ".apks");
        mBundletoolJar = File.createTempFile("bundletool", ".jar");

        mockCleanInstalledApexPackages();
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(
                new ApexInfo(
                        "com.android.FAKE_APEX_PACKAGE_NAME",
                        1,
                        "/system/apex/com.android.FAKE_APEX_PACKAGE_NAME.apex"));
        when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
        Set<String> installableModules = new HashSet<>();
        installableModules.add(SPLIT_APK_PACKAGE_NAME);
        installableModules.add(APK_PACKAGE_NAME);
        when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);
        when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                .thenReturn("serial.json");

        when(mMockBundletoolUtil.extractSplitsFromApks(
                Mockito.eq(mFakeApkApks),
                anyString(),
                Mockito.any(ITestDevice.class),
                Mockito.any(IBuildInfo.class)))
                .thenReturn(null);

        // Only install apk, throw no error for apks.
        mockSuccessfulInstallMultiPackages(Arrays.asList(mFakeApk));
        when(mMockDevice.uninstallPackage(APK_PACKAGE_NAME)).thenReturn(null);
        when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);

        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verify(mMockDevice, times(2)).getActiveApexes();
        verify(mMockDevice, times(1)).deleteFile(APEX_DATA_DIR + "*");
        verify(mMockDevice, times(1)).deleteFile(SESSION_DATA_DIR + "*");
        verify(mMockDevice, times(1)).deleteFile(STAGING_DATA_DIR + "*");
        verify(mMockDevice, times(1)).getInstalledPackageNames();
        verify(mMockDevice, times(1)).uninstallPackage(APK_PACKAGE_NAME);
        verify(mMockDevice, times(2)).reboot();

        FileUtil.deleteFile(mFakeApkApks);
        FileUtil.deleteFile(mBundletoolJar);
    }

    @Test
    public void testNoFilesToInstall() throws Exception {
        mockCleanInstalledApexPackages();
        mInstallApexModuleTargetPreparer.setUp(mTestInfo);
        mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
        verifyCleanInstalledApexPackages(0);
        verify(mMockDevice, times(0))
                .executeShellV2Command(String.format("pm install-add-session " + "123 1"));
        verify(mMockDevice, times(0)).executeShellV2Command(PARENT_SESSION_CREATION_CMD);
        verify(mMockDevice, times(0)).executeShellV2Command("pm install-commit " + "123");
    }

    @Test
    public void testInstallModulesFromZipUsingBundletool() throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);

        mBundletoolJar = File.createTempFile("/fake/absolute/path/bundletool", ".jar");

        mSetter.setOptionValue("apks-zip-file-name", "fakeApkZip.zip");

        try {
            mockCleanInstalledApexPackages();
            when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                    .thenReturn("serial.json");

            List<String> trainInstallCmd = new ArrayList<>();
            trainInstallCmd.add("install-multi-apks");
            trainInstallCmd.add("--enable-rollback");
            trainInstallCmd.add("--apks-zip=" + mFakeApkZip.getAbsolutePath());
            when(mMockDevice.executeAdbCommand(trainInstallCmd.toArray(new String[0])))
                    .thenReturn("Success");

            Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
            activatedApex.add(
                    new ApexInfo(
                            SPLIT_APEX_PACKAGE_NAME,
                            1,
                            "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex"));
            when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
            when(mMockDevice.executeShellCommand("pm rollback-app " + SPLIT_APEX_PACKAGE_NAME))
                    .thenReturn("Success");

            Set<String> installableModules = new HashSet<>();
            installableModules.add(APEX_PACKAGE_NAME);
            installableModules.add(SPLIT_APK_PACKAGE_NAME);
            when(mMockDevice.getInstalledPackageNames()).thenReturn(installableModules);

            mInstallApexModuleTargetPreparer.setUp(mTestInfo);
            mInstallApexModuleTargetPreparer.tearDown(mTestInfo, null);
            verifyCleanInstalledApexPackages(1);
            Mockito.verify(mMockBundletoolUtil, times(1))
                    .generateDeviceSpecFile(Mockito.any(ITestDevice.class));
            List<String> expectedArgs = new ArrayList<>();
            expectedArgs.add("--update-only");
            expectedArgs.add("--enable-rollback");
            Mockito.verify(mMockBundletoolUtil, times(1))
                    .installApksFromZip(mFakeApkZip, mMockDevice, expectedArgs);
            verify(mMockDevice, times(2)).reboot();
            verify(mMockDevice, times(1)).getActiveApexes();
            verify(mMockDevice, times(1)).waitForDeviceAvailable();
        } finally {
            FileUtil.deleteFile(mBundletoolJar);
            FileUtil.deleteFile(mFakeApkZip);
        }
    }

    @Test
    public void initDeviceSpecFilePath_whenGenerateDeviceSpecFileFailed_noExceptionWhenTwoFiles()
            throws Exception {
        mMockBundletoolUtil = mock(BundletoolUtil.class);
        when(mMockBundletoolUtil.generateDeviceSpecFile(Mockito.any(ITestDevice.class)))
                .thenReturn(null);
        try {
            mBundletoolJar = File.createTempFile("bundletool", ".jar");
            mFakeApexApks = File.createTempFile("fakeApex", ".apks");
            mFakeApkApks = File.createTempFile("fakeApk", ".apks");
            mInstallApexModuleTargetPreparer.addTestFile(mFakeApexApks);
            mInstallApexModuleTargetPreparer.addTestFile(mFakeApkApks);

            mInstallApexModuleTargetPreparer.getModulesToInstall(mTestInfo);
        } finally {
            FileUtil.deleteFile(mBundletoolJar);
            FileUtil.deleteFile(mFakeApexApks);
            FileUtil.deleteFile(mFakeApkApks);
        }
    }

    @Test
    public void addStagedReadyTimeoutForAdb_expected() throws Exception {
        List<String> cmd = new ArrayList<>();
        mInstallApexModuleTargetPreparer.addStagedReadyTimeoutForAdb(cmd);
        assertTrue(cmd.isEmpty());

        mSetter.setOptionValue("staged-ready-timeout-ms", "120000");
        mInstallApexModuleTargetPreparer.addStagedReadyTimeoutForAdb(cmd);
        assertEquals(cmd.toArray(new String[0]), new String[] {"--staged-ready-timeout", "120000"});
    }

    @Test
    public void addTimeoutMillisForBundletool_expected() throws Exception {
        List<String> cmd = new ArrayList<>();
        mInstallApexModuleTargetPreparer.addTimeoutMillisForBundletool(cmd);
        assertTrue(cmd.isEmpty());

        mSetter.setOptionValue("staged-ready-timeout-ms", "120000");
        mInstallApexModuleTargetPreparer.addTimeoutMillisForBundletool(cmd);
        assertEquals(cmd.toArray(new String[0]), new String[] {"--timeout-millis=120000"});
    }

    private void verifySuccessfulInstallPackages(List<File> files) throws Exception {
        int child_session_id = 1;
        for (File f : files) {
            verify(mMockDevice, times(1))
                    .executeShellV2Command(
                            String.format(
                                    "pm install-write -S %d %s %s %s",
                                    f.length(),
                                    String.valueOf(child_session_id),
                                    mInstallApexModuleTargetPreparer.parsePackageName(f),
                                    MODULE_PUSH_REMOTE_PATH + f.getName()));
        }
        verify(mMockDevice, times(files.size())).executeShellV2Command(String.format(
                "pm install-add-session " + "123" + " " + String.valueOf(child_session_id)));
        verify(mMockDevice, times(1)).executeShellV2Command(PARENT_SESSION_CREATION_CMD);
        verify(mMockDevice, times(1)).executeShellV2Command("pm install-commit " + "123");
    }

    private void mockSuccessfulInstallMultiPackages(List<File> files) throws Exception {
        for (File f : files) {
            when(mMockDevice.pushFile(f, MODULE_PUSH_REMOTE_PATH + f.getName()))
                    .thenReturn(Boolean.TRUE);
        }

        CommandResult parent_session_creation_res = new CommandResult();
        parent_session_creation_res.setStdout("123");
        parent_session_creation_res.setStatus(CommandStatus.SUCCESS);
        when(mMockDevice.executeShellV2Command(PARENT_SESSION_CREATION_CMD))
          .thenReturn(parent_session_creation_res);
        when(mMockDevice.executeShellV2Command(PARENT_SESSION_CREATION_ROLLBACK_NO_ENABLE_CMD))
          .thenReturn(parent_session_creation_res);

        CommandResult successful_shell_cmd_res = new CommandResult();
        successful_shell_cmd_res.setStatus(CommandStatus.SUCCESS);
        // Use same session id for child sessions in the test as the file iteration is
        // non-deterministic
        int child_session_id = 1;
        for (File f : files) {
            CommandResult child_session_creation_res = new CommandResult();
            child_session_creation_res.setStdout(String.valueOf(child_session_id));
            child_session_creation_res.setStatus(CommandStatus.SUCCESS);
            if (f.getName().endsWith("apex")) {
                when(mMockDevice.executeShellV2Command(CHILD_SESSION_CREATION_CMD_APEX))
                  .thenReturn(child_session_creation_res);
                when(mMockDevice.executeShellV2Command(
                  CHILD_SESSION_CREATION_ROLLBACK_NO_ENABLE_CMD_APEX))
                  .thenReturn(child_session_creation_res);
            } else {
                when(mMockDevice.executeShellV2Command(CHILD_SESSION_CREATION_CMD_APK))
                  .thenReturn(child_session_creation_res);
            }
            when(mMockDevice.executeShellV2Command(
                            String.format(
                                    "pm install-write -S %d %s %s %s",
                                    f.length(),
                                    String.valueOf(child_session_id),
                                    mInstallApexModuleTargetPreparer.parsePackageName(f),
                                    MODULE_PUSH_REMOTE_PATH + f.getName())))
                    .thenReturn(successful_shell_cmd_res);
            when(mMockDevice.executeShellV2Command(
                    String.format(
                            "pm install-add-session " + parent_session_creation_res.getStdout()
                   + " " + String.valueOf(child_session_id)))).thenReturn(successful_shell_cmd_res);
        }
        when(mMockDevice.executeShellV2Command("pm install-commit "
                    + parent_session_creation_res.getStdout())).thenReturn(successful_shell_cmd_res);
    }

    private void verifySuccessfulInstallMultiPackages() throws Exception {
        verify(mMockDevice, times(1)).executeShellV2Command("pm install-commit " + "123");
    }

    private void verifySuccessfulInstallPackageNoEnableRollback() throws Exception {
        verify(mMockDevice, times(1)).executeShellV2Command(
          CHILD_SESSION_CREATION_ROLLBACK_NO_ENABLE_CMD_APEX);
        verify(mMockDevice, times(1)).executeShellV2Command("pm install-commit " + "123");
    }

    private void verifySuccessfulInstallMultiPackagesNoEnableRollback() throws Exception {
        verify(mMockDevice, times(2)).executeShellV2Command(
          CHILD_SESSION_CREATION_ROLLBACK_NO_ENABLE_CMD_APEX);
        verify(mMockDevice, times(1)).executeShellV2Command("pm install-commit " + "123");
    }

    private void mockCleanInstalledApexPackages() throws DeviceNotAvailableException {
        CommandResult res = new CommandResult();
        res.setStdout("test.apex");
        when(mMockDevice.executeShellV2Command("ls " + APEX_DATA_DIR)).thenReturn(res);
        when(mMockDevice.executeShellV2Command("ls " + SESSION_DATA_DIR)).thenReturn(res);
        when(mMockDevice.executeShellV2Command("ls " + STAGING_DATA_DIR)).thenReturn(res);
    }

    private void verifyCleanInstalledApexPackages(int count) throws DeviceNotAvailableException {
        verify(mMockDevice, times(count)).deleteFile(APEX_DATA_DIR + "*");
        verify(mMockDevice, times(count)).deleteFile(SESSION_DATA_DIR + "*");
        verify(mMockDevice, times(count)).deleteFile(STAGING_DATA_DIR + "*");
    }

    private void setActivatedApex() throws DeviceNotAvailableException {
        Set<ApexInfo> activatedApex = new HashSet<ApexInfo>();
        activatedApex.add(
                new ApexInfo(
                        "com.android.FAKE_APEX_PACKAGE_NAME",
                        1,
                        "/data/apex/active/com.android.FAKE_APEX_PACKAGE_NAME@1.apex"));
        when(mMockDevice.getActiveApexes()).thenReturn(activatedApex);
    }

    private Set<String> setupInstallableModulesSingleApexSingleApk() throws
      DeviceNotAvailableException{
        mInstallApexModuleTargetPreparer.addTestFileName(APEX_NAME);
        mInstallApexModuleTargetPreparer.addTestFileName(APK_NAME);
        mockCleanInstalledApexPackages();
        when(mMockDevice.executeShellCommand("pm rollback-app " + APEX_PACKAGE_NAME))
                .thenReturn("Success");
        Set<String> installableModules = new HashSet<>();
        installableModules.add(APEX_PACKAGE_NAME);
        installableModules.add(APK_PACKAGE_NAME);
        return  installableModules;
    }
}
