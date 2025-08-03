/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.MATCH_ARCHIVED_PACKAGES;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.getDefaultLauncher;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.Flags;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ArchiveCompatibilityParams;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.UnarchivalState;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ApplicationInfoFlags;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.pm.cts.PackageManagerShellCommandInstallTest.PackageBroadcastReceiver;
import android.content.pm.cts.util.AbandonAllPackageSessionsRule;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@AppModeFull
@AppModeNonSdkSandbox
@RunWith(AndroidJUnit4.class)
public class PackageInstallerArchiveTest {
    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String PACKAGE_NAME = "android.content.cts.mocklauncherapp";

    private static final String NO_ACTIVITY_PACKAGE_NAME =
            "android.content.cts.IntentResolutionTest";
    private static final String ACTIVITY_NAME = PACKAGE_NAME + ".Launcher";
    private static final String APK_PATH = SAMPLE_APK_BASE + "CtsContentMockLauncherTestApp.apk";

    private static final String NO_ACTIVITY_APK_PATH =
            SAMPLE_APK_BASE + "CtsIntentResolutionTestApp.apk";

    private static final String ACTION_UNARCHIVE_ERROR_DIALOG =
            "com.android.intent.action.UNARCHIVE_ERROR_DIALOG";


    private static CompletableFuture<Integer> sUnarchiveId;
    private static CompletableFuture<String> sUnarchiveReceiverPackageName;
    private static CompletableFuture<Boolean> sUnarchiveReceiverAllUsers;

    @Rule
    public final Expect expect = Expect.create();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AbandonAllPackageSessionsRule mAbandonSessionsRule = new AbandonAllPackageSessionsRule();

    private Context mContext;
    private Instrumentation mInstrumentation;
    private UiDevice mUiDevice;
    private PackageManager mPackageManager;
    private PackageInstaller mPackageInstaller;
    private StorageStatsManager mStorageStatsManager;
    private ArchiveIntentSender mArchiveIntentSender;
    private UnarchiveIntentSender mUnarchiveIntentSender;
    private AppOpsManager mAppOpsManager;
    private String mDefaultHome;

    @Before
    public void setup() throws Exception {
        assumeTrue("Form factor is not supported", isFormFactorSupported());
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiDevice = UiDevice.getInstance(
                androidx.test.InstrumentationRegistry.getInstrumentation());
        mContext = mInstrumentation.getContext();
        mPackageManager = mContext.getPackageManager();
        mPackageInstaller = mPackageManager.getPackageInstaller();
        mStorageStatsManager = mContext.getSystemService(StorageStatsManager.class);
        mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        mArchiveIntentSender = new ArchiveIntentSender();
        mUnarchiveIntentSender = new UnarchiveIntentSender();
        sUnarchiveId = new CompletableFuture<>();
        sUnarchiveReceiverPackageName = new CompletableFuture<>();
        sUnarchiveReceiverAllUsers = new CompletableFuture<>();
        mContext.getPackageManager().setComponentEnabledSetting(
                new ComponentName(mContext, UnarchiveBroadcastReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
        mDefaultHome = getDefaultLauncher(mInstrumentation);
    }

    @After
    public void uninstall() {
        uninstallPackage(PACKAGE_NAME);
        if (mDefaultHome != null) {
            setDefaultLauncher(mInstrumentation, mDefaultHome);
        }
    }

    @Test
    public void archiveApp_dataIsKept() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);
        // This creates a data directory which will be verified later.
        launchTestActivity();
        PackageInfo packageInfo = getPackageInfo();

        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mPackage.get(5, TimeUnit.SECONDS)).isEqualTo(
                            PACKAGE_NAME);
                    assertThat(
                            mArchiveIntentSender.mStatus.get(10, TimeUnit.MILLISECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);
        StorageStats stats =
                runWithShellPermissionIdentity(() ->
                                mStorageStatsManager.queryStatsForPackage(
                                        packageInfo.applicationInfo.storageUuid,
                                        packageInfo.packageName,
                                        UserHandle.of(UserHandle.myUserId())),
                        Manifest.permission.PACKAGE_USAGE_STATS);
        // This number is bound to fluctuate as the data created during app startup will change
        // over time. We only need to verify that the data directory is kept.
        assertTrue(stats.getDataBytes() > 0L);
    }

    @Test
    public void archiveApp_getApplicationIcon() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);

        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get(5, TimeUnit.SECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);
        ApplicationInfo applicationInfo = mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES)).applicationInfo;
        Drawable actualIcon = mPackageManager.getApplicationIcon(applicationInfo);
        Bitmap actualBitmap = drawableToBitmap(actualIcon);
        assertThat(actualBitmap).isNotNull();

        recycleBitmap(actualIcon, actualBitmap);
    }

    @Test
    public void archiveApp_getApplicationIcon_compatOptionsOn() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);

        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get(5, TimeUnit.SECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);
        ApplicationInfo applicationInfo = mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES)).applicationInfo;
        Drawable overlaidIcon = mPackageManager.getApplicationIcon(applicationInfo);
        Bitmap overlaidBitmap = drawableToBitmap(overlaidIcon);

        ArchiveCompatibilityParams options = new ArchiveCompatibilityParams();
        options.setEnableIconOverlay(false);
        mContext.getSystemService(LauncherApps.class).setArchiveCompatibility(options);
        Drawable rawIcon = mPackageManager.getApplicationIcon(applicationInfo);
        Bitmap rawBitmap = drawableToBitmap(rawIcon);

        assertThat(overlaidBitmap.sameAs(rawBitmap)).isFalse();

        recycleBitmap(overlaidIcon, overlaidBitmap);
        recycleBitmap(rawIcon, rawBitmap);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void archiveApp_uninstallationWorksCorrectly() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);

        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get(5, TimeUnit.SECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);
        uninstallPackage(PACKAGE_NAME);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getPackageInfo(PACKAGE_NAME,
                        PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES)));
    }

    @Test
    public void archiveApp_noInstaller() throws NameNotFoundException {
        installPackageWithNoInstaller(PACKAGE_NAME, APK_PATH);

        PackageManager.NameNotFoundException e =
                runWithShellPermissionIdentity(
                        () -> assertThrows(
                                PackageManager.NameNotFoundException.class,
                                () -> mPackageInstaller.requestArchive(PACKAGE_NAME,
                                        new IntentSender((IIntentSender) mArchiveIntentSender))),
                        Manifest.permission.DELETE_PACKAGES);

        assertThat(e).hasMessageThat().isEqualTo("No installer found");
    }

    @Test
    public void archiveApp_installerDoesntSupportUnarchival() throws NameNotFoundException {
        installPackage(PACKAGE_NAME, APK_PATH);
        mContext.getPackageManager().setComponentEnabledSetting(
                new ComponentName(mContext, UnarchiveBroadcastReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        PackageManager.NameNotFoundException e =
                runWithShellPermissionIdentity(
                        () -> assertThrows(
                                PackageManager.NameNotFoundException.class,
                                () -> mPackageInstaller.requestArchive(PACKAGE_NAME,
                                        new IntentSender((IIntentSender) mArchiveIntentSender))),
                        Manifest.permission.DELETE_PACKAGES);

        assertThat(e).hasMessageThat().isEqualTo("Installer does not support unarchival");
    }

    @Test
    public void archiveApp_systemApp() throws NameNotFoundException {
        PackageManager.NameNotFoundException e =
                runWithShellPermissionIdentity(
                        () -> assertThrows(
                                PackageManager.NameNotFoundException.class,
                                () -> mPackageInstaller.requestArchive("android",
                                        new IntentSender((IIntentSender) mArchiveIntentSender))),
                        Manifest.permission.DELETE_PACKAGES);

        assertThat(e).hasMessageThat().isEqualTo("System apps cannot be archived.");
    }

    @Test
    public void matchArchivedPackages() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);

        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get(5, TimeUnit.SECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        assertThat(mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageInfoFlags.of(
                        MATCH_UNINSTALLED_PACKAGES)).applicationInfo.isArchived).isTrue();
        assertThat(mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES)).applicationInfo.isArchived).isTrue();
        assertThat(mPackageManager.getApplicationInfo(PACKAGE_NAME,
                ApplicationInfoFlags.of(
                        MATCH_UNINSTALLED_PACKAGES)).isArchived).isTrue();
        assertThat(mPackageManager.getApplicationInfo(PACKAGE_NAME,
                ApplicationInfoFlags.of(MATCH_ARCHIVED_PACKAGES)).isArchived).isTrue();
        // Ensure fully installed app are returned too.
        assertThat(mPackageManager.getInstalledPackages(
                PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES)).size()).isAtLeast(2);
        assertThrows(NameNotFoundException.class,
                () -> mPackageManager.getPackageInfo(PACKAGE_NAME, /* flags= */ 0));
        assertThrows(NameNotFoundException.class,
                () -> mPackageManager.getApplicationInfo(PACKAGE_NAME, /* flags= */ 0));

        assertThat(
                mPackageManager.getInstalledApplications(
                                ApplicationInfoFlags.of(MATCH_ARCHIVED_PACKAGES))
                        .stream()
                        .anyMatch(
                                applicationInfo ->
                                        applicationInfo.packageName.equals(PACKAGE_NAME)
                                                && applicationInfo.isArchived))
                .isTrue();
    }

    @Test
    public void archiveApp_missingPermissions() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);

        SecurityException e =
                assertThrows(
                        SecurityException.class,
                        () -> mPackageInstaller.requestArchive(PACKAGE_NAME,
                                new IntentSender((IIntentSender) mArchiveIntentSender)));

        assertThat(e).hasMessageThat().isEqualTo("You need the "
                + "com.android.permission.DELETE_PACKAGES or "
                + "com.android.permission.REQUEST_DELETE_PACKAGES permission to request an "
                + "archival."
        );
    }

    @Test
    public void archiveApp_twiceFails() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);

        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get(5, TimeUnit.SECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        PackageManager.NameNotFoundException e =
                runWithShellPermissionIdentity(
                        () -> assertThrows(
                                PackageManager.NameNotFoundException.class,
                                () -> mPackageInstaller.requestArchive(PACKAGE_NAME,
                                        new IntentSender((IIntentSender) mArchiveIntentSender))),
                        Manifest.permission.DELETE_PACKAGES);
        assertThat(e).hasMessageThat().isEqualTo(PACKAGE_NAME + " is not installed.");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void archiveApp_getArchiveTimeMillis() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);
        final long timestampBeforeArchive = System.currentTimeMillis();
        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        final long timestampAfterArchive = System.currentTimeMillis();

        // Test that the archiveTimeMillis field is valid
        PackageInfo pi = mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES));
        assertThat(pi).isNotNull();
        assertThat(pi.getArchiveTimeMillis()).isGreaterThan(timestampBeforeArchive);
        assertThat(pi.getArchiveTimeMillis()).isLessThan(timestampAfterArchive);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void archiveApp_archiveStateClearedAfterUpdate() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);
        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        // Test that the archiveTimeMillis field is valid
        PackageInfo pi = mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES));
        assertThat(pi).isNotNull();
        assertThat(pi.getArchiveTimeMillis()).isGreaterThan(0);
        assertThat(pi.applicationInfo.isArchived).isTrue();

        // reinstall the app
        installPackage(PACKAGE_NAME, APK_PATH);
        pi = mPackageManager.getPackageInfo(PACKAGE_NAME, PackageInfoFlags.of(0));
        assertThat(pi).isNotNull();
        assertThat(pi.getArchiveTimeMillis()).isEqualTo(0);
        assertThat(pi.applicationInfo.isArchived).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void unarchiveApp() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);
        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get(5, TimeUnit.SECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        SessionListener sessionListener = new SessionListener();
        mPackageInstaller.registerSessionCallback(sessionListener,
                new Handler(Looper.getMainLooper()));

        runWithShellPermissionIdentity(
                () -> mPackageInstaller.requestUnarchive(PACKAGE_NAME,
                        new IntentSender((IIntentSender) mUnarchiveIntentSender)),
                Manifest.permission.INSTALL_PACKAGES);
        assertThat(sUnarchiveReceiverPackageName.get(5, TimeUnit.SECONDS)).isEqualTo(PACKAGE_NAME);
        assertThat(sUnarchiveReceiverAllUsers.get(10, TimeUnit.MILLISECONDS)).isFalse();
        int unarchiveId = sUnarchiveId.get(10, TimeUnit.MILLISECONDS);

        int draftSessionId = sessionListener.mSessionIdCreated.get(5, TimeUnit.SECONDS);
        PackageInstaller.SessionInfo sessionInfo = mPackageInstaller.getSessionInfo(
                draftSessionId);
        assertThat(unarchiveId).isEqualTo(draftSessionId);
        assertThat(sessionInfo.appPackageName).isEqualTo(PACKAGE_NAME);
        assertThat(sessionInfo.installFlags & PackageManager.INSTALL_UNARCHIVE_DRAFT).isNotEqualTo(
                0);
        assertThat(sessionInfo.isUnarchival()).isTrue();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setUnarchiveId(unarchiveId);
        params.appPackageName = PACKAGE_NAME;
        int sessionId = mPackageInstaller.createSession(params);
        assertThat(unarchiveId).isEqualTo(sessionId);
        sessionInfo = mPackageInstaller.getSessionInfo(sessionId);
        assertThat(sessionInfo.installFlags & PackageManager.INSTALL_UNARCHIVE).isNotEqualTo(0);
        assertThat(sessionInfo.isUnarchival()).isTrue();
        mPackageInstaller.abandonSession(sessionId);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void unarchiveApp_repeatedCallsDeduplicated() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);
        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get(5, TimeUnit.SECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        runWithShellPermissionIdentity(
                () -> mPackageInstaller.requestUnarchive(PACKAGE_NAME,
                        new IntentSender((IIntentSender) mUnarchiveIntentSender)),
                Manifest.permission.INSTALL_PACKAGES);
        int unarchiveId1 = sUnarchiveId.get(5, TimeUnit.SECONDS);

        sUnarchiveId = new CompletableFuture<>();
        sUnarchiveReceiverPackageName = new CompletableFuture<>();
        sUnarchiveReceiverAllUsers = new CompletableFuture<>();

        runWithShellPermissionIdentity(
                () -> mPackageInstaller.requestUnarchive(PACKAGE_NAME,
                        new IntentSender((IIntentSender) mUnarchiveIntentSender)),
                Manifest.permission.INSTALL_PACKAGES);
        int unarchiveId2 = sUnarchiveId.get(5, TimeUnit.SECONDS);

        assertThat(unarchiveId1).isEqualTo(unarchiveId2);

        mPackageInstaller.abandonSession(unarchiveId1);
    }

    @Test
    public void unarchiveApp_missingPermissions() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);
        assertThat(
                SystemUtil.runShellCommand(String.format("pm archive %s", PACKAGE_NAME))).isEqualTo(
                "Success\n");

        SecurityException e =
                assertThrows(
                        SecurityException.class,
                        () -> mPackageInstaller.requestUnarchive(PACKAGE_NAME,
                                new IntentSender((IIntentSender) mUnarchiveIntentSender)));

        assertThat(e).hasMessageThat().isEqualTo("You need the "
                + "com.android.permission.INSTALL_PACKAGES or "
                + "com.android.permission.REQUEST_INSTALL_PACKAGES permission to request an "
                + "unarchival."
        );
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void reportUnarchivalState_success() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);
        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get(5, TimeUnit.SECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        SessionListener sessionListener = new SessionListener();
        mPackageInstaller.registerSessionCallback(sessionListener,
                new Handler(Looper.getMainLooper()));

        runWithShellPermissionIdentity(
                () -> mPackageInstaller.requestUnarchive(PACKAGE_NAME,
                        new IntentSender((IIntentSender) mUnarchiveIntentSender)),
                Manifest.permission.INSTALL_PACKAGES);
        int unarchiveId = sUnarchiveId.get(5, TimeUnit.SECONDS);

        runWithShellPermissionIdentity(
                () -> mPackageInstaller.reportUnarchivalState(
                        UnarchivalState.createOkState(unarchiveId)),
                Manifest.permission.INSTALL_PACKAGES);
        assertThat(mUnarchiveIntentSender.mPackage.get(5, TimeUnit.SECONDS)).isEqualTo(
                PACKAGE_NAME);
        assertThat(mUnarchiveIntentSender.mStatus.get(10, TimeUnit.MILLISECONDS)).isEqualTo(
                PackageInstaller.UNARCHIVAL_OK);
        assertThat(mUnarchiveIntentSender.mIntent.get(10, TimeUnit.MILLISECONDS)).isNull();

        mPackageInstaller.abandonSession(unarchiveId);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    @Ignore("b/321969592")
    public void reportUnarchivalState_error() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);
        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get(5, TimeUnit.SECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        SessionListener sessionListener = new SessionListener();
        mPackageInstaller.registerSessionCallback(sessionListener,
                new Handler(Looper.getMainLooper()));

        runWithShellPermissionIdentity(
                () -> mPackageInstaller.requestUnarchive(PACKAGE_NAME,
                        new IntentSender((IIntentSender) mUnarchiveIntentSender)),
                Manifest.permission.INSTALL_PACKAGES);
        int unarchiveId = sUnarchiveId.get(5, TimeUnit.SECONDS);

        int draftSessionId = sessionListener.mSessionIdCreated.get(10, TimeUnit.MILLISECONDS);

        runWithShellPermissionIdentity(
                () -> mPackageInstaller.reportUnarchivalState(
                        UnarchivalState.createGenericErrorState(unarchiveId)),
                Manifest.permission.INSTALL_PACKAGES);
        assertThat(mUnarchiveIntentSender.mPackage.get(5, TimeUnit.SECONDS)).isEqualTo(
                PACKAGE_NAME);
        assertThat(mUnarchiveIntentSender.mStatus.get(10, TimeUnit.MILLISECONDS)).isEqualTo(
                PackageInstaller.UNARCHIVAL_GENERIC_ERROR);
        assertThat(mUnarchiveIntentSender.mIntent.get(10,
                TimeUnit.MILLISECONDS).getAction()).isEqualTo(ACTION_UNARCHIVE_ERROR_DIALOG);

        assertThat(sessionListener.mSessionIdFinished.get(5, TimeUnit.SECONDS)).isEqualTo(
                draftSessionId);
    }

    @Test
    public void archiveApp_noMainActivity() throws NameNotFoundException {
        // To ensure the installer is set.
        uninstallPackage(NO_ACTIVITY_PACKAGE_NAME);
        installPackage(NO_ACTIVITY_PACKAGE_NAME, NO_ACTIVITY_APK_PATH);

        PackageManager.NameNotFoundException e =
                runWithShellPermissionIdentity(
                        () -> assertThrows(
                                PackageManager.NameNotFoundException.class,
                                () -> mPackageInstaller.requestArchive(NO_ACTIVITY_PACKAGE_NAME,
                                        new IntentSender((IIntentSender) mArchiveIntentSender))),
                        Manifest.permission.DELETE_PACKAGES);

        assertThat(e).hasMessageThat()
                .isEqualTo(TextUtils.formatSimple("The app %s does not have a main activity.",
                        NO_ACTIVITY_PACKAGE_NAME));

        // Reset for other PM tests.
        installPackageWithNoInstaller(NO_ACTIVITY_PACKAGE_NAME, NO_ACTIVITY_APK_PATH);
    }

    @Test
    public void archiveApp_appOptedOut() throws NameNotFoundException {
        installPackage(PACKAGE_NAME, APK_PATH);
        setOptInStatus(PACKAGE_NAME, /* optIn= */ false);

        PackageManager.NameNotFoundException e =
                runWithShellPermissionIdentity(
                        () -> assertThrows(
                                PackageManager.NameNotFoundException.class,
                                () -> mPackageInstaller.requestArchive(PACKAGE_NAME,
                                        new IntentSender((IIntentSender) mArchiveIntentSender))),
                        Manifest.permission.DELETE_PACKAGES);

        assertThat(e).hasMessageThat().isEqualTo(
                TextUtils.formatSimple("The app %s is opted out of archiving.", PACKAGE_NAME));
    }

    @Test
    public void archiveApp_shellCommand() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);

        assertThat(
                SystemUtil.runShellCommand(String.format("pm archive %s", PACKAGE_NAME))).isEqualTo(
                "Success\n");
        assertThat(mPackageManager.getPackageInfo(PACKAGE_NAME,
                PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES)).applicationInfo.isArchived).isTrue();
    }

    @Test
    public void unarchiveApp_shellCommand() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);
        assertThat(
                SystemUtil.runShellCommand(String.format("pm archive %s", PACKAGE_NAME))).isEqualTo(
                "Success\n");

        assertThat(
                SystemUtil.runShellCommand(String.format("pm request-unarchive %s", PACKAGE_NAME)))
                .isEqualTo("Success\n");

        assertThat(sUnarchiveReceiverPackageName.get(5, TimeUnit.SECONDS)).isEqualTo(PACKAGE_NAME);
        assertThat(sUnarchiveReceiverAllUsers.get(1, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    public void archiveApp_broadcasts() throws Exception {
        installPackage(PACKAGE_NAME, APK_PATH);

        int currentUser = ActivityManager.getCurrentUser();
        PackageBroadcastReceiver
                addedBroadcastReceiver = new PackageBroadcastReceiver(
                PACKAGE_NAME, currentUser, Intent.ACTION_PACKAGE_ADDED
        );
        PackageBroadcastReceiver removedBroadcastReceiver = new PackageBroadcastReceiver(
                PACKAGE_NAME, currentUser, Intent.ACTION_PACKAGE_REMOVED
        );
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        try {
            mContext.registerReceiver(removedBroadcastReceiver, intentFilter);

            runWithShellPermissionIdentity(
                    () -> {
                        mPackageInstaller.requestArchive(PACKAGE_NAME,
                                new IntentSender((IIntentSender) mArchiveIntentSender));
                        assertThat(mArchiveIntentSender.mStatus.get(5, TimeUnit.SECONDS)).isEqualTo(
                                PackageInstaller.STATUS_SUCCESS);
                    },
                    Manifest.permission.DELETE_PACKAGES);

            removedBroadcastReceiver.assertBroadcastReceived();
            Intent removedIntent = removedBroadcastReceiver.getBroadcastResult();
            assertNotNull(removedIntent);
            assertTrue(removedIntent.getExtras().getBoolean(Intent.EXTRA_ARCHIVAL, false));
            assertTrue(removedIntent.getExtras().getBoolean(Intent.EXTRA_REPLACING, false));

            mContext.registerReceiver(addedBroadcastReceiver, intentFilter);
            installPackage(PACKAGE_NAME, APK_PATH);

            addedBroadcastReceiver.assertBroadcastReceived();
            Intent addedIntent = addedBroadcastReceiver.getBroadcastResult();
            assertNotNull(addedIntent);
            assertTrue(addedIntent.getExtras().getBoolean(Intent.EXTRA_REPLACING, false));
        } finally {
            try {
                mContext.unregisterReceiver(removedBroadcastReceiver);
                mContext.unregisterReceiver(addedBroadcastReceiver);
            } catch (Exception e) {
                // Already unregistered.
            }
        }
    }

    @Test
    public void isAppArchivable_success() throws NameNotFoundException {
        installPackage(PACKAGE_NAME, APK_PATH);

        assertThat(mContext.getPackageManager().isAppArchivable(PACKAGE_NAME)).isTrue();
    }

    @Test
    public void isAppArchivable_installerDoesntSupportUnarchival() throws NameNotFoundException {
        installPackage(PACKAGE_NAME, APK_PATH);
        mContext.getPackageManager().setComponentEnabledSetting(
                new ComponentName(mContext, UnarchiveBroadcastReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        assertThat(mContext.getPackageManager().isAppArchivable(PACKAGE_NAME)).isFalse();
    }

    @Test
    public void isAppArchivable_noMainActivity() throws NameNotFoundException {
        // To ensure the installer is set.
        uninstallPackage(NO_ACTIVITY_PACKAGE_NAME);
        installPackage(NO_ACTIVITY_PACKAGE_NAME, NO_ACTIVITY_APK_PATH);

        assertThat(
                mContext.getPackageManager().isAppArchivable(NO_ACTIVITY_PACKAGE_NAME)).isFalse();

        // Reset for other PM tests.
        installPackageWithNoInstaller(NO_ACTIVITY_PACKAGE_NAME, NO_ACTIVITY_APK_PATH);
    }

    @Test
    public void isAppArchivable_appOptedOut() throws NameNotFoundException {
        installPackage(PACKAGE_NAME, APK_PATH);
        setOptInStatus(PACKAGE_NAME, /* optIn= */ false);

        assertThat(
                mContext.getPackageManager().isAppArchivable(PACKAGE_NAME)).isFalse();

    }

    @Test
    public void isAppArchivable_systemApp() throws NameNotFoundException {
        assertThat(
                mContext.getPackageManager().isAppArchivable("android")).isFalse();

    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void startUnarchival_intentIsNotRelatedToArchivedApp()
            throws NameNotFoundException, ExecutionException, InterruptedException {
        installPackage(PACKAGE_NAME, APK_PATH);
        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(
                            PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);
        ComponentName archiveComponentName =
                new ComponentName(PACKAGE_NAME, "ClassRandom.MainActivity");
        Intent callingIntent =
                new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(PACKAGE_NAME, archiveComponentName.getClassName())
                        .setFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        ActivityNotFoundException e =
                assertThrows(
                        ActivityNotFoundException.class,
                        () -> mContext.startActivity(callingIntent));

        assertThat(e)
                .hasMessageThat()
                .contains(
                        TextUtils.formatSimple(
                                "Unable to find explicit activity class %s",
                                archiveComponentName.toShortString()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ARCHIVING)
    public void startUnarchival_appIsNotDefaultLauncher_permissionDeniedForUnarchival()
            throws NameNotFoundException, ExecutionException, InterruptedException {
        installPackage(PACKAGE_NAME, APK_PATH);
        runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(
                            PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);
        ComponentName archiveComponentName = new ComponentName(PACKAGE_NAME, ACTIVITY_NAME);
        Intent callingIntent =
                new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(PACKAGE_NAME, archiveComponentName.getClassName())
                        .setFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        SecurityException e =
                assertThrows(
                        SecurityException.class,
                        () -> mContext.startActivity(callingIntent));

        assertThat(e).hasMessageThat().contains("Not allowed to start activity Intent");
    }

    private void launchTestActivity() {
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
        mContext.startActivity(createTestActivityIntent(),
                options.toBundle());
        mUiDevice.wait(Until.hasObject(By.clazz(PACKAGE_NAME, ACTIVITY_NAME)),
                TimeUnit.SECONDS.toMillis(5));
    }

    private Intent createTestActivityIntent() {
        final Intent intent = new Intent();
        intent.setClassName(PACKAGE_NAME, ACTIVITY_NAME);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(
                String.format("pm uninstall %s", packageName));
    }

    private void installPackage(String packageName, String path) throws NameNotFoundException {
        assertEquals("Success\n", SystemUtil.runShellCommand(
                String.format("pm install -r -i %s -t -g %s", mContext.getPackageName(),
                        path)));
        setOptInStatus(packageName, /* optIn= */ true);
    }

    private void setOptInStatus(String packageName, boolean optIn) throws NameNotFoundException {
        ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(
                packageName, /* flags= */ 0);
        runWithShellPermissionIdentity(
                () -> mAppOpsManager.setUidMode(
                        AppOpsManager.OP_AUTO_REVOKE_PERMISSIONS_IF_UNUSED,
                        applicationInfo.uid,
                        optIn ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED),
                Manifest.permission.MANAGE_APP_OPS_MODES);
    }

    private void installPackageWithNoInstaller(String packageName, String path)
            throws NameNotFoundException {
        assertEquals("Success\n",
                SystemUtil.runShellCommand(String.format("pm install -r -t -g %s", path)));
        setOptInStatus(packageName, /* optIn= */ true);
    }

    private PackageInfo getPackageInfo() {
        try {
            return mContext.getPackageManager().getPackageInfo(
                    PACKAGE_NAME, /* flags= */ 0);
        } catch (NameNotFoundException e) {
            fail("Package " + PACKAGE_NAME + " not installed for user "
                    + mContext.getUser() + ": " + e);
        }
        return null;
    }

    private static void recycleBitmap(Drawable icon, Bitmap bitmap) {
        bitmap.recycle();
        if (icon instanceof BitmapDrawable) {
            ((BitmapDrawable) icon).getBitmap().recycle();
        }
    }

    private static boolean isFormFactorSupported() {
        return !FeatureUtil.isArc()
                && !FeatureUtil.isAutomotive()
                && !FeatureUtil.isTV()
                && !FeatureUtil.isWatch()
                && !FeatureUtil.isVrHeadset();
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();

        }

        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static class UnarchiveBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(Intent.ACTION_UNARCHIVE_PACKAGE)) {
                return;
            }
            if (sUnarchiveId == null) {
                sUnarchiveId = new CompletableFuture<>();
            }
            sUnarchiveId.complete(intent.getIntExtra(PackageInstaller.EXTRA_UNARCHIVE_ID, -1));
            if (sUnarchiveReceiverPackageName == null) {
                sUnarchiveReceiverPackageName = new CompletableFuture<>();
            }
            sUnarchiveReceiverPackageName.complete(
                    intent.getStringExtra(PackageInstaller.EXTRA_UNARCHIVE_PACKAGE_NAME));
            if (sUnarchiveReceiverAllUsers == null) {
                sUnarchiveReceiverAllUsers = new CompletableFuture<>();
            }
            sUnarchiveReceiverAllUsers.complete(
                    intent.getBooleanExtra(PackageInstaller.EXTRA_UNARCHIVE_ALL_USERS,
                            true /* defaultValue */));
        }
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

    static class UnarchiveIntentSender extends IIntentSender.Stub {

        final CompletableFuture<String> mPackage = new CompletableFuture<>();
        final CompletableFuture<Integer> mStatus = new CompletableFuture<>();
        final CompletableFuture<Intent> mIntent = new CompletableFuture<>();

        @Override
        public void send(int code, Intent intent, String resolvedType,
                IBinder whitelistToken, IIntentReceiver finishedReceiver,
                String requiredPermission, Bundle options) throws RemoteException {
            mPackage.complete(intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME));
            int status = intent.getIntExtra(PackageInstaller.EXTRA_UNARCHIVE_STATUS, -100);
            mStatus.complete(status);
            mIntent.complete(intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class));
        }

    }

    static class SessionListener extends PackageInstaller.SessionCallback {

        final CompletableFuture<Integer> mSessionIdCreated = new CompletableFuture<>();
        final CompletableFuture<Integer> mSessionIdFinished = new CompletableFuture<>();

        @Override
        public void onCreated(int sessionId) {
            mSessionIdCreated.complete(sessionId);
        }

        @Override
        public void onBadgingChanged(int sessionId) {
        }

        @Override
        public void onActiveChanged(int sessionId, boolean active) {
        }

        @Override
        public void onProgressChanged(int sessionId, float progress) {
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            mSessionIdFinished.complete(sessionId);
        }
    }
}
