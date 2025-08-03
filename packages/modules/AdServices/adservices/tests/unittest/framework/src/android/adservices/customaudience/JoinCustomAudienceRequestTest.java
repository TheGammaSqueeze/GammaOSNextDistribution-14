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

package android.adservices.customaudience;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.CommonFixture;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class JoinCustomAudienceRequestTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testJoinCustomAudienceRequestWithSameValuesAreEqual() {
        JoinCustomAudienceRequest obj1 =
                JoinCustomAudienceRequestFixture.getJoinCustomAudienceRequest(
                        JoinCustomAudienceRequestFixture.CUSTOM_AUDIENCE_1);
        JoinCustomAudienceRequest obj2 =
                JoinCustomAudienceRequestFixture.getJoinCustomAudienceRequest(
                        JoinCustomAudienceRequestFixture.CUSTOM_AUDIENCE_1);

        assertThat(obj1).isEqualTo(obj2);
    }

    @Test
    public void testJoinCustomAudienceRequestWithDifferentValuesAreNotEqual() {
        JoinCustomAudienceRequest obj1 =
                JoinCustomAudienceRequestFixture.getJoinCustomAudienceRequest(
                        JoinCustomAudienceRequestFixture.CUSTOM_AUDIENCE_1);
        JoinCustomAudienceRequest obj2 =
                JoinCustomAudienceRequestFixture.getJoinCustomAudienceRequest(
                        JoinCustomAudienceRequestFixture.CUSTOM_AUDIENCE_2);

        assertThat(obj1).isNotEqualTo(obj2);
    }

    @Test
    public void testEqualJoinCustomAudienceRequestHaveSameHashCodes() {
        JoinCustomAudienceRequest obj1 =
                JoinCustomAudienceRequestFixture.getJoinCustomAudienceRequest(
                        JoinCustomAudienceRequestFixture.CUSTOM_AUDIENCE_1);
        JoinCustomAudienceRequest obj2 =
                JoinCustomAudienceRequestFixture.getJoinCustomAudienceRequest(
                        JoinCustomAudienceRequestFixture.CUSTOM_AUDIENCE_1);

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualJoinCustomAudienceRequestHaveDifferentHashCodes() {
        JoinCustomAudienceRequest obj1 =
                JoinCustomAudienceRequestFixture.getJoinCustomAudienceRequest(
                        JoinCustomAudienceRequestFixture.CUSTOM_AUDIENCE_1);
        JoinCustomAudienceRequest obj2 =
                JoinCustomAudienceRequestFixture.getJoinCustomAudienceRequest(
                        JoinCustomAudienceRequestFixture.CUSTOM_AUDIENCE_2);

        CommonFixture.assertDifferentHashCode(obj1, obj2);
    }
}
