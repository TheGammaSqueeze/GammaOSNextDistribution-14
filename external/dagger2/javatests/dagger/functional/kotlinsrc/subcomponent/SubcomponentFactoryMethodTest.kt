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

package dagger.functional.kotlinsrc.subcomponent

import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for subcomponent factory methods. */
@RunWith(JUnit4::class)
class SubcomponentFactoryMethodTest {
  @Module
  internal class IntModule {
    @Provides fun provideInt(): Int = 42
  }

  @Module
  internal class StringModule(val s: String) {
    @Provides fun provideString(i: Int): String = s + i
  }

  @Component(modules = [IntModule::class])
  internal interface TestComponent {
    fun newSubcomponent(stringModule: StringModule?): TestSubcomponent
  }

  @Subcomponent(modules = [StringModule::class])
  internal interface TestSubcomponent {
    fun string(): String
  }

  @Test
  fun creatingSubcomponentViaFactoryMethod_failsForNullParameter() {
    val component: TestComponent = DaggerSubcomponentFactoryMethodTest_TestComponent.create()
    try {
      component.newSubcomponent(null)
      Assert.fail()
    } catch (expected: NullPointerException) {}
  }
}
