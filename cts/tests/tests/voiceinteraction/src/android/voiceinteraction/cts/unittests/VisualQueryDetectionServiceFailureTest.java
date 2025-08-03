/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.service.voice.FailureSuggestedAction;
import android.service.voice.VisualQueryDetectionServiceFailure;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link VisualQueryDetectionServiceFailure} APIs.
 */
@RunWith(AndroidJUnit4.class)
public class VisualQueryDetectionServiceFailureTest {
    static final int TEST_ERROR_CODE = VisualQueryDetectionServiceFailure.ERROR_CODE_BINDING_DIED;
    static final String TEST_ERROR_MESSAGE = "Error for VisualQueryDetectionServiceFailureTest";

    @Test
    public void testVisualQueryDetectionServiceFailure_nullErrorMessage() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new VisualQueryDetectionServiceFailure(TEST_ERROR_CODE, /* errorMessage= */
                        null));
    }

    @Test
    public void testVisualQueryDetectionServiceFailure_getErrorCode() throws Exception {
        final VisualQueryDetectionServiceFailure visualQueryDetectionServiceFailure =
                new VisualQueryDetectionServiceFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        assertThat(visualQueryDetectionServiceFailure.getErrorCode()).isEqualTo(TEST_ERROR_CODE);
    }

    @Test
    public void testVisualQueryDetectionServiceFailure_getErrorMessage() throws Exception {
        final VisualQueryDetectionServiceFailure visualQueryDetectionServiceFailure =
                new VisualQueryDetectionServiceFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        assertThat(visualQueryDetectionServiceFailure.getErrorMessage()).isEqualTo(
                TEST_ERROR_MESSAGE);
    }

    @Test
    public void testVisualQueryDetectionServiceFailure_getSuggestedAction() throws Exception {
        final VisualQueryDetectionServiceFailure visualQueryDetectionServiceFailure =
                new VisualQueryDetectionServiceFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        assertThat(visualQueryDetectionServiceFailure.getSuggestedAction()).isEqualTo(
                FailureSuggestedAction.RECREATE_DETECTOR);
    }

    @Test
    public void testVisualQueryDetectionServiceFailureParcelizeDeparcelize() throws Exception {
        final VisualQueryDetectionServiceFailure visualQueryDetectionServiceFailure =
                new VisualQueryDetectionServiceFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        final Parcel p = Parcel.obtain();
        visualQueryDetectionServiceFailure.writeToParcel(p, 0);
        p.setDataPosition(0);

        final VisualQueryDetectionServiceFailure targetVisualQueryDetectionServiceFailure =
                VisualQueryDetectionServiceFailure.CREATOR.createFromParcel(p);
        p.recycle();

        assertThat(visualQueryDetectionServiceFailure.getErrorCode()).isEqualTo(
                targetVisualQueryDetectionServiceFailure.getErrorCode());
        assertThat(visualQueryDetectionServiceFailure.getErrorMessage()).isEqualTo(
                targetVisualQueryDetectionServiceFailure.getErrorMessage());
        assertThat(visualQueryDetectionServiceFailure.getSuggestedAction()).isEqualTo(
                targetVisualQueryDetectionServiceFailure.getSuggestedAction());
    }
}
