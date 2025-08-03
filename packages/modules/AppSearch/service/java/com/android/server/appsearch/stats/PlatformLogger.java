/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.appsearch.stats;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.annotation.CanIgnoreReturnValue;
import android.app.appsearch.exceptions.AppSearchException;
import android.app.appsearch.stats.SchemaMigrationStats;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.appsearch.InternalAppSearchLogger;
import com.android.server.appsearch.FrameworkAppSearchConfig;
import com.android.server.appsearch.external.localstorage.stats.CallStats;
import com.android.server.appsearch.external.localstorage.stats.InitializeStats;
import com.android.server.appsearch.external.localstorage.stats.OptimizeStats;
import com.android.server.appsearch.external.localstorage.stats.PutDocumentStats;
import com.android.server.appsearch.external.localstorage.stats.RemoveStats;
import com.android.server.appsearch.external.localstorage.stats.SearchStats;
import com.android.server.appsearch.external.localstorage.stats.SetSchemaStats;
import com.android.server.appsearch.util.ApiCallRecord;
import com.android.server.appsearch.util.PackageUtil;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Logger Implementation for pushed atoms.
 *
 * <p>This class is thread-safe.
 *
 * @hide
 */
public final class PlatformLogger implements InternalAppSearchLogger {
    private static final String TAG = "AppSearchPlatformLogger";

    // Context of the user we're logging for.
    private final Context mUserContext;

    // Manager holding the configuration flags
    private final FrameworkAppSearchConfig mConfig;

    private final Random mRng = new Random();
    private final Object mLock = new Object();

    /**
     * SparseArray to track how many stats we skipped due to
     * {@link FrameworkAppSearchConfig#getCachedMinTimeIntervalBetweenSamplesMillis()}.
     *
     * <p> We can have correct extrapolated number by adding those counts back when we log
     * the same type of stats next time. E.g. the true count of an event could be estimated as:
     * SUM(sampling_interval * (num_skipped_sample + 1)) as est_count
     *
     * <p>The key to the SparseArray is {@link CallStats.CallType}
     */
    @GuardedBy("mLock")
    private final SparseIntArray mSkippedSampleCountLocked =
            new SparseIntArray();

    /**
     * Map to cache the packageUid for each package.
     *
     * <p>It maps packageName to packageUid.
     *
     * <p>The entry will be removed whenever the app gets uninstalled
     */
    @GuardedBy("mLock")
    private final Map<String, Integer> mPackageUidCacheLocked =
            new ArrayMap<>();

    /**
     * Elapsed time for last stats logged from boot in millis
     */
    @GuardedBy("mLock")
    private long mLastPushTimeMillisLocked = 0;

    /**
     * Record the last n API calls used by dumpsys to print debugging information about the
     * sequence of the API calls, where n is specified by
     * {@link FrameworkAppSearchConfig#getCachedApiCallStatsLimit()}.
     */
    @GuardedBy("mLock")
    private ArrayDeque<ApiCallRecord> mLastNCalls = new ArrayDeque<>();

    /**
     * Helper class to hold platform specific stats for statsd.
     */
    static final class ExtraStats {
        // UID for the calling package of the stats.
        final int mPackageUid;
        // sampling interval for the call type of the stats.
        final int mSamplingInterval;
        // number of samplings skipped before the current one for the same call type.
        final int mSkippedSampleCount;

        ExtraStats(int packageUid, int samplingInterval, int skippedSampleCount) {
            mPackageUid = packageUid;
            mSamplingInterval = samplingInterval;
            mSkippedSampleCount = skippedSampleCount;
        }
    }

    /**
     * Constructor
     */
    public PlatformLogger(
            @NonNull Context userContext,
            @NonNull FrameworkAppSearchConfig config) {
        mUserContext = Objects.requireNonNull(userContext);
        mConfig = Objects.requireNonNull(config);
    }

    /** Logs {@link CallStats}. */
    @Override
    public void logStats(@NonNull CallStats stats) {
        Objects.requireNonNull(stats);
        synchronized (mLock) {
            if (mConfig.getCachedApiCallStatsLimit() > 0) {
                addStatsToQueueLocked(new ApiCallRecord(stats));
            } else {
                mLastNCalls.clear();
            }
            if (shouldLogForTypeLocked(stats.getCallType())) {
                logStatsImplLocked(stats);
            }
        }
    }

    /** Logs {@link PutDocumentStats}. */
    @Override
    public void logStats(@NonNull PutDocumentStats stats) {
        Objects.requireNonNull(stats);
        synchronized (mLock) {
            if (shouldLogForTypeLocked(CallStats.CALL_TYPE_PUT_DOCUMENT)) {
                logStatsImplLocked(stats);
            }
        }
    }

    @Override
    public void logStats(@NonNull InitializeStats stats) {
        Objects.requireNonNull(stats);
        synchronized (mLock) {
            if (shouldLogForTypeLocked(CallStats.CALL_TYPE_INITIALIZE)) {
                logStatsImplLocked(stats);
            }
        }
    }

    @Override
    public void logStats(@NonNull SearchStats stats) {
        Objects.requireNonNull(stats);
        synchronized (mLock) {
            if (shouldLogForTypeLocked(CallStats.CALL_TYPE_SEARCH)) {
                logStatsImplLocked(stats);
            }
        }
    }

    @Override
    public void logStats(@NonNull RemoveStats stats) {
        // TODO(b/173532925): Log stats
    }

    @Override
    public void logStats(@NonNull OptimizeStats stats) {
        Objects.requireNonNull(stats);
        synchronized (mLock) {
            if (mConfig.getCachedApiCallStatsLimit() > 0) {
                // Unlike most other API calls, Optimize does not produce a CallStats, so we
                // record OptimizeStats in the queue.
                addStatsToQueueLocked(new ApiCallRecord(stats));
            } else {
                mLastNCalls.clear();
            }
            if (shouldLogForTypeLocked(CallStats.CALL_TYPE_OPTIMIZE)) {
                logStatsImplLocked(stats);
            }
        }
    }

    @Override
    public void logStats(@NonNull SetSchemaStats stats) {
        Objects.requireNonNull(stats);
        synchronized (mLock) {
            if (shouldLogForTypeLocked(CallStats.CALL_TYPE_SET_SCHEMA)) {
                logStatsImplLocked(stats);
            }
        }
    }

    @Override
    public void logStats(@NonNull SchemaMigrationStats stats) {
        Objects.requireNonNull(stats);
        synchronized (mLock) {
            if (shouldLogForTypeLocked(CallStats.CALL_TYPE_SCHEMA_MIGRATION)) {
                logStatsImplLocked(stats);
            }
        }
    }

    @Override
    public void removeCacheForPackage(@NonNull String packageName) {
        removeCachedUidForPackage(packageName);
    }

    /**
     * Removes cached UID for package.
     *
     * @return removed UID for the package, or {@code INVALID_UID} if package was not previously
     * cached.
     */
    @CanIgnoreReturnValue
    @VisibleForTesting
    int removeCachedUidForPackage(@NonNull String packageName) {
        // TODO(b/173532925) This needs to be called when we get PACKAGE_REMOVED intent
        Objects.requireNonNull(packageName);
        synchronized (mLock) {
            Integer uid = mPackageUidCacheLocked.remove(packageName);
            return uid != null ? uid : Process.INVALID_UID;
        }
    }

    /**
     * Return a copy of the recorded {@link ApiCallRecord}.
     */
    @Override
    @NonNull
    public List<ApiCallRecord> getLastCalledApis() {
        synchronized (mLock) {
            trimExcessStatsQueueLocked();
            return new ArrayList<>(mLastNCalls);
        }
    }

    @GuardedBy("mLock")
    private void logStatsImplLocked(@NonNull CallStats stats) {
        mLastPushTimeMillisLocked = SystemClock.elapsedRealtime();
        ExtraStats extraStats = createExtraStatsLocked(stats.getPackageName(), stats.getCallType());
        String database = stats.getDatabase();
        try {
            // The num_reported_calls field in AppSearchPutDocumentStatsReported is always set to 1.
            // This is so that we can use one single sum value metrics to compute the total
            // estimated call count based on the formula defined in num_skipped_sample's field doc
            // (see atoms.proto). For example, if a device logged 3 call stats atoms for a call
            // type, and the numbers of skipped samples are 5, 0, 7, respectively, and the sampling
            // interval is 10, the total estimated calls are 10*(5+1) + 10*(0+1) + 10*(7+1) = 150.
            // In the sum value metrics reported by the device, we'll see 12 (=5+0+7) as the sum of
            // num_skipped_sample and 3 (=1+1+1) as the sum of num_reported_calls, and the total
            // will be 10*12 + 10*3 = 150 for that device's reported value.
            final int numReportedCalls = 1;

            int hashCodeForDatabase = calculateHashCodeMd5(database);
            AppSearchStatsLog.write(AppSearchStatsLog.APP_SEARCH_CALL_STATS_REPORTED,
                    extraStats.mSamplingInterval,
                    extraStats.mSkippedSampleCount,
                    extraStats.mPackageUid,
                    hashCodeForDatabase,
                    stats.getStatusCode(),
                    stats.getTotalLatencyMillis(),
                    stats.getCallType(),
                    stats.getEstimatedBinderLatencyMillis(),
                    stats.getNumOperationsSucceeded(),
                    stats.getNumOperationsFailed(),
                    numReportedCalls);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            // TODO(b/184204720) report hashing error to statsd
            //  We need to set a special value(e.g. 0xFFFFFFFF) for the hashing of the database,
            //  so in the dashboard we know there is some error for hashing.
            //
            // Something is wrong while calculating the hash code for database
            // this shouldn't happen since we always use "MD5" and "UTF-8"
            if (database != null) {
                Log.e(TAG, "Error calculating hash code for database " + database, e);
            }
        }
    }

    @GuardedBy("mLock")
    private void logStatsImplLocked(@NonNull SetSchemaStats stats) {
        mLastPushTimeMillisLocked = SystemClock.elapsedRealtime();
        ExtraStats extraStats = createExtraStatsLocked(stats.getPackageName(),
                CallStats.CALL_TYPE_SET_SCHEMA);
        String database = stats.getDatabase();
        try {
            int hashCodeForDatabase = calculateHashCodeMd5(database);
            // ignore close exception
            AppSearchStatsLog.write(AppSearchStatsLog.APP_SEARCH_SET_SCHEMA_STATS_REPORTED,
                    extraStats.mSamplingInterval,
                    extraStats.mSkippedSampleCount,
                    extraStats.mPackageUid,
                    hashCodeForDatabase,
                    stats.getStatusCode(),
                    stats.getTotalLatencyMillis(),
                    stats.getNewTypeCount(),
                    stats.getDeletedTypeCount(),
                    stats.getCompatibleTypeChangeCount(),
                    stats.getIndexIncompatibleTypeChangeCount(),
                    stats.getBackwardsIncompatibleTypeChangeCount(),
                    stats.getVerifyIncomingCallLatencyMillis(),
                    stats.getExecutorAcquisitionLatencyMillis(),
                    stats.getRebuildFromBundleLatencyMillis(),
                    stats.getJavaLockAcquisitionLatencyMillis(),
                    stats.getRewriteSchemaLatencyMillis(),
                    stats.getTotalNativeLatencyMillis(),
                    stats.getVisibilitySettingLatencyMillis(),
                    stats.getDispatchChangeNotificationsLatencyMillis(),
                    stats.getOptimizeLatencyMillis(),
                    stats.isPackageObserved(),
                    stats.getGetOldSchemaLatencyMillis(),
                    stats.getGetObserverLatencyMillis(),
                    stats.getPreparingChangeNotificationLatencyMillis(),
                    stats.getSchemaMigrationCallType());
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            // TODO(b/184204720) report hashing error to statsd
            //  We need to set a special value(e.g. 0xFFFFFFFF) for the hashing of the database,
            //  so in the dashboard we know there is some error for hashing.
            //
            // Something is wrong while calculating the hash code for database
            // this shouldn't happen since we always use "MD5" and "UTF-8"
            if (database != null) {
                Log.e(TAG, "Error calculating hash code for database " + database, e);
            }
        }
    }

    @GuardedBy("mLock")
    private void logStatsImplLocked(@NonNull SchemaMigrationStats stats) {
        mLastPushTimeMillisLocked = SystemClock.elapsedRealtime();
        ExtraStats extraStats = createExtraStatsLocked(stats.getPackageName(),
                CallStats.CALL_TYPE_SCHEMA_MIGRATION);
        String database = stats.getDatabase();
        try {
            int hashCodeForDatabase = calculateHashCodeMd5(database);
            // ignore close exception
            AppSearchStatsLog.write(AppSearchStatsLog.APP_SEARCH_SET_SCHEMA_STATS_REPORTED,
                    extraStats.mSamplingInterval,
                    extraStats.mSkippedSampleCount,
                    extraStats.mPackageUid,
                    hashCodeForDatabase,
                    stats.getTotalLatencyMillis(),
                    stats.getGetSchemaLatencyMillis(),
                    stats.getQueryAndTransformLatencyMillis(),
                    stats.getFirstSetSchemaLatencyMillis(),
                    stats.getSecondSetSchemaLatencyMillis(),
                    stats.getSaveDocumentLatencyMillis(),
                    stats.getTotalNeedMigratedDocumentCount(),
                    stats.getTotalSuccessMigratedDocumentCount(),
                    stats.getMigrationFailureCount());
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            // TODO(b/184204720) report hashing error to statsd
            //  We need to set a special value(e.g. 0xFFFFFFFF) for the hashing of the database,
            //  so in the dashboard we know there is some error for hashing.
            //
            // Something is wrong while calculating the hash code for database
            // this shouldn't happen since we always use "MD5" and "UTF-8"
            if (database != null) {
                Log.e(TAG, "Error calculating hash code for database " + database, e);
            }
        }
    }

    @GuardedBy("mLock")
    private void logStatsImplLocked(@NonNull PutDocumentStats stats) {
        mLastPushTimeMillisLocked = SystemClock.elapsedRealtime();
        ExtraStats extraStats = createExtraStatsLocked(
                stats.getPackageName(), CallStats.CALL_TYPE_PUT_DOCUMENT);
        String database = stats.getDatabase();
        try {
            int hashCodeForDatabase = calculateHashCodeMd5(database);
            AppSearchStatsLog.write(AppSearchStatsLog.APP_SEARCH_PUT_DOCUMENT_STATS_REPORTED,
                    extraStats.mSamplingInterval,
                    extraStats.mSkippedSampleCount,
                    extraStats.mPackageUid,
                    hashCodeForDatabase,
                    stats.getStatusCode(),
                    stats.getTotalLatencyMillis(),
                    stats.getGenerateDocumentProtoLatencyMillis(),
                    stats.getRewriteDocumentTypesLatencyMillis(),
                    stats.getNativeLatencyMillis(),
                    stats.getNativeDocumentStoreLatencyMillis(),
                    stats.getNativeIndexLatencyMillis(),
                    stats.getNativeIndexMergeLatencyMillis(),
                    stats.getNativeDocumentSizeBytes(),
                    stats.getNativeNumTokensIndexed(),
                    /*nativeExceededMaxNumTokens=*/false /* Deprecated and removed */);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            // TODO(b/184204720) report hashing error to statsd
            //  We need to set a special value(e.g. 0xFFFFFFFF) for the hashing of the database,
            //  so in the dashboard we know there is some error for hashing.
            //
            // Something is wrong while calculating the hash code for database
            // this shouldn't happen since we always use "MD5" and "UTF-8"
            if (database != null) {
                Log.e(TAG, "Error calculating hash code for database " + database, e);
            }
        }
    }

    @GuardedBy("mLock")
    private void logStatsImplLocked(@NonNull SearchStats stats) {
        mLastPushTimeMillisLocked = SystemClock.elapsedRealtime();
        ExtraStats extraStats = createExtraStatsLocked(stats.getPackageName(),
                CallStats.CALL_TYPE_SEARCH);
        String database = stats.getDatabase();
        try {
            int hashCodeForDatabase = calculateHashCodeMd5(database);
            int hashCodeForSearchSourceLogTag = calculateHashCodeMd5(stats.getSearchSourceLogTag());
            AppSearchStatsLog.write(AppSearchStatsLog.APP_SEARCH_QUERY_STATS_REPORTED,
                    extraStats.mSamplingInterval,
                    extraStats.mSkippedSampleCount,
                    extraStats.mPackageUid,
                    hashCodeForDatabase,
                    stats.getStatusCode(),
                    stats.getTotalLatencyMillis(),
                    stats.getRewriteSearchSpecLatencyMillis(),
                    stats.getRewriteSearchResultLatencyMillis(),
                    stats.getVisibilityScope(),
                    stats.getNativeLatencyMillis(),
                    stats.getTermCount(),
                    stats.getQueryLength(),
                    stats.getFilteredNamespaceCount(),
                    stats.getFilteredSchemaTypeCount(),
                    stats.getRequestedPageSize(),
                    stats.getCurrentPageReturnedResultCount(),
                    stats.isFirstPage(),
                    stats.getParseQueryLatencyMillis(),
                    stats.getRankingStrategy(),
                    stats.getScoredDocumentCount(),
                    stats.getScoringLatencyMillis(),
                    stats.getRankingLatencyMillis(),
                    stats.getDocumentRetrievingLatencyMillis(),
                    stats.getResultWithSnippetsCount(),
                    stats.getJavaLockAcquisitionLatencyMillis(),
                    stats.getAclCheckLatencyMillis(),
                    stats.getNativeLockAcquisitionLatencyMillis(),
                    stats.getJavaToNativeJniLatencyMillis(),
                    stats.getNativeToJavaJniLatencyMillis(),
                    stats.getJoinType(),
                    stats.getNumJoinedResultsCurrentPage(),
                    stats.getJoinLatencyMillis(),
                    hashCodeForSearchSourceLogTag);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            // TODO(b/184204720) report hashing error to statsd
            //  We need to set a special value(e.g. 0xFFFFFFFF) for the hashing of the database,
            //  so in the dashboard we know there is some error for hashing.
            //
            // Something is wrong while calculating the hash code for database
            // this shouldn't happen since we always use "MD5" and "UTF-8"
            if (database != null) {
                Log.e(TAG, "Error calculating hash code for database " + database, e);
            }
        }
    }

    @GuardedBy("mLock")
    private void logStatsImplLocked(@NonNull InitializeStats stats) {
        mLastPushTimeMillisLocked = SystemClock.elapsedRealtime();
        ExtraStats extraStats = createExtraStatsLocked(/*packageName=*/ null,
                CallStats.CALL_TYPE_INITIALIZE);
        AppSearchStatsLog.write(AppSearchStatsLog.APP_SEARCH_INITIALIZE_STATS_REPORTED,
                extraStats.mSamplingInterval,
                extraStats.mSkippedSampleCount,
                extraStats.mPackageUid,
                stats.getStatusCode(),
                stats.getTotalLatencyMillis(),
                stats.hasDeSync(),
                stats.getPrepareSchemaAndNamespacesLatencyMillis(),
                stats.getPrepareVisibilityStoreLatencyMillis(),
                stats.getNativeLatencyMillis(),
                stats.getDocumentStoreRecoveryCause(),
                stats.getIndexRestorationCause(),
                stats.getSchemaStoreRecoveryCause(),
                stats.getDocumentStoreRecoveryLatencyMillis(),
                stats.getIndexRestorationLatencyMillis(),
                stats.getSchemaStoreRecoveryLatencyMillis(),
                stats.getDocumentStoreDataStatus(),
                stats.getDocumentCount(),
                stats.getSchemaTypeCount(),
                stats.hasReset(),
                stats.getResetStatusCode());
    }

    @GuardedBy("mLock")
    private void logStatsImplLocked(@NonNull OptimizeStats stats) {
        mLastPushTimeMillisLocked = SystemClock.elapsedRealtime();
        ExtraStats extraStats = createExtraStatsLocked(/*packageName=*/ null,
                CallStats.CALL_TYPE_OPTIMIZE);
        AppSearchStatsLog.write(AppSearchStatsLog.APP_SEARCH_OPTIMIZE_STATS_REPORTED,
                extraStats.mSamplingInterval,
                extraStats.mSkippedSampleCount,
                stats.getStatusCode(),
                stats.getTotalLatencyMillis(),
                stats.getNativeLatencyMillis(),
                stats.getDocumentStoreOptimizeLatencyMillis(),
                stats.getIndexRestorationLatencyMillis(),
                stats.getOriginalDocumentCount(),
                stats.getDeletedDocumentCount(),
                stats.getExpiredDocumentCount(),
                stats.getStorageSizeBeforeBytes(),
                stats.getStorageSizeAfterBytes(),
                stats.getTimeSinceLastOptimizeMillis());
    }

    /**
     * This method will drop the earliest stats in the queue when the number of calls is at the
     * capacity specified by {@link FrameworkAppSearchConfig#getCachedApiCallStatsLimit()}.
     */
    @GuardedBy("mLock")
    private void trimExcessStatsQueueLocked() {
        final int n = mConfig.getCachedApiCallStatsLimit();
        if (n <= 0) {
            mLastNCalls.clear();
            return;
        }
        while (mLastNCalls.size() > n) {
            mLastNCalls.removeFirst();
        }
    }

    /**
     * Record {@link ApiCallRecord} to {@link #mLastNCalls} for dumpsys.
     *
     * <p> This method will automatically drop the earliest stats when the number of calls is at the
     * capacity specified by {@link FrameworkAppSearchConfig#getCachedApiCallStatsLimit()}.
     */
    @GuardedBy("mLock")
    @VisibleForTesting
    void addStatsToQueueLocked(@NonNull ApiCallRecord stats) {
        mLastNCalls.addLast(stats);
        trimExcessStatsQueueLocked();
    }

    /**
     * Calculate the hash code as an integer by returning the last four bytes of its MD5.
     *
     * @param str a string
     * @return hash code as an integer. returns -1 if str is null.
     * @throws AppSearchException if either algorithm or encoding does not exist.
     */
    @VisibleForTesting
    @NonNull
    static int calculateHashCodeMd5(@Nullable String str) throws
            NoSuchAlgorithmException, UnsupportedEncodingException {
        if (str == null) {
            return -1;
        }

        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(str.getBytes(/*charsetName=*/ "UTF-8"));
        byte[] digest = md.digest();

        // Since MD5 generates 16 bytes digest, we don't need to check the length here to see
        // if it is smaller than sizeof(int)(4).
        //
        // We generate the same value as BigInteger(digest).intValue().
        // BigInteger takes bytes[] and treat it as big endian. And its intValue() would get the
        // lower 4 bytes. So here we take the last 4 bytes and treat them as big endian.
        return (digest[12] & 0xFF) << 24
                | (digest[13] & 0xFF) << 16
                | (digest[14] & 0xFF) << 8
                | (digest[15] & 0xFF);
    }

    /**
     * Creates {@link ExtraStats} to hold additional information generated for logging.
     *
     * <p>This method is called by most of logStatsImplLocked functions to reduce code
     * duplication.
     */
    // TODO(b/173532925) Once we add CTS test for logging atoms and can inspect the result, we can
    // remove this @VisibleForTesting and directly use PlatformLogger.logStats to test sampling and
    // rate limiting.
    @VisibleForTesting
    @GuardedBy("mLock")
    @NonNull
    ExtraStats createExtraStatsLocked(@Nullable String packageName,
            @CallStats.CallType int callType) {
        int packageUid = Process.INVALID_UID;
        if (packageName != null) {
            packageUid = getPackageUidAsUserLocked(packageName);
        }

        // The sampling ratio here might be different from the one used in
        // shouldLogForTypeLocked if there is a config change in the middle.
        // Since it is only one sample, we can just ignore this difference.
        // Or we can retrieve samplingRatio at beginning and pass along
        // as function parameter, but it will make code less cleaner with some duplication.
        int samplingInterval = getSamplingIntervalFromConfig(callType);
        int skippedSampleCount = mSkippedSampleCountLocked.get(callType,
                /*valueOfKeyIfNotFound=*/ 0);
        mSkippedSampleCountLocked.put(callType, 0);

        return new ExtraStats(packageUid, samplingInterval, skippedSampleCount);
    }

    /**
     * Checks if this stats should be logged.
     *
     * <p>It won't be logged if it is "sampled" out, or it is too close to the previous logged
     * stats.
     */
    @GuardedBy("mLock")
    // TODO(b/173532925) Once we add CTS test for logging atoms and can inspect the result, we can
    // remove this @VisibleForTesting and directly use PlatformLogger.logStats to test sampling and
    // rate limiting.
    @VisibleForTesting
    boolean shouldLogForTypeLocked(@CallStats.CallType int callType) {
        int samplingInterval = getSamplingIntervalFromConfig(callType);
        // Sampling
        if (!shouldSample(samplingInterval)) {
            return false;
        }

        // Rate limiting
        // Check the timestamp to see if it is too close to last logged sample
        long currentTimeMillis = SystemClock.elapsedRealtime();
        if (mLastPushTimeMillisLocked
                > currentTimeMillis - mConfig.getCachedMinTimeIntervalBetweenSamplesMillis()) {
            int count = mSkippedSampleCountLocked.get(callType, /*valueOfKeyIfNotFound=*/ 0);
            ++count;
            mSkippedSampleCountLocked.put(callType, count);
            return false;
        }

        return true;
    }

    /**
     * Checks if the stats should be "sampled"
     *
     * @param samplingInterval sampling interval
     * @return if the stats should be sampled
     */
    private boolean shouldSample(int samplingInterval) {
        if (samplingInterval <= 0) {
            return false;
        }

        return mRng.nextInt((int) samplingInterval) == 0;
    }

    /**
     * Finds the UID of the {@code packageName}. Returns {@link Process#INVALID_UID} if unable to
     * find the UID.
     */
    @GuardedBy("mLock")
    private int getPackageUidAsUserLocked(@NonNull String packageName) {
        Integer packageUid = mPackageUidCacheLocked.get(packageName);
        if (packageUid == null) {
            packageUid = PackageUtil.getPackageUid(mUserContext, packageName);
            if (packageUid != Process.INVALID_UID) {
                mPackageUidCacheLocked.put(packageName, packageUid);
            }
        }
        return packageUid;
    }

    /** Returns sampling ratio for stats type specified form {@link FrameworkAppSearchConfig}. */
    private int getSamplingIntervalFromConfig(@CallStats.CallType int statsType) {
        switch (statsType) {
            case CallStats.CALL_TYPE_PUT_DOCUMENTS:
            case CallStats.CALL_TYPE_GET_DOCUMENTS:
            case CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_ID:
            case CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_SEARCH:
                return mConfig.getCachedSamplingIntervalForBatchCallStats();
            case CallStats.CALL_TYPE_PUT_DOCUMENT:
                return mConfig.getCachedSamplingIntervalForPutDocumentStats();
            case CallStats.CALL_TYPE_INITIALIZE:
                return mConfig.getCachedSamplingIntervalForInitializeStats();
            case CallStats.CALL_TYPE_SEARCH:
                return mConfig.getCachedSamplingIntervalForSearchStats();
            case CallStats.CALL_TYPE_GLOBAL_SEARCH:
                return mConfig.getCachedSamplingIntervalForGlobalSearchStats();
            case CallStats.CALL_TYPE_OPTIMIZE:
                return mConfig.getCachedSamplingIntervalForOptimizeStats();
            case CallStats.CALL_TYPE_UNKNOWN:
            case CallStats.CALL_TYPE_SET_SCHEMA:
            case CallStats.CALL_TYPE_GET_DOCUMENT:
            case CallStats.CALL_TYPE_REMOVE_DOCUMENT_BY_ID:
            case CallStats.CALL_TYPE_FLUSH:
            case CallStats.CALL_TYPE_REMOVE_DOCUMENT_BY_SEARCH:
            case CallStats.CALL_TYPE_GLOBAL_GET_DOCUMENT_BY_ID:
            case CallStats.CALL_TYPE_GLOBAL_GET_SCHEMA:
            case CallStats.CALL_TYPE_GET_SCHEMA:
            case CallStats.CALL_TYPE_GET_NAMESPACES:
            case CallStats.CALL_TYPE_GET_NEXT_PAGE:
            case CallStats.CALL_TYPE_INVALIDATE_NEXT_PAGE_TOKEN:
            case CallStats.CALL_TYPE_WRITE_SEARCH_RESULTS_TO_FILE:
            case CallStats.CALL_TYPE_PUT_DOCUMENTS_FROM_FILE:
            case CallStats.CALL_TYPE_SEARCH_SUGGESTION:
            case CallStats.CALL_TYPE_REPORT_SYSTEM_USAGE:
            case CallStats.CALL_TYPE_REPORT_USAGE:
            case CallStats.CALL_TYPE_GET_STORAGE_INFO:
            case CallStats.CALL_TYPE_REGISTER_OBSERVER_CALLBACK:
            case CallStats.CALL_TYPE_UNREGISTER_OBSERVER_CALLBACK:
            case CallStats.CALL_TYPE_GLOBAL_GET_NEXT_PAGE:
                // TODO(b/173532925) Some of them above will have dedicated sampling ratio config
            default:
                return mConfig.getCachedSamplingIntervalDefault();
        }
    }

    //
    // Functions below are used for tests only
    //
    @VisibleForTesting
    @GuardedBy("mLock")
    void setLastPushTimeMillisLocked(long lastPushElapsedTimeMillis) {
        mLastPushTimeMillisLocked = lastPushElapsedTimeMillis;
    }
}
