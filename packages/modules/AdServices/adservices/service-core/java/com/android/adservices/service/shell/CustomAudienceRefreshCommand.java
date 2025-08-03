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

package com.android.adservices.service.shell;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;

import android.adservices.common.AdServicesStatusUtils.StatusCode;
import android.adservices.common.AdTechIdentifier;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;

import java.io.PrintWriter;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Command to refresh a single custom audience. */
public final class CustomAudienceRefreshCommand extends AbstractShellCommand {

    public static final String CMD = "refresh";
    public static final String HELP =
            CMD
                    + " --"
                    + CustomAudienceArgs.OWNER
                    + " <owner> --"
                    + CustomAudienceArgs.BUYER
                    + " <buyer> --"
                    + CustomAudienceArgs.NAME
                    + " <name>";
    @VisibleForTesting public static final int BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS = 3;

    @VisibleForTesting
    public static final String OUTPUT_ERROR_NO_CUSTOM_AUDIENCE = "Error: No custom audience found.";

    @VisibleForTesting
    public static final String OUTPUT_ERROR_WITH_CODE =
            "Failed to update custom audience. Error code: %d";

    @VisibleForTesting
    public static final String OUTPUT_SUCCESS = "Successfully updated custom audience.";

    private final BackgroundFetchRunner mBackgroundFetchRunner;
    private final CustomAudienceArgParser mArgParser;
    private final CustomAudienceDao mCustomAudienceDao;
    private final int mFetchTimeoutInSeconds;
    private final Clock mClock;
    private final ScheduledThreadPoolExecutor mScheduledExecutor;

    CustomAudienceRefreshCommand(
            BackgroundFetchRunner backgroundFetchRunner,
            CustomAudienceDao customAudienceDao,
            ScheduledThreadPoolExecutor executor) {
        this(
                backgroundFetchRunner,
                customAudienceDao,
                BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS,
                Clock.systemUTC(),
                executor);
    }

    @VisibleForTesting
    CustomAudienceRefreshCommand(
            BackgroundFetchRunner backgroundFetchRunner,
            CustomAudienceDao customAudienceDao,
            int backgroundFetchTimeoutInSeconds,
            Clock clock,
            ScheduledThreadPoolExecutor executor) {
        mBackgroundFetchRunner = backgroundFetchRunner;
        mCustomAudienceDao = customAudienceDao;
        mFetchTimeoutInSeconds = backgroundFetchTimeoutInSeconds;
        mClock = clock;
        mScheduledExecutor = executor;
        mArgParser =
                new CustomAudienceArgParser(
                        CustomAudienceArgs.OWNER,
                        CustomAudienceArgs.BUYER,
                        CustomAudienceArgs.NAME);
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String[] args) {
        try {
            mArgParser.parse(args);
        } catch (IllegalArgumentException e) {
            err.printf("Failed to parse arguments: %s\n", e.getMessage());
            Log.e(TAG, "Failed to parse arguments", e);
            return invalidArgsError(HELP, err, args);
        }

        Optional<DBCustomAudienceBackgroundFetchData> backgroundFetchData =
                getDbCustomAudienceBackgroundFetchData(
                        mArgParser.getValue(CustomAudienceArgs.NAME),
                        mArgParser.getValue(CustomAudienceArgs.OWNER),
                        AdTechIdentifier.fromString(mArgParser.getValue(CustomAudienceArgs.BUYER)));
        if (backgroundFetchData.isPresent()) {
            int statusCode = refreshCustomAudience(backgroundFetchData.get());
            if (statusCode == STATUS_SUCCESS) {
                out.printf(OUTPUT_SUCCESS);
                return RESULT_OK;
            } else {
                err.printf(OUTPUT_ERROR_WITH_CODE, statusCode);
                return RESULT_GENERIC_ERROR;
            }
        } else {
            err.printf(OUTPUT_ERROR_NO_CUSTOM_AUDIENCE);
            return RESULT_GENERIC_ERROR;
        }
    }

    @Override
    public String getCommandName() {
        return CMD;
    }

    @StatusCode
    private int refreshCustomAudience(DBCustomAudienceBackgroundFetchData backgroundFetchData) {
        try {
            return mBackgroundFetchRunner
                    .updateCustomAudience(mClock.instant(), backgroundFetchData)
                    .withTimeout(mFetchTimeoutInSeconds, TimeUnit.SECONDS, mScheduledExecutor)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Failed to update custom audience", e);
            return STATUS_INTERNAL_ERROR;
        }
    }

    private Optional<DBCustomAudienceBackgroundFetchData> getDbCustomAudienceBackgroundFetchData(
            String name, String ownerAppPackage, AdTechIdentifier buyer) {
        DBCustomAudienceBackgroundFetchData data =
                mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        ownerAppPackage, buyer, name);
        if (Objects.isNull(data)) {
            Log.d(
                    TAG,
                    String.format(
                            "No debuggable custom audience background fetch data found for key:"
                                    + " (name: %s, owner: %s, buyer: %s)",
                            name, ownerAppPackage, buyer));
            return Optional.empty();
        }
        Log.d(
                TAG,
                String.format(
                        "Debuggable custom audience background fetch was data found for key:"
                                + " (name: %s, owner: %s, buyer: %s)",
                        name, ownerAppPackage, buyer));
        return Optional.of(data);
    }
}
