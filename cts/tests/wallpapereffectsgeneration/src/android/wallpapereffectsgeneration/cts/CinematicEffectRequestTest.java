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

package android.wallpapereffectsgeneration.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.wallpapereffectsgeneration.CinematicEffectRequest;
import android.graphics.Bitmap;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/**
 * Tests for {@link CinematicEffectRequest}
 *
 * atest CtsWallpaperEffectsGenerationServiceTestCases
 */

@RunWith(AndroidJUnit4.class)
public class CinematicEffectRequestTest {
    private static final String TAG = "WallpaperEffectsGenerationTest";

    @Test
    public void testCreateCinematicEffectRequest() {
        final String taskId = UUID.randomUUID().toString();
        final Bitmap bmp = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888);
        CinematicEffectRequest request = new CinematicEffectRequest(taskId, bmp);

        /** Check the original request. */
        assertThat(request.getTaskId()).isEqualTo(taskId);
        assertThat(request.getBitmap()).isEqualTo(bmp);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CinematicEffectRequest copy =
                CinematicEffectRequest.CREATOR.createFromParcel(parcel);
        /** Check the copied request. */
        assertThat(copy.getTaskId()).isEqualTo(taskId);
        assertThat(copy.getBitmap().sameAs(bmp)).isTrue();

        parcel.recycle();
    }
}
