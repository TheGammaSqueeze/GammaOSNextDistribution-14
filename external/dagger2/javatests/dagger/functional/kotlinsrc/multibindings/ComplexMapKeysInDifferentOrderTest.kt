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

package dagger.functional.kotlinsrc.multibindings

import com.google.auto.value.AutoAnnotation
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComplexMapKeysInDifferentOrderTest {
  @MapKey(unwrapValue = false) annotation class ComplexMapKey(val i: Int, val j: Int)

  @Module
  internal interface TestModule {
    companion object {
      @Provides @IntoMap @ComplexMapKey(i = 1, j = 2) fun inOrder(): Int = 3

      @Provides @IntoMap @ComplexMapKey(j = 4, i = 5) fun backwardsOrder(): Int = 6
    }
  }

  @Component(modules = [TestModule::class])
  internal interface TestComponent {
    fun map(): Map<ComplexMapKey, Int>
  }

  @Test
  fun test() {
    val map = DaggerComplexMapKeysInDifferentOrderTest_TestComponent.create().map()
    assertThat(map[AutoAnnotationHolder.mapKey(1, 2)]).isEqualTo(3)
    assertThat(map[AutoAnnotationHolder.mapKey(5, 4)]).isEqualTo(6)
  }

  // Note: @AutoAnnotation requires a static method. Normally, we would just use a companion object
  // but that generates both a static and non-static method so we need to use a normal object.
  object AutoAnnotationHolder {
    @JvmStatic
    @AutoAnnotation
    fun mapKey(i: Int, j: Int): ComplexMapKey {
      return AutoAnnotation_ComplexMapKeysInDifferentOrderTest_AutoAnnotationHolder_mapKey(i, j)
    }
  }
}
