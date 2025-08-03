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

import static org.testng.Assert.assertThrows;

import android.os.Parcel;
import android.service.voice.VisualQueryDetectedResult;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VisualQueryDetectedResultTest {

    private static final byte[] TEST_BYTES = new byte[] { 0, 1, 2, 3 };
    private static final String TEST_QUERY = "What time is it?";

    @Test
    public void testVisualQueryDetectedResult_setInvalidSpeakerId() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new VisualQueryDetectedResult.Builder().setSpeakerId(-1).build());
        assertThrows(IllegalArgumentException.class,
                () -> new VisualQueryDetectedResult.Builder().setSpeakerId(
                        VisualQueryDetectedResult.getMaxSpeakerId() + 1).build());
    }

    @Test
    public void testVisualQueryDetectedResultBuilder() throws Exception {
        final VisualQueryDetectedResult visualQueryDetectedResult =
                buildVisualQueryDetectedResult(
                        /* partialQuery= */ TEST_QUERY,
                        /* speakerId= */ 1,
                        /* AccessibilityDetectionData= */ TEST_BYTES);
        assertVisualQueryDetectedResult(visualQueryDetectedResult);

    }

    @Test
    public void testHotwordDetectedResultParcelizeDeparcelize() throws Exception {
        final VisualQueryDetectedResult visualQueryDetectedResult =
                buildVisualQueryDetectedResult(
                        /* partialQuery= */ TEST_QUERY,
                        /* speakerId= */ 1,
                        /* AccessibilityDetectionData= */ TEST_BYTES);
        final Parcel p = Parcel.obtain();
        visualQueryDetectedResult.writeToParcel(p, 0);
        p.setDataPosition(0);

        final VisualQueryDetectedResult targetVisualQueryDetectedResult =
                VisualQueryDetectedResult.CREATOR.createFromParcel(p);
        p.recycle();
        assertVisualQueryDetectedResult(targetVisualQueryDetectedResult);
    }

    private VisualQueryDetectedResult buildVisualQueryDetectedResult(
            String partialQuery,
            int speakerId,
            byte[] accessibilityDetectionData) {
        return new VisualQueryDetectedResult.Builder()
                .setPartialQuery(TEST_QUERY)
                .setSpeakerId(speakerId)
                .setAccessibilityDetectionData(accessibilityDetectionData)
                .build();
    }

    private void assertVisualQueryDetectedResult(
            VisualQueryDetectedResult visualQueryDetectedResult) {
        assertThat(visualQueryDetectedResult.getPartialQuery()).isEqualTo(TEST_QUERY);
        assertThat(visualQueryDetectedResult.getSpeakerId()).isEqualTo(1);
        assertThat(visualQueryDetectedResult.getAccessibilityDetectionData()).isEqualTo(TEST_BYTES);
    }
}
