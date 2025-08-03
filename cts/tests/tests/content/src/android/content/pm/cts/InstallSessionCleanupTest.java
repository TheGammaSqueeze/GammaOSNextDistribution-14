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

package android.content.pm.cts;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.cts.util.AbandonAllPackageSessionsRule;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;

import com.android.compatibility.common.util.UiAutomatorUtils2;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull
@AppModeNonSdkSandbox
public class InstallSessionCleanupTest {
    private static final String INSTALLER_APP_PACKAGE_NAME = "com.android.cts.testinstallerapp";
    private static final TestApp INSTALLER_APP =
            new TestApp("TestInstallerApp", INSTALLER_APP_PACKAGE_NAME, 30,
                    false, "TestInstallerApp.apk");
    private static final int NUM_NEW_SESSIONS = 10;

    private final PackageManager mPackageManager = InstrumentationRegistry
            .getInstrumentation().getContext().getPackageManager();
    private final PackageInstaller mPackageInstaller = mPackageManager.getPackageInstaller();

    private final CompletableFuture<Boolean> mSessionsCleared = new CompletableFuture<>();
    private boolean mCheckSessions = false;
    private final Thread mCheckSessionsThread = new Thread(() -> {
        try {
            while (mCheckSessions) {
                if (getNumSessions(INSTALLER_APP_PACKAGE_NAME) == 0) {
                    mSessionsCleared.complete(true);
                    break;
                }
                Thread.sleep(50 /* mills */);
            }
        } catch (InterruptedException ignored) {
        }
    });

    @Rule
    public AbandonAllPackageSessionsRule mAbandonSessionsRule = new AbandonAllPackageSessionsRule();

    @After
    public void tearDown() throws InterruptedException {
        adoptShellPermissions();
        Uninstall.packages(INSTALLER_APP_PACKAGE_NAME);
        dropShellPermissions();
        mCheckSessions = false;
        mCheckSessionsThread.interrupt();
    }

    @Test
    public void testSessionsDeletedOnInstallerUninstalled() throws Exception {
        adoptShellPermissions();
        Install.single(INSTALLER_APP).commit();
        assertThat(getNumSessions(INSTALLER_APP_PACKAGE_NAME)).isEqualTo(0);
        final Intent intent = mPackageManager.getLaunchIntentForPackage(INSTALLER_APP_PACKAGE_NAME);
        intent.putExtra("numSessions", NUM_NEW_SESSIONS);

        InstrumentationRegistry
                .getInstrumentation().getContext().startActivity(intent);
        UiAutomatorUtils2.waitFindObject(By.pkg(INSTALLER_APP_PACKAGE_NAME).depth(0));
        assertThat(getNumSessions(INSTALLER_APP_PACKAGE_NAME)).isAtLeast(NUM_NEW_SESSIONS);
        Uninstall.packages(INSTALLER_APP_PACKAGE_NAME);
        // Due to the asynchronous nature of abandoning sessions, sessions don't get deleted
        // immediately after they are abandoned. Briefly wait until all sessions are cleared.
        mCheckSessions = true;
        mCheckSessionsThread.start();
        assertThat(mSessionsCleared.get(1, TimeUnit.SECONDS)).isTrue();
        dropShellPermissions();
    }

    private int getNumSessions(String installerPackageName) {
        final List<PackageInstaller.SessionInfo> allSessions = mPackageInstaller.getAllSessions();
        List<Integer> result = new ArrayList<>();
        for (PackageInstaller.SessionInfo sessionInfo : allSessions) {
            if (sessionInfo.installerPackageName != null
                        && sessionInfo.installerPackageName.equals(installerPackageName)) {
                result.add(sessionInfo.sessionId);
            }
        }
        return result.size();
    }

    private static void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES, Manifest.permission.DELETE_PACKAGES,
                        Manifest.permission.QUERY_ALL_PACKAGES,
                        Manifest.permission.USE_SYSTEM_DATA_LOADERS);
    }

    private static void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

}
