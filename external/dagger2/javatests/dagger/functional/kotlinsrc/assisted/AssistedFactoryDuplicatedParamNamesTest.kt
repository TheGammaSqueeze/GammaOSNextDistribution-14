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

package dagger.functional.kotlinsrc.assisted

import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class AssistedFactoryDuplicatedParamNamesTest {
  class Foo @AssistedInject constructor(@Assisted val arg: String, val bar: Bar)

  class Bar

  @AssistedFactory
  interface FooFactory {
    fun create(arg: String): Foo
  }

  @Component
  interface TestComponent {
    @Component.Factory
    interface Factory {
      fun create(@BindsInstance arg: Bar): TestComponent
    }

    fun fooFactory(): FooFactory
  }

  @Test
  fun duplicatedParameterNames_doesNotConflict() {
    val str = "test"
    val bar = Bar()
    val foo =
      DaggerAssistedFactoryDuplicatedParamNamesTest_TestComponent.factory()
        .create(bar)
        .fooFactory()
        .create(str)
    assertThat(foo.arg).isEqualTo(str)
    assertThat(foo.bar).isEqualTo(bar)
  }
}
