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

package com.android.intentresolver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;

/** Monitor for runtime conditions that may disable work profile display. */
public class WorkProfileAvailabilityManager {
    private final UserManager mUserManager;
    private final UserHandle mWorkProfileUserHandle;
    private final Runnable mOnWorkProfileStateUpdated;

    private BroadcastReceiver mWorkProfileStateReceiver;

    private boolean mIsWaitingToEnableWorkProfile;
    private boolean mWorkProfileHasBeenEnabled;

    public WorkProfileAvailabilityManager(
            UserManager userManager,
            UserHandle workProfileUserHandle,
            Runnable onWorkProfileStateUpdated) {
        mUserManager = userManager;
        mWorkProfileUserHandle = workProfileUserHandle;
        mWorkProfileHasBeenEnabled = isWorkProfileEnabled();
        mOnWorkProfileStateUpdated = onWorkProfileStateUpdated;
    }

    /**
     * Register a {@link BroadcastReceiver}, if we haven't already, to be notified about work
     * profile availability changes.
     *
     * TODO: this takes the context for testing, because we don't have a context on hand when we
     * set up this component's default "override" in {@link ChooserActivityOverrideData#reset()}.
     * The use of these overrides in our testing design is questionable and can hopefully be
     * improved someday; then this context should be injected in our constructor & held as `final`.
     *
     * TODO: consider injecting an optional `Lifecycle` so that this component can automatically
     * manage its own registration/unregistration. (This would be optional because registration of
     * the receiver is conditional on having `shouldShowTabs()` in our session.)
     */
    public void registerWorkProfileStateReceiver(Context context) {
        if (mWorkProfileStateReceiver != null) {
            return;
        }
        mWorkProfileStateReceiver = createWorkProfileStateReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        context.registerReceiverAsUser(
                mWorkProfileStateReceiver, UserHandle.ALL, filter, null, null);
    }

    /**
     * Unregister any {@link BroadcastReceiver} currently waiting for a work-enabled broadcast.
     *
     * TODO: this takes the context for testing, because we don't have a context on hand when we
     * set up this component's default "override" in {@link ChooserActivityOverrideData#reset()}.
     * The use of these overrides in our testing design is questionable and can hopefully be
     * improved someday; then this context should be injected in our constructor & held as `final`.
     */
    public void unregisterWorkProfileStateReceiver(Context context) {
        if (mWorkProfileStateReceiver == null) {
            return;
        }
        context.unregisterReceiver(mWorkProfileStateReceiver);
        mWorkProfileStateReceiver = null;
    }

    public boolean isQuietModeEnabled() {
        return mUserManager.isQuietModeEnabled(mWorkProfileUserHandle);
    }

    // TODO: why do clients only care about the result of `isQuietModeEnabled()`, even though
    // internally (in `isWorkProfileEnabled()`) we also check this 'unlocked' condition?
    @VisibleForTesting
    public boolean isWorkProfileUserUnlocked() {
        return mUserManager.isUserUnlocked(mWorkProfileUserHandle);
    }

    /**
     * Request that quiet mode be enabled (or disabled) for the work profile.
     * TODO: this is only used to disable quiet mode; should that be hard-coded?
     */
    public void requestQuietModeEnabled(boolean enabled) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(
                () -> mUserManager.requestQuietModeEnabled(enabled, mWorkProfileUserHandle));
        mIsWaitingToEnableWorkProfile = true;
    }

    /**
     * Stop waiting for a work-enabled broadcast.
     * TODO: this seems strangely low-level to include as part of the public API. Maybe some
     * responsibilities need to be pulled over from the client?
     */
    public void markWorkProfileEnabledBroadcastReceived() {
        mIsWaitingToEnableWorkProfile = false;
    }

    public boolean isWaitingToEnableWorkProfile() {
        return mIsWaitingToEnableWorkProfile;
    }

    private boolean isWorkProfileEnabled() {
        return (mWorkProfileUserHandle != null)
                && !isQuietModeEnabled()
                && isWorkProfileUserUnlocked();
    }

    private BroadcastReceiver createWorkProfileStateReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (!TextUtils.equals(action, Intent.ACTION_USER_UNLOCKED)
                        && !TextUtils.equals(action, Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                        && !TextUtils.equals(action, Intent.ACTION_MANAGED_PROFILE_AVAILABLE)) {
                    return;
                }

                if (intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1)
                        != mWorkProfileUserHandle.getIdentifier()) {
                    return;
                }

                if (isWorkProfileEnabled()) {
                    if (mWorkProfileHasBeenEnabled) {
                        return;
                    }
                    mWorkProfileHasBeenEnabled = true;
                    mIsWaitingToEnableWorkProfile = false;
                } else {
                    // Must be an UNAVAILABLE broadcast, so we watch for the next availability.
                    // TODO: confirm the above reasoning (& handling of "UNAVAILABLE" in general).
                    mWorkProfileHasBeenEnabled = false;
                }

                mOnWorkProfileStateUpdated.run();
            }
        };
    }
}
