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

package com.android.devicelockcontroller.storage;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ALLOWLIST;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_APP_PROVIDER_NAME;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DISABLE_OUTGOING_CALLS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_MANDATORY_PROVISION;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_PROVISIONING_TYPE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_SUPPORT_URL;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_TERMS_AND_CONDITIONS_URL;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType.TYPE_UNDEFINED;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.ArraySet;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.collect.ImmutableList;

import java.util.Locale;
import java.util.Set;

/**
 * Stores setup parameters.
 * <p>
 * Note that these parameters are created by the system user at the setup time and must not be
 * written afterwards.
 * <p>
 * Also, these parameters are accessed globally by all users and must be accessed all the time via
 * the {@link SetupParametersClient}.
 */
final class SetupParameters {
    private static final String TAG = "SetupParameters";
    private static final String FILENAME = "setup-prefs";
    private static final String KEY_KIOSK_PACKAGE = "kiosk-package-name";
    private static final String KEY_KIOSK_ALLOWLIST = "kiosk-allowlist";
    private static final String KEY_KIOSK_DISABLE_OUTGOING_CALLS =
            "kiosk-disable-outgoing-calls";
    private static final String KEY_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE =
            "kiosk-enable-notifications-in-lock-task-mode";
    private static final String KEY_PROVISIONING_TYPE = "provisioning-type";
    private static final String KEY_MANDATORY_PROVISION = "mandatory-provision";
    private static final String KEY_KIOSK_APP_PROVIDER_NAME = "kiosk-app-provider-name";
    private static final String KEY_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES =
            "disallow-installing-from-unknown-sources";
    private static final String KEY_TERMS_AND_CONDITIONS_URL =
            "terms-and-conditions-url";
    private static final String KEY_SUPPORT_URL = "support-url";

    private SetupParameters() {
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        Context deviceContext = context.createDeviceProtectedStorageContext();
        return deviceContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE);
    }

    // Note that this API is only used for debugging purpose and should only be called in
    // debuggable build.
    static synchronized void overridePrefs(Context context, Bundle bundle) {
        if (!Build.isDebuggable()) {
            throw new SecurityException(
                    "Setup parameters is not allowed to be override in non-debuggable build!");
        }
        populatePreferencesLocked(getSharedPreferences(context), bundle);
        dumpParameters(context);
    }

    static void dumpParameters(Context context) {
        LogUtil.d(TAG, String.format(Locale.US,
                "Dumping SetupParameters ...\n"
                + "%s: %s\n"    // kiosk-package-name:
                + "%s: %s\n"    // kiosk-allowlist:
                + "%s: %s\n"    // kiosk-disable-outgoing-calls:
                + "%s: %s\n"    // kiosk-enable-notifications-in-lock-task-mode:
                + "%s: %d\n"    // provisioning-type:
                + "%s: %s\n"    // mandatory-provision:
                + "%s: %s\n"    // kiosk-app-provider-name:
                + "%s: %s\n"    // disallow-installing-from-unknown-sources:
                + "%s: %s\n"    // terms-and-conditions-url:
                + "%s: %s\n",   // support-url:
                KEY_KIOSK_PACKAGE, getKioskPackage(context),
                KEY_KIOSK_ALLOWLIST, getKioskAllowlist(context),
                KEY_KIOSK_DISABLE_OUTGOING_CALLS, getOutgoingCallsDisabled(context),
                KEY_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE,
                isNotificationsInLockTaskModeEnabled(context),
                KEY_PROVISIONING_TYPE, getProvisioningType(context),
                KEY_MANDATORY_PROVISION, isProvisionMandatory(context),
                KEY_KIOSK_APP_PROVIDER_NAME, getKioskAppProviderName(context),
                KEY_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES,
                isInstallingFromUnknownSourcesDisallowed(context),
                KEY_TERMS_AND_CONDITIONS_URL, getTermsAndConditionsUrl(context),
                KEY_SUPPORT_URL, getSupportUrl(context)
        ));
    }

    /**
     * Parse setup parameters from the extras bundle.
     *
     * @param context Application context
     * @param bundle  Bundle with provisioning parameters.
     */
    static synchronized void createPrefs(Context context, Bundle bundle) {
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        if (sharedPreferences.contains(KEY_KIOSK_PACKAGE)) {
            LogUtil.i(TAG, "Setup parameters are already populated");

            return;
        }
        populatePreferencesLocked(sharedPreferences, bundle);
    }

    private static void populatePreferencesLocked(SharedPreferences sharedPreferences,
            Bundle bundle) {

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_KIOSK_PACKAGE, bundle.getString(EXTRA_KIOSK_PACKAGE));
        editor.putBoolean(KEY_KIOSK_DISABLE_OUTGOING_CALLS,
                bundle.getBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS));
        editor.putBoolean(KEY_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE,
                bundle.getBoolean(EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE));
        editor.putStringSet(KEY_KIOSK_ALLOWLIST,
                new ArraySet<>(bundle.getStringArrayList(EXTRA_KIOSK_ALLOWLIST)));
        editor.putInt(KEY_PROVISIONING_TYPE, bundle.getInt(EXTRA_PROVISIONING_TYPE));
        editor.putBoolean(KEY_MANDATORY_PROVISION, bundle.getBoolean(EXTRA_MANDATORY_PROVISION));
        editor.putString(KEY_KIOSK_APP_PROVIDER_NAME,
                bundle.getString(EXTRA_KIOSK_APP_PROVIDER_NAME));
        editor.putBoolean(KEY_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES,
                bundle.getBoolean(EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES));
        editor.putString(KEY_TERMS_AND_CONDITIONS_URL,
                bundle.getString(EXTRA_TERMS_AND_CONDITIONS_URL));
        editor.putString(KEY_SUPPORT_URL, bundle.getString(EXTRA_SUPPORT_URL));
        editor.apply();
    }

    /**
     * Get the name of the package implementing the kiosk app.
     *
     * @param context Context used to get the shared preferences.
     * @return kiosk app package name.
     */
    @Nullable
    static String getKioskPackage(Context context) {
        return getSharedPreferences(context).getString(KEY_KIOSK_PACKAGE, null /* defValue */);
    }

    /**
     * Check if the configuration disables outgoing calls.
     *
     * @param context Context used to get the shared preferences.
     * @return True if outgoing calls are disabled.
     */
    static boolean getOutgoingCallsDisabled(Context context) {
        return getSharedPreferences(context)
                .getBoolean(KEY_KIOSK_DISABLE_OUTGOING_CALLS, false /* defValue */);
    }

    /**
     * Get package allowlist provisioned by the server.
     *
     * @param context Context used to get the shared preferences.
     * @return List of allowed packages.
     */
    static ImmutableList<String> getKioskAllowlist(Context context) {
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        Set<String> allowlistSet =
                sharedPreferences.getStringSet(KEY_KIOSK_ALLOWLIST, null /* defValue */);
        return allowlistSet == null ? ImmutableList.of() : ImmutableList.copyOf(allowlistSet);
    }

    /**
     * Check if notifications are enabled in lock task mode.
     *
     * @param context Context used to get the shared preferences.
     * @return True if notification are enabled.
     */
    static boolean isNotificationsInLockTaskModeEnabled(Context context) {
        return getSharedPreferences(context)
                .getBoolean(KEY_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE, false /* defValue */);
    }

    /**
     * Get the provisioning type of this configuration.
     *
     * @param context Context used to get the shared preferences.
     * @return The type of provisioning which could be one of {@link ProvisioningType}.
     */
    @ProvisioningType
    static int getProvisioningType(Context context) {
        return getSharedPreferences(context).getInt(KEY_PROVISIONING_TYPE, TYPE_UNDEFINED);
    }

    /**
     * Check if provision is mandatory.
     *
     * @param context Context used to get the shared preferences.
     * @return True if the provision should be mandatory.
     */
    static boolean isProvisionMandatory(Context context) {
        return getSharedPreferences(context).getBoolean(KEY_MANDATORY_PROVISION, false);
    }

    /**
     * Get the name of the provider of the kiosk app.
     *
     * @param context Context used to get the shared preferences.
     * @return the name of the provider.
     */
    @Nullable
    static String getKioskAppProviderName(Context context) {
        return getSharedPreferences(context).getString(KEY_KIOSK_APP_PROVIDER_NAME,
                null /* defValue */);
    }

    /**
     * Check if installing from unknown sources should be disallowed on this device after provision
     *
     * @param context Context used to get the shared preferences.
     * @return True if installing from unknown sources is disallowed.
     */
    static boolean isInstallingFromUnknownSourcesDisallowed(Context context) {
        return getSharedPreferences(context).getBoolean(
                KEY_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES, /* defValue= */ false);
    }

    /**
     * Get the URL to the terms and conditions of the partner for enrolling in a Device Lock
     * program.
     *
     * @param context Context used to get the shared preferences.
     * @return The URL to the terms and conditions.
     */
    @Nullable
    static String getTermsAndConditionsUrl(Context context) {
        return getSharedPreferences(context).getString(
                KEY_TERMS_AND_CONDITIONS_URL, /* defValue= */ null);
    }

    /**
     * The URL to the support page the user can use to get help.
     *
     * @param context Context used to get the shared preferences.
     * @return The URL to the support page.
     */
    @Nullable
    static String getSupportUrl(Context context) {
        return getSharedPreferences(context).getString(
                KEY_SUPPORT_URL, /* defValue= */ null);
    }

    static void clear(Context context) {
        if (!Build.isDebuggable()) {
            throw new SecurityException("Clear is not allowed in non-debuggable build!");
        }
        getSharedPreferences(context).edit().clear().commit();
    }
}
