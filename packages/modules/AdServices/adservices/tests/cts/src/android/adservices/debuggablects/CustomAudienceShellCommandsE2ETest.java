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

package android.adservices.debuggablects;

import static android.adservices.debuggablects.CustomAudienceShellCommandHelper.fromJson;
import static android.adservices.debuggablects.CustomAudienceShellCommandHelper.verifyActivationTime;
import static android.adservices.debuggablects.CustomAudienceShellCommandHelper.verifyBackgroundFetchData;

import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED;
import static com.android.adservices.service.FlagsConstants.PPAPI_AND_SYSTEM_SERVER;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.utils.CustomAudienceTestFixture;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;
import com.android.adservices.common.annotations.SetFlagEnabled;
import com.android.adservices.common.annotations.SetIntegerFlag;
import com.android.compatibility.common.util.ShellUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = PPAPI_AND_SYSTEM_SERVER)
@SetFlagEnabled(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_IS_CUSTOM_AUDIENCE_CLI_ENABLED)
@RequiresSdkLevelAtLeastT
public final class CustomAudienceShellCommandsE2ETest extends ForegroundDebuggableCtsTest {
    private static final String OWNER = "android.adservices.debuggablects";
    private CustomAudience mShirtsCustomAudience;
    private CustomAudience mShoesCustomAudience;
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private CustomAudienceTestFixture mCustomAudienceTestFixture;

    @Before
    public void setUp() throws Exception {
        AdservicesTestHelper.killAdservicesProcess(sContext);
        assertForegroundActivityStarted();

        mCustomAudienceTestFixture = new CustomAudienceTestFixture(sContext);
        mShirtsCustomAudience =
                mCustomAudienceTestFixture.createCustomAudience(
                        "shirts",
                        BUYER,
                        List.of(1D),
                        null,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME);
        mShoesCustomAudience =
                mCustomAudienceTestFixture.createCustomAudience(
                        "shoes",
                        BUYER,
                        List.of(1D),
                        null,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME);
    }

    @After
    public void tearDown() throws Exception {
        mCustomAudienceTestFixture.leaveJoinedCustomAudiences();
    }

    @Test
    public void testRun_listCustomAudience_happyPath() throws Exception {
        mCustomAudienceTestFixture.joinCustomAudience(mShirtsCustomAudience);
        mCustomAudienceTestFixture.joinCustomAudience(mShoesCustomAudience);

        JSONArray customAudiences =
                runAndParseShellCommandJson(
                                "cmd adservices_manager %s %s --owner %s --buyer %s",
                                "custom-audience", "list", OWNER, BUYER.toString())
                        .getJSONArray("custom_audiences");
        mCustomAudienceTestFixture.leaveCustomAudience(mShirtsCustomAudience);
        JSONArray customAudiencesAfterLeaving =
                runAndParseShellCommandJson(
                                "cmd adservices_manager %s %s --owner %s --buyer %s",
                                "custom-audience", "list", OWNER, BUYER.toString())
                        .getJSONArray("custom_audiences");

        assertThat(
                        List.of(
                                fromJson(customAudiences.getJSONObject(0)),
                                fromJson(customAudiences.getJSONObject(1))))
                .containsExactly(mShirtsCustomAudience, mShoesCustomAudience);
        assertThat(fromJson(customAudiencesAfterLeaving.getJSONObject(0)))
                .isEqualTo(mShoesCustomAudience);
        verifyActivationTime(customAudiences.getJSONObject(0));
        verifyActivationTime(customAudiences.getJSONObject(1));
        verifyActivationTime(customAudiencesAfterLeaving.getJSONObject(0));
        verifyBackgroundFetchData(customAudiences.getJSONObject(0), 0, 0);
        verifyBackgroundFetchData(customAudiences.getJSONObject(1), 0, 0);
        verifyBackgroundFetchData(customAudiencesAfterLeaving.getJSONObject(0), 0, 0);
    }

    @Test
    public void testRun_viewCustomAudience_happyPath() throws Exception {
        mCustomAudienceTestFixture.joinCustomAudience(mShirtsCustomAudience);

        JSONObject customAudience =
                runAndParseShellCommandJson(
                        "cmd adservices_manager %s %s --owner %s --buyer %s --name %s",
                        "custom-audience",
                        "view",
                        OWNER,
                        BUYER.toString(),
                        mShirtsCustomAudience.getName());

        CustomAudience parsedCustomAudience = fromJson(customAudience);
        assertThat(mShirtsCustomAudience).isEqualTo(parsedCustomAudience);
        verifyActivationTime(customAudience);
        verifyBackgroundFetchData(customAudience, 0, 0);
    }

    @Test
    public void testRun_refreshCustomAudiences_verifyNoCustomAudienceChanged() {
        String output =
                runAndParseShellCommand(
                        "custom-audience",
                        "refresh",
                        OWNER,
                        BUYER.toString(),
                        mShirtsCustomAudience.getName());

        // Shell command output would be printed to stderr instead, so cannot be captured here.
        assertThat(output).isEmpty();
    }

    private static JSONObject runAndParseShellCommandJson(String template, String... commandArgs)
            throws JSONException {
        return new JSONObject(ShellUtils.runShellCommand(template, (Object[]) commandArgs));
    }

    private static String runAndParseShellCommand(String... commandArgs) {
        return ShellUtils.runShellCommand(
                "cmd adservices_manager %s %s --owner %s --buyer %s --name %s",
                (Object[]) commandArgs);
    }
}
