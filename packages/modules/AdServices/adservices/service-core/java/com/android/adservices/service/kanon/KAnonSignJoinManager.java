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

package com.android.adservices.service.kanon;

import android.annotation.RequiresApi;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.kanon.KAnonMessageConstants;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

/** KAnon sign join manager class */
@RequiresApi(Build.VERSION_CODES.S)
public class KAnonSignJoinManager {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final KAnonCaller mKAnonCaller;
    private final KAnonMessageManager mKAnonMessageManager;
    private final Flags mFlags;
    private final Clock mClock;
    private final AdServicesLogger mAdServicesLogger;
    private final Context mContext;

    public KAnonSignJoinManager(
            @NonNull Context context,
            @NonNull KAnonCaller kAnonCaller,
            @NonNull KAnonMessageManager kAnonMessageManager,
            @NonNull Flags flags,
            @NonNull Clock clock,
            @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(kAnonCaller);
        Objects.requireNonNull(context);
        Objects.requireNonNull(kAnonMessageManager);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);

        mContext = context;
        mKAnonCaller = kAnonCaller;
        mKAnonMessageManager = kAnonMessageManager;
        mFlags = flags;
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
    }

    /**
     * Filters whether a message needs to be processed in the current instance. We will filter a
     * message out for current processing if it exists in the database already and is bound to be
     * picked up by a background process or if it has already been processed.
     *
     * @return {@code false} if the message is to be filtered out, and {@code true} otherwise.
     */
    private boolean filterRequest(KAnonMessageEntity kAnonMessageEntity) {
        boolean shouldProcessRightNow = false;
        List<KAnonMessageEntity> messageEntitiesFromDB =
                mKAnonMessageManager.fetchKAnonMessageEntityWithMessage(
                        kAnonMessageEntity.getHashSet());
        // We will be making sign/join calls for this message if it doesn't exist in the database OR
        // It exists in the database in the PROCESSED(SIGNED/JOINED) status with expired
        // corresponding client params.
        if (messageEntitiesFromDB.isEmpty()) {
            shouldProcessRightNow = true;
        } else {
            for (KAnonMessageEntity messageInDB : messageEntitiesFromDB) {
                Instant clientParamsExpiryInstant =
                        messageInDB.getCorrespondingClientParametersExpiryInstant();
                if (messageInDB.getStatus()
                                != KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED
                        && clientParamsExpiryInstant != null
                        && clientParamsExpiryInstant.isBefore(mClock.instant())) {
                    shouldProcessRightNow = true;
                }
            }
        }
        return shouldProcessRightNow;
    }

    /**
     * This generates a boolean with probability of {@code true} equal to the given percentage X. If
     * the randomly generated number between (0-100) is less than X, then return {@code true}
     * otherwise return {@code false}.
     */
    private boolean shouldMakeKAnonCallsNow() {
        Random random = new Random();
        return mFlags.getFledgeKAnonPercentageImmediateSignJoinCalls() > random.nextInt(100);
    }

    /**
     * This method will be used to process the new {@link KAnonMessageEntity}. This will be used by
     * {@link com.android.adservices.service.adselection.PersistAdSelectionResultRunner} to process
     * the new ad winner/ghost ad winners.
     */
    public void processNewMessages(List<KAnonMessageEntity> newMessages) {
        try {
            boolean forceSchedule = false;
            KAnonSignJoinBackgroundJobService.scheduleIfNeeded(mContext, forceSchedule);
        } catch (Throwable t) {
            // Not throwing this error because we want this error to fail silently.
            sLogger.d("Error while scheduling KAnon background job service:" + t.getMessage());
        }
        List<KAnonMessageEntity> messageAfterFiltering =
                newMessages.stream().filter(this::filterRequest).collect(Collectors.toList());
        if (messageAfterFiltering.isEmpty()) {
            return;
        }
        List<KAnonMessageEntity> insertedMessages =
                mKAnonMessageManager.persistNewAnonMessageEntities(messageAfterFiltering);
        if (shouldMakeKAnonCallsNow()) {
            mKAnonCaller.signAndJoinMessages(insertedMessages);
        } else {
            // TODO(b/326903508): Remove unused loggers. Use callback instead of logger
            // for testing.
            mAdServicesLogger.logKAnonSignJoinStatus();
        }
    }

    /**
     * This method is used by the background job. This method fetches the messages from the database
     * and processes them by making sign join calls.
     */
    public void processMessagesFromDatabase(int numberOfMessages) {
        List<KAnonMessageEntity> messageEntities =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        numberOfMessages, KAnonMessageConstants.MessageStatus.NOT_PROCESSED);
        if (messageEntities.isEmpty()) {
            return;
        }
        mKAnonCaller.signAndJoinMessages(messageEntities);
    }
}
