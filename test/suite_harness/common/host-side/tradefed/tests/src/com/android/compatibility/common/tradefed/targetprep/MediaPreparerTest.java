/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link MediaPreparer}. */
@RunWith(JUnit4.class)
public class MediaPreparerTest {

    private MediaPreparer mMediaPreparer;
    private DeviceBuildInfo mMockBuildInfo;
    private ITestDevice mMockDevice;
    private OptionSetter mOptionSetter;
    private TestInformation mTestInfo;
    private File mTestsDir;

    @Before
    public void setUp() throws Exception {
        mMediaPreparer =
                new MediaPreparer() {
                    @Override
                    protected void setMaxRes(TestInformation testInfo)
                            throws DeviceNotAvailableException, TargetSetupError {
                        if (mMaxRes != null) return;
                        super.setMaxRes(testInfo);
                    }
                };
        mMediaPreparer.setUserId(0);
        mMockDevice = Mockito.mock(ITestDevice.class);
        mMockBuildInfo = new DeviceBuildInfo("0", "");
        mTestsDir = FileUtil.createTempDir("media-unit-tests");
        mMockBuildInfo.setTestsDir(mTestsDir, "1");
        new File(mTestsDir, "CtsMediaPreparerApp.apk").createNewFile();
        mOptionSetter = new OptionSetter(mMediaPreparer);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        context.addAllocatedDevice("device", mMockDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTestsDir);
    }

    @Test
    public void testLegacySetMountPoint() throws Exception {
        when(mMockDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).thenReturn(
                "/sdcard");

        mMediaPreparer.setMountPoint(mMockDevice);
        assertEquals(mMediaPreparer.mBaseDeviceShortDir, "/sdcard/test/bbb_short/");
        assertEquals(mMediaPreparer.mBaseDeviceFullDir, "/sdcard/test/bbb_full/");
    }

    @Test
    public void testSetMountPoint() throws Exception {
        String mediaFolderName = "unittest";
        mOptionSetter.setOptionValue("media-folder-name", mediaFolderName);
        mOptionSetter.setOptionValue("use-legacy-folder-structure", "false");
        when(mMockDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).thenReturn("/sdcard");

        mMediaPreparer.setMountPoint(mMockDevice);
        String baseDir = "/sdcard/test/" + mediaFolderName;
        assertEquals(mMediaPreparer.mBaseDeviceShortDir, baseDir + "/bbb_short/");
        assertEquals(mMediaPreparer.mBaseDeviceFullDir, baseDir + "/bbb_full/");
    }

    @Test
    public void testDefaultModuleDirMountPoint() throws Exception {
        when(mMockDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).thenReturn(
                "/sdcard");

        mMediaPreparer.setMountPoint(mMockDevice);
        assertEquals(mMediaPreparer.mBaseDeviceModuleDir, "/sdcard/test/android-cts-media/");
        assertEquals(mMediaPreparer.getMediaDir().getName(), "android-cts-media");
    }

    @Test
    public void testSetModuleDirMountPoint() throws Exception {
        mOptionSetter.setOptionValue("media-folder-name", "unittest");
        when(mMockDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).thenReturn(
                "/sdcard");

        mMediaPreparer.setMountPoint(mMockDevice);
        assertEquals(mMediaPreparer.mBaseDeviceModuleDir, "/sdcard/test/unittest/");
        assertEquals(mMediaPreparer.getMediaDir().getName(), "unittest");
    }

    @Test
    public void testCopyMediaFiles() throws Exception {
        mMediaPreparer.mMaxRes = MediaPreparer.RESOLUTIONS[1];
        mMediaPreparer.mBaseDeviceShortDir = "/sdcard/test/bbb_short/";
        mMediaPreparer.mBaseDeviceFullDir = "/sdcard/test/bbb_full/";
        mMediaPreparer.mBaseDeviceModuleDir = "/sdcard/test/android-cts-media/";
        for (MediaPreparer.Resolution resolution : MediaPreparer.RESOLUTIONS) {
            if (resolution.getWidth() > MediaPreparer.RESOLUTIONS[1].getWidth()) {
                // Stop when we reach the default max resolution
                continue;
            }
            String shortFile = String.format("%s%s", mMediaPreparer.mBaseDeviceShortDir,
                    resolution.toString());
            String fullFile = String.format("%s%s", mMediaPreparer.mBaseDeviceFullDir,
                    resolution.toString());
            when(mMockDevice.doesFileExist(shortFile)).thenReturn(true);
            when(mMockDevice.doesFileExist(fullFile)).thenReturn(true);
        }
        when(mMockDevice.doesFileExist(mMediaPreparer.mBaseDeviceModuleDir))
                .thenReturn(false);

        mMediaPreparer.copyMediaFiles(mMockDevice);
    }

    @Test
    public void testMediaFilesExistOnDeviceTrue() throws Exception {
        mMediaPreparer.mMaxRes = MediaPreparer.RESOLUTIONS[1];
        mMediaPreparer.mBaseDeviceShortDir = "/sdcard/test/bbb_short/";
        mMediaPreparer.mBaseDeviceFullDir = "/sdcard/test/bbb_full/";
        for (MediaPreparer.Resolution resolution : MediaPreparer.RESOLUTIONS) {
            String shortFile = String.format("%s%s", mMediaPreparer.mBaseDeviceShortDir,
                    resolution.toString());
            String fullFile = String.format("%s%s", mMediaPreparer.mBaseDeviceFullDir,
                    resolution.toString());
            when(mMockDevice.doesFileExist(shortFile, 0)).thenReturn(true);
            when(mMockDevice.doesFileExist(fullFile, 0)).thenReturn(true);
        }
        assertTrue(mMediaPreparer.mediaFilesExistOnDevice(mMockDevice));
    }

    @Test
    public void testMediaFilesExistOnDeviceTrueWithPushAll() throws Exception {
        mOptionSetter.setOptionValue("push-all", "true");
        mMediaPreparer.mBaseDeviceModuleDir = "/sdcard/test/android-cts-media/";
        when(mMockDevice.doesFileExist(mMediaPreparer.mBaseDeviceModuleDir, 0)).thenReturn(true);

        assertTrue(mMediaPreparer.mediaFilesExistOnDevice(mMockDevice));
    }

    @Test
    public void testMediaFilesExistOnDeviceFalse() throws Exception {
        mMediaPreparer.mMaxRes = MediaPreparer.RESOLUTIONS[1];
        mMediaPreparer.mBaseDeviceShortDir = "/sdcard/test/bbb_short/";
        String firstFileChecked = "/sdcard/test/bbb_short/176x144";
        when(mMockDevice.doesFileExist(firstFileChecked)).thenReturn(false);

        assertFalse(mMediaPreparer.mediaFilesExistOnDevice(mMockDevice));
    }

    @Test
    public void testSkipMediaDownload() throws Exception {
        mOptionSetter.setOptionValue("skip-media-download", "true");

        mMediaPreparer.setUp(mTestInfo);
    }

    @Test
    public void testPushAll() throws Exception {
        mOptionSetter.setOptionValue("push-all", "true");
        mOptionSetter.setOptionValue("media-folder-name", "unittest");
        mMediaPreparer.mBaseDeviceModuleDir = "/sdcard/test/unittest/";
        mMediaPreparer.mBaseDeviceShortDir = "/sdcard/test/bbb_short/";
        mMediaPreparer.mBaseDeviceFullDir = "/sdcard/test/bbb_full/";
        when(mMockDevice.doesFileExist(mMediaPreparer.mBaseDeviceModuleDir, 0)).thenReturn(true);
        when(mMockDevice.doesFileExist(mMediaPreparer.mBaseDeviceShortDir, 0)).thenReturn(false);
        when(mMockDevice.doesFileExist(mMediaPreparer.mBaseDeviceFullDir, 0)).thenReturn(false);

        mMediaPreparer.copyMediaFiles(mMockDevice);
    }

    /** Test that if we decide to run and files are on the device, we don't download again. */
    @Test
    public void testMediaDownloadOnly_existsOnDevice() throws Exception {
        mMediaPreparer.mMaxRes = MediaPreparer.RESOLUTIONS[3];
        mOptionSetter.setOptionValue("local-media-path", "/fake/media/dir");
        mMediaPreparer.mBaseDeviceShortDir = "/sdcard/test/bbb_short/";
        mMediaPreparer.mBaseDeviceFullDir = "/sdcard/test/bbb_full/";

        when(mMockDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE))
                .thenReturn("/sdcard");
        for (MediaPreparer.Resolution resolution : MediaPreparer.RESOLUTIONS) {
            if (resolution.getWidth() > MediaPreparer.RESOLUTIONS[1].getWidth()) {
                // Stop when we reach the default max resolution
                continue;
            }
            String shortFile =
                    String.format(
                            "%s%s", mMediaPreparer.mBaseDeviceShortDir, resolution.toString());
            String fullFile =
                    String.format("%s%s", mMediaPreparer.mBaseDeviceFullDir, resolution.toString());
            when(mMockDevice.doesFileExist(shortFile)).thenReturn(true);
            when(mMockDevice.doesFileExist(fullFile)).thenReturn(true);
        }

        mMediaPreparer.setUp(mTestInfo);
    }

    private void setUpTocTests() throws Exception {
        mOptionSetter.setOptionValue("push-all", "true");
        mOptionSetter.setOptionValue("media-folder-name", "toc");
        mOptionSetter.setOptionValue("simple-caching-semantics", "false");
        mMediaPreparer.mBaseDeviceModuleDir = "/sdcard/test/toc/";

        when(mMockDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE))
                .thenReturn("/sdcard");
        when(mMockDevice.doesFileExist(Mockito.any()))
                .thenReturn(false);
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.pushDir(Mockito.any(), Mockito.any()))
                .thenReturn(true);
        when(mMockDevice.executeShellCommand(Mockito.any()))
                .thenReturn("");
    }

    /** Test that if TOC doesn't exist, we download again */
    @Test
    public void testMissingTOC() throws Exception {
        setUpTocTests();

        File mediaFolder = mMediaPreparer.getMediaDir();
        mediaFolder.mkdirs();

        // In order to test non-existent TOC triggers a download, need to ensure there is at
        // least a file in the folder, otherwise empty folder triggers a download */
        File file = new File(mediaFolder, "file");
        file.createNewFile();

        try {
            mMediaPreparer.setUp(mTestInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        } finally {
            FileUtil.recursiveDelete(mediaFolder);
        }
    }

    /** Test that if TOC has a non-existent file, we download again */
    @Test
    public void testNonExistentFileInTOC() throws Exception {
        setUpTocTests();
        File mediaFolder = mMediaPreparer.getMediaDir();
        mediaFolder.mkdirs();

        File file = new File(mediaFolder, "file");
        file.createNewFile();

        File tocFile = new File(mediaFolder, MediaPreparer.TOC_NAME);
        String content = "file\n" + "non-existent-file";
        FileUtil.writeToFile(content, tocFile);

        try {
            mMediaPreparer.setUp(mTestInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        } finally {
            FileUtil.recursiveDelete(mediaFolder);
        }
    }

    /** Test that if TOC is valid, we don't download again */
    @Test
    public void testValidTOC() throws Exception {
        setUpTocTests();

        File mediaFolder = mMediaPreparer.getMediaDir();
        mediaFolder.mkdirs();
        File file = new File(mediaFolder, "file");
        file.createNewFile();

        File tocFile = new File(mediaFolder, MediaPreparer.TOC_NAME);
        String content = "file\n";
        FileUtil.writeToFile(content, tocFile);

        try {
            mMediaPreparer.setUp(mTestInfo);
        } finally {
            FileUtil.recursiveDelete(mediaFolder);
        }
    }

    @Test
    public void testGetDynamicConfig() throws Exception {
        Configuration config = new Configuration("name", "test");
        mMediaPreparer.setConfiguration(config);
        List<ITargetPreparer> preparers = new ArrayList<>();
        DynamicConfigPusher pusher = new DynamicConfigPusher();
        pusher.setModuleName("CtsModuleName");
        preparers.add(pusher);
        preparers.add(mMediaPreparer);
        config.setTargetPreparers(preparers);
        assertEquals("CtsModuleName", mMediaPreparer.getDynamicModuleName());
    }

    @Test
    public void testGetDynamicConfig_outOfOrder() throws Exception {
        Configuration config = new Configuration("name", "test");
        mMediaPreparer.setConfiguration(config);
        List<ITargetPreparer> preparers = new ArrayList<>();
        preparers.add(mMediaPreparer);
        DynamicConfigPusher pusher = new DynamicConfigPusher();
        pusher.setModuleName("CtsModuleName");
        preparers.add(pusher);
        config.setTargetPreparers(preparers);
        try {
            mMediaPreparer.getDynamicModuleName();
            fail("Should have thrown an exception.");
        } catch (TargetSetupError expected) {
            // Expected
        }
    }

    @Test
    public void testDownloadOnly() throws Exception {
        mOptionSetter.setOptionValue("media-download-only", "true");
        mOptionSetter.setOptionValue("local-media-path", "/fake/media/dir");
        mMediaPreparer.setUp(mTestInfo);

        verify(mMockDevice, never()).getMountPoint(Mockito.any());
        verify(mMockDevice, never()).doesFileExist(Mockito.any());
    }
}
