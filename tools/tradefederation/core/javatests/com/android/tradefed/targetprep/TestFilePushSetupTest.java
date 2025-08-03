/*
 * Copyright (C) 2012 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.FakeTestsZipFolder;
import com.android.tradefed.util.FakeTestsZipFolder.ItemType;
import com.android.tradefed.util.FileUtil;

import com.google.common.io.Files;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A test for {@link TestFilePushSetup}. */
@RunWith(JUnit4.class)
public class TestFilePushSetupTest {

    private Map<String, ItemType> mFiles;
    private List<String> mDeviceLocationList;
    private FakeTestsZipFolder mFakeTestsZipFolder;
    private File mAltDirFile1, mAltDirFile2;
    private static final String ALT_FILENAME1 = "foobar";
    private static final String ALT_FILENAME2 = "barfoo";

    @Mock ITestDevice mMockDevice;
    private TestInformation mTestInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFiles = new HashMap<String, ItemType>();
        mFiles.put("app/AndroidCommonTests.apk", ItemType.FILE);
        mFiles.put("app/GalleryTests.apk", ItemType.FILE);
        mFiles.put("testinfo", ItemType.DIRECTORY);
        mFiles.put(ALT_FILENAME1, ItemType.FILE);
        mFakeTestsZipFolder = new FakeTestsZipFolder(mFiles);
        assertTrue(mFakeTestsZipFolder.createItems());
        mDeviceLocationList = new ArrayList<String>();
        for (String file : mFiles.keySet()) {
            mDeviceLocationList.add(TestFilePushSetup.getDevicePathFromUserData(file));
        }
        File tmpBase = Files.createTempDir();
        mAltDirFile1 = new File(tmpBase, ALT_FILENAME1);
        assertTrue("failed to create temp file", mAltDirFile1.createNewFile());
        mAltDirFile2 = new File(tmpBase, ALT_FILENAME2);
        assertTrue("failed to create temp file", mAltDirFile2.createNewFile());

        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");
    }

    @After
    public void tearDown() throws Exception {
        mFakeTestsZipFolder.cleanUp();
        File tmpDir = mAltDirFile1.getParentFile();
        FileUtil.deleteFile(mAltDirFile1);
        FileUtil.deleteFile(mAltDirFile2);
        FileUtil.recursiveDelete(tmpDir);
    }

    @Test
    public void testSetup() throws TargetSetupError, BuildError, DeviceNotAvailableException {
        TestFilePushSetup testFilePushSetup = new TestFilePushSetup();
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        stubBuild.setTestsDir(mFakeTestsZipFolder.getBasePath(), "0");
        assertFalse(mFiles.isEmpty());
        assertFalse(mDeviceLocationList.isEmpty());
        ITestDevice device = mock(ITestDevice.class);
        when(device.pushDir((File) Mockito.any(), (String) Mockito.any()))
                .thenAnswer(
                        invocation -> {
                            return mDeviceLocationList.remove(invocation.getArguments()[1]);
                        });
        when(device.pushFile((File) Mockito.any(), (String) Mockito.any()))
                .thenAnswer(
                        invocation -> {
                            return mDeviceLocationList.remove(invocation.getArguments()[1]);
                        });
        when(device.executeShellCommand((String) Mockito.any())).thenReturn("");

        for (String file : mFiles.keySet()) {
            testFilePushSetup.addTestFileName(file);
        }

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", device);
        context.addDeviceBuildInfo("device", stubBuild);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        testFilePushSetup.setUp(mTestInfo);
        assertTrue(mDeviceLocationList.isEmpty());
        verify(device, times(4)).executeShellCommand((String) Mockito.any());
    }

    /** Test that setup throws an exception if provided with something else than DeviceBuildInfo */
    @Test
    public void testSetup_notDeviceBuildInfo() throws Exception {
        TestFilePushSetup testFilePushSetup = new TestFilePushSetup();
        BuildInfo stubBuild = new BuildInfo("stub", "stub");
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mock(ITestDevice.class));
        context.addDeviceBuildInfo("device", stubBuild);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        try {
            testFilePushSetup.setUp(mTestInfo);
            fail("should have thrown an exception");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "Provided buildInfo is not a com.android.tradefed.build.IDeviceBuildInfo",
                    expected.getMessage());
        }
    }

    /** Test that an exception is thrown if the file doesn't exist in extracted test dir */
    @Test
    public void testThrowIfNotFound() throws Exception {
        TestFilePushSetup setup = new TestFilePushSetup();
        setup.setThrowIfNoFile(true);
        // Assuming that the "file-not-in-test-zip" file doesn't exist in the test zips folder.
        setup.addTestFileName("file-not-in-test-zip");
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        stubBuild.setTestsDir(mFakeTestsZipFolder.getBasePath(), "0");
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", stubBuild);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        try {
            setup.setUp(mTestInfo);
            fail("Should have thrown an exception");
        } catch (TargetSetupError expected) {
            assertEquals(
                    "Could not find test file file-not-in-test-zip "
                            + "directory in extracted tests.zip",
                    expected.getMessage());
        }
    }

    /**
     * Test that no exception is thrown if the file doesn't exist in extracted test dir given that
     * the option "throw-if-not-found" is set to false.
     */
    @Test
    public void testThrowIfNotFound_false() throws Exception {
        TestFilePushSetup setup = new TestFilePushSetup();
        setup.setThrowIfNoFile(false);
        // Assuming that the "file-not-in-test-zip" file doesn't exist in the test zips folder.
        setup.addTestFileName("file-not-in-test-zip");
        DeviceBuildInfo stubBuild = new DeviceBuildInfo("0", "stub");
        stubBuild.setTestsDir(mFakeTestsZipFolder.getBasePath(), "0");
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", stubBuild);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        // test that it does not throw
        setup.setUp(mTestInfo);
    }
}
