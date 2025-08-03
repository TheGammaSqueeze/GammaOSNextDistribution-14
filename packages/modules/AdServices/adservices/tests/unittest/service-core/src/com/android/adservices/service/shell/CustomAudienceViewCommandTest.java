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

import static com.android.adservices.service.shell.CustomAudienceHelper.getCustomAudienceBackgroundFetchDataFromJson;
import static com.android.adservices.service.shell.CustomAudienceHelper.getCustomAudienceFromJson;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.customaudience.DBCustomAudienceBackgroundFetchDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;

import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

public final class CustomAudienceViewCommandTest
        extends ShellCommandTest<CustomAudienceViewCommand> {

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");
    private static final String CA_NAME = CustomAudienceFixture.VALID_NAME;
    public static final String OWNER = CustomAudienceFixture.VALID_OWNER;
    private static final DBCustomAudience CUSTOM_AUDIENCE_1 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER, CA_NAME)
                    .setOwner(OWNER)
                    .setDebuggable(true)
                    .build();
    private static final DBCustomAudienceBackgroundFetchData
            CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1 =
                    DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(BUYER)
                            .setOwner(CUSTOM_AUDIENCE_1.getOwner())
                            .setIsDebuggable(CUSTOM_AUDIENCE_1.isDebuggable())
                            .build();
    @Mock private CustomAudienceDao mCustomAudienceDao;

    @Test
    public void testRun_missingArgument_returnsHelp() throws Exception {
        runAndExpectInvalidArgument(
                new CustomAudienceViewCommand(mCustomAudienceDao),
                CustomAudienceViewCommand.HELP,
                CustomAudienceShellCommandFactory.COMMAND_PREFIX,
                CustomAudienceViewCommand.CMD,
                "--owner",
                "valid-owner");
    }

    @Test
    public void testRun_happyPath_returnsSuccess() throws Exception {
        when(mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_1);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult);
        assertThat(getCustomAudienceFromJson(new JSONObject(actualResult.mOut)))
                .isEqualTo(CUSTOM_AUDIENCE_1);
        assertThat(getCustomAudienceBackgroundFetchDataFromJson(new JSONObject(actualResult.mOut)))
                .isEqualTo(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);
    }

    @Test
    public void testRun_notPresent_returnsEmpty() {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(List.of(CUSTOM_AUDIENCE_1));
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult, "{}");
    }

    @Test
    public void testRun_presentButNotDebuggable_returnsEmpty() {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(
                        List.of(CUSTOM_AUDIENCE_1.cloneToBuilder().setDebuggable(false).build()));
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult, "{}");
    }

    @Test
    public void testRun_presentButBackgroundFetchDataNotDebuggable_returnsEmpty() {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(List.of(CUSTOM_AUDIENCE_1));
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(
                        DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(BUYER)
                                .setOwner(OWNER)
                                .setIsDebuggable(false)
                                .build());

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult, "{}");
    }

    private Result runCommandAndGetResult() {
        return run(
                new CustomAudienceViewCommand(mCustomAudienceDao),
                CustomAudienceShellCommandFactory.COMMAND_PREFIX,
                CustomAudienceViewCommand.CMD,
                "--owner",
                OWNER,
                "--buyer",
                BUYER.toString(),
                "--name",
                CA_NAME);
    }
}
