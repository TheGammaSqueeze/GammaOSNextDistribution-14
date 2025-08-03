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
import android.service.voice.HotwordDetectionServiceFailure;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link HotwordDetectionServiceFailure} APIs.
 */
@RunWith(AndroidJUnit4.class)
public class HotwordDetectionServiceFailureTest {
    static final int TEST_ERROR_CODE = HotwordDetectionServiceFailure.ERROR_CODE_BINDING_DIED;
    static final String TEST_ERROR_MESSAGE = "Error for HotwordDetectionServiceFailureTest";

    @Test
    public void testHotwordDetectionServiceFailure_nullErrorMessage() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new HotwordDetectionServiceFailure(TEST_ERROR_CODE, /* errorMessage= */
                        null));
    }

    @Test
    public void testHotwordDetectionServiceFailure_getErrorCode() throws Exception {
        final HotwordDetectionServiceFailure hotwordDetectionServiceFailure =
                new HotwordDetectionServiceFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        assertThat(hotwordDetectionServiceFailure.getErrorCode()).isEqualTo(TEST_ERROR_CODE);
    }

    @Test
    public void testHotwordDetectionServiceFailure_getErrorMessage() throws Exception {
        final HotwordDetectionServiceFailure hotwordDetectionServiceFailure =
                new HotwordDetectionServiceFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        assertThat(hotwordDetectionServiceFailure.getErrorMessage()).isEqualTo(TEST_ERROR_MESSAGE);
    }

    @Test
    public void testHotwordDetectionServiceFailure_getSuggestedAction() throws Exception {
        final HotwordDetectionServiceFailure hotwordDetectionServiceFailure =
                new HotwordDetectionServiceFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        assertThat(hotwordDetectionServiceFailure.getSuggestedAction()).isEqualTo(
                FailureSuggestedAction.RECREATE_DETECTOR);
    }

    @Test
    public void testHotwordDetectionServiceFailureParcelizeDeparcelize() throws Exception {
        final HotwordDetectionServiceFailure hotwordDetectionServiceFailure =
                new HotwordDetectionServiceFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        final Parcel p = Parcel.obtain();
        hotwordDetectionServiceFailure.writeToParcel(p, 0);
        p.setDataPosition(0);

        final HotwordDetectionServiceFailure targetHotwordDetectionServiceFailure =
                HotwordDetectionServiceFailure.CREATOR.createFromParcel(p);
        p.recycle();

        assertThat(hotwordDetectionServiceFailure.getErrorCode()).isEqualTo(
                targetHotwordDetectionServiceFailure.getErrorCode());
        assertThat(hotwordDetectionServiceFailure.getErrorMessage()).isEqualTo(
                targetHotwordDetectionServiceFailure.getErrorMessage());
        assertThat(hotwordDetectionServiceFailure.getSuggestedAction()).isEqualTo(
                targetHotwordDetectionServiceFailure.getSuggestedAction());
    }
}
