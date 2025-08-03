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

package dagger.functional.kotlinsrc.generictypes

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GenericTypesComponentTest {
  @Component(modules = [GenericTypesModule::class])
  internal interface GenericTypesComponent : GenericTypesInterface<Int, String>
  internal interface GenericTypesInterface<T1, T2> {
    fun genericSet(): Set<T2>
    fun genericMap(): Map<T1, T2>
  }

  @Module
  internal interface GenericTypesModule {
    companion object {
      @Provides fun provideMap(str: String): Map<Int, String> = mapOf(INT_VALUE to str)

      @Provides fun provideSet(str: String): Set<String> = setOf(str)

      @Provides fun provideString(): String = STRING_VALUE
    }
  }

  @Test
  fun testComponent() {
    val component: GenericTypesComponent =
      DaggerGenericTypesComponentTest_GenericTypesComponent.create()
    assertThat(component.genericSet()).containsExactly(STRING_VALUE)
    assertThat(component.genericMap()).containsExactly(INT_VALUE, STRING_VALUE)
  }

  companion object {
    private const val STRING_VALUE = "someString"
    private const val INT_VALUE = 3
  }
}
