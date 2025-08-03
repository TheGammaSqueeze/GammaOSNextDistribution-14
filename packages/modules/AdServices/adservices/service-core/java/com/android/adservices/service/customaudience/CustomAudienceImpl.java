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

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.Validator;
import com.android.adservices.service.devapi.DevContext;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Worker for implementation of {@link CustomAudienceServiceImpl}.
 *
 * <p>This class is thread safe.
 */
public class CustomAudienceImpl {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static CustomAudienceImpl sSingleton;

    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final CustomAudienceQuantityChecker mCustomAudienceQuantityChecker;
    @NonNull private final Validator<CustomAudience> mCustomAudienceValidator;
    @NonNull private final Clock mClock;
    @NonNull private final Flags mFlags;
    private final boolean mAuctionServerRequestFlagsEnabled;

    @VisibleForTesting
    public CustomAudienceImpl(
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull CustomAudienceQuantityChecker customAudienceQuantityChecker,
            @NonNull Validator<CustomAudience> customAudienceValidator,
            @NonNull Clock clock,
            @NonNull Flags flags) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(customAudienceQuantityChecker);
        Objects.requireNonNull(customAudienceValidator);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(flags);

        mCustomAudienceDao = customAudienceDao;
        mCustomAudienceQuantityChecker = customAudienceQuantityChecker;
        mCustomAudienceValidator = customAudienceValidator;
        mClock = clock;
        mFlags = flags;
        mAuctionServerRequestFlagsEnabled =
                BinderFlagReader.readFlag(flags::getFledgeAuctionServerRequestFlagsEnabled);
    }

    /**
     * Gets an instance of {@link CustomAudienceImpl} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    public static CustomAudienceImpl getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                Flags flags = FlagsFactory.getFlags();
                CustomAudienceDao customAudienceDao =
                        CustomAudienceDatabase.getInstance(context).customAudienceDao();
                sSingleton =
                        new CustomAudienceImpl(
                                customAudienceDao,
                                new CustomAudienceQuantityChecker(customAudienceDao, flags),
                                CustomAudienceValidator.getInstance(context),
                                Clock.systemUTC(),
                                flags);
            }
            return sSingleton;
        }
    }

    /**
     * Perform check on {@link CustomAudience} and write into db if it is valid.
     *
     * @param customAudience instance staged to be inserted.
     * @param callerPackageName package name for the calling application, used as the owner
     *     application identifier
     */
    public void joinCustomAudience(
            @NonNull CustomAudience customAudience,
            @NonNull String callerPackageName,
            @NonNull DevContext devContext) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(callerPackageName);
        Instant currentTime = mClock.instant();

        sLogger.v("Validating CA limits");
        mCustomAudienceQuantityChecker.check(customAudience, callerPackageName);
        sLogger.v("Validating CA");
        mCustomAudienceValidator.validate(customAudience);

        boolean adSelectionFilteringEnabled = mFlags.getFledgeAdSelectionFilteringEnabled();
        sLogger.v("Ad Selection filtering enabled flag is %s", adSelectionFilteringEnabled);
        boolean adRenderIdEnabled = mFlags.getFledgeAuctionServerAdRenderIdEnabled();
        sLogger.v("Ad render id enabled flag is %s", adRenderIdEnabled);
        AdDataConversionStrategy dataConversionStrategy =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(
                        adSelectionFilteringEnabled, adRenderIdEnabled);

        boolean isDebuggableCustomAudience = devContext.getDevOptionsEnabled();
        sLogger.v("Is debuggable custom audience: %b", isDebuggableCustomAudience);

        Duration customAudienceDefaultExpireIn =
                Duration.ofMillis(mFlags.getFledgeCustomAudienceDefaultExpireInMs());
        DBCustomAudience dbCustomAudience =
                DBCustomAudience.fromServiceObject(
                        customAudience,
                        callerPackageName,
                        currentTime,
                        customAudienceDefaultExpireIn,
                        dataConversionStrategy,
                        isDebuggableCustomAudience,
                        mAuctionServerRequestFlagsEnabled);

        sLogger.v("Inserting CA in the DB");
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dbCustomAudience, customAudience.getDailyUpdateUri(), isDebuggableCustomAudience);
    }

    /** Delete a custom audience with given key. No-op if not exist. */
    public void leaveCustomAudience(
            @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name) {
        Preconditions.checkStringNotEmpty(owner);
        Objects.requireNonNull(buyer);
        Preconditions.checkStringNotEmpty(name);

        mCustomAudienceDao.deleteAllCustomAudienceDataByPrimaryKey(owner, buyer, name);
    }

    /** Returns DAO to be used in {@link CustomAudienceServiceImpl} */
    public CustomAudienceDao getCustomAudienceDao() {
        return mCustomAudienceDao;
    }
}
