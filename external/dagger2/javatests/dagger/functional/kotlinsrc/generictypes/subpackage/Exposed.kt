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

package dagger.functional.kotlinsrc.generictypes.subpackage

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Provider

/** Injects inaccessible dependencies to test casting of these dependency arguments. */
@Suppress("BadInject")
class Exposed
@Inject
internal constructor(
  internal val pp: Internal,
  internal val ppp: Provider<Internal>,
  internal val lpp: Lazy<Internal>,
  internal val plpp: Provider<Lazy<Internal>>,
  internal val gpp: Generic<Internal>,
  internal val gppc: Generic<InternalContainer.PublicEnclosed>,
  internal val pgpp: Provider<Generic<Internal>>,
  internal val lgpp: Lazy<Generic<Internal>>,
  internal val plgpp: Provider<Lazy<Generic<Internal>>>
) {
  // Define public getters so that we can access the values in tests
  fun pp(): Public = pp
  fun ppp(): Provider<out Public> = ppp
  fun lpp(): Lazy<out Public> = lpp
  fun plpp(): Provider<out Lazy<out Public>> = plpp
  fun gpp(): Generic<out Public> = gpp
  fun gppc(): Generic<out Public> = gppc
  fun pgpp(): Provider<out Generic<out Public>> = pgpp
  fun lgpp(): Lazy<out Generic<out Public>> = lgpp
  fun plgpp(): Provider<out Lazy<out Generic<out Public>>> = plgpp

  @Inject internal lateinit var pp2: Internal
  @Inject internal lateinit var ppp2: Provider<Internal>
  @Inject internal lateinit var lpp2: Lazy<Internal>
  @Inject internal lateinit var plpp2: Provider<Lazy<Internal>>
  @Inject internal lateinit var gpp2: Generic2<Internal>
  @Inject internal lateinit var gppc2: Generic2<InternalContainer.PublicEnclosed>
  @Inject internal lateinit var pgpp2: Provider<Generic2<Internal>>
  @Inject internal lateinit var lgpp2: Lazy<Generic2<Internal>>
  @Inject internal lateinit var plgpp2: Provider<Lazy<Generic2<Internal>>>

  // Define public getters so that we can access the values in tests
  fun pp2(): Public = pp2
  fun ppp2(): Provider<out Public> = ppp2
  fun lpp2(): Lazy<out Public> = lpp2
  fun plpp2(): Provider<out Lazy<out Public>> = plpp2
  fun gpp2(): Generic2<out Public> = gpp2
  fun gppc2(): Generic2<out Public> = gppc2
  fun pgpp2(): Provider<out Generic2<out Public>> = pgpp2
  fun lgpp2(): Lazy<out Generic2<out Public>> = lgpp2
  fun plgpp2(): Provider<out Lazy<out Generic2<out Public>>> = plgpp2
}
