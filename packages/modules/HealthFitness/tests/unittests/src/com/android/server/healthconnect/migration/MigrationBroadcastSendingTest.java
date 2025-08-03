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

package com.android.server.healthconnect.migration;

import static com.android.server.healthconnect.migration.MigrationTestUtils.MOCK_CONFIGURED_PACKAGE;
import static com.android.server.healthconnect.migration.MigrationTestUtils.MOCK_QUERIED_BROADCAST_RECEIVER_ONE;
import static com.android.server.healthconnect.migration.MigrationTestUtils.MOCK_QUERIED_BROADCAST_RECEIVER_TWO;
import static com.android.server.healthconnect.migration.MigrationTestUtils.MOCK_UNCONFIGURED_PACKAGE_ONE;
import static com.android.server.healthconnect.migration.MigrationTestUtils.MOCK_UNCONFIGURED_PACKAGE_TWO;
import static com.android.server.healthconnect.migration.MigrationTestUtils.PERMISSIONS_TO_CHECK;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.health.connect.HealthConnectManager;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for broadcast sending logic in {@link MigrationBroadcast} */
@RunWith(AndroidJUnit4.class)
public class MigrationBroadcastSendingTest {
    @Mock private Context mContext;
    @Mock private Context mUserContext;
    @Mock private PackageManager mPackageManager;
    @Mock private PackageManager mUserContextPackageManager;
    @Mock private Resources mResources;
    @Mock private UserHandle mUser;
    @Mock private UserManager mUserManager;

    private MigrationBroadcast mMigrationBroadcast;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getString(anyInt())).thenReturn(MOCK_CONFIGURED_PACKAGE);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        when(mContext.createContextAsUser(eq(mUser), eq(0))).thenReturn(mUserContext);
        when(mUserContext.getPackageManager()).thenReturn(mUserContextPackageManager);

        when(mUserContext.getSystemService(UserManager.class)).thenReturn(mUserManager);

        mMigrationBroadcast = new MigrationBroadcast(mContext, mUser);
    }

    /** Tests case where there are no apps holding the required permission. */
    @Test
    public void testBroadcast_noPermissionMatchingApps_noBroadcastSent() throws Exception {
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new ArrayList<PackageInfo>());

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyExplicitBroadcastInvocation(never());
        verifyImplicitBroadcastInvocation(never());
    }

    /** Tests case where there are no apps handling the required intent. */
    @Test
    public void testBroadcast_noIntentMatchingApps_noBroadcastSent() throws Exception {
        ArrayList<PackageInfo> packageInfoArray = createPackageInfoArray(MOCK_CONFIGURED_PACKAGE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(null, mPackageManager);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyExplicitBroadcastInvocation(never());
        verifyImplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there is exactly one migration aware app, which is the configured app that
     * is installed on the foreground user, and has exactly one broadcast receiver handling the
     * required intent.
     */
    @Test
    public void testBroadcast_oneConfiguredMigratorAppWithNonNullReceiver_explicitBroadcastSent()
            throws Exception {
        ArrayList<PackageInfo> packageInfoArray = createPackageInfoArray(MOCK_CONFIGURED_PACKAGE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new PackageInfo());
        when(mUserManager.isUserForeground()).thenReturn(true);
        List<ResolveInfo> resolveInfoList =
                MigrationTestUtils.createResolveInfoList(
                        false, MOCK_CONFIGURED_PACKAGE, MOCK_QUERIED_BROADCAST_RECEIVER_ONE);
        setQueryBroadcastReceiversAsUserResult(resolveInfoList);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyExplicitBroadcastInvocation(times(1));
        verifyImplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there is exactly one migration aware app, which is the configured app that
     * is installed on the foreground user, but does not have any broadcast receivers handling the
     * required intent.
     */
    @Test
    public void testBroadcast_oneConfiguredMigratorAppWithNullReceiver_implicitBroadcastSent()
            throws Exception {
        ArrayList<PackageInfo> packageInfoArray = createPackageInfoArray(MOCK_CONFIGURED_PACKAGE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new PackageInfo());
        when(mUserManager.isUserForeground()).thenReturn(true);
        List<ResolveInfo> resolveInfoList =
                MigrationTestUtils.createResolveInfoList(
                        true, MOCK_CONFIGURED_PACKAGE, MOCK_QUERIED_BROADCAST_RECEIVER_ONE);
        setQueryBroadcastReceiversAsUserResult(resolveInfoList);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyImplicitBroadcastInvocation(times(1));
        verifyExplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there is exactly one migration aware app, which is the configured app that
     * is installed on the foreground user, but does not have any broadcast receivers handling the
     * required intent.
     */
    @Test
    public void testBroadcast_oneConfiguredMigratorAppWithoutReceiver_implicitBroadcastSent()
            throws Exception {
        ArrayList<PackageInfo> packageInfoArray = createPackageInfoArray(MOCK_CONFIGURED_PACKAGE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new PackageInfo());
        when(mUserManager.isUserForeground()).thenReturn(true);
        List<android.content.pm.ResolveInfo> resolveInfoList =
                new ArrayList<android.content.pm.ResolveInfo>();
        setQueryBroadcastReceiversAsUserResult(resolveInfoList);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyImplicitBroadcastInvocation(times(1));
        verifyExplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there is exactly one migration aware app, which is the configured app that
     * is installed on the foreground user, but has multiple broadcast receivers handling the
     * required intent.
     */
    @Test
    public void testBroadcast_oneConfiguredMigratorAppWithMultipleReceivers_implicitBroadcastSent()
            throws Exception {
        ArrayList<PackageInfo> packageInfoArray = createPackageInfoArray(MOCK_CONFIGURED_PACKAGE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new PackageInfo());
        when(mUserManager.isUserForeground()).thenReturn(true);
        List<ResolveInfo> resolveInfoList =
                MigrationTestUtils.createResolveInfoList(
                        true,
                        MOCK_CONFIGURED_PACKAGE,
                        MOCK_QUERIED_BROADCAST_RECEIVER_ONE,
                        MOCK_QUERIED_BROADCAST_RECEIVER_TWO);
        setQueryBroadcastReceiversAsUserResult(resolveInfoList);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyImplicitBroadcastInvocation(times(1));
        verifyExplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there is exactly one migration aware app, which is the configured app that
     * is installed on the given user, but is not the foreground user.
     */
    @Test
    public void testBroadcast_oneConfiguredMigratorAppUserNotForeground_noBroadcastSent()
            throws Exception {
        ArrayList<PackageInfo> packageInfoArray = createPackageInfoArray(MOCK_CONFIGURED_PACKAGE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new PackageInfo());
        when(mUserManager.isUserForeground()).thenReturn(false);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyExplicitBroadcastInvocation(never());
        verifyImplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there is exactly one migration aware app, which is the configured app, but
     * it is not installed on the given user.
     */
    @Test
    public void testBroadcast_oneConfiguredMigratorAppNotInstalledOnUser_noBroadcastSent()
            throws Exception {
        ArrayList<PackageInfo> packageInfoArray = createPackageInfoArray(MOCK_CONFIGURED_PACKAGE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenThrow(PackageManager.NameNotFoundException.class);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyExplicitBroadcastInvocation(never());
        verifyImplicitBroadcastInvocation(never());
    }

    /** Tests case where there is exactly one migration aware, but it is not the configured app. */
    @Test(expected = Exception.class)
    public void testBroadcast_oneMigratorNotConfiguredApp_exceptionThrown() throws Exception {
        ArrayList<PackageInfo> packageInfoArray =
                createPackageInfoArray(MOCK_UNCONFIGURED_PACKAGE_ONE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);

        try {
            mMigrationBroadcast.sendInvocationBroadcast();
        } finally {
            verifyExplicitBroadcastInvocation(never());
            verifyImplicitBroadcastInvocation(never());
        }
    }

    /**
     * Tests case where there are multiple migration aware apps, including the configured app which
     * is installed on the foreground user and has exactly one broadcast receiver handling the
     * required intent.
     */
    @Test
    public void testBroadcast_multipleAppsIncludingConfiguredAppOneReceiver_explicitBroadcastSent()
            throws Exception {
        ArrayList<PackageInfo> packageInfoArray =
                createPackageInfoArray(MOCK_CONFIGURED_PACKAGE, MOCK_UNCONFIGURED_PACKAGE_ONE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new PackageInfo());
        when(mUserManager.isUserForeground()).thenReturn(true);
        List<ResolveInfo> resolveInfoList =
                MigrationTestUtils.createResolveInfoList(
                        false, MOCK_CONFIGURED_PACKAGE, MOCK_QUERIED_BROADCAST_RECEIVER_ONE);
        setQueryBroadcastReceiversAsUserResult(resolveInfoList);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyExplicitBroadcastInvocation(times(1));
        verifyImplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there are multiple migration aware apps, including the configured app which
     * is installed on the foreground user but there are no broadcast receivers handling the
     * required intent.
     */
    @Test
    public void
            testBroadcast_multipleAppsIncludingConfiguredAppOneNullReceiver_implicitBroadcastSent()
                    throws Exception {
        ArrayList<PackageInfo> packageInfoArray =
                createPackageInfoArray(MOCK_CONFIGURED_PACKAGE, MOCK_UNCONFIGURED_PACKAGE_ONE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new PackageInfo());
        when(mUserManager.isUserForeground()).thenReturn(true);
        List<ResolveInfo> resolveInfoList =
                MigrationTestUtils.createResolveInfoList(
                        true, MOCK_CONFIGURED_PACKAGE, MOCK_QUERIED_BROADCAST_RECEIVER_ONE);
        setQueryBroadcastReceiversAsUserResult(resolveInfoList);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyImplicitBroadcastInvocation(times(1));
        verifyExplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there are multiple migration aware apps, including the configured app which
     * is installed on the foreground user but does not have broadcast receivers handling the
     * required intent.
     */
    @Test
    public void
            testBroadcast_multipleAppsIncludingConfiguredAppWithoutReceiver_implicitBroadcastSent()
                    throws Exception {
        ArrayList<PackageInfo> packageInfoArray =
                createPackageInfoArray(MOCK_CONFIGURED_PACKAGE, MOCK_UNCONFIGURED_PACKAGE_ONE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new PackageInfo());
        when(mUserManager.isUserForeground()).thenReturn(true);
        List<android.content.pm.ResolveInfo> resolveInfoList =
                new ArrayList<android.content.pm.ResolveInfo>();
        setQueryBroadcastReceiversAsUserResult(resolveInfoList);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyImplicitBroadcastInvocation(times(1));
        verifyExplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there are multiple migration aware apps, including the configured app which
     * is installed on the foreground user but has multiple broadcast receivers handling the
     * required intent.
     */
    @Test
    public void
            testBroadcast_multipleAppIncludingConfiguredAppMultipleReceivers_implicitBroadcastSent()
                    throws Exception {
        ArrayList<PackageInfo> packageInfoArray =
                createPackageInfoArray(MOCK_CONFIGURED_PACKAGE, MOCK_UNCONFIGURED_PACKAGE_ONE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new PackageInfo());
        when(mUserManager.isUserForeground()).thenReturn(true);
        List<ResolveInfo> resolveInfoList =
                MigrationTestUtils.createResolveInfoList(
                        true,
                        MOCK_CONFIGURED_PACKAGE,
                        MOCK_QUERIED_BROADCAST_RECEIVER_ONE,
                        MOCK_QUERIED_BROADCAST_RECEIVER_TWO);
        setQueryBroadcastReceiversAsUserResult(resolveInfoList);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyImplicitBroadcastInvocation(times(1));
        verifyExplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there are multiple migration aware apps, including the configured app which
     * is installed on the given user, but is not the foreground user.
     */
    @Test
    public void testBroadcast_multipleAppsIncludingConfiguredAppUserNotForeground_noBroadcastSent()
            throws Exception {
        ArrayList<PackageInfo> packageInfoArray =
                createPackageInfoArray(MOCK_CONFIGURED_PACKAGE, MOCK_UNCONFIGURED_PACKAGE_ONE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(new PackageInfo());
        when(mUserManager.isUserForeground()).thenReturn(false);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyExplicitBroadcastInvocation(never());
        verifyImplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there are multiple migration aware apps, including the configured app but it
     * is not installed on the given user.
     */
    @Test
    public void testBroadcast_multipleAppsIncludingConfiguredAppNotInstalledOnUser_noBroadcastSent()
            throws Exception {
        ArrayList<PackageInfo> packageInfoArray =
                createPackageInfoArray(MOCK_CONFIGURED_PACKAGE, MOCK_UNCONFIGURED_PACKAGE_ONE);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);
        when(mUserContextPackageManager.getPackageInfo(
                        anyString(), argThat(flag -> (flag.getValue() == 0))))
                .thenThrow(PackageManager.NameNotFoundException.class);

        mMigrationBroadcast.sendInvocationBroadcast();

        verifyExplicitBroadcastInvocation(never());
        verifyImplicitBroadcastInvocation(never());
    }

    /**
     * Tests case where there are multiple migration aware apps but the configured app is not one of
     * them.
     */
    @Test(expected = Exception.class)
    public void testBroadcast_multipleAppsExcludingConfiguredApp_exceptionThrown()
            throws Exception {
        ArrayList<PackageInfo> packageInfoArray =
                createPackageInfoArray(
                        MOCK_UNCONFIGURED_PACKAGE_ONE, MOCK_UNCONFIGURED_PACKAGE_TWO);
        when(mPackageManager.getPackagesHoldingPermissions(
                        eq(PERMISSIONS_TO_CHECK), argThat(flag -> (flag.getValue() == 0))))
                .thenReturn(packageInfoArray);
        MigrationTestUtils.setResolveActivityResult(new ResolveInfo(), mPackageManager);

        try {
            mMigrationBroadcast.sendInvocationBroadcast();
        } finally {
            verifyExplicitBroadcastInvocation(never());
            verifyImplicitBroadcastInvocation(never());
        }
    }

    private ArrayList<PackageInfo> createPackageInfoArray(String... packageNames) {
        ArrayList<PackageInfo> packageInfoArray = new ArrayList<PackageInfo>();
        for (String packageName : packageNames) {
            PackageInfo packageInfo = new PackageInfo();
            packageInfo.packageName = packageName;
            packageInfoArray.add(packageInfo);
        }
        return packageInfoArray;
    }

    private void setQueryBroadcastReceiversAsUserResult(List<ResolveInfo> result) {
        when(mPackageManager.queryBroadcastReceiversAsUser(
                        argThat(
                                intent ->
                                        (HealthConnectManager.ACTION_HEALTH_CONNECT_MIGRATION_READY
                                                .equals(intent.getAction()))),
                        argThat(flag -> (flag.getValue() == PackageManager.MATCH_ALL)),
                        eq(mUser)))
                .thenReturn(result);
    }

    private void verifyExplicitBroadcastInvocation(VerificationMode verificationMode) {
        String mockedClassName = MOCK_CONFIGURED_PACKAGE + MOCK_QUERIED_BROADCAST_RECEIVER_ONE;

        verify(mContext, verificationMode)
                .sendBroadcastAsUser(
                        argThat(
                                intent ->
                                        (HealthConnectManager.ACTION_HEALTH_CONNECT_MIGRATION_READY
                                                        .equals(intent.getAction())
                                                && (MOCK_CONFIGURED_PACKAGE.equals(
                                                        intent.getPackage()))
                                                && (intent.getComponent() != null)
                                                && (MOCK_CONFIGURED_PACKAGE.equals(
                                                        intent.getComponent().getPackageName()))
                                                && (mockedClassName.equals(
                                                        intent.getComponent().getClassName())))),
                        eq(mUser));
    }

    private void verifyImplicitBroadcastInvocation(VerificationMode verificationMode) {
        verify(mContext, verificationMode)
                .sendBroadcastAsUser(
                        argThat(
                                intent ->
                                        (HealthConnectManager.ACTION_HEALTH_CONNECT_MIGRATION_READY
                                                        .equals(intent.getAction())
                                                && (MOCK_CONFIGURED_PACKAGE.equals(
                                                        intent.getPackage()))
                                                && (intent.getComponent() == null))),
                        eq(mUser));
    }
}
