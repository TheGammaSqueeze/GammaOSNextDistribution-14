/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.incrementalinstall.inrementaltestappvalidation;

import static android.incrementalinstall.common.Consts.INCREMENTAL_TEST_APP_STATUS_RECEIVER_ACTION;
import static android.incrementalinstall.common.Consts.INSTALLED_VERSION_CODE_TAG;
import static android.incrementalinstall.common.Consts.IS_INCFS_INSTALLATION_TAG;
import static android.incrementalinstall.common.Consts.LOADED_COMPONENTS_TAG;
import static android.incrementalinstall.common.Consts.NOT_LOADED_COMPONENTS_TAG;
import static android.incrementalinstall.common.Consts.PACKAGE_TO_LAUNCH_TAG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.content.IntentFilter;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;
import android.system.Os;
import android.system.StructStat;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public class AppValidationTest {

    private Context mContext;
    private String mPackageToLaunch;
    private UiDevice mDevice;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPackageToLaunch = InstrumentationRegistry.getArguments().getString(PACKAGE_TO_LAUNCH_TAG);
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    public void testAppComponentsInvoked() throws Exception {
        String[] loadedComponents = InstrumentationRegistry.getArguments()
                .getString(LOADED_COMPONENTS_TAG).split(",");
        String[] notLoadedComponents = InstrumentationRegistry.getArguments()
                .getString(NOT_LOADED_COMPONENTS_TAG).split(",");
        StatusReceiver statusReceiver = new StatusReceiver();
        IntentFilter intentFilter = new IntentFilter(INCREMENTAL_TEST_APP_STATUS_RECEIVER_ACTION);
        mContext.registerReceiver(statusReceiver, intentFilter,
                Context.RECEIVER_EXPORTED_UNAUDITED);
        launchTestApp();
        for (String component : loadedComponents) {
            assertEquals(
                    String.format("Component :%s was not loaded, when it should have.", component),
                    "true", statusReceiver.verifyComponentInvoked(component));
        }
        for (String component : notLoadedComponents) {
            assertNotEquals(
                    String.format("Component :%s was loaded, when it shouldn't have.", component),
                    "true", statusReceiver.verifyComponentInvoked(component));
        }
        mContext.unregisterReceiver(statusReceiver);
    }

    @Test
    public void testInstallationTypeAndVersion() throws Exception {
        boolean isIncfsInstallation = Boolean.parseBoolean(InstrumentationRegistry.getArguments()
                .getString(IS_INCFS_INSTALLATION_TAG));
        int versionCode = Integer.parseInt(InstrumentationRegistry.getArguments()
                .getString(INSTALLED_VERSION_CODE_TAG));
        InstalledAppInfo installedAppInfo = getInstalledAppInfo();
        assertEquals(isIncfsInstallation,
                new PathChecker().isIncFsPath(installedAppInfo.installationPath));

        StructStat st =
                Os.stat(Paths.get(installedAppInfo.installationPath).getParent().toString());
        assertEquals("App parent directory may not be world-readable", 0771, st.st_mode & 0777);
        assertEquals(versionCode, installedAppInfo.versionCode);
        // Read the whole file to make sure it's streamed.
        readFullFile(new File(installedAppInfo.installationPath, "base.apk"));
    }

    private static void readFullFile(File file) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            while (inputStream.read(buffer) != -1) {
                // ignore
            }
        }
    }

    private void launchTestApp() throws Exception {
        mDevice.executeShellCommand(String.format("am start %s/.MainActivity", mPackageToLaunch));
    }

    private InstalledAppInfo getInstalledAppInfo() throws Exception {
        // Output of the command is package:<path>/apk=<package_name> versionCode:<version>, we
        // just need the <path> and <version>.
        String output = mDevice.executeShellCommand(
                "pm list packages -f --show-versioncode " + mPackageToLaunch);
        // outputSplits[0] will contain path information and outputSplits[1] will contain
        // versionCode.
        String[] outputSplits = output.split(" ");
        String installationPath = outputSplits[0].trim().substring("package:".length(),
                output.lastIndexOf("/"));
        int versionCode = Integer.parseInt(
                outputSplits[1].trim().substring("versionCode:".length()));
        return new InstalledAppInfo(installationPath, versionCode);
    }

    private class InstalledAppInfo {

        private final String installationPath;
        private final int versionCode;

        InstalledAppInfo(String installedPath, int versionCode) {
            this.installationPath = installedPath;
            this.versionCode = versionCode;
        }
    }
}
