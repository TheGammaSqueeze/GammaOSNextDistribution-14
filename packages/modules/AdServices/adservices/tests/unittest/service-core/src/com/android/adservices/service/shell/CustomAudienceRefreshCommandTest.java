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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceBackgroundFetchDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class CustomAudienceRefreshCommandTest
        extends ShellCommandTest<CustomAudienceRefreshCommand> {

    public static final Instant OVERRIDE_CURRENT_TIME = Instant.now();
    public static final Clock OVERRIDE_CLOCK =
            Clock.fixed(OVERRIDE_CURRENT_TIME, ZoneId.systemDefault());
    private static final DBCustomAudience CUSTOM_AUDIENCE =
            DBCustomAudienceFixture.getValidBuilderByBuyer(
                            AdTechIdentifier.fromString("example.com"))
                    .build();
    private static final String OWNER = CUSTOM_AUDIENCE.getOwner();
    private static final AdTechIdentifier BUYER = CUSTOM_AUDIENCE.getBuyer();
    private static final String NAME = CUSTOM_AUDIENCE.getName();
    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA =
            DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(BUYER)
                    .setOwner(OWNER)
                    .setIsDebuggable(CUSTOM_AUDIENCE.isDebuggable())
                    .build();

    @Mock private BackgroundFetchRunner mBackgroundFetchRunnerMock;
    @Mock private CustomAudienceDao mCustomAudienceDao;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testRun_withValidCustomAudience_returnsSuccess() {
        when(mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        when(mBackgroundFetchRunnerMock.updateCustomAudience(
                        OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(STATUS_SUCCESS)));

        ShellCommandTest.Result result =
                runRefreshCustomAudienceCommand(
                        CustomAudienceRefreshCommand.BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS);

        verify(mBackgroundFetchRunnerMock, times(1))
                .updateCustomAudience(OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        expectSuccess(result);
    }

    @Test
    public void testRun_withInvalidCustomAudienceBackgroundFetchData_returnsGenericFailure() {
        when(mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER, BUYER, NAME))
                .thenReturn(null);
        when(mBackgroundFetchRunnerMock.updateCustomAudience(
                        OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(STATUS_SUCCESS)));

        ShellCommandTest.Result result =
                runRefreshCustomAudienceCommand(
                        CustomAudienceRefreshCommand.BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS);

        verify(mBackgroundFetchRunnerMock, never())
                .updateCustomAudience(OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        expectFailure(result, CustomAudienceRefreshCommand.OUTPUT_ERROR_NO_CUSTOM_AUDIENCE);
    }

    @Test
    public void testRun_withFailedBackgroundFetch_returnsGenericFailure() {
        when(mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        when(mBackgroundFetchRunnerMock.updateCustomAudience(
                        OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(STATUS_INTERNAL_ERROR)));

        ShellCommandTest.Result result =
                runRefreshCustomAudienceCommand(
                        CustomAudienceRefreshCommand.BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS);

        verify(mBackgroundFetchRunnerMock)
                .updateCustomAudience(OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        expectFailure(
                result,
                String.format(
                        CustomAudienceRefreshCommand.OUTPUT_ERROR_WITH_CODE,
                        STATUS_INTERNAL_ERROR));
    }

    @Test
    public void testRun_withTimedOutBackgroundFetch_returnsGenericFailure() {
        when(mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        when(mBackgroundFetchRunnerMock.updateCustomAudience(
                        OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(Futures.immediateFuture(STATUS_INTERNAL_ERROR)));

        ShellCommandTest.Result result = runRefreshCustomAudienceCommand(0);

        verify(mBackgroundFetchRunnerMock)
                .updateCustomAudience(OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        expectFailure(
                result,
                String.format(
                        CustomAudienceRefreshCommand.OUTPUT_ERROR_WITH_CODE,
                        STATUS_INTERNAL_ERROR));
    }

    private ShellCommandTest.Result runRefreshCustomAudienceCommand(int timeoutInSeconds) {
        return run(
                new CustomAudienceRefreshCommand(
                        mBackgroundFetchRunnerMock,
                        mCustomAudienceDao,
                        timeoutInSeconds,
                        OVERRIDE_CLOCK,
                        AdServicesExecutors.getScheduler()),
                CustomAudienceShellCommandFactory.COMMAND_PREFIX,
                CustomAudienceRefreshCommand.CMD,
                "--owner",
                OWNER,
                "--buyer",
                BUYER.toString(),
                "--name",
                NAME);
    }
}
