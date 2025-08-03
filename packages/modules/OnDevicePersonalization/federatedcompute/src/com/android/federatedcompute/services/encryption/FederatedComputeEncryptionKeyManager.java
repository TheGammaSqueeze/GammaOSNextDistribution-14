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

package com.android.federatedcompute.services.encryption;

import android.content.Context;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKey;
import com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyDao;
import com.android.federatedcompute.services.http.FederatedComputeHttpRequest;
import com.android.federatedcompute.services.http.FederatedComputeHttpResponse;
import com.android.federatedcompute.services.http.HttpClient;
import com.android.federatedcompute.services.http.HttpClientUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Class to manage key fetch. */
public class FederatedComputeEncryptionKeyManager {
    private static final String TAG = "FederatedComputeEncryptionKeyManager";

    private interface EncryptionKeyResponseContract {
        String RESPONSE_HEADER_CACHE_CONTROL_LABEL = "cache-control";
        String RESPONSE_HEADER_AGE_LABEL = "age";

        String RESPONSE_HEADER_CACHE_CONTROL_MAX_AGE_LABEL = "max-age=";

        String RESPONSE_KEYS_LABEL = "keys";

        String RESPONSE_KEY_ID_LABEL = "id";

        String RESPONSE_PUBLIC_KEY = "key";
    }

    @VisibleForTesting private final FederatedComputeEncryptionKeyDao mEncryptionKeyDao;

    private static volatile FederatedComputeEncryptionKeyManager sBackgroundKeyManager;

    private final Clock mClock;

    private final Flags mFlags;

    private final HttpClient mHttpClient;

    private final ExecutorService mBackgroundExecutor;

    public FederatedComputeEncryptionKeyManager(
            Clock clock,
            FederatedComputeEncryptionKeyDao encryptionKeyDao,
            Flags flags,
            HttpClient httpClient,
            ExecutorService backgroundExecutor) {
        mClock = clock;
        mEncryptionKeyDao = encryptionKeyDao;
        mFlags = flags;
        mHttpClient = httpClient;
        mBackgroundExecutor = backgroundExecutor;
    }

    /**
     * @return a singleton instance for key manager
     */
    public static FederatedComputeEncryptionKeyManager getInstance(Context context) {
        if (sBackgroundKeyManager == null) {
            synchronized (FederatedComputeEncryptionKeyManager.class) {
                if (sBackgroundKeyManager == null) {
                    FederatedComputeEncryptionKeyDao encryptionKeyDao =
                            FederatedComputeEncryptionKeyDao.getInstance(context);
                    HttpClient client = new HttpClient();
                    Clock clock = MonotonicClock.getInstance();
                    Flags flags = FlagsFactory.getFlags();
                    sBackgroundKeyManager =
                            new FederatedComputeEncryptionKeyManager(
                                    clock,
                                    encryptionKeyDao,
                                    flags,
                                    client,
                                    FederatedComputeExecutors.getBackgroundExecutor());
                }
            }
        }
        return sBackgroundKeyManager;
    }

    /** For testing only, returns an instance of key manager for test. */
    @VisibleForTesting
    public static FederatedComputeEncryptionKeyManager getInstanceForTest(
            Clock clock,
            FederatedComputeEncryptionKeyDao encryptionKeyDao,
            Flags flags,
            HttpClient client,
            ExecutorService executor) {
        if (sBackgroundKeyManager == null) {
            synchronized (FederatedComputeEncryptionKeyManager.class) {
                if (sBackgroundKeyManager == null) {
                    sBackgroundKeyManager =
                            new FederatedComputeEncryptionKeyManager(
                                    clock, encryptionKeyDao, flags, client, executor);
                }
            }
        }
        return sBackgroundKeyManager;
    }

    /**
     * Fetch the active key from the server, persists the fetched key to encryption_key table, and
     * deletes expired keys
     */
    public FluentFuture<List<FederatedComputeEncryptionKey>> fetchAndPersistActiveKeys(
            @FederatedComputeEncryptionKey.KeyType int keyType, boolean isScheduledJob) {
        String fetchUri = mFlags.getEncryptionKeyFetchUrl();
        if (fetchUri == null) {
            return FluentFuture.from(Futures.immediateFailedFuture(
                    new IllegalArgumentException("Url to fetch active encryption keys is null")));
        }

        FederatedComputeHttpRequest request;
        try {
            request =
                    FederatedComputeHttpRequest.create(
                            fetchUri,
                            HttpClientUtil.HttpMethod.GET,
                            new HashMap<String, String>(),
                            HttpClientUtil.EMPTY_BODY);
        } catch (Exception e) {
            return FluentFuture.from(Futures.immediateFailedFuture(e));
        }

        return FluentFuture.from(mHttpClient.performRequestAsyncWithRetry(request))
                .transform(
                        response ->
                                parseFetchEncryptionKeyPayload(
                                        response, keyType, mClock.currentTimeMillis()),
                        mBackgroundExecutor)
                .transform(
                        result -> {
                            result.forEach(mEncryptionKeyDao::insertEncryptionKey);
                            if (isScheduledJob) {
                                // When the job is a background scheduled job, delete the
                                // expired keys, otherwise, only fetch from the key server.
                                mEncryptionKeyDao.deleteExpiredKeys();
                            }
                            return result;
                        },
                        mBackgroundExecutor); // TODO: Add timeout controlled by Ph flags
    }

    private ImmutableList<FederatedComputeEncryptionKey> parseFetchEncryptionKeyPayload(
            FederatedComputeHttpResponse keyFetchResponse,
            @FederatedComputeEncryptionKey.KeyType int keyType,
            Long fetchTime) {
        String payload = new String(Objects.requireNonNull(keyFetchResponse.getPayload()));
        Map<String, List<String>> headers = keyFetchResponse.getHeaders();
        long ttlInSeconds = getTTL(headers);
        if (ttlInSeconds <= 0) {
            ttlInSeconds = mFlags.getFederatedComputeEncryptionKeyMaxAgeSeconds();
        }

        try {
            JSONObject responseObj = new JSONObject(payload);
            JSONArray keysArr =
                    responseObj.getJSONArray(EncryptionKeyResponseContract.RESPONSE_KEYS_LABEL);
            ImmutableList.Builder<FederatedComputeEncryptionKey> encryptionKeys =
                    ImmutableList.builder();

            for (int i = 0; i < keysArr.length(); i++) {
                JSONObject keyObj = keysArr.getJSONObject(i);
                FederatedComputeEncryptionKey key =
                        new FederatedComputeEncryptionKey.Builder()
                                .setKeyIdentifier(
                                        keyObj.getString(
                                                EncryptionKeyResponseContract
                                                        .RESPONSE_KEY_ID_LABEL))
                                .setPublicKey(
                                        keyObj.getString(
                                                EncryptionKeyResponseContract.RESPONSE_PUBLIC_KEY))
                                .setKeyType(keyType)
                                .setCreationTime(fetchTime)
                                .setExpiryTime(
                                        fetchTime + ttlInSeconds * 1000) // convert to milliseconds
                                .build();
                encryptionKeys.add(key);
            }
            return encryptionKeys.build();
        } catch (JSONException e) {
            LogUtil.e(TAG, "Invalid Json response: " + e.getMessage());
            return ImmutableList.of();
        }
    }

    /**
     * Parse the "age" and "cache-control" of response headers. Calculate the ttl of the current key
     * maxage (in cache-control) - age.
     *
     * @return the ttl in seconds of the keys.
     */
    @VisibleForTesting
    static long getTTL(Map<String, List<String>> headers) {
        String cacheControl = null;
        int cachedAge = 0;
        int remainingHeaders = 2;
        for (String key : headers.keySet()) {
            if (key != null) {
                if (key.equalsIgnoreCase(
                        EncryptionKeyResponseContract.RESPONSE_HEADER_CACHE_CONTROL_LABEL)) {
                    List<String> field = headers.get(key);
                    if (field != null && field.size() > 0) {
                        cacheControl = field.get(0).toLowerCase(Locale.ENGLISH);
                        remainingHeaders -= 1;
                    }

                } else if (key.equalsIgnoreCase(
                        EncryptionKeyResponseContract.RESPONSE_HEADER_AGE_LABEL)) {
                    List<String> field = headers.get(key);
                    if (field != null && field.size() > 0) {
                        try {
                            cachedAge = Integer.parseInt(field.get(0));
                        } catch (NumberFormatException e) {
                            LogUtil.e(TAG, "Error parsing age header");
                        }
                        remainingHeaders -= 1;
                    }
                }
            }
            if (remainingHeaders == 0) {
                break;
            }
        }
        if (cacheControl == null) {
            LogUtil.d(TAG, "Cache-Control header or value is missing");
            return 0;
        }

        String[] tokens = cacheControl.split(",", /* limit= */ 0);
        long maxAge = 0;
        for (String s : tokens) {
            String token = s.trim();
            if (token.startsWith(
                    EncryptionKeyResponseContract.RESPONSE_HEADER_CACHE_CONTROL_MAX_AGE_LABEL)) {
                try {
                    maxAge =
                            Long.parseLong(
                                    token.substring(
                                            /* beginIndex= */ EncryptionKeyResponseContract
                                                    .RESPONSE_HEADER_CACHE_CONTROL_MAX_AGE_LABEL
                                                    .length())); // in the format of
                    // "max-age=<number>"
                } catch (NumberFormatException e) {
                    LogUtil.d(TAG, "Failed to parse max-age value");
                    return 0;
                }
            }
        }
        if (maxAge == 0) {
            LogUtil.d(TAG, "max-age directive is missing");
            return 0;
        }
        return maxAge - cachedAge;
    }

    /** Get active keys, if there is no active key, then force a fetch from the key service.
     * In the case of key fetching from the key service, the http call
     * is executed on a BlockingExecutor.
     * @return The list of active keys.
     */
    public List<FederatedComputeEncryptionKey> getOrFetchActiveKeys(int keyType, int keyCount) {
        List<FederatedComputeEncryptionKey> activeKeys = mEncryptionKeyDao
                .getLatestExpiryNKeys(keyCount);
        if (activeKeys.size() > 0) {
            return activeKeys;
        }
        try {
            var fetchedKeysUnused = fetchAndPersistActiveKeys(keyType,
                    /* isScheduledJob= */ false).get(/* timeout= */ 5, TimeUnit.SECONDS);
            activeKeys = mEncryptionKeyDao.getLatestExpiryNKeys(keyCount);
            if (activeKeys.size() > 0) {
                return activeKeys;
            }
        } catch (TimeoutException e) {
            LogUtil.e(TAG, "Time out when forcing encryption key fetch: "
                    + e.getMessage());
        } catch (Exception e) {
            LogUtil.e(TAG, "Exception encountered when forcing encryption key fetch: "
                    + e.getMessage());
        }
        return activeKeys;
    }
}
