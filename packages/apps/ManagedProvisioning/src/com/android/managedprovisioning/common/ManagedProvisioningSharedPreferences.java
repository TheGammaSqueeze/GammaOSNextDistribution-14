/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.common;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.VisibleForTesting;

/**
 * Default implementation of {@link com.android.managedprovisioning.common.SharedPreferences}.
 */
public class ManagedProvisioningSharedPreferences implements
        com.android.managedprovisioning.common.SharedPreferences {
    public static final long DEFAULT_PROVISIONING_ID = 0L;

    @VisibleForTesting
    static final String KEY_PROVISIONING_ID = "provisioning_id";

    @VisibleForTesting
    static final String KEY_PROVISIONING_START_TIMESTAMP = "provisioning_start_timestamp";

    private static final String KEY_NAVIGATION_BAR_BACKGROUND_COLOR =
            "navigation_bar_background_color";
    private static final String KEY_NAVIGATION_BAR_DIVIDER_COLOR =
            "navigation_bar_divider_color";
    private static final String KEY_TEXT_PRIMARY_COLOR = "text_primary_color";
    private static final String KEY_TEXT_SECONDARY_COLOR = "text_secondary_color";
    private static final String KEY_BACKGROUND_COLOR = "background_color";
    private static final String KEY_NOTIFICATION_BACKGROUND_COLOR = "notification_background_color";

    @VisibleForTesting
    static final String SHARED_PREFERENCE = "managed_profile_shared_preferences";

    /**
     * It's a process-wise in-memory write lock. No other processes will write the same file.
     */
    private static final Object sWriteLock = new Object();
    private static final String KEY_ACCENT_COLOR = "accent_color";
    private static final String KEY_IS_PROVISIONING_FLOW_DELEGATED_TO_ROLE_HOLDER =
            "is_provisioning_flow_delegated_to_role_holder";
    private static final String KEY_IS_ESTABLISH_NETWORK_CONNECTION_RUN =
            "is_establish_network_connection_run";

    private final SharedPreferences mSharedPreferences;

    public ManagedProvisioningSharedPreferences(Context context) {
        mSharedPreferences = context.getSharedPreferences(SHARED_PREFERENCE, Context.MODE_PRIVATE);
    }

    @VisibleForTesting
    public long getProvisioningId() {
        return mSharedPreferences.getLong(KEY_PROVISIONING_ID, DEFAULT_PROVISIONING_ID);
    }

    /**
     * Can assume the id is unique across all provisioning sessions
     * @return a new provisioning id by incrementing the current id
     */
    public long incrementAndGetProvisioningId() {
        synchronized (sWriteLock) {
            long provisioningId = getProvisioningId();
            provisioningId++;
            // commit synchronously
            mSharedPreferences.edit().putLong(KEY_PROVISIONING_ID, provisioningId).commit();
            return provisioningId;
        }
    }

    /**
     * @param time the provisioning started timestamp, in milliseconds
     */
    public void writeProvisioningStartedTimestamp(long time) {
        mSharedPreferences.edit()
                .putLong(KEY_PROVISIONING_START_TIMESTAMP, time)
                .apply();
    }

    /**
     * @return the provisioning started timestamp, in milliseconds
     */
    public long getProvisioningStartedTimestamp() {
        return mSharedPreferences.getLong(KEY_PROVISIONING_START_TIMESTAMP, 0L);
    }

    /**
     * Writes the navigation bar color
     */
    public void writeNavigationBarColor(int color) {
        mSharedPreferences.edit()
                .putInt(KEY_NAVIGATION_BAR_BACKGROUND_COLOR, color)
                .apply();
    }

    /**
     * Returns the navigation bar color
     */
    public int getNavigationBarColor() {
        return mSharedPreferences.getInt(KEY_NAVIGATION_BAR_BACKGROUND_COLOR, 0);
    }

    /**
     * Writes the navigation bar divider color
     */
    public void writeNavigationBarDividerColor(int color) {
        mSharedPreferences.edit()
                .putInt(KEY_NAVIGATION_BAR_DIVIDER_COLOR, color)
                .apply();
    }

    /**
     * Returns the navigation bar divider color
     */
    public int getNavigationBarDividerColor() {
        return mSharedPreferences.getInt(KEY_NAVIGATION_BAR_DIVIDER_COLOR, 0);
    }

    /**
     * Writes the text primary color
     */
    public void writeTextPrimaryColor(int color) {
        mSharedPreferences.edit()
                .putInt(KEY_TEXT_PRIMARY_COLOR, color)
                .apply();
    }

    /**
     * Returns the text primary color
     */
    public int getTextPrimaryColor() {
        return mSharedPreferences.getInt(KEY_TEXT_PRIMARY_COLOR, 0);
    }

    /**
     * Writes the text secondary color
     */
    public void writeTextSecondaryColor(int color) {
        mSharedPreferences.edit()
                .putInt(KEY_TEXT_SECONDARY_COLOR, color)
                .apply();
    }

    /**
     * Returns the text secondary color
     */
    public int getTextSecondaryColor() {
        return mSharedPreferences.getInt(KEY_TEXT_SECONDARY_COLOR, 0);
    }

    /**
     * Writes the theme background color
     */
    public void writeBackgroundColor(int color) {
        mSharedPreferences.edit()
                .putInt(KEY_BACKGROUND_COLOR, color)
                .apply();
    }

    /**
     * Returns the theme background color
     */
    public int getBackgroundColor() {
        return mSharedPreferences.getInt(KEY_BACKGROUND_COLOR, 0);
    }

    /**
     * Writes the theme accent color
     */
    public void writeAccentColor(int color) {
        mSharedPreferences.edit()
                .putInt(KEY_ACCENT_COLOR, color)
                .apply();
    }

    /**
     * Returns the theme accent color
     */
    public int getAccentColor() {
        return mSharedPreferences.getInt(KEY_ACCENT_COLOR, 0);
    }

    /**
     * Writes the notification background color
     */
    public void writeNotificationBackgroundColor(int color) {
        mSharedPreferences.edit()
                .putInt(KEY_NOTIFICATION_BACKGROUND_COLOR, color)
                .apply();
    }

    /**
     * Returns the notification background color
     */
    public int getNotificationBackgroundColor() {
        return mSharedPreferences.getInt(KEY_NOTIFICATION_BACKGROUND_COLOR, 0);
    }

    @Override
    public void setIsProvisioningFlowDelegatedToRoleHolder(boolean value) {
        mSharedPreferences.edit()
                .putBoolean(KEY_IS_PROVISIONING_FLOW_DELEGATED_TO_ROLE_HOLDER, value)
                .apply();
    }

    @Override
    public boolean isProvisioningFlowDelegatedToRoleHolder() {
        return mSharedPreferences.getBoolean(
                KEY_IS_PROVISIONING_FLOW_DELEGATED_TO_ROLE_HOLDER,
                /* defaultValue= */ false);
    }

    @Override
    public void setIsEstablishNetworkConnectionRun(boolean value) {
        mSharedPreferences.edit()
                .putBoolean(KEY_IS_ESTABLISH_NETWORK_CONNECTION_RUN, value)
                .apply();
    }

    @Override
    public boolean isEstablishNetworkConnectionRun() {
        return mSharedPreferences.getBoolean(
                KEY_IS_ESTABLISH_NETWORK_CONNECTION_RUN,
                /* defaultValue= */ false);
    }
}
