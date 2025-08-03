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

import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.functional.kotlinsrc.generictypes.subpackage.Exposed
import dagger.functional.kotlinsrc.generictypes.subpackage.PublicSubclass
import javax.inject.Provider

@Component(
  modules =
    [ChildDoubleModule::class, ChildIntegerModule::class, GenericComponent.NongenericModule::class]
)
interface GenericComponent {
  fun referencesGeneric(): ReferencesGeneric
  fun doubleGenericA(): GenericDoubleReferences<A>
  fun doubleGenericB(): GenericDoubleReferences<B>

  fun complexGenerics(): ComplexGenerics

  fun noDepsA(): GenericNoDeps<A>

  fun noDepsB(): GenericNoDeps<B>

  fun injectA(childA: GenericChild<A>)
  fun injectB(childB: GenericChild<B>)

  fun exposed(): Exposed
  fun publicSubclass(): PublicSubclass

  fun iterableInt(): Iterable<Int>
  fun iterableDouble(): Iterable<Double>

  fun stringsProvider(): Provider<List<String>> // Regression test for b/71595104

  // Regression test for b/71595104
  @Module
  abstract class GenericModule<T> {
    // Note that for subclasses that use String for T, this factory will still need two
    // Provider<String> framework dependencies.
    @Provides fun list(t: T, @Suppress("UNUSED_PARAMETER") string: String): List<T> = listOf(t)
  }

  // Regression test for b/71595104
  // TODO(b/264618194): Use object rather than class here.
  @Module
  class NongenericModule : GenericModule<String>() {
    @Provides fun string(): String = "string"
  }
}
