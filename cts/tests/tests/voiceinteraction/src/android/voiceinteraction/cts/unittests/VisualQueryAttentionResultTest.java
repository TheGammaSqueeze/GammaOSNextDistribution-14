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

package android.voiceinteraction.cts.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;
import android.service.voice.VisualQueryAttentionResult;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VisualQueryAttentionResultTest {

    private static final int TEST_VALID_INTERACTION_INTENTION_AUDIO_VISUAL =
            VisualQueryAttentionResult.INTERACTION_INTENTION_AUDIO_VISUAL;
    private static final int TEST_VALID_INTERACTION_INTENTION_VISUAL_ACCESSIBILITY =
            VisualQueryAttentionResult.INTERACTION_INTENTION_VISUAL_ACCESSIBILITY;
    private static final int TEST_INVALID_INTERACTION_INTENTION = -1;
    private static final int TEST_VALID_ENGAGEMENT_LEVEL = 1;
    private static final int TEST_INVALID_ENGAGEMENT_LEVEL_HIGH = 101;
    private static final int TEST_INVALID_ENGAGEMENT_LEVEL_LOW = 0;

    @Test
    public void testVisualQueryAttentionResult_buildAudioVisualAttentionSuccess() throws Exception {
        final VisualQueryAttentionResult visualQueryAttentionResult =
                buildVisualQueryAttentionResult(
                        TEST_VALID_INTERACTION_INTENTION_AUDIO_VISUAL,
                        TEST_VALID_ENGAGEMENT_LEVEL);
        assertThat(visualQueryAttentionResult.getInteractionIntention()).isEqualTo(
                TEST_VALID_INTERACTION_INTENTION_AUDIO_VISUAL);
        assertThat(visualQueryAttentionResult.getEngagementLevel()).isEqualTo(
                TEST_VALID_ENGAGEMENT_LEVEL);
    }

    @Test
    public void testVisualQueryAttentionResult_buildAttentionResultFail_invalidLowEngagementLevel()
            throws Exception {
        assertThrows(IllegalStateException.class,
                () -> {
                    buildVisualQueryAttentionResult(
                            TEST_VALID_INTERACTION_INTENTION_AUDIO_VISUAL,
                            TEST_INVALID_ENGAGEMENT_LEVEL_LOW);
                });
    }

    @Test
    public void testVisualQueryAttentionResult_buildAttentionResultFail_invalidHighEngagementLevel()
            throws Exception {
        assertThrows(IllegalStateException.class,
                () -> {
                    buildVisualQueryAttentionResult(
                            TEST_VALID_INTERACTION_INTENTION_AUDIO_VISUAL,
                            TEST_INVALID_ENGAGEMENT_LEVEL_HIGH);
                });
    }

    @Test
    public void testVisualQueryAttentionResult_buildAttentionResultFail_invalidInteractionIntent()
            throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> {
                    buildVisualQueryAttentionResult(
                            TEST_INVALID_INTERACTION_INTENTION,
                            TEST_VALID_ENGAGEMENT_LEVEL);
                });
    }

    @Test
    public void testVisualQueryAttentionResult_buildAccessibilityInteractionIntentSuccess()
            throws Exception {
        final VisualQueryAttentionResult visualQueryAttentionResult =
                buildVisualQueryAttentionResult(
                        TEST_VALID_INTERACTION_INTENTION_VISUAL_ACCESSIBILITY,
                        TEST_VALID_ENGAGEMENT_LEVEL);
        assertThat(visualQueryAttentionResult.getInteractionIntention()).isEqualTo(
                TEST_VALID_INTERACTION_INTENTION_VISUAL_ACCESSIBILITY);
        assertThat(visualQueryAttentionResult.getEngagementLevel()).isEqualTo(
                TEST_VALID_ENGAGEMENT_LEVEL);
    }

    @Test
    public void testVisualQueryAttentionResultParcelizeDeparcelize() throws Exception {
        final VisualQueryAttentionResult visualQueryAttentionResult =
                buildVisualQueryAttentionResult(
                        TEST_VALID_INTERACTION_INTENTION_VISUAL_ACCESSIBILITY,
                        TEST_VALID_ENGAGEMENT_LEVEL);
        final Parcel p = Parcel.obtain();
        visualQueryAttentionResult.writeToParcel(p, 0);
        p.setDataPosition(0);

        final VisualQueryAttentionResult targetVisualQueryAttentionResult =
                VisualQueryAttentionResult.CREATOR.createFromParcel(p);
        p.recycle();

        assertThat(visualQueryAttentionResult.getInteractionIntention()).isEqualTo(
                targetVisualQueryAttentionResult.getInteractionIntention());
        assertThat(visualQueryAttentionResult.getEngagementLevel()).isEqualTo(
                targetVisualQueryAttentionResult.getEngagementLevel());
    }

    private VisualQueryAttentionResult buildVisualQueryAttentionResult(int interactionIntention,
            int engagementLevel) {
        return new VisualQueryAttentionResult.Builder()
                .setInteractionIntention(interactionIntention)
                .setEngagementLevel(engagementLevel)
                .build();
    }
}
