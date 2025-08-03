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
class MapKeyWithDefaultTest {
  @MapKey(unwrapValue = false)
  annotation class MapKeyWithDefault(val hasDefault: Boolean = true, val required: Boolean)

  @Module
  internal interface TestModule {
    companion object {
      @Provides @IntoMap @MapKeyWithDefault(required = false) fun justRequired(): Int = 1

      @Provides
      @IntoMap
      @MapKeyWithDefault(required = false, hasDefault = false)
      fun both(): Int = 2
    }
  }

  @Component(modules = [TestModule::class])
  internal interface TestComponent {
    fun map(): Map<MapKeyWithDefault, Int>
  }

  @Test
  fun test() {
    val map = DaggerMapKeyWithDefaultTest_TestComponent.create().map()
    assertThat(map).hasSize(2)
    assertThat(map[AutoAnnotationHolder.mapKey(true, false)]).isEqualTo(1)
    assertThat(map[AutoAnnotationHolder.mapKey(false, false)]).isEqualTo(2)
  }

  // Note: @AutoAnnotation requires a static method. Normally, we would just use a companion object
  // but that generates both a static and non-static method so we need to use a normal object.
  object AutoAnnotationHolder {
    @JvmStatic
    @AutoAnnotation
    fun mapKey(hasDefault: Boolean, required: Boolean): MapKeyWithDefault {
      return AutoAnnotation_MapKeyWithDefaultTest_AutoAnnotationHolder_mapKey(hasDefault, required)
    }
  }
}
