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

package com.android.safetycenter.data;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.safetycenter.SafetyCenterEntry;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.SafetySourceStatus;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;
import com.android.safetycenter.PendingIntentFactory;
import com.android.safetycenter.SafetyCenterFlags;

import java.util.List;

/**
 * A class to work around an issue with the {@code AndroidLockScreen} safety source, by potentially
 * overriding its {@link SafetySourceData}.
 */
final class AndroidLockScreenFix {

    private static final String TAG = "AndroidLockScreenFix";

    private static final String ANDROID_LOCK_SCREEN_SOURCE_ID = "AndroidLockScreen";
    private static final int SUSPECT_REQ_CODE = 0;
    // Arbitrary values to construct PendingIntents that are guaranteed not to be equal due to
    // these request codes not being equal. The values match the ones in Settings QPR, in case we
    // ever end up using these request codes in QPR.
    private static final int ANDROID_LOCK_SCREEN_ENTRY_REQ_CODE = 1;
    private static final int ANDROID_LOCK_SCREEN_ICON_ACTION_REQ_CODE = 2;

    private AndroidLockScreenFix() {}

    static boolean shouldApplyFix(String sourceId) {
        if (SdkLevel.isAtLeastU()) {
            // No need to override on U+ as the issue has been fixed in a T QPR release.
            // As such, U+ fields for the SafetySourceData are not taken into account in the methods
            // below.
            return false;
        }
        if (!ANDROID_LOCK_SCREEN_SOURCE_ID.equals(sourceId)) {
            return false;
        }
        return SafetyCenterFlags.getReplaceLockScreenIconAction();
    }

    /**
     * Overrides the {@link SafetySourceData} of the {@code AndroidLockScreen} source by replacing
     * its {@link PendingIntent}s.
     *
     * <p>This is done because of a bug in the Settings app where the {@link PendingIntent}s created
     * end up referencing either the {@link SafetyCenterEntry#getPendingIntent()} or the {@link
     * SafetyCenterEntry.IconAction#getPendingIntent()}. The reason for this is that {@link
     * PendingIntent} instances are cached and keyed by an object which does not take into account
     * the underlying {@link Intent} extras; and these two {@link Intent}s only differ by the extras
     * that they set.
     *
     * <p>We fix this issue by recreating the desired {@link PendingIntent}s manually here, using
     * different request codes for the different {@link PendingIntent}s to ensure new instances are
     * created (the key does take into account the request code).
     */
    static SafetySourceData applyFix(Context context, SafetySourceData data) {
        SafetySourceData.Builder overriddenData =
                SafetySourceDataOverrides.copyDataToBuilderWithoutIssues(data);

        SafetySourceStatus originalStatus = data.getStatus();
        if (originalStatus != null) {
            overriddenData.setStatus(overrideTiramisuSafetySourceStatus(context, originalStatus));
        }

        List<SafetySourceIssue> issues = data.getIssues();
        for (int i = 0; i < issues.size(); i++) {
            overriddenData.addIssue(overrideTiramisuIssue(context, issues.get(i)));
        }

        return overriddenData.build();
    }

    private static SafetySourceStatus overrideTiramisuSafetySourceStatus(
            Context context, SafetySourceStatus status) {
        SafetySourceStatus.Builder overriddenStatus =
                SafetySourceDataOverrides.copyStatusToBuilder(status);

        PendingIntent originalPendingIntent = status.getPendingIntent();
        if (originalPendingIntent != null) {
            overriddenStatus.setPendingIntent(
                    overridePendingIntent(
                            context, originalPendingIntent, /* isIconAction= */ false));
        }

        SafetySourceStatus.IconAction iconAction = status.getIconAction();
        if (iconAction != null) {
            overriddenStatus.setIconAction(
                    overrideTiramisuIconAction(context, status.getIconAction()));
        }

        return overriddenStatus.build();
    }

    private static SafetySourceStatus.IconAction overrideTiramisuIconAction(
            Context context, SafetySourceStatus.IconAction iconAction) {
        return new SafetySourceStatus.IconAction(
                iconAction.getIconType(),
                overridePendingIntent(
                        context, iconAction.getPendingIntent(), /* isIconAction= */ true));
    }

    private static SafetySourceIssue overrideTiramisuIssue(
            Context context, SafetySourceIssue issue) {
        SafetySourceIssue.Builder overriddenIssue =
                SafetySourceDataOverrides.copyIssueToBuilderWithoutActions(issue);

        List<SafetySourceIssue.Action> actions = issue.getActions();
        for (int i = 0; i < actions.size(); i++) {
            SafetySourceIssue.Action action = actions.get(i);
            overriddenIssue.addAction(overrideTiramisuIssueAction(context, action));
        }

        return overriddenIssue.build();
    }

    private static SafetySourceIssue.Action overrideTiramisuIssueAction(
            Context context, SafetySourceIssue.Action action) {
        PendingIntent pendingIntent =
                overridePendingIntent(
                        context, action.getPendingIntent(), /* isIconAction= */ false);
        return SafetySourceDataOverrides.overrideActionPendingIntent(action, pendingIntent);
    }

    private static PendingIntent overridePendingIntent(
            Context context, PendingIntent pendingIntent, boolean isIconAction) {
        String settingsPackageName = pendingIntent.getCreatorPackage();
        int userId = pendingIntent.getCreatorUserHandle().getIdentifier();
        Context settingsPackageContext =
                PendingIntentFactory.createPackageContextAsUser(
                        context, settingsPackageName, userId);
        if (settingsPackageContext == null) {
            return pendingIntent;
        }
        if (hasFixedSettingsIssue(settingsPackageContext)) {
            return pendingIntent;
        }
        PendingIntent suspectPendingIntent =
                PendingIntentFactory.getNullableActivityPendingIntent(
                        settingsPackageContext,
                        SUSPECT_REQ_CODE,
                        newBaseLockScreenIntent(settingsPackageName),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (suspectPendingIntent == null) {
            // Nothing was cached.
            return pendingIntent;
        }
        if (!suspectPendingIntent.equals(pendingIntent)) {
            // The pending intent is not hitting this caching issue, so we should skip the override.
            return pendingIntent;
        }
        // We’re most likely hitting the caching issue described in this method’s documentation, so
        // we should ensure we create brand new pending intents where applicable by using different
        // request codes. We only perform this override for the applicable pending intents.
        // This is important because there are scenarios where the Settings app provides different
        // pending intents (e.g. in the work profile), and in this case we shouldn't override them.
        if (isIconAction) {
            Log.i(
                    TAG,
                    "Replacing " + ANDROID_LOCK_SCREEN_SOURCE_ID + " icon action pending intent");
            return PendingIntentFactory.getActivityPendingIntent(
                    settingsPackageContext,
                    ANDROID_LOCK_SCREEN_ICON_ACTION_REQ_CODE,
                    newLockScreenIconActionIntent(settingsPackageName),
                    PendingIntent.FLAG_IMMUTABLE);
        }
        Log.i(TAG, "Replacing " + ANDROID_LOCK_SCREEN_SOURCE_ID + " entry or issue pending intent");
        return PendingIntentFactory.getActivityPendingIntent(
                settingsPackageContext,
                ANDROID_LOCK_SCREEN_ENTRY_REQ_CODE,
                newLockScreenIntent(settingsPackageName),
                PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean hasFixedSettingsIssue(Context settingsPackageContext) {
        Resources settingsResources = settingsPackageContext.getResources();
        int hasSettingsFixedIssueResourceId =
                settingsResources.getIdentifier(
                        "config_isSafetyCenterLockScreenPendingIntentFixed",
                        "bool",
                        settingsPackageContext.getPackageName());
        if (hasSettingsFixedIssueResourceId != Resources.ID_NULL) {
            return settingsResources.getBoolean(hasSettingsFixedIssueResourceId);
        }
        return false;
    }

    private static Intent newBaseLockScreenIntent(String settingsPackageName) {
        return new Intent(Intent.ACTION_MAIN)
                .setComponent(
                        new ComponentName(
                                settingsPackageName, settingsPackageName + ".SubSettings"))
                .putExtra(":settings:source_metrics", 1917);
    }

    private static Intent newLockScreenIntent(String settingsPackageName) {
        String targetFragment =
                settingsPackageName + ".password.ChooseLockGeneric$ChooseLockGenericFragment";
        return newBaseLockScreenIntent(settingsPackageName)
                .putExtra(":settings:show_fragment", targetFragment)
                .putExtra("page_transition_type", 1);
    }

    private static Intent newLockScreenIconActionIntent(String settingsPackageName) {
        String targetFragment = settingsPackageName + ".security.screenlock.ScreenLockSettings";
        return newBaseLockScreenIntent(settingsPackageName)
                .putExtra(":settings:show_fragment", targetFragment)
                .putExtra("page_transition_type", 0);
    }
}
