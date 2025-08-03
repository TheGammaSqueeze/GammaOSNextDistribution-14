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
import dagger.Component
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class AssistedFactoryWithArrayTypesTest {
  @Component
  interface TestComponent {
    fun fooFactory(): FooFactory
  }

  @AssistedFactory
  interface FooFactory {
    fun create(depArray: Array<Dep>): Foo
  }

  class Dep

  class Foo @AssistedInject constructor(@Assisted val depArray: Array<Dep>)

  @Test
  fun testFooFactory() {
    val depArray = arrayOf(Dep(), Dep())
    val foo =
      DaggerAssistedFactoryWithArrayTypesTest_TestComponent.create().fooFactory().create(depArray)
    assertThat(foo.depArray).isEqualTo(depArray)
  }
}
