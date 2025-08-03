/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.membersinject

import dagger.functional.kotlinsrc.membersinject.subpackage.MembersWithSameNameParent
import javax.inject.Inject

// https://github.com/google/dagger/issues/755
class MembersWithSameNameChild : MembersWithSameNameParent() {
  @Inject internal lateinit var sameName: String
  internal var sameNameStringWasInvoked = false
  internal var sameNameObjectWasInvoked = false

  @Inject
  internal fun sameName(@Suppress("UNUSED_PARAMETER") sameName: String) {
    sameNameStringWasInvoked = true
  }

  @Inject
  internal fun sameName(@Suppress("UNUSED_PARAMETER") sameName: Any) {
    sameNameObjectWasInvoked = true
  }

  fun childSameName(): String {
    return sameName
  }

  fun childSameNameStringWasInvoked(): Boolean {
    return sameNameStringWasInvoked
  }

  fun childSameNameObjectWasInvoked(): Boolean {
    return sameNameObjectWasInvoked
  }
}
