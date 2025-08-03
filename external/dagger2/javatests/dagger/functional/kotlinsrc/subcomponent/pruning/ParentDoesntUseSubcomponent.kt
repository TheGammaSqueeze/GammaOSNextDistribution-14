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

package dagger.functional.kotlinsrc.subcomponent.pruning

import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.functional.kotlinsrc.subcomponent.pruning.ParentDoesntUseSubcomponent.ChildA
import dagger.multibindings.IntoSet
import javax.inject.Qualifier

/**
 * Supporting types for [SubcomponentOnlyRequestedBySiblingTest]. [ChildA] is a direct child of the
 * top level component, but is only requested within its sibling, not directly from its parent.
 */
@Component(modules = [ParentDoesntUseSubcomponent.ParentModule::class])
interface ParentDoesntUseSubcomponent {
  fun childBBuilder(): ChildB.Builder

  @Subcomponent(modules = [ChildAModule::class])
  interface ChildA {
    @Subcomponent.Builder
    interface Builder {
      fun build(): ChildA
    }

    fun componentHierarchy(): Set<Class<*>>
  }

  @Subcomponent(modules = [ChildBModule::class])
  interface ChildB {
    @Subcomponent.Builder
    interface Builder {
      fun build(): ChildB
    }

    fun componentHierarchy(): Set<Class<*>>

    @FromChildA fun componentHierarchyFromChildA(): Set<Class<*>>
  }

  @Module(subcomponents = [ChildA::class, ChildB::class])
  object ParentModule {
    @Provides
    @IntoSet
    fun provideComponentType(): Class<*> = ParentDoesntUseSubcomponent::class.java
  }

  @Module
  object ChildAModule {
    @Provides @IntoSet fun provideComponentType(): Class<*> = ChildA::class.java
  }

  @Module
  class ChildBModule {
    @Provides
    @FromChildA
    fun fromChildA(childABuilder: ChildA.Builder): Set<Class<*>> =
      childABuilder.build().componentHierarchy()

    companion object {
      @Provides @IntoSet fun provideComponentType(): Class<*> = ChildB::class.java
    }
  }

  @Qualifier annotation class FromChildA
}
