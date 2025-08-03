/*
 * * Copyright 2022 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package srcartifacts

import com.google.common.truth.Truth.assertThat
import java.nio.charset.StandardCharsets
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

interface SrcArtifact {
  fun getAllSrcArtifacts(): Set<Class<out SrcArtifact>>
}

@RunWith(JUnit4::class)
class SrcArtifactsTest {

  @Test
  fun allSrcArtifactsInterop() {
    val allSrcArtifacts =
      setOf(
        JavaInJavaDir::class.java,
        JavaSrc::class.java,
        JavaSrcjar::class.java,
        KtInKotlinDir::class.java,
        KtSrc::class.java,
      )

    for (artifact in allSrcArtifacts) {
      val instance = artifact.getDeclaredConstructor().newInstance()
      assertThat(instance.getAllSrcArtifacts()).isEqualTo(allSrcArtifacts)
    }
  }

  @Test
  fun resourceFileContent() {
    val fileContent =
      javaClass.classLoader
        .getResourceAsStream("resources/resources_in_resources_dir.txt")
        .use { it.readAllBytes() }
        .toString(StandardCharsets.UTF_8)
    assertThat(fileContent).isEqualTo("Test resource content.\n")
  }
}
