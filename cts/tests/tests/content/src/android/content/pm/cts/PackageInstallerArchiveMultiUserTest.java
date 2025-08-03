/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.content.pm.cts;

import static android.content.pm.PackageManager.MATCH_ARCHIVED_PACKAGES;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@AppModeFull
@AppModeNonSdkSandbox
@EnsureHasSecondaryUser
@RunWith(AndroidJUnit4.class)
public class PackageInstallerArchiveMultiUserTest {
    private static final String PACKAGE_NAME = "android.content.cts.mocklauncherapp";
    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String APK_PATH = SAMPLE_APK_BASE + "CtsContentMockLauncherTestApp.apk";

    private static final int TIMEOUT_SECONDS = 5;
    private static final int TIMEOUT_MILLISECONDS = 10;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private Context mContext;
    private ArchiveIntentSender mArchiveIntentSender;
    private UserReference mPrimaryUser;
    private UserReference mSecondaryUser;

    @Before
    public void setup() throws Exception {
        assumeTrue("Form factor is not supported", isFormFactorSupported());
        mPrimaryUser = sDeviceState.initialUser();
        mSecondaryUser = sDeviceState.secondaryUser();
        assumeTrue(UserManager.supportsMultipleUsers());
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mArchiveIntentSender = new ArchiveIntentSender();
    }

    @After
    public void uninstall() {
        uninstallPackage(PACKAGE_NAME);
    }

    @Test
    public void archiveApp_onlyForOneUser_multiuser() throws PackageManager.NameNotFoundException {
        installExistingPackageAsUser(mContext.getPackageName(), mSecondaryUser);
        installPackage(APK_PATH);
        Context contextPrimaryUser = mContext.createContextAsUser(mPrimaryUser.userHandle(), 0);
        Context contextSecondaryUser = mContext.createContextAsUser(mSecondaryUser.userHandle(), 0);
        assertTrue(isAppInstalledForUser(PACKAGE_NAME, mPrimaryUser));
        assertTrue(isAppInstalledForUser(PACKAGE_NAME, mSecondaryUser));

        runWithShellPermissionIdentity(
                () -> {
                    contextPrimaryUser.getPackageManager().getPackageInstaller().requestArchive(
                            PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mPackage.get(TIMEOUT_SECONDS,
                            TimeUnit.SECONDS)).isEqualTo(
                            PACKAGE_NAME);
                    assertThat(
                            mArchiveIntentSender.mStatus.get(TIMEOUT_MILLISECONDS,
                                    TimeUnit.MILLISECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        assertTrue(Objects.requireNonNull(
                contextPrimaryUser.getPackageManager().getPackageInfo(PACKAGE_NAME,
                        PackageManager.PackageInfoFlags.of(
                                MATCH_ARCHIVED_PACKAGES)).applicationInfo).isArchived);
        assertFalse(Objects.requireNonNull(
                contextSecondaryUser.getPackageManager().getPackageInfo(PACKAGE_NAME,
                        PackageManager.PackageInfoFlags.of(
                                MATCH_ARCHIVED_PACKAGES)).applicationInfo).isArchived);
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(
                String.format("pm uninstall %s", packageName));
    }

    private void installPackage(String path) {
        assertEquals("Success\n", SystemUtil.runShellCommand(
                String.format("pm install -r -i %s -t -g %s", mContext.getPackageName(),
                        path)));
    }

    private void installExistingPackageAsUser(String packageName, UserReference user) {
        int userId = user.id();
        assertThat(SystemUtil.runShellCommand(
                String.format("pm install-existing --user %s %s", userId, packageName)))
                .isEqualTo(
                        String.format("Package %s installed for user: %s\n", packageName, userId));
    }

    private boolean isAppInstalledForUser(String packageName, UserReference user) {
        return Arrays.stream(SystemUtil.runShellCommand(
                        String.format("pm list packages --user %s %s", user.id(), packageName))
                .split("\\r?\\n"))
                .anyMatch(pkg -> pkg.equals(String.format("package:%s", packageName)));
    }

    private static boolean isFormFactorSupported() {
        return !FeatureUtil.isArc()
                && !FeatureUtil.isAutomotive()
                && !FeatureUtil.isTV()
                && !FeatureUtil.isWatch()
                && !FeatureUtil.isVrHeadset();
    }

    static class ArchiveIntentSender extends IIntentSender.Stub {

        final CompletableFuture<String> mPackage = new CompletableFuture<>();
        final CompletableFuture<Integer> mStatus = new CompletableFuture<>();
        final CompletableFuture<String> mMessage = new CompletableFuture<>();
        final CompletableFuture<Intent> mExtraIntent = new CompletableFuture<>();

        @Override
        public void send(int code, Intent intent, String resolvedType,
                IBinder whitelistToken, IIntentReceiver finishedReceiver,
                String requiredPermission, Bundle options) throws RemoteException {
            mPackage.complete(intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME));
            mStatus.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -100));
            mMessage.complete(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            mExtraIntent.complete(intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class));
        }
    }
}
