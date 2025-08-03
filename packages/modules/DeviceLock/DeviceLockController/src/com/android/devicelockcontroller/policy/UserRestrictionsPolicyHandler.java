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

package com.android.devicelockcontroller.policy;

import android.app.admin.DevicePolicyManager;
import android.os.Bundle;
import android.os.UserManager;
import android.util.ArraySet;

import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Enforces UserRestriction policies.
 */
final class UserRestrictionsPolicyHandler implements PolicyHandler {

    private static final String TAG = "UserRestrictionsPolicyHandler";

    private final ArraySet<String> mAlwaysOnRestrictions = new ArraySet<>();
    private final Executor mBgExecutor;

    /**
     * A list of restrictions that will be always active, it is optional, partners can config the
     * list via provisioning configs.
     */
    private ArraySet<String> mOptionalAlwaysOnRestrictions;

    private ArraySet<String> mLockModeRestrictions;

    private final DevicePolicyManager mDpm;
    private final UserManager mUserManager;
    private final boolean mIsDebug;

    UserRestrictionsPolicyHandler(DevicePolicyManager dpm, UserManager userManager,
            boolean isDebug, Executor bgExecutor) {
        mDpm = dpm;
        mUserManager = userManager;
        mIsDebug = isDebug;
        mBgExecutor = bgExecutor;

        LogUtil.i(TAG, String.format(Locale.US, "Build type DEBUG = %s", isDebug));

        Collections.addAll(mAlwaysOnRestrictions, UserManager.DISALLOW_SAFE_BOOT);
        if (!isDebug) {
            Collections.addAll(mAlwaysOnRestrictions, UserManager.DISALLOW_DEBUGGING_FEATURES);
        }
    }

    @Override
    public ListenableFuture<Boolean> onProvisionInProgress() {
        setupRestrictions(mAlwaysOnRestrictions, true);
        return Futures.whenAllSucceed(
                        setupRestrictions(retrieveOptionalAlwaysOnRestrictions(), true),
                        setupRestrictions(retrieveLockModeRestrictions(), false))
                .call(() -> true, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Boolean> onLocked() {
        setupRestrictions(mAlwaysOnRestrictions, true);
        return Futures.whenAllSucceed(
                        setupRestrictions(retrieveOptionalAlwaysOnRestrictions(), true),
                        setupRestrictions(retrieveLockModeRestrictions(), true))
                .call(() -> true, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Boolean> onUnlocked() {
        setupRestrictions(mAlwaysOnRestrictions, true);
        return Futures.whenAllSucceed(
                        setupRestrictions(retrieveOptionalAlwaysOnRestrictions(), true),
                        setupRestrictions(retrieveLockModeRestrictions(), false))
                .call(() -> true, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Boolean> onCleared() {
        setupRestrictions(mAlwaysOnRestrictions, false);
        return Futures.whenAllSucceed(
                        setupRestrictions(retrieveOptionalAlwaysOnRestrictions(), false),
                        setupRestrictions(retrieveLockModeRestrictions(), false))
                .call(() -> true, MoreExecutors.directExecutor());
    }

    private ListenableFuture<ArraySet<String>> retrieveLockModeRestrictions() {
        if (mLockModeRestrictions != null) return Futures.immediateFuture(mLockModeRestrictions);
        final SetupParametersClient parameters = SetupParametersClient.getInstance();
        final ListenableFuture<String> kioskPackageTask = parameters.getKioskPackage();
        final ListenableFuture<Boolean> outgoingCallsDisabledTask =
                parameters.getOutgoingCallsDisabled();
        return Futures.whenAllSucceed(kioskPackageTask,
                        outgoingCallsDisabledTask)
                .call(() -> {
                    if (Futures.getDone(kioskPackageTask) == null) {
                        throw new IllegalStateException("Setup parameters does not exist!");
                    }
                    if (mLockModeRestrictions == null) {
                        mLockModeRestrictions = new ArraySet<>(1);
                        if (Futures.getDone(outgoingCallsDisabledTask)) {
                            mLockModeRestrictions.add(UserManager.DISALLOW_OUTGOING_CALLS);
                        }
                    }
                    return mLockModeRestrictions;
                }, mBgExecutor);
    }

    private ListenableFuture<ArraySet<String>> retrieveOptionalAlwaysOnRestrictions() {
        if (mOptionalAlwaysOnRestrictions != null) {
            return Futures.immediateFuture(
                    mOptionalAlwaysOnRestrictions);
        }
        final SetupParametersClient parameters = SetupParametersClient.getInstance();
        final ListenableFuture<String> kioskPackageTask = parameters.getKioskPackage();
        final ListenableFuture<Boolean> installingFromUnknownSourcesDisallowedTask =
                parameters.isInstallingFromUnknownSourcesDisallowed();

        return Futures.whenAllSucceed(kioskPackageTask,
                        installingFromUnknownSourcesDisallowedTask)
                .call(() -> {
                    if (Futures.getDone(kioskPackageTask) == null) {
                        throw new IllegalStateException("Setup parameters does not exist!");
                    }
                    if (mOptionalAlwaysOnRestrictions == null) {
                        mOptionalAlwaysOnRestrictions = new ArraySet<>(1);
                        if (Futures.getDone(installingFromUnknownSourcesDisallowedTask)) {
                            mOptionalAlwaysOnRestrictions.add(
                                    UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
                        }
                    }
                    return mOptionalAlwaysOnRestrictions;
                }, mBgExecutor);
    }

    private boolean setupRestrictions(ArraySet<String> restrictions, boolean enable) {
        Bundle userRestrictionBundle = mUserManager.getUserRestrictions();

        for (int i = 0, size = restrictions.size(); i < size; i++) {
            String restriction = restrictions.valueAt(i);
            if (userRestrictionBundle.getBoolean(restriction, false) != enable) {
                if (enable) {
                    mDpm.addUserRestriction(null /* admin */, restriction);
                    LogUtil.v(TAG, String.format(Locale.US, "enable %s restriction", restriction));
                } else {
                    mDpm.clearUserRestriction(null /* admin */, restriction);
                    LogUtil.v(TAG, String.format(Locale.US, "clear %s restriction", restriction));
                }
            }
        }
        return true;
    }

    private ListenableFuture<Boolean> setupRestrictions(
            ListenableFuture<ArraySet<String>> restrictionsFuture, boolean enable) {
        return Futures.transform(restrictionsFuture,
                restrictions -> setupRestrictions(restrictions, enable),
                mBgExecutor);
    }
}
