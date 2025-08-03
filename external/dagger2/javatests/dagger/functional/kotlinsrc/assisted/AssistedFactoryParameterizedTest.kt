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
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class AssistedFactoryParameterizedTest {
  @Singleton
  @Component
  interface ParentComponent {
    // Tests a parameterized Factory with unique @Assisted types
    fun uniqueParameterizedFooFactory(): ParameterizedFooFactory<Dep2, AssistedDep2>

    // Tests a parameterized Factory with duplicate @Assisted types in its resolved request type.
    // Note: this is fine since the @Assisted types are still unique on the @AssistedInject and
    // @AssistedFactory types, so that the generated code can correctly matches types.
    fun dupeParameterizedFooFactory(): ParameterizedFooFactory<Dep1, AssistedDep1>

    // Tests a parameterized Factory with same type as binding
    fun bindingParameterizedFooFactory(): ParameterizedFooFactory<Dep1, Dep1>

    // Tests a parameterized Factory with fixed type parameters
    fun fixedParameterizedFooFactory(): FixedParameterizedFooFactory

    // Tests a parameterized Factory that extends an interface with a parameterized return type
    fun extendedParameterizedFooFactory(): ExtendedFooFactory<Dep2, AssistedDep2>

    // Tests a request of factories from another binding.
    fun someEntryPoint(): SomeEntryPoint
  }

  class Dep1 @Inject constructor(
    @Suppress("UNUSED_PARAMETER") dep2: Dep2, @Suppress("UNUSED_PARAMETER") dep3: Dep3)
  class Dep2 @Inject constructor(@Suppress("UNUSED_PARAMETER") dep3: Dep3)
  class Dep3 @Inject constructor(@Suppress("UNUSED_PARAMETER") dep4: Dep4)
  class Dep4 @Inject constructor()

  // A base interface to test that factories can reference subclasses of the assisted parameter.
  interface AssistedDep
  class AssistedDep1 : AssistedDep
  class AssistedDep2 : AssistedDep
  abstract class BaseFoo {
    @Inject lateinit var dep4: Dep4
  }

  class ParameterizedFoo<DepT, AssistedDepT>
  @AssistedInject
  constructor(
    val dep1: Dep1,
    @Assisted val assistedDep1: AssistedDep1,
    val depTProvider: Provider<DepT>,
    @Assisted val assistedDepT: AssistedDepT,
    @Assisted val assistedInt: Int,
    val factory: ParameterizedFooFactory<DepT, AssistedDepT>
  ) : BaseFoo() {
    @Inject lateinit var dep3: Dep3
  }

  @AssistedFactory
  interface ParameterizedFooFactory<DepT, AssistedDepT> {
    fun create(
      assistedDep1: AssistedDep1,
      assistedDepT: AssistedDepT,
      assistedInt: Int
    ): ParameterizedFoo<DepT, AssistedDepT>
  }

  @Test
  fun testUniqueParameterizedFooFactory() {
    val assistedDep1 = AssistedDep1()
    val assistedDep2 = AssistedDep2()
    val assistedInt = 7
    val parameterizedFoo =
      DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
        .uniqueParameterizedFooFactory()
        .create(assistedDep1, assistedDep2, assistedInt)
    assertThat(parameterizedFoo.dep1).isNotNull()
    assertThat(parameterizedFoo.depTProvider).isNotNull()
    assertThat(parameterizedFoo.depTProvider.get()).isNotNull()
    assertThat(parameterizedFoo.dep3).isNotNull()
    assertThat(parameterizedFoo.dep4).isNotNull()
    assertThat(parameterizedFoo.assistedDep1).isEqualTo(assistedDep1)
    assertThat(parameterizedFoo.assistedDepT).isEqualTo(assistedDep2)
    assertThat(parameterizedFoo.assistedInt).isEqualTo(assistedInt)
    assertThat(parameterizedFoo.factory).isNotNull()
  }

  @Test
  fun testDupeParameterizedFooFactory() {
    val assistedDep1 = AssistedDep1()
    val assistedInt = 7
    val parameterizedFoo =
      DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
        .dupeParameterizedFooFactory()
        .create(assistedDep1, assistedDep1, assistedInt)
    assertThat(parameterizedFoo.dep1).isNotNull()
    assertThat(parameterizedFoo.depTProvider).isNotNull()
    assertThat(parameterizedFoo.depTProvider.get()).isNotNull()
    assertThat(parameterizedFoo.dep3).isNotNull()
    assertThat(parameterizedFoo.dep4).isNotNull()
    assertThat(parameterizedFoo.assistedDep1).isEqualTo(assistedDep1)
    assertThat(parameterizedFoo.assistedDepT).isEqualTo(assistedDep1)
    assertThat(parameterizedFoo.assistedInt).isEqualTo(assistedInt)
    assertThat(parameterizedFoo.factory).isNotNull()
  }

  @Test
  fun testBindingParameterizedFooFactory() {
    val assistedDep1 = AssistedDep1()
    val dep1 = Dep1(Dep2(Dep3(Dep4())), Dep3(Dep4()))
    val assistedInt = 7
    val parameterizedFoo =
      DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
        .bindingParameterizedFooFactory()
        .create(assistedDep1, dep1, assistedInt)
    assertThat(parameterizedFoo.dep1).isNotNull()
    assertThat(parameterizedFoo.depTProvider).isNotNull()
    assertThat(parameterizedFoo.depTProvider.get()).isNotNull()
    assertThat(parameterizedFoo.dep3).isNotNull()
    assertThat(parameterizedFoo.dep4).isNotNull()
    assertThat(parameterizedFoo.assistedDep1).isEqualTo(assistedDep1)
    assertThat(parameterizedFoo.assistedDepT).isEqualTo(dep1)
    assertThat(parameterizedFoo.assistedInt).isEqualTo(assistedInt)
    assertThat(parameterizedFoo.factory).isNotNull()
  }

  @AssistedFactory
  interface FixedParameterizedFooFactory {
    fun create(
      assistedDep1: AssistedDep1,
      assistedDep2: AssistedDep2,
      assistedInt: Int
    ): ParameterizedFoo<Dep2, AssistedDep2>
  }

  @Test
  fun testFixedParameterizedFooFactory() {
    val assistedDep1 = AssistedDep1()
    val assistedDep2 = AssistedDep2()
    val assistedInt = 7
    val parameterizedFoo =
      DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
        .fixedParameterizedFooFactory()
        .create(assistedDep1, assistedDep2, assistedInt)
    assertThat(parameterizedFoo.dep1).isNotNull()
    assertThat(parameterizedFoo.depTProvider).isNotNull()
    assertThat(parameterizedFoo.depTProvider.get()).isNotNull()
    assertThat(parameterizedFoo.dep3).isNotNull()
    assertThat(parameterizedFoo.dep4).isNotNull()
    assertThat(parameterizedFoo.assistedDep1).isEqualTo(assistedDep1)
    assertThat(parameterizedFoo.assistedDepT).isEqualTo(assistedDep2)
    assertThat(parameterizedFoo.assistedInt).isEqualTo(assistedInt)
    assertThat(parameterizedFoo.factory).isNotNull()
  }

  interface ParameterizedFactory<ReturnT, DepT, AssistedDepT> {
    // Use different parameter names than Foo to make sure we're not assuming they're the same.
    fun create(
      factoryAssistedDep1: AssistedDep1,
      factoryAssistedDepT: AssistedDepT,
      factoryAssistedInt: Int
    ): ReturnT
  }

  @AssistedFactory
  interface ExtendedFooFactory<DepT, AssistedDepT> :
    ParameterizedFactory<ParameterizedFoo<DepT, AssistedDepT>, DepT, AssistedDepT>

  @Test
  fun testExtendedFooFactory() {
    val assistedDep1 = AssistedDep1()
    val assistedDep2 = AssistedDep2()
    val assistedInt = 7
    val parameterizedFoo =
      DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
        .extendedParameterizedFooFactory()
        .create(assistedDep1, assistedDep2, assistedInt)
    assertThat(parameterizedFoo.dep1).isNotNull()
    assertThat(parameterizedFoo.depTProvider).isNotNull()
    assertThat(parameterizedFoo.depTProvider.get()).isNotNull()
    assertThat(parameterizedFoo.dep3).isNotNull()
    assertThat(parameterizedFoo.dep4).isNotNull()
    assertThat(parameterizedFoo.assistedDep1).isEqualTo(assistedDep1)
    assertThat(parameterizedFoo.assistedDepT).isEqualTo(assistedDep2)
    assertThat(parameterizedFoo.assistedInt).isEqualTo(assistedInt)
    assertThat(parameterizedFoo.factory).isNotNull()
  }

  class SomeEntryPoint
  @Inject
  constructor(val dupeParameterizedFooFactory: ParameterizedFooFactory<Dep1, AssistedDep1>)

  @Test
  fun testParameterizedFooFactoryFromSomeEntryPoint() {
    val assistedDep1 = AssistedDep1()
    val assistedInt = 7
    val parameterizedFoo =
      DaggerAssistedFactoryParameterizedTest_ParentComponent.create()
        .someEntryPoint()
        .dupeParameterizedFooFactory
        .create(assistedDep1, assistedDep1, assistedInt)
    assertThat(parameterizedFoo.dep1).isNotNull()
    assertThat(parameterizedFoo.depTProvider).isNotNull()
    assertThat(parameterizedFoo.depTProvider.get()).isNotNull()
    assertThat(parameterizedFoo.dep3).isNotNull()
    assertThat(parameterizedFoo.dep4).isNotNull()
    assertThat(parameterizedFoo.assistedDep1).isEqualTo(assistedDep1)
    assertThat(parameterizedFoo.assistedDepT).isEqualTo(assistedDep1)
    assertThat(parameterizedFoo.assistedInt).isEqualTo(assistedInt)
    assertThat(parameterizedFoo.factory).isNotNull()
  }
}
