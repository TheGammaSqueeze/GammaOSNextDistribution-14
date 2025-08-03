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
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class AssistedFactoryTest {
  @Component
  interface ParentComponent {
    // Simple factory using a nested factory.
    fun nestedSimpleFooFactory(): SimpleFoo.Factory
    fun nestedSimpleFooFactoryProvider(): Provider<SimpleFoo.Factory>

    // Simple factory using a non-nested factory.
    fun nonNestedSimpleFooFactory(): SimpleFooFactory

    // Simple factory using a factory that extends a supertype.
    fun extendedSimpleFooFactory(): ExtendedSimpleFooFactory

    // Factory as interface
    fun fooFactory(): FooFactory

    // Factory as abstract class
    fun abstractFooFactory(): AbstractFooFactory

    // Factory without any assisted parameters
    fun noAssistedParametersFooFactory(): NoAssistedParametersFooFactory

    // Test injecting the factories from another class
    fun someEntryPoint(): SomeEntryPoint
  }

  // This class tests the request of factories from another binding.
  class SomeEntryPoint
  @Inject
  constructor(
    val nestedSimpleFooFactory: SimpleFoo.Factory,
    val nestedSimpleFooFactoryProvider: Provider<SimpleFoo.Factory>,
    val nonNestedSimpleFooFactory: SimpleFooFactory,
    val extendedSimpleFooFactory: ExtendedSimpleFooFactory,
    val fooFactory: FooFactory,
    val abstractFooFactory: AbstractFooFactory,
    val noAssistedParametersFooFactory: NoAssistedParametersFooFactory
  )

  class Dep1 @Inject constructor(
    @Suppress("UNUSED_PARAMETER") dep2: Dep2, @Suppress("UNUSED_PARAMETER") dep3: Dep3)
  class Dep2 @Inject constructor(@Suppress("UNUSED_PARAMETER") dep3: Dep3)
  class Dep3 @Inject constructor(@Suppress("UNUSED_PARAMETER") dep4: Dep4)
  class Dep4 @Inject constructor()

  // A base interface to test that factories can reference subclasses of the assisted parameter.
  interface AssistedDep
  class AssistedDep1 : AssistedDep
  class AssistedDep2 : AssistedDep
  class SimpleFoo @AssistedInject constructor(@Assisted val assistedDep: AssistedDep) {
    @AssistedFactory
    interface Factory {
      // Use different parameter names than Foo to make sure we're not assuming they're the same.
      fun createSimpleFoo(factoryAssistedDep: AssistedDep): SimpleFoo

      companion object {
        // A no-op method to test static methods in assisted factories
        fun staticMethod() {}
      }
    }
  }

  @AssistedFactory
  interface SimpleFooFactory {
    // Use different parameter names than Foo to make sure we're not assuming they're the same.
    fun createSimpleFoo(factoryAssistedDep1: AssistedDep): SimpleFoo

    companion object {
      // A no-op method to test static methods are allowed
      fun staticMethod() {}

      // A no-op method to test static methods that return assisted type are allowed
      fun staticSimpleFooMethod(): SimpleFoo? = null
    }
  }

  @AssistedFactory interface ExtendedSimpleFooFactory : SimpleFooFactory
  abstract class BaseFoo {
    @Inject lateinit var dep4: Dep4
  }

  class Foo
  @AssistedInject
  constructor(
    val dep1: Dep1,
    @Assisted val assistedDep1: AssistedDep1,
    val dep2Provider: Provider<Dep2>,
    @Assisted val assistedDep2: AssistedDep2,
    @Assisted val assistedInt: Int,
    val factory: FooFactory
  ) : BaseFoo() {
    @Inject lateinit var dep3: Dep3
  }

  @AssistedFactory
  interface FooFactory {
    // Use different parameter names than Foo to make sure we're not assuming they're the same.
    fun createFoo(
      factoryAssistedDep1: AssistedDep1,
      factoryAssistedDep2: AssistedDep2,
      factoryAssistedInt: Int
    ): Foo
  }

  @AssistedFactory
  abstract class AbstractFooFactory {
    // Use different parameter names than Foo to make sure we're not assuming they're the same.
    abstract fun createFoo(
      factoryAssistedDep1: AssistedDep1,
      factoryAssistedDep2: AssistedDep2,
      factoryAssistedInt: Int
    ): Foo

    // A no-op method to test concrete methods are allowed
    fun concreteMethod() {}

    // A no-op method to test concrete methods that return assisted type are allowed
    fun concreteFooMethod(): Foo? = null

    companion object {
      // A no-op method to test static methods are allowed
      fun staticMethod() {}

      // A no-op method to test static methods that return assisted type are allowed
      fun staticFooMethod(): Foo? = null
    }
  }

  class NoAssistedParametersFoo
  @AssistedInject
  constructor(
    val dep1: Dep1,
    val dep2Provider: Provider<Dep2>,
    val factory: NoAssistedParametersFooFactory
  ) : BaseFoo() {
    @Inject lateinit var dep3: Dep3
  }

  @AssistedFactory
  interface NoAssistedParametersFooFactory {
    fun createNoAssistedParametersFoo(): NoAssistedParametersFoo
  }

  @Test
  fun testNestedSimpleFooFactory() {
    val assistedDep1 = AssistedDep1()
    val simpleFoo1 =
      DaggerAssistedFactoryTest_ParentComponent.create()
        .nestedSimpleFooFactory()
        .createSimpleFoo(assistedDep1)
    assertThat(simpleFoo1.assistedDep).isEqualTo(assistedDep1)
    val assistedDep2 = AssistedDep2()
    val simpleFoo2 =
      DaggerAssistedFactoryTest_ParentComponent.create()
        .nestedSimpleFooFactory()
        .createSimpleFoo(assistedDep2)
    assertThat(simpleFoo2.assistedDep).isEqualTo(assistedDep2)
  }

  @Test
  fun testNestedSimpleFooFactoryProvider() {
    val assistedDep1 = AssistedDep1()
    val simpleFoo1 =
      DaggerAssistedFactoryTest_ParentComponent.create()
        .nestedSimpleFooFactoryProvider()
        .get()
        .createSimpleFoo(assistedDep1)
    assertThat(simpleFoo1.assistedDep).isEqualTo(assistedDep1)
    val assistedDep2 = AssistedDep2()
    val simpleFoo2 =
      DaggerAssistedFactoryTest_ParentComponent.create()
        .nestedSimpleFooFactoryProvider()
        .get()
        .createSimpleFoo(assistedDep2)
    assertThat(simpleFoo2.assistedDep).isEqualTo(assistedDep2)
  }

  @Test
  fun testNonNestedSimpleFooFactory() {
    val assistedDep1 = AssistedDep1()
    val simpleFoo =
      DaggerAssistedFactoryTest_ParentComponent.create()
        .nonNestedSimpleFooFactory()
        .createSimpleFoo(assistedDep1)
    assertThat(simpleFoo.assistedDep).isEqualTo(assistedDep1)
  }

  @Test
  fun testExtendedSimpleFooFactory() {
    val assistedDep1 = AssistedDep1()
    val simpleFoo =
      DaggerAssistedFactoryTest_ParentComponent.create()
        .extendedSimpleFooFactory()
        .createSimpleFoo(assistedDep1)
    assertThat(simpleFoo.assistedDep).isEqualTo(assistedDep1)
  }

  @Test
  fun testFooFactory() {
    val assistedDep1 = AssistedDep1()
    val assistedDep2 = AssistedDep2()
    val assistedInt = 7
    val foo =
      DaggerAssistedFactoryTest_ParentComponent.create()
        .fooFactory()
        .createFoo(assistedDep1, assistedDep2, assistedInt)
    assertThat(foo.dep1).isNotNull()
    assertThat(foo.dep2Provider).isNotNull()
    assertThat(foo.dep2Provider.get()).isNotNull()
    assertThat(foo.dep3).isNotNull()
    assertThat(foo.dep4).isNotNull()
    assertThat(foo.assistedDep1).isEqualTo(assistedDep1)
    assertThat(foo.assistedDep2).isEqualTo(assistedDep2)
    assertThat(foo.assistedInt).isEqualTo(assistedInt)
    assertThat(foo.factory).isNotNull()
  }

  @Test
  fun testNoAssistedParametersFooFactory() {
    val foo: NoAssistedParametersFoo =
      DaggerAssistedFactoryTest_ParentComponent.create()
        .noAssistedParametersFooFactory()
        .createNoAssistedParametersFoo()
    assertThat(foo.dep1).isNotNull()
    assertThat(foo.dep2Provider).isNotNull()
    assertThat(foo.dep2Provider.get()).isNotNull()
    assertThat(foo.dep3).isNotNull()
    assertThat(foo.dep4).isNotNull()
    assertThat(foo.factory).isNotNull()
  }

  @Test
  fun testAssistedFactoryFromSomeEntryPoint() {
    val someEntryPoint: SomeEntryPoint =
      DaggerAssistedFactoryTest_ParentComponent.create().someEntryPoint()
    assertThat(someEntryPoint.nestedSimpleFooFactory).isNotNull()
    assertThat(someEntryPoint.nestedSimpleFooFactoryProvider).isNotNull()
    assertThat(someEntryPoint.nonNestedSimpleFooFactory).isNotNull()
    assertThat(someEntryPoint.extendedSimpleFooFactory).isNotNull()
    assertThat(someEntryPoint.fooFactory).isNotNull()
    assertThat(someEntryPoint.abstractFooFactory).isNotNull()
    assertThat(someEntryPoint.noAssistedParametersFooFactory).isNotNull()
  }
}
