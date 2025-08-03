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

import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.RESPONSE_PROPERTIES_CONTENT_TYPE;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;

import java.security.spec.InvalidKeySpecException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public abstract class ProtectedServersEncryptionConfigManagerBase {
    protected static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    protected final Clock mClock;
    protected final ExecutorService mLightweightExecutor;

    protected final Flags mFlags;
    protected final AuctionEncryptionKeyParser mAuctionEncryptionKeyParser;
    protected final JoinEncryptionKeyParser mJoinEncryptionKeyParser;
    protected final AdServicesHttpsClient mAdServicesHttpsClient;

    @Nullable
    abstract FluentFuture<ObliviousHttpKeyConfig> getLatestOhttpKeyConfigOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            long timeoutMs,
            @Nullable Uri coordinatorUrl);

    abstract FluentFuture<List<DBEncryptionKey>> fetchAndPersistActiveKeysOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionKeyType,
            Instant keyExpiryInstant,
            long timeoutMs,
            @Nullable Uri coordinatorUrl);

    abstract Set<Integer> getExpiredAdSelectionEncryptionKeyTypes(Instant keyExpiryInstant);

    protected ProtectedServersEncryptionConfigManagerBase(
            @NonNull Flags flags,
            @NonNull Clock clock,
            @NonNull AuctionEncryptionKeyParser auctionEncryptionKeyParser,
            @NonNull JoinEncryptionKeyParser joinEncryptionKeyParser,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ExecutorService lightweightExecutor) {
        Objects.requireNonNull(flags);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(auctionEncryptionKeyParser);
        Objects.requireNonNull(joinEncryptionKeyParser);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(lightweightExecutor);

        this.mFlags = flags;
        this.mClock = clock;
        this.mAuctionEncryptionKeyParser = auctionEncryptionKeyParser;
        this.mJoinEncryptionKeyParser = joinEncryptionKeyParser;
        this.mAdServicesHttpsClient = adServicesHttpsClient;
        this.mLightweightExecutor = lightweightExecutor;
    }

    protected List<DBEncryptionKey> parseKeyResponse(
            AdServicesHttpClientResponse keyFetchResponse,
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int encryptionKeyType) {
        switch (encryptionKeyType) {
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION:
                return mAuctionEncryptionKeyParser.getDbEncryptionKeys(keyFetchResponse);
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN:
                return mJoinEncryptionKeyParser.getDbEncryptionKeys(keyFetchResponse);
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.UNASSIGNED:
            default:
                return ImmutableList.of();
        }
    }

    protected AdSelectionEncryptionKey parseDbEncryptionKey(DBEncryptionKey dbEncryptionKey) {
        switch (dbEncryptionKey.getEncryptionKeyType()) {
            case EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION:
                return mAuctionEncryptionKeyParser.parseDbEncryptionKey(dbEncryptionKey);
            case EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_JOIN:
                return mJoinEncryptionKeyParser.parseDbEncryptionKey(dbEncryptionKey);
            case EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_QUERY:
            default:
                return null;
        }
    }

    protected int getKeyCountForType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int type) {
        switch (type) {
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION:
                // For auctions, more than one key is fetched from the DB to mitigate impact
                // due to key leakage.
                return mFlags.getFledgeAuctionServerAuctionKeySharding();
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN:
                return 1;
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.UNASSIGNED:
            default:
                return 0;
        }
    }

    protected ObliviousHttpKeyConfig getOhttpKeyConfigForKey(AdSelectionEncryptionKey encryptionKey)
            throws InvalidKeySpecException {
        Objects.requireNonNull(encryptionKey);
        switch (encryptionKey.keyType()) {
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION:
                return mAuctionEncryptionKeyParser.getObliviousHttpKeyConfig(encryptionKey);
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN:
                return mJoinEncryptionKeyParser.getObliviousHttpKeyConfig(encryptionKey);
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.UNASSIGNED:
            default:
                throw new IllegalArgumentException(
                        "Encryption Key of given type is not supported.");
        }
    }

    protected ListenableFuture<AdServicesHttpClientResponse> fetchKeyPayload(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionKeyType,
            Uri fetchUri) {
        switch (adSelectionKeyType) {
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION:
                return mAdServicesHttpsClient.fetchPayload(
                        fetchUri, DevContext.createForDevOptionsDisabled());
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN:
                AdServicesHttpClientRequest fetchKeyRequest =
                        AdServicesHttpClientRequest.builder()
                                .setUri(fetchUri)
                                .setRequestProperties(REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                                .setResponseHeaderKeys(RESPONSE_PROPERTIES_CONTENT_TYPE)
                                .setDevContext(DevContext.createForDevOptionsDisabled())
                                .build();
                return mAdServicesHttpsClient.performRequestGetResponseInBase64String(
                        fetchKeyRequest);
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.UNASSIGNED:
            default:
                throw new IllegalStateException(
                        "AdSelectionEncryptionKeyType: "
                                + adSelectionKeyType
                                + " is not supported.");
        }
    }

    protected Uri getKeyFetchUriOfType(
            @AdSelectionEncryptionKey.AdSelectionEncryptionKeyType int adSelectionEncryptionKeyType,
            @Nullable Uri coordinatorUrl,
            @Nullable String allowList) {

        if (coordinatorUrl != null
                && allowList != null
                && adSelectionEncryptionKeyType
                        == AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION) {
            return getUriFromAllowlist(coordinatorUrl, allowList);
        }

        sLogger.v("The passed coordinatorUrl was null. Fetching default coordinator");

        switch (adSelectionEncryptionKeyType) {
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION:
                return Uri.parse(mFlags.getFledgeAuctionServerAuctionKeyFetchUri());
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN:
                return Uri.parse(mFlags.getFledgeAuctionServerJoinKeyFetchUri());
            case AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.UNASSIGNED:
            default:
                return null;
        }
    }

    private Uri getUriFromAllowlist(@Nullable Uri coordinatorUrl, String allowlist) {
        List<String> allowedUrls = AllowLists.splitAllowList(allowlist);

        for (String url : allowedUrls) {
            Uri allowedUri = Uri.parse(url);
            if (coordinatorUrl.getHost().equals(allowedUri.getHost())) {
                return allowedUri;
            }
        }

        return null;
    }
}
