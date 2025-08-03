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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;


@SmallTest
public class AdSelectionOutcomeTest {
    private static final Uri VALID_RENDER_URI =
            new Uri.Builder().path("valid.example.com/testing/hello").build();
    private static final int TEST_AD_SELECTION_ID = 12345;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildAdSelectionOutcome() {
        AdSelectionOutcome adSelectionOutcome =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        assertThat(adSelectionOutcome.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(adSelectionOutcome.getRenderUri()).isEqualTo(VALID_RENDER_URI);
    }

    @Test
    public void testBuildAdSelectionOutcomeChecksIfOutcomeIsEmpty() {
        AdSelectionOutcome notEmptyOutcome =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();
        AdSelectionOutcome emptyOutcome = AdSelectionOutcome.NO_OUTCOME;

        assertThat(notEmptyOutcome.hasOutcome()).isTrue();
        assertThat(emptyOutcome.hasOutcome()).isFalse();
    }

    @Test
    public void testAdSelectionOutcomeWithSameValuesAreEqual() {
        AdSelectionOutcome obj1 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        AdSelectionOutcome obj2 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        assertThat(obj1).isEqualTo(obj2);
    }

    @Test
    public void testAdSelectionOutcomeWithDifferentValuesAreNotEqual() {
        AdSelectionOutcome obj1 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        AdSelectionOutcome obj2 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(
                                new Uri.Builder().path("different.url.com/testing/hello").build())
                        .build();
        assertThat(obj1).isNotEqualTo(obj2);
    }

    @Test
    public void testEqualAdSelectionOutcomesHaveSameHashCode() {
        AdSelectionOutcome obj1 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();
        AdSelectionOutcome obj2 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualAdSelectionOutcomesHaveDifferentHashCodes() {
        AdSelectionOutcome obj1 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();
        AdSelectionOutcome obj2 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUri(
                                new Uri.Builder().path("different.url.com/testing/hello").build())
                        .build();
        AdSelectionOutcome obj3 =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(13579)
                        .setRenderUri(VALID_RENDER_URI)
                        .build();

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3);
    }
}
