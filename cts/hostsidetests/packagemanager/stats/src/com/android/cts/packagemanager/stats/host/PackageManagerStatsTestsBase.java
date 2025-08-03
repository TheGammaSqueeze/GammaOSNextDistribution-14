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

package com.android.cts.packagemanager.stats.host;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackageManagerStatsTestsBase extends DeviceTestCase implements IBuildReceiver {

    protected static final String TEST_REMOTE_DIR = "/data/local/tmp/statsdatom";
    protected IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        getDevice().deleteFile(TEST_REMOTE_DIR);
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    protected void installPackageUsingIncremental(String[] apkNames)
            throws Exception {
        getDevice().executeShellCommand("mkdir -p " + TEST_REMOTE_DIR);
        String[] remoteApkPaths = new String[apkNames.length];
        for (int i = 0; i < remoteApkPaths.length; i++) {
            remoteApkPaths[i] = Utils.pushApkToRemote(
                    apkNames[i], TEST_REMOTE_DIR, mCtsBuild, getDevice());
        }
        String installResult = getDevice().executeShellCommand(
                "pm install-incremental -t -g " + "--user " + getDevice().getCurrentUser() + " "
                        + String.join(" ", remoteApkPaths));
        assertEquals("Success\n", installResult);
    }

    protected int getAppUid(String pkgName) throws Exception {
        final int currentUser = getDevice().getCurrentUser();
        final String uidLine = getDevice().executeShellCommand(
                "cmd package list packages --match-libraries -U --user " + currentUser + " "
                        + pkgName);
        final Pattern pattern = Pattern.compile("package:" + pkgName + " uid:(\\d+)");
        final Matcher matcher = pattern.matcher(uidLine);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new IllegalStateException("Package " + pkgName + " is not installed for user "
                + currentUser);
    }

    protected long getTestFileSize(String fileName) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        final File file = buildHelper.getTestFile(fileName);
        return file.length();
    }

    protected boolean isLibraryInstalled(String libraryName) throws Exception {
        final String uidLine = getDevice().executeShellCommand("cmd package list libraries");
        final Pattern pattern = Pattern.compile("library:" + libraryName);
        final Matcher matcher = pattern.matcher(uidLine);
        return matcher.find();
    }
}
