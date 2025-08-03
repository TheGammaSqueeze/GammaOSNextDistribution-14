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

import static android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_ERROR;
import static android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_MIGRATOR_DISABLED;
import static android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_NOT_STARTED;
import static android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_ALLOWED_PAUSED;
import static android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_APP_UPGRADE_REQUIRED;
import static android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_COMPLETE;
import static android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_COMPLETE_IDLE;
import static android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_IDLE;
import static android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_IN_PROGRESS;
import static android.health.connect.migration.HealthConnectMigrationUiState.MIGRATION_UI_STATE_MODULE_UPGRADE_REQUIRED;

import android.annotation.NonNull;
import android.content.Context;
import android.health.connect.HealthConnectDataState;
import android.health.connect.migration.HealthConnectMigrationUiState;
import android.os.UserHandle;

import com.android.server.healthconnect.migration.notification.MigrationNotificationSender;

/**
 * Sends the appropriate notification based on the migration state, and returns the UI state to the
 * caller.
 *
 * @hide
 */
public class MigrationUiStateManager {

    private final Context mContext;
    private volatile UserHandle mUserHandle;
    private final MigrationNotificationSender mMigrationNotificationSender;
    private final MigrationStateManager mMigrationStateManager;
    private static final String TAG = "MigrationUiStateManager";

    public MigrationUiStateManager(
            @NonNull Context context,
            @NonNull UserHandle userHandle,
            @NonNull MigrationStateManager migrationStateManager,
            @NonNull MigrationNotificationSender migrationNotificationSender) {
        this.mContext = context;
        this.mUserHandle = userHandle;
        this.mMigrationNotificationSender = migrationNotificationSender;
        this.mMigrationStateManager = migrationStateManager;
    }

    /** Assigns a new user handle to this object. */
    public void setUserHandle(@NonNull UserHandle userHandle) {
        this.mUserHandle = userHandle;
    }

    /** Attaches this MigrationUiStateManager to the provided {@link MigrationStateManager}. */
    public void attachTo(@NonNull MigrationStateManager migrationStateManager) {
        migrationStateManager.addStateChangedListener(this::onMigrationStateChanged);
    }

    /** Returns the current Migration UI state. */
    @HealthConnectMigrationUiState.Type
    public int getHealthConnectMigrationUiState() {
        // get current migration state
        @HealthConnectDataState.DataMigrationState
        int migrationState = mMigrationStateManager.getMigrationState();

        switch (migrationState) {
            case HealthConnectDataState.MIGRATION_STATE_IDLE:
                return MIGRATION_UI_STATE_IDLE;

            case HealthConnectDataState.MIGRATION_STATE_APP_UPGRADE_REQUIRED:
                return MIGRATION_UI_STATE_APP_UPGRADE_REQUIRED;

            case HealthConnectDataState.MIGRATION_STATE_MODULE_UPGRADE_REQUIRED:
                return MIGRATION_UI_STATE_MODULE_UPGRADE_REQUIRED;

            case HealthConnectDataState.MIGRATION_STATE_ALLOWED:
                if (isUiStateAllowedPaused()) {
                    return MIGRATION_UI_STATE_ALLOWED_NOT_STARTED;
                } else if (isMigratorDisabled()) {
                    return MIGRATION_UI_STATE_ALLOWED_ERROR;
                } else if (isUiStateInProgressPaused()) {
                    return MIGRATION_UI_STATE_ALLOWED_PAUSED;
                } else {
                    return MIGRATION_UI_STATE_ALLOWED_MIGRATOR_DISABLED;
                }

            case HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS:
                return MIGRATION_UI_STATE_IN_PROGRESS;

            case HealthConnectDataState.MIGRATION_STATE_COMPLETE:
                if (isUiStateCompleteFromIdle()) {
                    return MIGRATION_UI_STATE_COMPLETE_IDLE;
                } else {
                    return MIGRATION_UI_STATE_COMPLETE;
                }

            default:
                throw new IllegalArgumentException(
                        "Cannot compute migration UI state. Unknown migration state: "
                                + migrationState);
        }
    }

    private void onMigrationStateChanged(@HealthConnectDataState.DataMigrationState int newState) {

        @HealthConnectMigrationUiState.Type
        int migrationUiState = getHealthConnectMigrationUiState();

        switch (migrationUiState) {
            case MIGRATION_UI_STATE_ALLOWED_PAUSED:
                mMigrationNotificationSender.sendNotification(
                        MigrationNotificationSender.NOTIFICATION_TYPE_MIGRATION_PAUSED,
                        mUserHandle);
                break;

            case MIGRATION_UI_STATE_MODULE_UPGRADE_REQUIRED:
                mMigrationNotificationSender.sendNotification(
                        MigrationNotificationSender
                                .NOTIFICATION_TYPE_MIGRATION_MODULE_UPDATE_NEEDED,
                        mUserHandle);
                break;
            default:
                mMigrationNotificationSender.clearNotifications(mUserHandle);
                break;
        }
    }

    private boolean isUiStateCompleteFromIdle() {
        return mMigrationStateManager.hasIdleStateTimedOut();
    }

    private boolean isUiStateAllowedPaused() {
        // Migrator not responding to broadcast before migration started
        int migrationStartsCount = mMigrationStateManager.getMigrationStartsCount();
        boolean isApkInstalled =
                mMigrationStateManager.existsMigrationAwarePackage(mContext)
                        && mMigrationStateManager.existsMigratorPackage(mContext);
        boolean doesMigratorHandleInfoIntent =
                mMigrationStateManager.doesMigratorHandleInfoIntent(mContext);

        return migrationStartsCount == 0 && isApkInstalled && doesMigratorHandleInfoIntent;
    }

    private boolean isMigratorDisabled() {
        int migrationStartsCount = mMigrationStateManager.getMigrationStartsCount();
        boolean doesMigratorHandleInfoIntent =
                mMigrationStateManager.doesMigratorHandleInfoIntent(mContext);

        return migrationStartsCount > 0 && !doesMigratorHandleInfoIntent;
    }

    private boolean isUiStateInProgressPaused() {
        int migrationStartsCount = mMigrationStateManager.getMigrationStartsCount();
        boolean doesMigratorHandleInfoIntent =
                mMigrationStateManager.doesMigratorHandleInfoIntent(mContext);
        boolean hasInProgressStateTimedOut = mMigrationStateManager.hasInProgressStateTimedOut();

        return migrationStartsCount > 0
                && doesMigratorHandleInfoIntent
                && hasInProgressStateTimedOut;
    }
}
