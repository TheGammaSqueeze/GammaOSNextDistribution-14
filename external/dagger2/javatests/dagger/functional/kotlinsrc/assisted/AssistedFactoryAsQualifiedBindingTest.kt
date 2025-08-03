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
import com.google.common.truth.Truth8.assertThat
import dagger.Binds
import dagger.BindsInstance
import dagger.BindsOptionalOf
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import java.util.Optional
import javax.inject.Inject
import javax.inject.Qualifier
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests that qualified assisted types can be provided and injected as normal types. */
@RunWith(JUnit4::class)
internal class AssistedFactoryAsQualifiedBindingTest {
  @Qualifier annotation class AsComponentDependency

  @Qualifier annotation class AsProvides

  @Qualifier annotation class AsBinds

  @Qualifier annotation class AsOptional

  @Qualifier annotation class AsMultibinding

  @Component(modules = [BarFactoryModule::class])
  interface TestComponent {
    fun foo(): Foo

    @Component.Factory
    interface Factory {
      fun create(
        @BindsInstance @AsComponentDependency bar: Bar,
        @BindsInstance @AsComponentDependency barFactory: BarFactory
      ): TestComponent
    }
  }

  @Module
  interface BarFactoryModule {
    @Binds @AsBinds fun bindsBar(@AsComponentDependency bar: Bar): Bar

    @Binds @AsBinds fun bindsBarFactory(@AsComponentDependency barFactory: BarFactory): BarFactory

    @BindsOptionalOf @AsOptional fun optionalBar(): Bar

    @BindsOptionalOf @AsOptional fun optionalBarFactory(): BarFactory

    @Multibinds @AsMultibinding fun barSet(): Set<Bar>

    @Multibinds @AsMultibinding fun barFactorySet(): Set<BarFactory>

    @Multibinds fun unqualifiedBarSet(): Set<Bar>

    @Multibinds fun unqualifiedBarFactorySet(): Set<BarFactory>

    companion object {
      @Provides @AsProvides fun providesBar(@AsComponentDependency bar: Bar): Bar = bar

      @Provides
      @AsProvides
      fun providesBarFactory(@AsComponentDependency barFactory: BarFactory): BarFactory = barFactory

      @Provides @AsOptional fun providesOptionalBar(@AsComponentDependency bar: Bar): Bar = bar

      @Provides
      @AsOptional
      fun providesOptionalBarFactory(@AsComponentDependency barFactory: BarFactory): BarFactory =
        barFactory

      @Provides
      @IntoSet
      @AsMultibinding
      fun providesMultibindingBar(@AsComponentDependency bar: Bar): Bar = bar

      @Provides
      @IntoSet
      @AsMultibinding
      fun providesMultibindingBarFactory(
        @AsComponentDependency barFactory: BarFactory
      ): BarFactory = barFactory

      @Provides
      @IntoSet
      fun providesUnqualifiedMultibindingBar(@AsComponentDependency bar: Bar): Bar = bar

      @Provides
      @IntoSet
      fun providesUnqualifiedMultibindingBarFactory(
        @AsComponentDependency barFactory: BarFactory
      ): BarFactory = barFactory
    }
  }

  class Foo
  @Inject
  constructor(
    val barFactory: BarFactory,
    @AsComponentDependency val barAsComponentDependency: Bar,
    @AsComponentDependency val barFactoryAsComponentDependency: BarFactory,
    @AsProvides val barAsProvides: Bar,
    @AsProvides val barFactoryAsProvides: BarFactory,
    @AsBinds val barAsBinds: Bar,
    @AsBinds val barFactoryAsBinds: BarFactory,
    @AsOptional val optionalBar: Optional<Bar>,
    @AsOptional val optionalBarFactory: Optional<BarFactory>,
    @AsMultibinding val barSet: Set<Bar>,
    @AsMultibinding val barFactorySet: Set<@JvmSuppressWildcards BarFactory>,
    val unqualifiedBarSet: Set<Bar>,
    val unqualifiedBarFactorySet: Set<@JvmSuppressWildcards BarFactory>
  )

  class Bar @AssistedInject constructor()

  @AssistedFactory
  fun interface BarFactory {
    fun create(): Bar
  }

  @Test
  fun testFoo() {
    val bar = Bar()
    val barFactory = BarFactory { bar }
    val foo =
      DaggerAssistedFactoryAsQualifiedBindingTest_TestComponent.factory()
        .create(bar, barFactory)
        .foo()

    // Test we can inject the "real" BarFactory implemented by Dagger
    assertThat(foo.barFactory).isNotNull()
    assertThat(foo.barFactory).isNotEqualTo(barFactory)
    assertThat(foo.barFactory.create()).isNotEqualTo(bar)

    // Test injection of a qualified Bar/BarFactory with custom @BindsInstance implementation
    assertThat(foo.barAsComponentDependency).isEqualTo(bar)
    assertThat(foo.barFactoryAsComponentDependency).isEqualTo(barFactory)

    // Test injection of a qualified Bar/BarFactory with custom @Provides implementation
    assertThat(foo.barAsProvides).isEqualTo(bar)
    assertThat(foo.barFactoryAsProvides).isEqualTo(barFactory)

    // Test injection of a qualified Bar/BarFactory with custom @Binds implementation
    assertThat(foo.barAsBinds).isEqualTo(bar)
    assertThat(foo.barFactoryAsBinds).isEqualTo(barFactory)

    // Test injection of a qualified Bar/BarFactory with custom @BindsOptionalOf implementation
    assertThat(foo.optionalBar).isPresent()
    assertThat(foo.optionalBar).hasValue(bar)
    assertThat(foo.optionalBarFactory).isPresent()
    assertThat(foo.optionalBarFactory).hasValue(barFactory)

    // Test injection of a qualified Bar/BarFactory as multibinding
    assertThat(foo.barSet).containsExactly(bar)
    assertThat(foo.barFactorySet).containsExactly(barFactory)

    // Test injection of a unqualified Bar/BarFactory as multibinding
    assertThat(foo.unqualifiedBarSet).containsExactly(bar)
    assertThat(foo.unqualifiedBarFactorySet).containsExactly(barFactory)
  }
}
