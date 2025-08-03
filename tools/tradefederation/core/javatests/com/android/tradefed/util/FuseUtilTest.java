/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tradefed.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;

/** Unit tests for {@link FuseUtil}. */
@RunWith(JUnit4.class)
public class FuseUtilTest {

    private IRunUtil mRunUtil;
    private FuseUtil mFuseUtil;

    @Before
    public void setUp() {
        mRunUtil = Mockito.mock(IRunUtil.class);
        mFuseUtil =
                new FuseUtil() {
                    @Override
                    IRunUtil getRunUtil() {
                        return mRunUtil;
                    }
                };
    }

    @Test
    public void testCanMountZip() {
        mFuseUtil.resetCanMountZip();
        CommandResult haveFuseZipInstalledResult = new CommandResult();
        haveFuseZipInstalledResult.setStatus(CommandStatus.SUCCESS);
        haveFuseZipInstalledResult.setStdout("/abc/def/fuse-zip");
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.<String>any()))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS))
                .thenReturn(haveFuseZipInstalledResult);

        Assert.assertTrue(mFuseUtil.canMountZip());

        Mockito.verify(mRunUtil)
                .runTimedCmd(FuseUtil.FUSE_ZIP_TIMEOUT_MILLIS, "test", "-c", "/dev/fuse");
        Mockito.verify(mRunUtil).runTimedCmd(FuseUtil.FUSE_ZIP_TIMEOUT_MILLIS, "which", "fuse-zip");
    }

    @Test
    public void testCanMountZip_unsupported() {
        mFuseUtil.resetCanMountZip();
        CommandResult noFuseZipInstalledResult = new CommandResult();
        noFuseZipInstalledResult.setStatus(CommandStatus.SUCCESS);
        noFuseZipInstalledResult.setStdout("");
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.<String>any()))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS))
                .thenReturn(noFuseZipInstalledResult);

        Assert.assertFalse(mFuseUtil.canMountZip());

        Mockito.verify(mRunUtil)
                .runTimedCmd(FuseUtil.FUSE_ZIP_TIMEOUT_MILLIS, "test", "-c", "/dev/fuse");
        Mockito.verify(mRunUtil).runTimedCmd(FuseUtil.FUSE_ZIP_TIMEOUT_MILLIS, "which", "fuse-zip");
    }

    @Test
    public void testMountZip() {
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.<String>any()))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));
        File zipFile = new File("/path/to/zip");
        File mountDir = new File("/path/to/mount");

        mFuseUtil.mountZip(zipFile, mountDir);

        Mockito.verify(mRunUtil)
                .runTimedCmd(
                        FuseUtil.FUSE_ZIP_TIMEOUT_MILLIS,
                        "fuse-zip",
                        "-r",
                        zipFile.getAbsolutePath(),
                        mountDir.getAbsolutePath());
    }

    @Test
    public void testUnmountZip() {
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.<String>any()))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));
        File mountDir = new File("/path/to/mount");

        mFuseUtil.unmountZip(mountDir);

        Mockito.verify(mRunUtil)
                .runTimedCmd(
                        FuseUtil.FUSE_ZIP_TIMEOUT_MILLIS,
                        "fusermount",
                        "-u",
                        mountDir.getAbsolutePath());
    }
}
