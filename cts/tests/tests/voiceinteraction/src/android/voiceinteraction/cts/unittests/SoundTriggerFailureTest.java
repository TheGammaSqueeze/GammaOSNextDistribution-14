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
import android.service.voice.SoundTriggerFailure;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SoundTriggerFailure} APIs.
 */
@RunWith(AndroidJUnit4.class)
public class SoundTriggerFailureTest {
    static final int TEST_ERROR_CODE = SoundTriggerFailure.ERROR_CODE_MODULE_DIED;
    static final String TEST_ERROR_MESSAGE = "Error for SoundTriggerFailureTest";

    @Test
    public void testSoundTriggerFailure_nullErrorMessage() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new SoundTriggerFailure(TEST_ERROR_CODE, /* errorMessage= */ null));
    }

    @Test
    public void testSoundTriggerFailure_getErrorCode() throws Exception {
        final SoundTriggerFailure soundTriggerFailure =
                new SoundTriggerFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        assertThat(soundTriggerFailure.getErrorCode()).isEqualTo(TEST_ERROR_CODE);
    }

    @Test
    public void testSoundTriggerFailure_getErrorMessage() throws Exception {
        final SoundTriggerFailure soundTriggerFailure =
                new SoundTriggerFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        assertThat(soundTriggerFailure.getErrorMessage()).isEqualTo(TEST_ERROR_MESSAGE);
    }

    @Test
    public void testSoundTriggerFailure_getSuggestedAction() throws Exception {
        final SoundTriggerFailure soundTriggerFailure =
                new SoundTriggerFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        assertThat(soundTriggerFailure.getSuggestedAction()).isEqualTo(
                FailureSuggestedAction.RECREATE_DETECTOR);
    }

    @Test
    public void testSoundTriggerFailureParcelizeDeparcelize() throws Exception {
        final SoundTriggerFailure soundTriggerFailure =
                new SoundTriggerFailure(TEST_ERROR_CODE, TEST_ERROR_MESSAGE);

        final Parcel p = Parcel.obtain();
        soundTriggerFailure.writeToParcel(p, 0);
        p.setDataPosition(0);

        final SoundTriggerFailure targetSoundTriggerFailure =
                SoundTriggerFailure.CREATOR.createFromParcel(p);
        p.recycle();

        assertThat(soundTriggerFailure.getErrorCode()).isEqualTo(
                targetSoundTriggerFailure.getErrorCode());
        assertThat(soundTriggerFailure.getErrorMessage()).isEqualTo(
                targetSoundTriggerFailure.getErrorMessage());
        assertThat(soundTriggerFailure.getSuggestedAction()).isEqualTo(
                targetSoundTriggerFailure.getSuggestedAction());
    }
}
