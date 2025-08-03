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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.error.DeviceErrorIdentifier;

import com.google.common.collect.ImmutableMultimap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Unit test for {@link ModuleOemTargetPreparerTest} */
@RunWith(JUnit4.class)
public class ModuleOemTargetPreparerTest {
    private static final String APEX_PACKAGE_NAME = "com.android.FAKE_APEX_PACKAGE_NAME";
    private static final String APK_PACKAGE_NAME = "com.android.FAKE_APK_PACKAGE_NAME";
    private static final String APK_PACKAGE_NAME2 = "com.android.FAKE_APK_PACKAGE_NAME2";
    private static final String SPLIT_APK_PACKAGE_NAME = "com.android.SPLIT_FAKE_APK_PACKAGE_NAME";
    private static final String APEX_PRELOAD_NAME = APEX_PACKAGE_NAME + ".apex";
    private static final String APEX_PATH_ON_DEVICE = "/system/apex/" + APEX_PRELOAD_NAME;
    private static final String TEST_APEX_NAME = "fakeApex.apex";
    private static final String TEST_APK_NAME = "fakeApk.apk";
    private static final String TEST_SPLIT_APK_APKS_NAME = "fakeApk.apks";
    private static final String TEST_SPLIT_APK_NAME = "FakeSplit/base-master.apk";
    private static final String TEST_HDPI_APK_NAME = "FakeSplit/base-hdpi.apk";

    @Rule public TemporaryFolder testDir = new TemporaryFolder();
    private ModuleOemTargetPreparer mModuleOemTargetPreparer;
    @Mock ModulePusher mMockPusher;
    @Mock ITestDevice mMockDevice;
    private TestInformation mTestInfo;
    private File mFakeApex;
    private File mFakeApk;
    private File mFakeApkApks;
    private File mFakeSplitApk;
    private File mFakeHdpiApk;
    private File mFakeRecoverApex;
    private File mFakeRecoverSplitApk;
    private File mFakeRecoverHdpiApk;
    private OptionSetter mOptionSetter;
    private final ITestDevice.ApexInfo mFakeApexData =
            new ITestDevice.ApexInfo(APEX_PACKAGE_NAME, 1, APEX_PATH_ON_DEVICE);
    private File mRecoverRootDir;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFakeApex = testDir.newFile(TEST_APEX_NAME);
        mFakeApk = testDir.newFile(TEST_APK_NAME);
        mFakeApkApks = testDir.newFile(TEST_SPLIT_APK_APKS_NAME);
        testDir.newFolder("FakeSplit");
        mFakeSplitApk = testDir.newFile(TEST_SPLIT_APK_NAME);
        mFakeHdpiApk = testDir.newFile(TEST_HDPI_APK_NAME);
        mRecoverRootDir = testDir.newFolder();
        testDir.newFolder(mRecoverRootDir.getName(), APEX_PACKAGE_NAME);
        mFakeRecoverApex =
                testDir.newFile(
                        Paths.get(mRecoverRootDir.getName(), APEX_PACKAGE_NAME, TEST_APEX_NAME)
                                .toString());
        testDir.newFolder(mRecoverRootDir.getName(), SPLIT_APK_PACKAGE_NAME);
        mFakeRecoverSplitApk =
                testDir.newFile(
                        Paths.get(
                                        mRecoverRootDir.getName(),
                                        SPLIT_APK_PACKAGE_NAME,
                                        "base-master.apk")
                                .toString());
        mFakeRecoverHdpiApk =
                testDir.newFile(
                        Paths.get(
                                        mRecoverRootDir.getName(),
                                        SPLIT_APK_PACKAGE_NAME,
                                        "base-hdpi.apk")
                                .toString());
        when(mMockDevice.getApiLevel()).thenReturn(30 /* Build.VERSION_CODES.R */);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mModuleOemTargetPreparer =
                new ModuleOemTargetPreparer() {
                    @Override
                    protected String parsePackageName(File testAppFile) {
                        if (testAppFile.getName().endsWith(".apex")) {
                            return APEX_PACKAGE_NAME;
                        }
                        if (TEST_APK_NAME.equals(testAppFile.getName())) {
                            return APK_PACKAGE_NAME;
                        }
                        return SPLIT_APK_PACKAGE_NAME;
                    }

                    @Override
                    protected List<File> getSplitsForApks(
                            TestInformation testInfo, File moduleFile) {
                        List<File> result = new ArrayList<>();
                        result.add(mFakeSplitApk);
                        result.add(mFakeHdpiApk);
                        return result;
                    }

                    @Override
                    protected ModulePusher getPusher(ITestDevice device) {
                        return mMockPusher;
                    }
                };
        mOptionSetter = new OptionSetter(mModuleOemTargetPreparer);
        mOptionSetter.setOptionValue("ignore-if-module-not-preloaded", "true");
    }

    /** Test getting apk modules on device. */
    @Test
    public void testGetApkModules() {
        Set<String> modules =
                new HashSet<>(
                        Arrays.asList(APK_PACKAGE_NAME, APK_PACKAGE_NAME2, APEX_PACKAGE_NAME));
        Set<ITestDevice.ApexInfo> apexes = new HashSet<>(Collections.singletonList(mFakeApexData));
        Set<String> expected = new HashSet<>(Arrays.asList(APK_PACKAGE_NAME, APK_PACKAGE_NAME2));

        assertEquals(expected, mModuleOemTargetPreparer.getApkModules(modules, apexes));
    }

    /** Test setup installs non-split packages. */
    @Test
    public void testSetupInstallModules() throws Exception {
        addTestFiles(mFakeApk, mFakeApex);
        addPreloadPackages(APK_PACKAGE_NAME);
        ArgumentCaptor<ImmutableMultimap<String, File>> modulesCaptor =
                ArgumentCaptor.forClass(ImmutableMultimap.class);
        ImmutableMultimap<String, File> expectedArg =
                ImmutableMultimap.of(APEX_PACKAGE_NAME, mFakeApex, APK_PACKAGE_NAME, mFakeApk);

        mModuleOemTargetPreparer.setUp(mTestInfo);

        verify(mMockPusher)
                .installModules(
                        modulesCaptor.capture(),
                        /*factoryReset=*/ eq(false),
                        /*disablePackageCache=*/ eq(false));
        assertContainSameElements(expectedArg, modulesCaptor.getValue());
    }

    /** Test setup installs only preloaded packages. */
    @Test
    public void testSetupInstallsPreloadedModules() throws Exception {
        addTestFiles(mFakeApk, mFakeApex);
        addPreloadPackages();

        mModuleOemTargetPreparer.setUp(mTestInfo);

        verify(mMockPusher)
                .installModules(
                        ImmutableMultimap.of(APEX_PACKAGE_NAME, mFakeApex),
                        /*factoryReset=*/ false,
                        /*disablePackageCache=*/ false);
    }

    /** Test setup installs split apks. */
    @Test
    public void testSetupInstallApks() throws Exception {
        addTestFiles(mFakeApex, mFakeApkApks);
        addPreloadPackages(SPLIT_APK_PACKAGE_NAME);
        ArgumentCaptor<ImmutableMultimap<String, File>> modulesCaptor =
                ArgumentCaptor.forClass(ImmutableMultimap.class);
        ImmutableMultimap<String, File> expecteds =
                ImmutableMultimap.of(
                        APEX_PACKAGE_NAME,
                        mFakeApex,
                        SPLIT_APK_PACKAGE_NAME,
                        mFakeSplitApk,
                        SPLIT_APK_PACKAGE_NAME,
                        mFakeHdpiApk);

        mModuleOemTargetPreparer.setUp(mTestInfo);

        verify(mMockPusher)
                .installModules(
                        modulesCaptor.capture(),
                        /*factoryReset=*/ eq(false),
                        /*disablePackageCache=*/ eq(false));
        assertContainSameElements(expecteds, modulesCaptor.getValue());
    }

    /** Test setup recovers packages. */
    @Test
    public void testSetupRecoverModule() throws Exception {
        mOptionSetter.setOptionValue("push-test-modules", "false");
        mOptionSetter.setOptionValue("recover-preload-modules", "true");
        mOptionSetter.setOptionValue("recover-module-folder", mRecoverRootDir.getAbsolutePath());
        addPreloadPackages(SPLIT_APK_PACKAGE_NAME);
        ArgumentCaptor<ImmutableMultimap<String, File>> modulesCaptor =
                ArgumentCaptor.forClass(ImmutableMultimap.class);
        ImmutableMultimap<String, File> expecteds =
                ImmutableMultimap.of(
                        APEX_PACKAGE_NAME,
                        mFakeRecoverApex,
                        SPLIT_APK_PACKAGE_NAME,
                        mFakeRecoverSplitApk,
                        SPLIT_APK_PACKAGE_NAME,
                        mFakeRecoverHdpiApk);
        mModuleOemTargetPreparer.setUp(mTestInfo);

        verify(mMockPusher)
                .installModules(
                        modulesCaptor.capture(),
                        /*factoryReset=*/ eq(false),
                        /*disablePackageCache=*/ eq(false));
        assertContainSameElements(expecteds, modulesCaptor.getValue());
    }

    /** Test setup recovers only preloaded modules. */
    @Test
    public void testSetupRecoverPreloadedModules() throws Exception {
        mOptionSetter.setOptionValue("push-test-modules", "false");
        mOptionSetter.setOptionValue("recover-preload-modules", "true");
        mOptionSetter.setOptionValue("recover-module-folder", mRecoverRootDir.getAbsolutePath());
        addPreloadPackages();

        mModuleOemTargetPreparer.setUp(mTestInfo);

        verify(mMockPusher)
                .installModules(
                        ImmutableMultimap.of(APEX_PACKAGE_NAME, mFakeRecoverApex),
                        /*factoryReset=*/ false,
                        /*disablePackageCache=*/ false);
    }

    /** Test setup recover and install packages. */
    @Test
    public void testSetupRecoverAndInstall() throws Exception {
        mOptionSetter.setOptionValue("push-test-modules", "true");
        mOptionSetter.setOptionValue("recover-preload-modules", "true");
        mOptionSetter.setOptionValue("recover-module-folder", mRecoverRootDir.getAbsolutePath());
        addTestFiles(mFakeApex, mFakeApkApks);
        addPreloadPackages(SPLIT_APK_PACKAGE_NAME);
        ArgumentCaptor<ImmutableMultimap<String, File>> modulesCaptor =
                ArgumentCaptor.forClass(ImmutableMultimap.class);
        ImmutableMultimap<String, File> expectedRecovers =
                ImmutableMultimap.of(
                        APEX_PACKAGE_NAME,
                        mFakeRecoverApex,
                        SPLIT_APK_PACKAGE_NAME,
                        mFakeRecoverSplitApk,
                        SPLIT_APK_PACKAGE_NAME,
                        mFakeRecoverHdpiApk);
        ImmutableMultimap<String, File> expectedTests =
                ImmutableMultimap.of(
                        APEX_PACKAGE_NAME,
                        mFakeApex,
                        SPLIT_APK_PACKAGE_NAME,
                        mFakeSplitApk,
                        SPLIT_APK_PACKAGE_NAME,
                        mFakeHdpiApk);

        mModuleOemTargetPreparer.setUp(mTestInfo);

        verify(mMockPusher, times(2))
                .installModules(
                        modulesCaptor.capture(),
                        /*factoryReset=*/ eq(false),
                        /*disablePackageCache=*/ eq(false));
        List<ImmutableMultimap<String, File>> capturedValues = modulesCaptor.getAllValues();
        assertContainSameElements(expectedRecovers, capturedValues.get(0));
        assertContainSameElements(expectedTests, capturedValues.get(1));
    }

    /** Throws TargetSetupError if install fails. */
    @Test(expected = TargetSetupError.class)
    public void testSetupFailureIfInstallModulesFailure() throws Exception {
        addTestFiles(mFakeApex);
        addPreloadPackages();
        doThrow(
                        new ModulePusher.ModulePushError(
                                "Mock exception", DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE))
                .when(mMockPusher)
                .installModules(
                        any(), /*factoryReset=*/ eq(false), /*disablePackageCache=*/ eq(false));

        mModuleOemTargetPreparer.setUp(mTestInfo);
    }

    /** Throws TargetSetupError if recover fails. */
    @Test(expected = TargetSetupError.class)
    public void testSetupFailureIfRecoverFailure() throws Exception {
        mOptionSetter.setOptionValue("push-test-modules", "false");
        mOptionSetter.setOptionValue("recover-preload-modules", "true");
        mOptionSetter.setOptionValue("recover-module-folder", mRecoverRootDir.getAbsolutePath());
        addPreloadPackages();
        doThrow(
                        new ModulePusher.ModulePushError(
                                "Mock exception", DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE))
                .when(mMockPusher)
                .installModules(
                        any(), /*factoryReset=*/ eq(false), /*disablePackageCache=*/ eq(false));

        mModuleOemTargetPreparer.setUp(mTestInfo);
    }

    /** Skip recovering if recover module folder is not set. */
    @Test
    public void testSetupSkipRecoveryWithoutModuleFolder() throws Exception {
        mOptionSetter.setOptionValue("push-test-modules", "false");
        mOptionSetter.setOptionValue("recover-preload-modules", "true");
        addPreloadPackages(APEX_PACKAGE_NAME);

        mModuleOemTargetPreparer.setUp(mTestInfo);
        verify(mMockPusher, never())
                .installModules(any(), anyBoolean(), /*disablePackageCache=*/ anyBoolean());
    }

    /** Test that teardown without setup does not cause a NPE. */
    @Test
    public void testTearDown() throws Exception {
        mModuleOemTargetPreparer.tearDown(mTestInfo, null);
    }

    private void addTestFiles(File... testFiles) throws Exception {
        for (File testFile : testFiles) {
            mOptionSetter.setOptionValue("test-file-name", testFile.getAbsolutePath());
        }
    }

    private void addPreloadPackages(String... packageNames) throws DeviceNotAvailableException {
        when(mMockDevice.getInstalledPackageNames())
                .thenReturn(new HashSet<>(Arrays.asList(packageNames)));
        when(mMockDevice.getActiveApexes())
                .thenReturn(new HashSet<>(Collections.singletonList(mFakeApexData)));
    }

    /** Assert contain the same elements, regardless of the order. */
    private <K extends Comparable<? super K>, V extends Comparable<? super V>>
            void assertContainSameElements(
                    ImmutableMultimap<K, V> expecteds, ImmutableMultimap<K, V> actuals) {
        assertContainSameElements(expecteds.keys(), actuals.keys());
        for (K key : expecteds.keySet()) {
            assertContainSameElements(expecteds.get(key), actuals.get(key));
        }
    }

    /** Assert contain the same elements, regardless of the order. */
    private <T extends Comparable<? super T>> void assertContainSameElements(
            Collection<T> expecteds, Collection<T> actuals) {
        List<T> expectedList = new ArrayList<>(expecteds);
        List<T> actualList = new ArrayList<>(actuals);
        assertEquals(expectedList.size(), actualList.size());
        Collections.sort(expectedList);
        Collections.sort(actualList);
        for (int i = 0; i < expectedList.size(); i++) {
            assertEquals(expectedList.get(i), actualList.get(i));
        }
    }
}
