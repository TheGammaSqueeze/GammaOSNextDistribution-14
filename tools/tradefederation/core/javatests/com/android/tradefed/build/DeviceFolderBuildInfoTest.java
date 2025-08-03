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
package com.android.tradefed.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.build.IBuildInfo.BuildInfoProperties;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.FuseUtil;
import com.android.tradefed.util.SerializationUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.Collection;

/** Unit tests for {@link BuildInfo}. */
@RunWith(JUnit4.class)
public class DeviceFolderBuildInfoTest {
    private DeviceFolderBuildInfo mDeviceFolderBuildInfo;
    private File mFile;
    private FuseUtil mMockFuseUtil;

    @Before
    public void setUp() throws Exception {
        mDeviceFolderBuildInfo = new DeviceFolderBuildInfo("1", "target");
        mDeviceFolderBuildInfo.setFolderBuild(new FolderBuildInfo("1", "target"));
        mDeviceFolderBuildInfo.setDeviceBuild(new DeviceBuildInfo());
        mFile = FileUtil.createTempFile("image", "tmp");
        mMockFuseUtil = Mockito.mock(FuseUtil.class);
        Mockito.when(mMockFuseUtil.canMountZip()).thenReturn(true);
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                File mountDir = (File) invocation.getArgument(0);
                                FileUtil.recursiveDelete(mountDir);
                                return null;
                            }
                        })
                .when(mMockFuseUtil)
                .unmountZip(Mockito.any(File.class));
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mFile);
    }

    /** Return bool collection contains VersionedFile with specified version." */
    boolean hasFile(Collection<VersionedFile> files, String version) {
        for (VersionedFile candidate : files) {
            if (candidate.getVersion().equals(version)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test {@link DeviceFolderBuildInfo#getFiles()}. Verify files added to root and nested
     * DeviceBuildInfo are in result.
     */
    @Test
    public void testGetFiles_both() {
        mDeviceFolderBuildInfo.setFile("foo", mFile, "foo-version");
        mDeviceFolderBuildInfo.setDeviceImageFile(mFile, "img-version");
        Collection<VersionedFile> files = mDeviceFolderBuildInfo.getFiles();

        assertEquals(2, files.size());
        assertTrue(hasFile(files, "foo-version"));
        assertTrue(hasFile(files, "img-version"));
    }

    /**
     * Test {@link DeviceFolderBuildInfo#getFiles()}. Verify empty result when no files are present.
     */
    @Test
    public void testGetFiles_none() {
        Collection<VersionedFile> files = mDeviceFolderBuildInfo.getFiles();
        assertEquals(0, files.size());
    }

    /** Test {@link DeviceFolderBuildInfo#getFiles()}. Verify device image in result. */
    @Test
    public void testGetFiles_deviceImages() {
        mDeviceFolderBuildInfo.setDeviceImageFile(mFile, "img-version");
        Collection<VersionedFile> files = mDeviceFolderBuildInfo.getFiles();

        assertEquals(1, files.size());
        assertTrue(hasFile(files, "img-version"));
    }

    /**
     * Test that all the components of {@link DeviceFolderBuildInfo} can be serialized via the
     * default java object serialization.
     */
    @Test
    public void testSerialization() throws Exception {
        DeviceBuildInfo deviceBuildInfo = new DeviceBuildInfo();
        deviceBuildInfo.setDeviceImageFile(new File("fake"), "version 32");
        mDeviceFolderBuildInfo.setDeviceBuild(deviceBuildInfo);
        File tmpSerialized = SerializationUtil.serialize(mDeviceFolderBuildInfo);
        Object o = SerializationUtil.deserialize(tmpSerialized, true);
        assertTrue(o instanceof DeviceFolderBuildInfo);
        DeviceFolderBuildInfo test = (DeviceFolderBuildInfo) o;
        // use the custom build info equals to check similar properties
        assertTrue(mDeviceFolderBuildInfo.equals(test));
        // Both sub-build properties have been copied.
        assertEquals("version 32", mDeviceFolderBuildInfo.getDeviceBuildId());
        assertEquals("version 32", test.getDeviceBuildId());
    }

    /** Ensure that the property to skip device image copying is working. */
    @Test
    public void testProperty_skipCopy() throws Exception {
        File fakeDeviceImage = null;
        IBuildInfo info = null;
        try {
            fakeDeviceImage = FileUtil.createTempFile("fake_image", ".img");
            mDeviceFolderBuildInfo.setDeviceImageFile(fakeDeviceImage, "image");
            assertNotNull(mDeviceFolderBuildInfo.getDeviceImageFile());
            mDeviceFolderBuildInfo.setProperties(BuildInfoProperties.DO_NOT_COPY_IMAGE_FILE);
            info = mDeviceFolderBuildInfo.clone();
            assertNull(info.getFile(BuildInfoFileKey.DEVICE_IMAGE));
        } finally {
            FileUtil.deleteFile(fakeDeviceImage);
            if (info != null) {
                info.cleanUp();
            }
        }
    }

    /**
     * Test that when setting the device and folger build, the DeviceFolderBuildInfo gets all their
     * file.
     */
    @Test
    public void testSettingBuilds() {
        IFolderBuildInfo folderBuilder = new FolderBuildInfo("5555", "build_target2");
        folderBuilder.setRootDir(new File("package"));
        assertNotNull(folderBuilder.getRootDir());
        // Original build doesn't have the root dir yet.
        assertNull(mDeviceFolderBuildInfo.getRootDir());
        mDeviceFolderBuildInfo.setFolderBuild(folderBuilder);
        // folderBuild gave its file to the main build
        assertNotNull(mDeviceFolderBuildInfo.getRootDir());

        IDeviceBuildInfo deviceBuild = new DeviceBuildInfo("3333", "build_target3");
        deviceBuild.setBootloaderImageFile(new File("bootloader"), "v2");
        assertNull(mDeviceFolderBuildInfo.getBootloaderImageFile());

        mDeviceFolderBuildInfo.setDeviceBuild(deviceBuild);
        // deviceBuild gave its file to the main build
        assertNotNull(mDeviceFolderBuildInfo.getBootloaderImageFile());
    }

    /**
     * Test that when setting the device and folder build, the DeviceFolderBuildInfo gets all their
     * file. Then test
     */
    @Test
    public void testBuildWithZipMounts() {
        IFolderBuildInfo folderBuild = new FolderBuildInfo("1234", "cts_target", true);

        folderBuild.setRootDir(mFile);
        assertNotNull(folderBuild.getRootDir());

        DeviceFolderBuildInfo mDeviceFolderBuildInfoWithZipMount =
                new DeviceFolderBuildInfo("1111", "target", folderBuild.shouldUseFuseZip());

        // Set the build to use the mock fuse util for clean up.
        mDeviceFolderBuildInfoWithZipMount.setFuseUtil(mMockFuseUtil);

        // Original build doesn't have the root dir and zip mount files yet.
        assertNull(mDeviceFolderBuildInfoWithZipMount.getRootDir());
        assertTrue(mDeviceFolderBuildInfoWithZipMount.shouldUseFuseZip());

        // Set the folderBuild
        mDeviceFolderBuildInfoWithZipMount.setFolderBuild(folderBuild);
        // folderBuild should pass its file, its flag and list zip mount files to the main build
        assertNotNull(mDeviceFolderBuildInfoWithZipMount.getRootDir());
        assertTrue(mDeviceFolderBuildInfoWithZipMount.shouldUseFuseZip());

        // Clone the build which contains the zip mount info
        IBuildInfo buildInfoClone = mDeviceFolderBuildInfoWithZipMount.clone();

        // The clone should also have the zip mount info
        assertTrue(buildInfoClone instanceof DeviceFolderBuildInfo);
        assertNotNull(mDeviceFolderBuildInfoWithZipMount.getRootDir());
        assertTrue(((DeviceFolderBuildInfo) buildInfoClone).shouldUseFuseZip());

        // Set the cloned build to use the mock fuse util as the fuse util won't be copied.
        ((DeviceFolderBuildInfo) buildInfoClone).setFuseUtil(mMockFuseUtil);

        // Clean up the build
        buildInfoClone.cleanUp();
        assertNull(((DeviceFolderBuildInfo) buildInfoClone).getRootDir());
        mDeviceFolderBuildInfoWithZipMount.cleanUp();
        assertNull(mDeviceFolderBuildInfoWithZipMount.getRootDir());
    }
}

