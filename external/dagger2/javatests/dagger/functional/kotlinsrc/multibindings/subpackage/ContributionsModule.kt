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

package dagger.functional.kotlinsrc.multibindings.subpackage

import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet

@Module
object ContributionsModule {
  @Provides @IntoSet fun contributeAnInt(
    @Suppress("UNUSED_PARAMETER") doubleDependency: Double
  ): Int = 1742

  @Provides @IntoSet fun contributeAnotherInt(): Int = 832

  @Provides @ElementsIntoSet fun contributeSomeInts(): Set<Int> = linkedSetOf(-1, -90, -17)
}
