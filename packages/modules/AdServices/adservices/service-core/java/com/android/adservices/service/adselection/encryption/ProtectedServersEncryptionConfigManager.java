/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.adservices.data.adselection.DBProtectedServersEncryptionConfig;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.data.adselection.ProtectedServersEncryptionConfigDao;
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

public class ProtectedServersEncryptionConfigManager
        extends ProtectedServersEncryptionConfigManagerBase {
    private final ProtectedServersEncryptionConfigDao mProtectedServersEncryptionConfigDao;

    public ProtectedServersEncryptionConfigManager(
            @NonNull ProtectedServersEncryptionConfigDao protectedServersEncryptionConfigDao,
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

        Objects.requireNonNull(protectedServersEncryptionConfigDao);
        mProtectedServersEncryptionConfigDao = protectedServersEncryptionConfigDao;
    }

    @VisibleForTesting
    ProtectedServersEncryptionConfigManager(
            ProtectedServersEncryptionConfigDao protectedServersEncryptionConfigDao,
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
        mProtectedServersEncryptionConfigDao = protectedServersEncryptionConfigDao;
    }

    /**
     * Returns an ObliviousHttpKeyConfig consisting of the latest key of keyType fetched from the
     * given coordinator url. Can return null if no keys are available.
     */
    @Nullable
    public FluentFuture<ObliviousHttpKeyConfig> getLatestOhttpKeyConfigOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            long timeoutMs,
            @Nullable Uri coordinatorUrl) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_LATEST_OHTTP_KEY_CONFIG);

        Uri fetchUri =
                getKeyFetchUriOfType(
                        adSelectionEncryptionKeyType,
                        coordinatorUrl,
                        mFlags.getFledgeAuctionServerCoordinatorUrlAllowlist());
        if (fetchUri == null) {
            sLogger.e(
                    "Fetch URI shouldn't have been null."
                            + " This means fetching default key also failed.");

            throw new IllegalStateException(
                    "Uri to fetch active key of type "
                            + adSelectionEncryptionKeyType
                            + " is null.");
        }

        sLogger.v("Using the coordinator url : " + fetchUri.toString());

        return FluentFuture.from(
                        immediateFuture(
                                getLatestKeyFromDatabase(
                                        adSelectionEncryptionKeyType, fetchUri.toString())))
                .transformAsync(
                        encryptionKey ->
                                encryptionKey == null
                                        ? fetchPersistAndGetActiveKey(
                                                adSelectionEncryptionKeyType, fetchUri, timeoutMs)
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
     * Returns one of the latest key of given keyType.
     *
     * <p>Multiple keys of a given keyType with the same expiry are present in the Database. This
     * method selects one of the keys and returns it. Can return keys which have expired which could
     * be useful if no active keys are available. If null is returned, then no keys are available in
     * the db.
     */
    @Nullable
    public AdSelectionEncryptionKey getLatestKeyFromDatabase(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            @NonNull String coordinatorUrl) {
        List<DBProtectedServersEncryptionConfig> keys =
                mProtectedServersEncryptionConfigDao.getLatestExpiryNKeys(
                        EncryptionKeyConstants.from(adSelectionEncryptionKeyType),
                        coordinatorUrl,
                        getKeyCountForType(adSelectionEncryptionKeyType));

        return keys.isEmpty()
                ? null
                : selectRandomDbKeyAndParse(
                        keys.stream()
                                .map(object -> toDbEncryptionKey(object))
                                .collect(Collectors.toList()));
    }

    FluentFuture<AdSelectionEncryptionKey> fetchPersistAndGetActiveKey(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionKeyType,
            Uri coordinatorUrl,
            long timeoutMs) {
        Instant fetchInstant = mClock.instant();
        return fetchAndPersistActiveKeysOfType(
                        adSelectionKeyType, fetchInstant, timeoutMs, coordinatorUrl)
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
            Uri fetchUri) {

        return FluentFuture.from(fetchKeyPayload(adSelectionKeyType, fetchUri))
                .transform(
                        response -> parseKeyResponse(response, adSelectionKeyType),
                        mLightweightExecutor)
                .transform(
                        result -> {
                            sLogger.d("Persisting " + result.size() + " fetched active keys.");

                            List<DBProtectedServersEncryptionConfig> encryptionConfigs =
                                    result.stream()
                                            .map(
                                                    object ->
                                                            fromDbEncryptionKey(
                                                                    object, fetchUri.toString()))
                                            .collect(Collectors.toList());
                            mProtectedServersEncryptionConfigDao.insertKeys(encryptionConfigs);
                            mProtectedServersEncryptionConfigDao.deleteExpiredRows(
                                    adSelectionKeyType, fetchUri.toString(), keyExpiryInstant);
                            return result;
                        },
                        mLightweightExecutor)
                .withTimeout(timeoutMs, TimeUnit.MILLISECONDS, AdServicesExecutors.getScheduler());
    }

    /** Returns the AdSelectionEncryptionKeyType which are expired at the given instant. */
    public Set<Integer> getExpiredAdSelectionEncryptionKeyTypes(Instant keyExpiryInstant) {
        return mProtectedServersEncryptionConfigDao.getAllExpiredKeys(keyExpiryInstant).stream()
                .map(
                        key ->
                                EncryptionKeyConstants.toAdSelectionEncryptionKeyType(
                                        key.getEncryptionKeyType()))
                .collect(Collectors.toSet());
    }

    // TODO(b/325260373) : Have EncryptionKeyParsers return an object from which both
    // DBEncryptionKey and DBProtectedServersEncryptionConfig can derive their data from.
    private DBProtectedServersEncryptionConfig fromDbEncryptionKey(
            DBEncryptionKey key, String coordinatorUrl) {
        DBProtectedServersEncryptionConfig.Builder builder =
                DBProtectedServersEncryptionConfig.builder();

        return builder.setEncryptionKeyType(key.getEncryptionKeyType())
                .setPublicKey(key.getPublicKey())
                .setKeyIdentifier(key.getKeyIdentifier())
                .setCoordinatorUrl(coordinatorUrl)
                .setExpiryTtlSeconds(key.getExpiryTtlSeconds())
                .build();
    }

    private DBEncryptionKey toDbEncryptionKey(DBProtectedServersEncryptionConfig key) {
        DBEncryptionKey.Builder builder = DBEncryptionKey.builder();

        return builder.setEncryptionKeyType(key.getEncryptionKeyType())
                .setPublicKey(key.getPublicKey())
                .setKeyIdentifier(key.getKeyIdentifier())
                .setExpiryTtlSeconds(key.getExpiryTtlSeconds())
                .build();
    }

    private AdSelectionEncryptionKey selectRandomDbKeyAndParse(List<DBEncryptionKey> keys) {
        Random random = new Random();
        DBEncryptionKey randomKey = keys.get(random.nextInt(keys.size()));
        return parseDbEncryptionKey(randomKey);
    }
}
