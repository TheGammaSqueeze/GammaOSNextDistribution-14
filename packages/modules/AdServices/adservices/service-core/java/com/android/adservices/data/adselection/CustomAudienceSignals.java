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

package com.android.adservices.data.adselection;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import androidx.room.ColumnInfo;

import com.android.adservices.data.customaudience.DBCustomAudience;

import java.time.Instant;
import java.util.Objects;

/**
 * This class represents the custom_audience_signals passed into generateBid javascript. Part of
 * these signals : buyer and name are passed into scoreAd javascript It contains fields from a
 * {@link DBCustomAudience} object.
 */
public class CustomAudienceSignals {

    // TODO(b/276333013) : Refactor the Ad Selection result to avoid using special contextual CA
    public static final String CONTEXTUAL_CA_NAME = "CONTEXTUAL_CA";
    public static final int EXPIRATION_OFFSET_TWO_WEEKS = 2 * 7 * 24 * 60;

    @ColumnInfo(name = "owner")
    @NonNull
    private final String mOwner;

    @ColumnInfo(name = "buyer")
    @NonNull
    private final AdTechIdentifier mBuyer;

    @ColumnInfo(name = "name")
    @NonNull
    private final String mName;

    @ColumnInfo(name = "activation_time")
    @NonNull
    private final Instant mActivationTime;

    @ColumnInfo(name = "expiration_time")
    @NonNull
    private final Instant mExpirationTime;

    @ColumnInfo(name = "user_bidding_signals")
    @NonNull
    private final AdSelectionSignals mUserBiddingSignals;

    public CustomAudienceSignals(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull Instant activationTime,
            @NonNull Instant expirationTime,
            @NonNull AdSelectionSignals userBiddingSignals) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);
        Objects.requireNonNull(activationTime);
        Objects.requireNonNull(expirationTime);
        Objects.requireNonNull(userBiddingSignals);

        mOwner = owner;
        mBuyer = buyer;
        mName = name;
        mActivationTime = activationTime;
        mExpirationTime = expirationTime;
        mUserBiddingSignals = userBiddingSignals;
    }

    /** @return a String package name for the custom audience's owner application. */
    @NonNull
    public String getOwner() {
        return mOwner;
    }

    /** @return an {@link AdTechIdentifier} representing the custom audience's buyer's domain. */
    @NonNull
    public AdTechIdentifier getBuyer() {
        return mBuyer;
    }

    /**
     * @return a String representing the custom audience's name.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * @return the custom audience's time, truncated to whole seconds, after which the custom
     *     audience is active.
     */
    @NonNull
    public Instant getActivationTime() {
        return mActivationTime;
    }

    /**
     * @return the custom audience's time, truncated to whole seconds, after which the custom
     *     audience should be removed.
     */
    @NonNull
    public Instant getExpirationTime() {
        return mExpirationTime;
    }

    /**
     * @return a JSON String representing the opaque user bidding signals for the custom audience.
     */
    @NonNull
    public AdSelectionSignals getUserBiddingSignals() {
        return mUserBiddingSignals;
    }

    @Override
    public boolean equals(@NonNull Object o) {
        if (o instanceof CustomAudienceSignals) {
            CustomAudienceSignals customAudienceSignals = (CustomAudienceSignals) o;
            return mOwner.equals(customAudienceSignals.mOwner)
                    && mBuyer.equals(customAudienceSignals.mBuyer)
                    && mName.equals(customAudienceSignals.mName)
                    && mActivationTime.equals(customAudienceSignals.mActivationTime)
                    && mExpirationTime.equals(customAudienceSignals.mExpirationTime)
                    && mUserBiddingSignals.equals(customAudienceSignals.mUserBiddingSignals);
        }
        return false;
    }

    /**
     * @return the hash of the {@link CustomAudienceSignals} object data.
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                mOwner, mBuyer, mName, mActivationTime, mExpirationTime, mUserBiddingSignals);
    }

    /**
     * @return a CustomAudienceSignals data object built from a {@link DBCustomAudience} object.
     */
    @NonNull
    public static CustomAudienceSignals buildFromCustomAudience(
            @NonNull DBCustomAudience customAudience) {
        return new CustomAudienceSignals.Builder()
                .setOwner(customAudience.getOwner())
                .setBuyer(customAudience.getBuyer())
                .setName(customAudience.getName())
                .setActivationTime(customAudience.getActivationTime())
                .setExpirationTime(customAudience.getExpirationTime())
                .setUserBiddingSignals(customAudience.getUserBiddingSignals())
                .build();
    }

    /** Builder for @link CustomAudienceSignals} object. */
    public static final class Builder {
        @NonNull private String mOwner;
        @NonNull private AdTechIdentifier mBuyer;
        @NonNull private String mName;
        @NonNull private Instant mActivationTime;
        @NonNull private Instant mExpirationTime;
        @NonNull private AdSelectionSignals mUserBiddingSignals;

        public Builder() {}

        /**
         * Sets the package name for the owner application.
         *
         * <p>See {@link #getOwner()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setOwner(@NonNull String owner) {
            Objects.requireNonNull(owner);
            mOwner = owner;
            return this;
        }

        /**
         * Sets the buyer domain.
         *
         * <p>See {@link #getBuyer()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setBuyer(AdTechIdentifier buyer) {
            Objects.requireNonNull(buyer);
            mBuyer = buyer;
            return this;
        }

        /**
         * Sets the application name.
         *
         * <p>See {@link #getName()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setName(String name) {
            Objects.requireNonNull(name);
            mName = name;
            return this;
        }

        /**
         * Sets the activation time.
         *
         * <p>See {@link #getActivationTime()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setActivationTime(Instant activationTime) {
            Objects.requireNonNull(activationTime);
            mActivationTime = activationTime;
            return this;
        }

        /**
         * Sets the expiration time.
         *
         * <p>See {@link #getExpirationTime()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setExpirationTime(Instant expirationTime) {
            Objects.requireNonNull(expirationTime);
            mExpirationTime = expirationTime;
            return this;
        }

        /**
         * Sets the user bidding signals used in the ad selection.
         *
         * <p>See {@link #getUserBiddingSignals()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setUserBiddingSignals(
                AdSelectionSignals userBiddingSignals) {
            Objects.requireNonNull(userBiddingSignals);
            mUserBiddingSignals = userBiddingSignals;
            return this;
        }

        /**
         * Builds an instance of {@link CustomAudienceSignals}.
         *
         * @throws NullPointerException if any non-null parameter is null.
         */
        @NonNull
        public CustomAudienceSignals build() {
            Objects.requireNonNull(mOwner);
            Objects.requireNonNull(mBuyer);
            Objects.requireNonNull(mName);
            Objects.requireNonNull(mActivationTime);
            Objects.requireNonNull(mExpirationTime);
            Objects.requireNonNull(mUserBiddingSignals);

            return new CustomAudienceSignals(
                    mOwner, mBuyer, mName, mActivationTime, mExpirationTime, mUserBiddingSignals);
        }
    }
}
