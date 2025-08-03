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

public class WallpaperEffectsGenerationTestUtils {

    public static float[] createVertices(int verticesNum) {
        float[] vertices = new float[verticesNum];
        for (int i = 0; i < verticesNum; i++) {
            vertices[i] = (float) i;
        }
        return vertices;
    }

    public static int[] createIndices(int indicesNum) {
        int[] indices = new int[indicesNum];
        for (int i = 0; i < indicesNum; i++) {
            indices[i] =  i;
        }
        return indices;
    }
}
