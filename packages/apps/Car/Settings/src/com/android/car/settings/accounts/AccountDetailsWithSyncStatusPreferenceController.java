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

package com.android.car.settings.accounts;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.content.SyncInfo;
import android.content.SyncStatusInfo;
import android.content.SyncStatusObserver;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.ui.preference.CarUiTwoActionIconPreference;
import com.android.settingslib.utils.ThreadUtils;

import java.util.List;
import java.util.Set;

/**
 * Controller for the preference that shows information about an account, including info about
 * failures. It also handles a secondary button for account syncing.
 */
public class AccountDetailsWithSyncStatusPreferenceController extends
        AccountDetailsBasePreferenceController {
    private Object mStatusChangeListenerHandle;
    private SyncStatusObserver mSyncStatusObserver =
            which -> ThreadUtils.postOnMainThread(() -> {
                // The observer call may occur even if the fragment hasn't been started, so
                // only force an update if the fragment hasn't been stopped.
                if (isStarted()) {
                    refreshUi();
                }
            });

    public AccountDetailsWithSyncStatusPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /**
     * Registers the account update and sync status change callbacks.
     */
    @Override
    protected void onStartInternal() {
        mStatusChangeListenerHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                        | ContentResolver.SYNC_OBSERVER_TYPE_STATUS
                        | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncStatusObserver);
    }

    /**
     * Unregisters the account update and sync status change callbacks.
     */
    @Override
    protected void onStopInternal() {
        if (mStatusChangeListenerHandle != null) {
            ContentResolver.removeStatusChangeListener(mStatusChangeListenerHandle);
        }
    }

    @Override
    protected void updateState(CarUiTwoActionIconPreference preference) {
        super.updateState(preference);
        if (isSyncFailing()) {
            preference.setSummary(R.string.sync_is_failing);
        } else {
            preference.setSummary("");
        }
        updateSyncButton();
    }

    private boolean isSyncFailing() {
        int userId = getUserHandle().getIdentifier();
        List<SyncInfo> currentSyncs = getCurrentSyncs(userId);
        boolean syncIsFailing = false;

        Set<SyncAdapterType> syncAdapters = AccountSyncHelper.getVisibleSyncAdaptersForAccount(
                getContext(), getAccount(), getUserHandle());
        for (SyncAdapterType syncAdapter : syncAdapters) {
            String authority = syncAdapter.authority;

            SyncStatusInfo status = ContentResolver.getSyncStatusAsUser(getAccount(), authority,
                    userId);
            boolean syncEnabled = ContentResolver.getSyncAutomaticallyAsUser(getAccount(),
                    authority, userId);
            boolean activelySyncing = AccountSyncHelper.isSyncing(getAccount(), currentSyncs,
                    authority);

            AccountSyncHelper.SyncState syncState = AccountSyncHelper.getSyncState(status,
                    syncEnabled, activelySyncing);

            boolean syncIsPending = status != null && status.pending;
            if (syncState == AccountSyncHelper.SyncState.FAILED && !activelySyncing
                    && !syncIsPending) {
                syncIsFailing = true;
            }
        }

        return syncIsFailing;
    }

    private void updateSyncButton() {
        // Set the button to either request or cancel sync, depending on the current state
        boolean hasActiveSyncs = !getCurrentSyncs(
                getUserHandle().getIdentifier()).isEmpty();

        // If there are active syncs, clicking the button with cancel them. Otherwise, clicking the
        // button will start them.
        getPreference().setSecondaryActionIcon(
                hasActiveSyncs ? R.drawable.ic_sync_cancel : R.drawable.ic_sync);
        getPreference().setOnSecondaryActionClickListener(hasActiveSyncs
                ? this::cancelSyncForEnabledProviders
                : this::requestSyncForEnabledProviders);
    }

    private void requestSyncForEnabledProviders() {
        int userId = getUserHandle().getIdentifier();

        Set<SyncAdapterType> adapters = AccountSyncHelper.getSyncableSyncAdaptersForAccount(
                getAccount(), getUserHandle());
        for (SyncAdapterType adapter : adapters) {
            requestSync(adapter.authority, userId);
        }
    }

    private void cancelSyncForEnabledProviders() {
        int userId = getUserHandle().getIdentifier();

        Set<SyncAdapterType> adapters = AccountSyncHelper.getSyncableSyncAdaptersForAccount(
                getAccount(), getUserHandle());
        for (SyncAdapterType adapter : adapters) {
            cancelSync(adapter.authority, userId);
        }
    }

    @VisibleForTesting
    List<SyncInfo> getCurrentSyncs(int userId) {
        return ContentResolver.getCurrentSyncsAsUser(userId);
    }

    @VisibleForTesting
    void requestSync(String authority, int userId) {
        AccountSyncHelper.requestSyncIfAllowed(getAccount(), authority, userId);
    }

    @VisibleForTesting
    void cancelSync(String authority, int userId) {
        ContentResolver.cancelSyncAsUser(getAccount(), authority, userId);
    }
}
