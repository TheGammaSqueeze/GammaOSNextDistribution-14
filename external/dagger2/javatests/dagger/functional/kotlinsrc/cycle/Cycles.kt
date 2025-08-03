/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.cycle

import dagger.Binds
import dagger.Component
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Inject
import javax.inject.Provider
import kotlin.jvm.JvmSuppressWildcards

/**
 * Cycle classes used for testing cyclic dependencies.
 *
 * `A <= (E <= D <= B <= C <= Provider<A>, Lazy<A>), (B <= C <= Provider<A>, Lazy<A>)`
 * `S <= Provider<S>, Lazy<S>`
 */
@Suppress("BadInject")
class Cycles private constructor() {
  class A @Inject constructor(val e: E, val b: B)
  class B @Inject constructor(val c: C)
  class C @Inject constructor(val aProvider: Provider<A>) {
    @Inject lateinit var aLazy: Lazy<A>
    @Inject lateinit var aLazyProvider: Provider<Lazy<A>>
  }
  class D @Inject constructor(val b: B)
  class E @Inject constructor(val d: D)
  class S @Inject constructor(val sProvider: Provider<S>) {
    @Inject lateinit var sLazy: Lazy<S>
  }
  class X @Inject constructor(val y: Y)
  class Y @Inject constructor(
    val mapOfProvidersOfX: Map<String, @JvmSuppressWildcards Provider<X>>,
    val mapOfProvidersOfY: Map<String, @JvmSuppressWildcards Provider<Y>>
  )

  @Module
  internal abstract class CycleMapModule {
    @Binds
    @IntoMap
    @StringKey("X")
    abstract fun x(x: X): X

    @Binds
    @IntoMap
    @StringKey("Y")
    abstract fun y(y: Y): Y
  }

  @Component(modules = [CycleMapModule::class])
  interface CycleMapComponent {
    fun y(): Y
  }

  @Component(modules = [CycleModule::class])
  interface CycleComponent {
    fun a(): A
    fun c(): C
    fun child(): ChildCycleComponent
  }

  @Module
  internal object CycleModule {
    @Provides
    fun provideObjectWithCycle(
      @Suppress("UNUSED_PARAMETER") someObject: Provider<Any>
    ): Any = "object"
  }

  @Component
  interface SelfCycleComponent {
    fun s(): S
  }

  @Subcomponent
  interface ChildCycleComponent {
    fun a(): A
    fun someObject(): Any
  }

  interface Foo
  class Bar @Inject constructor(
    @Suppress("UNUSED_PARAMETER") fooProvider: Provider<Foo>
  ) : Foo

  /**
   * A component with a cycle in which a `@Binds` binding depends on the binding that has to be
   * deferred.
   */
  @Component(modules = [BindsCycleModule::class])
  interface BindsCycleComponent {
    fun bar(): Bar
  }

  @Module
  internal abstract class BindsCycleModule {
    @Binds abstract fun foo(bar: Bar): Foo
  }
}
