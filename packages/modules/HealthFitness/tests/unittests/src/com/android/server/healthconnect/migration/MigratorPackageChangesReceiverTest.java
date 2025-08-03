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

import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_CHANGED;
import static android.content.Intent.ACTION_PACKAGE_REMOVED;

import static com.android.server.healthconnect.migration.MigrationTestUtils.MOCK_CONFIGURED_PACKAGE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
public class MigratorPackageChangesReceiverTest {
    public static final String INTENT_URI_SCHEME = "package";

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(PreferenceHelper.class)
                    .mockStatic(MigrationStateManager.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Mock MigrationStateManager mMigrationStateManager;
    @Mock PreferenceHelper mPreferenceHelper;
    @Mock private Context mContext;
    @Mock private Context mUserContext;
    @Mock private UserManager mUserManager;
    @Mock private Intent mIntent;
    @Mock private Uri mUri;
    private MigratorPackageChangesReceiver mMigratorPackageChangesReceiver;
    private static final UserHandle DEFAULT_USER_HANDLE = UserHandle.of(UserHandle.myUserId());

    @Before
    public void setUp() {
        when(MigrationStateManager.getInitialisedInstance()).thenReturn(mMigrationStateManager);
        when(PreferenceHelper.getInstance()).thenReturn(mPreferenceHelper);
        mMigratorPackageChangesReceiver =
                new MigratorPackageChangesReceiver(mMigrationStateManager);
        when(mContext.createContextAsUser(any(), anyInt())).thenReturn(mUserContext);
        when(mUserContext.getSystemService(eq(UserManager.class))).thenReturn(mUserManager);
        when(mIntent.getData()).thenReturn(mUri);
    }

    @After
    public void tearDown() {
        clearInvocations(mPreferenceHelper);
        clearInvocations(mMigrationStateManager);
    }

    @Test
    public void testPackageAdded_callsOnPackageInstalledOrChanged() {
        when(mUserManager.isUserForeground()).thenReturn(true);
        mMigratorPackageChangesReceiver.onReceive(
                mContext, getIntent(ACTION_PACKAGE_ADDED, MOCK_CONFIGURED_PACKAGE));
        verify(mMigrationStateManager)
                .onPackageInstalledOrChanged(eq(mContext), eq(MOCK_CONFIGURED_PACKAGE));
    }

    @Test
    public void testPackageChanged_callsOnPackageInstalledOrChanged() {
        when(mUserManager.isUserForeground()).thenReturn(true);
        mMigratorPackageChangesReceiver.onReceive(
                mContext, getIntent(ACTION_PACKAGE_CHANGED, MOCK_CONFIGURED_PACKAGE));
        verify(mMigrationStateManager)
                .onPackageInstalledOrChanged(eq(mContext), eq(MOCK_CONFIGURED_PACKAGE));
    }

    @Test
    public void testPackageRemoved_callsOnPackageRemoved() {
        when(mUserManager.isUserForeground()).thenReturn(true);
        mMigratorPackageChangesReceiver.onReceive(
                mContext, getIntent(ACTION_PACKAGE_REMOVED, MOCK_CONFIGURED_PACKAGE));
        verify(mMigrationStateManager).onPackageRemoved(eq(mContext), eq(MOCK_CONFIGURED_PACKAGE));
    }

    @Test
    public void testOnReceive_nullPackage_noMigrationStateManagerCall() {
        when(mUserManager.isUserForeground()).thenReturn(true);
        when(mUri.getSchemeSpecificPart()).thenReturn(null);
        mMigratorPackageChangesReceiver.onReceive(mContext, mIntent);
        verify(mMigrationStateManager, never()).onPackageInstalledOrChanged(any(), any());
        verify(mMigrationStateManager, never()).onPackageRemoved(any(), any());
    }

    @Test
    public void testOnReceive_nullUserHandle_noMigrationStateManagerCall() {
        mMigratorPackageChangesReceiver.onReceive(
                mContext,
                getIntentWithNoUserHandle(ACTION_PACKAGE_REMOVED, MOCK_CONFIGURED_PACKAGE));
        verify(mMigrationStateManager, never()).onPackageInstalledOrChanged(any(), any());
        verify(mMigrationStateManager, never()).onPackageRemoved(any(), any());
    }

    @Test
    public void testOnReceive_notAForegroundUser_noMigrationStateManagerCall() {
        mMigratorPackageChangesReceiver.onReceive(
                mContext, getIntent(ACTION_PACKAGE_CHANGED, MOCK_CONFIGURED_PACKAGE));
        when(mUserManager.isUserForeground()).thenReturn(false);
        verify(mMigrationStateManager, never()).onPackageInstalledOrChanged(any(), any());
        verify(mMigrationStateManager, never()).onPackageRemoved(any(), any());
    }

    private Intent getIntent(String action, String packageName) {
        return new Intent(
                        action, Uri.fromParts(INTENT_URI_SCHEME, packageName, null /* fragment */))
                .putExtra(Intent.EXTRA_UID, DEFAULT_USER_HANDLE.getIdentifier());
    }

    private Intent getIntentWithNoUserHandle(String action, String packageName) {
        return new Intent(
                action, Uri.fromParts(INTENT_URI_SCHEME, packageName, null /* fragment */));
    }
}
