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

package android.os.lib.app;

import static android.Manifest.permission.INSTALL_PACKAGES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.RequireRunOnAdditionalUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * On-device tests driven by StaticSharedLibsHostTests. Specifically tests multi user scenarios.
 */
@RunWith(BedsteadJUnit4.class)
public class StaticSharedLibsMultiUserTests {

    private static final String APK_BASE_PATH = "/data/local/tmp/cts/hostside/os/";
    private static final String STATIC_LIB_PROVIDER3_APK = APK_BASE_PATH
            + "CtsStaticSharedLibProviderApp3.apk";
    private static final String STATIC_LIB_PROVIDER3_PKG = "android.os.lib.provider";

    private static final long TIMEOUT_MS = 10000L;

    private UserReference mInitialUser;
    private UserReference mAdditionalUser;

    Context mContextInitial;
    Context mContextAdditional;

    @Rule
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Before
    public void setUp() throws Exception {
        mInitialUser = sDeviceState.initialUser();
        mAdditionalUser = sDeviceState.additionalUser();

        mContextInitial = TestApis.context().androidContextAsUser(mInitialUser);
        mContextAdditional = TestApis.context().androidContextAsUser(mAdditionalUser);
    }

    private boolean installPackageAsUser(String apkPath, String installerName, UserReference user) {
        ShellCommand.Builder cmd = ShellCommand.builderForUser(user, "pm install");
        if (installerName != null) {
            cmd.addOption("i", installerName);
        }
        cmd.addOperand(apkPath);
        try {
            return ShellCommandUtils.startsWithSuccess(cmd.execute());
        } catch (AdbException e) {
            return false;
        }
    }

    private boolean uninstallPackage(String packageName) {
        ShellCommand.Builder cmd = ShellCommand.builder("pm uninstall");
        cmd.addOperand(packageName);
        try {
            return ShellCommandUtils.startsWithSuccess(cmd.execute());
        } catch (AdbException e) {
            return false;
        }
    }

    @Test
    @RequireRunOnAdditionalUser
    @EnsureHasPermission(INSTALL_PACKAGES)
    public void testStaticSharedLibInstallOnSecondaryUser_broadcastReceivedByAllUsers() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        BlockingBroadcastReceiver initialReceiver =
                sDeviceState.registerBroadcastReceiverForUser(mInitialUser, filter);
        BlockingBroadcastReceiver additionalReceiver =
                sDeviceState.registerBroadcastReceiverForUser(mAdditionalUser, filter);

        assertThat(installPackageAsUser(STATIC_LIB_PROVIDER3_APK,
                mContextAdditional.getPackageName(), mAdditionalUser)).isTrue();

        Intent intent = initialReceiver.awaitForBroadcast(TIMEOUT_MS);
        assertWithMessage("Initial user should get the broadcast.")
                .that(intent).isNotNull();
        assertWithMessage("Incorrect broadcast action in initial user")
                .that(intent.getAction()).isEqualTo(Intent.ACTION_PACKAGE_ADDED);

        intent = additionalReceiver.awaitForBroadcast(TIMEOUT_MS);
        assertWithMessage("Additional user should get the broadcast.")
                .that(intent).isNotNull();
        assertWithMessage("Incorrect broadcast action in additional user")
                .that(intent.getAction()).isEqualTo(Intent.ACTION_PACKAGE_ADDED);

        initialReceiver.unregisterQuietly();
        additionalReceiver.unregisterQuietly();
        uninstallPackage(STATIC_LIB_PROVIDER3_PKG);
    }

    @Test
    @RequireRunOnAdditionalUser
    @EnsureHasPermission(INSTALL_PACKAGES)
    public void testStaticSharedLibUninstallOnAllUsers_broadcastReceivedByAllUsers() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        BlockingBroadcastReceiver initialReceiver =
                sDeviceState.registerBroadcastReceiverForUser(mInitialUser, filter);
        BlockingBroadcastReceiver additionalReceiver =
                sDeviceState.registerBroadcastReceiverForUser(mAdditionalUser, filter);

        assertThat(installPackageAsUser(STATIC_LIB_PROVIDER3_APK,
                mContextAdditional.getPackageName(), mAdditionalUser)).isTrue();
        assertThat(uninstallPackage(STATIC_LIB_PROVIDER3_PKG)).isTrue();

        Intent intent = initialReceiver.awaitForBroadcast(TIMEOUT_MS);
        assertWithMessage("Initial user should get the broadcast")
                .that(intent).isNotNull();
        assertWithMessage("Incorrect broadcast action in initial user")
                .that(intent.getAction()).isEqualTo(Intent.ACTION_PACKAGE_REMOVED);

        intent = additionalReceiver.awaitForBroadcast(TIMEOUT_MS);
        assertWithMessage("Additional user should get the broadcast")
                .that(intent).isNotNull();
        assertWithMessage("Incorrect broadcast action in additional user")
                .that(intent.getAction()).isEqualTo(Intent.ACTION_PACKAGE_REMOVED);

        initialReceiver.unregisterQuietly();
        additionalReceiver.unregisterQuietly();
    }
}
