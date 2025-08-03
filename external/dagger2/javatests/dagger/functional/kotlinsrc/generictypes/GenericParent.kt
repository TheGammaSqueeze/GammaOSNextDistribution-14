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

import javax.inject.Inject
import javax.inject.Provider

@Suppress("BadInject")
open class GenericParent<X : Any, Y : Any> @Inject constructor() {
  var registeredX: Provider<X>? = null
  var registeredY: Y? = null
  var registeredB: B? = null
  var registerParameterizedOfY: Parameterized<Y>? = null

  @Inject lateinit var x: Provider<X>
  @Inject lateinit var y: Y
  @Inject lateinit var b: B
  @Inject lateinit var parameterizedOfX: Parameterized<X>

  @Inject
  fun registerX(x: Provider<X>) {
    registeredX = x
  }

  @Inject
  fun registerY(y: Y) {
    registeredY = y
  }

  @Inject
  fun registerB(b: B) {
    registeredB = b
  }

  @Inject
  fun registerParameterizedOfY(parameterizedOfY: Parameterized<Y>) {
    registerParameterizedOfY = parameterizedOfY
  }

  class Parameterized<P : Any> @Inject constructor() {
    @Inject lateinit var p: P
  }
}
