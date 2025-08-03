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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.customaudience.DBCustomAudienceBackgroundFetchDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

public final class CustomAudienceListCommandTest
        extends ShellCommandTest<CustomAudienceListCommand> {

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");
    public static final String OWNER = CustomAudienceFixture.VALID_OWNER;
    private static final DBCustomAudience CUSTOM_AUDIENCE_1 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER, "ca1").setOwner(OWNER).build();
    private static final DBCustomAudience CUSTOM_AUDIENCE_2 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER, "ca2").setOwner(OWNER).build();
    private static final DBCustomAudienceBackgroundFetchData
            CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1 =
                    DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(BUYER)
                            .setName(CUSTOM_AUDIENCE_1.getName())
                            .setOwner(CUSTOM_AUDIENCE_1.getOwner())
                            .setIsDebuggable(CUSTOM_AUDIENCE_1.isDebuggable())
                            .build();
    private static final DBCustomAudienceBackgroundFetchData
            CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_2 =
                    DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(BUYER)
                            .setName(CUSTOM_AUDIENCE_2.getName())
                            .setOwner(CUSTOM_AUDIENCE_2.getOwner())
                            .setIsDebuggable(CUSTOM_AUDIENCE_2.isDebuggable())
                            .build();

    @Mock private CustomAudienceDao mCustomAudienceDao;
    @Test
    public void testRun_simpleCase_returnsSuccess() throws Exception {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(List.of(CUSTOM_AUDIENCE_1));
        when(mCustomAudienceDao.listDebuggableCustomAudienceBackgroundFetchData(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(List.of(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1));

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult);
        JSONArray jsonArray = new JSONObject(actualResult.mOut).getJSONArray("custom_audiences");
        assertWithMessage("Length of JsonArray (%s)", jsonArray)
                .that(jsonArray.length())
                .isEqualTo(1);
        assertThat(getCustomAudienceFromJson(jsonArray.getJSONObject(0)))
                .isEqualTo(CUSTOM_AUDIENCE_1);
        assertThat(getCustomAudienceBackgroundFetchDataFromJson(jsonArray.getJSONObject(0)))
                .isEqualTo(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);
    }

    @Test
    public void testRun_missingBackgroundFetchData_returnsEmpty() throws Exception {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(List.of(CUSTOM_AUDIENCE_1, CUSTOM_AUDIENCE_2));
        when(mCustomAudienceDao.listDebuggableCustomAudienceBackgroundFetchData(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(List.of(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_2));

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult);
        JSONArray jsonArray = new JSONObject(actualResult.mOut).getJSONArray("custom_audiences");
        assertWithMessage("Length of JsonArray (%s)", jsonArray)
                .that(jsonArray.length())
                .isEqualTo(1);
        assertThat(getCustomAudienceFromJson(jsonArray.getJSONObject(0)))
                .isEqualTo(CUSTOM_AUDIENCE_2);
        assertThat(getCustomAudienceBackgroundFetchDataFromJson(jsonArray.getJSONObject(0)))
                .isEqualTo(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_2);
    }

    @Test
    public void testRun_missingArgument_returnsGenericError() throws Exception {
        runAndExpectInvalidArgument(
                new CustomAudienceListCommand(mCustomAudienceDao),
                CustomAudienceListCommand.HELP,
                CustomAudienceListCommand.CMD,
                "--owner",
                "valid-owner");
    }

    @Test
    public void testRun_noCustomAudiences_returnsEmpty() throws Exception {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(any(), any()))
                .thenReturn(List.of());
        when(mCustomAudienceDao.listDebuggableCustomAudienceBackgroundFetchData(any(), any()))
                .thenReturn(List.of());

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult);
        JSONArray jsonArray = new JSONObject(actualResult.mOut).getJSONArray("custom_audiences");
        assertWithMessage("Length of JsonArray (%s)", jsonArray)
                .that(jsonArray.length())
                .isEqualTo(0);
    }

    @Test
    public void testRun_complexCase_returnsSuccess() throws Exception {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(List.of(CUSTOM_AUDIENCE_1, CUSTOM_AUDIENCE_2));
        when(mCustomAudienceDao.listDebuggableCustomAudienceBackgroundFetchData(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(
                        List.of(
                                CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1,
                                CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_2));

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult);
        JSONArray jsonArray = new JSONObject(actualResult.mOut).getJSONArray("custom_audiences");
        assertWithMessage("Length of JsonArray (%s)", jsonArray)
                .that(jsonArray.length())
                .isEqualTo(2);
        assertThat(getCustomAudienceFromJson(jsonArray.getJSONObject(0)))
                .isEqualTo(CUSTOM_AUDIENCE_1);
        assertThat(getCustomAudienceBackgroundFetchDataFromJson(jsonArray.getJSONObject(0)))
                .isEqualTo(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);
        assertThat(getCustomAudienceFromJson(jsonArray.getJSONObject(1)))
                .isEqualTo(CUSTOM_AUDIENCE_2);
        assertThat(getCustomAudienceBackgroundFetchDataFromJson(jsonArray.getJSONObject(1)))
                .isEqualTo(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_2);
    }

    private Result runCommandAndGetResult() {
        return run(
                new CustomAudienceListCommand(mCustomAudienceDao),
                CustomAudienceShellCommandFactory.COMMAND_PREFIX,
                CustomAudienceListCommand.CMD,
                "--owner",
                OWNER,
                "--buyer",
                BUYER.toString());
    }
}
