/*
 * Copyright (C) 2023 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.functional.kotlinsrc.staticprovides

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.lang.Deprecated
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class StaticProvidesTest {
  @Parameter lateinit var component: StaticTestComponent

  @Test
  fun setMultibinding() {
    assertThat(component.multiboundStrings())
      .containsExactly(
        "${AllStaticModule::class.java}.contributeString",
        "${SomeStaticModule::class.java}.contributeStringFromAStaticMethod",
        "${SomeStaticModule::class.java}.contributeStringFromAnInstanceMethod",
      )
  }

  @Test
  fun allStaticProvidesModules_noFieldInComponentBuilder() {
    for (field in DaggerStaticTestComponent.Builder::class.java.getDeclaredFields()) {
      assertWithMessage(field.name).that(field.type).isNotEqualTo(AllStaticModule::class.java)
    }
  }

  @Test
  fun allStaticProvidesModules_deprecatedMethodInComponentBuilder() {
    for (method in DaggerStaticTestComponent.Builder::class.java.getDeclaredMethods()) {
      if (method.parameterTypes.contains(AllStaticModule::class.java)) {
        assertWithMessage(method.name)
          .that(method.isAnnotationPresent(Deprecated::class.java))
          .isTrue()
      }
    }
  }

  companion object {
    @JvmStatic
    @Parameters
    fun components(): Collection<Array<Any>> {
      return listOf(
        arrayOf(DaggerStaticTestComponent.create()),
        arrayOf(DaggerStaticTestComponentWithBuilder.builder().build()),
        arrayOf(
          DaggerStaticTestComponentWithBuilder.builder()
            // Note: Kotlin doesn't allow instantiating AllStaticModule object.
            .someStaticModule(SomeStaticModule())
            .build()
        )
      )
    }
  }
}
