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

package android.tools.io

import android.tools.utils.createDefaultArtifactBuilder
import com.google.common.truth.Truth
import kotlin.io.path.createTempDirectory
import org.junit.Test

class ArtifactTest {
    @Test
    fun equalityTestSameFile() {
        val outputDir = createTempDirectory().toFile()

        val artifact1 = createDefaultArtifactBuilder(RunStatus.RUN_FAILED, outputDir).build()
        artifact1.file.delete()
        val artifact2 = createDefaultArtifactBuilder(RunStatus.RUN_FAILED, outputDir).build()
        Truth.assertWithMessage("Artifacts are equal").that(artifact1).isEqualTo(artifact2)
    }

    @Test
    fun equalityTestDifferentFile() {
        val outputDir = createTempDirectory().toFile()
        val artifact1 = createDefaultArtifactBuilder(RunStatus.RUN_FAILED, outputDir).build()
        val artifact2 = createDefaultArtifactBuilder(RunStatus.RUN_FAILED, outputDir).build()
        Truth.assertWithMessage("Artifacts are equal").that(artifact1).isNotEqualTo(artifact2)
    }
}
