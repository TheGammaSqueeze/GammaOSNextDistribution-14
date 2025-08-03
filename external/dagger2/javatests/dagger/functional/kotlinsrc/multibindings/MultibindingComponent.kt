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

package dagger.functional.kotlinsrc.multibindings

import dagger.Component
import dagger.functional.kotlinsrc.multibindings.subpackage.ContributionsModule
import dagger.multibindings.StringKey
import javax.inject.Named
import javax.inject.Provider

@Component(
  modules = [MultibindingModule::class, MultibindsModule::class, ContributionsModule::class],
  dependencies = [MultibindingDependency::class]
)
interface MultibindingComponent {
  fun map(): Map<String, String>
  fun mapOfArrays(): Map<String, Array<String>>
  fun mapOfProviders(): Map<String, Provider<String>>
  fun mapKeys(): Set<String>
  fun mapValues(): Collection<String>
  fun set(): Set<Int>
  fun nestedKeyMap(): Map<NestedAnnotationContainer.NestedWrappedKey, String>
  fun numberClassKeyMap(): Map<Class<out Number>, String>
  fun classKeyMap(): Map<Class<*>, String>
  fun longKeyMap(): Map<Long, String>
  fun integerKeyMap(): Map<Int, String>
  fun shortKeyMap(): Map<Short, String>
  fun byteKeyMap(): Map<Byte, String>
  fun booleanKeyMap(): Map<Boolean, String>
  fun characterKeyMap(): Map<Char, String>
  fun unwrappedAnnotationKeyMap(): Map<StringKey, String>
  fun wrappedAnnotationKeyMap(): Map<WrappedAnnotationKey, String>

  @Named("complexQualifier") fun complexQualifierStringSet(): Set<String>
  fun emptySet(): Set<Any>

  @Named("complexQualifier") fun emptyQualifiedSet(): Set<Any>
  fun emptyMap(): Map<String, Any>

  @Named("complexQualifier") fun emptyQualifiedMap(): Map<String, Any>
  fun maybeEmptySet(): Set<CharSequence>

  @Named("complexQualifier") fun maybeEmptyQualifiedSet(): Set<CharSequence>
  fun maybeEmptyMap(): Map<String, CharSequence>

  @Named("complexQualifier") fun maybeEmptyQualifiedMap(): Map<String, CharSequence>
}
