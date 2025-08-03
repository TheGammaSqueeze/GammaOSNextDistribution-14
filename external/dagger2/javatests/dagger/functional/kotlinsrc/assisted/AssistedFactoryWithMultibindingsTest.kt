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
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.multibindings.IntoSet
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class AssistedFactoryWithMultibindingsTest {
  @Component(modules = [ParentModule::class])
  interface ParentComponent {
    // Factory for assisted injection binding with multi binding contribution.
    fun multibindingFooFactory(): MultibindingFooFactory
    fun childComponent(): ChildComponent.Builder
  }

  class AssistedDep

  class MultibindingFoo
  @AssistedInject
  constructor(@Assisted val assistedDep: AssistedDep, private val stringSet: Set<String>) {
    fun assistedDep(): AssistedDep = assistedDep

    fun stringSet(): Set<String> = stringSet
  }

  @Subcomponent(modules = [ChildModule::class])
  interface ChildComponent {
    fun multibindingFooFactory(): MultibindingFooFactory

    @Subcomponent.Builder
    interface Builder {
      fun build(): ChildComponent
    }
  }

  @Module(subcomponents = [ChildComponent::class])
  class ParentModule {
    @Provides @IntoSet fun parentString(): String = "parent"
  }

  @Module
  class ChildModule {
    @Provides @IntoSet fun childString(): String = "child"
  }

  @AssistedFactory
  interface MultibindingFooFactory {
    fun createFoo(factoryAssistedDep1: AssistedDep): MultibindingFoo
  }

  @Test
  fun testAssistedFactoryWithMultibinding() {
    val assistedDep1 = AssistedDep()
    val parent = DaggerAssistedFactoryWithMultibindingsTest_ParentComponent.create()
    val child = parent.childComponent().build()
    val foo1 = parent.multibindingFooFactory().createFoo(assistedDep1)
    val foo2 = child.multibindingFooFactory().createFoo(assistedDep1)
    assertThat(foo1.assistedDep()).isEqualTo(foo2.assistedDep)
    assertThat(foo1.stringSet()).containsExactly("parent")
    assertThat(foo2.stringSet()).containsExactly("child", "parent")
  }
}
