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

package dagger.functional.kotlinsrc.subcomponent.module

import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Supporting types for [ModuleWithSubcomponentsTest]. */
@Component(modules = [UsesModuleSubcomponents.ModuleWithSubcomponents::class])
interface UsesModuleSubcomponents {
  fun usesChild(): UsesChild
  fun strings(): Set<String>

  @Module(subcomponents = [Child::class], includes = [AlsoIncludesSubcomponents::class])
  object ModuleWithSubcomponents {
    @Provides @IntoSet fun provideStringInParent(): String = "from parent"
  }

  @Module(subcomponents = [Child::class]) class AlsoIncludesSubcomponents

  @Subcomponent(modules = [ChildModule::class])
  interface Child {
    fun strings(): Set<String>

    @Subcomponent.Builder
    interface Builder {
      fun build(): Child
    }
  }

  @Module
  object ChildModule {
    @Provides @IntoSet fun provideStringInChild(): String = "from child"
  }

  class UsesChild @Inject internal constructor(childBuilder: Child.Builder) {
    var strings: Set<String> = childBuilder.build().strings()
  }

  @Module(includes = [ModuleWithSubcomponents::class]) class OnlyIncludesModuleWithSubcomponents

  @Component(modules = [OnlyIncludesModuleWithSubcomponents::class])
  interface ParentIncludesSubcomponentTransitively : UsesModuleSubcomponents
}
