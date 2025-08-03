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

package com.android.adservices.service.common;

import android.annotation.NonNull;
import android.os.Binder;
import android.util.Pair;

import com.android.adservices.service.Flags;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.RateLimiter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Class to throttle PPAPI requests. */
public class Throttler {
    // Enum for each PP API or entry point that will be throttled.
    public enum ApiKey {
        UNKNOWN,

        // Key to throttle AdId API, based on app package name.
        ADID_API_APP_PACKAGE_NAME,

        // Key to throttle AppSetId API, based on app package name.
        APPSETID_API_APP_PACKAGE_NAME,

        // Key to throttle Join Custom Audience API
        FLEDGE_API_JOIN_CUSTOM_AUDIENCE,

        // Key to throttle Fetch Custom Audience API
        FLEDGE_API_FETCH_CUSTOM_AUDIENCE,

        // Key to throttle Leave Custom Audience API
        FLEDGE_API_LEAVE_CUSTOM_AUDIENCE,

        // Key to throttle Report impressions API
        FLEDGE_API_REPORT_IMPRESSIONS,

        // Key to throttle Report impressions API
        FLEDGE_API_REPORT_INTERACTION,

        // Key to throttle Select Ads API
        FLEDGE_API_SELECT_ADS,

        // Key to throttle Get Ad Selection Data API
        FLEDGE_API_GET_AD_SELECTION_DATA,
        // Key to throttle Persist Ad Selection Result API
        FLEDGE_API_PERSIST_AD_SELECTION_RESULT,

        // Key to throttle Schedule Custom Audience Update API
        FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE,

        // Key to throttle Set App Install Advertisers API
        FLEDGE_API_SET_APP_INSTALL_ADVERTISERS,

        // Key to throttle FLEDGE updateAdCounterHistogram API
        FLEDGE_API_UPDATE_AD_COUNTER_HISTOGRAM,

        // Key to throttle Measurement Deletion Registration API
        MEASUREMENT_API_DELETION_REGISTRATION,

        // Key to throttle Measurement Register Source API
        MEASUREMENT_API_REGISTER_SOURCE,

        // Key to throttle Measurement Register Trigger API
        MEASUREMENT_API_REGISTER_TRIGGER,

        // Key to throttle Measurement Register Web Source API
        MEASUREMENT_API_REGISTER_WEB_SOURCE,

        // Key to throttle Measurement Register Web Trigger API
        MEASUREMENT_API_REGISTER_WEB_TRIGGER,

        // Key to throttle Measurement Register Sources API
        MEASUREMENT_API_REGISTER_SOURCES,
        // Key to throttle updateSignals API
        PROTECTED_SIGNAL_API_UPDATE_SIGNALS,

        // Key to throttle Topics API based on the App Package Name.
        TOPICS_API_APP_PACKAGE_NAME,

        // Key to throttle Topics API based on the Sdk Name.
        TOPICS_API_SDK_NAME,
    }

    private static volatile Throttler sSingleton;
    private static final double DEFAULT_RATE_LIMIT = 1d;

    // A Map from a Pair<ApiKey, Requester> to its RateLimiter.
    // The Requester could be a SdkName or an AppPackageName depending on the rate limiting needs.
    // Example Pair<TOPICS_API, "SomeSdkName">, Pair<TOPICS_API, "SomePackageName">.
    private final ConcurrentHashMap<Pair<ApiKey, String>, RateLimiter> mSdkRateLimitMap =
            new ConcurrentHashMap<>();

    // Used as a configuration to determine the rate limit per API
    // Example:
    // - TOPICS_API_SDK_NAME, 1 request per second
    // - MEASUREMENT_API_REGISTER_SOURCE, 5 requests per second
    private final Map<ApiKey, Double> mRateLimitPerApiMap = new HashMap<>();

    /** Returns the singleton instance of the Throttler. */
    @NonNull
    public static Throttler getInstance(@NonNull Flags flags) {
        Objects.requireNonNull(flags);
        synchronized (Throttler.class) {
            if (null == sSingleton) {
                // Clearing calling identity to check device config permission read by flags on the
                // local process and not on the process called. Once the device configs are read,
                // restore calling identity.
                final long token = Binder.clearCallingIdentity();
                sSingleton = new Throttler(flags);
                Binder.restoreCallingIdentity(token);
            }
            return sSingleton;
        }
    }

    @VisibleForTesting
    Throttler(Flags flags) {
        setRateLimitPerApiMap(flags);
    }

    /**
     * The throttler is a Singleton and does not allow changing the rate limits once initialised,
     * therefore it is not feasible to test different throttling policies across tests without
     * destroying the previous instance. Intended to be used in test cases only.
     */
    @VisibleForTesting
    public static void destroyExistingThrottler() {
        sSingleton = null;
    }

    /**
     * Acquires a permit for an API and a Requester if it can be acquired immediately without delay.
     * Example: {@code tryAcquire(TOPICS_API, "SomeSdkName") }
     *
     * @return {@code true} if the permit was acquired, {@code false} otherwise
     */
    public boolean tryAcquire(ApiKey apiKey, String requester) {
        // Negative Permits Per Second turns off rate limiting.
        final double permitsPerSecond =
                mRateLimitPerApiMap.getOrDefault(apiKey, DEFAULT_RATE_LIMIT);
        if (permitsPerSecond <= 0) {
            return true;
        }

        final RateLimiter rateLimiter =
                mSdkRateLimitMap.computeIfAbsent(
                        Pair.create(apiKey, requester), ignored -> create(permitsPerSecond));

        return rateLimiter.tryAcquire();
    }

    /** Configures permits per second per {@link ApiKey} */
    private void setRateLimitPerApiMap(Flags flags) {
        final double defaultPermitsPerSecond = flags.getSdkRequestPermitsPerSecond();
        final double adIdPermitsPerSecond = flags.getAdIdRequestPermitsPerSecond();
        final double appSetIdPermitsPerSecond = flags.getAppSetIdRequestPermitsPerSecond();
        final double registerSource = flags.getMeasurementRegisterSourceRequestPermitsPerSecond();
        final double registerWebSource =
                flags.getMeasurementRegisterWebSourceRequestPermitsPerSecond();
        final double registerSources = flags.getMeasurementRegisterSourcesRequestPermitsPerSecond();
        final double registerTrigger = flags.getMeasurementRegisterTriggerRequestPermitsPerSecond();
        final double registerWebTrigger =
                flags.getMeasurementRegisterWebTriggerRequestPermitsPerSecond();
        final double topicsApiAppRequestPermitsPerSecond =
                flags.getTopicsApiAppRequestPermitsPerSecond();
        final double topicsApiSdkRequestPermitsPerSecond =
                flags.getTopicsApiSdkRequestPermitsPerSecond();

        mRateLimitPerApiMap.put(ApiKey.UNKNOWN, defaultPermitsPerSecond);

        mRateLimitPerApiMap.put(ApiKey.ADID_API_APP_PACKAGE_NAME, adIdPermitsPerSecond);
        mRateLimitPerApiMap.put(ApiKey.APPSETID_API_APP_PACKAGE_NAME, appSetIdPermitsPerSecond);

        mRateLimitPerApiMap.put(ApiKey.FLEDGE_API_JOIN_CUSTOM_AUDIENCE, defaultPermitsPerSecond);
        mRateLimitPerApiMap.put(ApiKey.FLEDGE_API_LEAVE_CUSTOM_AUDIENCE, defaultPermitsPerSecond);
        mRateLimitPerApiMap.put(ApiKey.FLEDGE_API_REPORT_IMPRESSIONS, defaultPermitsPerSecond);
        mRateLimitPerApiMap.put(ApiKey.FLEDGE_API_REPORT_INTERACTION, defaultPermitsPerSecond);
        mRateLimitPerApiMap.put(ApiKey.FLEDGE_API_SELECT_ADS, defaultPermitsPerSecond);
        mRateLimitPerApiMap.put(ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA, defaultPermitsPerSecond);
        mRateLimitPerApiMap.put(
                ApiKey.FLEDGE_API_UPDATE_AD_COUNTER_HISTOGRAM, defaultPermitsPerSecond);

        mRateLimitPerApiMap.put(
                ApiKey.PROTECTED_SIGNAL_API_UPDATE_SIGNALS, defaultPermitsPerSecond);

        mRateLimitPerApiMap.put(
                ApiKey.MEASUREMENT_API_DELETION_REGISTRATION, defaultPermitsPerSecond);
        mRateLimitPerApiMap.put(ApiKey.MEASUREMENT_API_REGISTER_SOURCE, registerSource);
        mRateLimitPerApiMap.put(ApiKey.MEASUREMENT_API_REGISTER_TRIGGER, registerTrigger);
        mRateLimitPerApiMap.put(ApiKey.MEASUREMENT_API_REGISTER_WEB_SOURCE, registerWebSource);
        mRateLimitPerApiMap.put(ApiKey.MEASUREMENT_API_REGISTER_WEB_TRIGGER, registerWebTrigger);
        mRateLimitPerApiMap.put(ApiKey.MEASUREMENT_API_REGISTER_SOURCES, registerSources);

        mRateLimitPerApiMap.put(
                ApiKey.TOPICS_API_APP_PACKAGE_NAME, topicsApiAppRequestPermitsPerSecond);
        mRateLimitPerApiMap.put(ApiKey.TOPICS_API_SDK_NAME, topicsApiSdkRequestPermitsPerSecond);
    }

    /**
     * Creates a Burst RateLimiter. This is a workaround since Guava does not support RateLimiter
     * with initial Burst.
     *
     * <p>The RateLimiter is created with {@link Double#POSITIVE_INFINITY} to open all permit slots
     * immediately. It is immediately overridden to the expected rate based on the permitsPerSecond
     * parameter. Then {@link RateLimiter#tryAcquire()} is called to use the first acquisition so
     * the expected bursting rate could kick in on the following calls. This flow enables initial
     * bursting, multiple simultaneous permits would be acquired as soon as RateLimiter is created.
     * Otherwise, if only {@link RateLimiter#create(double)} is called, after the 1st call
     * subsequent request would have to be spread out evenly over 1 second.
     */
    private RateLimiter create(double permitsPerSecond) {
        RateLimiter rateLimiter = RateLimiter.create(Double.POSITIVE_INFINITY);
        rateLimiter.setRate(permitsPerSecond);
        boolean unused = rateLimiter.tryAcquire();
        return rateLimiter;
    }
}
