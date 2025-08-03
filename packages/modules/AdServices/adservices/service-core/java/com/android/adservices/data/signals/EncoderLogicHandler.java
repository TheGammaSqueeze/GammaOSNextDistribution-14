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

package com.android.adservices.data.signals;

import android.adservices.common.AdTechIdentifier;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Responsible for handling downloads, updates & delete for encoder logics for buyers
 *
 * <p>Thread safety:
 *
 * <ol>
 *   <li>The updates are thread safe per buyer
 *   <li>Updates for one buyer do not block updates for other buyer
 * </ol>
 */
public class EncoderLogicHandler {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting static final String ENCODER_VERSION_RESPONSE_HEADER = "X_ENCODER_VERSION";
    @VisibleForTesting static final int FALLBACK_VERSION = 0;
    @NonNull private final EncoderPersistenceDao mEncoderPersistenceDao;
    @NonNull private final EncoderEndpointsDao mEncoderEndpointsDao;
    @NonNull private final EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    @NonNull private final ProtectedSignalsDao mProtectedSignalsDao;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;

    @NonNull
    private static final Map<AdTechIdentifier, ReentrantLock> BUYER_REENTRANT_LOCK_HASH_MAP =
            new HashMap<>();

    @NonNull
    private final ImmutableSet<String> mDownloadRequestProperties =
            ImmutableSet.of(ENCODER_VERSION_RESPONSE_HEADER);

    @VisibleForTesting
    public EncoderLogicHandler(
            @NonNull EncoderPersistenceDao encoderPersistenceDao,
            @NonNull EncoderEndpointsDao encoderEndpointsDao,
            @NonNull EncoderLogicMetadataDao encoderLogicMetadataDao,
            @NonNull ProtectedSignalsDao protectedSignalsDao,
            @NonNull AdServicesHttpsClient httpsClient,
            @NonNull ListeningExecutorService backgroundExecutorService) {
        Objects.requireNonNull(encoderPersistenceDao);
        Objects.requireNonNull(encoderEndpointsDao);
        Objects.requireNonNull(encoderLogicMetadataDao);
        Objects.requireNonNull(protectedSignalsDao);
        Objects.requireNonNull(httpsClient);
        Objects.requireNonNull(backgroundExecutorService);
        mEncoderPersistenceDao = encoderPersistenceDao;
        mEncoderEndpointsDao = encoderEndpointsDao;
        mEncoderLogicMetadataDao = encoderLogicMetadataDao;
        mProtectedSignalsDao = protectedSignalsDao;
        mAdServicesHttpsClient = httpsClient;
        mBackgroundExecutorService = backgroundExecutorService;
    }

    public EncoderLogicHandler(@NonNull Context context) {
        this(
                EncoderPersistenceDao.getInstance(context),
                ProtectedSignalsDatabase.getInstance(context).getEncoderEndpointsDao(),
                ProtectedSignalsDatabase.getInstance(context).getEncoderLogicMetadataDao(),
                ProtectedSignalsDatabase.getInstance(context).protectedSignalsDao(),
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBackgroundExecutor(),
                        CacheProviderFactory.createNoOpCache()),
                AdServicesExecutors.getBackgroundExecutor());
    }

    /**
     * When requested to update encoder for a buyer, following events take place
     *
     * <ol>
     *   <li>1. Fetch the encoding URI from {@link EncoderEndpointsDao}
     *   <li>2. Make a web request using {@link AdServicesHttpsClient} to download the encoder
     *   <li>3. Extract the encoder from the web-response and persist
     *       <ol>
     *         <li>3a. The encoder body is persisted in file storage using {@link
     *             EncoderPersistenceDao}
     *         <li>3b. The entry for the downloaded encoder and the version is persisted using
     *             {@link EncoderLogicMetadataDao}
     *       </ol>
     * </ol>
     *
     * @param buyer The buyer for which the encoder logic is required to be updated
     * @param devContext development context used for testing network calls
     * @return a Fluent Future with success or failure in the form of boolean
     */
    public FluentFuture<Boolean> downloadAndUpdate(
            @NonNull AdTechIdentifier buyer, @NonNull DevContext devContext) {
        Objects.requireNonNull(buyer);

        DBEncoderEndpoint encoderEndpoint = mEncoderEndpointsDao.getEndpoint(buyer);
        if (encoderEndpoint == null) {
            sLogger.v(
                    String.format(
                            "No encoder endpoint found for buyer: %s, skipping download and update",
                            buyer));
            return FluentFuture.from(Futures.immediateFuture(false));
        }

        AdServicesHttpClientRequest downloadRequest =
                AdServicesHttpClientRequest.builder()
                        .setUri(encoderEndpoint.getDownloadUri())
                        .setUseCache(false)
                        .setResponseHeaderKeys(mDownloadRequestProperties)
                        .setDevContext(devContext)
                        .build();
        sLogger.v(
                "Initiating encoder download request for buyer: %s, uri: %s",
                buyer, encoderEndpoint.getDownloadUri());
        FluentFuture<AdServicesHttpClientResponse> response =
                FluentFuture.from(mAdServicesHttpsClient.fetchPayload(downloadRequest));

        return response.transform(
                r -> extractAndPersistEncoder(buyer, r), mBackgroundExecutorService);
    }

    @VisibleForTesting
    protected boolean extractAndPersistEncoder(
            AdTechIdentifier buyer, AdServicesHttpClientResponse response) {

        if (response == null || response.getResponseBody().isEmpty()) {
            sLogger.e("Empty response from from client for downloading encoder");
            return false;
        }

        String encoderLogicBody = response.getResponseBody();

        int version = FALLBACK_VERSION;
        try {
            if (response.getResponseHeaders() != null
                    && response.getResponseHeaders().get(ENCODER_VERSION_RESPONSE_HEADER) != null) {
                version =
                        Integer.valueOf(
                                response.getResponseHeaders()
                                        .get(ENCODER_VERSION_RESPONSE_HEADER)
                                        .get(0));
            }

        } catch (NumberFormatException e) {
            sLogger.e("Invalid or missing version, setting to fallback: " + FALLBACK_VERSION);
        }

        DBEncoderLogicMetadata encoderLogicEntry =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setCreationTime(Instant.now())
                        .setVersion(version)
                        .build();
        boolean updateSucceeded = false;

        ReentrantLock buyerLock = getBuyerLock(buyer);
        if (buyerLock.tryLock()) {
            updateSucceeded = mEncoderPersistenceDao.persistEncoder(buyer, encoderLogicBody);

            if (updateSucceeded) {
                sLogger.v(
                        "Update for encoding logic on persistence layer succeeded, updating DB"
                                + " entry");
                mEncoderLogicMetadataDao.persistEncoderLogicMetadata(encoderLogicEntry);
            } else {
                sLogger.e(
                        "Update for encoding logic on persistence layer failed, skipping update"
                                + " entry");
            }
            buyerLock.unlock();
        }
        return updateSucceeded;
    }

    @VisibleForTesting
    protected ReentrantLock getBuyerLock(AdTechIdentifier buyer) {
        synchronized (BUYER_REENTRANT_LOCK_HASH_MAP) {
            ReentrantLock lock = BUYER_REENTRANT_LOCK_HASH_MAP.get(buyer);
            if (lock == null) {
                lock = new ReentrantLock();
                BUYER_REENTRANT_LOCK_HASH_MAP.put(buyer, lock);
            }
            return lock;
        }
    }

    /**
     * @return all the buyers that have registered their encoders
     */
    public List<AdTechIdentifier> getBuyersWithEncoders() {
        return mEncoderLogicMetadataDao.getAllBuyersWithRegisteredEncoders();
    }

    /** Returns all registered encoding logic metadata. */
    public List<DBEncoderLogicMetadata> getAllRegisteredEncoders() {
        return mEncoderLogicMetadataDao.getAllRegisteredEncoders();
    }

    /** Returns the encoding logic for the given buyer. */
    public String getEncoder(AdTechIdentifier buyer) {
        return mEncoderPersistenceDao.getEncoder(buyer);
    }

    /** Returns the encoder metadata for the given buyer. */
    public DBEncoderLogicMetadata getEncoderLogicMetadata(AdTechIdentifier adTechIdentifier) {
        return mEncoderLogicMetadataDao.getMetadata(adTechIdentifier);
    }

    /** Update the failed count for a buyer */
    public void updateEncoderFailedCount(AdTechIdentifier adTechIdentifier, int count) {
        mEncoderLogicMetadataDao.updateEncoderFailedCount(adTechIdentifier, count);
    }

    /**
     * @param expiry time before which the encoders are considered stale
     * @return the list of buyers that have stale encoders
     */
    public List<AdTechIdentifier> getBuyersWithStaleEncoders(Instant expiry) {
        return mEncoderLogicMetadataDao.getBuyersWithEncodersBeforeTime(expiry);
    }

    /** Deletes the encoder endpoint and logic for a list of buyers */
    public void deleteEncodersForBuyers(Set<AdTechIdentifier> buyers) {
        for (AdTechIdentifier buyer : buyers) {
            deleteEncoderForBuyer(buyer);
        }
    }

    /** Deletes the encoder endpoint and logic for a certain buyer. */
    public void deleteEncoderForBuyer(AdTechIdentifier buyer) {
        ReentrantLock buyerLock = getBuyerLock(buyer);
        if (buyerLock.tryLock()) {
            mEncoderLogicMetadataDao.deleteEncoder(buyer);
            mEncoderPersistenceDao.deleteEncoder(buyer);
            mEncoderEndpointsDao.deleteEncoderEndpoint(buyer);
            mProtectedSignalsDao.deleteSignalsUpdateMetadata(buyer);
            buyerLock.unlock();
        }
    }
}
