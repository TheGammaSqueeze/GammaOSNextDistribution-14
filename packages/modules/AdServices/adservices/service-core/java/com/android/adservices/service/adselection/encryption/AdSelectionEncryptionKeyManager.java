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

package com.android.adservices.service.adselection.encryption;


import static com.google.common.util.concurrent.Futures.immediateFuture;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.data.adselection.EncryptionKeyDao;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.profiling.Tracing;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;

import java.security.spec.InvalidKeySpecException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Class to manage key fetch. */
public class AdSelectionEncryptionKeyManager extends ProtectedServersEncryptionConfigManagerBase {
    private final EncryptionKeyDao mEncryptionKeyDao;

    public AdSelectionEncryptionKeyManager(
            @NonNull EncryptionKeyDao encryptionKeyDao,
            @NonNull Flags flags,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ExecutorService lightweightExecutor) {
        super(
                flags,
                Clock.systemUTC(),
                new AuctionEncryptionKeyParser(flags),
                new JoinEncryptionKeyParser(flags),
                adServicesHttpsClient,
                lightweightExecutor);

        Objects.requireNonNull(encryptionKeyDao);
        mEncryptionKeyDao = encryptionKeyDao;
    }

    @VisibleForTesting
    AdSelectionEncryptionKeyManager(
            @NonNull EncryptionKeyDao encryptionKeyDao,
            @NonNull Flags flags,
            @NonNull Clock clock,
            @NonNull AuctionEncryptionKeyParser auctionEncryptionKeyParser,
            @NonNull JoinEncryptionKeyParser joinEncryptionKeyParser,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ExecutorService lightweightExecutor) {
        super(
                flags,
                clock,
                auctionEncryptionKeyParser,
                joinEncryptionKeyParser,
                adServicesHttpsClient,
                lightweightExecutor);
        mEncryptionKeyDao = encryptionKeyDao;
    }

    /**
     * Returns an ObliviousHttpKeyConfig consisting of the latest active key of keyType. Can return
     * null if no active keys are available.
     */
    @Nullable
    public FluentFuture<ObliviousHttpKeyConfig> getLatestActiveOhttpKeyConfigOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            long timeoutMs) {
        return FluentFuture.from(
                        immediateFuture(getLatestActiveKeyOfType(adSelectionEncryptionKeyType)))
                .transformAsync(
                        encryptionKey ->
                                encryptionKey == null
                                        ? fetchPersistAndGetActiveKeyOfType(
                                                adSelectionEncryptionKeyType, timeoutMs)
                                        : immediateFuture(encryptionKey),
                        mLightweightExecutor)
                .transform(
                        key -> {
                            try {
                                return getOhttpKeyConfigForKey(key);
                            } catch (InvalidKeySpecException e) {
                                // TODO(b/286839408): Delete all keys of given keyType if they
                                //  can't be parsed into key config.
                                throw new IllegalStateException(
                                        "Unable to parse the key into ObliviousHttpKeyConfig.");
                            }
                        },
                        mLightweightExecutor);
    }

    /**
     * Returns an ObliviousHttpKeyConfig consisting of the latest key of keyType. Can return null if
     * no keys are available. Ignores the passed coordinator in this implementation.
     */
    @Nullable
    public FluentFuture<ObliviousHttpKeyConfig> getLatestOhttpKeyConfigOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            long timeoutMs,
            @Nullable Uri coordinatorUrl) {
        sLogger.v("Ignoring the coordinatorUrl passed, if any.");
        return getLatestOhttpKeyConfigOfType(adSelectionEncryptionKeyType, timeoutMs);
    }

    /**
     * Returns an ObliviousHttpKeyConfig consisting of the latest key of keyType. The key might be
     * expired. Can return null if no keys are available.
     */
    @Nullable
    private FluentFuture<ObliviousHttpKeyConfig> getLatestOhttpKeyConfigOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            long timeoutMs) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_LATEST_OHTTP_KEY_CONFIG);
        return FluentFuture.from(immediateFuture(getLatestKeyOfType(adSelectionEncryptionKeyType)))
                .transformAsync(
                        encryptionKey ->
                                encryptionKey == null
                                        ? fetchPersistAndGetActiveKeyOfType(
                                                adSelectionEncryptionKeyType, timeoutMs)
                                        : immediateFuture(encryptionKey),
                        mLightweightExecutor)
                .transform(
                        key -> {
                            try {
                                ObliviousHttpKeyConfig configKey = getOhttpKeyConfigForKey(key);
                                Tracing.endAsyncSection(
                                        Tracing.GET_LATEST_OHTTP_KEY_CONFIG, traceCookie);
                                return configKey;
                            } catch (InvalidKeySpecException e) {
                                // TODO(b/286839408): Delete all keys of given keyType if they
                                //  can't be parsed into key config.
                                Tracing.endAsyncSection(
                                        Tracing.GET_LATEST_OHTTP_KEY_CONFIG, traceCookie);
                                throw new IllegalStateException(
                                        "Unable to parse the key into ObliviousHttpKeyConfig.");
                            }
                        },
                        mLightweightExecutor);
    }

    /**
     * Returns the latest active key of keyType JOIN. Can return null if no active keys are
     * available.
     */
    @Nullable
    public AdSelectionEncryptionKey getLatestActiveKeyOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType
                    int adSelectionEncryptionKeyType) {
        List<DBEncryptionKey> keys =
                mEncryptionKeyDao.getLatestExpiryNActiveKeysOfType(
                        EncryptionKeyConstants.from(adSelectionEncryptionKeyType),
                        mClock.instant(),
                        getKeyCountForType(adSelectionEncryptionKeyType));

        return keys.isEmpty() ? null : selectRandomDbKeyAndParse(keys);
    }

    /**
     * Returns one of the latest key of given keyType.
     *
     * <p>Multiple keys of a given keyType with the same expiry are present in the Database. This
     * method selects one of the keys and returns it. Can return keys which have expired which could
     * be useful if no active keys are available. If null is returned, then no keys are available in
     * the db.
     */
    @Nullable
    public AdSelectionEncryptionKey getLatestKeyOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType
                    int adSelectionEncryptionKeyType) {
        List<DBEncryptionKey> keys =
                mEncryptionKeyDao.getLatestExpiryNKeysOfType(
                        EncryptionKeyConstants.from(adSelectionEncryptionKeyType),
                        getKeyCountForType(adSelectionEncryptionKeyType));

        return keys.isEmpty() ? null : selectRandomDbKeyAndParse(keys);
    }

    /**
     * For given AdSelectionKeyType, this method does the following - 1. Fetches the active key from
     * the server. 2. Once the active keys are fetched, it persists the fetched key to
     * db_encryption_key table. 3. Deletes the expired keys of given type. 4. Returns one of the
     * latest active key.
     */
    public FluentFuture<AdSelectionEncryptionKey> fetchPersistAndGetActiveKeyOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionKeyType,
            long timeoutMs) {
        Instant fetchInstant = mClock.instant();
        return fetchAndPersistActiveKeysOfType(adSelectionKeyType, fetchInstant, timeoutMs, null)
                .transform(keys -> selectRandomDbKeyAndParse(keys), mLightweightExecutor);
    }

    /**
     * For given AdSelectionKeyType, this method does the following - 1. Fetches the active key from
     * the server. 2. Once the active keys are fetched, it persists the fetched key to
     * db_encryption_key table. 3. Deletes the expired keys of given type and which expired at the
     * given instant.
     */
    public FluentFuture<List<DBEncryptionKey>> fetchAndPersistActiveKeysOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionKeyType,
            Instant keyExpiryInstant,
            long timeoutMs,
            @Nullable Uri unusedCoordinatorUrl) {
        Uri fetchUri = getKeyFetchUriOfType(adSelectionKeyType, null, null);
        if (fetchUri == null) {
            throw new IllegalStateException(
                    "Uri to fetch active key of type " + adSelectionKeyType + " is null.");
        }

        return FluentFuture.from(fetchKeyPayload(adSelectionKeyType, fetchUri))
                .transform(
                        response -> parseKeyResponse(response, adSelectionKeyType),
                        mLightweightExecutor)
                .transform(
                        result -> {
                            sLogger.d("Persisting " + result.size() + " fetched active keys.");

                            mEncryptionKeyDao.insertAllKeys(result);
                            mEncryptionKeyDao.deleteExpiredRowsByType(
                                    adSelectionKeyType, keyExpiryInstant);
                            return result;
                        },
                        mLightweightExecutor)
                .withTimeout(timeoutMs, TimeUnit.MILLISECONDS, AdServicesExecutors.getScheduler());
    }

    /** Returns the AdSelectionEncryptionKeyType which are expired at the given instant. */
    public Set<Integer> getExpiredAdSelectionEncryptionKeyTypes(Instant keyExpiryInstant) {
        return mEncryptionKeyDao.getExpiredKeys(keyExpiryInstant).stream()
                .map(
                        key ->
                                EncryptionKeyConstants.toAdSelectionEncryptionKeyType(
                                        key.getEncryptionKeyType()))
                .collect(Collectors.toSet());
    }

    private AdSelectionEncryptionKey selectRandomDbKeyAndParse(List<DBEncryptionKey> keys) {
        Random random = new Random();
        return parseDbEncryptionKey(keys.get(random.nextInt(keys.size())));
    }

}
