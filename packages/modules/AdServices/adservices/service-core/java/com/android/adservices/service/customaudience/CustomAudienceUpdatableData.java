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

package com.android.adservices.service.customaudience;

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_DEFAULT;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** This class represents the result of a daily fetch that will update a custom audience. */
@AutoValue
public abstract class CustomAudienceUpdatableData {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    enum ReadStatus {
        STATUS_UNKNOWN,
        STATUS_NOT_FOUND,
        STATUS_FOUND_VALID,
        STATUS_FOUND_INVALID
    }

    private static final String INVALID_JSON_TYPE_ERROR_FORMAT =
            "%s Invalid JSON type while parsing %s found in JSON response";
    private static final String VALIDATION_FAILED_ERROR_FORMAT =
            "%s Data validation failed while parsing %s found in JSON response";

    /**
     * @return the user bidding signals that were sent in the update response. If there were no
     *     valid user bidding signals, returns {@code null}.
     */
    @Nullable
    public abstract AdSelectionSignals getUserBiddingSignals();

    /**
     * @return trusted bidding data that was sent in the update response. If no valid trusted
     *     bidding data was found, returns {@code null}.
     */
    @Nullable
    public abstract DBTrustedBiddingData getTrustedBiddingData();

    /**
     * @return the list of ads that were sent in the update response. If no valid ads were sent,
     *     returns {@code null}.
     */
    @Nullable
    public abstract ImmutableList<DBAdData> getAds();

    /** @return the time at which the custom audience update was attempted */
    @NonNull
    public abstract Instant getAttemptedUpdateTime();

    /** Returns the bitfield of auction server request flags. */
    @CustomAudience.AuctionServerRequestFlag
    public abstract int getAuctionServerRequestFlags();

    /**
     * @return the result type for the update attempt before {@link
     *     #createFromResponseString(Instant, AdTechIdentifier,
     *     BackgroundFetchRunner.UpdateResultType, String, Flags, boolean)} was called
     */
    public abstract BackgroundFetchRunner.UpdateResultType getInitialUpdateResult();

    /**
     * Returns whether this object represents a successful update.
     *
     * <ul>
     *   <li>An empty response is valid, representing that the buyer does not want to update its
     *       custom audience.
     *   <li>If a response is not empty but fails to be parsed into a JSON object, it will be
     *       considered a failed response which does not contain a successful update.
     *   <li>If a response is not empty and is parsed successfully into a JSON object but does not
     *       contain any units of updatable data, it is considered empty (albeit full of junk) and
     *       valid, representing that the buyer does not want to update its custom audience.
     *   <li>A non-empty response that contains relevant fields but which all fail to be parsed into
     *       valid objects is considered a failed update. This might happen if fields are found but
     *       do not follow the correct schema/expected object types.
     *   <li>A non-empty response that is not completely invalid and which does have at least one
     *       successful field is considered successful.
     * </ul>
     *
     * @return {@code true} if this object represents a successful update; otherwise, {@code false}
     */
    public abstract boolean getContainsSuccessfulUpdate();

    /**
     * Creates a {@link CustomAudienceUpdatableData} object based on the response of a GET request
     * to a custom audience's daily fetch URI.
     *
     * <p>Note that if a response contains extra fields in its JSON, the extra information will be
     * ignored, and the validation of the response will continue as if the extra data had not been
     * included. For example, if {@code trusted_bidding_data} contains an extra field {@code
     * campaign_ids} (which is not considered part of the {@code trusted_bidding_data} JSON schema),
     * the resulting {@link CustomAudienceUpdatableData} object will not be built with the extra
     * data.
     *
     * <p>See {@link #getContainsSuccessfulUpdate()} for more details.
     *
     * @param attemptedUpdateTime the time at which the update for this custom audience was
     *     attempted
     * @param buyer the buyer ad tech's eTLD+1
     * @param initialUpdateResult the result type of the fetch attempt prior to parsing the {@code
     *     response}
     * @param response the String response returned from querying the custom audience's daily fetch
     *     URI
     * @param flags the {@link Flags} used to get configurable limits for validating the {@code
     *     response}
     */
    @NonNull
    public static CustomAudienceUpdatableData createFromResponseString(
            @NonNull Instant attemptedUpdateTime,
            @NonNull AdTechIdentifier buyer,
            BackgroundFetchRunner.UpdateResultType initialUpdateResult,
            @NonNull final String response,
            @NonNull Flags flags) {
        Objects.requireNonNull(attemptedUpdateTime);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(response);
        Objects.requireNonNull(flags);

        // Use the hash of the response string as a session identifier for logging purposes
        final String responseHash = "[" + response.hashCode() + "]";
        sLogger.v("Parsing JSON response string with hash %s", responseHash);

        // By default unset nullable AutoValue fields are null
        CustomAudienceUpdatableData.Builder dataBuilder =
                builder()
                        .setAttemptedUpdateTime(attemptedUpdateTime)
                        .setContainsSuccessfulUpdate(false)
                        .setInitialUpdateResult(initialUpdateResult);

        // No need to continue if an error occurred upstream for this custom audience update
        if (initialUpdateResult != BackgroundFetchRunner.UpdateResultType.SUCCESS) {
            sLogger.v("%s Skipping response string parsing due to upstream failure", responseHash);
            dataBuilder.setContainsSuccessfulUpdate(false);
            return dataBuilder.build();
        }

        if (response.isEmpty()) {
            sLogger.v("%s Response string was empty", responseHash);
            dataBuilder.setContainsSuccessfulUpdate(true);
            return dataBuilder.build();
        }

        JSONObject responseObject;
        try {
            responseObject = new JSONObject(response);
        } catch (JSONException exception) {
            sLogger.e("%s Error parsing JSON response into an object", responseHash);
            dataBuilder.setContainsSuccessfulUpdate(false);
            return dataBuilder.build();
        }

        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        responseHash,
                        buyer,
                        flags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        flags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        flags.getFledgeCustomAudienceMaxAdsSizeB(),
                        flags.getFledgeCustomAudienceMaxNumAds(),
                        flags.getFledgeAdSelectionFilteringEnabled(),
                        flags.getFledgeAuctionServerAdRenderIdEnabled(),
                        flags.getFledgeAuctionServerAdRenderIdMaxLength());

        ReadStatus userBiddingSignalsReadStatus =
                readUserBiddingSignals(reader, responseHash, dataBuilder);
        ReadStatus trustedBiddingDataReadStatus =
                readTrustedBiddingData(reader, responseHash, dataBuilder);
        ReadStatus adsReadStatus = readAds(reader, responseHash, dataBuilder);

        ReadStatus auctionServerFlagStatus = ReadStatus.STATUS_UNKNOWN;
        if (flags.getFledgeAuctionServerRequestFlagsEnabled()) {
            auctionServerFlagStatus =
                    readAuctionServerRequestFlags(reader, responseHash, dataBuilder);
        }

        // If there were no useful fields found, or if there was something useful found and
        // successfully updated, then this object should signal a successful update.
        boolean containsSuccessfulUpdate =
                (userBiddingSignalsReadStatus == ReadStatus.STATUS_FOUND_VALID
                                || trustedBiddingDataReadStatus == ReadStatus.STATUS_FOUND_VALID
                                || adsReadStatus == ReadStatus.STATUS_FOUND_VALID)
                        || (userBiddingSignalsReadStatus == ReadStatus.STATUS_NOT_FOUND
                                && trustedBiddingDataReadStatus == ReadStatus.STATUS_NOT_FOUND
                                && adsReadStatus == ReadStatus.STATUS_NOT_FOUND)
                        || auctionServerFlagStatus == ReadStatus.STATUS_FOUND_VALID;
        sLogger.v(
                "%s Completed parsing JSON response with containsSuccessfulUpdate = %b",
                responseHash, containsSuccessfulUpdate);
        dataBuilder.setContainsSuccessfulUpdate(containsSuccessfulUpdate);

        return dataBuilder.build();
    }

    @VisibleForTesting
    @NonNull
    static ReadStatus readUserBiddingSignals(
            @NonNull CustomAudienceUpdatableDataReader reader,
            @NonNull String responseHash,
            @NonNull CustomAudienceUpdatableData.Builder dataBuilder) {
        try {
            AdSelectionSignals userBiddingSignals = reader.getUserBiddingSignalsFromJsonObject();
            dataBuilder.setUserBiddingSignals(userBiddingSignals);

            if (userBiddingSignals == null) {
                return ReadStatus.STATUS_NOT_FOUND;
            } else {
                return ReadStatus.STATUS_FOUND_VALID;
            }
        } catch (JSONException | NullPointerException exception) {
            sLogger.e(
                    exception,
                    INVALID_JSON_TYPE_ERROR_FORMAT,
                    responseHash,
                    CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY);
            dataBuilder.setUserBiddingSignals(null);
            return ReadStatus.STATUS_FOUND_INVALID;
        } catch (IllegalArgumentException exception) {
            sLogger.e(
                    exception,
                    VALIDATION_FAILED_ERROR_FORMAT,
                    responseHash,
                    CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY);
            dataBuilder.setUserBiddingSignals(null);
            return ReadStatus.STATUS_FOUND_INVALID;
        }
    }

    @VisibleForTesting
    @NonNull
    static ReadStatus readTrustedBiddingData(
            @NonNull CustomAudienceUpdatableDataReader reader,
            @NonNull String responseHash,
            @NonNull CustomAudienceUpdatableData.Builder dataBuilder) {
        try {
            DBTrustedBiddingData trustedBiddingData = reader.getTrustedBiddingDataFromJsonObject();
            dataBuilder.setTrustedBiddingData(trustedBiddingData);

            if (trustedBiddingData == null) {
                return ReadStatus.STATUS_NOT_FOUND;
            } else {
                return ReadStatus.STATUS_FOUND_VALID;
            }
        } catch (JSONException | NullPointerException exception) {
            sLogger.e(
                    exception,
                    INVALID_JSON_TYPE_ERROR_FORMAT,
                    responseHash,
                    CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_DATA_KEY);
            dataBuilder.setTrustedBiddingData(null);
            return ReadStatus.STATUS_FOUND_INVALID;
        } catch (IllegalArgumentException exception) {
            sLogger.e(
                    exception,
                    VALIDATION_FAILED_ERROR_FORMAT,
                    responseHash,
                    CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_DATA_KEY);
            dataBuilder.setTrustedBiddingData(null);
            return ReadStatus.STATUS_FOUND_INVALID;
        }
    }

    @VisibleForTesting
    @NonNull
    static ReadStatus readAds(
            @NonNull CustomAudienceUpdatableDataReader reader,
            @NonNull String responseHash,
            @NonNull CustomAudienceUpdatableData.Builder dataBuilder) {
        try {
            List<DBAdData> ads = reader.getAdsFromJsonObject();
            dataBuilder.setAds(ads);

            if (ads == null) {
                return ReadStatus.STATUS_NOT_FOUND;
            } else {
                return ReadStatus.STATUS_FOUND_VALID;
            }
        } catch (JSONException | NullPointerException exception) {
            sLogger.e(
                    exception,
                    INVALID_JSON_TYPE_ERROR_FORMAT,
                    responseHash,
                    CustomAudienceUpdatableDataReader.ADS_KEY);
            dataBuilder.setAds(null);
            return ReadStatus.STATUS_FOUND_INVALID;
        } catch (IllegalArgumentException exception) {
            sLogger.e(
                    exception,
                    VALIDATION_FAILED_ERROR_FORMAT,
                    responseHash,
                    CustomAudienceUpdatableDataReader.ADS_KEY);
            dataBuilder.setAds(null);
            return ReadStatus.STATUS_FOUND_INVALID;
        }
    }

    @VisibleForTesting
    @NonNull
    static ReadStatus readAuctionServerRequestFlags(
            @NonNull CustomAudienceUpdatableDataReader reader,
            @NonNull String responseHash,
            @NonNull CustomAudienceUpdatableData.Builder dataBuilder) {
        try {
            dataBuilder.setAuctionServerRequestFlags(reader.getAuctionServerRequestFlags());
            return ReadStatus.STATUS_FOUND_VALID;
        } catch (JSONException e) {
            sLogger.e(
                    e,
                    INVALID_JSON_TYPE_ERROR_FORMAT,
                    responseHash,
                    CustomAudienceUpdatableDataReader.AUCTION_SERVER_REQUEST_FLAGS_KEY);
            return ReadStatus.STATUS_FOUND_INVALID;
        }
    }

    /**
     * Gets a Builder to make {@link #createFromResponseString(Instant, AdTechIdentifier,
     * BackgroundFetchRunner.UpdateResultType, String, Flags, boolean)} easier.
     */
    @VisibleForTesting
    @NonNull
    public static CustomAudienceUpdatableData.Builder builder() {
        return new AutoValue_CustomAudienceUpdatableData.Builder()
                .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_DEFAULT);
    }

    /**
     * This is a hidden (visible for testing) AutoValue builder to make {@link
     * #createFromResponseString(Instant, AdTechIdentifier, BackgroundFetchRunner.UpdateResultType,
     * String, Flags, boolean)} easier.
     */
    @VisibleForTesting
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the user bidding signals found in the response string. */
        @NonNull
        public abstract Builder setUserBiddingSignals(@Nullable AdSelectionSignals value);

        /** Sets the trusted bidding data found in the response string. */
        @NonNull
        public abstract Builder setTrustedBiddingData(@Nullable DBTrustedBiddingData value);

        /** Sets the list of ads found in the response string. */
        @NonNull
        public abstract Builder setAds(@Nullable List<DBAdData> value);

        /** Sets the time at which the custom audience update was attempted. */
        @NonNull
        public abstract Builder setAttemptedUpdateTime(@NonNull Instant value);

        /** Sets the bitfield of auction server request flags. */
        @NonNull
        public abstract Builder setAuctionServerRequestFlags(
                @CustomAudience.AuctionServerRequestFlag int auctionServerRequestFlags);

        /** Sets the result of the update prior to parsing the response string. */
        @NonNull
        public abstract Builder setInitialUpdateResult(
                BackgroundFetchRunner.UpdateResultType value);

        /**
         * Sets whether the response contained a successful update.
         *
         * <p>See {@link #getContainsSuccessfulUpdate()} for more details.
         */
        @NonNull
        public abstract Builder setContainsSuccessfulUpdate(boolean value);

        /**
         * Builds the {@link CustomAudienceUpdatableData} object and returns it.
         *
         * <p>Note that AutoValue doesn't by itself do any validation, so splitting the builder with
         * a manual verification is recommended. See go/autovalue/builders-howto#validate for more
         * information.
         */
        @NonNull
        protected abstract CustomAudienceUpdatableData autoValueBuild();

        /** Builds, validates, and returns the {@link CustomAudienceUpdatableData} object. */
        @NonNull
        public final CustomAudienceUpdatableData build() {
            CustomAudienceUpdatableData updatableData = autoValueBuild();

            Preconditions.checkArgument(
                    updatableData.getContainsSuccessfulUpdate()
                            || (updatableData.getUserBiddingSignals() == null
                                    && updatableData.getTrustedBiddingData() == null
                                    && updatableData.getAds() == null),
                    "CustomAudienceUpdatableData should not contain non-null updatable fields if"
                            + " the object does not represent a successful update");

            return updatableData;
        }
    }
}
