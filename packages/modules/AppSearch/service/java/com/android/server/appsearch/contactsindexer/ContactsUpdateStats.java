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

package com.android.server.appsearch.contactsindexer;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.util.ArraySet;

import com.android.server.appsearch.stats.AppSearchStatsLog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

/**
 * The class to hold stats for DeltaUpdate or FullUpdate.
 *
 * <p>This will be used to populate
 * {@link AppSearchStatsLog#CONTACTS_INDEXER_UPDATE_STATS_REPORTED}.
 *
 * <p>This class is not thread-safe.
 *
 * @hide
 */
public class ContactsUpdateStats {
    @IntDef(
            value = {
                    UNKNOWN_UPDATE_TYPE,
                    DELTA_UPDATE,
                    FULL_UPDATE,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UpdateType {
    }

    public static final int UNKNOWN_UPDATE_TYPE =
            AppSearchStatsLog.CONTACTS_INDEXER_UPDATE_STATS_REPORTED__UPDATE_TYPE__UNKNOWN;
    /** Incremental update reacting to CP2 change notifications. */
    public static final int DELTA_UPDATE =
            AppSearchStatsLog.CONTACTS_INDEXER_UPDATE_STATS_REPORTED__UPDATE_TYPE__DELTA;
    /** Complete update to bring AppSearch in sync with CP2. */
    public static final int FULL_UPDATE =
            AppSearchStatsLog.CONTACTS_INDEXER_UPDATE_STATS_REPORTED__UPDATE_TYPE__FULL;

    @IntDef(
            value = {
                    ERROR_CODE_CP2_RUNTIME_EXCEPTION,
                    ERROR_CODE_CP2_NULL_CURSOR,
                    ERROR_CODE_APP_SEARCH_SYSTEM_ERROR,
                    ERROR_CODE_CONTACTS_INDEXER_UNKNOWN_ERROR,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ErrorCode {
    }

    // Error code logged from CP2 runtime exceptions
    public static final int ERROR_CODE_CP2_RUNTIME_EXCEPTION = 10000;
    // Error code logged from CP2 null query cursors
    public static final int ERROR_CODE_CP2_NULL_CURSOR = 10001;
    // Error code logged from AppSearch system errors. This code may be combined with an
    // AppSearchResult code from toFailedResult on the throwable from onSystemError().
    public static final int ERROR_CODE_APP_SEARCH_SYSTEM_ERROR = 10100;
    // Error code logged from ContactsIndexer for otherwise uncaught exceptions
    public static final int ERROR_CODE_CONTACTS_INDEXER_UNKNOWN_ERROR = 10200;

    @UpdateType
    int mUpdateType = UNKNOWN_UPDATE_TYPE;
    // Status for updates.
    // In case of success, we will just have one success status stored.
    // In case of Error,  we store the unique error codes during the update.
    Set<Integer> mUpdateStatuses = new ArraySet<>();
    // Status for deletions.
    // In case of success, we will just have one success status stored.
    // In case of Error,  we store the unique error codes during the deletion.
    Set<Integer> mDeleteStatuses = new ArraySet<>();

    // Start time in millis for update and delete.
    long mUpdateAndDeleteStartTimeMillis;
    // Start time in millis for last full update
    long mLastFullUpdateStartTimeMillis;
    // Start time in millis for last delta update
    long mLastDeltaUpdateStartTimeMillis;
    // Time in millis of last contact updated from CP2
    long mLastContactUpdatedTimeMillis;
    // Time in millis of last contact deleted from CP2
    long mLastContactDeletedTimeMillis;
    // The mLastContactUpdatedTimeMillis from the previous update. This field is logged only for
    // full updates and should match the current mLastContactUpdatedTimeMillis.
    // Delta updates are run in response to CP2 notifications, so we expect the last contact updated
    // to have changed. Full updates are scheduled as a fix/maintenance job, so it's not expected
    // for the last contact updated to have changed. It's possible that a full update lands right as
    // a contact is updated, but we expect this to happen very rarely or not at all. There is an
    // issue if we find that these timestamps frequently do not match.
    long mPreviousLastContactUpdatedTimeMillis;

    //
    // Update for both old and new contacts(a.k.a insertion).
    //
    // # of old and new contacts failed to be updated.
    int mContactsUpdateFailedCount;
    // # of old and new contacts succeeds to be updated.
    int mContactsUpdateSucceededCount;
    // # of contacts update skipped due to NO significant change during the update.
    int mContactsUpdateSkippedCount;
    // Total # of old and new contacts to be updated.
    // It should equal to
    // mContactsUpdateFailedCount + mContactsUpdateSucceededCount + mContactsUpdateSkippedCount
    int mTotalContactsToBeUpdated;
    // Among the succeeded and failed contacts updates, how many of them are for the new contacts
    // currently NOT available in AppSearch.
    int mNewContactsToBeUpdated;

    //
    // Deletion for old documents.
    //
    // # of old contacts that failed to be deleted. This includes contacts that were not found.
    int mContactsDeleteFailedCount;
    // # of old contacts that were deleted successfully.
    int mContactsDeleteSucceededCount;
    // # of old contacts to be deleted that were not found.
    int mContactsDeleteNotFoundCount;
    // Total # of old contacts to be deleted. It should equal
    // mContactsDeleteFailedCount + mContactsDeleteSucceededCount
    int mTotalContactsToBeDeleted;

    public void clear() {
        mUpdateType = UNKNOWN_UPDATE_TYPE;
        mUpdateStatuses.clear();
        mDeleteStatuses.clear();
        mUpdateAndDeleteStartTimeMillis = 0;
        mLastFullUpdateStartTimeMillis = 0;
        mLastDeltaUpdateStartTimeMillis = 0;
        mLastContactUpdatedTimeMillis = 0;
        mLastContactDeletedTimeMillis = 0;
        mPreviousLastContactUpdatedTimeMillis = 0;
        // Update for old and new contacts
        mContactsUpdateFailedCount = 0;
        mContactsUpdateSucceededCount = 0;
        mContactsUpdateSkippedCount = 0;
        mNewContactsToBeUpdated = 0;
        mTotalContactsToBeUpdated = 0;
        // delete for old contacts
        mContactsDeleteFailedCount = 0;
        mContactsDeleteSucceededCount = 0;
        mContactsDeleteNotFoundCount = 0;
        mTotalContactsToBeDeleted = 0;
    }

    @NonNull
    public String toString() {
        return "UpdateType: " + mUpdateType
                + ", UpdateStatus: " + mUpdateStatuses.toString()
                + ", DeleteStatus: " + mDeleteStatuses.toString()
                + ", UpdateAndDeleteStartTimeMillis: " + mUpdateAndDeleteStartTimeMillis
                + ", LastFullUpdateStartTimeMillis: " + mLastFullUpdateStartTimeMillis
                + ", LastDeltaUpdateStartTimeMillis: " + mLastDeltaUpdateStartTimeMillis
                + ", LastContactUpdatedTimeMillis: " + mLastContactUpdatedTimeMillis
                + ", LastContactDeletedTimeMillis: " + mLastContactDeletedTimeMillis
                + ", PreviousLastContactUpdatedTimeMillis: " + mPreviousLastContactUpdatedTimeMillis
                + ", ContactsUpdateFailedCount: " + mContactsUpdateFailedCount
                + ", ContactsUpdateSucceededCount: " + mContactsUpdateSucceededCount
                + ", NewContactsToBeUpdated: " + mNewContactsToBeUpdated
                + ", ContactsUpdateSkippedCount: " + mContactsUpdateSkippedCount
                + ", TotalContactsToBeUpdated: " + mTotalContactsToBeUpdated
                + ", ContactsDeleteFailedCount: " + mContactsDeleteFailedCount
                + ", ContactsDeleteSucceededCount: " + mContactsDeleteSucceededCount
                + ", ContactsDeleteNotFoundCount: " + mContactsDeleteNotFoundCount
                + ", TotalContactsToBeDeleted: " + mTotalContactsToBeDeleted;
    }
}