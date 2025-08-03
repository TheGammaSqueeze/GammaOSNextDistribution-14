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

import android.app.wallpapereffectsgeneration.CameraAttributes;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link CameraAttributes}
 *
 * atest CtsWallpaperEffectsGenerationServiceTestCases
 */

@RunWith(AndroidJUnit4.class)
public class CameraAttributesTest {
    private static final String TAG = "WallpaperEffectsGenerationTest";

    @Test
    public void testCreateCameraAttributesRequest() {
        final float[] anchorPointInWorldSpace = new float[]{0.5f, 1.5f, -1.0f};
        final float[] anchorPointInOutputUvSpace = new float[]{0.5f, 2.5f};
        final float yaw = 35.2f;
        final float pitch = 45.6f;
        final float dolly = 0.3f;
        final float fov = 60.0f;
        final float frustumNear = 0.5f;
        final float frustumFar = 200.0f;

        CameraAttributes attributes = new CameraAttributes.Builder(anchorPointInWorldSpace,
                anchorPointInOutputUvSpace)
                .setCameraOrbitYawDegrees(yaw)
                .setCameraOrbitPitchDegrees(pitch)
                .setDollyDistanceInWorldSpace(dolly)
                .setVerticalFovDegrees(fov)
                .setFrustumNearInWorldSpace(frustumNear)
                .setFrustumFarInWorldSpace(frustumFar)
                .build();

        /** Check the original attributes. */
        assertThat(attributes.getAnchorPointInWorldSpace()).isEqualTo(anchorPointInWorldSpace);
        assertThat(attributes.getAnchorPointInOutputUvSpace()).isEqualTo(
                anchorPointInOutputUvSpace);
        assertThat(attributes.getCameraOrbitYawDegrees()).isEqualTo(yaw);
        assertThat(attributes.getCameraOrbitPitchDegrees()).isEqualTo(pitch);
        assertThat(attributes.getDollyDistanceInWorldSpace()).isEqualTo(dolly);
        assertThat(attributes.getVerticalFovDegrees()).isEqualTo(fov);
        assertThat(attributes.getFrustumNearInWorldSpace()).isEqualTo(frustumNear);
        assertThat(attributes.getFrustumFarInWorldSpace()).isEqualTo(frustumFar);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        attributes.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CameraAttributes copy =
                CameraAttributes.CREATOR.createFromParcel(parcel);
        /** Check the copied attributes */
        assertThat(copy.getAnchorPointInWorldSpace()).isEqualTo(anchorPointInWorldSpace);
        assertThat(copy.getAnchorPointInOutputUvSpace()).isEqualTo(anchorPointInOutputUvSpace);
        assertThat(copy.getCameraOrbitYawDegrees()).isEqualTo(yaw);
        assertThat(copy.getCameraOrbitPitchDegrees()).isEqualTo(pitch);
        assertThat(copy.getDollyDistanceInWorldSpace()).isEqualTo(dolly);
        assertThat(copy.getVerticalFovDegrees()).isEqualTo(fov);
        assertThat(copy.getFrustumNearInWorldSpace()).isEqualTo(frustumNear);
        assertThat(copy.getFrustumFarInWorldSpace()).isEqualTo(frustumFar);

        parcel.recycle();
    }
}
