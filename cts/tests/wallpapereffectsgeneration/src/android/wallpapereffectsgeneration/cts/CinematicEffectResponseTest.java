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
import android.app.wallpapereffectsgeneration.CinematicEffectResponse;
import android.app.wallpapereffectsgeneration.TexturedMesh;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests for {@link CinematicEffectResponse}
 *
 * atest CtsWallpaperEffectsGenerationServiceTestCases
 */

@RunWith(AndroidJUnit4.class)
public class CinematicEffectResponseTest {
    private static final String TAG = "WallpaperEffectsGenerationTest";

    @Test
    public void testCreateCinematicEffectResponse() {
        final int statusCode = CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_OK;
        final String taskId = UUID.randomUUID().toString();
        final int imageContentType = CinematicEffectResponse.IMAGE_CONTENT_TYPE_LANDSCAPE;
        final Bitmap bmp = Bitmap.createBitmap(3200, 4800, Config.ARGB_8888);
        final float[] vertices = WallpaperEffectsGenerationTestUtils.createVertices(1500000);
        final int[] indices = WallpaperEffectsGenerationTestUtils.createIndices(1500000);
        final int indicesLayoutType = TexturedMesh.INDICES_LAYOUT_TRIANGLES;
        final int verticesLayoutType = TexturedMesh.VERTICES_LAYOUT_POSITION3_UV2;

        final TexturedMesh texturedMesh = new TexturedMesh.Builder(bmp)
                .setIndices(indices)
                .setVertices(vertices)
                .setIndicesLayoutType(indicesLayoutType)
                .setVerticesLayoutType(verticesLayoutType).build();
        List<TexturedMesh> texturedMeshes = new ArrayList<TexturedMesh>();
        texturedMeshes.add(texturedMesh);

        final float[] anchorPointInWorldSpace1 = new float[]{0.5f, 1.5f, -1.0f};
        final float[] anchorPointInOutputUvSpace1 = new float[]{0.5f, 2.5f};
        final float yaw1 = 35.2f;
        final float pitch1 = 45.6f;
        final float dolly1 = 0.3f;
        final float fov1 = 60.0f;
        final float frustumNear1 = 0.5f;
        final float frustumFar1 = 200.0f;
        final float[] anchorPointInWorldSpace2 = new float[]{0.6f, 1.6f, -1.1f};
        final float[] anchorPointInOutputUvSpace2 = new float[]{0.6f, 2.6f};
        final float yaw2 = 35.1f;
        final float pitch2 = 45.4f;
        final float dolly2 = 0.4f;
        final float fov2 = 45.0f;
        final float frustumNear2 = 0.6f;
        final float frustumFar2 = 220.0f;

        final CameraAttributes attributes1 =
                new CameraAttributes.Builder(anchorPointInWorldSpace1,
                        anchorPointInOutputUvSpace1)
                        .setCameraOrbitYawDegrees(yaw1)
                        .setCameraOrbitPitchDegrees(pitch1)
                        .setDollyDistanceInWorldSpace(dolly1)
                        .setVerticalFovDegrees(fov1)
                        .setFrustumNearInWorldSpace(frustumNear1)
                        .setFrustumFarInWorldSpace(frustumFar1)
                        .build();
        final CameraAttributes attributes2 =
                new CameraAttributes.Builder(anchorPointInWorldSpace2,
                        anchorPointInOutputUvSpace2)
                        .setCameraOrbitYawDegrees(yaw2)
                        .setCameraOrbitPitchDegrees(pitch2)
                        .setDollyDistanceInWorldSpace(dolly2)
                        .setVerticalFovDegrees(fov2)
                        .setFrustumNearInWorldSpace(frustumNear2)
                        .setFrustumFarInWorldSpace(frustumFar2)
                        .build();

        CinematicEffectResponse response =
                new CinematicEffectResponse.Builder(statusCode, taskId)
                        .setImageContentType(imageContentType)
                        .setTexturedMeshes(texturedMeshes)
                        .setStartKeyFrame(attributes1)
                        .setEndKeyFrame(attributes2)
                        .build();

        /** Check the original request. */
        assertThat(response.getStatusCode()).isEqualTo(statusCode);
        assertThat(response.getTaskId()).isEqualTo(taskId);
        assertThat(response.getImageContentType()).isEqualTo(imageContentType);

        assertThat(response.getTexturedMeshes().size()).isEqualTo(1);
        final TexturedMesh mesh = response.getTexturedMeshes().get(0);
        assertThat(mesh.getBitmap()).isEqualTo(bmp);
        assertThat(mesh.getIndices()).isEqualTo(indices);
        assertThat(mesh.getVertices()).isEqualTo(vertices);
        assertThat(mesh.getIndicesLayoutType()).isEqualTo(indicesLayoutType);
        assertThat(mesh.getVerticesLayoutType()).isEqualTo(verticesLayoutType);

        CameraAttributes startAttributes = response.getStartKeyFrame();
        assertThat(startAttributes.getAnchorPointInWorldSpace()).isEqualTo(
                anchorPointInWorldSpace1);
        assertThat(startAttributes.getAnchorPointInOutputUvSpace()).isEqualTo(
                anchorPointInOutputUvSpace1);
        assertThat(startAttributes.getCameraOrbitYawDegrees()).isEqualTo(yaw1);
        assertThat(startAttributes.getCameraOrbitPitchDegrees()).isEqualTo(pitch1);
        assertThat(startAttributes.getDollyDistanceInWorldSpace()).isEqualTo(dolly1);
        assertThat(startAttributes.getVerticalFovDegrees()).isEqualTo(fov1);
        assertThat(startAttributes.getFrustumNearInWorldSpace()).isEqualTo(frustumNear1);
        assertThat(startAttributes.getFrustumFarInWorldSpace()).isEqualTo(frustumFar1);

        CameraAttributes endAttributes = response.getEndKeyFrame();
        assertThat(endAttributes.getAnchorPointInWorldSpace()).isEqualTo(anchorPointInWorldSpace2);
        assertThat(endAttributes.getAnchorPointInOutputUvSpace()).isEqualTo(
                anchorPointInOutputUvSpace2);
        assertThat(endAttributes.getCameraOrbitYawDegrees()).isEqualTo(yaw2);
        assertThat(endAttributes.getCameraOrbitPitchDegrees()).isEqualTo(pitch2);
        assertThat(endAttributes.getDollyDistanceInWorldSpace()).isEqualTo(dolly2);
        assertThat(endAttributes.getVerticalFovDegrees()).isEqualTo(fov2);
        assertThat(endAttributes.getFrustumNearInWorldSpace()).isEqualTo(frustumNear2);
        assertThat(endAttributes.getFrustumFarInWorldSpace()).isEqualTo(frustumFar2);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CinematicEffectResponse copy =
                CinematicEffectResponse.CREATOR.createFromParcel(parcel);

        /** Check the copied response. */
        assertThat(copy.getStatusCode()).isEqualTo(statusCode);
        assertThat(copy.getTaskId()).isEqualTo(taskId);
        assertThat(copy.getImageContentType()).isEqualTo(imageContentType);

        assertThat(copy.getTexturedMeshes().size()).isEqualTo(1);
        final TexturedMesh meshCopy = copy.getTexturedMeshes().get(0);
        assertThat(meshCopy.getBitmap().sameAs(bmp)).isTrue();
        assertThat(meshCopy.getIndices()).isEqualTo(indices);
        assertThat(meshCopy.getVertices()).isEqualTo(vertices);
        assertThat(meshCopy.getIndicesLayoutType()).isEqualTo(indicesLayoutType);
        assertThat(meshCopy.getVerticesLayoutType()).isEqualTo(verticesLayoutType);

        CameraAttributes startAttributesCopy = copy.getStartKeyFrame();
        assertThat(startAttributesCopy.getAnchorPointInWorldSpace()).isEqualTo(
                anchorPointInWorldSpace1);
        assertThat(startAttributesCopy.getAnchorPointInOutputUvSpace()).isEqualTo(
                anchorPointInOutputUvSpace1);
        assertThat(startAttributesCopy.getCameraOrbitYawDegrees()).isEqualTo(yaw1);
        assertThat(startAttributesCopy.getCameraOrbitPitchDegrees()).isEqualTo(pitch1);
        assertThat(startAttributesCopy.getDollyDistanceInWorldSpace()).isEqualTo(dolly1);
        assertThat(startAttributesCopy.getVerticalFovDegrees()).isEqualTo(fov1);
        assertThat(startAttributesCopy.getFrustumNearInWorldSpace()).isEqualTo(frustumNear1);
        assertThat(startAttributesCopy.getFrustumFarInWorldSpace()).isEqualTo(frustumFar1);

        CameraAttributes endAttributesCopy = copy.getEndKeyFrame();
        assertThat(endAttributesCopy.getAnchorPointInWorldSpace())
                .isEqualTo(anchorPointInWorldSpace2);
        assertThat(endAttributesCopy.getAnchorPointInOutputUvSpace()).isEqualTo(
                anchorPointInOutputUvSpace2);
        assertThat(endAttributesCopy.getCameraOrbitYawDegrees()).isEqualTo(yaw2);
        assertThat(endAttributesCopy.getCameraOrbitPitchDegrees()).isEqualTo(pitch2);
        assertThat(endAttributesCopy.getDollyDistanceInWorldSpace()).isEqualTo(dolly2);
        assertThat(endAttributesCopy.getVerticalFovDegrees()).isEqualTo(fov2);
        assertThat(endAttributesCopy.getFrustumNearInWorldSpace()).isEqualTo(frustumNear2);
        assertThat(endAttributesCopy.getFrustumFarInWorldSpace()).isEqualTo(frustumFar2);

        parcel.recycle();
    }
}
