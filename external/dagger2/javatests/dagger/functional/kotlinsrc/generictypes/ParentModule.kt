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

package dagger.functional.kotlinsrc.generictypes

import dagger.Module
import dagger.Provides

@Module
internal abstract class ParentModule<T1, T2, T3 : Iterable<T1>> where
T1 : Number,
T1 : Comparable<T1> {
  @Provides
  fun provideIterableOfAWithC(
    t1: T1,
    // TODO(b/264776723): The Javac version of this test uses T3 as the type, but we can't do that
    // here because it leads to List<? extends T1> rather than just List<T1> and there's no way
    // to fix that since even using @JvmSuppressWildcards at the subclass doesn't work.
    c: List<@JvmSuppressWildcards T1>
  ): Iterable<T1> = buildList {
    add(t1)
    addAll(c)
  }

  companion object {
    @Provides fun provideNonGenericBindingInParameterizedModule(): Char = 'c'

    @Provides
    fun provideStaticGenericTypeWithNoTypeParametersInParameterizedModule(): List<Set<String>> =
      emptyList()
  }
}
