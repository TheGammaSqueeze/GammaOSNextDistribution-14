/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.tradefed.build.BuildInfoKey.BuildInfoFileKey;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.FuseUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * A {@link IDeviceBuildInfo} that also contains other build artifacts contained in a directory on
 * the local filesystem.
 */
public class DeviceFolderBuildInfo extends DeviceBuildInfo
        implements IDeviceBuildInfo, IFolderBuildInfo {

    private static final long serialVersionUID = BuildSerializedVersion.VERSION;

    /**
     * The flag to indicate whether the build is using fuse-zip. This should align with the
     * FolderBuildInfo which this DeviceFolderBuild refers to.
     */
    private boolean mUseFuseZip;

    /** The fuse util which will be used to execute fuse-zip commands. */
    private transient FuseUtil mFuseUtil = new FuseUtil();

    /**
     * @see DeviceBuildInfo#DeviceBuildInfo(String, String)
     */
    public DeviceFolderBuildInfo(String buildId, String buildName) {
        super(buildId, buildName);
        mUseFuseZip = false;
    }

    /**
     * Creates a {@link DeviceFolderBuildInfo} The constructor allows the flag of mUseFuseZip to be
     * configured at the time of building up the DeviceFolderBuildInfo.
     *
     * @param buildId the build id
     * @param buildName the build target name
     * @param useFuseZip the flag to determine if the build uses zip mounting
     */
    public DeviceFolderBuildInfo(String buildId, String buildName, boolean useFuseZip) {
        super(buildId, buildName);
        mUseFuseZip = useFuseZip;
    }

    /**
     * @see DeviceBuildInfo#DeviceBuildInfo()
     */
    public DeviceFolderBuildInfo() {
        super();
        mUseFuseZip = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getRootDir() {
        return getFile(BuildInfoFileKey.ROOT_DIRECTORY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRootDir(File rootDir) {
        setFile(
                BuildInfoFileKey.ROOT_DIRECTORY,
                rootDir,
                BuildInfoFileKey.ROOT_DIRECTORY.getFileKey());
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldUseFuseZip() {
        return mUseFuseZip;
    }

    /** Copy all the files from the {@link IFolderBuildInfo}. */
    public void setFolderBuild(IFolderBuildInfo folderBuild) {
        copyAllFileFrom((BuildInfo) folderBuild);
    }

    /** Copy all the files from the {@link IDeviceBuildInfo}. */
    public void setDeviceBuild(IDeviceBuildInfo deviceBuild) {
        copyAllFileFrom((BuildInfo) deviceBuild);
    }

    @VisibleForTesting
    FuseUtil getFuseUtil() {
        return mFuseUtil;
    }

    @VisibleForTesting
    void setFuseUtil(FuseUtil fuseUtil) {
        mFuseUtil = fuseUtil;
    }

    /** {@inheritDoc} Create symlinks for fuse-zip mounted files, instead of hardlinks. */
    @Override
    protected void addAllFiles(BuildInfo build) throws IOException {
        DeviceFolderBuildInfo folderBuildInfo = (DeviceFolderBuildInfo) build;
        this.mUseFuseZip = folderBuildInfo.shouldUseFuseZip();
        if (!this.mUseFuseZip) {
            super.addAllFiles(build);
            return;
        }
        File rootDir = folderBuildInfo.getRootDir();
        for (Map.Entry<String, VersionedFile> fileEntry :
                folderBuildInfo.getVersionedFileMap().entrySet()) {
            File origFile = fileEntry.getValue().getFile();
            boolean isFuseMountedDir = (rootDir != null) && rootDir.equals(origFile);
            if (applyBuildProperties(fileEntry.getValue(), build, this)) {
                continue;
            }
            File copyFile;
            if (origFile.isDirectory()) {
                copyFile = FileUtil.createTempDir(fileEntry.getKey());
                if (isFuseMountedDir) {
                    // The mount point of the zip file is from fuse file system, and hard link
                    // can't be used between different file systems. Hence, symlink is used here.
                    FileUtil.recursiveSymlink(origFile, copyFile);
                } else {
                    FileUtil.recursiveHardlink(origFile, copyFile, false);
                }
            } else {
                // Only using createTempFile to create a unique dest filename
                copyFile =
                        FileUtil.createTempFile(
                                fileEntry.getKey(), FileUtil.getExtension(origFile.getName()));
                copyFile.delete();
                if (isFuseMountedDir) {
                    FileUtil.symlinkFile(origFile, copyFile);
                } else {
                    FileUtil.hardlinkFile(origFile, copyFile);
                }
            }
            setFile(fileEntry.getKey(), copyFile, fileEntry.getValue().getVersion());
        }
    }

    /**
     * {@inheritDoc} Additionally, unmount fuse-zip mounted files based on the list of fuse-zip
     * mounted files.
     */
    @Override
    public void cleanUp() {
        if (mUseFuseZip) {
            File rootDir = getRootDir();
            FuseUtil fuseUtil = getFuseUtil();
            fuseUtil.unmountZip(rootDir);
            FileUtil.recursiveDelete(rootDir);
        }
        super.cleanUp();
    }
}

