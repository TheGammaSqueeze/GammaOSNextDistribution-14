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

package app

import app.AssistedInjectClasses.MyComponent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AssistedInjectTest {
  private lateinit var component: MyComponent

  @Before
  fun setUp() {
    component = DaggerAssistedInjectClasses_MyComponent.create()
  }

  @Test
  fun testFoo() {
    val foo = component.fooFactory().create("str1")
    assertThat(foo).isNotNull()
    assertThat(foo.bar).isNotNull()
    assertThat(foo.assistedStr).isEqualTo("str1")
  }

  @Test
  fun testParameterizedFoo() {
    val parameterizedFoo = component.parameterizedFooFactory().create("str2")
    assertThat(parameterizedFoo).isNotNull()
    assertThat(parameterizedFoo.t1).isNotNull()
    assertThat(parameterizedFoo.assistedT2).isEqualTo("str2")
  }
}
