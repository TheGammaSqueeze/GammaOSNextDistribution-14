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

import android.app.wallpapereffectsgeneration.TexturedMesh;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Parcel;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link TexturedMesh}
 *
 * atest CtsWallpaperEffectsGenerationServiceTestCases
 */

@RunWith(AndroidJUnit4.class)
public class TexturedMeshTest {
    private static final String TAG = "WallpaperEffectsGenerationTest";

    @Test
    public void testCreateTexturedMeshRequest() {
        final Bitmap bmp = Bitmap.createBitmap(3200, 4800, Config.ARGB_8888);
        final float[] vertices = WallpaperEffectsGenerationTestUtils.createVertices(1500000);
        final int[] indices = WallpaperEffectsGenerationTestUtils.createIndices(500000);
        final int indicesLayoutType = TexturedMesh.INDICES_LAYOUT_TRIANGLES;
        final int verticesLayoutType = TexturedMesh.VERTICES_LAYOUT_POSITION3_UV2;

        TexturedMesh texturedMesh = new TexturedMesh.Builder(bmp)
                .setIndices(indices)
                .setVertices(vertices)
                .setIndicesLayoutType(indicesLayoutType)
                .setVerticesLayoutType(verticesLayoutType).build();

        /** Check the original mesh. */
        assertThat(texturedMesh.getBitmap()).isEqualTo(bmp);
        assertThat(texturedMesh.getIndices()).isEqualTo(indices);
        assertThat(texturedMesh.getVertices()).isEqualTo(vertices);
        assertThat(texturedMesh.getIndicesLayoutType()).isEqualTo(indicesLayoutType);
        assertThat(texturedMesh.getVerticesLayoutType()).isEqualTo(verticesLayoutType);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        texturedMesh.writeToParcel(parcel, 0);
        Log.i(TAG, "textured mesh size = " + parcel.dataSize());
        parcel.setDataPosition(0);
        TexturedMesh copy =
                TexturedMesh.CREATOR.createFromParcel(parcel);
        /** Check the copied mesh. */
        assertThat(copy.getBitmap().sameAs(bmp)).isTrue();
        assertThat(copy.getIndices()).isEqualTo(indices);
        assertThat(copy.getVertices()).isEqualTo(vertices);
        assertThat(copy.getIndicesLayoutType()).isEqualTo(indicesLayoutType);
        assertThat(copy.getVerticesLayoutType()).isEqualTo(verticesLayoutType);

        parcel.recycle();
    }
}
