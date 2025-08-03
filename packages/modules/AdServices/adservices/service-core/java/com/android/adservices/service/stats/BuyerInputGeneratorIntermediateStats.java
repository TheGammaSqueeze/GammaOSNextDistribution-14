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

package com.android.adservices.service.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the intermediate values serving to calculate the stats for a {@link
 * GetAdSelectionDataBuyerInputGeneratedStats}
 */
public class BuyerInputGeneratorIntermediateStats {
    private int mNumCustomAudiences;
    private int mNumCustomAudiencesOmitAds;
    List<Integer> mCustomAudienceSizes;
    List<Integer> mTrustedBiddingSignalsKeysSizes;
    List<Integer> mUserBiddingSignalsSizes;

    /** Constructs a {@link BuyerInputGeneratorIntermediateStats} instance */
    public BuyerInputGeneratorIntermediateStats() {
        mCustomAudienceSizes = new ArrayList<>();
        mTrustedBiddingSignalsKeysSizes = new ArrayList<>();
        mUserBiddingSignalsSizes = new ArrayList<>();
    }

    private float getMean(List<Integer> list) {
        if (list.size() == 0) {
            return 0;
        }
        float temp = 0;
        for (Integer n : list) {
            temp += n;
        }
        return temp / list.size();
    }

    private float getVariance(List<Integer> list) {
        if (list.size() == 0) {
            return 0;
        }
        float mean = getMean(list);
        float temp = 0;
        for (Integer n : list) {
            temp += Math.pow(mean - n, 2);
        }
        return temp / list.size();
    }

    /** Returns the current number of custom audiences for this buyer input. */
    public int getNumCustomAudiences() {
        return mNumCustomAudiences;
    }

    /** Returns the current number of custom audiences omitting ads for this buyer input. */
    public int getNumCustomAudiencesOmitAds() {
        return mNumCustomAudiencesOmitAds;
    }

    /** Returns the list of custom audience sizes for this buyer input. */
    public List<Integer> getCustomAudienceSizes() {
        return mCustomAudienceSizes;
    }

    /** Returns the list of trusted bidding signals keys sizes for this buyer input. */
    public List<Integer> getTrustedBiddingSignalsKeysSizes() {
        return mTrustedBiddingSignalsKeysSizes;
    }

    /** Returns the list of user bidding signals keys sizes for this buyer input. */
    public List<Integer> getUserBiddingSignalsSizes() {
        return mUserBiddingSignalsSizes;
    }

    /** Returns the mean of the size of a custom audience for this buyer input */
    public float getCustomAudienceSizeMeanB() {
        return getMean(mCustomAudienceSizes);
    }

    /** Returns the variance of the size of a custom audience for this buyer input */
    public float getCustomAudienceSizeVarianceB() {
        return getVariance(mCustomAudienceSizes);
    }

    /** Returns the mean of the size of the trusted bidding signals keys for this buyer input */
    public float getTrustedBiddingSignalsKeysSizeMeanB() {
        return getMean(mTrustedBiddingSignalsKeysSizes);
    }

    /** Returns the variance of the size of the trusted bidding signals keys for this buyer input */
    public float getTrustedBiddingSignalskeysSizeVarianceB() {
        return getVariance(mTrustedBiddingSignalsKeysSizes);
    }

    /** Returns the mean of the size of the user bidding signals for this buyer input */
    public float getUserBiddingSignalsSizeMeanB() {
        return getMean(mUserBiddingSignalsSizes);
    }

    /** Returns the variance of the size of the user bidding signals for this buyer input */
    public float getUserBiddingSignalsSizeVarianceB() {
        return getVariance(mUserBiddingSignalsSizes);
    }

    /** Increments the number of custom audiences for this buyer input by one. */
    public void incrementNumCustomAudiences() {
        mNumCustomAudiences++;
    }

    /** Increments the number of custom audiences omitting ads for this buyer input by one. */
    public void incrementNumCustomAudiencesOmitAds() {
        mNumCustomAudiencesOmitAds++;
    }

    /** Increments the custom audiences size list by the given size. */
    public void addCustomAudienceSize(int customAudienceSize) {
        mCustomAudienceSizes.add(customAudienceSize);
    }

    /** Increments the trusted bidding signals keys size list by the given size. */
    public void addTrustedBiddingSignalsKeysSize(int trustedBiddingSignalsKeysSize) {
        mTrustedBiddingSignalsKeysSizes.add(trustedBiddingSignalsKeysSize);
    }

    /** Increments the user bidding signals keys size list by the given size. */
    public void addUserBiddingSignalsSize(int userBiddingSignalsKeysSize) {
        mUserBiddingSignalsSizes.add(userBiddingSignalsKeysSize);
    }
}
