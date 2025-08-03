/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.wm.other;

import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.UiDeviceUtils.pressBackButton;
import static android.server.wm.deprecatedabi.Components.WARNING_DIALOG_ACTIVITY;

import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;


/**
 * Ensure that compatibility dialog is shown when launching an application
 * targeting a deprecated ABI.
 * <p>Build/Install/Run:
 *     atest CtsWindowManagerDeviceActivity:DeprecatedAbiTest
 */
@Presubmit
@ApiTest(apis = {"android.content.pm.PackageInstaller#STATUS_FAILURE_INCOMPATIBLE"})
public class DeprecatedAbiTest extends ActivityManagerTestBase {

    /** @see com.android.server.wm.DeprecatedAbiDialog */
    private static final String DEPRECATED_ABI_DIALOG =
            "DeprecatedAbiDialog";
    private static final String TEST_APK_PATH = "/data/local/tmp/cts/CtsDeviceDeprecatedAbiApp.apk";
    private static final String TEST_PACKAGE_NAME = "android.server.wm.deprecatedabi";

    @Before
    public void setUp() {
        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP");
        SystemUtil.runShellCommand("wm dismiss-keyguard");
        SystemUtil.runShellCommand("setprop debug.wm.disable_deprecated_abi_dialog 0");
    }

    @After
    public void tearDown() {
        // Ensure app process is stopped.
        stopTestPackage(WARNING_DIALOG_ACTIVITY.getPackageName());
        executeShellCommand("pm uninstall " + TEST_PACKAGE_NAME);
        // Continue to disable DeprecatedAbi dialog so other tests can pass
        SystemUtil.runShellCommand("setprop debug.wm.disable_deprecated_abi_dialog 1");
    }

    @Test
    public void testWarningDialog() throws Exception {
        // Skip the test if the device only supports 32-bit ABI
        List<String> deviceAbis = Arrays.asList(
                SystemProperties.get("ro.product.cpu.abilist").split(","));
        assumeTrue(deviceAbis.stream().anyMatch(s->s.contains("64")));
        executeShellCommand("pm install " + TEST_APK_PATH);
        // If app fails to install, the device does not support 32-bit ABI. Skip.
        assumeTrue(isPackageInstalled(TEST_PACKAGE_NAME));

        // Launch target app.
        launchActivity(WARNING_DIALOG_ACTIVITY);
        mWmState.assertActivityDisplayed(WARNING_DIALOG_ACTIVITY);
        mWmState.assertWindowDisplayed(DEPRECATED_ABI_DIALOG);

        // Go back to dismiss the warning dialog.
        pressBackButton();

        // Go back again to formally stop the app. If we just kill the process, it'll attempt to
        // resume rather than starting from scratch (as far as ActivityStack is concerned) and it
        // won't invoke the warning dialog.
        pressBackButton();
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            PackageInfo pi = mContext.getPackageManager().getPackageInfo(packageName, 0);
            return pi != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
