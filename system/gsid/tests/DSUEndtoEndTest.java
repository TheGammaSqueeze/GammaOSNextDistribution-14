/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tests.dsu;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.SparseImageUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil2;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.Before;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Test Dynamic System Updates by booting in and out of a supplied system image
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class DSUEndtoEndTest extends DsuTestBase {
    private static final long kDefaultUserdataSize = 4L * 1024 * 1024 * 1024;
    private static final String LPUNPACK_PATH = "bin/lpunpack";

    // Example: atest -v DSUEndtoEndTest -- --test-arg \
    // com.android.tradefed.testtype.HostTest:set-option:system_image_path:/full/path/to/system.img
    @Option(name="system_image_path",
            shortName='s',
            description="full path to the system image to use. If not specified, attempt " +
                        "to download the image from the test infrastructure",
            importance=Importance.ALWAYS)
    private String mSystemImagePath;

    private File mTempDir;

    private File getTempPath(String relativePath) throws IOException {
        if (mTempDir == null) {
            mTempDir = FileUtil.createTempDir("DSUEndtoEndTest");
        }
        return new File(mTempDir, relativePath);
    }

    /** Extract system.img from build info to a temproary file. */
    private File extractSystemImageFromBuildInfo(IBuildInfo buildInfo)
            throws IOException, InterruptedException {
        File imgZip = ((IDeviceBuildInfo) buildInfo).getDeviceImageFile();
        Assert.assertNotNull(
                "Failed to fetch system image. See system_image_path parameter", imgZip);

        File superImg = getTempPath("super.img");
        try (ZipFile zip = new ZipFile(imgZip)) {
            File systemImg = getTempPath("system.img");
            if (ZipUtil2.extractFileFromZip(zip, "system.img", systemImg)) {
                return systemImg;
            }
            Assert.assertTrue(
                    "No system.img or super.img in img zip.",
                    ZipUtil2.extractFileFromZip(zip, "super.img", superImg));
        }

        if (SparseImageUtil.isSparse(superImg)) {
            File unsparseSuperImage = getTempPath("super.raw");
            SparseImageUtil.unsparse(superImg, unsparseSuperImage);
            superImg = unsparseSuperImage;
        }

        File otaTools = buildInfo.getFile("otatools.zip");
        Assert.assertNotNull("No otatools.zip in BuildInfo.", otaTools);
        File otaToolsDir = getTempPath("otatools");
        ZipUtil2.extractZip(otaTools, otaToolsDir);

        String lpunpackPath = new File(otaToolsDir, LPUNPACK_PATH).getAbsolutePath();
        File outputDir = getTempPath("lpunpack_output");
        outputDir.mkdirs();
        String[] cmd = {
            lpunpackPath, "-p", "system_a", superImg.getAbsolutePath(), outputDir.getAbsolutePath()
        };
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
        if (p.exitValue() != 0) {
            String stderr = StreamUtil.getStringFromStream(p.getErrorStream());
            Assert.fail(String.format("lpunpack returned %d. (%s)", p.exitValue(), stderr));
        }
        return new File(outputDir, "system_a.img");
    }

    @Before
    public void setUp() {
        mTempDir = null;
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mTempDir);
    }

    @Test
    public void testDSU() throws Exception {
        File systemImage;
        if (mSystemImagePath != null) {
            systemImage = new File(mSystemImagePath);
        } else {
            systemImage = extractSystemImageFromBuildInfo(getBuild());
        }
        Assert.assertTrue("not a valid file", systemImage.isFile());

        if (SparseImageUtil.isSparse(systemImage)) {
            File unsparseSystemImage = getTempPath("system.raw");
            SparseImageUtil.unsparse(systemImage, unsparseSystemImage);
            systemImage = unsparseSystemImage;
        }

        boolean wasRoot = getDevice().isAdbRoot();
        if (!wasRoot)
            Assert.assertTrue("Test requires root", getDevice().enableAdbRoot());

        assertDsuStatus("normal");

        // Sleep after installing to allow time for gsi_tool to reboot. This prevents a race between
        // the device rebooting and waitForDeviceAvailable() returning.
        getDevice()
                .executeShellV2Command(
                        String.format(
                                "gsi_tool install --userdata-size %d"
                                        + " --gsi-size %d"
                                        + " && sleep 10000000",
                                getDsuUserdataSize(kDefaultUserdataSize), systemImage.length()),
                        systemImage,
                        null,
                        10,
                        TimeUnit.MINUTES,
                        1);
        getDevice().waitForDeviceAvailable();
        getDevice().enableAdbRoot();

        assertDsuStatus("running");

        getDevice().rebootUntilOnline();

        assertDsuStatus("installed");

        assertShellCommand("gsi_tool enable");

        getDevice().reboot();

        assertDsuStatus("running");

        getDevice().reboot();

        assertDsuStatus("running");

        assertShellCommand("gsi_tool wipe");

        getDevice().rebootUntilOnline();

        assertDsuStatus("normal");

        if (wasRoot) {
            getDevice().enableAdbRoot();
        }
    }
}

