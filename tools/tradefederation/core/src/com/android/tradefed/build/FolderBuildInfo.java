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
import com.android.tradefed.util.FuseUtil;

import java.io.File;

/**
 * Concrete implementation of a {@link IFolderBuildInfo}.
 */
public class FolderBuildInfo extends BuildInfo implements IFolderBuildInfo {

    private static final long serialVersionUID = BuildSerializedVersion.VERSION;

    /**
     * The flag to indicate whether the build is using fuse-zip. This should align with the
     * FolderBuildInfo which this DeviceFolderBuild refer to.
     */
    private boolean mUseFuseZip = false;

    /** @see BuildInfo#BuildInfo() */
    public FolderBuildInfo() {
        super();
    }

    /**
     * @see BuildInfo#BuildInfo(String, String)
     */
    public FolderBuildInfo(String buildId, String buildName) {
        super(buildId, buildName);
    }

    /**
     * Creates a {@link FolderBuildInfo} The constructor allows the flag of mUseFuseZip to be
     * configured at the time of building up the FolderBuildInfo.
     *
     * @param buildId the build id
     * @param buildName the build target name
     * @param useFuseZip the flag to determine if the build uses zip mounting
     */
    public FolderBuildInfo(String buildId, String buildName, Boolean useFuseZip) {
        super(buildId, buildName);
        mUseFuseZip = useFuseZip;
    }

    /**
     * @see BuildInfo#BuildInfo(BuildInfo)
     */
    FolderBuildInfo(BuildInfo buildToCopy) {
        super(buildToCopy);
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldUseFuseZip() {
        return mUseFuseZip;
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

    /**
     * {@inheritDoc} Additionally, unmount fuse-zip mounted files based on the list of fuse-zip
     * mounted files.
     */
    @Override
    public void cleanUp() {
        if (mUseFuseZip) {
            FuseUtil fuseUtil = new FuseUtil();
            fuseUtil.unmountZip(this.getRootDir());
        }
        super.cleanUp();
    }
}

