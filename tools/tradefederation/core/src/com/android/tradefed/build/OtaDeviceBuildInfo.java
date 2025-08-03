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

package com.android.tradefed.build;

import com.android.tradefed.device.ITestDevice;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * A {@link IDeviceBuildInfo} used for over-the-air update testing. It is composed of two device
 * builds for {@link ITestDevice}:
 *
 * <ul>
 *   <li>a baseline build image (the build to OTA from).
 *   <li>a OTA build (a build to OTA to). Should contain necessary build attributes and associated
 *       OTA package.
 * </ul>
 *
 * <var>this</var> contains the baseline build, and {@link #getOtaBuild()} returns the OTA build.
 */
public class OtaDeviceBuildInfo extends DeviceBuildInfo {

    private static final long serialVersionUID = BuildSerializedVersion.VERSION;
    private static final String OTA_TOOLS_DIR_KEY_NAME = "otatools-dir";
    private static final String BASELINE_TARGET_FILE_KEY_NAME = "baseline-target-file";
    private static final String TARGET_TARGET_FILE_KEY_NAME = "target-target-file";
    private static final String BASELINE_OTA_TOOLS_KEY_NAME = "baseline-otatools";
    private static final String TARGET_OTA_TOOLS_KEY_NAME = "target-otatools";

    protected IDeviceBuildInfo mOtaBuild;
    protected boolean mReportTargetBuild = false;

    public OtaDeviceBuildInfo() {
        super();
    }

    public OtaDeviceBuildInfo(IDeviceBuildInfo buildInfo) {
        super((BuildInfo) buildInfo);
    }

    public void setOtaBuild(IDeviceBuildInfo otaBuild) {
        mOtaBuild = otaBuild;
    }

    public IDeviceBuildInfo getOtaBuild() {
        return mOtaBuild;
    }

    public String getBaselineBuildId() {
        return super.getBuildId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildId() {
        if (mReportTargetBuild) {
            return mOtaBuild.getBuildId();
        }
        return super.getBuildId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildTargetName() {
        if (mReportTargetBuild) {
            return mOtaBuild.getBuildTargetName();
        }
        return super.getBuildTargetName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildFlavor() {
        if (mReportTargetBuild) {
            return mOtaBuild.getBuildFlavor();
        }
        return super.getBuildFlavor();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildBranch() {
        if (mReportTargetBuild) {
            return mOtaBuild.getBuildBranch();
        }
        return super.getBuildBranch();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp() {
        super.cleanUp();
        if (mOtaBuild != null) {
            mOtaBuild.cleanUp();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void cleanUp(List<File> doNotDelete) {
        super.cleanUp();
        mOtaBuild.cleanUp(doNotDelete);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo clone() {
        OtaDeviceBuildInfo clone = (OtaDeviceBuildInfo) super.clone();
        if (mOtaBuild != null) {
            clone.setOtaBuild((IDeviceBuildInfo)mOtaBuild.clone());
        }
        return clone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<VersionedFile> getFiles() {
        Collection<VersionedFile> combinedFiles = super.getFiles();
        combinedFiles.addAll(mOtaBuild.getFiles());
        return combinedFiles;
    }

    public void setReportTargetBuild(boolean downgrade) {
        mReportTargetBuild = downgrade;
    }

    public void setOtaTools(File otaTools, String version) {
        setFile(OTA_TOOLS_DIR_KEY_NAME, otaTools, version);
    }

    public File getOtaTools() {
        return getFile(OTA_TOOLS_DIR_KEY_NAME);
    }

    public void setBaselineTargetFileZip(File file, String version) {
        setFile(BASELINE_TARGET_FILE_KEY_NAME, file, version);
    }
    public void setTargetTargetFileZip(File file, String version) {
        setFile(TARGET_TARGET_FILE_KEY_NAME, file, version);
    }
    public File getBaselineTargetFileZip() {
        return getFile(BASELINE_TARGET_FILE_KEY_NAME);
    }
    public File getTargetTargetFileZip() {
        return getFile(TARGET_TARGET_FILE_KEY_NAME);
    }

    public void setBaselineOtatoolsZip(File file, String version) {
        setFile(BASELINE_OTA_TOOLS_KEY_NAME, file, version);
    }
    public void setTargetOtatoolsZip(File file, String version) {
        setFile(TARGET_OTA_TOOLS_KEY_NAME, file, version);
    }
    public File getBaselineOtatoolsZip() {
        return getFile(BASELINE_OTA_TOOLS_KEY_NAME);
    }
    public File getTargetOtatoolsZip() {
        return getFile(TARGET_OTA_TOOLS_KEY_NAME);
    }
}
