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

import dagger.Lazy
import dagger.functional.kotlinsrc.generictypes.subpackage.Generic
import dagger.functional.kotlinsrc.generictypes.subpackage.Generic2
import javax.inject.Inject
import javax.inject.Provider

class ComplexGenerics
@Inject
constructor(
  val g2ga: Generic2<Generic<A>>,
  val g2gaLazy: Lazy<Generic2<Generic<A>>>,
  val g2gaProvider: Provider<Generic2<Generic<A>>>,
  val g2gb: Generic2<Generic<B>>,
  val g2gbLazy: Lazy<Generic2<Generic<B>>>,
  val g2gbProvider: Provider<Generic2<Generic<B>>>,
  val g2a: Generic2<A>,
  val gg2a: Generic<Generic2<A>>,
  val gg2b: Generic<Generic2<B>>
)
