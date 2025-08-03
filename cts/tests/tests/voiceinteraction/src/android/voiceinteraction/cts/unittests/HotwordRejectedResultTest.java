/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.Parcel;
import android.service.voice.HotwordRejectedResult;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link HotwordRejectedResult} APIs.
 */
@RunWith(AndroidJUnit4.class)
public class HotwordRejectedResultTest {

    @Test
    public void testHotwordRejectedResultBuilder() throws Exception {
        final HotwordRejectedResult hotwordRejectedResult =
                buildHotwordRejectedResult(HotwordRejectedResult.CONFIDENCE_LEVEL_LOW);

        assertThat(hotwordRejectedResult.getConfidenceLevel()).isEqualTo(
                HotwordRejectedResult.CONFIDENCE_LEVEL_LOW);
    }

    @Test
    public void testHotwordRejectedResultParcelizeDeparcelize() throws Exception {
        final HotwordRejectedResult hotwordRejectedResult =
                buildHotwordRejectedResult(HotwordRejectedResult.CONFIDENCE_LEVEL_LOW);

        final Parcel p = Parcel.obtain();
        hotwordRejectedResult.writeToParcel(p, 0);
        p.setDataPosition(0);

        final HotwordRejectedResult targetHotwordRejectedResult =
                HotwordRejectedResult.CREATOR.createFromParcel(p);
        p.recycle();

        assertThat(hotwordRejectedResult).isEqualTo(targetHotwordRejectedResult);
    }

    private HotwordRejectedResult buildHotwordRejectedResult(int confidenceLevel) {
        return new HotwordRejectedResult.Builder()
                .setConfidenceLevel(confidenceLevel)
                .build();
    }
}
