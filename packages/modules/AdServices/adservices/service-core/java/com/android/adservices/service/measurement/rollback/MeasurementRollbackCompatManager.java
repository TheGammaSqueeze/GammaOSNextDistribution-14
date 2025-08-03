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

package com.android.adservices.service.measurement.rollback;

import static com.android.adservices.AdServicesCommon.EXTSERVICES_APEX_NAME_SUFFIX;

import android.annotation.NonNull;
import android.app.adservices.AdServicesManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.service.appsearch.AppSearchMeasurementRollbackWorker;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.util.List;
import java.util.Objects;

/**
 * This class manages the interface for reading/writing AdServices Measurement deletion data on S-
 * devices. This is needed because AdServices does not run any code in the system server on S-
 * devices, so data indicating that a deletion happened prior to rollback is stored in AppSearch on
 * S, and in External Storage on R, in order to make it rollback safe.
 */
public final class MeasurementRollbackCompatManager {
    public static final long APEX_VERSION_WHEN_NOT_FOUND = -1L;

    private static volatile Long sCurrentApexVersion;
    private static final Object LOCK_OBJECT = new Object();

    private final long mCurrentApexVersion;
    private final int mDeletionApiType;
    private final MeasurementRollbackWorker<?> mMeasurementRollbackWorker;

    @VisibleForTesting
    MeasurementRollbackCompatManager(
            long currentApexVersion, int deletionApiType, MeasurementRollbackWorker<?> worker) {
        mCurrentApexVersion = currentApexVersion;
        mDeletionApiType = deletionApiType;
        mMeasurementRollbackWorker = worker;
    }

    /** Returns an instance of MeasurementRollbackCompatManager. */
    public static MeasurementRollbackCompatManager getInstance(
            @NonNull Context context, @AdServicesManager.DeletionApiType int deletionApiType) {
        Objects.requireNonNull(context);
        return new MeasurementRollbackCompatManager(
                computeApexVersionIfNeeded(context), deletionApiType, getWorker(context));
    }

    /**
     * Records that a measurement deletion event occurred. This store the userId and the apex
     * version that this deletion occurred in.
     */
    public void recordAdServicesDeletionOccurred() {
        if (mCurrentApexVersion == APEX_VERSION_WHEN_NOT_FOUND) {
            LogUtil.e(
                    "MeasurementRollbackCompatManager current apex version not found. Skipping"
                            + " recording deletion.");
            return;
        }

        try {
            mMeasurementRollbackWorker.recordAdServicesDeletionOccurred(
                    mDeletionApiType, mCurrentApexVersion);
        } catch (RuntimeException e) {
            LogUtil.e(e, "MeasurementRollbackCompatManager failed to record deletion");
        }
    }

    /**
     * Checks if rollback-proof storage contains data indicating that a rollback happened in a
     * higher apex version than is currently executing. This method loads the saved data from
     * storage and compares the version stored in it to the current version.
     *
     * <p><b><u>Side effect</u></b>: If the stored data indicates that a rollback reconciliation is
     * needed, the stored data is deleted before returning. This method is therefore NOT idempotent.
     *
     * @return true if storage contains a higher than the current version; false otherwise.
     */
    public boolean needsToHandleRollbackReconciliation() {
        if (mCurrentApexVersion == APEX_VERSION_WHEN_NOT_FOUND) {
            LogUtil.e(
                    "MeasurementRollbackCompatManager current apex version not found. Skipping"
                            + " handling rollback reconciliation.");
            return false;
        }

        try {
            return needsToHandleRollbackReconciliationHelper(mMeasurementRollbackWorker);
        } catch (RuntimeException e) {
            LogUtil.e(
                    e, "MeasurementRollbackCompatManager failed to handle rollback reconciliation");
            return false;
        }
    }

    // Helper method created so that the wildcard can be captured through type inference.
    // See https://docs.oracle.com/javase/tutorial/java/generics/capture.html
    private <T> boolean needsToHandleRollbackReconciliationHelper(
            MeasurementRollbackWorker<T> worker) {
        Pair<Long, T> rollbackVersionAndStorageIdPair =
                worker.getAdServicesDeletionRollbackMetadata(mDeletionApiType);
        if (rollbackVersionAndStorageIdPair == null
                || rollbackVersionAndStorageIdPair.first <= mCurrentApexVersion) {
            return false;
        }

        worker.clearAdServicesDeletionOccurred(rollbackVersionAndStorageIdPair.second);
        return true;
    }

    private static MeasurementRollbackWorker<?> getWorker(Context context) {
        return SdkLevel.isAtLeastS()
                ? AppSearchMeasurementRollbackWorker.getInstance(context, getUserId())
                : new AdServicesExtStorageMeasurementRollbackWorker(context);
    }

    @VisibleForTesting
    static String getUserId() {
        return "" + UserHandle.getUserHandleForUid(Binder.getCallingUid()).getIdentifier();
    }

    @VisibleForTesting
    static long computeApexVersion(Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> installedPackages =
                packageManager.getInstalledPackages(PackageManager.MATCH_APEX);
        return installedPackages.stream()
                .filter(s -> s.isApex && s.packageName.endsWith(EXTSERVICES_APEX_NAME_SUFFIX))
                .findFirst()
                .map(PackageInfo::getLongVersionCode)
                .orElse(APEX_VERSION_WHEN_NOT_FOUND);
    }

    private static long computeApexVersionIfNeeded(Context context) {
        if (sCurrentApexVersion == null) {
            synchronized (LOCK_OBJECT) {
                if (sCurrentApexVersion == null) {
                    sCurrentApexVersion = computeApexVersion(context);
                }
            }
        }

        return sCurrentApexVersion;
    }
}
