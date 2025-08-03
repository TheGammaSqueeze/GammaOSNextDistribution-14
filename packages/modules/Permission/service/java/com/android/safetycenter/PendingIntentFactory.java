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

package com.android.safetycenter;

import static java.util.Objects.requireNonNull;

import android.annotation.UserIdInt;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ResolveInfoFlags;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.safetycenter.resources.SafetyCenterResourcesApk;

import java.util.Arrays;

/**
 * Helps build or retrieve {@link PendingIntent} instances.
 *
 * @hide
 */
public final class PendingIntentFactory {

    private static final String TAG = "PendingIntentFactory";

    private static final int DEFAULT_REQUEST_CODE = 0;

    private static final String IS_SETTINGS_HOMEPAGE = "is_from_settings_homepage";

    private final Context mContext;
    private final SafetyCenterResourcesApk mSafetyCenterResourcesApk;

    PendingIntentFactory(Context context, SafetyCenterResourcesApk safetyCenterResourcesApk) {
        mContext = context;
        mSafetyCenterResourcesApk = safetyCenterResourcesApk;
    }

    /**
     * Creates or retrieves a {@link PendingIntent} that will start a new {@code Activity} matching
     * the given {@code intentAction}.
     *
     * <p>If the given {@code intentAction} resolves, the {@link PendingIntent} will use an implicit
     * {@link Intent}. Otherwise, the {@link PendingIntent} will explicitly target the {@code
     * packageName} if it resolves.
     *
     * <p>The {@code PendingIntent} is associated with a specific source given by {@code sourceId}.
     *
     * <p>Returns {@code null} if the required {@link PendingIntent} cannot be created or if there
     * is no valid target for the given {@code intentAction}.
     */
    @Nullable
    public PendingIntent getPendingIntent(
            String sourceId,
            @Nullable String intentAction,
            String packageName,
            @UserIdInt int userId,
            boolean isQuietModeEnabled) {
        if (intentAction == null) {
            return null;
        }
        Context packageContext = createPackageContextAsUser(mContext, packageName, userId);
        if (packageContext == null) {
            return null;
        }
        Intent intent = createIntent(packageContext, sourceId, intentAction, isQuietModeEnabled);
        if (intent == null) {
            return null;
        }
        return getActivityPendingIntent(
                packageContext, DEFAULT_REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    @Nullable
    private Intent createIntent(
            Context packageContext,
            String sourceId,
            String intentAction,
            boolean isQuietModeEnabled) {
        Intent intent = new Intent(intentAction);

        if (shouldAddSettingsHomepageExtra(sourceId)) {
            // Identify this intent as coming from Settings. Because this intent is actually coming
            // from Safety Center, which is served by PermissionController, this is useful to
            // indicate that it is presented as part of the Settings app.
            //
            // In particular, the AOSP Settings app uses this to ensure that two-pane mode works
            // correctly.
            intent.putExtra(IS_SETTINGS_HOMEPAGE, true);
            // Given we've added an extra to this intent, set an ID on it to ensure that it is not
            // considered equal to the same intent without the extra. PendingIntents are cached
            // using Intent equality as the key, and we want to make sure the extra is propagated.
            intent.setIdentifier("with_settings_homepage_extra");
        }

        if (intentResolvesToActivity(packageContext, intent)) {
            return intent;
        }

        // If the intent resolves for the package provided, then we make the assumption that it is
        // the desired app and make the intent explicit. This is to workaround implicit internal
        // intents that may not be exported which will stop working on Android U+.
        Intent explicitIntent = new Intent(intent).setPackage(packageContext.getPackageName());
        if (intentResolvesToActivity(packageContext, explicitIntent)) {
            return explicitIntent;
        }

        // resolveActivity does not return any activity when the work profile is in quiet mode, even
        // though it opens the quiet mode dialog and/or the original intent would otherwise resolve
        // when quiet mode is turned off. So, we assume that the explicit intent will always resolve
        // to this dialog. This heuristic is preferable on U+ as it has a higher chance of resolving
        // once the work profile is enabled considering the implicit internal intent restriction.
        if (isQuietModeEnabled) {
            // TODO(b/266538628): Find a way to fix this, this heuristic isn't ideal.
            return explicitIntent;
        }

        return null;
    }

    private boolean shouldAddSettingsHomepageExtra(String sourceId) {
        return Arrays.asList(
                        mSafetyCenterResourcesApk
                                .getStringByName("config_useSettingsHomepageIntentExtra")
                                .split(","))
                .contains(sourceId);
    }

    private static boolean intentResolvesToActivity(Context packageContext, Intent intent) {
        ResolveInfo resolveInfo = resolveActivity(packageContext, intent);
        if (resolveInfo == null) {
            return false;
        }
        ActivityInfo activityInfo = resolveInfo.activityInfo;
        if (activityInfo == null) {
            return false;
        }
        boolean intentIsImplicit = intent.getPackage() == null && intent.getComponent() == null;
        if (intentIsImplicit) {
            return activityInfo.exported;
        }
        return true;
    }

    @Nullable
    private static ResolveInfo resolveActivity(Context packageContext, Intent intent) {
        PackageManager packageManager = packageContext.getPackageManager();
        // This call requires the INTERACT_ACROSS_USERS permission as the `packageContext` could
        // belong to another user.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return packageManager.resolveActivity(intent, ResolveInfoFlags.of(0));
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Creates a {@link PendingIntent} to start an Activity from the given {@code packageContext}.
     *
     * <p>This function can only return {@code null} if the {@link PendingIntent#FLAG_NO_CREATE}
     * flag is passed in.
     */
    @Nullable
    public static PendingIntent getNullableActivityPendingIntent(
            Context packageContext, int requestCode, Intent intent, int flags) {
        // This call requires Binder identity to be cleared for getIntentSender() to be allowed to
        // send as another package.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return PendingIntent.getActivity(packageContext, requestCode, intent, flags);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Creates a {@link PendingIntent} to start an Activity from the given {@code packageContext}.
     *
     * <p>{@code flags} must not include {@link PendingIntent#FLAG_NO_CREATE}
     */
    public static PendingIntent getActivityPendingIntent(
            Context packageContext, int requestCode, Intent intent, int flags) {
        if ((flags & PendingIntent.FLAG_NO_CREATE) != 0) {
            throw new IllegalArgumentException("flags must not include FLAG_NO_CREATE");
        }
        return requireNonNull(
                getNullableActivityPendingIntent(packageContext, requestCode, intent, flags));
    }

    /**
     * Creates a non-protected broadcast {@link PendingIntent} which can only be received by the
     * system. Use this method to create PendingIntents to be received by Context-registered
     * receivers, for example for notification-related callbacks.
     *
     * <p>{@code flags} must include {@link PendingIntent#FLAG_IMMUTABLE} and must not include
     * {@link PendingIntent#FLAG_NO_CREATE}
     */
    public static PendingIntent getNonProtectedSystemOnlyBroadcastPendingIntent(
            Context context, int requestCode, Intent intent, int flags) {
        if ((flags & PendingIntent.FLAG_IMMUTABLE) == 0) {
            throw new IllegalArgumentException("flags must include FLAG_IMMUTABLE");
        }
        if ((flags & PendingIntent.FLAG_NO_CREATE) != 0) {
            throw new IllegalArgumentException("flags must not include FLAG_NO_CREATE");
        }
        intent.setPackage("android");
        // This call is needed to be allowed to send the broadcast as the "android" package.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return PendingIntent.getBroadcast(context, requestCode, intent, flags);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /** Creates a {@link Context} for the given {@code packageName} and {@code userId}. */
    @Nullable
    public static Context createPackageContextAsUser(
            Context context, String packageName, @UserIdInt int userId) {
        // This call requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return context.createPackageContextAsUser(
                    packageName, /* flags= */ 0, UserHandle.of(userId));
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package name " + packageName + " not found", e);
            return null;
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }
}
