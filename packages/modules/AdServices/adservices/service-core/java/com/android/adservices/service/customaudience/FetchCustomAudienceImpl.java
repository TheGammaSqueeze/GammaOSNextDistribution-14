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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.ValidatorUtil.AD_TECH_ROLE_BUYER;
import static com.android.adservices.service.customaudience.CustomAudienceBlob.AUCTION_SERVER_REQUEST_FLAGS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.adservices.common.AdData;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.FetchAndJoinCustomAudienceCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.exceptions.RetryableAdServicesNetworkException;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.Build;
import android.os.LimitExceededException;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceQuarantine;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.JsonValidator;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InvalidObjectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Implementation of Fetch Custom Audience. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class FetchCustomAudienceImpl {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final int API_NAME =
            AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
    private static final String CUSTOM_AUDIENCE_HEADER = "X-CUSTOM-AUDIENCE-DATA";
    public static final String REQUEST_CUSTOM_HEADER_EXCEEDS_SIZE_LIMIT_MESSAGE =
            "Size of custom headers exceeds limit.";

    public static final String FUSED_CUSTOM_AUDIENCE_INCOMPLETE_MESSAGE =
            "Fused custom audience is incomplete.";
    public static final String FUSED_CUSTOM_AUDIENCE_EXCEEDS_SIZE_LIMIT_MESSAGE =
            "Fused custom audience exceeds size limit.";

    // Placeholder value to be used with the CustomAudienceQuantityChecker
    private static final CustomAudience PLACEHOLDER_CUSTOM_AUDIENCE =
            new CustomAudience.Builder()
                    .setName("placeholder")
                    .setBuyer(AdTechIdentifier.fromString("buyer.com"))
                    .setDailyUpdateUri(Uri.parse("https://www.buyer.com/update"))
                    .setBiddingLogicUri(Uri.parse("https://www.buyer.com/bidding"))
                    .build();
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final ListeningExecutorService mExecutorService;
    @NonNull private final CustomAudienceServiceFilter mCustomAudienceServiceFilter;
    @NonNull private final AdServicesHttpsClient mHttpClient;
    @NonNull private final Clock mClock;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final CustomAudienceQuantityChecker mCustomAudienceQuantityChecker;
    @NonNull private final CustomAudienceBlobValidator mCustomAudienceBlobValidator;
    @NonNull private final boolean mFledgeFetchCustomAudienceEnabled;
    @NonNull private final boolean mDisableFledgeEnrollmentCheck;
    @NonNull private final boolean mEnforceForegroundStatus;
    @NonNull private final int mMaxNameSizeB;
    @NonNull private final int mMaxUserBiddingSignalsSizeB;
    @NonNull private final long mMaxActivationDelayInMs;
    @NonNull private final long mMaxExpireInMs;
    @NonNull private final int mMaxBiddingLogicUriSizeB;
    @NonNull private final int mMaxDailyUpdateUriSizeB;
    @NonNull private final int mMaxTrustedBiddingDataSizeB;
    @NonNull private final int mFledgeCustomAudienceMaxAdsSizeB;
    @NonNull private final int mFledgeCustomAudienceMaxNumAds;
    @NonNull private final int mFledgeCustomAudienceMaxCustomHeaderSizeB;
    @NonNull private final int mFledgeCustomAudienceMaxCustomAudienceSizeB;
    @NonNull private final long mFledgeCustomAuienceMaxTotal;
    private final boolean mFledgeAdSelectionFilteringEnabled;
    private final boolean mFledgeAuctionServerAdRenderIdEnabled;
    private final boolean mAuctionServerRequestFlagsEnabled;
    private final long mFledgeAuctionServerAdRenderIdMaxLength;
    private final long mDefaultRetryDurationSeconds;
    private final long mMaxRetryDurationSeconds;

    // TODO(b/289123035): Make these locally scoped, passed down by the orchestrator function.
    @NonNull private AdTechIdentifier mBuyer;
    @NonNull private String mOwner;
    @NonNull private Uri mFetchUri;
    @NonNull private CustomAudienceBlob mRequestCustomAudience;
    @NonNull private CustomAudienceBlob mResponseCustomAudience;
    @NonNull private CustomAudienceBlob mFusedCustomAudience;
    private final int mCallingAppUid;
    @NonNull private String mCallerAppPackageName;

    public FetchCustomAudienceImpl(
            @NonNull Flags flags,
            @NonNull Clock clock,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull ExecutorService executor,
            @NonNull CustomAudienceDao customAudienceDao,
            int callingAppUid,
            @NonNull CustomAudienceServiceFilter customAudienceServiceFilter,
            @NonNull AdServicesHttpsClient httpClient,
            @NonNull FrequencyCapAdDataValidator frequencyCapAdDataValidator,
            @NonNull AdRenderIdValidator adRenderIdValidator,
            @NonNull AdDataConversionStrategy adDataConversionStrategy) {
        Objects.requireNonNull(flags);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(customAudienceServiceFilter);
        Objects.requireNonNull(httpClient);

        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        mExecutorService = MoreExecutors.listeningDecorator(executor);
        mCustomAudienceDao = customAudienceDao;
        mCallingAppUid = callingAppUid;
        mCustomAudienceServiceFilter = customAudienceServiceFilter;
        mHttpClient = httpClient;
        mCustomAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(customAudienceDao, flags);

        // TODO(b/278016820): Revisit handling field limit validation.
        // Ensuring process-stable flag values by assigning to local variables at instantiation.
        mFledgeFetchCustomAudienceEnabled = flags.getFledgeFetchCustomAudienceEnabled();
        mDisableFledgeEnrollmentCheck = flags.getDisableFledgeEnrollmentCheck();
        mEnforceForegroundStatus = flags.getEnforceForegroundStatusForFledgeCustomAudience();
        mMaxNameSizeB = flags.getFledgeCustomAudienceMaxNameSizeB();
        mMaxActivationDelayInMs = flags.getFledgeCustomAudienceMaxActivationDelayInMs();
        mMaxExpireInMs = flags.getFledgeCustomAudienceMaxExpireInMs();
        mMaxUserBiddingSignalsSizeB =
                flags.getFledgeFetchCustomAudienceMaxUserBiddingSignalsSizeB();
        mMaxBiddingLogicUriSizeB = flags.getFledgeCustomAudienceMaxBiddingLogicUriSizeB();
        mMaxDailyUpdateUriSizeB = flags.getFledgeCustomAudienceMaxDailyUpdateUriSizeB();
        mMaxTrustedBiddingDataSizeB = flags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB();
        mFledgeCustomAudienceMaxAdsSizeB = flags.getFledgeCustomAudienceMaxAdsSizeB();
        mFledgeCustomAudienceMaxNumAds = flags.getFledgeCustomAudienceMaxNumAds();
        mFledgeAdSelectionFilteringEnabled = flags.getFledgeAdSelectionFilteringEnabled();
        mFledgeAuctionServerAdRenderIdEnabled = flags.getFledgeAuctionServerAdRenderIdEnabled();
        mAuctionServerRequestFlagsEnabled = flags.getFledgeAuctionServerRequestFlagsEnabled();
        mFledgeAuctionServerAdRenderIdMaxLength = flags.getFledgeAuctionServerAdRenderIdMaxLength();
        mFledgeCustomAudienceMaxCustomHeaderSizeB =
                flags.getFledgeFetchCustomAudienceMaxRequestCustomHeaderSizeB();
        mFledgeCustomAudienceMaxCustomAudienceSizeB =
                flags.getFledgeFetchCustomAudienceMaxCustomAudienceSizeB();
        mFledgeCustomAuienceMaxTotal = flags.getFledgeCustomAudienceMaxCount();
        mDefaultRetryDurationSeconds = flags.getFledgeFetchCustomAudienceMinRetryAfterValueMs();
        mMaxRetryDurationSeconds = flags.getFledgeFetchCustomAudienceMaxRetryAfterValueMs();
        // Instantiate request, response and result CustomAudienceBlobs
        mRequestCustomAudience =
                new CustomAudienceBlob(
                        mFledgeAdSelectionFilteringEnabled,
                        mFledgeAuctionServerAdRenderIdEnabled,
                        mFledgeAuctionServerAdRenderIdMaxLength,
                        mAuctionServerRequestFlagsEnabled);
        mResponseCustomAudience =
                new CustomAudienceBlob(
                        mFledgeAdSelectionFilteringEnabled,
                        mFledgeAuctionServerAdRenderIdEnabled,
                        mFledgeAuctionServerAdRenderIdMaxLength,
                        mAuctionServerRequestFlagsEnabled);
        mFusedCustomAudience =
                new CustomAudienceBlob(
                        mFledgeAdSelectionFilteringEnabled,
                        mFledgeAuctionServerAdRenderIdEnabled,
                        mFledgeAuctionServerAdRenderIdMaxLength,
                        mAuctionServerRequestFlagsEnabled);

        // Instantiate a CustomAudienceBlobValidator
        mCustomAudienceBlobValidator =
                new CustomAudienceBlobValidator(
                        clock,
                        new CustomAudienceNameValidator(mMaxNameSizeB),
                        new CustomAudienceUserBiddingSignalsValidator(
                                new JsonValidator(
                                        CustomAudienceBlobValidator.CLASS_NAME,
                                        USER_BIDDING_SIGNALS_KEY),
                                mMaxUserBiddingSignalsSizeB),
                        new CustomAudienceActivationTimeValidator(
                                clock, Duration.ofMillis(mMaxActivationDelayInMs)),
                        new CustomAudienceExpirationTimeValidator(
                                clock, Duration.ofMillis(mMaxExpireInMs)),
                        new AdTechIdentifierValidator(
                                CustomAudienceBlobValidator.CLASS_NAME, AD_TECH_ROLE_BUYER),
                        new CustomAudienceBiddingLogicUriValidator(mMaxBiddingLogicUriSizeB),
                        new CustomAudienceDailyUpdateUriValidator(mMaxDailyUpdateUriSizeB),
                        new TrustedBiddingDataValidator(mMaxTrustedBiddingDataSizeB),
                        new CustomAudienceAdsValidator(
                                frequencyCapAdDataValidator,
                                adRenderIdValidator,
                                adDataConversionStrategy,
                                mFledgeCustomAudienceMaxAdsSizeB,
                                mFledgeCustomAudienceMaxNumAds));
    }

    /** Adds a user to a fetched custom audience. */
    public void doFetchCustomAudience(
            @NonNull FetchAndJoinCustomAudienceInput request,
            @NonNull FetchAndJoinCustomAudienceCallback callback,
            @NonNull DevContext devContext) {
        try {
            mCallerAppPackageName = request.getCallerPackageName();
            // Failing fast and silently if fetchCustomAudience is disabled.
            if (!mFledgeFetchCustomAudienceEnabled) {
                sLogger.v("fetchCustomAudience is disabled.");
                throw new IllegalStateException("fetchCustomAudience is disabled.");
            } else {
                sLogger.v("fetchCustomAudience is enabled.");
                // TODO(b/282017342): Evaluate correctness of futures chain.
                filterAndValidateRequest(request, devContext)
                        .transformAsync(ignoredVoid -> performFetch(devContext), mExecutorService)
                        .transformAsync(this::validateResponse, mExecutorService)
                        .transformAsync(
                                ignoredVoid -> persistResponse(devContext), mExecutorService)
                        .addCallback(
                                new FutureCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void unusedResult) {
                                        sLogger.v("Completed fetchCustomAudience execution");
                                        notifySuccess(callback);
                                    }

                                    @Override
                                    public void onFailure(Throwable t) {
                                        sLogger.d(
                                                t,
                                                "Error encountered in fetchCustomAudience"
                                                        + " execution");
                                        if (t instanceof RetryableAdServicesNetworkException) {
                                            tryToPersistQuarantineEntryAndNotifyCaller(
                                                    ((RetryableAdServicesNetworkException) t),
                                                    callback);
                                        } else if (t instanceof FilterException
                                                && t.getCause()
                                                        instanceof
                                                        ConsentManager.RevokedConsentException) {
                                            // Skip logging if a FilterException occurs.
                                            // AdSelectionServiceFilter ensures the failing
                                            // assertion is logged
                                            // internally.

                                            // Fail Silently by notifying success to caller
                                            notifySuccess(callback);
                                        } else {
                                            notifyFailure(callback, t);
                                        }
                                    }
                                },
                                mExecutorService);
            }
        } catch (Throwable t) {
            notifyFailure(callback, t);
        }
    }

    private FluentFuture<Void> filterAndValidateRequest(
            @NonNull FetchAndJoinCustomAudienceInput input, @NonNull DevContext devContext) {
        mOwner = input.getCallerPackageName();
        return FluentFuture.from(
                mExecutorService.submit(
                        () -> {
                            sLogger.v("In fetchCustomAudience filterAndValidateRequest");
                            try {
                                // Extract buyer ad tech identifier and filter request
                                mBuyer =
                                        mCustomAudienceServiceFilter
                                                .filterRequestAndExtractIdentifier(
                                                        input.getFetchUri(),
                                                        input.getCallerPackageName(),
                                                        mDisableFledgeEnrollmentCheck,
                                                        mEnforceForegroundStatus,
                                                        true,
                                                        mCallingAppUid,
                                                        API_NAME,
                                                        FLEDGE_API_FETCH_CUSTOM_AUDIENCE,
                                                        devContext);
                            } catch (Throwable t) {
                                throw new FilterException(t);
                            }

                            // Ensure request is not quarantined
                            assertNotQuarantined();

                            // Check if Custom Audience API quota exists.
                            mCustomAudienceQuantityChecker.check(
                                    PLACEHOLDER_CUSTOM_AUDIENCE, input.getCallerPackageName());

                            // Validate request
                            mRequestCustomAudience.overrideFromFetchAndJoinCustomAudienceInput(
                                    input);
                            mRequestCustomAudience.setBuyer(mBuyer);
                            mCustomAudienceBlobValidator.validate(mRequestCustomAudience);

                            mFetchUri = input.getFetchUri();
                            sLogger.v("Completed fetchCustomAudience filterAndValidateRequest");
                            return null;
                        }));
    }

    private ListenableFuture<AdServicesHttpClientResponse> performFetch(
            @NonNull DevContext devContext) {
        sLogger.v("In fetchCustomAudience performFetch");

        // Optional fields as a json string.
        String jsonString = mRequestCustomAudience.asJSONObject().toString();

        // Validate size of headers.
        if (jsonString.getBytes(UTF_8).length > mFledgeCustomAudienceMaxCustomHeaderSizeB) {
            throw new IllegalArgumentException(REQUEST_CUSTOM_HEADER_EXCEEDS_SIZE_LIMIT_MESSAGE);
        }

        // Custom headers under X-CUSTOM-AUDIENCE-DATA
        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(CUSTOM_AUDIENCE_HEADER, jsonString);

        // GET request
        sLogger.v("Sending request from fetchCustomAudience performFetch");
        return mHttpClient.fetchPayload(
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(mFetchUri)
                        .setDevContext(devContext)
                        .build());
    }

    private void assertNotQuarantined() throws LimitExceededException {
        if (mCustomAudienceDao.doesCustomAudienceQuarantineExist(mOwner, mBuyer)) {
            Instant expiration =
                    mCustomAudienceDao.getCustomAudienceQuarantineExpiration(mOwner, mBuyer);
            Instant now = mClock.instant();
            if (now.isBefore(expiration)) {
                sLogger.d(
                        String.format(
                                "Combination of owner:%s and buyer%s is quarantined!",
                                mOwner, mBuyer.toString()));
                throw new LimitExceededException(
                        "This combination of owner and buyer is quarantined!");
            } else {
                sLogger.v("Clearing stale quarantine entry");
                mCustomAudienceDao.deleteQuarantineEntry(mOwner, mBuyer);
            }
        }
    }

    private ListenableFuture<Void> validateResponse(
            @NonNull AdServicesHttpClientResponse fetchResponse) throws JSONException {
        return FluentFuture.from(
                mExecutorService.submit(
                        () -> {
                            // Parse and validate the fetched HTTP response.
                            // Validate response is a well-formed JSON.
                            String responseJsonString = fetchResponse.getResponseBody();
                            JSONObject responseJson;
                            try {
                                responseJson = new JSONObject(responseJsonString);
                            } catch (JSONException exception) {
                                throw new InvalidObjectException(exception.getMessage());
                            }
                            // Populate the response custom audience from the valid JSON response.
                            mResponseCustomAudience.overrideFromJSONObject(responseJson);

                            // Construct and validate the fused custom audience.
                            // If a field has valid values in both the request and the response
                            // custom audiences, the value from the server response is discarded.
                            // TODO(b/283857101): Add an overrideFromCustomAudienceBlob() method.
                            mFusedCustomAudience.overrideFromJSONObject(
                                    mResponseCustomAudience.asJSONObject());
                            mFusedCustomAudience.overrideFromJSONObject(
                                    mRequestCustomAudience.asJSONObject());
                            // Validate the fused custom audience has values for all fields.
                            if (!isComplete(mFusedCustomAudience)) {
                                throw new InvalidObjectException(
                                        FUSED_CUSTOM_AUDIENCE_INCOMPLETE_MESSAGE);
                            }
                            // Validate the fields of the fused custom audience
                            mCustomAudienceBlobValidator.validate(mFusedCustomAudience);
                            // Validate the size of the fused custom audience
                            // TODO(b/283857101): Add a size() method.
                            if (mFusedCustomAudience
                                            .asJSONObject()
                                            .toString()
                                            .getBytes(UTF_8)
                                            .length
                                    > mFledgeCustomAudienceMaxCustomAudienceSizeB) {
                                throw new InvalidObjectException(
                                        FUSED_CUSTOM_AUDIENCE_EXCEEDS_SIZE_LIMIT_MESSAGE);
                            }
                            return null;
                        }));
    }

    private ListenableFuture<Void> persistResponse(DevContext devContext) {
        return FluentFuture.from(
                mExecutorService.submit(
                        () -> {
                            boolean isDebuggableCustomAudience = devContext.getDevOptionsEnabled();
                            sLogger.v(
                                    "Is debuggable custom audience: %b",
                                    isDebuggableCustomAudience);
                            // TODO(b/283857101): Add a asDBCustomAudience() method.
                            DBCustomAudience.Builder customAudienceBuilder =
                                    new DBCustomAudience.Builder()
                                            .setOwner(mFusedCustomAudience.getOwner())
                                            .setBuyer(mFusedCustomAudience.getBuyer())
                                            .setName(mFusedCustomAudience.getName())
                                            .setActivationTime(
                                                    mFusedCustomAudience.getActivationTime())
                                            .setExpirationTime(
                                                    mFusedCustomAudience.getExpirationTime())
                                            .setBiddingLogicUri(
                                                    mFusedCustomAudience.getBiddingLogicUri())
                                            .setUserBiddingSignals(
                                                    mFusedCustomAudience.getUserBiddingSignals())
                                            .setTrustedBiddingData(
                                                    DBTrustedBiddingData.fromServiceObject(
                                                            mFusedCustomAudience
                                                                    .getTrustedBiddingData()))
                                            .setDebuggable(isDebuggableCustomAudience)
                                            .setAuctionServerRequestFlags(
                                                    mFusedCustomAudience
                                                            .getAuctionServerRequestFlags());

                            List<DBAdData> ads = new ArrayList<>();
                            for (AdData ad : mFusedCustomAudience.getAds()) {
                                ads.add(
                                        new DBAdData.Builder()
                                                .setRenderUri(ad.getRenderUri())
                                                .setMetadata(ad.getMetadata())
                                                .setAdCounterKeys(ad.getAdCounterKeys())
                                                .setAdFilters(ad.getAdFilters())
                                                .setAdRenderId(ad.getAdRenderId())
                                                .build());
                            }

                            customAudienceBuilder.setAds(ads);

                            Instant currentTime = mClock.instant();
                            customAudienceBuilder.setCreationTime(currentTime);
                            customAudienceBuilder.setLastAdsAndBiddingDataUpdatedTime(currentTime);
                            DBCustomAudience customAudience = customAudienceBuilder.build();

                            // Persist response
                            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                                    customAudience,
                                    mFusedCustomAudience.getDailyUpdateUri(),
                                    isDebuggableCustomAudience);
                            return null;
                        }));
    }

    private void tryToPersistQuarantineEntryAndNotifyCaller(
            RetryableAdServicesNetworkException retryableAdServicesNetworkException,
            FetchAndJoinCustomAudienceCallback callback) {

        retryableAdServicesNetworkException.setRetryAfterToValidDuration(
                mDefaultRetryDurationSeconds, mMaxRetryDurationSeconds);

        DBCustomAudienceQuarantine dbFetchCustomAudienceQuarantine =
                DBCustomAudienceQuarantine.builder()
                        .setOwner(mOwner)
                        .setBuyer(mBuyer)
                        .setQuarantineExpirationTime(
                                mClock.instant()
                                        .plusMillis(
                                                retryableAdServicesNetworkException
                                                        .getRetryAfter()
                                                        .toMillis()))
                        .build();

        FluentFuture<Void> persistFuture =
                FluentFuture.from(
                        mExecutorService.submit(
                                () -> {
                                    mCustomAudienceDao.safelyInsertCustomAudienceQuarantine(
                                            dbFetchCustomAudienceQuarantine,
                                            mFledgeCustomAuienceMaxTotal);
                                    return null;
                                }));

        persistFuture.addCallback(
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        sLogger.d("Persisted to quarantine table successfully");
                        notifyFailure(callback, retryableAdServicesNetworkException);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.e(t, "Encountered failure while persisting to quarantine table!");
                        notifyFailure(callback, new IllegalStateException());
                    }
                },
                mExecutorService);
    }

    private void notifyFailure(FetchAndJoinCustomAudienceCallback callback, Throwable t) {
        try {
            int resultCode;

            boolean isFilterException = t instanceof FilterException;

            if (isFilterException) {
                resultCode = FilterException.getResultCode(t);
            } else if (t instanceof IllegalArgumentException) {
                resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
            } else if (t instanceof InvalidObjectException) {
                resultCode = AdServicesStatusUtils.STATUS_INVALID_OBJECT;
            } else if (t instanceof RetryableAdServicesNetworkException
                    || (t instanceof LimitExceededException)) {
                resultCode = AdServicesStatusUtils.STATUS_SERVER_RATE_LIMIT_REACHED;
            } else {
                sLogger.d(t, "Unexpected error during operation");
                resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            }

            // Skip logging if a FilterException occurs.
            // AdSelectionServiceFilter ensures the failing assertion is logged internally.
            // Note: Failure is logged before the callback to ensure deterministic testing.
            if (!isFilterException) {
                mAdServicesLogger.logFledgeApiCallStats(
                        API_NAME, mCallerAppPackageName, resultCode, 0);
            }

            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(resultCode)
                            .setErrorMessage(t.getMessage())
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send failed result to the callback");
            mAdServicesLogger.logFledgeApiCallStats(
                    API_NAME,
                    mCallerAppPackageName,
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR,
                    0);
            throw new RuntimeException(e);
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void notifySuccess(@NonNull FetchAndJoinCustomAudienceCallback callback) {
        try {
            mAdServicesLogger.logFledgeApiCallStats(
                    API_NAME, mCallerAppPackageName, AdServicesStatusUtils.STATUS_SUCCESS, 0);
            callback.onSuccess();
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send successful result to the callback");
            mAdServicesLogger.logFledgeApiCallStats(
                    API_NAME,
                    mCallerAppPackageName,
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR,
                    0);
            throw new RuntimeException(e);
        }
    }

    private boolean isComplete(CustomAudienceBlob fusedCustomAudience) {
        HashSet<String> expectedKeysSet = new HashSet<>(CustomAudienceBlob.mKeysSet);
        HashSet<String> currentKeySet = new HashSet<>(fusedCustomAudience.mFieldsMap.keySet());

        if (mAuctionServerRequestFlagsEnabled) {
            currentKeySet.remove(AUCTION_SERVER_REQUEST_FLAGS_KEY);
        }
        return currentKeySet.size() == expectedKeysSet.size();
    }
}
