/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.content.pm.Flags.FLAG_ARCHIVING;
import static android.content.pm.Flags.FLAG_LIGHTWEIGHT_INVISIBLE_LABEL_DETECTION;
import static android.os.Flags.FLAG_ALLOW_PRIVATE_PROFILE;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.getDefaultLauncher;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ArchiveCompatibilityParams;
import android.content.pm.LauncherUserInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Some tests in this class are ignored until b/126946674 is fixed. */
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class LauncherAppsTest {
    private static CompletableFuture<String> sUnarchiveReceiverPackageName;
    private Context mContext;
    private Instrumentation mInstrumentation;
    private LauncherApps mLauncherApps;
    private PackageInstaller mPackageInstaller;
    private UsageStatsManager mUsageStatsManager;
    private String mDefaultHome;
    private ArchiveIntentSender mIntentSender;
    private String mTestHome = PACKAGE_NAME;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String PACKAGE_NAME = "android.content.cts";
    private static final String FULL_CLASS_NAME = "android.content.pm.cts.LauncherMockActivity";
    private static final ComponentName FULL_COMPONENT_NAME =
            new ComponentName(PACKAGE_NAME, FULL_CLASS_NAME);

    private static final String FULL_DISABLED_CLASS_NAME =
            "android.content.pm.cts.MockActivity_Disabled";
    private static final ComponentName FULL_DISABLED_COMPONENT_NAME = new ComponentName(
            PACKAGE_NAME, FULL_DISABLED_CLASS_NAME);

    private static final int DEFAULT_OBSERVER_ID = 0;
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String[] SETTINGS_PACKAGE_GROUP = new String[] {SETTINGS_PACKAGE};
    private static final int DEFAULT_TIME_LIMIT = 1;
    private static final UserHandle USER_HANDLE = Process.myUserHandle();

    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String ARCHIVE_PACKAGE_NAME = "android.content.cts.mocklauncherapp";
    private static final String ARCHIVE_APP_TITLE = "Mock Launcher";
    private static final String ARCHIVE_ACTIVITY_NAME = ARCHIVE_PACKAGE_NAME + ".Launcher";
    private static final String ARCHIVE_APK_PATH =
            SAMPLE_APK_BASE + "CtsContentMockLauncherTestApp.apk";

    private static final String ACTIVITY_LABEL_TITLE = "Launcher Activity";
    private static final String ACTIVITY_LABEL_PACKAGE_NAME =
            "android.content.cts.mocklauncherapp.activitylabel";
    private static final String ACTIVITY_LABEL_APK_PATH =
            SAMPLE_APK_BASE + "CtsContentMockLauncherActivityLabelTestApp.apk";

    private static final String APPLICATION_LABEL_TITLE = "Launcher Application";
    private static final String APPLICATION_LABEL_PACKAGE_NAME =
            "android.content.cts.mocklauncherapp.applicationlabel";
    private static final String APPLICATION_LABEL_APK_PATH =
            SAMPLE_APK_BASE + "CtsContentMockLauncherApplicationLabelTestApp.apk";

    private static final String INVISIBLE_LABELS_PACKAGE_NAME =
            "android.content.cts.mocklauncherapp.invisiblelabels";
    private static final String INVISIBLE_LABELS_APK_PATH =
            SAMPLE_APK_BASE + "CtsContentMockLauncherInvisibleLabelsTestApp.apk";

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mLauncherApps = (LauncherApps) mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        mUsageStatsManager = (UsageStatsManager) mContext.getSystemService(
                Context.USAGE_STATS_SERVICE);

        mDefaultHome = getDefaultLauncher(mInstrumentation);
        mPackageInstaller = mContext.getPackageManager().getPackageInstaller();
        mIntentSender = new ArchiveIntentSender();
        sUnarchiveReceiverPackageName = new CompletableFuture<>();
        setDefaultLauncher(mInstrumentation, mTestHome);
        ArchiveCompatibilityParams options = new ArchiveCompatibilityParams();
        options.setEnableUnarchivalConfirmation(false);
        mLauncherApps.setArchiveCompatibility(options);
    }

    @After
    public void tearDown() throws Exception {
        unregisterObserver(DEFAULT_OBSERVER_ID);
        if (mDefaultHome != null) {
            setDefaultLauncher(mInstrumentation, mDefaultHome);
        }
        uninstallPackage(ARCHIVE_PACKAGE_NAME);
    }

    @Test
    @Ignore("Can be enabled only after b/126946674 is fixed")
    @AppModeFull(reason = "Need special permission")
    public void testGetAppUsageLimit_isNull() {
        final LauncherApps.AppUsageLimit limit = mLauncherApps.getAppUsageLimit(
                SETTINGS_PACKAGE, USER_HANDLE);
        assertNull(limit); // An observer was never registered
    }

    @Test
    @Ignore("Can be enabled only after b/126946674 is fixed")
    @AppModeFull(reason = "Need special permission")
    public void testGetAppUsageLimit_isNotNull() {
        registerDefaultObserver();
        final LauncherApps.AppUsageLimit limit = mLauncherApps.getAppUsageLimit(
                SETTINGS_PACKAGE, USER_HANDLE);
        assertNotNull(limit);
    }

    @Test
    @Ignore("Can be enabled only after b/126946674 is fixed")
    @AppModeFull(reason = "Need special permission")
    public void testGetAppUsageLimit_isNullOnUnregister() {
        registerDefaultObserver();
        unregisterObserver(DEFAULT_OBSERVER_ID);
        final LauncherApps.AppUsageLimit limit = mLauncherApps.getAppUsageLimit(
                SETTINGS_PACKAGE, USER_HANDLE);
        assertNull("An unregistered observer was returned.", limit);
    }

    @Test
    @Ignore("Can be enabled only after b/126946674 is fixed")
    @AppModeFull(reason = "Need special permission")
    public void testGetAppUsageLimit_getTotalUsageLimit() {
        registerDefaultObserver();
        final LauncherApps.AppUsageLimit limit = mLauncherApps.getAppUsageLimit(
                SETTINGS_PACKAGE, USER_HANDLE);
        assertEquals("Total usage limit not equal to the limit registered.",
                TimeUnit.MINUTES.toMillis(DEFAULT_TIME_LIMIT), limit.getTotalUsageLimit());
    }

    @Test
    @Ignore("Can be enabled only after b/126946674 is fixed")
    @AppModeFull(reason = "Need special permission")
    public void testGetAppUsageLimit_getTotalUsageRemaining() {
        registerDefaultObserver();
        final LauncherApps.AppUsageLimit limit = mLauncherApps.getAppUsageLimit(
                SETTINGS_PACKAGE, USER_HANDLE);
        assertEquals("Usage remaining not equal to the total limit with no usage.",
                limit.getTotalUsageLimit(), limit.getUsageRemaining());
    }

    @Test
    @Ignore("Can be enabled only after b/126946674 is fixed")
    @AppModeFull(reason = "Need special permission")
    public void testGetAppUsageLimit_smallestLimitReturned() {
        registerDefaultObserver();
        registerObserver(1, Duration.ofMinutes(5), Duration.ofMinutes(0));
        final LauncherApps.AppUsageLimit limit = mLauncherApps.getAppUsageLimit(
                SETTINGS_PACKAGE, USER_HANDLE);
        try {
            assertEquals("Smallest usage limit not returned when multiple limits exist.",
                    TimeUnit.MINUTES.toMillis(DEFAULT_TIME_LIMIT), limit.getTotalUsageLimit());
        } finally {
            unregisterObserver(1);
        }
    }

    @Test
    @Ignore("Can be enabled only after b/126946674 is fixed")
    @AppModeFull(reason = "Need special permission")
    public void testGetAppUsageLimit_zeroUsageRemaining() {
        registerObserver(DEFAULT_OBSERVER_ID, Duration.ofMinutes(1), Duration.ofMinutes(1));
        final LauncherApps.AppUsageLimit limit = mLauncherApps.getAppUsageLimit(
                SETTINGS_PACKAGE, USER_HANDLE);
        assertNotNull("An observer with an exhaused time limit was not registered.", limit);
        assertEquals("Usage remaining expected to be 0.", 0, limit.getUsageRemaining());
    }


    @Test
    @AppModeFull(reason = "Need special permission")
    public void testIsActivityEnabled() {
        assertTrue(mLauncherApps.isActivityEnabled(FULL_COMPONENT_NAME, USER_HANDLE));
        assertFalse(mLauncherApps.isActivityEnabled(FULL_DISABLED_COMPONENT_NAME, USER_HANDLE));

        mContext.getPackageManager().setComponentEnabledSetting(
                FULL_COMPONENT_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        mContext.getPackageManager().setComponentEnabledSetting(
                FULL_DISABLED_COMPONENT_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        assertFalse(mLauncherApps.isActivityEnabled(FULL_COMPONENT_NAME, USER_HANDLE));
        assertFalse(mLauncherApps.isActivityEnabled(FULL_DISABLED_COMPONENT_NAME, USER_HANDLE));

        mContext.getPackageManager().setComponentEnabledSetting(
                FULL_COMPONENT_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        mContext.getPackageManager().setComponentEnabledSetting(
                FULL_DISABLED_COMPONENT_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);

        assertTrue(mLauncherApps.isActivityEnabled(FULL_COMPONENT_NAME, USER_HANDLE));
        assertTrue(mLauncherApps.isActivityEnabled(FULL_DISABLED_COMPONENT_NAME, USER_HANDLE));

        mContext.getPackageManager().setComponentEnabledSetting(
                FULL_COMPONENT_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
        mContext.getPackageManager().setComponentEnabledSetting(
                FULL_DISABLED_COMPONENT_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);

        assertTrue(mLauncherApps.isActivityEnabled(FULL_COMPONENT_NAME, USER_HANDLE));
        assertFalse(mLauncherApps.isActivityEnabled(FULL_DISABLED_COMPONENT_NAME, USER_HANDLE));
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    public void testGetActivityInfo() {
        LauncherActivityInfo info = mLauncherApps.resolveActivity(
                new Intent().setComponent(FULL_COMPONENT_NAME), USER_HANDLE);
        assertNotNull(info);
        assertNotNull(info.getActivityInfo());
        assertEquals(info.getName(), info.getActivityInfo().name);
        assertEquals(info.getComponentName().getPackageName(), info.getActivityInfo().packageName);
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_LIGHTWEIGHT_INVISIBLE_LABEL_DETECTION)
    public void testGetActivityListAndGetLabel_ActivityLabel() {
        try {
            installPackage(ACTIVITY_LABEL_APK_PATH);
            List<LauncherActivityInfo> activities =
                    mLauncherApps.getActivityList(ACTIVITY_LABEL_PACKAGE_NAME, USER_HANDLE);

            assertThat(activities).hasSize(1);
            LauncherActivityInfo launcherActivityInfo = activities.get(0);
            assertThat(launcherActivityInfo.getLabel().toString()).isEqualTo(ACTIVITY_LABEL_TITLE);
        } finally {
            uninstallPackage(ACTIVITY_LABEL_PACKAGE_NAME);
        }
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_LIGHTWEIGHT_INVISIBLE_LABEL_DETECTION)
    public void testGetActivityListAndGetLabel_emptyActivityLabel_ApplicationLabel() {
        try {
            installPackage(APPLICATION_LABEL_APK_PATH);
            List<LauncherActivityInfo> activities =
                    mLauncherApps.getActivityList(APPLICATION_LABEL_PACKAGE_NAME, USER_HANDLE);

            assertThat(activities).hasSize(1);
            LauncherActivityInfo launcherActivityInfo = activities.get(0);
            assertThat(launcherActivityInfo.getLabel().toString())
                    .isEqualTo(APPLICATION_LABEL_TITLE);
        } finally {
            uninstallPackage(APPLICATION_LABEL_PACKAGE_NAME);
        }
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_LIGHTWEIGHT_INVISIBLE_LABEL_DETECTION)
    public void testGetActivityListAndGetLabel_emptyLabels_PackageName() {
        try {
            installPackage(INVISIBLE_LABELS_APK_PATH);
            List<LauncherActivityInfo> activities =
                    mLauncherApps.getActivityList(INVISIBLE_LABELS_PACKAGE_NAME, USER_HANDLE);

            assertThat(activities).hasSize(1);
            LauncherActivityInfo launcherActivityInfo = activities.get(0);
            assertThat(launcherActivityInfo.getLabel().toString())
                    .isEqualTo(INVISIBLE_LABELS_PACKAGE_NAME);
        } finally {
            uninstallPackage(INVISIBLE_LABELS_PACKAGE_NAME);
        }
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ALLOW_PRIVATE_PROFILE)
    public void testLauncherUserInfo() {
        LauncherUserInfo info = mLauncherApps.getLauncherUserInfo(UserHandle.of(0));
        assertThat(info).isNotNull();
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ALLOW_PRIVATE_PROFILE)
    public void testGetMarketIntent() {
        IntentSender intentSender =
                mLauncherApps.getAppMarketActivityIntent(PACKAGE_NAME, UserHandle.of(0));
        assertThat(intentSender).isNotNull();
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ALLOW_PRIVATE_PROFILE)
    public void testGetPreInstalledSystemPackages() {
        List<String> packages = mLauncherApps.getPreInstalledSystemPackages(UserHandle.of(0));
        assertThat(packages).isNotNull();
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ARCHIVING)
    public void testGetActivityList_targetArchivedApp()
            throws PackageManager.NameNotFoundException {
        installPackage(ARCHIVE_APK_PATH);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(
                            ARCHIVE_PACKAGE_NAME,
                            new IntentSender((IIntentSender) mIntentSender));
                    assertThat(mIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        List<LauncherActivityInfo> activities =
                mLauncherApps.getActivityList(ARCHIVE_PACKAGE_NAME, USER_HANDLE);

        assertThat(activities).hasSize(1);
        LauncherActivityInfo archiveAppActivityInfo = activities.get(0);
        assertThat(archiveAppActivityInfo.getComponentName())
                .isEqualTo(new ComponentName(ARCHIVE_PACKAGE_NAME, ARCHIVE_ACTIVITY_NAME));
        assertThat(archiveAppActivityInfo.getUser()).isEqualTo(USER_HANDLE);
        assertThat(archiveAppActivityInfo.getApplicationInfo().isArchived).isEqualTo(true);
        assertThat(archiveAppActivityInfo.getLabel().toString()).isEqualTo(ARCHIVE_APP_TITLE);
        PackageManager packageManager = mContext.getPackageManager();
        Drawable expectedIcon =
                packageManager.getApplicationIcon(archiveAppActivityInfo.getApplicationInfo());
        Bitmap iconFromPackageManager = drawableToBitmap(expectedIcon);
        Drawable actualIcon =
                packageManager.getApplicationIcon(
                        mContext.getPackageManager()
                                .getApplicationInfo(
                                        ARCHIVE_PACKAGE_NAME,
                                        PackageManager.ApplicationInfoFlags.of(
                                                PackageManager.MATCH_ARCHIVED_PACKAGES)));
        Bitmap iconFromArchiveActivityInfo = drawableToBitmap(actualIcon);
        assertTrue(iconFromPackageManager.sameAs(iconFromArchiveActivityInfo));

        recycleBitmaps(
                expectedIcon, actualIcon, iconFromArchiveActivityInfo, iconFromPackageManager);
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ARCHIVING)
    public void testGetActivityList_allArchivedApps()
            throws PackageManager.NameNotFoundException {
        installPackage(ARCHIVE_APK_PATH);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(
                            ARCHIVE_PACKAGE_NAME,
                            new IntentSender((IIntentSender) mIntentSender));
                    assertThat(mIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        List<LauncherActivityInfo> activities = mLauncherApps.getActivityList(null, USER_HANDLE);

        assertThat(activities).isNotEmpty();
        List<LauncherActivityInfo> archiveAppActivityInfoList =
                activities.stream()
                        .filter(activity -> activity.getActivityInfo().isArchived)
                        .collect(Collectors.toList());
        assertThat(archiveAppActivityInfoList).isNotEmpty();
        assertThat(
                        archiveAppActivityInfoList.stream()
                                .anyMatch(
                                        activity ->
                                                activity.getComponentName()
                                                        .equals(
                                                                new ComponentName(
                                                                        ARCHIVE_PACKAGE_NAME,
                                                                        ARCHIVE_ACTIVITY_NAME))))
                .isTrue();
        assertThat(archiveAppActivityInfoList.get(0).getUser()).isEqualTo(USER_HANDLE);
        assertThat(archiveAppActivityInfoList.get(0).getApplicationInfo().isArchived)
                .isEqualTo(true);
        assertThat(archiveAppActivityInfoList.get(0).getLabel().toString())
                .isEqualTo(ARCHIVE_APP_TITLE);
        PackageManager packageManager = mContext.getPackageManager();
        Drawable expectedIcon =
                packageManager.getApplicationIcon(
                        archiveAppActivityInfoList.get(0).getApplicationInfo());
        Bitmap iconFromPackageManager = drawableToBitmap(expectedIcon);
        Drawable actualIcon =
                packageManager.getApplicationIcon(
                        mContext.getPackageManager()
                                .getApplicationInfo(
                                        ARCHIVE_PACKAGE_NAME,
                                        PackageManager.ApplicationInfoFlags.of(
                                                PackageManager.MATCH_ARCHIVED_PACKAGES)));
        Bitmap iconFromArchiveActivityInfo = drawableToBitmap(actualIcon);
        assertTrue(iconFromPackageManager.sameAs(iconFromArchiveActivityInfo));

        recycleBitmaps(
                expectedIcon, actualIcon, iconFromArchiveActivityInfo, iconFromPackageManager);
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ARCHIVING)
    public void resolveActivity_archivedApp_componentNameMatches()
            throws PackageManager.NameNotFoundException {
        installPackage(ARCHIVE_APK_PATH);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(
                            ARCHIVE_PACKAGE_NAME,
                            new IntentSender((IIntentSender) mIntentSender));
                    assertThat(mIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);
        ComponentName archiveAppComponentName =
                new ComponentName(ARCHIVE_PACKAGE_NAME, ARCHIVE_ACTIVITY_NAME);

        LauncherActivityInfo activity =
                mLauncherApps.resolveActivity(
                        new Intent().setComponent(archiveAppComponentName), USER_HANDLE);

        assertThat(activity).isNotNull();
        assertThat(activity.getComponentName()).isEqualTo(archiveAppComponentName);
        assertThat(activity.getUser()).isEqualTo(USER_HANDLE);
        assertThat(activity.getApplicationInfo().isArchived).isEqualTo(true);
        assertThat(activity.getLabel().toString()).isEqualTo(ARCHIVE_APP_TITLE);
        PackageManager packageManager = mContext.getPackageManager();
        Drawable expectedIcon = packageManager.getApplicationIcon(activity.getApplicationInfo());
        Bitmap iconFromPackageManager = drawableToBitmap(expectedIcon);
        Drawable actualIcon =
                packageManager.getApplicationIcon(
                        mContext.getPackageManager()
                                .getApplicationInfo(
                                        ARCHIVE_PACKAGE_NAME,
                                        PackageManager.ApplicationInfoFlags.of(
                                                PackageManager.MATCH_ARCHIVED_PACKAGES)));
        Bitmap iconFromArchiveActivityInfo = drawableToBitmap(actualIcon);
        assertTrue(iconFromPackageManager.sameAs(iconFromArchiveActivityInfo));

        recycleBitmaps(
                expectedIcon, actualIcon, iconFromArchiveActivityInfo, iconFromPackageManager);
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ARCHIVING)
    public void resolveActivity_archivedApp_classNameMismatch() {
        installPackage(ARCHIVE_APK_PATH);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(
                            ARCHIVE_PACKAGE_NAME,
                            new IntentSender((IIntentSender) mIntentSender));
                    assertThat(mIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        LauncherActivityInfo activity =
                mLauncherApps.resolveActivity(
                        new Intent()
                                .setComponent(
                                        new ComponentName(ARCHIVE_PACKAGE_NAME, "randomClassName")),
                        USER_HANDLE);

        assertThat(activity).isNull();
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ARCHIVING)
    public void isActivityEnabled_archivedApp_componentNameMatches() {
        installPackage(ARCHIVE_APK_PATH);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(
                            ARCHIVE_PACKAGE_NAME,
                            new IntentSender((IIntentSender) mIntentSender));
                    assertThat(mIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);
        ComponentName archiveAppComponentName =
                new ComponentName(ARCHIVE_PACKAGE_NAME, ARCHIVE_ACTIVITY_NAME);

        boolean isArchivedAppActivityEnabled =
                mLauncherApps.isActivityEnabled(archiveAppComponentName, USER_HANDLE);

        assertThat(isArchivedAppActivityEnabled).isTrue();
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ARCHIVING)
    public void isActivityEnabled_archivedApp_componentNameMismatch() {
        installPackage(ARCHIVE_APK_PATH);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(
                            ARCHIVE_PACKAGE_NAME,
                            new IntentSender((IIntentSender) mIntentSender));
                    assertThat(mIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);
        ComponentName archiveAppComponentName =
                new ComponentName(ARCHIVE_PACKAGE_NAME, "randomClassName");

        boolean isArchivedAppActivityEnabled =
                mLauncherApps.isActivityEnabled(archiveAppComponentName, USER_HANDLE);

        assertThat(isArchivedAppActivityEnabled).isFalse();
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ARCHIVING)
    public void isPackageEnabled_archivedApp() {
        installPackage(ARCHIVE_APK_PATH);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(
                            ARCHIVE_PACKAGE_NAME,
                            new IntentSender((IIntentSender) mIntentSender));
                    assertThat(mIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        boolean isArchivedAppPackageEnabled =
                mLauncherApps.isPackageEnabled(ARCHIVE_PACKAGE_NAME, USER_HANDLE);

        assertThat(isArchivedAppPackageEnabled).isTrue();
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ARCHIVING)
    public void shouldHideFromSuggestions_archivedApp() {
        installPackage(ARCHIVE_APK_PATH);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(
                            ARCHIVE_PACKAGE_NAME,
                            new IntentSender((IIntentSender) mIntentSender));
                    assertThat(mIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        boolean isArchivedAppPackageEnabled =
                mLauncherApps.shouldHideFromSuggestions(ARCHIVE_PACKAGE_NAME, USER_HANDLE);

        assertThat(isArchivedAppPackageEnabled).isTrue();
    }

    @Test
    @AppModeFull(reason = "Need special permission")
    @RequiresFlagsEnabled(FLAG_ARCHIVING)
    public void startActivityAsUser_archivedApp() throws ExecutionException, InterruptedException {
        installPackage(ARCHIVE_APK_PATH);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mPackageInstaller.requestArchive(ARCHIVE_PACKAGE_NAME,
                            new IntentSender((IIntentSender) mIntentSender));
                    assertThat(mIntentSender.mStatus.get()).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);
        LauncherUnarchiveBroadcastReceiver unarchiveReceiver =
                new LauncherUnarchiveBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_UNARCHIVE_PACKAGE);
        mContext.registerReceiver(
                unarchiveReceiver,
                intentFilter,
                null,
                null,
                Context.RECEIVER_EXPORTED);

        mLauncherApps.startMainActivity(
                new ComponentName(ARCHIVE_PACKAGE_NAME, ARCHIVE_ACTIVITY_NAME), USER_HANDLE,
                null /* sourceBounds */,
                null /* opts */);

        assertThat(sUnarchiveReceiverPackageName.get()).isEqualTo(ARCHIVE_PACKAGE_NAME);

        mContext.unregisterReceiver(unarchiveReceiver);
    }

    private void registerDefaultObserver() {
        registerObserver(DEFAULT_OBSERVER_ID, Duration.ofMinutes(DEFAULT_TIME_LIMIT),
                Duration.ofMinutes(0));
    }

    private void registerObserver(int observerId, Duration timeLimit, Duration timeUsed) {
        SystemUtil.runWithShellPermissionIdentity(() ->
                mUsageStatsManager.registerAppUsageLimitObserver(
                        observerId, SETTINGS_PACKAGE_GROUP, timeLimit, timeUsed,
                        PendingIntent.getActivity(mContext, -1,
                                new Intent().setPackage(mContext.getPackageName()),
                                PendingIntent.FLAG_MUTABLE)));
    }

    private void unregisterObserver(int observerId) {
        SystemUtil.runWithShellPermissionIdentity(() ->
                mUsageStatsManager.unregisterAppUsageLimitObserver(observerId));
    }

    private void installPackage(String path) {
        assertEquals(
                "Success\n",
                SystemUtil.runShellCommand(
                        String.format(
                                "pm install -r -i %s -t -g %s", mContext.getPackageName(), path)));
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(String.format("pm uninstall %s", packageName));
    }

    public static class LauncherUnarchiveBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(Intent.ACTION_UNARCHIVE_PACKAGE)) {
                return;
            }
            if (sUnarchiveReceiverPackageName == null) {
                sUnarchiveReceiverPackageName = new CompletableFuture<>();
            }
            sUnarchiveReceiverPackageName.complete(
                    intent.getStringExtra(PackageInstaller.EXTRA_UNARCHIVE_PACKAGE_NAME));
        }
    }

    private static class ArchiveIntentSender extends IIntentSender.Stub {
        final CompletableFuture<Integer> mStatus = new CompletableFuture<>();

        @Override
        public void send(
                int code,
                Intent intent,
                String resolvedType,
                IBinder whitelistToken,
                IIntentReceiver finishedReceiver,
                String requiredPermission,
                Bundle options)
                throws RemoteException {
            mStatus.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -100));
        }
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap =
                Bitmap.createBitmap(
                        drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(),
                        Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private static void recycleBitmaps(Drawable expectedIcon, Drawable actualIcon,
            Bitmap actualBitmap, Bitmap expectedBitmap) {
        actualBitmap.recycle();
        expectedBitmap.recycle();
        if (expectedIcon instanceof BitmapDrawable) {
            ((BitmapDrawable) expectedIcon).getBitmap().recycle();
        }
        if (actualIcon instanceof BitmapDrawable) {
            ((BitmapDrawable) actualIcon).getBitmap().recycle();
        }
    }
}
