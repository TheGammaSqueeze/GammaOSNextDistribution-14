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

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.health.connect.Constants;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.Slog;

import com.android.modules.utils.BackgroundThread;

import java.util.Objects;

/**
 * A listener for package changes that routes the action to Migration State manager on app install,
 * change, or uninstall.
 *
 * @hide
 */
public class MigratorPackageChangesReceiver extends BroadcastReceiver {
    private static final String TAG = "MigratorPackageChangesReceiver";
    private static final IntentFilter sPackageFilter = buildPackageChangeFilter();
    private final MigrationStateManager mMigrationStateManager;

    public MigratorPackageChangesReceiver(MigrationStateManager migrationStateManager) {
        mMigrationStateManager = migrationStateManager;
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        String packageName = getPackageName(intent);
        UserHandle userHandle = getUserHandle(intent);
        if (packageName == null || userHandle == null) {
            Log.w(TAG, "Can't extract info from the input intent");
            return;
        }

        Context userContext = context.createContextAsUser(userHandle, 0);
        UserManager userManager =
                Objects.requireNonNull(userContext.getSystemService(UserManager.class));
        if (!userManager.isUserForeground()) {
            if (Constants.DEBUG) {
                Slog.d(TAG, "User " + userHandle + " is not a foreground user.");
            }
            return;
        }

        if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)
                && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            // package deleted
            mMigrationStateManager.onPackageRemoved(context, packageName);
        }

        if (intent.getAction().equals(Intent.ACTION_PACKAGE_CHANGED)
                || intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
            // package install
            mMigrationStateManager.onPackageInstalledOrChanged(context, packageName);
        }
    }

    /**
     * Register broadcast receiver to track package changes.
     *
     * @hide
     */
    public void registerBroadcastReceiver(@NonNull Context context) {
        context.registerReceiverForAllUsers(
                this, sPackageFilter, null, BackgroundThread.getHandler());
    }

    @NonNull
    private static IntentFilter buildPackageChangeFilter() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme(/* scheme= */ "package");
        return filter;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @NonNull
    private String getPackageName(@NonNull Intent intent) {
        Uri uri = intent.getData();
        return uri != null ? uri.getSchemeSpecificPart() : null;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    @NonNull
    private UserHandle getUserHandle(@NonNull Intent intent) {
        final int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
        if (uid >= 0) {
            return UserHandle.getUserHandleForUid(uid);
        } else {
            Log.w(TAG, "UID extra is missing from intent");
            return null;
        }
    }
}
