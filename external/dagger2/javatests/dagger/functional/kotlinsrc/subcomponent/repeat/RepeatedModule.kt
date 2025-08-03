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

package dagger.functional.kotlinsrc.subcomponent.repeat

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet

@Module
class RepeatedModule {
  private val state = Any()

  @Provides fun state(): Any = state

  companion object {
    @Provides fun provideString(): String = "a string"

    @Provides @IntoSet fun contributeString(): String = "a string in a set"

    @Provides fun provideOnlyUsedInParent(): OnlyUsedInParent = object : OnlyUsedInParent() {}

    @Provides fun provideOnlyUsedInChild(): OnlyUsedInChild = object : OnlyUsedInChild() {}
  }
}
